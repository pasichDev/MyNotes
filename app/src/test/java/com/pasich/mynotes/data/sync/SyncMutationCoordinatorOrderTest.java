package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pasich.mynotes.data.database.dao.NoteDao;
import com.pasich.mynotes.data.database.dao.SyncMetadataDao;
import com.pasich.mynotes.data.database.dao.TagsDao;
import com.pasich.mynotes.data.database.dao.TaskCategoryDao;
import com.pasich.mynotes.data.database.dao.TaskDao;
import com.pasich.mynotes.data.database.dao.Transactions;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.order.CustomOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

/** The custom note order as the coordinator keeps it: local writes, new notes on top. */
public class SyncMutationCoordinatorOrderTest {

    private final Map<Integer, Note> notes = new LinkedHashMap<>();
    private NoteDao noteDao;
    private SyncMetadataDao syncMetadataDao;
    private SyncMutationCoordinator coordinator;

    @Before
    public void setUp() {
        noteDao = mock(NoteDao.class);
        syncMetadataDao = mock(SyncMetadataDao.class);
        when(noteDao.getCustomPositionSync(anyInt()))
                .thenAnswer(
                        call -> {
                            Note note = notes.get((Integer) call.getArgument(0));
                            return note == null ? null : note.getCustomPosition();
                        });
        when(noteDao.getHighestCustomPositionSync())
                .thenAnswer(
                        call -> {
                            long highest = 0;
                            for (Note note : notes.values())
                                highest = Math.max(highest, note.getCustomPosition());
                            return highest;
                        });
        when(noteDao.getAllNotesSync()).thenAnswer(call -> new ArrayList<>(notes.values()));
        doAnswer(
                        call -> {
                            notes.get((Integer) call.getArgument(0))
                                    .setCustomPosition(call.getArgument(1));
                            return null;
                        })
                .when(noteDao)
                .setCustomPositionSync(anyInt(), anyLong());
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
                        () -> 5_000L,
                        () -> "stable");
        put(1, 3 * CustomOrder.STEP);
        put(2, 2 * CustomOrder.STEP);
        put(3, CustomOrder.STEP);
    }

    @Test
    public void moveNoteInCustomOrder_writesOneRowAndIsNotAnEdit() {
        // Note 3 dropped between notes 1 and 2.
        coordinator.moveNoteInCustomOrder(3, 1, 2);

        assertThat(notes.get(3).getCustomPosition()).isEqualTo(2560L);
        verify(noteDao).setCustomPositionSync(3, 2560L);
        // A local arrangement: no sync timestamp moves, so nothing is published.
        verify(syncMetadataDao, never()).touch(anyString(), anyLong(), anyLong());
        verify(syncMetadataDao, never()).insertIfAbsent(any());
    }

    @Test
    public void moveNoteInCustomOrder_toTheTopAndTheBottom() {
        coordinator.moveNoteInCustomOrder(3, null, 1);
        assertThat(notes.get(3).getCustomPosition()).isEqualTo(4 * CustomOrder.STEP);

        coordinator.moveNoteInCustomOrder(1, 2, null);
        assertThat(notes.get(1).getCustomPosition()).isEqualTo(CustomOrder.STEP);
    }

    @Test
    public void moveNoteInCustomOrder_renumbersWhenTheGapIsUsedUp() {
        put(1, 1025L);
        put(2, 1024L);
        put(3, 10L);

        coordinator.moveNoteInCustomOrder(3, 1, 2);

        // Renumbered top first, then placed between its new neighbours.
        assertThat(notes.get(1).getCustomPosition()).isEqualTo(3 * CustomOrder.STEP);
        assertThat(notes.get(2).getCustomPosition()).isEqualTo(2 * CustomOrder.STEP);
        assertThat(notes.get(3).getCustomPosition()).isEqualTo(2560L);
    }

    @Test
    public void insertNote_goesToTheTopOfTheCustomOrder() {
        Note fresh = new Note().create("New", "", 1L, "");
        when(noteDao.addNote(fresh)).thenReturn(9L);

        coordinator.insertNote(fresh);

        assertThat(fresh.getCustomPosition()).isEqualTo(4 * CustomOrder.STEP);
    }

    @Test
    public void insertNotes_keepsAPlaceFromTheBackupAndPlacesTheRestOnTopNewestHighest() {
        Note placed = new Note().create("Placed", "", 1L, "");
        placed.setCustomPosition(1500L);
        Note older = new Note().create("Older", "", 10L, "");
        Note newer = new Note().create("Newer", "", 20L, "");
        when(noteDao.addNotes(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new long[] {10L, 11L, 12L});

        coordinator.insertNotes(new ArrayList<>(List.of(placed, older, newer)));

        assertThat(placed.getCustomPosition()).isEqualTo(1500L);
        assertThat(older.getCustomPosition()).isEqualTo(4 * CustomOrder.STEP);
        assertThat(newer.getCustomPosition()).isEqualTo(5 * CustomOrder.STEP);
    }

    private void put(int id, long position) {
        Note note = notes.get(id);
        if (note == null) {
            note = new Note().create("n" + id, "", id, "");
            note.setId(id);
            notes.put(id, note);
        }
        note.setCustomPosition(position);
    }
}
