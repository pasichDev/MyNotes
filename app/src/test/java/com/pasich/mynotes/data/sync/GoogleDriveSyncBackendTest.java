package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GoogleDriveSyncBackendTest {
    private static final String NOTE_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String SECOND_NOTE_ID = "550e8400-e29b-41d4-a716-446655440001";
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC);

    private FakeDriveServer server;

    @Before
    public void setUp() throws Exception {
        server = new FakeDriveServer();
    }

    @After
    public void tearDown() {
        server.close();
    }

    @Test
    public void writeSnapshot_createsOwnedFolderBundleAndAttachment() throws Exception {
        GoogleDriveSyncBackend backend =
                new GoogleDriveSyncBackend(
                        "token",
                        server.apiBase(),
                        server.uploadBase(),
                        CLOCK,
                        new SyncBundleCodec());
        byte[] attachmentBytes = "photo".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(attachmentBytes);

        assertThat(backend.readSnapshot().getRecords()).isEmpty();

        backend.writeAttachment(
                hash, attachmentBytes.length, new ByteArrayInputStream(attachmentBytes));
        backend.writeAttachment(
                hash, attachmentBytes.length, new ByteArrayInputStream(attachmentBytes));
        publish(backend, snapshot(hash));

        assertThat(server.ownedFolderCount()).isEqualTo(1);
        assertThat(server.bundleCount()).isEqualTo(1);
        assertThat(server.ownedAttachmentCount(hash)).isEqualTo(1);
        assertThat(server.readAttachment(hash)).isEqualTo(attachmentBytes);
        assertThat(backend.hasAttachment(hash)).isTrue();

        SyncSnapshot remoteSnapshot =
                new SyncBundleCodec()
                        .decode(new ByteArrayInputStream(server.readBundleBytes()))
                        .getSnapshot();
        assertThat(remoteSnapshot.find(SyncRecord.Type.NOTE, NOTE_ID)).isNotNull();
    }

    @Test
    public void writeAttachment_resumesAcrossMultipleDriveChunksWithoutBufferingTheFile()
            throws Exception {
        GoogleDriveSyncBackend backend =
                new GoogleDriveSyncBackend(
                        "token",
                        server.apiBase(),
                        server.uploadBase(),
                        CLOCK,
                        new SyncBundleCodec());
        byte[] bytes = new byte[600 * 1024];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) (index % 251);
        }
        String hash = sha256(bytes);

        backend.writeAttachment(hash, bytes.length, new ByteArrayInputStream(bytes));

        assertThat(server.ownedAttachmentCount(hash)).isEqualTo(1);
        assertThat(server.readAttachment(hash)).isEqualTo(bytes);
    }

    @Test
    public void writeAttachment_doesNotTrustCorruptObjectTaggedWithExpectedHash() throws Exception {
        byte[] expected = "verified attachment".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(expected);
        server.seedCorruptAttachment(hash, "wrong bytes".getBytes(StandardCharsets.UTF_8));

        backend().writeAttachment(hash, expected.length, new ByteArrayInputStream(expected));

        assertThat(server.ownedAttachmentCount(hash)).isEqualTo(2);
        try (java.io.InputStream restored = backend().readAttachment(hash)) {
            assertThat(readAll(restored)).isEqualTo(expected);
        }
    }

    @Test
    public void concurrentFirstSync_createsDuplicateRootsThenConvergesWithoutLosingEitherNote()
            throws Exception {
        GoogleDriveSyncBackend first = backend();
        GoogleDriveSyncBackend second = backend();
        // Both devices read the empty account first, which is what makes the publishes concurrent.
        RemoteSnapshot firstContext = first.readSnapshotResult();
        RemoteSnapshot secondContext = second.readSnapshotResult();
        server.pauseTheNextTwoEmptyRootListings();
        SyncSnapshot firstSnapshot = snapshot(NOTE_ID, null);
        SyncSnapshot secondSnapshot = snapshot(SECOND_NOTE_ID, null);
        Thread firstThread = new Thread(() -> publishUnchecked(first, firstSnapshot, firstContext));
        Thread secondThread =
                new Thread(() -> publishUnchecked(second, secondSnapshot, secondContext));

        firstThread.start();
        secondThread.start();
        firstThread.join(5_000L);
        secondThread.join(5_000L);
        assertThat(firstThread.isAlive()).isFalse();
        assertThat(secondThread.isAlive()).isFalse();
        assertThat(server.ownedFolderCount()).isEqualTo(2);

        SyncSnapshot reconciled = backend().readSnapshot();

        assertThat(reconciled.find(SyncRecord.Type.NOTE, NOTE_ID)).isNotNull();
        assertThat(reconciled.find(SyncRecord.Type.NOTE, SECOND_NOTE_ID)).isNotNull();
        publish(first, reconciled);
        assertThat(second.readSnapshot().find(SyncRecord.Type.NOTE, NOTE_ID)).isNotNull();
        assertThat(second.readSnapshot().find(SyncRecord.Type.NOTE, SECOND_NOTE_ID)).isNotNull();
    }

    @Test
    public void readSnapshotResult_preservesConflictBetweenConcurrentCausalHeads()
            throws Exception {
        SyncBundleCodec codec = new SyncBundleCodec();
        byte[] base = codec.encode(snapshotWithTitle("Base"), CLOCK.instant());
        String baseId = codec.decode(new ByteArrayInputStream(base)).getBundleId();
        byte[] first =
                codec.encode(
                        snapshotWithTitle("First offline edit"),
                        CLOCK.instant(),
                        Collections.singleton(baseId));
        byte[] second =
                codec.encode(
                        snapshotWithTitle("Second offline edit"),
                        CLOCK.instant(),
                        Collections.singleton(baseId));
        server.seedOwnedBundleBytes(base);
        server.seedOwnedBundleBytes(first);
        server.seedOwnedBundleBytes(second);

        RemoteSnapshot remote = backend().readSnapshotResult();

        assertThat(remote.getFrontierBundleIds()).hasSize(2);
        assertThat(remote.getConflicts()).hasSize(1);
        assertThat(remote.getConflicts().get(0).getLoser().getPayload().get("title").getAsString())
                .isAnyOf("First offline edit", "Second offline edit");
    }

    @Test
    public void readSnapshotResult_descendantSupersedesSiblingHeadsWithoutRepeatingConflict()
            throws Exception {
        SyncBundleCodec codec = new SyncBundleCodec();
        byte[] base = codec.encode(snapshotWithTitle("Base"), CLOCK.instant());
        String baseId = codec.decode(new ByteArrayInputStream(base)).getBundleId();
        byte[] first =
                codec.encode(
                        snapshotWithTitle("First"), CLOCK.instant(), Collections.singleton(baseId));
        String firstId = codec.decode(new ByteArrayInputStream(first)).getBundleId();
        byte[] second =
                codec.encode(
                        snapshotWithTitle("Second"),
                        CLOCK.instant(),
                        Collections.singleton(baseId));
        String secondId = codec.decode(new ByteArrayInputStream(second)).getBundleId();
        byte[] descendant =
                codec.encode(
                        snapshotWithTitle("Resolved"),
                        CLOCK.instant(),
                        Arrays.asList(firstId, secondId));
        server.seedOwnedBundleBytes(base);
        server.seedOwnedBundleBytes(first);
        server.seedOwnedBundleBytes(second);
        server.seedOwnedBundleBytes(descendant);

        RemoteSnapshot remote = backend().readSnapshotResult();

        assertThat(remote.getFrontierBundleIds())
                .containsExactly(codec.decode(new ByteArrayInputStream(descendant)).getBundleId());
        assertThat(remote.getConflicts()).isEmpty();
        assertThat(
                        remote.getSnapshot()
                                .find(SyncRecord.Type.NOTE, NOTE_ID)
                                .getPayload()
                                .get("title")
                                .getAsString())
                .isEqualTo("Resolved");
    }

    // ---------------------------------------------------------------- resumable uploads

    @Test
    public void resumableUpload_completesWhenTheFirstChunkIsOnlyPartiallyAcknowledged()
            throws Exception {
        byte[] payload = payloadOfBytes(600 * 1024);
        String hash = sha256(payload);
        server.acceptOnlyNextChunkBytes(100_000);

        backend().writeAttachment(hash, payload.length, new ByteArrayInputStream(payload));

        assertThat(server.attachmentContent(hash)).isEqualTo(payload);
        assertThat(server.rejectedChunkRanges()).isEmpty();
    }

    @Test
    public void resumableUpload_completesAcrossSeveralPartialAcknowledgements() throws Exception {
        byte[] payload = payloadOfBytes(700 * 1024);
        String hash = sha256(payload);
        server.acceptOnlyNextChunkBytes(1);
        server.acceptOnlyNextChunkBytes(50_000);
        server.acceptOnlyNextChunkBytes(3);
        server.acceptOnlyNextChunkBytes(200_000);

        backend().writeAttachment(hash, payload.length, new ByteArrayInputStream(payload));

        assertThat(server.attachmentContent(hash)).isEqualTo(payload);
        assertThat(server.rejectedChunkRanges()).isEmpty();
    }

    @Test
    public void resumableUpload_completesOnExactChunkBoundaries() throws Exception {
        byte[] payload = payloadOfBytes(512 * 1024);
        String hash = sha256(payload);

        backend().writeAttachment(hash, payload.length, new ByteArrayInputStream(payload));

        assertThat(server.attachmentContent(hash)).isEqualTo(payload);
        assertThat(server.rejectedChunkRanges()).isEmpty();
    }

    @Test
    public void resumableUpload_rejectsAnAcknowledgementThatMovesBackwards() throws Exception {
        byte[] payload = payloadOfBytes(600 * 1024);
        String hash = sha256(payload);
        server.acceptOnlyNextChunkBytes(200_000);
        server.reportNextChunkRangeEnd(1_000);

        IOException failure = assertUploadFails(hash, payload);

        assertThat(failure).hasMessageThat().contains("backwards");
        assertThat(server.attachmentContent(hash)).isNull();
    }

    @Test
    public void resumableUpload_rejectsAnAcknowledgementBeyondTheDeclaredSize() throws Exception {
        byte[] payload = payloadOfBytes(300 * 1024);
        String hash = sha256(payload);
        server.reportNextChunkRangeEnd(payload.length + 5_000L);

        IOException failure = assertUploadFails(hash, payload);

        assertThat(failure).hasMessageThat().contains("more bytes than the attachment declares");
        assertThat(server.attachmentContent(hash)).isNull();
    }

    @Test
    public void resumableUpload_rejectsAnAcknowledgementOfBytesThatWereNeverSent()
            throws Exception {
        // Inside the declared size, but past the end of the 256 KiB range actually sent.
        byte[] payload = payloadOfBytes(600 * 1024);
        String hash = sha256(payload);
        server.reportNextChunkRangeEnd(400_000L);

        IOException failure = assertUploadFails(hash, payload);

        assertThat(failure).hasMessageThat().contains("never sent");
        assertThat(server.attachmentContent(hash)).isNull();
    }

    @Test
    public void resumableUpload_failsRatherThanSpinWhenDriveStopsMakingProgress() throws Exception {
        byte[] payload = payloadOfBytes(300 * 1024);
        String hash = sha256(payload);
        // Every PUT answered with a 308 that commits nothing at all.
        for (int index = 0; index < 6; index++) {
            server.acceptOnlyNextChunkBytes(0);
        }

        IOException failure = assertUploadFails(hash, payload);

        assertThat(failure).hasMessageThat().contains("stopped making progress");
        assertThat(server.attachmentContent(hash)).isNull();
    }

    @Test
    public void resumableUpload_recoversFromATransientServerErrorBetweenChunks() throws Exception {
        byte[] payload = payloadOfBytes(600 * 1024);
        String hash = sha256(payload);
        server.acceptOnlyNextChunkBytes(120_000);
        server.failNextChunk(503);

        backend().writeAttachment(hash, payload.length, new ByteArrayInputStream(payload));

        assertThat(server.attachmentContent(hash)).isEqualTo(payload);
        assertThat(server.rejectedChunkRanges()).isEmpty();
    }

    @Test
    public void resumableUpload_neverCommitsWrongBytesWhenTheConnectionDropsBetweenChunks()
            throws Exception {
        byte[] payload = payloadOfBytes(600 * 1024);
        String hash = sha256(payload);
        server.acceptOnlyNextChunkBytes(90_000);
        server.dropNextChunkConnection();

        try {
            backend().writeAttachment(hash, payload.length, new ByteArrayInputStream(payload));
        } catch (IOException recoveredOrFailed) {
            // Either outcome is acceptable here; a committed blob with wrong bytes is not.
        }

        byte[] stored = server.attachmentContent(hash);
        if (stored != null) {
            assertThat(stored).isEqualTo(payload);
        }
        assertThat(server.rejectedChunkRanges()).isEmpty();
    }

    @Test
    public void resumableUpload_abortsWhenTheThreadIsInterrupted() throws Exception {
        byte[] payload = payloadOfBytes(600 * 1024);
        String hash = sha256(payload);

        Thread.currentThread().interrupt();
        try {
            backend().writeAttachment(hash, payload.length, new ByteArrayInputStream(payload));
            throw new AssertionError("Expected an interrupted upload to fail");
        } catch (IOException expected) {
            assertThat(expected).isInstanceOf(java.io.InterruptedIOException.class);
        } finally {
            Thread.interrupted();
        }

        assertThat(server.attachmentContent(hash)).isNull();
    }

    @Test
    public void resumableUpload_rejectsASourceShorterThanItsDeclaredSize() throws Exception {
        byte[] payload = payloadOfBytes(600 * 1024);
        String hash = sha256(payload);
        byte[] truncated = Arrays.copyOf(payload, 300 * 1024);

        try {
            backend().writeAttachment(hash, payload.length, new ByteArrayInputStream(truncated));
            throw new AssertionError("Expected a short source to fail");
        } catch (IOException expected) {
            assertThat(expected).hasMessageThat().contains("ended before its declared size");
        }

        assertThat(server.attachmentContent(hash)).isNull();
    }

    @Test
    public void resumableUpload_rejectsASourceLongerThanItsDeclaredSize() throws Exception {
        byte[] declared = payloadOfBytes(300 * 1024);
        byte[] actual = payloadOfBytes(400 * 1024);
        String hash = sha256(declared);

        try {
            backend().writeAttachment(hash, declared.length, new ByteArrayInputStream(actual));
            throw new AssertionError("Expected an oversized source to fail");
        } catch (IOException expected) {
            assertThat(expected).hasMessageThat().contains("exceeds its declared size");
        }
    }

    // ---------------------------------------------------------------- zero-byte attachments

    @Test
    public void writeAttachment_publishesAZeroByteBlobThatIsReadableAgain() throws Exception {
        byte[] empty = new byte[0];
        String hash = sha256(empty);
        assertThat(hash)
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

        GoogleDriveSyncBackend backend = backend();
        backend.writeAttachment(hash, 0L, new ByteArrayInputStream(empty));

        assertThat(server.attachmentContent(hash)).isEqualTo(empty);
        assertThat(backend.hasAttachment(hash)).isTrue();
        try (java.io.InputStream restored = backend.readAttachment(hash)) {
            assertThat(restored).isNotNull();
            assertThat(readAll(restored)).isEqualTo(empty);
        }
    }

    @Test
    public void writeAttachment_rejectsANonEmptySourceDeclaredAsZeroBytes() throws Exception {
        String hash = sha256(new byte[0]);

        try {
            backend().writeAttachment(hash, 0L, new ByteArrayInputStream(new byte[] {1}));
            throw new AssertionError("Expected a non-empty source declared as empty to fail");
        } catch (IOException expected) {
            assertThat(expected).hasMessageThat().contains("exceeds its declared size");
        }
    }

    private IOException assertUploadFails(String hash, byte[] payload) {
        try {
            backend().writeAttachment(hash, payload.length, new ByteArrayInputStream(payload));
        } catch (IOException failure) {
            return failure;
        }
        throw new AssertionError("Expected the resumable upload to fail");
    }

    private static byte[] payloadOfBytes(int size) {
        byte[] payload = new byte[size];
        for (int index = 0; index < size; index++) {
            payload[index] = (byte) ((index * 31 + 7) & 0xff);
        }
        return payload;
    }

    // ------------------------------------------------- durable unresolved conflicts

    @Test
    public void aFreshDeviceStillDiscoversAnUnresolvedConflictAfterAMergedDescendant()
            throws Exception {
        // Device A publishes its version.
        MemoryStore deviceA = new MemoryStore(note(NOTE_ID, T10, "written on A"));
        assertThat(sync(deviceA).getStatus()).isEqualTo(SyncState.Status.SUCCESS);

        // Device B has its own concurrent edit of the same note, merges, and publishes the
        // descendant. Before this change that descendant carried only the winner.
        MemoryStore deviceB = new MemoryStore(note(NOTE_ID, T20, "written on B"));
        assertThat(sync(deviceB).getStatus()).isEqualTo(SyncState.Status.SUCCESS);
        assertThat(deviceB.conflicts).hasSize(1);

        // Device D is brand new: empty local database, no knowledge of either edit.
        MemoryStore deviceD = new MemoryStore();
        assertThat(sync(deviceD).getStatus()).isEqualTo(SyncState.Status.SUCCESS);

        // The deterministic winner is visible...
        SyncRecord winner = deviceD.snapshot.find(SyncRecord.Type.NOTE, NOTE_ID);
        assertThat(winner).isNotNull();
        assertThat(winner.getPayload().get("value").getAsString()).isEqualTo("written on B");

        // ...and the losing version is still recoverable, with identity enough to resolve it.
        assertThat(deviceD.conflicts).hasSize(1);
        SyncMergeResult.Conflict recovered = deviceD.conflicts.get(0);
        assertThat(recovered.getLoser().getPayload().get("value").getAsString())
                .isEqualTo("written on A");
        assertThat(recovered.getLoserVersionId()).isNotEmpty();
        assertThat(recovered.getWinnerSource()).isEqualTo(SyncMergeResult.Source.REMOTE);
        assertThat(recovered.getLoserSource()).isEqualTo(SyncMergeResult.Source.REMOTE);
    }

    @Test
    public void resolvingAConflictRetiresItForEveryOtherDevice() throws Exception {
        MemoryStore deviceA = new MemoryStore(note(NOTE_ID, T10, "written on A"));
        sync(deviceA);
        MemoryStore deviceB = new MemoryStore(note(NOTE_ID, T20, "written on B"));
        sync(deviceB);
        assertThat(deviceB.conflicts).hasSize(1);

        // The user settles it on B, which records both versions as resolved.
        deviceB.resolved.add(deviceB.conflicts.get(0).getWinnerVersionId());
        deviceB.resolved.add(deviceB.conflicts.get(0).getLoserVersionId());
        deviceB.conflicts.clear();
        sync(deviceB);

        MemoryStore deviceD = new MemoryStore();
        sync(deviceD);

        assertThat(deviceD.snapshot.find(SyncRecord.Type.NOTE, NOTE_ID)).isNotNull();
        assertThat(deviceD.conflicts).isEmpty();
    }

    @Test
    public void anUnresolvedAlternativeSurvivesSeveralUnrelatedPublishes() throws Exception {
        MemoryStore deviceA = new MemoryStore(note(NOTE_ID, T10, "written on A"));
        sync(deviceA);
        MemoryStore deviceB = new MemoryStore(note(NOTE_ID, T20, "written on B"));
        sync(deviceB);

        // Three more publishes, each adding a note of its own so nothing else conflicts.
        for (int round = 0; round < 3; round++) {
            MemoryStore other =
                    new MemoryStore(
                            note(
                                    "6ba7b810-9dad-11d1-80b4-00c04fd4300" + round,
                                    T20.plusSeconds(round + 1),
                                    "unrelated " + round));
            SyncState roundState = sync(other);
            assertThat(roundState.getErrorMessage()).isNull();
            assertThat(roundState.getStatus()).isEqualTo(SyncState.Status.SUCCESS);
        }

        MemoryStore deviceD = new MemoryStore();
        sync(deviceD);

        assertThat(deviceD.conflicts).hasSize(1);
        assertThat(deviceD.conflicts.get(0).getLoser().getPayload().get("value").getAsString())
                .isEqualTo("written on A");
    }

    @Test
    public void publishingWithoutAPrecedingReadIsRefused() throws Exception {
        GoogleDriveSyncBackend backend = backend();

        try {
            // A context that no read of this backend produced: the token is empty.
            backend.publish(
                    new SyncPublication(
                            snapshot(NOTE_ID, null),
                            Collections.emptyList(),
                            Collections.emptySet(),
                            RemoteSnapshot.of(snapshot(NOTE_ID, null))));
            throw new AssertionError("Expected a publish with no read context to be refused");
        } catch (IOException expected) {
            assertThat(expected).hasMessageThat().contains("latest remote read");
        }
    }

    @Test
    public void publishingWithAStaleReadContextIsRefused() throws Exception {
        GoogleDriveSyncBackend backend = backend();
        RemoteSnapshot stale = backend.readSnapshotResult();
        // Something else reads through the same backend, so the earlier context is no longer
        // the one describing remote state.
        backend.readSnapshotResult();

        try {
            backend.publish(
                    new SyncPublication(
                            snapshot(NOTE_ID, null),
                            Collections.emptyList(),
                            Collections.emptySet(),
                            stale));
            throw new AssertionError("Expected a stale read context to be refused");
        } catch (IOException expected) {
            assertThat(expected).hasMessageThat().contains("latest remote read");
        }
    }

    @Test
    public void aDeletedAncestorBundleDoesNotBreakSyncOrLoseAnAlternative() throws Exception {
        MemoryStore deviceA = new MemoryStore(note(NOTE_ID, T10, "written on A"));
        sync(deviceA);
        MemoryStore deviceB = new MemoryStore(note(NOTE_ID, T20, "written on B"));
        sync(deviceB);
        assertThat(server.bundleCount()).isEqualTo(2);

        // The oldest bundle is now only an ancestor: its content lives on in the descendant.
        assertThat(server.deleteOldestBundle()).isTrue();

        MemoryStore deviceD = new MemoryStore();
        SyncState state = sync(deviceD);

        assertThat(state.getErrorMessage()).isNull();
        assertThat(state.getStatus()).isEqualTo(SyncState.Status.SUCCESS);
        assertThat(deviceD.snapshot.find(SyncRecord.Type.NOTE, NOTE_ID)).isNotNull();
        // The losing version travels in the descendant, so removing the ancestor loses nothing.
        assertThat(deviceD.conflicts).hasSize(1);
        assertThat(deviceD.conflicts.get(0).getLoser().getPayload().get("value").getAsString())
                .isEqualTo("written on A");
    }

    // ------------------------------------------------- transport failure classification

    @Test
    public void mayHaveCommitted_treatsAReadTimeoutAsAmbiguous() {
        // SocketTimeoutException extends InterruptedIOException, and the old classifier asked
        // about the parent first, so a timeout waiting for the response of an upload that had
        // already landed failed the sync instead of being confirmed by discovery.
        assertThat(GoogleDriveSyncBackend.mayHaveCommitted(new java.net.SocketTimeoutException()))
                .isTrue();
        assertThat(GoogleDriveSyncBackend.mayHaveCommitted(new java.io.InterruptedIOException()))
                .isFalse();
    }

    @Test
    public void mayHaveCommitted_agreesWithTheRetryPolicyAboutTransientStatuses() {
        // One answer for bundle and attachment uploads: a 429 used to be rediscovered for the
        // bundle POST and rethrown for the attachment POST.
        assertThat(GoogleDriveSyncBackend.mayHaveCommitted(http(429, ""))).isTrue();
        assertThat(GoogleDriveSyncBackend.mayHaveCommitted(http(503, ""))).isTrue();
        assertThat(GoogleDriveSyncBackend.mayHaveCommitted(http(403, "rateLimitExceeded")))
                .isTrue();
        assertThat(GoogleDriveSyncBackend.mayHaveCommitted(http(403, "forbidden"))).isFalse();
        assertThat(GoogleDriveSyncBackend.mayHaveCommitted(http(401, ""))).isFalse();
        assertThat(GoogleDriveSyncBackend.mayHaveCommitted(http(400, ""))).isFalse();
        assertThat(
                        GoogleDriveSyncBackend.mayHaveCommitted(
                                new AttachmentIntegrityException("checksum")))
                .isFalse();
    }

    private static IOException http(int status, String detail) {
        return new DriveRequestExecutor.DriveHttpException(status, null, detail);
    }

    // ------------------------------------------------- bundle history

    @Test
    public void validateAncestry_walksALongLinearHistoryWithoutOverflowingTheStack()
            throws Exception {
        // An account that syncs after every edit builds exactly this shape. The recursive walk
        // used one frame per ancestor and a StackOverflowError is not an IOException the sync
        // knows how to report.
        Map<String, List<String>> parents = new LinkedHashMap<>();
        String previous = null;
        for (int index = 0; index < 200_000; index++) {
            String id = "bundle-" + index;
            parents.put(id, previous == null ? Collections.emptyList() : List.of(previous));
            previous = id;
        }
        Throwable[] failure = new Throwable[1];
        Thread small =
                new Thread(
                        null,
                        () -> {
                            try {
                                GoogleDriveSyncBackend.validateAncestry(parents);
                            } catch (Throwable error) {
                                failure[0] = error;
                            }
                        },
                        "small-stack",
                        256L * 1024L);
        small.start();
        small.join(30_000L);

        assertThat(small.isAlive()).isFalse();
        assertThat(failure[0]).isNull();
    }

    @Test
    public void validateAncestry_stillRejectsACycle() {
        Map<String, List<String>> parents = new LinkedHashMap<>();
        parents.put("a", List.of("b"));
        parents.put("b", List.of("c"));
        parents.put("c", List.of("a", "missing"));

        try {
            GoogleDriveSyncBackend.validateAncestry(parents);
            throw new AssertionError("Expected the cycle to be refused");
        } catch (IOException expected) {
            assertThat(expected).hasMessageThat().contains("cycle");
        }
    }

    @Test
    public void publish_namesTheFrontierOfTheReadItQuotesAsParents() throws Exception {
        SyncBundleCodec codec = new SyncBundleCodec();
        byte[] base = codec.encode(snapshotWithTitle("Base"), CLOCK.instant());
        String baseId = codec.decode(new ByteArrayInputStream(base)).getBundleId();
        server.seedOwnedBundleBytes(base);
        GoogleDriveSyncBackend backend = backend();
        RemoteSnapshot context = backend.readSnapshotResult();

        backend.publish(
                new SyncPublication(
                        snapshotWithTitle("Next"),
                        Collections.emptyList(),
                        Collections.emptySet(),
                        context));

        // The parents come from the quoted read, not from a second copy of its frontier kept on
        // the backend that had to be kept in step by hand.
        assertThat(context.getFrontierBundleIds()).containsExactly(baseId);
        assertThat(
                        codec.decode(new ByteArrayInputStream(server.newestBundleBytes()))
                                .getParentBundleIds())
                .containsExactly(baseId);
    }

    @Test
    public void publish_retiresABundleOnlyOnceItHasBeenSupersededForTheWholeGrace()
            throws Exception {
        // Nothing ever deleted a bundle, so every sync downloaded and decoded the whole history
        // to find one or two heads. A bundle is marked the first time a read finds it outside
        // the frontier; Drive dates the mark, and the grace runs from there — not from the
        // bundle's creation. A head created days ago and superseded seconds ago is exactly the
        // file another device is most likely to be reading.
        SyncBundleCodec codec = new SyncBundleCodec();
        byte[] base = codec.encode(snapshotWithTitle("Base"), CLOCK.instant());
        String baseId = codec.decode(new ByteArrayInputStream(base)).getBundleId();
        byte[] first =
                codec.encode(
                        snapshotWithTitle("First"), CLOCK.instant(), Collections.singleton(baseId));
        String firstId = codec.decode(new ByteArrayInputStream(first)).getBundleId();
        byte[] second =
                codec.encode(
                        snapshotWithTitle("Second"),
                        CLOCK.instant(),
                        Collections.singleton(firstId));
        server.seedOwnedBundleBytes(base);
        server.seedOwnedBundleBytes(first);
        server.seedOwnedBundleBytes(second);
        // Created long ago; superseded only as far as this sync can tell.
        server.ageBundles(3L * GoogleDriveSyncBackend.BUNDLE_PRUNE_GRACE_MILLIS);

        publish(backend(), snapshotWithTitle("Third"));

        // Old by creation, but their supersession was only just recorded: nothing goes yet.
        assertThat(server.deletedFileIds()).isEmpty();
        assertThat(server.supersededBundleCount()).isEqualTo(2);
        assertThat(server.bundleCount()).isEqualTo(4);

        // Two hours on, by Drive's clock and this device's alike.
        server.advanceClock(2L * GoogleDriveSyncBackend.BUNDLE_PRUNE_GRACE_MILLIS);
        GoogleDriveSyncBackend later =
                new GoogleDriveSyncBackend(
                        "token",
                        server.apiBase(),
                        server.uploadBase(),
                        Clock.offset(CLOCK, java.time.Duration.ofHours(2)),
                        new SyncBundleCodec());
        publish(later, snapshotWithTitle("Fourth"));

        // base and first were marked two hours ago and go; second was superseded by Third and
        // is only marked now; Third is the head this publish descends from.
        assertThat(server.deletedFileIds()).hasSize(2);
        assertThat(server.bundleCount()).isEqualTo(3);
        assertThat(
                        backend()
                                .readSnapshot()
                                .find(SyncRecord.Type.NOTE, NOTE_ID)
                                .getPayload()
                                .get("title")
                                .getAsString())
                .isEqualTo("Fourth");
    }

    @Test
    public void publish_neverPrunesABundleWhoseSupersessionDriveDoesNotDate() throws Exception {
        SyncBundleCodec codec = new SyncBundleCodec();
        byte[] base = codec.encode(snapshotWithTitle("Base"), CLOCK.instant());
        String baseId = codec.decode(new ByteArrayInputStream(base)).getBundleId();
        byte[] head =
                codec.encode(
                        snapshotWithTitle("Head"), CLOCK.instant(), Collections.singleton(baseId));
        server.seedOwnedBundleBytes(base);
        server.seedOwnedBundleBytes(head);
        server.withholdModifiedTime();
        publish(backend(), snapshotWithTitle("Next"));
        server.advanceClock(2L * GoogleDriveSyncBackend.BUNDLE_PRUNE_GRACE_MILLIS);

        publish(
                new GoogleDriveSyncBackend(
                        "token",
                        server.apiBase(),
                        server.uploadBase(),
                        Clock.offset(CLOCK, java.time.Duration.ofHours(2)),
                        new SyncBundleCodec()),
                snapshotWithTitle("Later"));

        // Marked, but Drive reports no time for the mark: nothing can be proven old enough.
        assertThat(server.deletedFileIds()).isEmpty();
        assertThat(server.bundleCount()).isEqualTo(4);
    }

    // ------------------------------------------------- attachment transfer cost

    @Test
    public void readAttachment_downloadsTheBlobOnce() throws Exception {
        byte[] bytes = "photo".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        backend().writeAttachment(hash, bytes.length, new ByteArrayInputStream(bytes));

        try (java.io.InputStream stream = backend().readAttachment(hash)) {
            assertThat(readAll(stream)).isEqualTo(bytes);
        }

        // It used to be downloaded in full to pick a verified candidate and then downloaded
        // again to hand over; every caller verifies the stream it receives anyway.
        assertThat(server.mediaReadsOfAttachment(hash)).isEqualTo(1);
    }

    @Test
    public void hasVerifiedAttachment_trustsDrivesOwnChecksumWithoutDownloading() throws Exception {
        byte[] bytes = "photo".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        backend().writeAttachment(hash, bytes.length, new ByteArrayInputStream(bytes));
        byte[] wrong = "wrong bytes".getBytes(StandardCharsets.UTF_8);
        String claimed = sha256("something else".getBytes(StandardCharsets.UTF_8));
        server.seedCorruptAttachment(claimed, wrong);

        GoogleDriveSyncBackend backend = backend();

        // Every attachment in the account used to be re-downloaded on every sync just to answer
        // this; Drive computes the digest over the stored bytes, so a mismatch shows in the
        // listing as well.
        assertThat(backend.hasVerifiedAttachment(hash, (long) bytes.length)).isTrue();
        assertThat(backend.hasVerifiedAttachment(claimed, (long) wrong.length)).isFalse();
        assertThat(server.mediaReadsOfAttachment(hash)).isEqualTo(0);
        assertThat(server.mediaReadsOfAttachment(claimed)).isEqualTo(0);
    }

    @Test
    public void hasVerifiedAttachment_readsTheBlobWhenTheListingCannotConfirmItsSize()
            throws Exception {
        // Drive's digest matches but the listing carries no usable size. Treating that as corrupt
        // reported a good blob absent, so the service uploaded a duplicate on every sync and then
        // failed anyway when the duplicate listed the same way.
        byte[] bytes = "photo".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        backend().writeAttachment(hash, bytes.length, new ByteArrayInputStream(bytes));
        server.withholdSizes();
        GoogleDriveSyncBackend backend = backend();

        assertThat(backend.hasVerifiedAttachment(hash, (long) bytes.length)).isTrue();

        assertThat(server.mediaReadsOfAttachment(hash)).isEqualTo(1);
        assertThat(server.ownedAttachmentCount(hash)).isEqualTo(1);
    }

    @Test
    public void oneSyncListsTheRootFoldersOnce() throws Exception {
        byte[] bytes = "photo".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        backend().writeAttachment(hash, bytes.length, new ByteArrayInputStream(bytes));
        GoogleDriveSyncBackend backend = backend();
        int before = server.folderListings();

        backend.readSnapshotResult();
        backend.hasAttachment(hash);
        backend.hasVerifiedAttachment(hash, (long) bytes.length);
        try (java.io.InputStream stream = backend.readAttachment(hash)) {
            readAll(stream);
        }

        // Each of those used to list the roots again; with N attachments that was several times
        // N listings per sync before a byte moved, which is what Drive rate-limited.
        assertThat(server.folderListings() - before).isEqualTo(1);
    }

    @Test
    public void anOversizedCandidateIsSkippedRatherThanFailingTheSync() throws Exception {
        // A Drive object larger than the ceiling and tagged with the expected hash: the ceiling
        // used to throw a plain IOException from the stream, past the "skip a corrupt candidate"
        // path, so one bad object failed every sync. Drive is asked to withhold its checksum so
        // the bytes have to be read, which is what the ceiling guards.
        byte[] good = "good".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(good);
        server.seedCorruptAttachment(hash, new byte[64]);
        server.withholdChecksums();
        GoogleDriveSyncBackend backend =
                new GoogleDriveSyncBackend(
                        "token",
                        server.apiBase(),
                        server.uploadBase(),
                        CLOCK,
                        new SyncBundleCodec(),
                        16L);

        assertThat(backend.hasVerifiedAttachment(hash, (long) good.length)).isFalse();
        backend.writeAttachment(hash, good.length, new ByteArrayInputStream(good));

        assertThat(server.ownedAttachmentCount(hash)).isEqualTo(2);
        try (java.io.InputStream restored = backend.readAttachment(hash)) {
            assertThat(readAll(restored)).isEqualTo(good);
        }
    }

    @Test
    public void writeAttachment_withAnUnknownSizeBuffersWithinTheCeilingAndUploads()
            throws Exception {
        byte[] bytes = "size unknown".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);

        backend().writeAttachment(hash, -1L, new ByteArrayInputStream(bytes));

        assertThat(server.attachmentContent(hash)).isEqualTo(bytes);
    }

    private static final java.time.Instant T10 = java.time.Instant.parse("2026-08-31T12:00:10Z");
    private static final java.time.Instant T20 = java.time.Instant.parse("2026-08-31T12:00:20Z");

    private SyncState sync(MemoryStore store) {
        return new SyncService(store, new SyncMerger(), CLOCK).sync(backend());
    }

    private static SyncRecord note(String id, java.time.Instant updatedAt, String value) {
        JsonObject payload = new JsonObject();
        payload.addProperty("title", "Shopping");
        payload.addProperty("value", value);
        return SyncRecord.live(SyncRecord.Type.NOTE, id, updatedAt, payload);
    }

    /** One device's durable state: its records, its conflict queue and its settled versions. */
    private static final class MemoryStore implements SyncStore {
        private SyncSnapshot snapshot;
        private final List<SyncMergeResult.Conflict> conflicts = new ArrayList<>();
        private final java.util.Set<String> resolved = new java.util.LinkedHashSet<>();
        private SyncState state = SyncState.idle();

        MemoryStore(SyncRecord... records) {
            snapshot = new SyncSnapshot(Arrays.asList(records));
        }

        @Override
        public SyncSnapshot readSnapshot() {
            return snapshot;
        }

        @Override
        public void applySnapshot(SyncSnapshot snapshot, List<SyncMergeResult.Conflict> conflicts) {
            this.snapshot = snapshot;
            for (SyncMergeResult.Conflict conflict : conflicts) {
                if (!resolved.contains(conflict.getLoserVersionId())) {
                    this.conflicts.add(conflict);
                }
            }
        }

        @Override
        public java.util.Set<String> getResolvedAlternativeIds() {
            return resolved;
        }

        @Override
        public java.util.Collection<String> getAttachmentHashes(SyncSnapshot snapshot) {
            return Collections.emptyList();
        }

        @Override
        public boolean hasAttachment(String sha256) {
            return false;
        }

        @Override
        public java.io.InputStream readAttachment(String sha256) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void writeAttachment(String sha256, long sizeBytes, java.io.InputStream content) {}

        @Override
        public SyncState readState() {
            return state;
        }

        @Override
        public void writeState(SyncState state) {
            this.state = state;
        }
    }

    /**
     * Publishes the way {@code SyncService} does: read first, then publish quoting that read.
     *
     * <p>{@code writeSnapshot} on its own is refused now, because taking causal parents from a
     * mutable field let a write with no preceding read fork the bundle DAG permanently.
     */
    private static void publish(GoogleDriveSyncBackend backend, SyncSnapshot snapshot)
            throws IOException {
        RemoteSnapshot context = backend.readSnapshotResult();
        backend.publish(
                new SyncPublication(
                        snapshot, Collections.emptyList(), Collections.emptySet(), context));
    }

    private GoogleDriveSyncBackend backend() {
        return new GoogleDriveSyncBackend(
                "token", server.apiBase(), server.uploadBase(), CLOCK, new SyncBundleCodec());
    }

    private static void publishUnchecked(
            GoogleDriveSyncBackend backend, SyncSnapshot snapshot, RemoteSnapshot context) {
        try {
            backend.publish(
                    new SyncPublication(
                            snapshot, Collections.emptyList(), Collections.emptySet(), context));
        } catch (IOException error) {
            throw new AssertionError(error);
        }
    }

    @Test
    public void readSnapshot_ignoresUnownedFiles() throws Exception {
        server.seedUnownedBundle(
                snapshot("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        GoogleDriveSyncBackend backend =
                new GoogleDriveSyncBackend(
                        "token",
                        server.apiBase(),
                        server.uploadBase(),
                        CLOCK,
                        new SyncBundleCodec());

        assertThat(backend.readSnapshot().getRecords()).isEmpty();
    }

    @Test
    public void readSnapshot_mergesEveryOwnedRootAfterAFirstSyncRace() throws Exception {
        String firstHash = server.registerAttachment("first".getBytes(StandardCharsets.UTF_8));
        String secondHash = server.registerAttachment("other".getBytes(StandardCharsets.UTF_8));
        // These are the durable results of two devices that both listed Drive before either
        // created its root folder. Choosing only the lowest folder ID would lose note B forever.
        server.seedOwnedBundle(snapshot(NOTE_ID, firstHash));
        server.seedOwnedBundle(snapshot(SECOND_NOTE_ID, secondHash));
        GoogleDriveSyncBackend backend =
                new GoogleDriveSyncBackend(
                        "token",
                        server.apiBase(),
                        server.uploadBase(),
                        CLOCK,
                        new SyncBundleCodec());

        SyncSnapshot merged = backend.readSnapshot();

        assertThat(server.ownedFolderCount()).isEqualTo(2);
        assertThat(merged.find(SyncRecord.Type.NOTE, NOTE_ID)).isNotNull();
        assertThat(merged.find(SyncRecord.Type.NOTE, SECOND_NOTE_ID)).isNotNull();
    }

    @Test
    public void writeSnapshot_copiesDuplicateRootAttachmentsIntoTheCanonicalRoot()
            throws Exception {
        String firstHash = server.registerAttachment("first".getBytes(StandardCharsets.UTF_8));
        String secondHash = server.registerAttachment("other".getBytes(StandardCharsets.UTF_8));
        server.seedOwnedBundle(snapshot(NOTE_ID, firstHash));
        server.seedOwnedBundle(snapshot(SECOND_NOTE_ID, secondHash));
        GoogleDriveSyncBackend backend =
                new GoogleDriveSyncBackend(
                        "token",
                        server.apiBase(),
                        server.uploadBase(),
                        CLOCK,
                        new SyncBundleCodec());

        SyncSnapshot merged = backend.readSnapshot();
        publish(backend, merged);

        assertThat(server.ownedAttachmentCountInCanonicalRoot(firstHash)).isEqualTo(1);
        assertThat(server.ownedAttachmentCountInCanonicalRoot(secondHash)).isEqualTo(1);
        SyncSnapshot reread =
                new GoogleDriveSyncBackend(
                                "token",
                                server.apiBase(),
                                server.uploadBase(),
                                CLOCK,
                                new SyncBundleCodec())
                        .readSnapshot();
        assertThat(reread.find(SyncRecord.Type.NOTE, NOTE_ID)).isNotNull();
        assertThat(reread.find(SyncRecord.Type.NOTE, SECOND_NOTE_ID)).isNotNull();
    }

    @Test
    public void writeSnapshot_createsNewBundleWhenLegacyBundleChanges() throws Exception {
        server.seedOwnedBundle(snapshot(NOTE_ID, null));
        GoogleDriveSyncBackend backend =
                new GoogleDriveSyncBackend(
                        "token",
                        server.apiBase(),
                        server.uploadBase(),
                        CLOCK,
                        new SyncBundleCodec());

        assertThat(backend.readSnapshot().find(SyncRecord.Type.NOTE, NOTE_ID)).isNotNull();
        server.forceConcurrentBundleUpdate(
                snapshot("cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"));

        publish(backend, snapshot(NOTE_ID, null));

        assertThat(server.bundleCount()).isEqualTo(3);
    }

    @Test
    public void writeSnapshot_preservesUpdateThatArrivesBetweenReadAndPublish() throws Exception {
        server.seedOwnedBundle(snapshot(NOTE_ID, null));
        GoogleDriveSyncBackend backend =
                new GoogleDriveSyncBackend(
                        "token",
                        server.apiBase(),
                        server.uploadBase(),
                        CLOCK,
                        new SyncBundleCodec());

        backend.readSnapshot();
        server.updateBundleImmediatelyBeforeNextUpload(snapshot(SECOND_NOTE_ID, null));
        publish(backend, snapshot(NOTE_ID, null));

        SyncSnapshot remote =
                new GoogleDriveSyncBackend(
                                "token",
                                server.apiBase(),
                                server.uploadBase(),
                                CLOCK,
                                new SyncBundleCodec())
                        .readSnapshot();
        assertThat(remote.find(SyncRecord.Type.NOTE, NOTE_ID)).isNotNull();
        assertThat(remote.find(SyncRecord.Type.NOTE, SECOND_NOTE_ID)).isNotNull();
    }

    private static SyncSnapshot snapshot(String hash) throws IOException {
        return snapshot(NOTE_ID, hash);
    }

    private static SyncSnapshot snapshotWithTitle(String title) {
        JsonObject note = new JsonObject();
        note.addProperty("title", title);
        note.addProperty("value", "body");
        return new SyncSnapshot(
                Collections.singletonList(
                        SyncRecord.live(SyncRecord.Type.NOTE, NOTE_ID, CLOCK.instant(), note)));
    }

    private static SyncSnapshot snapshot(String noteId, String hash) throws IOException {
        JsonObject note = new JsonObject();
        note.addProperty("title", "Shopping");
        note.addProperty("value", "Milk");
        JsonArray hashes = new JsonArray();
        if (hash != null) hashes.add(hash);
        note.add("attachmentHashes", hashes);
        JsonArray manifest = new JsonArray();
        if (hash != null) {
            manifest.add(
                    new SyncBundleCodec.AttachmentManifestEntry(
                                    UUID.nameUUIDFromBytes(hash.getBytes(StandardCharsets.UTF_8))
                                            .toString(),
                                    hash,
                                    "image/png",
                                    5L,
                                    "attachments/" + hash,
                                    "photo.png")
                            .toJson(true));
        }
        note.add("attachmentsManifest", manifest);
        return new SyncSnapshot(
                Collections.singletonList(
                        SyncRecord.live(
                                SyncRecord.Type.NOTE,
                                noteId,
                                Instant.parse("2026-08-31T12:00:00Z"),
                                note)));
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder value = new StringBuilder(digest.length * 2);
        for (byte byteValue : digest) {
            value.append(String.format("%02x", byteValue & 0xff));
        }
        return value.toString();
    }

    private static byte[] readAll(java.io.InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static final class FakeDriveServer implements AutoCloseable {
        private static final Pattern PARENT_PATTERN = Pattern.compile("'([^']+)' in parents");
        private static final Pattern APP_PROPERTY_PATTERN =
                Pattern.compile("appProperties has \\{ key='([^']+)' and value='([^']+)' \\}");

        private final ServerSocket serverSocket;
        private final Thread thread;
        private final Map<String, DriveFile> files = new ConcurrentHashMap<>();
        private final Map<String, byte[]> seededAttachmentContent = new LinkedHashMap<>();
        private final Map<String, UploadSession> uploadSessions = new ConcurrentHashMap<>();
        private final java.util.Queue<ChunkScript> scriptedChunks =
                new java.util.concurrent.ConcurrentLinkedQueue<>();
        private final List<String> rejectedChunkRanges =
                java.util.Collections.synchronizedList(new ArrayList<>());
        private volatile boolean running = true;
        private volatile CyclicBarrier emptyRootListingBarrier;
        private final AtomicInteger folderListings = new AtomicInteger();
        private final Map<String, Integer> mediaReads = new ConcurrentHashMap<>();
        private final List<String> deletedFileIds =
                java.util.Collections.synchronizedList(new ArrayList<>());
        private volatile boolean withholdChecksums;
        private volatile boolean withholdSizes;
        private volatile boolean withholdModifiedTime;

        /** Drive's clock, as the fake stamps files with it; starts at the tests' fixed CLOCK. */
        private volatile long serverNowMillis = CLOCK.millis();

        private SyncSnapshot updateBeforeNextPatch;
        private final AtomicInteger nextId = new AtomicInteger(1);

        FakeDriveServer() throws IOException {
            serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            thread =
                    new Thread(
                            () -> {
                                while (running) {
                                    try {
                                        Socket socket = serverSocket.accept();
                                        new Thread(
                                                        () -> {
                                                            try {
                                                                handle(socket);
                                                            } catch (IOException ignored) {
                                                                // Individual test connection
                                                                // failed.
                                                            }
                                                        })
                                                .start();
                                    } catch (IOException ignored) {
                                        if (running) {
                                            // Keep the fake server lightweight for tests.
                                        }
                                    }
                                }
                            },
                            "fake-drive-server");
            thread.start();
        }

        String apiBase() {
            return "http://127.0.0.1:" + serverSocket.getLocalPort() + "/drive/v3";
        }

        String uploadBase() {
            return "http://127.0.0.1:" + serverSocket.getLocalPort() + "/upload/drive/v3/files";
        }

        /** Commits only the first {@code bytes} of the next chunk, then reports real progress. */
        void acceptOnlyNextChunkBytes(int bytes) {
            scriptedChunks.add(ChunkScript.partial(bytes));
        }

        /** Answers the next chunk with an HTTP status and commits nothing. */
        void failNextChunk(int status) {
            scriptedChunks.add(ChunkScript.status(status));
        }

        /** Closes the socket mid-chunk without writing a response. */
        void dropNextChunkConnection() {
            scriptedChunks.add(ChunkScript.drop());
        }

        /** Answers the next chunk with a 308 carrying a fabricated acknowledged range. */
        void reportNextChunkRangeEnd(long inclusiveEnd) {
            scriptedChunks.add(ChunkScript.forcedRange(inclusiveEnd));
        }

        /** Content-Range values the server refused because they did not continue the upload. */
        List<String> rejectedChunkRanges() {
            return new ArrayList<>(rejectedChunkRanges);
        }

        /** How many times the folder index was listed. */
        int folderListings() {
            return folderListings.get();
        }

        /** How many times the bytes of the blob carrying {@code sha256} were downloaded. */
        int mediaReadsOfAttachment(String sha256) {
            int total = 0;
            for (DriveFile file : files.values()) {
                if (sha256.equals(file.appProperties.get("mynotesAttachmentSha256"))) {
                    total += mediaReads.getOrDefault(file.id, 0);
                }
            }
            return total;
        }

        /** Ids of files the client deleted. */
        List<String> deletedFileIds() {
            return new ArrayList<>(deletedFileIds);
        }

        /** Moves every stored bundle's Drive-side timestamps {@code millis} into the past. */
        void ageBundles(long millis) {
            for (DriveFile file : files.values()) {
                if ("1".equals(file.appProperties.get("mynotesBundle"))) {
                    file.createdAtMillis -= millis;
                    file.modifiedAtMillis -= millis;
                }
            }
        }

        /** Committed bytes of the attachment blob carrying {@code sha256}, or null. */
        byte[] attachmentContent(String sha256) {
            for (DriveFile file : files.values()) {
                if (sha256.equals(file.appProperties.get("mynotesAttachmentSha256"))) {
                    return file.content;
                }
            }
            return null;
        }

        void pauseTheNextTwoEmptyRootListings() {
            emptyRootListingBarrier = new CyclicBarrier(2);
        }

        int ownedFolderCount() {
            int count = 0;
            for (DriveFile file : files.values()) {
                if ("application/vnd.google-apps.folder".equals(file.mimeType)
                        && "1".equals(file.appProperties.get("mynotesOwner"))) {
                    count++;
                }
            }
            return count;
        }

        /** Removes one stored bundle, the way a user tidying Drive or its trash purge would. */
        boolean deleteOldestBundle() {
            String oldest = null;
            for (Map.Entry<String, DriveFile> entry : files.entrySet()) {
                if (!"1".equals(entry.getValue().appProperties.get("mynotesBundle"))) continue;
                if (oldest == null || entry.getKey().compareTo(oldest) < 0) {
                    oldest = entry.getKey();
                }
            }
            return oldest != null && files.remove(oldest) != null;
        }

        int bundleCount() {
            int count = 0;
            for (DriveFile file : files.values()) {
                if ("1".equals(file.appProperties.get("mynotesBundle"))) {
                    count++;
                }
            }
            return count;
        }

        int ownedAttachmentCount(String hash) {
            int count = 0;
            for (DriveFile file : files.values()) {
                if (hash.equals(file.appProperties.get("mynotesAttachmentSha256"))) {
                    count++;
                }
            }
            return count;
        }

        int ownedAttachmentCountInCanonicalRoot(String hash) {
            String canonical = null;
            for (DriveFile file : files.values()) {
                if ("application/vnd.google-apps.folder".equals(file.mimeType)
                        && "1".equals(file.appProperties.get("mynotesOwner"))
                        && (canonical == null || file.id.compareTo(canonical) < 0)) {
                    canonical = file.id;
                }
            }
            int count = 0;
            for (DriveFile file : files.values()) {
                if (hash.equals(file.appProperties.get("mynotesAttachmentSha256"))
                        && file.parents.contains(canonical)) {
                    count++;
                }
            }
            return count;
        }

        byte[] readAttachment(String hash) {
            for (DriveFile file : files.values()) {
                if (hash.equals(file.appProperties.get("mynotesAttachmentSha256"))) {
                    return file.content;
                }
            }
            return null;
        }

        byte[] readBundleBytes() {
            for (DriveFile file : files.values()) {
                if ("1".equals(file.appProperties.get("mynotesBundle"))) {
                    return file.content;
                }
            }
            return null;
        }

        /** The most recently created bundle file's bytes. */
        byte[] newestBundleBytes() {
            DriveFile newest = null;
            for (DriveFile file : files.values()) {
                if ("1".equals(file.appProperties.get("mynotesBundle"))
                        && (newest == null
                                || Integer.parseInt(file.id) > Integer.parseInt(newest.id))) {
                    newest = file;
                }
            }
            return newest == null ? null : newest.content;
        }

        /** Models objects Drive has not (yet) checksummed: listings carry no digest or size. */
        void withholdChecksums() {
            withholdChecksums = true;
        }

        /** Models a listing that reports no modification time for its files. */
        void withholdModifiedTime() {
            withholdModifiedTime = true;
        }

        /** Models objects whose listing carries a checksum but no usable size. */
        void withholdSizes() {
            withholdSizes = true;
        }

        /** Moves Drive's clock forward; later stamps are dated from the new time. */
        void advanceClock(long millis) {
            serverNowMillis += millis;
        }

        /** Whether the client has marked the newest {@code count} bundles as superseded. */
        int supersededBundleCount() {
            int count = 0;
            for (DriveFile file : files.values()) {
                if ("1".equals(file.appProperties.get("mynotesBundle"))
                        && file.appProperties.containsKey("mynotesBundleSuperseded")) {
                    count++;
                }
            }
            return count;
        }

        String registerAttachment(byte[] bytes) throws Exception {
            String hash = sha256(bytes);
            seededAttachmentContent.put(hash, bytes);
            return hash;
        }

        void seedCorruptAttachment(String claimedHash, byte[] bytes) {
            DriveFile folder =
                    createFile("MyNotes Sync", "application/vnd.google-apps.folder", null);
            folder.appProperties.put("mynotesOwner", "1");
            DriveFile blob = createFile(claimedHash, "application/octet-stream", folder.id);
            blob.appProperties.put("mynotesAttachmentSha256", claimedHash);
            blob.content = bytes;
        }

        void seedOwnedBundle(SyncSnapshot snapshot) throws IOException {
            DriveFile folder =
                    createFile("MyNotes Sync", "application/vnd.google-apps.folder", null);
            folder.appProperties.put("mynotesOwner", "1");
            DriveFile bundle = createFile("MyNotes.sync.v1.zip", "application/zip", folder.id);
            bundle.appProperties.put("mynotesBundle", "1");
            bundle.content = new SyncBundleCodec().encode(snapshot, CLOCK.instant());
            for (SyncRecord record : snapshot.getLiveRecords(SyncRecord.Type.NOTE)) {
                JsonArray manifest = record.getPayload().getAsJsonArray("attachmentsManifest");
                if (manifest == null) {
                    continue;
                }
                for (int index = 0; index < manifest.size(); index++) {
                    JsonObject attachment = manifest.get(index).getAsJsonObject();
                    String hash = attachment.get("sha256").getAsString();
                    DriveFile blob = createFile(hash, "application/octet-stream", folder.id);
                    blob.appProperties.put("mynotesAttachmentSha256", hash);
                    blob.content =
                            seededAttachmentContent.getOrDefault(
                                    hash, new byte[attachment.get("size").getAsInt()]);
                }
            }
        }

        void seedOwnedBundleBytes(byte[] bytes) {
            DriveFile folder =
                    createFile("MyNotes Sync", "application/vnd.google-apps.folder", null);
            folder.appProperties.put("mynotesOwner", "1");
            DriveFile bundle = createFile("MyNotes.sync.v1.zip", "application/zip", folder.id);
            bundle.appProperties.put("mynotesBundle", "1");
            bundle.content = bytes;
        }

        void seedUnownedBundle(SyncSnapshot snapshot) throws IOException {
            DriveFile folder = createFile("Elsewhere", "application/vnd.google-apps.folder", null);
            DriveFile bundle = createFile("MyNotes.sync.v1.zip", "application/zip", folder.id);
            bundle.content = new SyncBundleCodec().encode(snapshot, CLOCK.instant());
        }

        void forceConcurrentBundleUpdate(SyncSnapshot snapshot) throws IOException {
            for (DriveFile file : files.values()) {
                if ("1".equals(file.appProperties.get("mynotesBundle"))) {
                    // Bundles are immutable. Model another device's publication as a sibling,
                    // never as replacement of a durable history object.
                    String parent = file.parents.isEmpty() ? null : file.parents.get(0);
                    DriveFile sibling =
                            createFile("MyNotes.sync.v1.zip", "application/zip", parent);
                    sibling.appProperties.put("mynotesBundle", "1");
                    sibling.content = new SyncBundleCodec().encode(snapshot, CLOCK.instant());
                    return;
                }
            }
            throw new IOException("No bundle to update");
        }

        void updateBundleImmediatelyBeforeNextUpload(SyncSnapshot snapshot) {
            updateBeforeNextPatch = snapshot;
        }

        private void handle(Socket socket) throws IOException {
            try (Socket current = socket;
                    BufferedInputStream input = new BufferedInputStream(current.getInputStream());
                    OutputStream output = current.getOutputStream()) {
                Request request = readRequest(input);
                Response response = dispatch(request);
                writeResponse(output, response);
            }
        }

        private Response dispatch(Request request) throws IOException {
            URI uri = URI.create("http://localhost" + request.target);
            String path = uri.getPath();
            if ("/drive/v3/files".equals(path)) {
                if ("GET".equals(request.method)) {
                    return handleList(uri);
                }
                if ("POST".equals(request.method)) {
                    return handleCreateMetadata(request.body);
                }
                return Response.json(405, "{}");
            }
            if (path.startsWith("/drive/v3/files/")) {
                return handleFileRead(request, uri, path.substring("/drive/v3/files/".length()));
            }
            if ("/upload/drive/v3/files".equals(path) && "POST".equals(request.method)) {
                if ("resumable".equals(parseQuery(uri).get("uploadType"))) {
                    return handleResumableInitiation(request);
                }
                return handleUpload(request, null);
            }
            if (path.startsWith("/resumable/") && "PUT".equals(request.method)) {
                return handleResumableChunk(request, path.substring("/resumable/".length()));
            }
            if (path.startsWith("/upload/drive/v3/files/")) {
                return handleUpload(request, path.substring("/upload/drive/v3/files/".length()));
            }
            return Response.json(404, "{}");
        }

        private Response handleList(URI uri) {
            String query = parseQuery(uri).get("q");
            CyclicBarrier barrier = emptyRootListingBarrier;
            if (barrier != null
                    && query != null
                    && query.contains("mynotesOwner")
                    && ownedFolderCount() == 0) {
                try {
                    barrier.await(5L, TimeUnit.SECONDS);
                    emptyRootListingBarrier = null;
                } catch (Exception error) {
                    return Response.json(500, "{}");
                }
            }
            if (query != null
                    && query.contains("mimeType = 'application/vnd.google-apps.folder'")) {
                folderListings.incrementAndGet();
            }
            JsonArray array = new JsonArray();
            for (DriveFile file : files.values()) {
                if (matchesQuery(file, query)) {
                    JsonObject value = new JsonObject();
                    value.addProperty("id", file.id);
                    value.addProperty("name", file.name);
                    // Drive reports these for binary files; the fake computes them from the
                    // stored bytes exactly as Drive does, so a corrupt object is exposed by its
                    // real digest rather than by the property the uploader claimed.
                    if (!withholdChecksums) {
                        if (!withholdSizes) {
                            value.addProperty("size", String.valueOf(file.content.length));
                        }
                        if (!"application/vnd.google-apps.folder".equals(file.mimeType)) {
                            value.addProperty("sha256Checksum", sha256Unchecked(file.content));
                        }
                    }
                    JsonObject appProperties = new JsonObject();
                    for (Map.Entry<String, String> entry : file.appProperties.entrySet()) {
                        appProperties.addProperty(entry.getKey(), entry.getValue());
                    }
                    value.add("appProperties", appProperties);
                    // Drive's own clock, RFC 3339, as the real listing reports it.
                    value.addProperty(
                            "createdTime", Instant.ofEpochMilli(file.createdAtMillis).toString());
                    if (!withholdModifiedTime) {
                        value.addProperty(
                                "modifiedTime",
                                Instant.ofEpochMilli(file.modifiedAtMillis).toString());
                    }
                    array.add(value);
                }
            }
            JsonObject response = new JsonObject();
            response.add("files", array);
            return Response.json(200, response.toString());
        }

        private static String sha256Unchecked(byte[] bytes) {
            try {
                return sha256(bytes);
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        }

        private Response handleCreateMetadata(byte[] body) throws IOException {
            JsonObject metadata = readJson(body);
            DriveFile file =
                    createFile(
                            metadata.get("name").getAsString(),
                            metadata.get("mimeType").getAsString(),
                            null);
            applyMetadata(file, metadata);
            return Response.json(200, fileMetadata(file).toString(), file.eTag());
        }

        private Response handleFileRead(Request request, URI uri, String id) {
            DriveFile file = files.get(id);
            if (file == null) {
                return Response.json(404, "{}");
            }
            if ("DELETE".equals(request.method)) {
                files.remove(id);
                deletedFileIds.add(id);
                return Response.json(204, "");
            }
            if ("POST".equals(request.method)
                    && "PATCH".equals(request.headers.get("x-http-method-override"))) {
                // files.update: merges appProperties and, like Drive, stamps modifiedTime.
                JsonObject patch = readJson(request.body);
                if (patch.has("appProperties")) {
                    for (Map.Entry<String, com.google.gson.JsonElement> entry :
                            patch.getAsJsonObject("appProperties").entrySet()) {
                        file.appProperties.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
                file.modifiedAtMillis = serverNowMillis;
                return Response.json(200, fileMetadata(file).toString(), file.eTag());
            }
            if ("media".equals(parseQuery(uri).get("alt"))) {
                mediaReads.merge(id, 1, Integer::sum);
                return Response.binary(200, file.content, file.eTag());
            }
            return Response.json(200, fileMetadata(file).toString(), file.eTag());
        }

        private Response handleUpload(Request request, String fileId) throws IOException {
            DriveFile existing = fileId == null ? null : files.get(fileId);
            if (fileId != null && existing == null) {
                return Response.json(404, "{}");
            }
            if (updateBeforeNextPatch != null) {
                SyncSnapshot concurrent = updateBeforeNextPatch;
                updateBeforeNextPatch = null;
                forceConcurrentBundleUpdate(concurrent);
            }

            MultipartPayload payload = readMultipart(request);
            DriveFile file =
                    existing == null
                            ? createFile(
                                    payload.metadata.get("name").getAsString(),
                                    payload.metadata.has("mimeType")
                                            ? payload.metadata.get("mimeType").getAsString()
                                            : "application/octet-stream",
                                    firstParent(payload.metadata))
                            : existing;
            file.content = payload.data;
            applyMetadata(file, payload.metadata);
            if (existing != null) {
                file.version++;
            }
            return Response.json(200, fileMetadata(file).toString(), file.eTag());
        }

        private Response handleResumableInitiation(Request request) throws IOException {
            String length = request.headers.get("x-upload-content-length");
            if (length == null) {
                return Response.json(400, "{}");
            }
            String id = "session-" + uploadSessions.size();
            uploadSessions.put(
                    id,
                    new UploadSession(
                            readJson(request.body),
                            Long.parseLong(length),
                            request.headers.get("x-upload-content-type")));
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put(
                    "Location",
                    "http://127.0.0.1:" + serverSocket.getLocalPort() + "/resumable/" + id);
            return Response.json(200, "{}", headers);
        }

        private Response handleResumableChunk(Request request, String sessionId)
                throws IOException {
            UploadSession session = uploadSessions.get(sessionId);
            if (session == null) {
                return Response.json(404, "{}");
            }
            ChunkScript script = scriptedChunks.poll();
            if (script != null && script.dropConnection) {
                throw new IOException("Fake Drive dropped the connection mid-chunk");
            }
            if (script != null && script.status > 0) {
                return Response.json(script.status, "{}");
            }

            String range = request.headers.get("content-range");
            if (range == null) {
                return Response.json(400, "{}");
            }
            if (range.startsWith("bytes */")) {
                return resumableProgress(session);
            }
            Matcher matcher = Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)").matcher(range);
            if (!matcher.matches() || Long.parseLong(matcher.group(3)) != session.totalBytes) {
                rejectedChunkRanges.add(range);
                return Response.json(400, "{}");
            }
            long start = Long.parseLong(matcher.group(1));
            long end = Long.parseLong(matcher.group(2));
            if (start != session.data.size() || end - start + 1L != request.body.length) {
                // The client tried to continue somewhere other than the first unacknowledged
                // byte. Recorded so a test can assert this never happens.
                rejectedChunkRanges.add(range);
                return Response.json(400, "{}");
            }
            if (end + 1L < session.totalBytes && request.body.length % (256 * 1024) != 0) {
                // Drive's rule: every chunk but the last is a multiple of 256 KiB. A partially
                // acknowledged window used to be continued with just its tail, which Drive
                // answers with a 400 nothing retries.
                rejectedChunkRanges.add(range);
                return Response.json(400, "{}");
            }

            if (script != null && script.forcedRangeInclusiveEnd != null) {
                Map<String, String> headers = new LinkedHashMap<>();
                headers.put("Range", "bytes=0-" + script.forcedRangeInclusiveEnd);
                return Response.json(308, "", headers);
            }

            int accepted =
                    script == null || script.acceptBytes < 0
                            ? request.body.length
                            : Math.min(script.acceptBytes, request.body.length);
            session.data.write(request.body, 0, accepted);
            if (session.data.size() < session.totalBytes) {
                return resumableProgress(session);
            }
            DriveFile file =
                    createFile(
                            session.metadata.get("name").getAsString(),
                            session.mimeType == null
                                    ? "application/octet-stream"
                                    : session.mimeType,
                            firstParent(session.metadata));
            file.content = session.data.toByteArray();
            applyMetadata(file, session.metadata);
            uploadSessions.remove(sessionId);
            return Response.json(200, fileMetadata(file).toString(), file.eTag());
        }

        private static Response resumableProgress(UploadSession session) {
            Map<String, String> headers = new LinkedHashMap<>();
            if (session.data.size() > 0) {
                headers.put("Range", "bytes=0-" + (session.data.size() - 1));
            }
            return Response.json(308, "", headers);
        }

        private void applyMetadata(DriveFile file, JsonObject metadata) {
            if (metadata.has("name")) {
                file.name = metadata.get("name").getAsString();
            }
            if (metadata.has("mimeType")) {
                file.mimeType = metadata.get("mimeType").getAsString();
            }
            if (metadata.has("parents")) {
                file.parents.clear();
                JsonArray parents = metadata.getAsJsonArray("parents");
                for (int i = 0; i < parents.size(); i++) {
                    file.parents.add(parents.get(i).getAsString());
                }
            }
            if (metadata.has("appProperties")) {
                file.appProperties.clear();
                JsonObject appProperties = metadata.getAsJsonObject("appProperties");
                for (Map.Entry<String, com.google.gson.JsonElement> entry :
                        appProperties.entrySet()) {
                    file.appProperties.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        }

        private DriveFile createFile(String name, String mimeType, String parentId) {
            DriveFile file =
                    new DriveFile(Integer.toString(nextId.getAndIncrement()), name, mimeType);
            file.createdAtMillis = serverNowMillis;
            file.modifiedAtMillis = serverNowMillis;
            if (parentId != null) {
                file.parents.add(parentId);
            }
            files.put(file.id, file);
            return file;
        }

        private boolean matchesQuery(DriveFile file, String query) {
            if (query == null || query.isEmpty()) {
                return true;
            }
            if (query.contains("mimeType = 'application/vnd.google-apps.folder'")
                    && !"application/vnd.google-apps.folder".equals(file.mimeType)) {
                return false;
            }
            Matcher parent = PARENT_PATTERN.matcher(query);
            if (parent.find() && !file.parents.contains(parent.group(1))) {
                return false;
            }
            Matcher properties = APP_PROPERTY_PATTERN.matcher(query);
            while (properties.find()) {
                if (!properties.group(2).equals(file.appProperties.get(properties.group(1)))) {
                    return false;
                }
            }
            return true;
        }

        private static MultipartPayload readMultipart(Request request) throws IOException {
            String contentType = request.headers.get("content-type");
            int boundaryIndex = contentType.indexOf("boundary=");
            if (boundaryIndex < 0) {
                throw new IOException("Missing multipart boundary");
            }
            String boundary = contentType.substring(boundaryIndex + 9);
            String body = new String(request.body, StandardCharsets.ISO_8859_1);
            String[] segments = body.split("--" + Pattern.quote(boundary));
            List<String> parts = new ArrayList<>();
            for (String segment : segments) {
                if (segment == null || segment.trim().isEmpty() || segment.equals("--")) {
                    continue;
                }
                parts.add(segment);
            }
            if (parts.size() < 2) {
                throw new IOException("Invalid multipart payload");
            }
            return new MultipartPayload(
                    JsonParser.parseString(extractMultipartBody(parts.get(0))).getAsJsonObject(),
                    extractMultipartBody(parts.get(1)).getBytes(StandardCharsets.ISO_8859_1));
        }

        private static String extractMultipartBody(String part) throws IOException {
            int bodyIndex = part.indexOf("\r\n\r\n");
            if (bodyIndex < 0) {
                throw new IOException("Multipart part is malformed");
            }
            String value = part.substring(bodyIndex + 4);
            if (value.endsWith("\r\n")) {
                value = value.substring(0, value.length() - 2);
            }
            if (value.endsWith("--")) {
                value = value.substring(0, value.length() - 2);
            }
            return value;
        }

        private static String firstParent(JsonObject metadata) {
            JsonArray parents = metadata.getAsJsonArray("parents");
            return parents == null || parents.size() == 0 ? null : parents.get(0).getAsString();
        }

        private static JsonObject fileMetadata(DriveFile file) {
            JsonObject response = new JsonObject();
            response.addProperty("id", file.id);
            response.addProperty("name", file.name);
            // Drive v3 reports the change counter as a string-encoded long.
            response.addProperty("version", String.valueOf(file.version));
            JsonObject appProperties = new JsonObject();
            for (Map.Entry<String, String> entry : file.appProperties.entrySet()) {
                appProperties.addProperty(entry.getKey(), entry.getValue());
            }
            response.add("appProperties", appProperties);
            return response;
        }

        private static JsonObject readJson(byte[] bytes) {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }

        private static Map<String, String> parseQuery(URI uri) {
            Map<String, String> values = new LinkedHashMap<>();
            String rawQuery = uri.getRawQuery();
            if (rawQuery == null || rawQuery.isEmpty()) {
                return values;
            }
            for (String pair : rawQuery.split("&")) {
                int separator = pair.indexOf('=');
                if (separator < 0) {
                    values.put(pair, "");
                    continue;
                }
                values.put(
                        URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
            }
            return values;
        }

        private static Request readRequest(BufferedInputStream input) throws IOException {
            String requestLine = readLine(input);
            if (requestLine == null || requestLine.isEmpty()) {
                throw new IOException("Missing request line");
            }
            String[] pieces = requestLine.split(" ");
            Map<String, String> headers = new LinkedHashMap<>();
            String line;
            while ((line = readLine(input)) != null && !line.isEmpty()) {
                int separator = line.indexOf(':');
                if (separator < 0) {
                    continue;
                }
                headers.put(
                        line.substring(0, separator).trim().toLowerCase(),
                        line.substring(separator + 1).trim());
            }
            int contentLength = 0;
            if (headers.containsKey("content-length")) {
                contentLength = Integer.parseInt(headers.get("content-length"));
            }
            byte[] body = new byte[contentLength];
            int offset = 0;
            while (offset < contentLength) {
                int read = input.read(body, offset, contentLength - offset);
                if (read < 0) {
                    throw new IOException("Unexpected end of stream");
                }
                offset += read;
            }
            return new Request(pieces[0], pieces[1], headers, body);
        }

        private static String readLine(BufferedInputStream input) throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int current;
            while ((current = input.read()) != -1) {
                if (current == '\r') {
                    int next = input.read();
                    if (next != '\n' && next != -1) {
                        output.write(next);
                    }
                    break;
                }
                if (current == '\n') {
                    break;
                }
                output.write(current);
            }
            if (current == -1 && output.size() == 0) {
                return null;
            }
            return output.toString(StandardCharsets.ISO_8859_1.name());
        }

        private static void writeResponse(OutputStream output, Response response)
                throws IOException {
            StringBuilder headers = new StringBuilder();
            headers.append("HTTP/1.1 ").append(response.code).append(" OK\r\n");
            headers.append("Content-Length: ").append(response.body.length).append("\r\n");
            headers.append("Connection: close\r\n");
            headers.append("Content-Type: ").append(response.contentType).append("\r\n");
            for (Map.Entry<String, String> header : response.headers.entrySet()) {
                headers.append(header.getKey())
                        .append(": ")
                        .append(header.getValue())
                        .append("\r\n");
            }
            // Deliberately no ETag header. Drive API v3 dropped the ETags that v2 sent; a fake
            // that returns one lets code depending on the header pass its tests and fail against
            // the real API, which is exactly what happened before.
            headers.append("\r\n");
            output.write(headers.toString().getBytes(StandardCharsets.ISO_8859_1));
            output.write(response.body);
            output.flush();
        }

        @Override
        public void close() {
            running = false;
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            try {
                thread.join(2000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class DriveFile {
        private final String id;

        /** Drive's own timestamps, stamped from the fake server's clock. */
        private long createdAtMillis = CLOCK.millis();

        private long modifiedAtMillis = CLOCK.millis();

        private byte[] content = new byte[0];
        private String name;
        private String mimeType;
        private int version = 1;
        private final List<String> parents = new ArrayList<>();
        private final Map<String, String> appProperties = new LinkedHashMap<>();

        private DriveFile(String id, String name, String mimeType) {
            this.id = id;
            this.name = name;
            this.mimeType = mimeType;
        }

        private String eTag() {
            return "\"etag-" + id + "-" + version + "\"";
        }
    }

    private static final class Request {
        private final String method;
        private final String target;
        private final Map<String, String> headers;
        private final byte[] body;

        private Request(String method, String target, Map<String, String> headers, byte[] body) {
            this.method = method;
            this.target = target;
            this.headers = headers;
            this.body = body;
        }
    }

    private static final class Response {
        private final int code;
        private final String contentType;
        private final byte[] body;
        private final String eTag;
        private final Map<String, String> headers;

        private Response(
                int code,
                String contentType,
                byte[] body,
                String eTag,
                Map<String, String> headers) {
            this.code = code;
            this.contentType = contentType;
            this.body = body;
            this.eTag = eTag;
            this.headers = headers;
        }

        private static Response json(int code, String body) {
            return json(code, body, (String) null);
        }

        private static Response json(int code, String body, String eTag) {
            return new Response(
                    code,
                    "application/json",
                    body.getBytes(StandardCharsets.UTF_8),
                    eTag,
                    new LinkedHashMap<>());
        }

        private static Response json(int code, String body, Map<String, String> headers) {
            return new Response(
                    code, "application/json", body.getBytes(StandardCharsets.UTF_8), null, headers);
        }

        private static Response binary(int code, byte[] body, String eTag) {
            return new Response(
                    code, "application/octet-stream", body, eTag, new LinkedHashMap<>());
        }
    }

    /** One scripted response for the next resumable chunk PUT. */
    private static final class ChunkScript {
        private final int acceptBytes;
        private final int status;
        private final Long forcedRangeInclusiveEnd;
        private final boolean dropConnection;

        private ChunkScript(
                int acceptBytes, int status, Long forcedRangeInclusiveEnd, boolean dropConnection) {
            this.acceptBytes = acceptBytes;
            this.status = status;
            this.forcedRangeInclusiveEnd = forcedRangeInclusiveEnd;
            this.dropConnection = dropConnection;
        }

        static ChunkScript partial(int bytes) {
            return new ChunkScript(bytes, 0, null, false);
        }

        static ChunkScript status(int status) {
            return new ChunkScript(-1, status, null, false);
        }

        static ChunkScript forcedRange(long inclusiveEnd) {
            return new ChunkScript(-1, 0, inclusiveEnd, false);
        }

        static ChunkScript drop() {
            return new ChunkScript(-1, 0, null, true);
        }
    }

    private static final class UploadSession {
        private final JsonObject metadata;
        private final long totalBytes;
        private final String mimeType;
        private final ByteArrayOutputStream data = new ByteArrayOutputStream();

        private UploadSession(JsonObject metadata, long totalBytes, String mimeType) {
            this.metadata = metadata;
            this.totalBytes = totalBytes;
            this.mimeType = mimeType;
        }
    }

    private static final class MultipartPayload {
        private final JsonObject metadata;
        private final byte[] data;

        private MultipartPayload(JsonObject metadata, byte[] data) {
            this.metadata = metadata;
            this.data = data;
        }
    }
}
