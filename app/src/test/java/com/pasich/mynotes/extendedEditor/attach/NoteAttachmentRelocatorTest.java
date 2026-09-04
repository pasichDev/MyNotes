package com.pasich.mynotes.extendedEditor.attach;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Restoring a backup onto a device whose note ids already collide.
 *
 * <p>The archive stores attachments under the id the note had when the backup was taken. When that
 * id is taken the note is inserted under a new one, and without relocation two notes end up sharing
 * one folder — saving the older note then deletes the restored note's files as orphans.
 */
public class NoteAttachmentRelocatorTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File root;

    @Before
    public void setUp() throws Exception {
        root = temporaryFolder.newFolder("attachments");
    }

    @Test
    public void copiesReferencedFilesIntoTheNewNoteFolderAndRewritesTheUrls() throws Exception {
        File original = seed(5, "1731000000000_882134.jpg", "photo bytes");

        NoteAttachmentRelocator.Result result =
                NoteAttachmentRelocator.relocate(
                        root, 5, 12, attachmentsJson(5, "1731000000000_882134.jpg"), null);

        assertThat(result.changed).isTrue();
        assertThat(result.attachmentsJson).contains("note_12");
        assertThat(result.attachmentsJson).doesNotContain("note_5");

        File moved = new File(new File(root, "note_12"), "1731000000000_882134.jpg");
        assertThat(moved.isFile()).isTrue();
        assertThat(contentOf(moved)).isEqualTo("photo bytes");
        // Copied, not moved: the pre-restore state still points at the original.
        assertThat(original.isFile()).isTrue();
    }

    @Test
    public void rewritesTheEditorBlocksThatCarryTheSameUrls() throws Exception {
        seed(5, "1731000000000_882134.jpg", "photo bytes");
        String blocks =
                "[{\"type\":\"attaches\",\"data\":{\"file\":{\"url\":\""
                        + AttachmentStorage.urlFor(5, "1731000000000_882134.jpg")
                        + "\",\"name\":\"photo.jpg\"}}}]";

        NoteAttachmentRelocator.Result result =
                NoteAttachmentRelocator.relocate(
                        root, 5, 12, attachmentsJson(5, "1731000000000_882134.jpg"), blocks);

        assertThat(result.changed).isTrue();
        // The column feeds the file list; the blocks are what the editor renders.
        assertThat(result.valueJson).contains("note_12");
        assertThat(result.valueJson).doesNotContain("note_5");
    }

    @Test
    public void leavesReferencesThatBelongToAnotherNoteAlone() throws Exception {
        seed(7, "other.png", "not mine");

        NoteAttachmentRelocator.Result result =
                NoteAttachmentRelocator.relocate(
                        root, 5, 12, attachmentsJson(7, "other.png"), null);

        assertThat(result.changed).isFalse();
        assertThat(result.attachmentsJson).contains("note_7");
        assertThat(new File(new File(root, "note_12"), "other.png").exists()).isFalse();
    }

    @Test
    public void doesNothingWhenTheIdDidNotChange() throws Exception {
        seed(5, "photo.png", "bytes");

        NoteAttachmentRelocator.Result result =
                NoteAttachmentRelocator.relocate(root, 5, 5, attachmentsJson(5, "photo.png"), null);

        assertThat(result.changed).isFalse();
    }

    @Test
    public void skipsAReferenceWhoseFileIsNotThere() {
        NoteAttachmentRelocator.Result result =
                NoteAttachmentRelocator.relocate(root, 5, 12, attachmentsJson(5, "gone.png"), null);

        assertThat(result.changed).isFalse();
        assertThat(result.attachmentsJson).contains("note_5");
    }

    @Test
    public void leavesUnreadableMetadataUntouched() {
        NoteAttachmentRelocator.Result result =
                NoteAttachmentRelocator.relocate(root, 5, 12, "{not json", null);

        assertThat(result.changed).isFalse();
        assertThat(result.attachmentsJson).isEqualTo("{not json");
    }

    @Test
    public void keepsAnExistingTargetFileRatherThanOverwritingIt() throws Exception {
        seed(5, "photo.png", "restored bytes");
        File existing = seed(12, "photo.png", "the note already here");

        NoteAttachmentRelocator.Result result =
                NoteAttachmentRelocator.relocate(
                        root, 5, 12, attachmentsJson(5, "photo.png"), null);

        assertThat(result.changed).isTrue();
        // Overwriting would destroy the file the note that owns note_12 is using.
        assertThat(contentOf(existing)).isEqualTo("the note already here");
    }

    private File seed(int noteId, String name, String content) throws Exception {
        File folder = new File(root, "note_" + noteId);
        assertThat(folder.mkdirs() || folder.isDirectory()).isTrue();
        File file = new File(folder, name);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String contentOf(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static String attachmentsJson(int noteId, String name) {
        return "[{\"url\":\""
                + AttachmentStorage.urlFor(noteId, name)
                + "\",\"name\":\""
                + name
                + "\"}]";
    }
}
