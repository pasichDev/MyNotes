package com.pasich.mynotes.data.history;

import static com.google.common.truth.Truth.assertThat;

import com.pasich.mynotes.data.database.entities.NoteVersionEntity;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.extendedEditor.attach.EditorAttachmentBlocks;
import org.junit.Test;

public class NoteVersionRestoreTest {

    private static final String KEPT = "editorjs://attachments/note_5/kept.png";
    private static final String GONE = "editorjs://attachments/note_5/gone.png";

    @Test
    public void bringsBackTheTextAndOnlyTheAttachmentsTheNoteStillHas() {
        Note current = note("Trip", "now", blocks(KEPT), "[{\"url\":\"" + KEPT + "\"}]");
        NoteVersionEntity version =
                version(
                        "Trip plan",
                        "then",
                        "[" + paragraph("then") + "," + image(KEPT) + "," + image(GONE) + "]",
                        "[{\"url\":\"" + KEPT + "\"},{\"url\":\"" + GONE + "\"}]");

        NoteVersionRestore plan = NoteVersionRestore.plan(current, version);

        assertThat(plan.title).isEqualTo("Trip plan");
        assertThat(plan.value).isEqualTo("then");
        assertThat(plan.droppedAttachments).isEqualTo(1);
        // The gone file may already be deleted from the device; a block for it would be broken.
        assertThat(EditorAttachmentBlocks.fileUrls(plan.valueJson)).containsExactly(KEPT);
        assertThat(plan.attachments).contains(KEPT);
        assertThat(plan.attachments).doesNotContain(GONE);
    }

    @Test
    public void aPlainTextVersionLeavesNoAttachmentsListed() {
        Note current = note("Trip", "now", blocks(KEPT), "[{\"url\":\"" + KEPT + "\"}]");
        NoteVersionEntity version = version("Trip", "plain", "", null);

        NoteVersionRestore plan = NoteVersionRestore.plan(current, version);

        assertThat(plan.valueJson).isEmpty();
        assertThat(plan.attachments).isEqualTo("[]");
        assertThat(plan.droppedAttachments).isEqualTo(0);
    }

    @Test
    public void applyTo_keepsTagReminderAndPin() {
        Note current = note("A", "now", "", "[]");
        current.setTag("work");
        current.setPinned(true);
        current.setReminderTime(5_000L);
        NoteVersionRestore.plan(current, version("A", "then", "", "[]")).applyTo(current);

        assertThat(current.getValue()).isEqualTo("then");
        assertThat(current.getTag()).isEqualTo("work");
        assertThat(current.isPinned()).isTrue();
        assertThat(current.getReminderTime()).isEqualTo(5_000L);
    }

    @Test
    public void isLargeEdit_countsWhatIsTakenOutNotWhereTypingHappens() {
        String text = "x".repeat(NoteHistory.LARGE_EDIT_CHARS * 2);
        assertThat(NoteHistory.isLargeEdit(note("", text, "", ""), note("", text + "y", "", "")))
                .isFalse();
        assertThat(NoteHistory.isLargeEdit(note("", text, "", ""), note("", "y", "", ""))).isTrue();
        assertThat(NoteHistory.isLargeEdit(note("T", "short", "", ""), note("", "", "", "")))
                .isTrue();
    }

    private static String blocks(String url) {
        return "[" + paragraph("now") + "," + image(url) + "]";
    }

    private static String paragraph(String text) {
        return "{\"id\":\"p\",\"type\":\"paragraph\",\"data\":{\"text\":\"" + text + "\"}}";
    }

    private static String image(String url) {
        return "{\"id\":\""
                + url.hashCode()
                + "\",\"type\":\"image\",\"data\":{\"file\":{\"url\":\""
                + url
                + "\"}}}";
    }

    private static Note note(String title, String value, String valueJson, String attachments) {
        Note note = new Note().create(title, value, 10L, "");
        note.setId(5);
        note.setValueJson(valueJson);
        note.setAttachments(attachments);
        return note;
    }

    private static NoteVersionEntity version(
            String title, String value, String valueJson, String attachments) {
        return new NoteVersionEntity(5, null, title, value, valueJson, attachments, 1L, "AUTOSAVE");
    }
}
