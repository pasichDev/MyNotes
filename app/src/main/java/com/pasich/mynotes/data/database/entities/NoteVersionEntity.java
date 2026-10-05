package com.pasich.mynotes.data.database.entities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * One saved earlier state of a note, kept on this device only.
 *
 * <p>Deliberately without a foreign key to {@code notes}: notes are written with REPLACE inserts (a
 * sync apply, a restore), and SQLite carries out a REPLACE as a delete followed by an insert, so an
 * {@code ON DELETE CASCADE} would wipe a note's history every time the note was applied. History is
 * removed explicitly when the note itself is deleted for good, plus an orphan sweep.
 */
@Entity(
        tableName = "note_versions",
        indices = {@Index(value = {"noteLocalId", "createdAt"}), @Index(value = {"noteStableId"})})
public class NoteVersionEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** The note's row id on this device. */
    public int noteLocalId;

    /** The note's sync identity when it had one, so the version can be traced across id changes. */
    @Nullable public String noteStableId;

    @NonNull public String title;
    @NonNull public String value;
    @Nullable public String valueJson;
    @Nullable public String attachments;

    /** When this state was replaced, which is when it was captured. */
    public long createdAt;

    /** Why it was captured; one of {@link com.pasich.mynotes.data.history.NoteVersionReason}. */
    @NonNull public String reason;

    public NoteVersionEntity(
            int noteLocalId,
            @Nullable String noteStableId,
            @NonNull String title,
            @NonNull String value,
            @Nullable String valueJson,
            @Nullable String attachments,
            long createdAt,
            @NonNull String reason) {
        this.noteLocalId = noteLocalId;
        this.noteStableId = noteStableId;
        this.title = title;
        this.value = value;
        this.valueJson = valueJson;
        this.attachments = attachments;
        this.createdAt = createdAt;
        this.reason = reason;
    }
}
