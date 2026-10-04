package com.pasich.mynotes.utils.encly;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.data.model.TaskCategory;
import com.pasich.mynotes.data.sync.SyncMetadata;
import com.pasich.mynotes.extendedEditor.attach.EditorAttachmentBlocks;
import com.pasich.mynotes.utils.managers.SystemTagsManager;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Writes {@code handoff.json} — the raw My Notes data Encly imports.
 *
 * <p>Raw on purpose: Encly maps it to its own blocks, so the Encly format stays private to Encly.
 * Attachment files and reminders are never sent; each note only says how many attachments stay
 * behind. Free of {@code android.*} so the exact wire shape is pinned by JVM tests.
 */
public final class HandoffPayloadBuilder {

    /** Looks up the durable sync identity of a local record. */
    public interface StableIds {
        /**
         * @return {@code sync_metadata.stableId} for that record, or {@code null} when it has none.
         */
        @Nullable
        String find(@NonNull String recordType, long localId);
    }

    private final List<Note> notes;
    private final List<Tag> tags;
    private final List<Task> tasks;
    private final List<TaskCategory> categories;
    private final StableIds stableIds;
    private final long exportedAt;

    public HandoffPayloadBuilder(
            @NonNull List<Note> notes,
            @NonNull List<Tag> tags,
            @NonNull List<Task> tasks,
            @NonNull List<TaskCategory> categories,
            @NonNull StableIds stableIds,
            long exportedAt) {
        this.notes = notes;
        this.tags = tags;
        this.tasks = tasks;
        this.categories = categories;
        this.stableIds = stableIds;
        this.exportedAt = exportedAt;
    }

    /** The stable id used on the wire: the sync identity, else {@code mynotes:<type>:<id>}. */
    @NonNull
    public static String stableId(
            @NonNull StableIds stableIds, @NonNull String recordType, long localId) {
        String stable = stableIds.find(recordType, localId);
        if (stable != null && !stable.trim().isEmpty()) return stable;
        return "mynotes:" + recordType + ":" + localId;
    }

    /**
     * Attachments that stay in My Notes: the larger of the note's attachment list and the file
     * references in its Editor.js document, so a note whose list is stale still says what is lost.
     */
    public static int attachmentCount(@NonNull Note note) {
        int listed = 0;
        String json = note.getAttachments();
        if (json != null && !json.trim().isEmpty()) {
            try {
                JsonElement parsed = JsonParser.parseString(json);
                if (parsed.isJsonArray()) listed = parsed.getAsJsonArray().size();
            } catch (RuntimeException unreadable) {
                listed = 0;
            }
        }
        int referenced = EditorAttachmentBlocks.fileUrls(note.getValueJson()).size();
        return Math.max(listed, referenced);
    }

    /** User tags only; system tags are UI rows, not data. */
    public static boolean isUserTag(@NonNull Tag tag) {
        return tag.getSystemAction() == SystemTagsManager.SYSTEM_ACTION_USER_TAG;
    }

    /**
     * The text of a task. The contract carries one {@code description}; a My Notes task has a
     * required title and an optional description, so both travel, title first.
     */
    @NonNull
    public static String taskText(@NonNull Task task) {
        String title = task.getTitle() == null ? "" : task.getTitle();
        String description = task.getDescription();
        if (description == null || description.trim().isEmpty()) return title;
        if (title.isEmpty()) return description;
        return title + "\n" + description;
    }

