package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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

/**
 * Two devices against one shared backend.
 *
 * <p>The merge engine is covered on its own, but nothing exercised the property that actually
 * matters to a user with a phone and a tablet: after everyone has synced, both devices and the
 * backend hold the same records, whatever order they synced in. These tests drive the real {@link
 * SyncService} twice over two independent stores sharing one backend.
 */
public class SyncConvergenceTest {

    private static final String NOTE = "550e8400-e29b-41d4-a716-446655440000";
    private static final String OTHER_NOTE = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";
    private static final Instant T10 = Instant.parse("2026-08-31T12:00:10Z");
    private static final Instant T20 = Instant.parse("2026-08-31T12:00:20Z");
    private static final Instant T30 = Instant.parse("2026-08-31T12:00:30Z");
    private static final Clock CLOCK = Clock.fixed(T30, ZoneOffset.UTC);

    @Test
    public void separateNotesOnEachDevice_endUpOnBoth() {
        SyncRecord fromPhone = note(NOTE, T10, "Phone note");
        SyncRecord fromTablet = note(OTHER_NOTE, T10, "Tablet note");
        Device phone = new Device(fromPhone);
        Device tablet = new Device(fromTablet);
        Backend backend = new Backend();

        phone.sync(backend);
        tablet.sync(backend);
        phone.sync(backend);

        assertThat(phone.records()).containsExactly(fromPhone, fromTablet);
        assertThat(tablet.records()).containsExactly(fromPhone, fromTablet);
        assertThat(backend.records()).containsExactly(fromPhone, fromTablet);
    }

    @Test
    public void sameNoteEditedOnBoth_newerEditWinsEverywhere() {
        SyncRecord older = note(NOTE, T10, "Written on the phone");
        SyncRecord newer = note(NOTE, T20, "Written on the tablet");
        Device phone = new Device(older);
        Device tablet = new Device(newer);
        Backend backend = new Backend();

        phone.sync(backend);
        tablet.sync(backend);
        phone.sync(backend);

        assertThat(phone.records()).containsExactly(newer);
        assertThat(tablet.records()).containsExactly(newer);
        assertThat(backend.records()).containsExactly(newer);
    }

    @Test
    public void syncOrderDoesNotChangeTheResult() {
        SyncRecord phoneEdit = note(NOTE, T10, "Phone");
        SyncRecord tabletEdit = note(NOTE, T20, "Tablet");

        Backend first = new Backend();
        Device phoneA = new Device(phoneEdit);
        Device tabletA = new Device(tabletEdit);
        phoneA.sync(first);
        tabletA.sync(first);
        phoneA.sync(first);

        Backend second = new Backend();
        Device phoneB = new Device(phoneEdit);
        Device tabletB = new Device(tabletEdit);
        tabletB.sync(second);
        phoneB.sync(second);
        tabletB.sync(second);

        assertThat(phoneB.records()).containsExactlyElementsIn(phoneA.records());
        assertThat(tabletB.records()).containsExactlyElementsIn(tabletA.records());
        assertThat(second.records()).containsExactlyElementsIn(first.records());
    }

    @Test
    public void equalTimestampsWithDifferentContent_stillConverge() {
        SyncRecord phoneEdit = note(NOTE, T10, "Phone wording");
        SyncRecord tabletEdit = note(NOTE, T10, "Tablet wording");
        Device phone = new Device(phoneEdit);
        Device tablet = new Device(tabletEdit);
        Backend backend = new Backend();

        phone.sync(backend);
        tablet.sync(backend);
        phone.sync(backend);

        // Which of the two wins is decided by the hash tiebreaker; the guarantee under test is
        // that nobody is left holding a different version.
        assertThat(phone.records()).containsExactlyElementsIn(tablet.records());
        assertThat(backend.records()).containsExactlyElementsIn(phone.records());
        assertThat(phone.records()).hasSize(1);
    }

    @Test
    public void deletionOnOneDevice_removesTheNoteFromTheOther() {
        SyncRecord live = note(NOTE, T10, "Shopping");
        SyncRecord deleted = SyncRecord.tombstone(SyncRecord.Type.NOTE, NOTE, T20, T20);
        Device phone = new Device(deleted);
        Device tablet = new Device(live);
        Backend backend = new Backend();

        phone.sync(backend);
        tablet.sync(backend);

        assertThat(tablet.records()).containsExactly(deleted);
        assertThat(tablet.liveNotes()).isEmpty();
        assertThat(backend.records()).containsExactly(deleted);
    }

