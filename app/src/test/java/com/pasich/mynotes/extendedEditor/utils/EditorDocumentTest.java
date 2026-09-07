package com.pasich.mynotes.extendedEditor.utils;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

/**
 * What counts as an empty rich note.
 *
 * <p>Emptiness used to be "the serialized document is not the string {@code []}", so a note the
 * user only opened — the editor writes one empty paragraph into it — was saved and counted in the
 * statistics. The shapes below are the ones the editor actually produces for a document nobody
 * typed into, plus the ones that must survive.
 */
public class EditorDocumentTest {

    @Test
    public void noDocumentAtAllIsEmpty() {
        assertThat(EditorDocument.hasContent(null)).isFalse();
        assertThat(EditorDocument.hasContent("")).isFalse();
        assertThat(EditorDocument.hasContent("   ")).isFalse();
        assertThat(EditorDocument.hasContent("[]")).isFalse();
    }

    @Test
    public void singleEmptyParagraphIsEmpty() {
        // The document an untouched extended editor serializes.
        assertThat(
                        EditorDocument.hasContent(
                                "[{\"id\":\"a1\",\"type\":\"paragraph\",\"data\":{\"text\":\"\"}}]"))
                .isFalse();
    }

    @Test
    public void paragraphOfWhitespaceAndEmptyMarkupIsEmpty() {
        assertThat(
                        EditorDocument.hasContent(
                                "[{\"type\":\"paragraph\",\"data\":{\"text\":\"   \"}},"
                                        + "{\"type\":\"paragraph\",\"data\":{\"text\":\"<br>\"}},"
                                        + "{\"type\":\"paragraph\",\"data\":{\"text\":\"&nbsp;\"}},"
                                        + "{\"type\":\"paragraph\",\"data\":{\"text\":\"<b></b>\"}}]"))
                .isFalse();
    }

    @Test
    public void emptyHeaderAndEmptyListAreEmpty() {
        assertThat(
                        EditorDocument.hasContent(
                                "[{\"type\":\"header\",\"data\":{\"text\":\"\",\"level\":2}},"
                                        + "{\"type\":\"list\",\"data\":{\"style\":\"unordered\",\"items\":[]}},"
                                        + "{\"type\":\"list\",\"data\":{\"style\":\"checklist\",\"items\":[{\"content\":\"\",\"meta\":{\"checked\":false}}]}}]"))
                .isFalse();
    }

    @Test
    public void typedTextIsContent() {
        assertThat(
                        EditorDocument.hasContent(
                                "[{\"type\":\"paragraph\",\"data\":{\"text\":\"hello\"}}]"))
                .isTrue();
    }

    @Test
    public void textHiddenBehindMarkupIsContent() {
        assertThat(
                        EditorDocument.hasContent(
                                "[{\"type\":\"paragraph\",\"data\":{\"text\":\"<b>bold</b>\"}}]"))
                .isTrue();
    }

    @Test
    public void filledListItemIsContent() {
        assertThat(
                        EditorDocument.hasContent(
                                "[{\"type\":\"list\",\"data\":{\"style\":\"unordered\",\"items\":[{\"content\":\"\"},{\"content\":\"milk\"}]}}]"))
                .isTrue();
    }

    @Test
    public void nestedListItemIsContent() {
        assertThat(
                        EditorDocument.hasContent(
                                "[{\"type\":\"list\",\"data\":{\"style\":\"unordered\",\"items\":[{\"content\":\"\",\"items\":[{\"content\":\"deep\"}]}]}}]"))
                .isTrue();
    }

    @Test
    public void attachmentAloneIsContent() {
        // A note that is only a file has no text at all and must still be kept.
        assertThat(
                        EditorDocument.hasContent(
                                "[{\"type\":\"paragraph\",\"data\":{\"text\":\"\"}},"
                                        + "{\"type\":\"attaches\",\"data\":{\"file\":{\"url\":\"editorjs://attachments/note_5/one.pdf\",\"name\":\"one.pdf\"}}}]"))
                .isTrue();
    }

    @Test
    public void imageAndGalleryBlocksAreContent() {
        assertThat(
                        EditorDocument.hasContent(
                                "[{\"type\":\"image\",\"data\":{\"file\":{\"url\":\"editorjs://attachments/note_5/a.png\"}}}]"))
                .isTrue();
        assertThat(
                        EditorDocument.hasContent(
                                "[{\"type\":\"gallery\",\"data\":{\"files\":[{\"url\":\"editorjs://attachments/note_5/b.png\"}]}}]"))
                .isTrue();
    }

    @Test
    public void aToolWithNoTextOfItsOwnIsContent() {
        // Nothing but an empty paragraph is written without the user asking for it, so a
        // delimiter, a table, or a tool this build cannot read is kept.
        assertThat(EditorDocument.hasContent("[{\"type\":\"delimiter\",\"data\":{}}]")).isTrue();
        assertThat(
                        EditorDocument.hasContent(
                                "[{\"type\":\"table\",\"data\":{\"content\":[[\"a\",\"b\"]]}}]"))
                .isTrue();
        assertThat(EditorDocument.hasContent("[{\"type\":\"someFutureTool\",\"data\":{}}]"))
                .isTrue();
    }

    @Test
    public void unreadableDocumentIsKept() {
        // Discarding a note we simply failed to parse would lose the user's data.
        assertThat(EditorDocument.hasContent("{not json")).isTrue();
        assertThat(EditorDocument.hasContent("{\"blocks\":[]}")).isTrue();
    }
}
