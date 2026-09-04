package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class SyncServiceTest {

    private static final String NOTE_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final Instant TEN = Instant.parse("2026-08-31T12:00:10Z");
    private static final Instant TWENTY = Instant.parse("2026-08-31T12:00:20Z");
    private static final Clock CLOCK = Clock.fixed(TWENTY, ZoneOffset.UTC);

    @Test
    public void sync_firstSyncUploadsSnapshotAndMarksSuccess() {
        SyncRecord localNote = note(TEN, "Local note");
        FakeStore store = new FakeStore(snapshot(localNote));
        FakeBackend backend = new FakeBackend(SyncSnapshot.empty());

        SyncState state = new SyncService(store, new SyncMerger(), CLOCK).sync(backend);

        assertThat(state.getStatus()).isEqualTo(SyncState.Status.SUCCESS);
        assertThat(state.getBackendIdentifier()).isEqualTo("fake-drive");
        assertThat(state.getLastSuccessfulSyncAt()).isEqualTo(TWENTY);
        assertThat(backend.snapshot.getRecords()).containsExactly(localNote);
        assertThat(store.appliedSnapshot.getRecords()).containsExactly(localNote);
        assertThat(store.states)
                .containsExactly(SyncState.Status.SYNCING, SyncState.Status.SUCCESS)
                .inOrder();
    }

    @Test
    public void sync_mergesRemoteNewerVersionThenPublishesAndAppliesIt() {
        SyncRecord local = note(TEN, "Older local text");
        SyncRecord remote = note(TWENTY, "Newer remote text");
        FakeStore store = new FakeStore(snapshot(local));
        FakeBackend backend = new FakeBackend(snapshot(remote));

        SyncState state = new SyncService(store, new SyncMerger(), CLOCK).sync(backend);

        assertThat(state.getStatus()).isEqualTo(SyncState.Status.SUCCESS);
        assertThat(state.getConflictCount()).isEqualTo(1);
        assertThat(backend.snapshot.getRecords()).containsExactly(remote);
        assertThat(store.appliedSnapshot.getRecords()).containsExactly(remote);
        assertThat(store.appliedConflicts).hasSize(1);
    }

    @Test
    public void sync_matchingRemoteSnapshotDoesNotPublishAgain() {
        SyncRecord note = note(TEN, "Already synchronized");
        FakeStore store = new FakeStore(snapshot(note));
        FakeBackend backend = new FakeBackend(snapshot(note));

        SyncState state = new SyncService(store, new SyncMerger(), CLOCK).sync(backend);

        assertThat(state.getStatus()).isEqualTo(SyncState.Status.SUCCESS);
        assertThat(backend.writeSnapshotCalls).isEqualTo(0);
        assertThat(store.appliedSnapshot.getRecords()).containsExactly(note);
    }

    @Test
    public void sync_doesNotReadOrPublishRemoteDataWhenLocalSnapshotIsIncomplete() {
        FakeStore store = new FakeStore(snapshot(note(TEN, "Local note")));
        store.snapshotBuildResult =
                SnapshotBuildResult.incomplete(
                        store.snapshot,
                        Collections.singletonList(
                                new SnapshotProblem(
                                        SnapshotProblem.Kind.MISSING_ATTACHMENT,
                                        SyncMetadata.RECORD_TYPE_NOTE,
                                        NOTE_ID)));
        FakeBackend backend = new FakeBackend(SyncSnapshot.empty());

        SyncState state = new SyncService(store, new SyncMerger(), CLOCK).sync(backend);

        assertThat(state.getStatus()).isEqualTo(SyncState.Status.ERROR);
        assertThat(state.getErrorMessage()).contains("MISSING_ATTACHMENT");
        assertThat(backend.events).isEmpty();
        assertThat(backend.writeSnapshotCalls).isEqualTo(0);
        assertThat(store.applyCalls).isEqualTo(0);
    }

    @Test
    public void sync_downloadsRequiredRemoteAttachmentBeforeApplyingSnapshot() throws Exception {
        SyncRecord remote = note(TEN, "Remote with attachment");
        byte[] bytes = "attachment content".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        FakeStore store = new FakeStore(SyncSnapshot.empty());
        store.attachmentHashes = Collections.singletonList(hash);
        FakeBackend backend = new FakeBackend(snapshot(remote));
        backend.attachments.put(hash, bytes);

        SyncState state = new SyncService(store, new SyncMerger(), CLOCK).sync(backend);

        assertThat(state.getStatus()).isEqualTo(SyncState.Status.SUCCESS);
        assertThat(store.attachments.get(hash)).isEqualTo(bytes);
        assertThat(store.events).containsExactly("writeAttachment", "applySnapshot").inOrder();
        assertThat(backend.events).containsExactly("readAttachment");
    }

    @Test
    public void sync_uploadsAttachmentBeforePublishingSnapshot() throws Exception {
        SyncRecord local = note(TEN, "Local with attachment");
        byte[] bytes = "local attachment".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        FakeStore store = new FakeStore(snapshot(local));
        store.attachmentHashes = Collections.singletonList(hash);
        store.attachments.put(hash, bytes);
        FakeBackend backend = new FakeBackend(SyncSnapshot.empty());

        SyncState state = new SyncService(store, new SyncMerger(), CLOCK).sync(backend);

        assertThat(state.getStatus()).isEqualTo(SyncState.Status.SUCCESS);
        assertThat(backend.attachments.get(hash)).isEqualTo(bytes);
        assertThat(backend.events).containsExactly("writeAttachment", "writeSnapshot").inOrder();
    }

    @Test
    public void sync_skipsUploadingAttachmentWhenRemoteBlobAlreadyExists() throws Exception {
        SyncRecord local = note(TEN, "Local with existing remote attachment");
        byte[] bytes = "local attachment".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        FakeStore store = new FakeStore(snapshot(local));
        store.attachmentHashes = Collections.singletonList(hash);
        store.attachments.put(hash, bytes);
        FakeBackend backend = new FakeBackend(SyncSnapshot.empty());
        backend.attachments.put(hash, bytes);

        SyncState state = new SyncService(store, new SyncMerger(), CLOCK).sync(backend);

        assertThat(state.getStatus()).isEqualTo(SyncState.Status.SUCCESS);
        // Drive is untrusted even for a content-addressed object, so remote bytes are verified
        // before publication.
        assertThat(backend.events).containsExactly("readAttachment", "writeSnapshot");
    }

    @Test
    public void sync_repairsCorruptLocalAttachmentFromRemote() throws Exception {
        SyncRecord local = note(TEN, "Local with corrupt attachment");
        byte[] bytes = "local attachment".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        FakeStore store = new FakeStore(snapshot(local));
        store.attachmentHashes = Collections.singletonList(hash);
        store.attachments.put(hash, "corrupted".getBytes(StandardCharsets.UTF_8));
        FakeBackend backend = new FakeBackend(SyncSnapshot.empty());
        backend.attachments.put(hash, bytes);

        SyncState state = new SyncService(store, new SyncMerger(), CLOCK).sync(backend);

        assertThat(state.getStatus()).isEqualTo(SyncState.Status.SUCCESS);
        assertThat(store.attachments.get(hash)).isEqualTo(bytes);
        assertThat(backend.events)
                .containsExactly("readAttachment", "readAttachment", "writeSnapshot")
                .inOrder();
    }

    @Test
    public void sync_corruptRemoteAttachmentWithValidLocalCopyDoesNotPublish() throws Exception {
        byte[] bytes = "local attachment".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        FakeStore store = new FakeStore(snapshot(note(TEN, "Local")));
        store.attachmentHashes = Collections.singletonList(hash);
        store.attachments.put(hash, bytes);
        FakeBackend backend = new FakeBackend(SyncSnapshot.empty());
        backend.attachments.put(hash, "corrupt remote".getBytes(StandardCharsets.UTF_8));

        SyncState state = new SyncService(store, new SyncMerger(), CLOCK).sync(backend);

        assertThat(state.getStatus()).isEqualTo(SyncState.Status.ERROR);
        assertThat(state.getErrorMessage()).contains("checksum");
        assertThat(backend.writeSnapshotCalls).isEqualTo(0);
        assertThat(store.applyCalls).isEqualTo(0);
    }

    @Test
    public void sync_invalidAttachmentDoesNotPublishOrApplySnapshot() {
        SyncRecord remote = note(TEN, "Remote with corrupt attachment");
        FakeStore store = new FakeStore(SyncSnapshot.empty());
        store.attachmentHashes =
                Collections.singletonList(
                        "d6f1f3d5d8cf9b5a4a2469787998dc45eb59f401b93b1b4cde4998dc409ebdc8");
        FakeBackend backend = new FakeBackend(snapshot(remote));
        backend.attachments.put(
                store.attachmentHashes.iterator().next(),
                "wrong bytes".getBytes(StandardCharsets.UTF_8));

        SyncState state = new SyncService(store, new SyncMerger(), CLOCK).sync(backend);

        assertThat(state.getStatus()).isEqualTo(SyncState.Status.ERROR);
        assertThat(state.getErrorMessage()).contains("checksum");
        assertThat(backend.writeSnapshotCalls).isEqualTo(0);
        assertThat(store.applyCalls).isEqualTo(0);
        // The blob is streamed rather than buffered, so the write is entered before the digest can
        // be checked — the mismatch surfaces at end of stream, inside the destination's own read
        // loop. What still must hold is that nothing was committed: RoomSyncStore writes to a
        // temporary file and only renames it once the stream completed cleanly.
        assertThat(store.attachments).isEmpty();
    }

    @Test
    public void sync_backendFailurePreservesLocalSnapshotAndRecordsError() {
        SyncRecord local = note(TEN, "Local");
        FakeStore store = new FakeStore(snapshot(local));
        store.state = SyncState.success("fake-drive", TEN, 0);
        FakeBackend backend = new FakeBackend(SyncSnapshot.empty());
        backend.readFailure = new IOException("offline");

        SyncState state = new SyncService(store, new SyncMerger(), CLOCK).sync(backend);

        assertThat(state.getStatus()).isEqualTo(SyncState.Status.ERROR);
        assertThat(state.getLastSuccessfulSyncAt()).isEqualTo(TEN);
        assertThat(state.getErrorMessage()).isEqualTo("offline");
        assertThat(store.applyCalls).isEqualTo(0);
        assertThat(backend.writeSnapshotCalls).isEqualTo(0);
    }

    @Test
    public void sync_invalidBackendIdentifierReturnsErrorInsteadOfThrowing() {
        FakeStore store = new FakeStore(SyncSnapshot.empty());
        FakeBackend backend = new FakeBackend(SyncSnapshot.empty());
        backend.identifier = " ";

        SyncState state = new SyncService(store, new SyncMerger(), CLOCK).sync(backend);

        assertThat(state.getStatus()).isEqualTo(SyncState.Status.ERROR);
        assertThat(state.getBackendIdentifier()).isEqualTo("unknown");
        assertThat(state.getErrorMessage()).contains("identifier");
        assertThat(store.applyCalls).isEqualTo(0);
    }

    @Test
    public void sync_runtimeStatusStoreFailureDoesNotCrashAfterApplyingContent() {
        SyncRecord local = note(TEN, "Local");
        FakeStore store = new FakeStore(snapshot(local));
        store.readStateFailure = new IllegalStateException("Room is unavailable");
        store.writeStateFailure = new IllegalStateException("Room is unavailable");
        FakeBackend backend = new FakeBackend(SyncSnapshot.empty());

        SyncState state = new SyncService(store, new SyncMerger(), CLOCK).sync(backend);

        assertThat(state.getStatus()).isEqualTo(SyncState.Status.ERROR);
        assertThat(store.applyCalls).isEqualTo(1);
        assertThat(backend.writeSnapshotCalls).isEqualTo(1);
    }

    @Test
    public void sync_oversizedAttachmentMetadataDoesNotUploadOrPublish() throws Exception {
        byte[] bytes = "small attachment".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        SyncRecord local = noteWithAttachment(hash, SyncBundleValidator.MAX_ATTACHMENT_BYTES + 1L);
        FakeStore store = new FakeStore(snapshot(local));
        store.attachmentHashes = Collections.singletonList(hash);
        store.attachments.put(hash, bytes);
        FakeBackend backend = new FakeBackend(SyncSnapshot.empty());

        SyncState state = new SyncService(store, new SyncMerger(), CLOCK).sync(backend);

        assertThat(state.getStatus()).isEqualTo(SyncState.Status.ERROR);
        assertThat(state.getErrorMessage()).contains("size exceeds");
        assertThat(backend.events).isEmpty();
        assertThat(backend.writeSnapshotCalls).isEqualTo(0);
    }

    @Test
    public void sync_replacesCorruptLocalAttachmentWithVerifiedRemoteBlob() throws Exception {
        byte[] expected = "remote attachment".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(expected);
        FakeStore store = new FakeStore(snapshot(note(TEN, "Local")));
        store.attachmentHashes = Collections.singletonList(hash);
        store.attachments.put(hash, "corrupt local".getBytes(StandardCharsets.UTF_8));
        FakeBackend backend = new FakeBackend(SyncSnapshot.empty());
        backend.attachments.put(hash, expected);

        SyncState state = new SyncService(store, new SyncMerger(), CLOCK).sync(backend);

        assertThat(state.getStatus()).isEqualTo(SyncState.Status.SUCCESS);
        assertThat(store.attachments.get(hash)).isEqualTo(expected);
        assertThat(backend.writeSnapshotCalls).isEqualTo(1);
    }

    private static SyncSnapshot snapshot(SyncRecord... records) {
        return new SyncSnapshot(Arrays.asList(records));
    }

    private static SyncRecord note(Instant updatedAt, String value) {
        JsonObject payload = new JsonObject();
        payload.addProperty("title", "Shopping");
        payload.addProperty("value", value);
        return SyncRecord.live(SyncRecord.Type.NOTE, NOTE_ID, updatedAt, payload);
    }

    private static SyncRecord noteWithAttachment(String hash, long size) {
        JsonObject payload = new JsonObject();
        JsonArray manifest = new JsonArray();
        JsonObject attachment = new JsonObject();
        attachment.addProperty("sha256", hash);
        attachment.addProperty("size", size);
        manifest.add(attachment);
        payload.add("attachmentsManifest", manifest);
        return SyncRecord.live(SyncRecord.Type.NOTE, NOTE_ID, TEN, payload);
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder value = new StringBuilder(digest.length * 2);
        for (byte byteValue : digest) {
            value.append(String.format("%02x", byteValue & 0xff));
        }
        return value.toString();
    }

    private static final class FakeStore implements SyncStore {
        private SyncSnapshot snapshot;
        private SnapshotBuildResult snapshotBuildResult;
        private SyncSnapshot appliedSnapshot;
        private List<SyncMergeResult.Conflict> appliedConflicts = Collections.emptyList();
        private SyncState state = SyncState.idle();
        private final Map<String, byte[]> attachments = new HashMap<>();
        private Collection<String> attachmentHashes = Collections.emptyList();
        private final List<SyncState.Status> states = new ArrayList<>();
        private final List<String> events = new ArrayList<>();
        private int applyCalls;
        private RuntimeException readStateFailure;
        private RuntimeException writeStateFailure;

        FakeStore(SyncSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public SyncSnapshot readSnapshot() {
            return snapshot;
        }

        @Override
        public SnapshotBuildResult buildSnapshot() {
            return snapshotBuildResult == null
                    ? SnapshotBuildResult.publishable(snapshot)
                    : snapshotBuildResult;
        }

        @Override
        public void applySnapshot(SyncSnapshot snapshot, List<SyncMergeResult.Conflict> conflicts) {
            events.add("applySnapshot");
            this.snapshot = snapshot;
            appliedSnapshot = snapshot;
            appliedConflicts = new ArrayList<>(conflicts);
            applyCalls++;
        }

        @Override
        public Collection<String> getAttachmentHashes(SyncSnapshot snapshot) {
            return attachmentHashes;
        }

        @Override
        public boolean hasAttachment(String sha256) {
            return attachments.containsKey(sha256);
        }

        @Override
        public InputStream readAttachment(String sha256) throws IOException {
            byte[] bytes = attachments.get(sha256);
            if (bytes == null) {
                throw new IOException("missing local attachment");
            }
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void writeAttachment(String sha256, long sizeBytes, InputStream content)
                throws IOException {
            events.add("writeAttachment");
            attachments.put(sha256, readAll(content));
        }

        @Override
        public SyncState readState() {
            if (readStateFailure != null) {
                throw readStateFailure;
            }
            return state;
        }

        @Override
        public void writeState(SyncState state) {
            if (writeStateFailure != null) {
                throw writeStateFailure;
            }
            this.state = state;
            states.add(state.getStatus());
        }
    }

    private static final class FakeBackend implements SyncBackend {
        private String identifier = "fake-drive";
        private SyncSnapshot snapshot;
        private final Map<String, byte[]> attachments = new HashMap<>();
        private final List<String> events = new ArrayList<>();
        private IOException readFailure;
        private int writeSnapshotCalls;

        FakeBackend(SyncSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public String getIdentifier() {
            return identifier;
        }

        @Override
        public SyncSnapshot readSnapshot() throws IOException {
            if (readFailure != null) {
                throw readFailure;
            }
            return snapshot;
        }

        @Override
        public void writeSnapshot(SyncSnapshot snapshot) {
            events.add("writeSnapshot");
            this.snapshot = snapshot;
            writeSnapshotCalls++;
        }

        @Override
        public boolean hasAttachment(String sha256) {
            return attachments.containsKey(sha256);
        }

        @Override
        public InputStream readAttachment(String sha256) {
            events.add("readAttachment");
            byte[] bytes = attachments.get(sha256);
            return bytes == null ? null : new ByteArrayInputStream(bytes);
        }

        @Override
        public void writeAttachment(String sha256, long sizeBytes, InputStream content)
                throws IOException {
            events.add("writeAttachment");
            attachments.put(sha256, readAll(content));
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        for (int read; (read = input.read(buffer)) != -1; ) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