    @Test
    public void editAfterDeletionOnAnotherDevice_bringsTheNoteBack() {
        SyncRecord deleted = SyncRecord.tombstone(SyncRecord.Type.NOTE, NOTE, T10, T10);
        SyncRecord editedLater = note(NOTE, T20, "Changed my mind");
        Device phone = new Device(deleted);
        Device tablet = new Device(editedLater);
        Backend backend = new Backend();

        phone.sync(backend);
        tablet.sync(backend);
        phone.sync(backend);

        assertThat(phone.records()).containsExactly(editedLater);
        assertThat(tablet.records()).containsExactly(editedLater);
    }

    @Test
    public void freshDeviceJoining_receivesEverythingWithoutDeletingAnything() {
        SyncRecord existing = note(NOTE, T10, "Already synced");
        Device phone = new Device(existing);
        Backend backend = new Backend();
        phone.sync(backend);

        Device freshTablet = new Device();
        freshTablet.sync(backend);
        phone.sync(backend);

        assertThat(freshTablet.records()).containsExactly(existing);
        assertThat(phone.records()).containsExactly(existing);
    }

    private void assertNoErrors(SyncState state) {
        assertThat(state.getStatus()).isEqualTo(SyncState.Status.SUCCESS);
    }

    /** One endpoint keeping its own snapshot across syncs. */
    private final class Device {
        private final Store store;

        Device(SyncRecord... records) {
            store = new Store(new SyncSnapshot(Arrays.asList(records)));
        }

        void sync(Backend backend) {
            assertNoErrors(new SyncService(store, new SyncMerger(), CLOCK).sync(backend));
        }

        List<SyncRecord> records() {
            return store.snapshot.getRecords();
        }

        List<SyncRecord> liveNotes() {
            return store.snapshot.getLiveRecords(SyncRecord.Type.NOTE);
        }
    }

    private static SyncRecord note(String id, Instant updatedAt, String value) {
        JsonObject payload = new JsonObject();
        payload.addProperty("title", "Shopping");
        payload.addProperty("value", value);
        return SyncRecord.live(SyncRecord.Type.NOTE, id, updatedAt, payload);
    }

    private static final class Store implements SyncStore {
        private SyncSnapshot snapshot;
        private SyncState state = SyncState.idle();

        Store(SyncSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public SyncSnapshot readSnapshot() {
            return snapshot;
        }

        @Override
        public void applySnapshot(SyncSnapshot snapshot, List<SyncMergeResult.Conflict> conflicts) {
            this.snapshot = snapshot;
        }

        @Override
        public Collection<String> getAttachmentHashes(SyncSnapshot snapshot) {
            return Collections.emptyList();
        }

        @Override
        public boolean hasAttachment(String sha256) {
            return false;
        }

        @Override
        public InputStream readAttachment(String sha256) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void writeAttachment(String sha256, long sizeBytes, InputStream content) {}

        @Override
        public SyncState readState() {
            return state;
        }

        @Override
        public void writeState(SyncState state) {
            this.state = state;
        }
    }

    /** The shared Drive bundle both devices read and write. */
    private static final class Backend implements SyncBackend {
        private SyncSnapshot snapshot = SyncSnapshot.empty();
        private final Map<String, byte[]> attachments = new HashMap<>();

        @Override
        public String getIdentifier() {
            return "fake-drive";
        }

        @Override
        public RemoteSnapshot readSnapshotResult() {
            return RemoteSnapshot.of(snapshot);
        }

        @Override
        public void publish(SyncPublication publication) {
            this.snapshot = publication.getSnapshot();
        }

        @Override
        public boolean hasAttachment(String sha256) {
            return attachments.containsKey(sha256);
        }

        @Override
        public InputStream readAttachment(String sha256) {
            byte[] value = attachments.get(sha256);
            return value == null ? null : new ByteArrayInputStream(value);
        }

        @Override
        public void writeAttachment(String sha256, long sizeBytes, InputStream content)
                throws IOException {
            attachments.put(sha256, new byte[0]);
            content.close();
        }

        List<SyncRecord> records() {
            return new ArrayList<>(snapshot.getRecords());
        }
    }
}
