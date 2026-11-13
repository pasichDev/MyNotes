package com.pasich.mynotes.utils.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Line wrapping for the PDF export, measured as one unit per character. */
public class PdfLineWrapTest {

    private static final FileExportUtils.TextWidth CHARS = String::length;

    @Test
    public void shortLine_isDrawnAsIs() {
        assertEquals(List.of("hello"), FileExportUtils.wrapLine("hello", 10, CHARS));
    }

    @Test
    public void longLine_breaksBetweenWords() {
        assertEquals(
                Arrays.asList("aaa bbb", "ccc ddd"),
                FileExportUtils.wrapLine("aaa bbb ccc ddd", 7, CHARS));
    }

    @Test
    public void wordWiderThanTheLine_isBrokenNotDropped() {
        List<String> lines = FileExportUtils.wrapLine("abcdefghijkl", 5, CHARS);

        assertEquals(Arrays.asList("abcde", "fghij", "kl"), lines);
    }

    @Test
    public void overlongWordAfterOthers_startsItsOwnLine() {
        assertEquals(
                Arrays.asList("ab", "cdefgh", "ij xy"),
                FileExportUtils.wrapLine("ab cdefghij xy", 6, CHARS));
    }

    @Test
    public void noDrawnLineIsWiderThanAllowed() {
        String text = "lorem ipsum dolorsitametconsectetur adipiscing elit sed do eiusmod";
        for (String line : FileExportUtils.wrapLine(text, 9, CHARS)) {
            assertTrue(line, line.length() <= 9);
        }
    }

    @Test
    public void surrogatePairs_areNotSplit() {
        String emoji = "😀"; // one character, two chars
        List<String> lines = FileExportUtils.wrapLine(emoji + emoji + emoji, 3, CHARS);

        for (String line : lines) {
            assertTrue(!Character.isHighSurrogate(line.charAt(line.length() - 1)));
            assertTrue(!Character.isLowSurrogate(line.charAt(0)));
        }
        assertEquals(emoji + emoji + emoji, String.join("", lines));
    }
}
