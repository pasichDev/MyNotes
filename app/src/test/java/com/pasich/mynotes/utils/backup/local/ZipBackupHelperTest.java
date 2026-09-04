package com.pasich.mynotes.utils.backup.local;

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.Gson;
import com.pasich.mynotes.utils.backup.models.JsonBackup;
import com.pasich.mynotes.utils.constants.Backup;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Reading a backup archive must not be able to change a note that already exists.
 *
 * <p>Attachments used to be extracted straight into the live {@code attachments/note_<id>} folders,
 * before the JSON was even validated and before the restore had decided whether that id belonged to
 * some other note already on the device.
 */
public class ZipBackupHelperTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void extractsIntoTheStagingDirectoryAndNeverIntoTheLiveFolders() throws Exception {
        File live = temporaryFolder.newFolder("files", "attachments", "note_7");
        File mine = new File(live, "photo.jpg");
        Files.write(mine.toPath(), "mine".getBytes(StandardCharsets.UTF_8));
        File staging = new File(temporaryFolder.getRoot(), "cache/restore-staging/attachments");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(
                Backup.FILE_NAME_BACKUP,
                new Gson().toJson(new JsonBackup()).getBytes(StandardCharsets.UTF_8));
        entries.put("attachments/note_7/photo.jpg", "foreign".getBytes(StandardCharsets.UTF_8));

        JsonBackup backup = ZipBackupHelper.readZipBackup(zip(entries), staging);

        assertThat(backup.isError()).isFalse();
        assertThat(new String(Files.readAllBytes(mine.toPath()), StandardCharsets.UTF_8))
                .isEqualTo("mine");
        File staged = new File(new File(staging, "note_7"), "photo.jpg");
        assertThat(new String(Files.readAllBytes(staged.toPath()), StandardCharsets.UTF_8))
                .isEqualTo("foreign");
    }

    @Test
    public void skipsEveryEntryThatIsNotOneFileInOneNoteFolder() throws Exception {
        // The archive is untrusted. "note_5/../note_6/x" lands in a folder the note's JSON never
        // names, and "evil/x" or "note_5/sub/x" are places the cleaner never looks, so they would
        // be written once and never reclaimed.
        File staging = new File(temporaryFolder.getRoot(), "staging/attachments");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(
                Backup.FILE_NAME_BACKUP,
                new Gson().toJson(new JsonBackup()).getBytes(StandardCharsets.UTF_8));
        entries.put(
                "attachments/note_5/../note_6/x.jpg", "traversal".getBytes(StandardCharsets.UTF_8));
        entries.put("attachments/evil/x.jpg", "foreign folder".getBytes(StandardCharsets.UTF_8));
        entries.put("attachments/note_5/sub/x.jpg", "nested".getBytes(StandardCharsets.UTF_8));
        entries.put("attachments/../../databases/notes", "escape".getBytes(StandardCharsets.UTF_8));
        entries.put("attachments/note_5/ok.jpg", "kept".getBytes(StandardCharsets.UTF_8));

        JsonBackup backup = ZipBackupHelper.readZipBackup(zip(entries), staging);

        assertThat(backup.isError()).isFalse();
        assertThat(new File(staging, "note_5/ok.jpg").isFile()).isTrue();
        assertThat(new File(staging, "note_6/x.jpg").exists()).isFalse();
        assertThat(new File(staging, "evil").exists()).isFalse();
        assertThat(new File(staging, "note_5/sub").exists()).isFalse();
        assertThat(new File(temporaryFolder.getRoot(), "databases").exists()).isFalse();
        assertThat(new File(temporaryFolder.getRoot(), "staging/databases").exists()).isFalse();
    }

    @Test
    public void anArchiveWithoutTheJsonIsAnError() throws Exception {
        File staging = new File(temporaryFolder.getRoot(), "staging/attachments");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("attachments/note_5/ok.jpg", "kept".getBytes(StandardCharsets.UTF_8));

        assertThat(ZipBackupHelper.readZipBackup(zip(entries), staging).isError()).isTrue();
    }

    @Test
    public void refusesAnArchiveThatUnpacksPastTheCeiling() throws Exception {
        // ZIP compresses a run of zeros a thousandfold; an archive is untrusted input and a
        // small one could fill the device.
        File staging = new File(temporaryFolder.getRoot(), "staging/attachments");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(
                Backup.FILE_NAME_BACKUP,
                new Gson().toJson(new JsonBackup()).getBytes(StandardCharsets.UTF_8));
        entries.put("attachments/note_5/zeros.bin", new byte[64 * 1024]);

        JsonBackup backup = ZipBackupHelper.readZipBackup(zip(entries), staging, 16 * 1024);

        assertThat(backup.isError()).isTrue();
    }

    private static ZipInputStream zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }
}
