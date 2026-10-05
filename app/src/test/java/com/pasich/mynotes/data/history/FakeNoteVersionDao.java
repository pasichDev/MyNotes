package com.pasich.mynotes.data.history;

import com.pasich.mynotes.data.database.dao.NoteVersionDao;
import com.pasich.mynotes.data.database.entities.NoteVersionEntity;
import io.reactivex.Flowable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** In-memory {@link NoteVersionDao} with the same ordering and pruning rules as the SQL. */
public final class FakeNoteVersionDao implements NoteVersionDao {

    public final List<NoteVersionEntity> rows = new ArrayList<>();

    /** Ids of the notes that exist, for {@link #deleteOrphans()}. */
    public final Set<Integer> liveNoteIds = new HashSet<>();

    private long nextId = 1;

    private static final Comparator<NoteVersionEntity> NEWEST_FIRST =
            Comparator.<NoteVersionEntity>comparingLong(v -> v.createdAt)
                    .thenComparingLong(v -> v.id)
                    .reversed();

    @Override
    public long insert(NoteVersionEntity version) {
        version.id = nextId++;
        rows.add(version);
        return version.id;
    }

    @Override
    public NoteVersionEntity getLatest(int noteId) {
        List<NoteVersionEntity> list = getForNoteSync(noteId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public NoteVersionEntity getById(long id) {
        for (NoteVersionEntity row : rows) if (row.id == id) return row;
        return null;
    }

    @Override
    public Flowable<List<NoteVersionEntity>> observeForNote(int noteId) {
        return Flowable.just(getForNoteSync(noteId));
    }

    @Override
    public List<NoteVersionEntity> getForNoteSync(int noteId) {
        List<NoteVersionEntity> list = new ArrayList<>();
        for (NoteVersionEntity row : rows) if (row.noteLocalId == noteId) list.add(row);
        list.sort(NEWEST_FIRST);
        return list;
    }

    @Override
    public void prune(int noteId, int keep) {
        List<NoteVersionEntity> list = getForNoteSync(noteId);
        if (list.size() > keep) rows.removeAll(list.subList(keep, list.size()));
    }

    @Override
    public void deleteForNotes(List<Integer> noteIds) {
        rows.removeIf(row -> noteIds.contains(row.noteLocalId));
    }

    @Override
    public void deleteAll() {
        rows.clear();
    }

    @Override
    public int deleteOrphans() {
        int before = rows.size();
        rows.removeIf(row -> !liveNoteIds.contains(row.noteLocalId));
        return before - rows.size();
    }
}
