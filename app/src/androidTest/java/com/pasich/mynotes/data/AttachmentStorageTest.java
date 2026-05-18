package com.pasich.mynotes.data;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.pasich.mynotes.extendedEditor.attach.AttachmentCleaner;
import com.pasich.mynotes.extendedEditor.attach.AttachmentStorage;
import java.io.File;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AttachmentStorageTest {

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void attachmentsBaseDir_isInFilesDir() {
        File base = new File(context.getFilesDir(), AttachmentStorage.ATTACHMENTS_BASE_DIR);
        assertThat(base.getAbsolutePath()).startsWith(context.getFilesDir().getAbsolutePath());
    }

    @Test
    public void createAndDeleteFolder_worksCorrectly() throws Exception {
        File base = new File(context.getFilesDir(), AttachmentStorage.ATTACHMENTS_BASE_DIR);
        File noteFolder = new File(base, "note_55555");
        noteFolder.mkdirs();
        new File(noteFolder, "sample.jpg").createNewFile();
        assertThat(noteFolder.exists()).isTrue();

        AttachmentCleaner.deleteAttachmentFolderByNoteId(context, 55555L);

        assertThat(noteFolder.exists()).isFalse();
    }

    @Test
    public void cleanup_withNoAttachments_deletesOrphanFiles() throws Exception {
        File base = new File(context.getFilesDir(), AttachmentStorage.ATTACHMENTS_BASE_DIR);
        File noteFolder = new File(base, "note_66666");
        noteFolder.mkdirs();
        File orphan = new File(noteFolder, "orphan.jpg");
        orphan.createNewFile();

        com.pasich.mynotes.data.model.Note emptyNote =
                new com.pasich.mynotes.data.model.Note()
                        .create("", "", System.currentTimeMillis(), "");
        emptyNote.setId(66666);

        AttachmentCleaner.cleanup(context, emptyNote);

        assertThat(orphan.exists()).isFalse();
    }
}
