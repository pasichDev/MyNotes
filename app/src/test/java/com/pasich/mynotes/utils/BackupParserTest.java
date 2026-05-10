package com.pasich.mynotes.utils;

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.Gson;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.utils.backup.models.JsonBackup;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class BackupParserTest {

    private final Gson gson = new Gson();

    @Test
    public void jsonBackup_serializeAndDeserialize_roundtrip() {
        Note note = new Note().create("Title", "Body", 12345L, "tag1");
        Tag tag = new Tag().create("tag1");
        JsonBackup backup = new JsonBackup();
        backup.setNotes(Collections.singletonList(note));
        backup.setTags(Collections.singletonList(tag));

        String json = gson.toJson(backup);
        JsonBackup restored = gson.fromJson(json, JsonBackup.class);

        assertThat(restored.getNotes()).hasSize(1);
        assertThat(restored.getNotes().get(0).getTitle()).isEqualTo("Title");
        assertThat(restored.getTags()).hasSize(1);
        assertThat(restored.getTags().get(0).getNameTag()).isEqualTo("tag1");
    }

    @Test
    public void jsonBackup_withMultipleNotes_preservesAll() {
        Note n1 = new Note().create("N1", "v1", 1000L, "");
        Note n2 = new Note().create("N2", "v2", 2000L, "");
        JsonBackup backup = new JsonBackup();
        backup.setNotes(Arrays.asList(n1, n2));
        backup.setTags(Collections.emptyList());

        String json = gson.toJson(backup);
        JsonBackup restored = gson.fromJson(json, JsonBackup.class);

        assertThat(restored.getNotes()).hasSize(2);
    }

    @Test
    public void jsonBackup_emptyLists_doesNotCrash() {
        JsonBackup backup = new JsonBackup();
        backup.setNotes(Collections.emptyList());
        backup.setTags(Collections.emptyList());

        String json = gson.toJson(backup);
        JsonBackup restored = gson.fromJson(json, JsonBackup.class);

        assertThat(restored.getNotes()).isEmpty();
        assertThat(restored.getTags()).isEmpty();
    }

    @Test
    public void jsonBackup_malformedJson_throwsOrReturnsNull() {
        String broken = "{ not valid json at all %%%";
        boolean exceptionThrown = false;
        JsonBackup result = null;
        try {
            result = gson.fromJson(broken, JsonBackup.class);
        } catch (Exception e) {
            exceptionThrown = true;
        }
        assertThat(exceptionThrown || result == null || result.getNotes() == null).isTrue();
    }

    @Test
    public void noteSerializedNames_shortFieldNames_mapCorrectly() {
        Note note = new Note().create("MyTitle", "MyValue", 99999L, "MyTag");
        String json = gson.toJson(note);
        Note restored = gson.fromJson(json, Note.class);
        assertThat(restored.getTitle()).isEqualTo("MyTitle");
        assertThat(restored.getValue()).isEqualTo("MyValue");
        assertThat(restored.getTag()).isEqualTo("MyTag");
    }
}
