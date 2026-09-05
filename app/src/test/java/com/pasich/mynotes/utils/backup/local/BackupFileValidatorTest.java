package com.pasich.mynotes.utils.backup.local;

import static com.google.common.truth.Truth.assertThat;

import com.pasich.mynotes.utils.constants.Backup;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;

/**
 * Whether a picked document is accepted as a backup.
 *
 * <p>Android's document picker appends " (2)" to a second backup saved into the same folder, so
 * judging by the extension alone made the app refuse its own file — reproduced on a Pixel 7a.
 */
public class BackupFileValidatorTest {

    @Test
    public void acceptsABackupArchiveWhateverThePickerCalledIt() throws Exception {
        byte[] archive = archiveWith(Backup.FILE_NAME_BACKUP);

        assertThat(
                        BackupFileValidator.isAcceptable(
                                "My_Notes_Backup.mnbkn (2)",
                                () -> new ByteArrayInputStream(archive)))
                .isTrue();
        assertThat(
                        BackupFileValidator.isAcceptable(
                                "anything at all", () -> new ByteArrayInputStream(archive)))
                .isTrue();
    }

    @Test
    public void stillAcceptsTheKnownExtensionsWithoutOpeningTheDocument() {
        // The legacy non-archive formats can only be judged by name.
        assertThat(
                        BackupFileValidator.isAcceptable(
                                "old.json",
                                () -> {
                                    throw new AssertionError("must not open");
                                }))
                .isTrue();
        assertThat(BackupFileValidator.isAcceptable("Backup.MNBKN", () -> null)).isTrue();
    }

    @Test
    public void refusesADocumentThatIsNeitherByNameNorByContent() throws Exception {
        byte[] otherArchive = archiveWith("notes.txt");

        assertThat(
                        BackupFileValidator.isAcceptable(
                                "photo.jpg",
                                () ->
                                        new ByteArrayInputStream(
                                                "not a zip".getBytes(StandardCharsets.UTF_8))))
                .isFalse();
        assertThat(
                        BackupFileValidator.isAcceptable(
                                "other.zip (2)", () -> new ByteArrayInputStream(otherArchive)))
                .isFalse();
        assertThat(BackupFileValidator.isAcceptable("missing (2)", () -> null)).isFalse();
    }

    private static byte[] archiveWith(String entryName) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("attachments/note_1/photo.jpg"));
            zip.write(new byte[] {1, 2, 3});
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write("{}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
