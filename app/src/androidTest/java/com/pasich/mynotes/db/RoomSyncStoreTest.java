package com.pasich.mynotes.db;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import android.content.Context;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.JsonObject;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.database.entities.SyncMetadataEntity;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.preferences.PreferenceHelper;
import com.pasich.mynotes.data.sync.RoomSyncStore;
import com.pasich.mynotes.data.sync.SnapshotBuildResult;
import com.pasich.mynotes.data.sync.SnapshotProblem;
import com.pasich.mynotes.data.sync.SyncMetadata;
import com.pasich.mynotes.data.sync.SyncRecord;
import com.pasich.mynotes.data.sync.SyncResolution;
import com.pasich.mynotes.data.sync.SyncSnapshot;
import com.pasich.mynotes.data.sync.SyncState;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Integration coverage for the sync store, which needs a real Room database and a real files
 * directory. The pure protocol classes are unit-tested; everything here is the part that only
 * behaves correctly against actual storage.
 */
@RunWith(AndroidJUnit4.class)
public class RoomSyncStoreTest {

    private Context context;
    private AppDatabase db;
    private RoomSyncStore store;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db =
                Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                        .allowMainThreadQueries()
                        .build();
        store = new RoomSyncStore(context, db, mock(PreferenceHelper.class));
        deleteRecursively(new File(context.getFilesDir(), "sync-attachments"));
        deleteRecursively(new File(context.getFilesDir(), "attachments"));
    }

    @After
    public void tearDown() {
        db.close();
        deleteRecursively(new File(context.getFilesDir(), "sync-attachments"));
        deleteRecursively(new File(context.getFilesDir(), "attachments"));
    }

    @Test
    public void readSnapshot_keepsLocalPrimaryKeysAndFilePathsOffTheWire() throws Exception {
        int noteId = seedNote("Shopping", "Milk", null);

        SyncRecord record = onlyNote(store.readSnapshot());

        JsonObject payload = record.getPayload();
        // "a" is Note.id and "h" is the attachments JSON of file:// paths. Both differ per device,
        // so leaving them in makes two devices hash the same logical note differently and report a
        // conflict on every sync forever.
        assertThat(payload.has("a")).isFalse();
        assertThat(payload.has("h")).isFalse();
        assertThat(payload.get("b").getAsString()).isEqualTo("Shopping");
        assertThat(noteId).isGreaterThan(0);
    }

    @Test
    public void hasAttachment_findsABlobThatOnlyExistsInTheNotesOwnFolder() throws Exception {
        byte[] bytes = "receipt bytes".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        int noteId = seedNoteWithAttachment("receipt.png", bytes);

        // The index is built while the snapshot is read, which is what every sync does first.
        store.readSnapshot();

        // Before this, only sync-attachments/ was consulted and nothing but the download path ever
        // wrote there. On the device that owns the file the lookup failed, SyncService asked the
        // backend for a blob nobody had uploaded, and every sync for that account aborted.
        assertThat(store.hasAttachment(hash)).isTrue();
        try (InputStream in = store.readAttachment(hash)) {
            assertThat(readAll(in)).isEqualTo(bytes);
        }
        assertThat(noteId).isGreaterThan(0);
    }

    @Test
    public void readSnapshot_failsClosedWhenAReferencedAttachmentIsMissing() {
        int noteId = seedNote("Missing attachment", "body", null);
        // The reference has to name this note's own folder, or the test would pass simply
        // because nothing resolves rather than because the file is gone.
        Note seeded = db.noteDao().getNoteSync(noteId);
        seeded.setAttachments("[" + attachmentJson(noteId, "gone.png") + "]");
        db.noteDao().addNote(seeded);
        String original = db.noteDao().getNoteSync(noteId).getAttachments();

        SnapshotBuildResult.SnapshotBuildException error = assertSnapshotBuildFails(store);

        assertThat(error.getProblems().get(0).getKind())
                .isEqualTo(SnapshotProblem.Kind.MISSING_ATTACHMENT);
        assertThat(db.noteDao().getNoteSync(noteId).getAttachments()).isEqualTo(original);
    }

    @Test
    public void readSnapshot_failsClosedWhenAnAttachmentCannotBeRead() throws Exception {
        int noteId = seedNoteWithAttachment("locked.png", "bytes".getBytes(StandardCharsets.UTF_8));
        File real = new File(context.getFilesDir(), "attachments/note_" + noteId + "/locked.png");
        File unreadable =
                new File(real.getAbsolutePath()) {
                    @Override
                    public boolean canRead() {
                        return false;
                    }
                };
        RoomSyncStore failingStore =
                new RoomSyncStore(
                        context,
                        db,
                        mock(PreferenceHelper.class),
                        (ignoredContext, ignoredAttachment) -> unreadable,
                        file -> sha256(readAll(new java.io.FileInputStream(file))));

        SnapshotBuildResult.SnapshotBuildException error = assertSnapshotBuildFails(failingStore);

        assertThat(error.getProblems().get(0).getKind())
                .isEqualTo(SnapshotProblem.Kind.UNREADABLE_ATTACHMENT);
    }

    @Test
    public void readSnapshot_failsClosedWhenAttachmentHashingFails() throws Exception {
        int noteId = seedNoteWithAttachment("hash.png", "bytes".getBytes(StandardCharsets.UTF_8));
        String original = db.noteDao().getNoteSync(noteId).getAttachments();
        RoomSyncStore failingStore =
                new RoomSyncStore(
                        context,
                        db,
                        mock(PreferenceHelper.class),
                        com.pasich.mynotes.extendedEditor.attach.AttachmentStorage::resolve,
                        file -> {
                            throw new IOException("cannot hash");
                        });

        SnapshotBuildResult.SnapshotBuildException error = assertSnapshotBuildFails(failingStore);

        assertThat(error.getProblems().get(0).getKind())
                .isEqualTo(SnapshotProblem.Kind.ATTACHMENT_HASH_FAILED);
        assertThat(db.noteDao().getNoteSync(noteId).getAttachments()).isEqualTo(original);
    }

    @Test
    public void readSnapshot_rejectsTheWholeSnapshotWhenOneOfFiveAttachmentsIsMissing()
            throws Exception {
        int noteId = seedNote("Five attachments", "body", null);
        File folder = new File(context.getFilesDir(), "attachments/note_" + noteId);
        assertThat(folder.mkdirs() || folder.isDirectory()).isTrue();
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < 5; index++) {
            String name = "item-" + index + ".png";
            if (index > 0) json.append(',');
            json.append(attachmentJson(noteId, name));
            if (index < 4) {
                try (FileOutputStream out = new FileOutputStream(new File(folder, name))) {
                    out.write(("bytes-" + index).getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        json.append(']');
        Note note = db.noteDao().getNoteSync(noteId);
        note.setAttachments(json.toString());
        db.noteDao().addNote(note);

        SnapshotBuildResult.SnapshotBuildException error = assertSnapshotBuildFails(store);

        assertThat(error.getProblems()).isNotEmpty();
        assertThat(error.getProblems().get(0).getKind())
                .isEqualTo(SnapshotProblem.Kind.MISSING_ATTACHMENT);
        assertThat(db.noteDao().getNoteSync(noteId).getAttachments()).isEqualTo(json.toString());
    }

    @Test
    public void readSnapshot_acceptsANoteWithNoAttachments() throws Exception {
        seedNote("No attachments", "body", "[]");

        SnapshotBuildResult result = store.buildSnapshot();

        assertThat(result.isPublishable()).isTrue();
        assertThat(
                        onlyNote(result.requireSnapshot())
                                .getPayload()
                                .getAsJsonArray("attachmentHashes"))
                .isEmpty();
    }

    @Test
    public void writeAttachment_leavesNothingBehindWhenTheStreamFails() {
        String hash = sha256("whatever".getBytes(StandardCharsets.UTF_8));
        InputStream failing =
                new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new IOException("stream died");
                    }
                };

        try {
            store.writeAttachment(hash, 8L, failing);
            throw new AssertionError("Expected the failing stream to propagate");
        } catch (IOException expected) {
            // The blob is streamed to a temporary file and renamed only on a clean finish, so a
            // half-written or checksum-mismatched blob must never appear under the hash's name.
            File dir = new File(context.getFilesDir(), "sync-attachments");
            assertThat(new File(dir, hash).exists()).isFalse();
            assertThat(new File(dir, hash + ".tmp").exists()).isFalse();
        }
    }

    @Test
    public void applySnapshot_reapplyingTheLocalVersionKeepsItsAttachments() throws Exception {
        byte[] bytes = "photo bytes".getBytes(StandardCharsets.UTF_8);
        int noteId = seedNoteWithAttachment("photo.png", bytes);

        // A sync applies the merged snapshot even when the local version won, so a note travels
        // through applyPayload -> restoreAttachments unchanged. Resolving blobs from the download
        // cache alone rewrote such a note with an empty attachment list and destroyed the files.
        SyncSnapshot snapshot = store.readSnapshot();
        store.applySnapshot(snapshot, Collections.emptyList());

        Note reloaded = db.noteDao().getNoteSync(noteId);
        assertThat(reloaded.getAttachments()).isNotNull();
        assertThat(reloaded.getAttachments()).contains("photo.png");
        // The display name appearing in the JSON proves nothing about the stored reference, so
        // resolve it the way the editor and the file list do.
        assertThat(resolveFirstAttachment(reloaded.getAttachments()).isFile()).isTrue();
        File restored =
                new File(
                        new File(context.getFilesDir(), "attachments/note_" + noteId), "photo.png");
        assertThat(restored.isFile()).isTrue();
        assertThat(readAll(new java.io.FileInputStream(restored))).isEqualTo(bytes);
    }

    @Test
    public void applySnapshot_repointsEditorBlocksAtTheFilesThisDeviceWrote() throws Exception {
        byte[] bytes = "photo bytes".getBytes(StandardCharsets.UTF_8);
        int noteId = seedNoteWithAttachment("photo.png", bytes);
        String senderUrl =
                com.pasich.mynotes.extendedEditor.attach.AttachmentStorage.urlFor(
                        noteId, "photo.png");
        Note seeded = db.noteDao().getNoteSync(noteId);
        seeded.setValueJson(
                "[{\"id\":\"blk1\",\"type\":\"attaches\",\"data\":{\"file\":{\"url\":\""
                        + senderUrl
                        + "\",\"name\":\"photo.png\"}}}]");
        db.noteDao().addNote(seeded);

        store.applySnapshot(store.readSnapshot(), Collections.emptyList());

        // The attachments column is what the file list reads; valueJson is what the editor
        // renders. Rebuilding only the column left every received rich note showing a broken
        // attachment, because the block still named the sending device's file.
        Note reloaded = db.noteDao().getNoteSync(noteId);
        String blockUrl =
                com.google.gson.JsonParser.parseString(reloaded.getValueJson())
                        .getAsJsonArray()
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonObject("data")
                        .getAsJsonObject("file")
                        .get("url")
                        .getAsString();
        File rendered =
                com.pasich.mynotes.extendedEditor.attach.AttachmentStorage.resolve(
                        context, blockUrl);
        assertThat(rendered).isNotNull();
        assertThat(rendered.isFile()).isTrue();
        assertThat(readAll(new java.io.FileInputStream(rendered))).isEqualTo(bytes);
    }

    @Test
    public void applySnapshot_leavesEditorBlocksAloneWhenTheyDoNotLineUp() throws Exception {
        byte[] bytes = "photo bytes".getBytes(StandardCharsets.UTF_8);
        int noteId = seedNoteWithAttachment("photo.png", bytes);
        // Two blocks, one attachment: the positional mapping cannot be trusted.
        String twoBlocks =
                "[{\"type\":\"attaches\",\"data\":{\"file\":{\"url\":\"editorjs://attachments/note_"
                        + noteId
                        + "/photo.png\"}}},"
                        + "{\"type\":\"image\",\"data\":{\"file\":{\"url\":\"editorjs://attachments/note_"
                        + noteId
                        + "/other.png\"}}}]";
        Note seeded = db.noteDao().getNoteSync(noteId);
        seeded.setValueJson(twoBlocks);
        db.noteDao().addNote(seeded);

        store.applySnapshot(store.readSnapshot(), Collections.emptyList());

        // Rewriting on a guess could point a block at the wrong file; leaving it is recoverable.
        assertThat(db.noteDao().getNoteSync(noteId).getValueJson()).isEqualTo(twoBlocks);
    }

    /** Resolves the first entry of an attachments JSON the way the app's consumers do. */
    private File resolveFirstAttachment(String attachmentsJson) {
        String url =
                com.google.gson.JsonParser.parseString(attachmentsJson)
                        .getAsJsonArray()
                        .get(0)
                        .getAsJsonObject()
                        .get("url")
                        .getAsString();
        return com.pasich.mynotes.extendedEditor.attach.AttachmentStorage.resolve(context, url);
    }

    @Test
    public void clearAfterDisconnect_dropsStatusConflictsAndCachedBlobs() throws Exception {
        store.writeState(SyncState.success("google-drive", java.time.Instant.now(), 0));
        byte[] bytes = "cached".getBytes(StandardCharsets.UTF_8);
        store.writeAttachment(sha256(bytes), bytes.length, new ByteArrayInputStream(bytes));
        assertThat(new File(context.getFilesDir(), "sync-attachments").listFiles()).isNotEmpty();

        store.clearAfterDisconnect();

        // A stale lastSuccessfulSyncAt is what used to make a freshly connected account look
        // already-synced, skipping the only dialog that could restore first-sync consent.
        assertThat(store.readState().getLastSuccessfulSyncAt()).isNull();
        assertThat(store.getConflicts()).isEmpty();
        File[] cached = new File(context.getFilesDir(), "sync-attachments").listFiles();
        assertThat(cached == null || cached.length == 0).isTrue();
    }

    @Test
    public void touch_advancesPastATimestampWrittenByAFasterDeviceClock() {
        // Merging is last-write-wins on wall-clock time, but the SQL in SyncMetadataDao assigns
        // max(now, stored + 1). A device whose clock runs behind therefore still outranks the
        // version it just synced, instead of losing every edit silently.
        db.syncMetadataDao()
                .insertIfAbsent(
                        new SyncMetadataEntity(
                                SyncMetadata.RECORD_TYPE_NOTE, 1L, "stable-a", 5_000L, null));

        db.syncMetadataDao().touch(SyncMetadata.RECORD_TYPE_NOTE, 1L, 1_000L);

        SyncMetadataEntity metadata = db.syncMetadataDao().get(SyncMetadata.RECORD_TYPE_NOTE, 1L);
        assertThat(metadata.updatedAt).isEqualTo(5_001L);
        assertThat(metadata.deletedAt).isNull();
    }

    @Test
    public void touch_clearsATombstoneSoAReusedRowIsNotResurrectedAsDeleted() {
        db.syncMetadataDao()
                .insertIfAbsent(
                        new SyncMetadataEntity(
                                SyncMetadata.RECORD_TYPE_NOTE, 2L, "stable-b", 100L, 100L));

        db.syncMetadataDao().touch(SyncMetadata.RECORD_TYPE_NOTE, 2L, 200L);

        SyncMetadataEntity metadata = db.syncMetadataDao().get(SyncMetadata.RECORD_TYPE_NOTE, 2L);
        assertThat(metadata.deletedAt).isNull();
        assertThat(metadata.updatedAt).isEqualTo(200L);
    }

    @Test
    public void applySnapshot_rollsBackNotesMetadataAndStateWhenARecordMutationFails()
            throws Exception {
        int noteId = seedNote("Original", "body", null);
        SyncRecord original = onlyNote(store.readSnapshot());
        JsonObject changedPayload = original.getPayload();
        changedPayload.addProperty("b", "Remote title");
        SyncSnapshot remote =
                new SyncSnapshot(
                        Collections.singletonList(
                                SyncRecord.live(
                                        SyncRecord.Type.NOTE,
                                        original.getId(),
                                        java.time.Instant.ofEpochMilli(2_000L),
                                        changedPayload)));
        RoomSyncStore failingStore =
                new RoomSyncStore(
                        context,
                        db,
                        mock(PreferenceHelper.class),
                        com.pasich.mynotes.extendedEditor.attach.AttachmentStorage::resolve,
                        file -> sha256(readAll(new java.io.FileInputStream(file))),
                        record -> {
                            throw new IllegalStateException("injected apply failure");
                        });

        try {
            failingStore.applySnapshot(
                    remote,
                    Collections.emptyList(),
                    SyncState.success("google-drive", java.time.Instant.now(), 0));
            throw new AssertionError("Expected injected transaction failure");
        } catch (IllegalStateException expected) {
            assertThat(expected).hasMessageThat().contains("injected apply failure");
        }

        assertThat(db.noteDao().getNoteSync(noteId).getTitle()).isEqualTo("Original");
        SyncMetadataEntity metadata = db.syncMetadataDao().get("note", noteId);
        assertThat(metadata.updatedAt).isEqualTo(1_000L);
        assertThat(store.readState().getStatus()).isEqualTo(SyncState.Status.IDLE);
        assertThat(store.getConflicts()).isEmpty();
    }

    // ---- helpers ----

    private int seedNote(String title, String value, String attachmentsJson) {
        Note note = new Note().create(title, value, 1_000L, "");
        note.setAttachments(attachmentsJson);
        int id = db.noteDao().addNote(note).intValue();
        db.syncMetadataDao()
                .insertIfAbsent(
                        new SyncMetadataEntity(
                                SyncMetadata.RECORD_TYPE_NOTE,
                                id,
                                "11111111-1111-4111-8111-111111111111",
                                1_000L,
                                null));
        return id;
    }

    @Test
    public void readSnapshot_stillResolvesLegacyFileScheme() throws Exception {
        int id = seedNote("Legacy", "body", null);
        File folder = new File(context.getFilesDir(), "attachments/note_" + id);
        assertThat(folder.mkdirs() || folder.isDirectory()).isTrue();
        try (FileOutputStream out = new FileOutputStream(new File(folder, "old.png"))) {
            out.write("legacy bytes".getBytes(StandardCharsets.UTF_8));
        }
        Note note = db.noteDao().getNoteSync(id);
        note.setAttachments("[" + legacyAttachmentJson(id, "old.png") + "]");
        db.noteDao().addNote(note);

        SnapshotBuildResult result = store.buildSnapshot();

        assertThat(result.isPublishable()).isTrue();
    }

    @Test
    public void applySnapshot_dropsCachedBlobsNothingReferencesAndKeepsConflictBlobs()
            throws Exception {
        File cache = new File(context.getFilesDir(), "sync-attachments");
        assertThat(cache.mkdirs() || cache.isDirectory()).isTrue();
        String orphan = "1111111111111111111111111111111111111111111111111111111111111111";
        try (FileOutputStream out = new FileOutputStream(new File(cache, orphan))) {
            out.write("nobody references this".getBytes(StandardCharsets.UTF_8));
        }

        store.applySnapshot(SyncSnapshot.empty(), Collections.emptyList());

        assertThat(new File(cache, orphan).exists()).isFalse();
    }

    @Test
    public void applySnapshot_keepsCachedBlobsAnUnresolvedConflictStillNeeds() throws Exception {
        File cache = new File(context.getFilesDir(), "sync-attachments");
        assertThat(cache.mkdirs() || cache.isDirectory()).isTrue();
        String pinned = "2222222222222222222222222222222222222222222222222222222222222222";
        try (FileOutputStream out = new FileOutputStream(new File(cache, pinned))) {
            out.write("needed by an unresolved conflict".getBytes(StandardCharsets.UTF_8));
        }
        db.syncConflictDao()
                .insertIgnoringDuplicates(
                        Collections.singletonList(
                                new com.pasich.mynotes.data.database.entities.SyncConflictEntity(
                                        "note",
                                        "550e8400-e29b-41d4-a716-446655440000",
                                        "pair",
                                        "LOCAL",
                                        "REMOTE",
                                        "winner-id",
                                        "loser-id",
                                        "{\"payload\":{\"attachmentHashes\":[\"" + pinned + "\"]}}",
                                        "{\"payload\":{}}",
                                        1L,
                                        2L,
                                        false,
                                        false,
                                        "PENDING",
                                        false,
                                        3L,
                                        0L)));

        store.applySnapshot(SyncSnapshot.empty(), Collections.emptyList());

        // Deleting this would make the losing version unrecoverable before the user has chosen.
        assertThat(new File(cache, pinned).exists()).isTrue();
    }

    // ------------------------------------------------- preferences conflict resolution

    @Test
    public void resolveConflict_appliesTheChosenPreferencesVersionDurably() throws Exception {
        PreferencesAdapter adapter = new PreferencesAdapter();
        RoomSyncStore preferencesStore = new RoomSyncStore(context, db, adapter.helper);
        preferencesStore.readState();
        long conflictId = seedPreferencesConflict(9, 11);

        preferencesStore.resolveConflict(conflictId, SyncResolution.KEEP_DRIVE);

        assertThat(adapter.committed.get()).isNotNull();
        assertThat(adapter.committed.get().getThemeValue()).isEqualTo(11);
        assertThat(db.syncConflictDao().getById(conflictId).resolved).isTrue();
        assertThat(db.syncPendingPreferencesDao().get()).isNull();
    }

    @Test
    public void resolveConflict_leavesThePreferencesConflictOpenWhenTheCommitFails()
            throws Exception {
        PreferencesAdapter adapter = new PreferencesAdapter();
        adapter.succeeds.set(false);
        RoomSyncStore preferencesStore = new RoomSyncStore(context, db, adapter.helper);
        preferencesStore.readState();
        long conflictId = seedPreferencesConflict(9, 11);

        try {
            preferencesStore.resolveConflict(conflictId, SyncResolution.KEEP_DRIVE);
            throw new AssertionError("Expected a failed preferences commit to propagate");
        } catch (IOException expected) {
            // Nothing may be claimed as resolved.
        }

        assertThat(db.syncConflictDao().getById(conflictId).resolved).isFalse();
        // The journal survives so the next attempt can finish the job.
        assertThat(db.syncPendingPreferencesDao().get()).isNotNull();
        // The record version must not move; otherwise the rejected value would win the next sync.
        SyncMetadataEntity metadata =
                db.syncMetadataDao()
                        .getByStableId(
                                SyncMetadata.RECORD_TYPE_PREFERENCES,
                                "00000000-0000-4000-8000-000000000000");
        assertThat(metadata.updatedAt).isEqualTo(0L);
    }

    @Test
    public void aRetryAfterAFailedCommit_completesTheResolution() throws Exception {
        PreferencesAdapter adapter = new PreferencesAdapter();
        adapter.succeeds.set(false);
        RoomSyncStore preferencesStore = new RoomSyncStore(context, db, adapter.helper);
        preferencesStore.readState();
        long conflictId = seedPreferencesConflict(9, 11);
        try {
            preferencesStore.resolveConflict(conflictId, SyncResolution.KEEP_DRIVE);
        } catch (IOException expected) {
            // First attempt fails.
        }

        adapter.succeeds.set(true);
        preferencesStore.resolveConflict(conflictId, SyncResolution.KEEP_DRIVE);

        assertThat(adapter.committed.get().getThemeValue()).isEqualTo(11);
        assertThat(db.syncConflictDao().getById(conflictId).resolved).isTrue();
        assertThat(db.syncPendingPreferencesDao().get()).isNull();
    }

    @Test
    public void anUnreadableJournal_isQuarantinedInsteadOfDisablingSync() throws Exception {
        db.syncPendingPreferencesDao()
                .upsert(
                        new com.pasich.mynotes.data.database.entities.SyncPendingPreferencesEntity(
                                1, "{not json", "target", "baseline", 0L, false, 0L, ""));
        PreferencesAdapter adapter = new PreferencesAdapter();
        RoomSyncStore preferencesStore = new RoomSyncStore(context, db, adapter.helper);

        // Must not throw: ensureSeeded gates both snapshot building and the status read.
        SyncState state = preferencesStore.readState();

        assertThat(state).isNotNull();
        assertThat(db.syncPendingPreferencesDao().get()).isNull();
        assertThat(db.syncPendingPreferencesDao().getIncludingQuarantined()).isNotNull();
        assertThat(adapter.committed.get()).isNull();
    }

    @Test
    public void recoveryFinishesAConflictWhosePreferenceWriteLandedBeforeTheCrash()
            throws Exception {
        PreferencesAdapter adapter = new PreferencesAdapter();
        RoomSyncStore first = new RoomSyncStore(context, db, adapter.helper);
        first.readState();
        long conflictId = seedPreferencesConflict(9, 11);
        // Simulates a process death between the durable preference write and the bookkeeping:
        // the journal is present and the live values already match its target.
        adapter.helper.commitListPreferences(preferencesWithTheme(11));
        String chosenJson = new com.google.gson.Gson().toJson(preferencesWithTheme(11));
        // The target digest has to be the real one, or recovery reads the journal as stale and
        // discards it instead of finishing what it started.
        String target = sha256(chosenJson.getBytes(StandardCharsets.UTF_8));
        db.syncPendingPreferencesDao()
                .upsert(
                        new com.pasich.mynotes.data.database.entities.SyncPendingPreferencesEntity(
                                1,
                                chosenJson,
                                target,
                                "digest-before-the-write",
                                0L,
                                false,
                                conflictId,
                                SyncResolution.KEEP_DRIVE.name()));

        // A fresh store seeds, which is where recovery runs.
        new RoomSyncStore(context, db, adapter.helper).readState();

        // Without this the choice stayed applied but unversioned and pending, so the next sync
        // could put the rejected version back.
        assertThat(db.syncPendingPreferencesDao().get()).isNull();
        assertThat(db.syncConflictDao().getById(conflictId).resolved).isTrue();
        SyncMetadataEntity metadata =
                db.syncMetadataDao()
                        .getByStableId(
                                SyncMetadata.RECORD_TYPE_PREFERENCES,
                                "00000000-0000-4000-8000-000000000000");
        assertThat(metadata.updatedAt).isGreaterThan(0L);
    }

    /** A preferences adapter whose durability can be turned off. */
    private static final class PreferencesAdapter {
        private final PreferenceHelper helper = mock(PreferenceHelper.class);
        private final java.util.concurrent.atomic.AtomicReference<
                        com.pasich.mynotes.utils.backup.models.PreferencesBackup>
                current =
                        new java.util.concurrent.atomic.AtomicReference<>(preferencesWithTheme(1));
        private final java.util.concurrent.atomic.AtomicReference<
                        com.pasich.mynotes.utils.backup.models.PreferencesBackup>
                committed = new java.util.concurrent.atomic.AtomicReference<>();
        private final java.util.concurrent.atomic.AtomicBoolean succeeds =
                new java.util.concurrent.atomic.AtomicBoolean(true);

        PreferencesAdapter() {
            org.mockito.Mockito.when(helper.getListPreferences())
                    .thenAnswer(invocation -> current.get());
            org.mockito.Mockito.when(
                            helper.commitListPreferences(org.mockito.ArgumentMatchers.any()))
                    .thenAnswer(
                            invocation -> {
                                if (!succeeds.get()) {
                                    return false;
                                }
                                com.pasich.mynotes.utils.backup.models.PreferencesBackup value =
                                        invocation.getArgument(0);
                                committed.set(value);
                                current.set(value);
                                return true;
                            });
        }
    }

    private long seedPreferencesConflict(int localTheme, int remoteTheme) {
        String winner = preferencesRecordJson(remoteTheme, "2026-08-31T12:00:20Z");
        String loser = preferencesRecordJson(localTheme, "2026-08-31T12:00:10Z");
        db.syncConflictDao()
                .insertIgnoringDuplicates(
                        Collections.singletonList(
                                new com.pasich.mynotes.data.database.entities.SyncConflictEntity(
                                        SyncMetadata.RECORD_TYPE_PREFERENCES,
                                        "00000000-0000-4000-8000-000000000000",
                                        "pair-hash",
                                        "REMOTE",
                                        "LOCAL",
                                        "winner-version-id",
                                        "loser-version-id",
                                        winner,
                                        loser,
                                        20L,
                                        10L,
                                        false,
                                        false,
                                        "PENDING",
                                        false,
                                        1L,
                                        0L)));
        return db.syncConflictDao().getAll().get(0).id;
    }

    private static String preferencesRecordJson(int themeValue, String updatedAt) {
        return "{\"type\":\"preferences\",\"id\":\"00000000-0000-4000-8000-000000000000\","
                + "\"updatedAt\":\""
                + updatedAt
                + "\",\"deletedAt\":null,\"payload\":"
                + new com.google.gson.Gson().toJson(preferencesWithTheme(themeValue))
                + "}";
    }

    private static com.pasich.mynotes.utils.backup.models.PreferencesBackup preferencesWithTheme(
            int themeValue) {
        return new com.pasich.mynotes.utils.backup.models.PreferencesBackup(
                1, "sans", "date", 14, themeValue, false, 0, false, false, false, 1.0f);
    }

    /** Writes a real file into the note's own attachment folder and links it from the note. */
    private int seedNoteWithAttachment(String fileName, byte[] bytes) throws IOException {
        int id = seedNote("With attachment", "body", null);
        File folder = new File(context.getFilesDir(), "attachments/note_" + id);
        assertThat(folder.mkdirs() || folder.isDirectory()).isTrue();
        try (FileOutputStream out = new FileOutputStream(new File(folder, fileName))) {
            out.write(bytes);
        }
        // Production shape: EditorJSInterface writes editorjs://attachments/note_<id>/<file>.
        String json = "[" + attachmentJson(id, fileName) + "]";
        Note note = db.noteDao().getNoteSync(id);
        note.setAttachments(json);
        db.noteDao().addNote(note);
        return id;
    }

    private static SyncRecord onlyNote(SyncSnapshot snapshot) {
        List<SyncRecord> notes = snapshot.getLiveRecords(SyncRecord.Type.NOTE);
        assertThat(notes).hasSize(1);
        return notes.get(0);
    }

    private static SnapshotBuildResult.SnapshotBuildException assertSnapshotBuildFails(
            RoomSyncStore store) {
        try {
            store.readSnapshot();
            throw new AssertionError("Expected a local snapshot build failure");
        } catch (SnapshotBuildResult.SnapshotBuildException expected) {
            return expected;
        } catch (IOException unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    /** The canonical reference the editor and sync restore both produce. */
    private static String attachmentJson(int noteId, String name) {
        return "{\"url\":\""
                + com.pasich.mynotes.extendedEditor.attach.AttachmentStorage.urlFor(noteId, name)
                + "\",\"name\":\""
                + name
                + "\"}";
    }

    /** The pre-2.6.49 reference shape, kept readable for already-stored notes. */
    private static String legacyAttachmentJson(int noteId, String name) {
        return "{\"url\":\"file://attachments/note_"
                + noteId
                + "/"
                + name
                + "\",\"name\":\""
                + name
                + "\"}";
    }

    private static byte[] readAll(InputStream input) throws IOException {
        try (InputStream stream = input;
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            StringBuilder hex = new StringBuilder(64);
            for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
