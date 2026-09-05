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
        // Absent, not an empty array: a decoded remote record carries no attachment fields at
        // all, so emitting empty ones here made the two shapes hash differently and every
        // attachment-free note conflicted with itself on every sync.
        com.google.gson.JsonObject payload = onlyNote(result.requireSnapshot()).getPayload();
        assertThat(payload.has("attachmentHashes")).isFalse();
        assertThat(payload.has("attachmentsManifest")).isFalse();
        assertThat(payload.has("attachmentNames")).isFalse();
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
    public void applySnapshot_rewritesOnlyTheBlocksThatNameAKnownAttachment() throws Exception {
        byte[] bytes = "photo bytes".getBytes(StandardCharsets.UTF_8);
        int noteId = seedNoteWithAttachment("photo.png", bytes);
        // Two blocks, one attachment: a block naming a file the column does not know is left
        // exactly as it was, and the one that does is repointed by identity, not by position.
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

        List<String> urls =
                com.pasich.mynotes.extendedEditor.attach.EditorAttachmentBlocks.fileUrls(
                        db.noteDao().getNoteSync(noteId).getValueJson());
        assertThat(urls).hasSize(2);
        File rendered =
                com.pasich.mynotes.extendedEditor.attach.AttachmentStorage.resolve(
                        context, urls.get(0));
        assertThat(rendered).isNotNull();
        assertThat(readAll(new java.io.FileInputStream(rendered))).isEqualTo(bytes);
        assertThat(urls.get(1)).isEqualTo("editorjs://attachments/note_" + noteId + "/other.png");
    }

    @Test
    public void applySnapshot_leavesLegacyBlocksAloneWhenTheyDoNotLineUp() throws Exception {
        // A bundle from an older client names the sender's files in its blocks; for those the
        // only mapping is positional, and two blocks for one attachment cannot be trusted.
        // Rewriting on a guess could point a block at the wrong file; leaving it is recoverable.
        byte[] bytes = "photo bytes".getBytes(StandardCharsets.UTF_8);
        int noteId = seedNoteWithAttachment("photo.png", bytes);
        SyncRecord built = onlyNote(store.readSnapshot());
        String legacyBlocks =
                "[{\"type\":\"attaches\",\"data\":{\"file\":{\"url\":\"editorjs://attachments/note_99/photo.png\"}}},"
                        + "{\"type\":\"image\",\"data\":{\"file\":{\"url\":\"editorjs://attachments/note_99/other.png\"}}}]";
        JsonObject legacyPayload = built.getPayload();
        legacyPayload.addProperty("f", legacyBlocks);
        SyncRecord legacy =
                SyncRecord.live(
                        SyncRecord.Type.NOTE,
                        built.getId(),
                        built.getUpdatedAt().plusSeconds(1),
                        legacyPayload);

        store.applySnapshot(
                new SyncSnapshot(Collections.singletonList(legacy)), Collections.emptyList());

        assertThat(db.noteDao().getNoteSync(noteId).getValueJson()).isEqualTo(legacyBlocks);
    }

    // ------------------------------------------------- a note that has crossed devices

    @Test
    public void aNoteReceivedFromAnotherDeviceHashesIdenticallyWhenRebuiltThere() throws Exception {
        // Device A: a rich note whose block names A's own file. A throwaway note first, so A's
        // row id differs from the one B will assign.
        seedNote("Placeholder", "x", null, "22222222-2222-4222-8222-222222222222");
        byte[] bytes = "photo bytes".getBytes(StandardCharsets.UTF_8);
        // The display name has no extension, so the MIME type can only come from the sender's
        // file name — which B never sees.
        int noteId = seedNoteWithAttachment("1700000000000_123.png", "photo", bytes);
        Note seeded = db.noteDao().getNoteSync(noteId);
        seeded.setValueJson(
                "[{\"id\":\"blk1\",\"type\":\"attaches\",\"data\":{\"file\":{\"url\":\""
                        + com.pasich.mynotes.extendedEditor.attach.AttachmentStorage.urlFor(
                                noteId, "1700000000000_123.png")
                        + "\",\"name\":\"photo\"}}}]");
        db.noteDao().addNote(seeded);
        SyncRecord fromA =
                store.readSnapshot()
                        .find(SyncRecord.Type.NOTE, "11111111-1111-4111-8111-111111111111");
        assertThat(fromA).isNotNull();

        // On the wire the block names the attachment, not A's row id and file.
        String wireBlocks = fromA.getPayload().get("f").getAsString();
        assertThat(wireBlocks).doesNotContain("note_");
        assertThat(wireBlocks).contains("mynotes-sync://attachment/");

        // Device B: its own database, its own row ids; the blob arrives through the sync cache.
        AppDatabase dbB =
                Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                        .allowMainThreadQueries()
                        .build();
        try {
            RoomSyncStore storeB = new RoomSyncStore(context, dbB, mock(PreferenceHelper.class));
            storeB.writeAttachment(sha256(bytes), bytes.length, new ByteArrayInputStream(bytes));
            storeB.applySnapshot(
                    new SyncSnapshot(Collections.singletonList(fromA)), Collections.emptyList());

            SyncRecord rebuiltOnB = onlyNote(storeB.readSnapshot());

            // The same version, not a conflict against itself: restoring rewrote B's blocks and
            // column to B's files, and every such note used to hash differently at the same
            // timestamp and republish a bundle on every sync forever.
            assertThat(rebuiltOnB.getCanonicalPayloadHash())
                    .isEqualTo(fromA.getCanonicalPayloadHash());
            long localIdOnB =
                    dbB.syncMetadataDao()
                            .getByStableId(SyncMetadata.RECORD_TYPE_NOTE, fromA.getId())
                            .localId;
            String blockUrl =
                    com.pasich
                            .mynotes
                            .extendedEditor
                            .attach
                            .EditorAttachmentBlocks
                            .fileUrls(dbB.noteDao().getNoteSync((int) localIdOnB).getValueJson())
                            .get(0);
            File rendered =
                    com.pasich.mynotes.extendedEditor.attach.AttachmentStorage.resolve(
                            context, blockUrl);
            assertThat(rendered).isNotNull();
            assertThat(readAll(new java.io.FileInputStream(rendered))).isEqualTo(bytes);
        } finally {
            dbB.close();
        }
    }

    // ------------------------------------------------- an ordinary edit after a clean sync

    @Test
    public void aLocalEditAfterACleanSyncIsPublishedWithoutAConflict() throws Exception {
        // Reproduced on a Pixel: fully synced, edit a note, sync — the conflict dialog offered
        // the edit against the text it had replaced, after every single edit.
        int noteId = seedNote("Original", "body", null);
        SyncRecord published = onlyNote(store.readSnapshot());
        // What a clean sync does with the merged snapshot: apply it, which records the version
        // the remote now holds.
        store.applySnapshot(
                new SyncSnapshot(Collections.singletonList(published)), Collections.emptyList());
        Note note = db.noteDao().getNoteSync(noteId);
        note.setTitle("Edited here");
        db.noteDao().addNote(note);
        db.syncMetadataDao().touch(SyncMetadata.RECORD_TYPE_NOTE, noteId, 5_000L);

        SyncRecord edited = onlyNote(store.readSnapshot());
        com.pasich.mynotes.data.sync.SyncMergeResult merge =
                new com.pasich.mynotes.data.sync.SyncMerger()
                        .merge(
                                new SyncSnapshot(Collections.singletonList(edited)),
                                new SyncSnapshot(Collections.singletonList(published)));

        assertThat(edited.getBaseVersionId()).isEqualTo(published.getCanonicalPayloadHash());
        assertThat(merge.getConflicts()).isEmpty();
        assertThat(
                        merge.getMergedSnapshot()
                                .getRecords()
                                .get(0)
                                .getPayload()
                                .get("b")
                                .getAsString())
                .isEqualTo("Edited here");
    }

    @Test
    public void anEditOnBothSidesAfterACleanSyncIsStillAConflict() throws Exception {
        int noteId = seedNote("Original", "body", null);
        SyncRecord published = onlyNote(store.readSnapshot());
        store.applySnapshot(
                new SyncSnapshot(Collections.singletonList(published)), Collections.emptyList());
        Note note = db.noteDao().getNoteSync(noteId);
        note.setTitle("Edited here");
        db.noteDao().addNote(note);
        db.syncMetadataDao().touch(SyncMetadata.RECORD_TYPE_NOTE, noteId, 5_000L);
        JsonObject elsewhere = published.getPayload();
        elsewhere.addProperty("b", "Edited elsewhere");
        SyncRecord remote =
                SyncRecord.live(
                        SyncRecord.Type.NOTE,
                        published.getId(),
                        java.time.Instant.ofEpochMilli(6_000L),
                        elsewhere);

        com.pasich.mynotes.data.sync.SyncMergeResult merge =
                new com.pasich.mynotes.data.sync.SyncMerger()
                        .merge(
                                new SyncSnapshot(
                                        Collections.singletonList(onlyNote(store.readSnapshot()))),
                                new SyncSnapshot(Collections.singletonList(remote)));

        assertThat(merge.getConflicts()).hasSize(1);
    }

    @Test
    public void aNoteWithADuplicatedBlockReceivedUnder2650RebuildsToWhatTheDecoderProduces()
            throws Exception {
        // 2.6.50 published such a note with one attachment id referenced twice, and its receivers
        // restored one column entry per reference under that id. The decoder keeps the id; the
        // store used to re-key the repeat, so the two versions hashed differently at the same
        // timestamp and the note conflicted with itself on every sync.
        byte[] bytes = "photo bytes".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        String restoredId = "7d444840-9dc0-11d1-b245-5ffdce74fad2";
        int noteId = seedNote("Shopping", "Milk", null);
        File folder = new File(context.getFilesDir(), "attachments/note_" + noteId);
        assertThat(folder.mkdirs() || folder.isDirectory()).isTrue();
        try (FileOutputStream out =
                new FileOutputStream(new File(folder, restoredId + "-" + hash))) {
            out.write(bytes);
        }
        String receiverUrl =
                com.pasich.mynotes.extendedEditor.attach.AttachmentStorage.urlFor(
                        noteId, restoredId + "-" + hash);
        String entry =
                "{\"url\":\""
                        + receiverUrl
                        + "\",\"name\":\"photo.png\",\"id\":\""
                        + restoredId
                        + "\"}";
        String block =
                "{\"type\":\"attaches\",\"data\":{\"file\":{\"url\":\""
                        + receiverUrl
                        + "\",\"name\":\"photo.png\"}}}";
        Note received = db.noteDao().getNoteSync(noteId);
        received.setAttachments("[" + entry + "," + entry + "]");
        received.setValueJson("[" + block + "," + block + "]");
        db.noteDao().addNote(received);
        // The same note as 2.6.50 put it on the wire: the sender's own URLs, [X, X].
        String senderUrl = "editorjs://attachments/note_77/photo.png";
        String senderBlock =
                "{\"type\":\"attaches\",\"data\":{\"file\":{\"url\":\""
                        + senderUrl
                        + "\",\"name\":\"photo.png\"}}}";
        Note sender = new Note().create("Shopping", "Milk", 1_000L, "");
        sender.setValueJson("[" + senderBlock + "," + senderBlock + "]");
        SyncRecord onDrive =
                published2650WithIds(
                        sender, "photo.png", hash, bytes.length, restoredId, restoredId);

        SyncRecord rebuilt = onlyNote(store.readSnapshot());

        assertThat(rebuilt.getCanonicalPayloadHash()).isEqualTo(onDrive.getCanonicalPayloadHash());
    }

    // ------------------------------------------------- notes published by 2.6.50

    @Test
    public void aNoteSyncedBy2650HashesIdenticallyAfterTheUpgrade() throws Exception {
        // The device that published the note under 2.6.50 upgrades. Its unchanged note, rebuilt
        // by the upgraded store, has to be the same version as the one on Drive, or the merge
        // reports a conflict against the note itself — and, when the old version won, again on
        // every sync until the user edited the note.
        byte[] bytes = "photo bytes".getBytes(StandardCharsets.UTF_8);
        int noteId = seedNoteWithAttachment("1700000000000_123.png", "photo.png", bytes);
        String localUrl =
                com.pasich.mynotes.extendedEditor.attach.AttachmentStorage.urlFor(
                        noteId, "1700000000000_123.png");
        String blocks =
                "[{\"id\":\"blk1\",\"type\":\"attaches\",\"data\":{\"file\":{\"url\":\""
                        + localUrl
                        + "\",\"name\":\"photo.png\"}}}]";
        Note seeded = db.noteDao().getNoteSync(noteId);
        seeded.setValueJson(blocks);
        db.noteDao().addNote(seeded);
        SyncRecord onDrive =
                published2650(seeded, localUrl, "photo.png", sha256(bytes), bytes.length);

        SyncRecord rebuilt = onlyNote(store.readSnapshot());
        com.pasich.mynotes.data.sync.SyncMergeResult merge =
                new com.pasich.mynotes.data.sync.SyncMerger()
                        .merge(
                                new SyncSnapshot(Collections.singletonList(rebuilt)),
                                new SyncSnapshot(Collections.singletonList(onDrive)));

        assertThat(rebuilt.getCanonicalPayloadHash()).isEqualTo(onDrive.getCanonicalPayloadHash());
        assertThat(merge.getConflicts()).isEmpty();
    }

    @Test
    public void aNoteReceivedUnder2650ConflictsOnceAfterTheUpgradeAndThenSyncsCleanly()
            throws Exception {
        // The other device: it received the note under 2.6.50, whose restore wrote the sender's
        // hash-less id into its column and its own file name into the blocks. Its rebuilt
        // version cannot equal the upgraded remote one, so one conflict is expected; what must
        // not happen is the same conflict on every sync afterwards.
        byte[] bytes = "photo bytes".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        String senderUrl = "editorjs://attachments/note_77/1700000000000_123.png";
        String stableId = "11111111-1111-4111-8111-111111111111";
        String legacyId =
                java.util
                        .UUID
                        .nameUUIDFromBytes(
                                (stableId + "\n0\n" + senderUrl + "\nphoto.png")
                                        .getBytes(StandardCharsets.UTF_8))
                        .toString();
        int noteId = seedNote("Shopping", "Milk", null);
        File folder = new File(context.getFilesDir(), "attachments/note_" + noteId);
        assertThat(folder.mkdirs() || folder.isDirectory()).isTrue();
        try (FileOutputStream out = new FileOutputStream(new File(folder, legacyId + "-" + hash))) {
            out.write(bytes);
        }
        String receiverUrl =
                com.pasich.mynotes.extendedEditor.attach.AttachmentStorage.urlFor(
                        noteId, legacyId + "-" + hash);
        Note received = db.noteDao().getNoteSync(noteId);
        received.setAttachments(
                "[{\"url\":\""
                        + receiverUrl
                        + "\",\"name\":\"photo.png\",\"id\":\""
                        + legacyId
                        + "\"}]");
        received.setValueJson(
                "[{\"id\":\"blk1\",\"type\":\"attaches\",\"data\":{\"file\":{\"url\":\""
                        + receiverUrl
                        + "\",\"name\":\"photo.png\"}}}]");
        db.noteDao().addNote(received);
        Note sender = new Note().create("Shopping", "Milk", 1_000L, "");
        sender.setValueJson(
                "[{\"id\":\"blk1\",\"type\":\"attaches\",\"data\":{\"file\":{\"url\":\""
                        + senderUrl
                        + "\",\"name\":\"photo.png\"}}}]");
        SyncRecord onDrive = published2650(sender, senderUrl, "photo.png", hash, bytes.length);

        SyncRecord rebuilt = onlyNote(store.readSnapshot());
        com.pasich.mynotes.data.sync.SyncMergeResult first =
                new com.pasich.mynotes.data.sync.SyncMerger()
                        .merge(
                                new SyncSnapshot(Collections.singletonList(rebuilt)),
                                new SyncSnapshot(Collections.singletonList(onDrive)));
        assertThat(first.getConflicts()).hasSize(1);
        store.applySnapshot(first.getMergedSnapshot(), first.getConflicts());

        SyncRecord afterApply = onlyNote(store.readSnapshot());
        com.pasich.mynotes.data.sync.SyncMergeResult second =
                new com.pasich.mynotes.data.sync.SyncMerger()
                        .merge(
                                new SyncSnapshot(Collections.singletonList(afterApply)),
                                first.getMergedSnapshot());

        assertThat(second.getConflicts()).isEmpty();
        assertThat(afterApply.getCanonicalPayloadHash())
                .isEqualTo(first.getMergedSnapshot().getRecords().get(0).getCanonicalPayloadHash());
    }

    /**
     * The record 2.6.50 published for {@code note}, read back through today's decoder: blocks
     * naming the sender's own file, the id derived without the content hash, and the MIME type
     * 2.6.50 detected from the display name. Built by hand because that code is gone.
     */
    private static SyncRecord published2650(
            Note note, String blockUrl, String displayName, String hash, long size)
            throws IOException {
        String stableId = "11111111-1111-4111-8111-111111111111";
        String legacyId =
                java.util
                        .UUID
                        .nameUUIDFromBytes(
                                (stableId + "\n0\n" + blockUrl + "\n" + displayName)
                                        .getBytes(StandardCharsets.UTF_8))
                        .toString();
        return published2650WithIds(note, displayName, hash, size, legacyId);
    }

    /** As above, with the manifest ids given: one entry per id, repeats included. */
    private static SyncRecord published2650WithIds(
            Note note, String displayName, String hash, long size, String... ids)
            throws IOException {
        String stableId = "11111111-1111-4111-8111-111111111111";
        JsonObject payload = new com.google.gson.Gson().toJsonTree(note).getAsJsonObject();
        payload.remove("a");
        payload.remove("h");
        com.google.gson.JsonArray manifest = new com.google.gson.JsonArray();
        com.google.gson.JsonArray hashes = new com.google.gson.JsonArray();
        JsonObject names = new JsonObject();
        for (String id : ids) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", id);
            entry.addProperty("sha256", hash);
            entry.addProperty("mimeType", "image/png");
            entry.addProperty("size", size);
            entry.addProperty("path", "attachments/" + hash);
            entry.addProperty("displayName", displayName);
            manifest.add(entry);
            hashes.add(hash);
            names.addProperty(id, displayName);
        }
        payload.add("attachmentsManifest", manifest);
        payload.add("attachmentHashes", hashes);
        payload.add("attachmentNames", names);
        SyncRecord asPublished =
                SyncRecord.live(
                        SyncRecord.Type.NOTE,
                        stableId,
                        java.time.Instant.ofEpochMilli(1_000L),
                        payload);
        // Through the bundle codec, which is where an old payload meets today's decoder.
        com.pasich.mynotes.data.sync.SyncBundleCodec codec =
                new com.pasich.mynotes.data.sync.SyncBundleCodec();
        byte[] bundle =
                codec.encode(
                        new SyncSnapshot(Collections.singletonList(asPublished)),
                        java.time.Instant.now());
        return codec.decode(new ByteArrayInputStream(bundle))
                .getSnapshot()
                .find(SyncRecord.Type.NOTE, stableId);
    }

    // ------------------------------------------------- conflicts and records that moved on
    // ------------------------------------------------- conflicts and records that moved on

    @Test
    public void applySnapshot_doesNotStoreAConflictForARecordSkippedAsStale() throws Exception {
        int noteId = seedNote("Edited during the sync", "body", null);
        SyncRecord local = onlyNote(store.readSnapshot());
        // The user edits while the sync is on Drive.
        db.syncMetadataDao().touch(SyncMetadata.RECORD_TYPE_NOTE, noteId, 5_000L);
        JsonObject remotePayload = local.getPayload();
        remotePayload.addProperty("b", "Remote title");
        SyncRecord remote =
                SyncRecord.live(
                        SyncRecord.Type.NOTE,
                        local.getId(),
                        java.time.Instant.ofEpochMilli(2_000L),
                        remotePayload);
        com.pasich.mynotes.data.sync.SyncMergeResult merge =
                new com.pasich.mynotes.data.sync.SyncMerger()
                        .merge(
                                new SyncSnapshot(Collections.singletonList(local)),
                                new SyncSnapshot(Collections.singletonList(remote)));
        assertThat(merge.getConflicts()).hasSize(1);

        store.applySnapshot(merge.getMergedSnapshot(), merge.getConflicts());

        // The apply rightly skipped the note; the conflict it would have stored offered two
        // versions older than the edit, and resolving it wrote one of them over the edit.
        assertThat(store.getConflicts()).isEmpty();
        assertThat(db.noteDao().getNoteSync(noteId).getTitle()).isEqualTo("Edited during the sync");
    }

    @Test
    public void applySnapshot_retiresAnOpenConflictWhoseWinnerIsNoLongerTheLiveVersion()
            throws Exception {
        int noteId = seedNote("Current", "body", null);
        SyncRecord local = onlyNote(store.readSnapshot());
        db.syncConflictDao()
                .insertIgnoringDuplicates(
                        Collections.singletonList(
                                noteConflictRow(local.getId(), "old-winner", 900L, 800L)));
        JsonObject remotePayload = local.getPayload();
        remotePayload.addProperty("b", "Remote title");
        SyncRecord remote =
                SyncRecord.live(
                        SyncRecord.Type.NOTE,
                        local.getId(),
                        java.time.Instant.ofEpochMilli(2_000L),
                        remotePayload);
        com.pasich.mynotes.data.sync.SyncMergeResult merge =
                new com.pasich.mynotes.data.sync.SyncMerger()
                        .merge(
                                new SyncSnapshot(Collections.singletonList(local)),
                                new SyncSnapshot(Collections.singletonList(remote)));

        store.applySnapshot(merge.getMergedSnapshot(), merge.getConflicts());

        // The old row pre-selected a winner the record has moved past; one tap on it reverted
        // the live version. Only the conflict against the current version remains.
        List<com.pasich.mynotes.data.database.entities.SyncConflictEntity> unresolved =
                store.getUnresolvedConflicts();
        assertThat(unresolved).hasSize(1);
        assertThat(unresolved.get(0).winnerVersionId).isEqualTo(remote.getCanonicalPayloadHash());
        assertThat(noteId).isGreaterThan(0);
    }

    @Test
    public void resolveConflict_dropsAConflictTheRecordHasMovedPastInsteadOfApplyingIt()
            throws Exception {
        int noteId = seedNote("Newest", "body", null);
        db.syncMetadataDao().touch(SyncMetadata.RECORD_TYPE_NOTE, noteId, 5_000L);
        db.syncConflictDao()
                .insertIgnoringDuplicates(
                        Collections.singletonList(
                                noteConflictRow(
                                        "11111111-1111-4111-8111-111111111111",
                                        "stale-winner",
                                        3_000L,
                                        2_000L)));
        long conflictId = db.syncConflictDao().getAll().get(0).id;

        store.resolveConflict(conflictId, SyncResolution.KEEP_WINNER);

        // Both offered versions are older than what the user has now; applying either would
        // have overwritten the edit with a version that was never offered against it.
        assertThat(db.noteDao().getNoteSync(noteId).getTitle()).isEqualTo("Newest");
        assertThat(db.syncConflictDao().getById(conflictId)).isNull();
        assertThat(db.syncMetadataDao().get(SyncMetadata.RECORD_TYPE_NOTE, noteId).updatedAt)
                .isEqualTo(5_000L);
    }

    /** A stored note conflict whose winner is titled after its version id. */
    private com.pasich.mynotes.data.database.entities.SyncConflictEntity noteConflictRow(
            String stableId, String winnerVersionId, long winnerUpdatedAt, long loserUpdatedAt) {
        String winner =
                "{\"type\":\"note\",\"id\":\""
                        + stableId
                        + "\",\"updatedAt\":\""
                        + java.time.Instant.ofEpochMilli(winnerUpdatedAt)
                        + "\",\"deletedAt\":null,\"payload\":{\"b\":\""
                        + winnerVersionId
                        + "\",\"c\":\"body\"}}";
        String loser =
                "{\"type\":\"note\",\"id\":\""
                        + stableId
                        + "\",\"updatedAt\":\""
                        + java.time.Instant.ofEpochMilli(loserUpdatedAt)
                        + "\",\"deletedAt\":null,\"payload\":{\"b\":\"loser\",\"c\":\"body\"}}";
        return new com.pasich.mynotes.data.database.entities.SyncConflictEntity(
                SyncMetadata.RECORD_TYPE_NOTE,
                stableId,
                "pair-" + winnerVersionId,
                "REMOTE",
                "LOCAL",
                winnerVersionId,
                "loser-" + winnerVersionId,
                winner,
                loser,
                winnerUpdatedAt,
                loserUpdatedAt,
                false,
                false,
                "PENDING",
                false,
                1L,
                0L);
    }

    // ------------------------------------------------- settings edited during a sync

    @Test
    public void applySnapshot_leavesSettingsChangedDuringTheSyncAlone() throws Exception {
        PreferencesAdapter adapter = new PreferencesAdapter();
        RoomSyncStore preferencesStore = new RoomSyncStore(context, db, adapter.helper);
        preferencesStore.readState();
        adapter.current.set(preferencesWithTheme(1));
        preferencesStore.buildSnapshot();
        // The user flips a setting while the sync is on Drive; the settings screens write the
        // preferences directly and nothing touches the sync record for them.
        adapter.current.set(preferencesWithTheme(2));
        db.syncMetadataDao().setVersion(SyncMetadata.RECORD_TYPE_PREFERENCES, 0, 1_000L, null);
        SyncRecord remote =
                SyncRecord.live(
                        SyncRecord.Type.PREFERENCES,
                        "00000000-0000-4000-8000-000000000000",
                        java.time.Instant.ofEpochMilli(2_000L),
                        new com.google.gson.Gson()
                                .toJsonTree(preferencesWithTheme(3))
                                .getAsJsonObject());

        preferencesStore.applySnapshot(
                new SyncSnapshot(Collections.singletonList(remote)), Collections.emptyList());

        // Not committed, and the baseline not rewritten to hide it: the merged version was chosen
        // against settings that no longer exist.
        assertThat(adapter.committed.get()).isNull();
        assertThat(adapter.current.get().getThemeValue()).isEqualTo(2);
        // The next build sees the edit and publishes it.
        preferencesStore.buildSnapshot();
        assertThat(
                        db.syncMetadataDao()
                                .getByStableId(
                                        SyncMetadata.RECORD_TYPE_PREFERENCES,
                                        "00000000-0000-4000-8000-000000000000")
                                .updatedAt)
                .isGreaterThan(2_000L);
    }

    @Test
    public void applySnapshot_leavesSettingsChangedInsideTheApplyTransactionAlone()
            throws Exception {
        // The guard used to run once before the transaction. Applying many notes takes seconds;
        // a setting toggled in that window was still overwritten and its digest recorded as the
        // baseline, so the next build saw nothing to publish.
        PreferencesAdapter adapter = new PreferencesAdapter();
        RoomSyncStore preferencesStore =
                new RoomSyncStore(
                        context,
                        db,
                        adapter.helper,
                        com.pasich.mynotes.extendedEditor.attach.AttachmentStorage::resolve,
                        file -> sha256(readAll(new java.io.FileInputStream(file))),
                        record -> {
                            if (record.getType() == SyncRecord.Type.NOTE) {
                                // The user flips a setting while the notes are being applied.
                                adapter.current.set(preferencesWithTheme(2));
                            }
                        });
        preferencesStore.readState();
        adapter.current.set(preferencesWithTheme(1));
        int noteId = seedNote("Applied first", "body", null);
        SyncRecord note = onlyNote(preferencesStore.readSnapshot());
        db.syncMetadataDao().setVersion(SyncMetadata.RECORD_TYPE_PREFERENCES, 0, 1_000L, null);
        JsonObject changed = note.getPayload();
        changed.addProperty("b", "Remote title");
        SyncRecord remoteNote =
                SyncRecord.live(
                        SyncRecord.Type.NOTE,
                        note.getId(),
                        java.time.Instant.ofEpochMilli(2_000L),
                        changed);
        SyncRecord remotePreferences =
                SyncRecord.live(
                        SyncRecord.Type.PREFERENCES,
                        "00000000-0000-4000-8000-000000000000",
                        java.time.Instant.ofEpochMilli(2_000L),
                        new com.google.gson.Gson()
                                .toJsonTree(preferencesWithTheme(3))
                                .getAsJsonObject());

        preferencesStore.applySnapshot(
                new SyncSnapshot(java.util.Arrays.asList(remoteNote, remotePreferences)),
                Collections.emptyList(),
                SyncState.success("google-drive", java.time.Instant.now(), 0));

        assertThat(db.noteDao().getNoteSync(noteId).getTitle()).isEqualTo("Remote title");
        assertThat(adapter.committed.get()).isNull();
        assertThat(adapter.current.get().getThemeValue()).isEqualTo(2);
        // Not recorded as holding the remote version, and the final state still lands.
        assertThat(
                        db.syncMetadataDao()
                                .getByStableId(
                                        SyncMetadata.RECORD_TYPE_PREFERENCES,
                                        "00000000-0000-4000-8000-000000000000")
                                .updatedAt)
                .isEqualTo(1_000L);
        assertThat(preferencesStore.readState().getStatus()).isEqualTo(SyncState.Status.SUCCESS);
        preferencesStore.buildSnapshot();
        assertThat(
                        db.syncMetadataDao()
                                .getByStableId(
                                        SyncMetadata.RECORD_TYPE_PREFERENCES,
                                        "00000000-0000-4000-8000-000000000000")
                                .updatedAt)
                .isGreaterThan(2_000L);
    }

    @Test
    public void applySnapshot_skipsAnUnusablePreferencesPayloadInsteadOfFailingEverySync()
            throws Exception {
        PreferencesAdapter adapter = new PreferencesAdapter();
        RoomSyncStore preferencesStore = new RoomSyncStore(context, db, adapter.helper);
        preferencesStore.readState();
        int noteId = seedNote("Applied anyway", "body", null);
        SyncRecord note = onlyNote(preferencesStore.readSnapshot());
        JsonObject changed = note.getPayload();
        changed.addProperty("b", "Remote title");
        SyncRecord remoteNote =
                SyncRecord.live(
                        SyncRecord.Type.NOTE,
                        note.getId(),
                        java.time.Instant.ofEpochMilli(2_000L),
                        changed);
        // A payload with no "g": nothing this app ever wrote, but one such record on Drive used
        // to stop every device from syncing anything until someone changed a setting locally.
        SyncRecord invalidPreferences =
                SyncRecord.live(
                        SyncRecord.Type.PREFERENCES,
                        "00000000-0000-4000-8000-000000000000",
                        java.time.Instant.ofEpochMilli(2_000L),
                        new JsonObject());

        preferencesStore.applySnapshot(
                new SyncSnapshot(java.util.Arrays.asList(remoteNote, invalidPreferences)),
                Collections.emptyList());

        assertThat(db.noteDao().getNoteSync(noteId).getTitle()).isEqualTo("Remote title");
        assertThat(adapter.committed.get()).isNull();
    }

    @Test
    public void clearAfterDisconnect_dropsThePendingPreferencesJournal() throws Exception {
        db.syncPendingPreferencesDao()
                .upsert(
                        new com.pasich.mynotes.data.database.entities.SyncPendingPreferencesEntity(
                                1, "{}", "target", "baseline", 0L, false, 0L, ""));

        store.clearAfterDisconnect();

        // Left behind, a fresh store replayed the disconnected account's settings onto the
        // device at its next seeding.
        assertThat(db.syncPendingPreferencesDao().get()).isNull();
        assertThat(db.syncPendingPreferencesDao().getIncludingQuarantined()).isNull();
    }

    // ------------------------------------------------- records deleted here, edited elsewhere

    @Test
    public void applySnapshot_bringsBackATaskDeletedHereAndEditedElsewhere() throws Exception {
        com.pasich.mynotes.data.model.Task task = new com.pasich.mynotes.data.model.Task("Call", 0);
        int taskId = (int) db.taskDao().insertTask(task);
        String stableId = "33333333-3333-4333-8333-333333333333";
        db.syncMetadataDao()
                .insertIfAbsent(
                        new SyncMetadataEntity(
                                SyncMetadata.RECORD_TYPE_TASK, taskId, stableId, 1_000L, null));
        db.taskDao().deleteById(taskId);
        db.syncMetadataDao().markDeleted(SyncMetadata.RECORD_TYPE_TASK, taskId, 1_500L);
        JsonObject edited = new com.google.gson.Gson().toJsonTree(task).getAsJsonObject();
        edited.addProperty("title", "Call back");
        SyncMetadata.stripDeviceLocalFields(SyncMetadata.RECORD_TYPE_TASK, edited);

        store.applySnapshot(
                new SyncSnapshot(
                        Collections.singletonList(
                                SyncRecord.live(
                                        SyncRecord.Type.TASK,
                                        stableId,
                                        java.time.Instant.ofEpochMilli(3_000L),
                                        edited))),
                Collections.emptyList());

        // @Update on the deleted row was a silent no-op while the tombstone was cleared anyway,
        // so the task never came back here and the remote edit was re-applied to nothing forever.
        com.pasich.mynotes.data.model.Task revived = db.taskDao().getTaskSync(taskId);
        assertThat(revived).isNotNull();
        assertThat(revived.getTitle()).isEqualTo("Call back");
        assertThat(db.syncMetadataDao().get(SyncMetadata.RECORD_TYPE_TASK, taskId).deletedAt)
                .isNull();
    }

    // ------------------------------------------------- tags created by name on two devices

    @Test
    public void applySnapshot_reconcilesATagCreatedUnderTheSameNameOnAnotherDevice()
            throws Exception {
        com.pasich.mynotes.data.model.Tag local =
                new com.pasich.mynotes.data.model.Tag().create("Work");
        long localId = db.tagsDao().addTag(local);
        db.syncMetadataDao()
                .insertIfAbsent(
                        new SyncMetadataEntity(
                                SyncMetadata.RECORD_TYPE_TAG,
                                localId,
                                "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                                1_000L,
                                null));

        store.applySnapshot(
                new SyncSnapshot(
                        Collections.singletonList(
                                remoteTag("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "Work"))),
                Collections.emptyList());

        // One "Work", under the identity both devices will settle on; the other identity is
        // tombstoned so the next sync retires it everywhere instead of leaving two rows.
        assertThat(tagsNamed("Work")).isEqualTo(1);
        SyncMetadataEntity winner =
                db.syncMetadataDao()
                        .getByStableId(
                                SyncMetadata.RECORD_TYPE_TAG,
                                "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        assertThat(winner).isNotNull();
        assertThat(winner.deletedAt).isNull();
        assertThat(
                        db.syncMetadataDao()
                                .getByStableId(
                                        SyncMetadata.RECORD_TYPE_TAG,
                                        "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
                                .deletedAt)
                .isNotNull();
    }

    @Test
    public void applySnapshot_keepsTheLocalTagWhenItHoldsTheWinningIdentity() throws Exception {
        com.pasich.mynotes.data.model.Tag local =
                new com.pasich.mynotes.data.model.Tag().create("Work");
        long localId = db.tagsDao().addTag(local);
        db.syncMetadataDao()
                .insertIfAbsent(
                        new SyncMetadataEntity(
                                SyncMetadata.RECORD_TYPE_TAG,
                                localId,
                                "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                                1_000L,
                                null));

        store.applySnapshot(
                new SyncSnapshot(
                        Collections.singletonList(
                                remoteTag("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", "Work"))),
                Collections.emptyList());

        assertThat(tagsNamed("Work")).isEqualTo(1);
        assertThat(db.tagsDao().getTagSync(localId)).isNotNull();
        assertThat(
                        db.syncMetadataDao()
                                .getByStableId(
                                        SyncMetadata.RECORD_TYPE_TAG,
                                        "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"))
                .isNull();
    }

    @Test
    public void applySnapshot_doesNotReviveADeletedTagBesideItsSameNamedSuccessor()
            throws Exception {
        // Device A deleted "Work" and created a new "Work"; device B edited the old one later.
        // Reviving the old row put a second "Work" beside the new one. The local identity holds
        // the smaller id, so the old one is tombstoned afresh and retires everywhere next sync.
        long newId = db.tagsDao().addTag(new com.pasich.mynotes.data.model.Tag().create("Work"));
        db.syncMetadataDao()
                .insertIfAbsent(
                        new SyncMetadataEntity(
                                SyncMetadata.RECORD_TYPE_TAG,
                                newId,
                                "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                                2_000L,
                                null));
        long oldId = db.tagsDao().addTag(new com.pasich.mynotes.data.model.Tag().create("Work"));
        db.syncMetadataDao()
                .insertIfAbsent(
                        new SyncMetadataEntity(
                                SyncMetadata.RECORD_TYPE_TAG,
                                oldId,
                                "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                                1_000L,
                                null));
        db.tagsDao().deleteById(oldId);
        db.syncMetadataDao().markDeleted(SyncMetadata.RECORD_TYPE_TAG, oldId, 1_500L);
        SyncRecord editedElsewhere =
                SyncRecord.live(
                        SyncRecord.Type.TAG,
                        "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                        java.time.Instant.ofEpochMilli(3_000L),
                        remoteTag("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", "Work").getPayload());

        store.applySnapshot(
                new SyncSnapshot(Collections.singletonList(editedElsewhere)),
                Collections.emptyList());

        assertThat(tagsNamed("Work")).isEqualTo(1);
        assertThat(db.tagsDao().getTagSync(newId)).isNotNull();
        SyncMetadataEntity old = db.syncMetadataDao().get(SyncMetadata.RECORD_TYPE_TAG, oldId);
        assertThat(old.deletedAt).isNotNull();
        assertThat(old.updatedAt).isGreaterThan(3_000L);
    }

    @Test
    public void applySnapshot_revivesADeletedTagAndRetiresItsSameNamedSuccessorWhenItWins()
            throws Exception {
        long newId = db.tagsDao().addTag(new com.pasich.mynotes.data.model.Tag().create("Work"));
        db.syncMetadataDao()
                .insertIfAbsent(
                        new SyncMetadataEntity(
                                SyncMetadata.RECORD_TYPE_TAG,
                                newId,
                                "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                                2_000L,
                                null));
        long oldId = db.tagsDao().addTag(new com.pasich.mynotes.data.model.Tag().create("Work"));
        db.syncMetadataDao()
                .insertIfAbsent(
                        new SyncMetadataEntity(
                                SyncMetadata.RECORD_TYPE_TAG,
                                oldId,
                                "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                                1_000L,
                                null));
        db.tagsDao().deleteById(oldId);
        db.syncMetadataDao().markDeleted(SyncMetadata.RECORD_TYPE_TAG, oldId, 1_500L);
        SyncRecord editedElsewhere =
                SyncRecord.live(
                        SyncRecord.Type.TAG,
                        "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                        java.time.Instant.ofEpochMilli(3_000L),
                        remoteTag("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "Work").getPayload());

        store.applySnapshot(
                new SyncSnapshot(Collections.singletonList(editedElsewhere)),
                Collections.emptyList());

        assertThat(tagsNamed("Work")).isEqualTo(1);
        assertThat(db.tagsDao().getTagSync(oldId)).isNotNull();
        assertThat(db.syncMetadataDao().get(SyncMetadata.RECORD_TYPE_TAG, oldId).deletedAt)
                .isNull();
        assertThat(db.syncMetadataDao().get(SyncMetadata.RECORD_TYPE_TAG, newId).deletedAt)
                .isNotNull();
    }

    private static SyncRecord remoteTag(String stableId, String name) {
        JsonObject payload = new JsonObject();
        payload.addProperty("b", name);
        payload.addProperty("c", 0);
        payload.addProperty("d", 0);
        payload.addProperty("e", -1);
        return SyncRecord.live(
                SyncRecord.Type.TAG, stableId, java.time.Instant.ofEpochMilli(1_000L), payload);
    }

    private int tagsNamed(String name) {
        int count = 0;
        for (com.pasich.mynotes.data.model.Tag tag : db.tagsDao().getTags().blockingFirst()) {
            if (name.equals(tag.getNameTag())) count++;
        }
        return count;
    }

    // ------------------------------------------------- blobs a fresh store has to find

    @Test
    public void hasAttachment_findsABlobInANoteFolderWithoutHavingBuiltASnapshot()
            throws Exception {
        byte[] bytes = "owned bytes".getBytes(StandardCharsets.UTF_8);
        seedNoteWithAttachment("owned.png", bytes);

        // The Backup screen's store, resolving a conflict the worker's store found: it has never
        // built a snapshot, and the only index of note-folder files used to be built there.
        RoomSyncStore fresh = new RoomSyncStore(context, db, mock(PreferenceHelper.class));

        assertThat(fresh.hasAttachment(sha256(bytes))).isTrue();
    }

    // ------------------------------------------------- attachments whose file is gone

    @Test
    public void buildSnapshot_describesAMissingFileFromWhatTheColumnRemembersAndRestoresIt()
            throws Exception {
        byte[] bytes = "restored later".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        String logicalId = "7d444840-9dc0-11d1-b245-5ffdce74fad2";
        int noteId = seedNote("Repairable", "body", null);
        // The column a sync wrote: the file it names is gone, but the id, hash, size and type
        // are all there — enough to publish the note and to fetch the bytes back.
        Note note = db.noteDao().getNoteSync(noteId);
        note.setAttachments(
                "[{\"url\":\""
                        + com.pasich.mynotes.extendedEditor.attach.AttachmentStorage.urlFor(
                                noteId, logicalId + "-" + hash)
                        + "\",\"name\":\"photo.png\",\"id\":\""
                        + logicalId
                        + "\",\"sha256\":\""
                        + hash
                        + "\",\"size\":"
                        + bytes.length
                        + ",\"mimeType\":\"image/png\"}]");
        db.noteDao().addNote(note);
        // The blob is in the sync cache, as SyncService puts it after downloading from Drive.
        store.writeAttachment(hash, bytes.length, new ByteArrayInputStream(bytes));

        SnapshotBuildResult result = store.buildSnapshot();

        // Every sync used to end in MISSING_ATTACHMENT here, with no way back but deleting the
        // block by hand.
        assertThat(result.isPublishable()).isTrue();
        JsonObject payload = onlyNote(result.requireSnapshot()).getPayload();
        assertThat(payload.getAsJsonArray("attachmentHashes").get(0).getAsString()).isEqualTo(hash);
        assertThat(
                        payload.getAsJsonArray("attachmentsManifest")
                                .get(0)
                                .getAsJsonObject()
                                .get("mimeType")
                                .getAsString())
                .isEqualTo("image/png");

        store.applySnapshot(result.requireSnapshot(), Collections.emptyList());

        File repaired = resolveFirstAttachment(db.noteDao().getNoteSync(noteId).getAttachments());
        assertThat(repaired.isFile()).isTrue();
        assertThat(readAll(new java.io.FileInputStream(repaired))).isEqualTo(bytes);
    }

    @Test
    public void buildSnapshot_stillNamesTheNoteWhenARememberedBlobIsNowhereToBeFound()
            throws Exception {
        // Published from the remembered hash and size, the note is fine as long as the cache or
        // Drive holds the bytes. When neither does — a different account, say — the service has
        // to be able to name the note, not fail forever on a hash.
        byte[] bytes = "gone everywhere".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        String logicalId = "7d444840-9dc0-11d1-b245-5ffdce74fad2";
        int noteId = seedNote("Shopping list", "body", null);
        Note note = db.noteDao().getNoteSync(noteId);
        note.setAttachments(
                "[{\"url\":\""
                        + com.pasich.mynotes.extendedEditor.attach.AttachmentStorage.urlFor(
                                noteId, logicalId + "-" + hash)
                        + "\",\"name\":\"photo.png\",\"id\":\""
                        + logicalId
                        + "\",\"sha256\":\""
                        + hash
                        + "\",\"size\":"
                        + bytes.length
                        + ",\"mimeType\":\"image/png\"}]");
        db.noteDao().addNote(note);

        SnapshotBuildResult result = store.buildSnapshot();

        assertThat(result.isPublishable()).isTrue();
        assertThat(store.hasAttachment(hash)).isFalse();
        SnapshotProblem problem = store.describeMissingAttachment(hash);
        assertThat(problem).isNotNull();
        assertThat(problem.getKind()).isEqualTo(SnapshotProblem.Kind.MISSING_ATTACHMENT);
        assertThat(problem.getLabel()).isEqualTo("Shopping list");
    }

    @Test
    public void buildSnapshot_namesTheNoteWhoseAttachmentCannotBeFound() {
        int noteId = seedNote("Shopping list", "body", null);
        Note seeded = db.noteDao().getNoteSync(noteId);
        seeded.setAttachments("[" + attachmentJson(noteId, "gone.png") + "]");
        db.noteDao().addNote(seeded);

        SnapshotBuildResult.SnapshotBuildException error = assertSnapshotBuildFails(store);

        // The account screen shows this string; "MISSING_ATTACHMENT" alone left the user to
        // guess which note to open.
        assertThat(error).hasMessageThat().contains("note \"Shopping list\"");
        assertThat(error.getProblems().get(0).getLabel()).isEqualTo("Shopping list");
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
    public void aLocallyBuiltNoteSurvivesABundleRoundTripUnchanged() throws Exception {
        // The editor stores "[]" for a note that simply has no attachments.
        int noteId = seedNote("Alpha note", "milk bread coffee", "[]");
        assertThat(noteId).isGreaterThan(0);

        SyncRecord local = onlyNote(store.readSnapshot());
        com.pasich.mynotes.data.sync.SyncBundleCodec codec =
                new com.pasich.mynotes.data.sync.SyncBundleCodec();
        byte[] bundle = codec.encode(store.readSnapshot(), java.time.Instant.now());
        SyncRecord decoded =
                codec.decode(new ByteArrayInputStream(bundle))
                        .getSnapshot()
                        .find(SyncRecord.Type.NOTE, local.getId());

        // Empty attachment arrays were written locally but never survive the wire, so the two
        // shapes hashed differently and every attachment-free note conflicted with itself on
        // every sync — reproduced on a device before this was fixed.
        assertThat(decoded).isNotNull();
        assertThat(decoded.getCanonicalPayloadHash()).isEqualTo(local.getCanonicalPayloadHash());
    }

    @Test
    public void aNoteWithAnAttachmentAlsoSurvivesTheRoundTripUnchanged() throws Exception {
        seedNoteWithAttachment("photo.png", "photo bytes".getBytes(StandardCharsets.UTF_8));

        SyncRecord local = onlyNote(store.readSnapshot());
        com.pasich.mynotes.data.sync.SyncBundleCodec codec =
                new com.pasich.mynotes.data.sync.SyncBundleCodec();
        byte[] bundle = codec.encode(store.readSnapshot(), java.time.Instant.now());
        SyncRecord decoded =
                codec.decode(new ByteArrayInputStream(bundle))
                        .getSnapshot()
                        .find(SyncRecord.Type.NOTE, local.getId());

        assertThat(decoded).isNotNull();
        assertThat(decoded.getCanonicalPayloadHash()).isEqualTo(local.getCanonicalPayloadHash());
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
        return seedNote(title, value, attachmentsJson, "11111111-1111-4111-8111-111111111111");
    }

    private int seedNote(String title, String value, String attachmentsJson, String stableId) {
        Note note = new Note().create(title, value, 1_000L, "");
        note.setAttachments(attachmentsJson);
        int id = db.noteDao().addNote(note).intValue();
        db.syncMetadataDao()
                .insertIfAbsent(
                        new SyncMetadataEntity(
                                SyncMetadata.RECORD_TYPE_NOTE, id, stableId, 1_000L, null));
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

    @Test
    public void applyingChangedPreferences_reportsThatTheScreenMustRedraw() throws Exception {
        PreferencesAdapter adapter = new PreferencesAdapter();
        RoomSyncStore preferencesStore = new RoomSyncStore(context, db, adapter.helper);
        preferencesStore.readState();
        long conflictId = seedPreferencesConflict(9, 11);

        preferencesStore.resolveConflict(conflictId, SyncResolution.KEEP_DRIVE);

        // Theme and UI scale are read when an activity is created, so the visible screen has to
        // be told; without this a theme from another device stayed invisible until the user
        // navigated away and back.
        assertThat(preferencesStore.consumeAppliedPreferencesChange()).isTrue();
        // The flag is consumed, so a later sync that changes nothing does not redraw.
        assertThat(preferencesStore.consumeAppliedPreferencesChange()).isFalse();
    }

    @Test
    public void applyingIdenticalPreferences_doesNotAskForARedraw() throws Exception {
        PreferencesAdapter adapter = new PreferencesAdapter();
        adapter.current.set(preferencesWithTheme(11));
        RoomSyncStore preferencesStore = new RoomSyncStore(context, db, adapter.helper);
        preferencesStore.readState();
        long conflictId = seedPreferencesConflict(9, 11);

        preferencesStore.resolveConflict(conflictId, SyncResolution.KEEP_DRIVE);

        // Same values in, same values out: recreating the screen would be a visible flicker for
        // no reason.
        assertThat(preferencesStore.consumeAppliedPreferencesChange()).isFalse();
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
        return seedNoteWithAttachment(fileName, fileName, bytes);
    }

    private int seedNoteWithAttachment(String fileName, String displayName, byte[] bytes)
            throws IOException {
        int id = seedNote("With attachment", "body", null);
        File folder = new File(context.getFilesDir(), "attachments/note_" + id);
        assertThat(folder.mkdirs() || folder.isDirectory()).isTrue();
        try (FileOutputStream out = new FileOutputStream(new File(folder, fileName))) {
            out.write(bytes);
        }
        // Production shape: EditorJSInterface writes editorjs://attachments/note_<id>/<file>.
        String json = "[" + attachmentJson(id, fileName, displayName) + "]";
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
        return attachmentJson(noteId, name, name);
    }

    private static String attachmentJson(int noteId, String fileName, String displayName) {
        return "{\"url\":\""
                + com.pasich.mynotes.extendedEditor.attach.AttachmentStorage.urlFor(
                        noteId, fileName)
                + "\",\"name\":\""
                + displayName
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