    @NonNull
    public String toJson() {
        StringWriter out = new StringWriter();
        try {
            write(out);
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        return out.toString();
    }

    /** Streams the document, so a large library is never held twice in memory. */
    public void write(@NonNull Writer out) throws IOException {
        JsonWriter json = new JsonWriter(out);
        json.setHtmlSafe(false);
        json.setSerializeNulls(true);
        json.beginObject();
        json.name("format").value(EnclyHandoff.FORMAT);
        json.name("schema").value(EnclyHandoff.SCHEMA);
        json.name("exportedAt").value(exportedAt);

        json.name("tags").beginArray();
        for (Tag tag : tags) {
            if (!isUserTag(tag)) continue;
            json.beginObject();
            json.name("id").value(stableId(stableIds, SyncMetadata.RECORD_TYPE_TAG, tag.getId()));
            json.name("name").value(tag.getNameTag());
            json.name("position").value(tag.getPosition());
            json.endObject();
        }
        json.endArray();

        json.name("notes").beginArray();
        for (Note note : notes) {
            json.beginObject();
            json.name("id").value(stableId(stableIds, SyncMetadata.RECORD_TYPE_NOTE, note.getId()));
            json.name("title").value(note.getTitle());
            json.name("value").value(note.getValue());
            String valueJson = note.getValueJson();
            if (valueJson.trim().isEmpty()) {
                json.name("valueJson").nullValue();
            } else {
                json.name("valueJson").value(valueJson);
            }
            json.name("date").value(note.getDate());
            String tag = note.getTag();
            if (tag.trim().isEmpty()) {
                json.name("tag").nullValue();
            } else {
                json.name("tag").value(tag);
            }
            json.name("isTrash").value(note.isTrash());
            json.name("isPinned").value(note.isPinned());
            json.name("attachments").value(attachmentCount(note));
            json.endObject();
        }
        json.endArray();

        Set<Integer> categoryIds = new HashSet<>();
        json.name("taskCategories").beginArray();
        for (TaskCategory category : categories) {
            categoryIds.add(category.getId());
            json.beginObject();
            json.name("id")
                    .value(
                            stableId(
                                    stableIds,
                                    SyncMetadata.RECORD_TYPE_CATEGORY,
                                    category.getId()));
            json.name("name").value(category.getName());
            json.name("position").value(category.getPosition());
            json.endObject();
        }
        json.endArray();

        json.name("tasks").beginArray();
        for (Task task : tasks) {
            json.beginObject();
            json.name("id").value(stableId(stableIds, SyncMetadata.RECORD_TYPE_TASK, task.getId()));
            json.name("description").value(taskText(task));
            json.name("isDone").value(task.isDone());
            json.name("createdAt").value(task.getCreatedAt());
            // 0 means "no category"; an id with no row would point Encly at nothing.
            if (task.getCategoryId() != 0 && categoryIds.contains(task.getCategoryId())) {
                json.name("categoryId")
                        .value(
                                stableId(
                                        stableIds,
                                        SyncMetadata.RECORD_TYPE_CATEGORY,
                                        task.getCategoryId()));
            } else {
                json.name("categoryId").nullValue();
            }
            json.name("position").value(task.getPosition());
            json.endObject();
        }
        json.endArray();

        json.endObject();
        json.flush();
    }

    /** Totals shown on the page before anything is sent. */
    public static final class Summary {
        public final int notes;
        public final int tasks;
        public final int tags;
        public final int attachments;
        public final int reminders;
        public final int pinned;

        public Summary(int notes, int tasks, int tags, int attachments, int reminders, int pinned) {
            this.notes = notes;
            this.tasks = tasks;
            this.tags = tags;
            this.attachments = attachments;
            this.reminders = reminders;
            this.pinned = pinned;
        }

        @NonNull
        public static Summary of(
                @NonNull List<Note> notes, @NonNull List<Tag> tags, @NonNull List<Task> tasks) {
            int userTags = 0;
            for (Tag tag : tags) if (isUserTag(tag)) userTags++;
            int attachments = 0;
            int reminders = 0;
            int pinned = 0;
            for (Note note : notes) {
                attachments += attachmentCount(note);
                if (note.getReminderTime() != null) reminders++;
                if (note.isPinned()) pinned++;
            }
            for (Task task : tasks) if (task.getReminderTime() != null) reminders++;
            return new Summary(
                    notes.size(), tasks.size(), userTags, attachments, reminders, pinned);
        }
    }
}
