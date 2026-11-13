package com.pasich.mynotes.utils.backup.models.googleKeep;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import org.junit.Test;

public class GoogleKeepNoteTest {

    private static GoogleKeepNote parse(String json) {
        return new Gson().fromJson(json, GoogleKeepNote.class);
    }

    @Test
    public void textNote_keepsItsText() {
        GoogleKeepNote note = parse("{\"title\":\"T\",\"textContent\":\"hello\"}");

        assertEquals("hello", note.toAppNotes().getValue());
    }

    @Test
    public void checklistNote_importsItsItemsWithTheirState() {
        GoogleKeepNote note =
                parse(
                        "{\"title\":\"Shopping\",\"listContent\":["
                                + "{\"text\":\"Milk\",\"isChecked\":false},"
                                + "{\"text\":\"Bread\",\"isChecked\":true}]}");

        assertEquals("[ ] Milk\n[x] Bread", note.toAppNotes().getValue());
        assertEquals("[ ] Milk\n[x] Bread", note.toAppNotesTrash().getValue());
    }

    @Test
    public void checklistWithText_keepsBoth() {
        GoogleKeepNote note =
                parse(
                        "{\"textContent\":\"intro\",\"listContent\":["
                                + "{\"text\":\"a\",\"isChecked\":false}]}");

        assertEquals("intro\n[ ] a", note.getBodyText());
    }

    @Test
    public void noContentAtAll_isEmptyNotNull() {
        assertEquals("", parse("{\"title\":\"x\"}").getBodyText());
    }
}
