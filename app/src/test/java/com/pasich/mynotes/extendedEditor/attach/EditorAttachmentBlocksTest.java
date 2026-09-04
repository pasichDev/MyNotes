package com.pasich.mynotes.extendedEditor.attach;

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.JsonObject;
import org.junit.Test;

/**
 * The one walk over an Editor.js document that finds attachment references.
 *
 * <p>Three walkers with three rule sets used to exist; the same note could relocate correctly after
 * a ZIP restore and show broken links after a Drive restore depending on which one ran.
 */
public class EditorAttachmentBlocksTest {

    private static final String DOCUMENT =
            "[{\"id\":\"p\",\"type\":\"paragraph\",\"data\":{\"text\":\"hello <b>there</b>\"}},"
                    + "{\"id\":\"a\",\"type\":\"attaches\",\"data\":{\"file\":{\"url\":\"editorjs://attachments/note_5/one.pdf\",\"name\":\"one.pdf\"}}},"
                    + "{\"id\":\"g\",\"type\":\"gallery\",\"data\":{\"files\":[{\"url\":\"editorjs://attachments/note_5/two.png\"},{\"url\":\"editorjs://attachments/note_5/three.png\"}]}},"
                    + "{\"id\":\"v\",\"type\":\"video\",\"data\":{\"file\":{\"url\":\"editorjs://attachments/note_5/four.mp4\"}}}]";

    @Test
    public void rewritesEveryFileUrlWhateverTheBlockType() {
        String rewritten =
                EditorAttachmentBlocks.rewriteUrls(
                        DOCUMENT, url -> url.replace("note_5", "note_9"));

        // The old sync walker filtered on the attaches and image types and never looked at
        // files[]; the gallery and video blocks stayed pointing at the sending device's files.
        assertThat(rewritten).doesNotContain("note_5");
        assertThat(EditorAttachmentBlocks.fileUrls(rewritten))
                .containsExactly(
                        "editorjs://attachments/note_9/one.pdf",
                        "editorjs://attachments/note_9/two.png",
                        "editorjs://attachments/note_9/three.png",
                        "editorjs://attachments/note_9/four.mp4")
                .inOrder();
        // Text is carried verbatim: the paragraph's markup is not HTML-escaped on the way through.
        assertThat(rewritten).contains("hello <b>there</b>");
    }

    @Test
    public void returnsTheDocumentItselfWhenNothingChanges() {
        // Identity, not equality: a note nothing touched must stay byte-identical on every
        // device, and a re-serialization would already have been a different string.
        assertThat(EditorAttachmentBlocks.rewriteUrls(DOCUMENT, url -> null))
                .isSameInstanceAs(DOCUMENT);
        assertThat(EditorAttachmentBlocks.rewriteUrls(DOCUMENT, url -> url))
                .isSameInstanceAs(DOCUMENT);
    }

    @Test
    public void leavesAnUnreadableDocumentAlone() {
        String broken = "[{\"type\":\"attaches\",\"data\":";

        assertThat(EditorAttachmentBlocks.rewriteUrls(broken, url -> "x")).isSameInstanceAs(broken);
        assertThat(EditorAttachmentBlocks.fileUrls(broken)).isEmpty();
        assertThat(EditorAttachmentBlocks.rewriteUrls(null, url -> "x")).isNull();
    }

    @Test
    public void rewritingIsIdempotent() {
        String once = EditorAttachmentBlocks.rewriteUrls(DOCUMENT, url -> url + "?v=2");
        String again = EditorAttachmentBlocks.rewriteUrls(once, url -> url);

        // Sync hashes the rewritten document on both devices, so re-parsing and re-serializing
        // the output has to reproduce it exactly.
        assertThat(again).isSameInstanceAs(once);
        assertThat(EditorAttachmentBlocks.rewriteUrls(once, url -> url.replace("?v=2", "?v=2")))
                .isSameInstanceAs(once);
    }

    @Test
    public void findsTheFileOfABlockInEitherShape() {
        JsonObject single = EditorAttachmentBlocks.findFile(DOCUMENT, "a");
        JsonObject fromList = EditorAttachmentBlocks.findFile(DOCUMENT, "g");

        assertThat(single.get("name").getAsString()).isEqualTo("one.pdf");
        assertThat(fromList.get("url").getAsString())
                .isEqualTo("editorjs://attachments/note_5/two.png");
        assertThat(EditorAttachmentBlocks.findFile(DOCUMENT, "p")).isNull();
        assertThat(EditorAttachmentBlocks.findFile(DOCUMENT, "missing")).isNull();
    }
}
