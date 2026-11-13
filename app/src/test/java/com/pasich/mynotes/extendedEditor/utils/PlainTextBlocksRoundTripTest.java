package com.pasich.mynotes.extendedEditor.utils;

import static com.google.common.truth.Truth.assertThat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The extended editor opens a plain-text note with a paragraph block per line, blank lines going in
 * front of the next line as {@code <br>} (plainTextBlocks in notes_editor/src/custom/runtime.js).
 * Turned back into plain text, those blocks must give exactly the note that went in, so switching
 * editors never changes a note.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PlainTextBlocksRoundTripTest {

    private static JSONObject paragraph(String html) throws Exception {
        return new JSONObject()
                .put("type", "paragraph")
                .put("data", new JSONObject().put("text", html));
    }

    @Test
    public void aBlockPerLineGivesBackTheSameText() throws Exception {
        String text = "first line\n\nsecond paragraph\nthird line\n  \n\nlast";
        JSONArray blocks =
                new JSONArray()
                        .put(paragraph("first line"))
                        .put(paragraph("<br>second paragraph"))
                        .put(paragraph("third line"))
                        .put(paragraph("  <br><br>last"));

        String plain = EditorJsonUtils.extendedNoteToOldNote(blocks.toString()).plainText;

        assertThat(plain).isEqualTo(text);
    }

    /**
     * The page escapes markup in a plain-text note (plainTextBlocks), so it is shown as text; the
     * way back decodes it again, for paragraphs and headers alike.
     */
    @Test
    public void escapedMarkupComesBackAsTyped() throws Exception {
        String text = "List<String> a && b\n\nx > 1 &amp; y\nTitle <h1>";
        JSONArray blocks =
                new JSONArray()
                        .put(paragraph("List&lt;String&gt; a &amp;&amp; b"))
                        .put(paragraph("<br>x &gt; 1 &amp;amp; y"))
                        .put(
                                new JSONObject()
                                        .put("type", "header")
                                        .put(
                                                "data",
                                                new JSONObject()
                                                        .put("text", "<b>Title</b> &lt;h1&gt;")
                                                        .put("level", 2)));

        String plain = EditorJsonUtils.extendedNoteToOldNote(blocks.toString()).plainText;

        assertThat(plain).isEqualTo(text);
    }

    @Test
    public void nonBreakingSpacesAndNumericReferencesAreDecoded() {
        assertThat(EditorJsonUtils.cleanText("a&nbsp;b&#160;c&#x41;&#39;")).isEqualTo("a b cA'");
        assertThat(EditorJsonUtils.cleanText("&unknown; & &#xZZ;")).isEqualTo("&unknown; & &#xZZ;");
    }
}
