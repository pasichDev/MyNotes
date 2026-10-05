package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pasich.mynotes.data.database.dao.NoteDao;
import com.pasich.mynotes.data.database.dao.SyncMetadataDao;
import com.pasich.mynotes.data.database.dao.TagsDao;
import com.pasich.mynotes.data.database.dao.TaskCategoryDao;
import com.pasich.mynotes.data.database.dao.TaskDao;
import com.pasich.mynotes.data.database.dao.Transactions;
import com.pasich.mynotes.data.database.entities.NoteVersionEntity;
import com.pasich.mynotes.data.history.FakeNoteVersionDao;
import com.pasich.mynotes.data.history.NoteHistory;
import com.pasich.mynotes.data.history.NoteVersionReason;
import com.pasich.mynotes.data.model.Note;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

/** Version history as the coordinator keeps it: when a snapshot is due, the cap, the cleanup. */
public class SyncMutationCoordinatorHistoryTest {

    private static final long MINUTE = 60_000L;

    private final Map<Integer, Note> notes = new HashMap<>();
    private final long[] now = {1_000_000L};
    private NoteDao noteDao;
    private SyncMetadataDao syncMetadataDao;
    private FakeNoteVersionDao versions;
    private SyncMutationCoordinator coordinator;

    @Before
    public void setUp() {
        noteDao = mock(NoteDao.class);
        syncMetadataDao = mock(SyncMetadataDao.class);
        versions = new FakeNoteVersionDao();
        when(noteDao.getNoteSync(anyInt()))
                .thenAnswer(call -> copy(notes.get(call.getArgument(0))));
        doAnswer(
                        call -> {
                            Note stored = notes.get((Integer) call.getArgument(0));
                            stored.setTitle(call.getArgument(1));
                            stored.setValue(call.getArgument(2));
                            stored.setValueJson(call.getArgument(3));
                            stored.setDate(call.getArgument(4));
                            stored.setAttachments(call.getArgument(6));
                            return null;
                        })
                .when(noteDao)
                .updateNoteContent(
                        anyInt(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyLong(),
                        anyString(),
                        org.mockito.ArgumentMatchers.any());
        coordinator =
                new SyncMutationCoordinator(
                        new SyncMutationCoordinator.TransactionExecutor() {
                            @Override
                            public <T> T run(
                                    SyncMutationCoordinator.TransactionCallable<T> callable) {
                                return callable.call();
                            }
                        },
                        noteDao,
                        mock(TaskDao.class),
                        mock(TagsDao.class),
                        mock(TaskCategoryDao.class),
                        mock(Transactions.class),
                        syncMetadataDao,
                        () -> now[0],
                        () -> "stable",
                        (note, previousId) -> false,
                        new NoteHistory(versions, noteId -> "stable-" + noteId));
        store(7, "Shopping", "milk");
    }

    @Test
    public void updateNoteContent_keepsTheReplacedTextOnlyOncePerInterval() {
        coordinator.updateNoteContent(edit(7, "Shopping", "milk, bread"));
        now[0] += MINUTE;
        coordinator.updateNoteContent(edit(7, "Shopping", "milk, bread, eggs"));

        // The first save of a session keeps what was there before it; the saves that follow a
        // few seconds or minutes later do not each add a near-identical copy.
        List<NoteVersionEntity> kept = versions.getForNoteSync(7);
        assertThat(kept).hasSize(1);
        assertThat(kept.get(0).value).isEqualTo("milk");
        assertThat(kept.get(0).reason).isEqualTo(NoteVersionReason.AUTOSAVE.name());
        assertThat(kept.get(0).noteStableId).isEqualTo("stable-7");

        now[0] += NoteHistory.AUTOSAVE_INTERVAL_MILLIS;
        coordinator.updateNoteContent(edit(7, "Shopping", "milk, bread, eggs, tea"));

        assertThat(versions.getForNoteSync(7)).hasSize(2);
        assertThat(versions.getLatest(7).value).isEqualTo("milk, bread, eggs");
    }

    @Test
    public void updateNoteContent_keepsALargeDeletionAtOnce() {
        String longText = "a".repeat(NoteHistory.LARGE_EDIT_CHARS + 50);
        store(8, "Essay", longText);
        coordinator.updateNoteContent(edit(8, "Essay", longText + " more"));
        now[0] += MINUTE;

        // Select-all and type over it, a minute later: inside the interval, kept anyway.
        coordinator.updateNoteContent(edit(8, "Essay", "oops"));

        List<NoteVersionEntity> kept = versions.getForNoteSync(8);
        assertThat(kept).hasSize(2);
        assertThat(kept.get(0).value).isEqualTo(longText + " more");
    }

    @Test
    public void updateNoteContent_keepsNothingForAnUnchangedOrEmptyNote() {
        coordinator.updateNoteContent(edit(7, "Shopping", "milk"));
        store(9, "", "");
        coordinator.updateNoteContent(edit(9, "First words", ""));

        assertThat(versions.rows).isEmpty();
    }

    @Test
    public void history_isCappedPerNoteWithTheOldestDroppedFirst() {
        for (int i = 0; i < NoteHistory.MAX_VERSIONS_PER_NOTE + 5; i++) {
            now[0] += NoteHistory.AUTOSAVE_INTERVAL_MILLIS;
            coordinator.updateNoteContent(edit(7, "Shopping", "item " + i));
        }

        List<NoteVersionEntity> kept = versions.getForNoteSync(7);
        assertThat(kept).hasSize(NoteHistory.MAX_VERSIONS_PER_NOTE);
        // Newest first; the very first states ("milk", "item 0"...) were pruned.
        assertThat(kept.get(0).value).isEqualTo("item " + (NoteHistory.MAX_VERSIONS_PER_NOTE + 3));
        assertThat(kept.get(kept.size() - 1).value).isEqualTo("item 4");
    }

    @Test
    public void restoreNoteVersion_keepsTheCurrentTextAndWritesTheVersionAsAnEdit() {
        coordinator.updateNoteContent(edit(7, "Shopping", "milk, bread"));
        long versionId = versions.getLatest(7).id;
        now[0] += MINUTE;

        assertThat(coordinator.restoreNoteVersion(7, versionId)).isTrue();

        assertThat(notes.get(7).getValue()).isEqualTo("milk");
        assertThat(notes.get(7).getDate()).isEqualTo(now[0]);
        NoteVersionEntity before = versions.getLatest(7);
        assertThat(before.value).isEqualTo("milk, bread");
        assertThat(before.reason).isEqualTo(NoteVersionReason.PRE_RESTORE.name());
        // An ordinary edit as far as sync is concerned.
        verify(syncMetadataDao).touch(SyncMetadata.RECORD_TYPE_NOTE, 7L, now[0]);
    }

    @Test
    public void restoreNoteVersion_refusesAVersionOfAnotherNote() {
        store(8, "Other", "text");
        coordinator.updateNoteContent(edit(8, "Other", "changed"));
        long otherVersion = versions.getLatest(8).id;

        assertThat(coordinator.restoreNoteVersion(7, otherVersion)).isFalse();
        assertThat(notes.get(7).getValue()).isEqualTo("milk");
    }

    @Test
    public void deletingNotesForGood_removesTheirHistory() {
        store(8, "Other", "text");
        coordinator.updateNoteContent(edit(7, "Shopping", "milk, bread"));
        coordinator.updateNoteContent(edit(8, "Other", "changed"));

        coordinator.deleteNote(notes.get(7));
        assertThat(versions.getForNoteSync(7)).isEmpty();
        assertThat(versions.getForNoteSync(8)).hasSize(1);

        when(noteDao.getTrashNoteIdsSync()).thenReturn(new ArrayList<>(List.of(8)));
        coordinator.deleteAllTrashNotes();
        assertThat(versions.rows).isEmpty();
    }

    @Test
    public void movingToTrash_keepsHistory() {
        coordinator.updateNoteContent(edit(7, "Shopping", "milk, bread"));

        coordinator.moveNoteToTrash(7);

        assertThat(versions.getForNoteSync(7)).hasSize(1);
    }

    @Test
    public void clearAllUserData_removesAllHistory() {
        coordinator.updateNoteContent(edit(7, "Shopping", "milk, bread"));
        when(noteDao.getAllNoteIdsSync()).thenReturn(new ArrayList<>(List.of(7)));

        coordinator.clearAllUserData();

        assertThat(versions.rows).isEmpty();
    }

    @Test
    public void sweepOrphanNoteVersions_removesHistoryWithoutItsNote() {
        coordinator.updateNoteContent(edit(7, "Shopping", "milk, bread"));
        versions.liveNoteIds.add(8);

        assertThat(coordinator.sweepOrphanNoteVersions()).isEqualTo(1);
        assertThat(versions.rows).isEmpty();
    }

    private void store(int id, String title, String value) {
        Note note = new Note().create(title, value, 10L, "");
        note.setId(id);
        note.setValueJson("");
        note.setAttachments("[]");
        notes.put(id, note);
    }

    private static Note edit(int id, String title, String value) {
        Note note = new Note().create(title, value, 20L, "");
        note.setId(id);
        note.setValueJson("");
        note.setAttachments("[]");
        return note;
    }

    private static Note copy(Note source) {
        if (source == null) return null;
        Note copy = new Note();
        copy.copyFrom(source);
        copy.setId(source.getId());
        return copy;
    }
}
