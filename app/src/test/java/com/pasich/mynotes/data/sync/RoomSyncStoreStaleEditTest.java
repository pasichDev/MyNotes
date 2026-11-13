package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import android.content.Context;
import androidx.room.Room;
import com.google.gson.JsonObject;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.database.entities.SyncMetadataEntity;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.preferences.PreferenceHelper;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * An edit saved while a sync is in flight survives the apply that sync ends with.
 *
 * <p>The apply skips a record that moved on locally after the build read it. Two things defeated
 * that guard: the build paired content read first with a timestamp read afterwards, and the guard
 * compared against the merged record's timestamp, which is another device's clock.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RoomSyncStoreStaleEditTest {

    private static final String NOTE_ID = "11111111-1111-4111-8111-111111111111";
    private static final String ATTACHMENT_ID = "66666666-6666-4666-8666-666666666666";

    private AppDatabase db;
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        db =
                Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                        .allowMainThreadQueries()
                        .build();
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void anEditSavedWhileTheBuildReadsTheNoteIsNotRevertedByTheApply() throws Exception {
        File blob = File.createTempFile("attachment", ".bin");
        blob.deleteOnExit();
        Files.write(blob.toPath(), "blob".getBytes(StandardCharsets.UTF_8));
        int noteId =
                seedNote(
                        "Shopping",
                        "[{\"id\":\""
                                + ATTACHMENT_ID
                                + "\",\"url\":\"file:///notes/a.bin\",\"name\":\"a\","
                                + "\"extension\":\"bin\",\"size\":4}]");
        boolean[] edited = {false};
        RoomSyncStore store =
                new RoomSyncStore(
                        context,
                        db,
                        mock(PreferenceHelper.class),
                        (ignored, attachment) -> blob,
                        file -> {
                            // The editor saves after the build read the note's row but before it
                            // finished describing the note.
                            if (!edited[0]) {
                                edited[0] = true;
                                saveEdit(noteId, "Edited meanwhile", 2_000L);
                            }
                            return Sha256.of(file);
                        });

        SyncSnapshot built = store.readSnapshot();
        // Nothing on the remote side: the merge result is the build itself.
        store.applySnapshot(built, Collections.emptyList());

        assertThat(edited[0]).isTrue();
        assertThat(db.noteDao().getNoteSync(noteId).getTitle()).isEqualTo("Edited meanwhile");
    }

    @Test
    public void aLocalEditDuringSyncSurvivesARemoteVersionFromAClockAhead() throws Exception {
        int noteId = seedNote("Shopping", null);
        RoomSyncStore store = new RoomSyncStore(context, db, mock(PreferenceHelper.class));
        SyncSnapshot built = store.readSnapshot();

        saveEdit(noteId, "Local edit", 2_000L);
        // The remote device's clock runs well ahead of this one.
        store.applySnapshot(
                new SyncSnapshot(
                        Collections.singletonList(
                                renamed(built, "Remote", Instant.ofEpochMilli(50_000L)))),
                Collections.emptyList());

        assertThat(db.noteDao().getNoteSync(noteId).getTitle()).isEqualTo("Local edit");
    }

    @Test
    public void anUntouchedNoteTakesANewerRemoteVersionFromAClockBehind() throws Exception {
        int noteId = seedNote("Shopping", null);
        RoomSyncStore store = new RoomSyncStore(context, db, mock(PreferenceHelper.class));
        SyncSnapshot built = store.readSnapshot();

        // The merge chose the remote version; its device's clock runs behind this one.
        store.applySnapshot(
                new SyncSnapshot(
                        Collections.singletonList(
                                renamed(built, "Remote", Instant.ofEpochMilli(500L)))),
                Collections.emptyList());

        assertThat(db.noteDao().getNoteSync(noteId).getTitle()).isEqualTo("Remote");
    }

    private void saveEdit(int noteId, String title, long at) {
        Note stored = db.noteDao().getNoteSync(noteId);
        db.noteDao()
                .updateNoteContent(
                        noteId,
                        title,
                        stored.getValue(),
                        stored.getValueJson(),
                        stored.getDate(),
                        stored.getTag(),
                        stored.getAttachments());
        db.syncMetadataDao().touch(SyncMetadata.RECORD_TYPE_NOTE, noteId, at);
    }

    private static SyncRecord renamed(SyncSnapshot snapshot, String title, Instant updatedAt) {
        SyncRecord note = snapshot.find(SyncRecord.Type.NOTE, NOTE_ID);
        JsonObject payload = note.getPayload().deepCopy();
        payload.addProperty("b", title);
        return SyncRecord.live(SyncRecord.Type.NOTE, NOTE_ID, updatedAt, payload);
    }

    private int seedNote(String title, String attachmentsJson) {
        Note note = new Note().create(title, "Milk", 1_000L, "");
        note.setAttachments(attachmentsJson);
        int id = db.noteDao().addNote(note).intValue();
        db.syncMetadataDao()
                .insertIfAbsent(
                        new SyncMetadataEntity(
                                SyncMetadata.RECORD_TYPE_NOTE, id, NOTE_ID, 1_000L, null));
        return id;
    }
}
