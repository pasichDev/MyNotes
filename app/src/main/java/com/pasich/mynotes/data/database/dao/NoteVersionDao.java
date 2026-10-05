package com.pasich.mynotes.data.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import com.pasich.mynotes.data.database.entities.NoteVersionEntity;
import io.reactivex.Flowable;
import java.util.List;

/** DAO for the local, never-synced version history of notes. */
@Dao
public interface NoteVersionDao {

    @Insert
    long insert(NoteVersionEntity version);

    /** The most recent version of a note, or null when it has none. */
    @Query(
            "SELECT * FROM note_versions WHERE noteLocalId = :noteId "
                    + "ORDER BY createdAt DESC, id DESC LIMIT 1")
    NoteVersionEntity getLatest(int noteId);

    @Query("SELECT * FROM note_versions WHERE id = :id LIMIT 1")
    NoteVersionEntity getById(long id);

    /** Every version of a note, newest first. */
    @Query(
            "SELECT * FROM note_versions WHERE noteLocalId = :noteId "
                    + "ORDER BY createdAt DESC, id DESC")
    Flowable<List<NoteVersionEntity>> observeForNote(int noteId);

    @Query(
            "SELECT * FROM note_versions WHERE noteLocalId = :noteId "
                    + "ORDER BY createdAt DESC, id DESC")
    List<NoteVersionEntity> getForNoteSync(int noteId);

    /** Keeps only the newest {@code keep} versions of a note. */
    @Query(
            "DELETE FROM note_versions WHERE noteLocalId = :noteId AND id NOT IN ("
                    + "SELECT id FROM note_versions WHERE noteLocalId = :noteId "
                    + "ORDER BY createdAt DESC, id DESC LIMIT :keep)")
    void prune(int noteId, int keep);

    /** The caller keeps the list under SQLite's bound-variable limit. */
    @Query("DELETE FROM note_versions WHERE noteLocalId IN (:noteIds)")
    void deleteForNotes(List<Integer> noteIds);

    @Query("DELETE FROM note_versions")
    void deleteAll();

    /** Removes history whose note no longer exists on this device. */
    @Query("DELETE FROM note_versions WHERE noteLocalId NOT IN (SELECT id FROM notes)")
    int deleteOrphans();
}
