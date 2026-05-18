package com.pasich.mynotes.data;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.pasich.mynotes.extendedEditor.attach.AttachmentCleaner;
import java.io.File;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AttachmentCleanupTest {

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void deleteAttachmentFolderByNoteId_deletesFolder() throws Exception {
        File base = new File(context.getFilesDir(), "attachments");
        File folder = new File(base, "note_9999");
        folder.mkdirs();
        File fakeFile = new File(folder, "test_image.jpg");
        fakeFile.createNewFile();

        assertThat(folder.exists()).isTrue();
        assertThat(fakeFile.exists()).isTrue();

        AttachmentCleaner.deleteAttachmentFolderByNoteId(context, 9999L);

        assertThat(folder.exists()).isFalse();
        assertThat(fakeFile.exists()).isFalse();
    }

    @Test
    public void deleteAttachmentFolderByNoteId_nonExistentFolder_doesNotCrash() {
        AttachmentCleaner.deleteAttachmentFolderByNoteId(context, 88888L);
    }

    @Test
    public void deleteAttachmentFolderByNoteId_multipleFiles_allDeleted() throws Exception {
        File base = new File(context.getFilesDir(), "attachments");
        File folder = new File(base, "note_7777");
        folder.mkdirs();
        new File(folder, "file1.jpg").createNewFile();
        new File(folder, "file2.png").createNewFile();
        new File(folder, "file3.pdf").createNewFile();

        AttachmentCleaner.deleteAttachmentFolderByNoteId(context, 7777L);

        assertThat(folder.exists()).isFalse();
    }
}
