package com.pasich.mynotes.utils.encly;

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.data.model.TaskCategory;
import com.pasich.mynotes.utils.managers.SystemTagsManager;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Pins the wire shape of {@code handoff.json} that Encly parses (contract v1). */
public class HandoffPayloadBuilderTest {

    private static final long EXPORTED_AT = 1_700_000_000_000L;

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final Map<String, String> stable = new HashMap<>();
    private final HandoffPayloadBuilder.StableIds stableIds =
            (type, localId) -> stable.get(type + ":" + localId);

    private JsonObject build(
            List<Note> notes, List<Tag> tags, List<Task> tasks, List<TaskCategory> categories) {
        String json =
                new HandoffPayloadBuilder(notes, tags, tasks, categories, stableIds, EXPORTED_AT)
                        .toJson();
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static Note note(int id, String title, String value) {
        Note note = new Note().create(title, value, 1000L + id, "");
        note.setId(id);
        return note;
    }

    @Test
    public void header() {
        JsonObject root = build(List.of(), List.of(), List.of(), List.of());

        assertThat(root.get("format").getAsString()).isEqualTo("mynotes-handoff");
        assertThat(root.get("schema").getAsInt()).isEqualTo(1);
        assertThat(root.get("exportedAt").getAsLong()).isEqualTo(EXPORTED_AT);
        assertThat(root.getAsJsonArray("tags")).isEmpty();
        assertThat(root.getAsJsonArray("notes")).isEmpty();
        assertThat(root.getAsJsonArray("taskCategories")).isEmpty();
        assertThat(root.getAsJsonArray("tasks")).isEmpty();
    }

    @Test
    public void plainNote_hasNullValueJsonAndTagAndZeroAttachments() {
        Note plain = note(3, "Shopping", "milk\nbread");

        JsonObject n =
                build(List.of(plain), List.of(), List.of(), List.of())
                        .getAsJsonArray("notes")
                        .get(0)
                        .getAsJsonObject();

        assertThat(n.get("id").getAsString()).isEqualTo("mynotes:note:3");
        assertThat(n.get("title").getAsString()).isEqualTo("Shopping");
        assertThat(n.get("value").getAsString()).isEqualTo("milk\nbread");
        assertThat(n.has("valueJson")).isTrue();
        assertThat(n.get("valueJson").isJsonNull()).isTrue();
        assertThat(n.get("date").getAsLong()).isEqualTo(1003L);
        assertThat(n.get("tag").isJsonNull()).isTrue();
        assertThat(n.get("isTrash").getAsBoolean()).isFalse();
        assertThat(n.get("isPinned").getAsBoolean()).isFalse();
        assertThat(n.get("attachments").getAsInt()).isEqualTo(0);
    }

    @Test
    public void editorJsNote_sendsTheDocumentVerbatimAsAString() {
        String doc =
                "[{\"id\":\"a\",\"type\":\"paragraph\",\"data\":{\"text\":\"<b>Hi</b> &amp; \\\"you\\\"\"}}]";
        Note rich = note(4, "Rich", "Hi & \"you\"");
        rich.setValueJson(doc);

        JsonObject n =
                build(List.of(rich), List.of(), List.of(), List.of())
                        .getAsJsonArray("notes")
                        .get(0)
                        .getAsJsonObject();

        assertThat(n.get("valueJson").isJsonPrimitive()).isTrue();
        assertThat(n.get("valueJson").getAsString()).isEqualTo(doc);
    }

    @Test
    public void trashedAndPinnedFlagsTravel() {
        Note trashed = note(1, "old", "x");
        trashed.setTrash(true);
        Note pinned = note(2, "top", "y");
        pinned.setPinned(true);

        JsonArray notes =
                build(List.of(trashed, pinned), List.of(), List.of(), List.of())
                        .getAsJsonArray("notes");

        assertThat(notes.get(0).getAsJsonObject().get("isTrash").getAsBoolean()).isTrue();
        assertThat(notes.get(0).getAsJsonObject().get("isPinned").getAsBoolean()).isFalse();
        assertThat(notes.get(1).getAsJsonObject().get("isTrash").getAsBoolean()).isFalse();
        assertThat(notes.get(1).getAsJsonObject().get("isPinned").getAsBoolean()).isTrue();
    }

    @Test
    public void attachmentsAreCountedNeverSent() {
        Note withFiles = note(5, "trip", "");
        withFiles.setAttachments(
                "[{\"url\":\"mynotes-attach://note_5/a.jpg\"},{\"url\":\"mynotes-attach://note_5/b.pdf\"}]");
        withFiles.setValueJson(
                "[{\"type\":\"image\",\"data\":{\"file\":{\"url\":\"mynotes-attach://note_5/a.jpg\"}}},"
                        + "{\"type\":\"attaches\",\"data\":{\"file\":{\"url\":\"mynotes-attach://note_5/b.pdf\"}}},"
                        + "{\"type\":\"paragraph\",\"data\":{\"text\":\"t\"}}]");
        // A stale list must not hide references the document still has.
        Note staleList = note(6, "stale", "");
        staleList.setAttachments("[]");
        staleList.setValueJson(
                "[{\"type\":\"image\",\"data\":{\"file\":{\"url\":\"mynotes-attach://note_6/c.png\"}}}]");

        JsonArray notes =
                build(List.of(withFiles, staleList), List.of(), List.of(), List.of())
                        .getAsJsonArray("notes");

        assertThat(notes.get(0).getAsJsonObject().get("attachments").getAsInt()).isEqualTo(2);
        assertThat(notes.get(1).getAsJsonObject().get("attachments").getAsInt()).isEqualTo(1);
        // The note object carries exactly the contract fields; no file data, no reminder.
        assertThat(notes.get(0).getAsJsonObject().keySet())
                .containsExactly(
                        "id",
                        "title",
                        "value",
                        "valueJson",
                        "date",
                        "tag",
                        "isTrash",
                        "isPinned",
                        "attachments");
    }

    @Test
    public void unreadableAttachmentList_countsAsNone() {
        Note broken = note(7, "b", "");
        broken.setAttachments("{not json");

        assertThat(HandoffPayloadBuilder.attachmentCount(broken)).isEqualTo(0);
    }

    @Test
    public void noteTagIsSentByName_andOnlyUserTagsAreListed() {
        Note tagged = note(8, "t", "v");
        tagged.setTag("Work");
        Tag work = new Tag().create("Work");
        work.id = 11;
        work.setPosition(2);
        Tag system = new Tag().create("allNotes", SystemTagsManager.SYSTEM_ACTION_ALL_NOTES);
        system.id = 12;

        JsonObject root = build(List.of(tagged), List.of(work, system), List.of(), List.of());

        assertThat(root.getAsJsonArray("notes").get(0).getAsJsonObject().get("tag").getAsString())
                .isEqualTo("Work");
        JsonArray tags = root.getAsJsonArray("tags");
        assertThat(tags).hasSize(1);
        JsonObject t = tags.get(0).getAsJsonObject();
        assertThat(t.get("id").getAsString()).isEqualTo("mynotes:tag:11");
        assertThat(t.get("name").getAsString()).isEqualTo("Work");
        assertThat(t.get("position").getAsInt()).isEqualTo(2);
    }

    @Test
    public void stableIdComesFromSyncMetadata_elseFallback() {
        stable.put("note:1", "3f6c1d1e-0000-4000-8000-000000000001");
        stable.put("tag:2", "3f6c1d1e-0000-4000-8000-000000000002");
        stable.put("task:3", "3f6c1d1e-0000-4000-8000-000000000003");
        stable.put("category:4", "3f6c1d1e-0000-4000-8000-000000000004");
        stable.put("note:9", "   ");
        Tag tag = new Tag().create("T");
        tag.id = 2;
        Task task = new Task("Buy", 4);
        task.setId(3);
        TaskCategory category = new TaskCategory("Home", "#000000");
        category.setId(4);

        JsonObject root =
                build(
                        List.of(note(1, "a", ""), note(9, "b", "")),
                        List.of(tag),
                        List.of(task),
                        List.of(category));

        JsonArray notes = root.getAsJsonArray("notes");
        assertThat(notes.get(0).getAsJsonObject().get("id").getAsString())
                .isEqualTo("3f6c1d1e-0000-4000-8000-000000000001");
        // A blank stable id is no identity at all.
        assertThat(notes.get(1).getAsJsonObject().get("id").getAsString())
                .isEqualTo("mynotes:note:9");
        assertThat(root.getAsJsonArray("tags").get(0).getAsJsonObject().get("id").getAsString())
                .isEqualTo("3f6c1d1e-0000-4000-8000-000000000002");
        JsonObject t = root.getAsJsonArray("tasks").get(0).getAsJsonObject();
        assertThat(t.get("id").getAsString()).isEqualTo("3f6c1d1e-0000-4000-8000-000000000003");
        assertThat(t.get("categoryId").getAsString())
                .isEqualTo("3f6c1d1e-0000-4000-8000-000000000004");
        assertThat(
                        root.getAsJsonArray("taskCategories")
                                .get(0)
                                .getAsJsonObject()
                                .get("id")
                                .getAsString())
                .isEqualTo("3f6c1d1e-0000-4000-8000-000000000004");
    }

    @Test
    public void categoriesAndTasks() {
        TaskCategory home = new TaskCategory("Home", "#112233");
        home.setId(4);
        home.setPosition(1);
        Task inHome = new Task("Fix sink", 4);
        inHome.setId(20);
        inHome.setDone(true);
        inHome.setCreatedAt(5555L);
        inHome.setPosition(3);
        inHome.setDescription("call plumber");
        Task noCategory = new Task("Read", 0);
        noCategory.setId(21);
        Task danglingCategory = new Task("Ghost", 99);
        danglingCategory.setId(22);

        JsonObject root =
                build(
                        List.of(),
                        List.of(),
                        List.of(inHome, noCategory, danglingCategory),
                        List.of(home));

        JsonObject c = root.getAsJsonArray("taskCategories").get(0).getAsJsonObject();
        assertThat(c.get("id").getAsString()).isEqualTo("mynotes:category:4");
        assertThat(c.get("name").getAsString()).isEqualTo("Home");
        assertThat(c.get("position").getAsInt()).isEqualTo(1);
        assertThat(c.keySet()).containsExactly("id", "name", "position");

        JsonArray tasks = root.getAsJsonArray("tasks");
        JsonObject first = tasks.get(0).getAsJsonObject();
        assertThat(first.get("id").getAsString()).isEqualTo("mynotes:task:20");
        assertThat(first.get("description").getAsString()).isEqualTo("Fix sink\ncall plumber");
        assertThat(first.get("isDone").getAsBoolean()).isTrue();
        assertThat(first.get("createdAt").getAsLong()).isEqualTo(5555L);
        assertThat(first.get("categoryId").getAsString()).isEqualTo("mynotes:category:4");
        assertThat(first.get("position").getAsInt()).isEqualTo(3);
        assertThat(first.keySet())
                .containsExactly(
                        "id", "description", "isDone", "createdAt", "categoryId", "position");

        assertThat(tasks.get(1).getAsJsonObject().get("description").getAsString())
                .isEqualTo("Read");
        assertThat(tasks.get(1).getAsJsonObject().get("categoryId").isJsonNull()).isTrue();
        assertThat(tasks.get(2).getAsJsonObject().get("categoryId").isJsonNull()).isTrue();
    }

    @Test
    public void summaryCountsWhatStaysBehind() {
        Note a = note(1, "a", "");
        a.setAttachments("[{\"url\":\"x\"}]");
        a.setReminderTime(1L);
        a.setPinned(true);
        Note b = note(2, "b", "");
        Task task = new Task("t", 0);
        task.setReminderTime(2L);
        Tag user = new Tag().create("U");
        Tag system = new Tag().create("allNotes", SystemTagsManager.SYSTEM_ACTION_ALL_NOTES);

        HandoffPayloadBuilder.Summary summary =
                HandoffPayloadBuilder.Summary.of(
                        List.of(a, b), List.of(user, system), List.of(task));

        assertThat(summary.notes).isEqualTo(2);
        assertThat(summary.tasks).isEqualTo(1);
        assertThat(summary.tags).isEqualTo(1);
        assertThat(summary.attachments).isEqualTo(1);
        assertThat(summary.reminders).isEqualTo(2);
        assertThat(summary.pinned).isEqualTo(1);
    }

    @Test
    public void archiveHoldsExactlyOneUtf8Entry_andClearRemovesIt() throws Exception {
        File cache = temporaryFolder.newFolder("cache");
        Note unicode = note(1, "Нотатка ✓", "текст");
        List<Note> notes = new ArrayList<>();
        notes.add(unicode);

        File zip =
                HandoffArchive.write(
                        cache,
                        new HandoffPayloadBuilder(
                                notes, List.of(), List.of(), List.of(), stableIds, EXPORTED_AT));

        assertThat(zip.getParentFile()).isEqualTo(new File(cache, "encly-handoff"));
        List<String> names = new ArrayList<>();
        String body = null;
        try (ZipInputStream in =
                new ZipInputStream(new ByteArrayInputStream(Files.readAllBytes(zip.toPath())))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                names.add(entry.getName());
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        assertThat(names).containsExactly("handoff.json");
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        assertThat(root.getAsJsonArray("notes").get(0).getAsJsonObject().get("title").getAsString())
                .isEqualTo("Нотатка ✓");

        assertThat(HandoffArchive.clear(cache)).isEqualTo(0);
        assertThat(zip.exists()).isFalse();
    }

    @Test
    public void clearWithoutDirectoryIsANoOp() throws Exception {
        assertThat(HandoffArchive.clear(temporaryFolder.newFolder("empty"))).isEqualTo(0);
    }
}
