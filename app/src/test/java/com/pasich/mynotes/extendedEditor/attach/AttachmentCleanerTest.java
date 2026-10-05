package com.pasich.mynotes.extendedEditor.attach;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Destructive-cleanup rules.
 *
 * <p>Every fixture here uses the production {@code editorjs://attachments/...} shape. A suite built
 * on synthetic {@code file://} URLs passed while the app deleted every attachment a user owned, so
 * matching production exactly is the point of these tests rather than an incidental detail.
 */
public class AttachmentCleanerTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File attachmentsRoot;
    private File noteFolder;

    @Before
    public void setUp() throws Exception {
        attachmentsRoot = temporaryFolder.newFolder("attachments");
        noteFolder = new File(attachmentsRoot, "note_42");
        assertThat(noteFolder.mkdirs()).isTrue();
    }

    @Test
    public void keepsEveryFileReferencedByProductionEditorUrls() throws Exception {
        File first = write("1731000000000_882134.jpg", "first");
        File second = write("1731000000001_991245.pdf", "second");

        AttachmentCleaner.Result result =
                AttachmentCleaner.cleanup(
                        attachmentsRoot,
                        42,
                        json(
                                "editorjs://attachments/note_42/1731000000000_882134.jpg",
                                "editorjs://attachments/note_42/1731000000001_991245.pdf"));

        assertThat(result).isEqualTo(AttachmentCleaner.Result.CLEANED);
        assertThat(first.exists()).isTrue();
        assertThat(second.exists()).isTrue();
        assertThat(contentOf(first)).isEqualTo("first");
        assertThat(contentOf(second)).isEqualTo("second");
    }

    @Test
    public void deletesOnlyGenuineOrphans() throws Exception {
        File referenced = write("1731000000000_882134.jpg", "keep");
        File orphan = write("1731000000009_000001.tmp", "drop");

        AttachmentCleaner.Result result =
                AttachmentCleaner.cleanup(
                        attachmentsRoot,
                        42,
                        json("editorjs://attachments/note_42/1731000000000_882134.jpg"));

        assertThat(result).isEqualTo(AttachmentCleaner.Result.CLEANED);
        assertThat(referenced.exists()).isTrue();
        assertThat(orphan.exists()).isFalse();
    }

    @Test
    public void abortsWithoutDeletingWhenOneReferenceCannotBeResolved() throws Exception {
        File resolvable = write("1731000000000_882134.jpg", "keep");
        File unrelated = write("1731000000009_000001.tmp", "would-be-orphan");

        AttachmentCleaner.Result result =
                AttachmentCleaner.cleanup(
                        attachmentsRoot,
                        42,
                        json(
                                "editorjs://attachments/note_42/1731000000000_882134.jpg",
                                "totally-broken-reference"));

        assertThat(result).isEqualTo(AttachmentCleaner.Result.ABORTED_UNRESOLVED_REFERENCE);
        assertThat(resolvable.exists()).isTrue();
        assertThat(unrelated.exists()).isTrue();
    }

    @Test
    public void abortsOnATraversalReferenceWithoutDeletingAnything() throws Exception {
        File kept = write("1731000000000_882134.jpg", "keep");

        AttachmentCleaner.Result result =
                AttachmentCleaner.cleanup(
                        attachmentsRoot, 42, json("editorjs://attachments/note_42/../../escape"));

        assertThat(result).isEqualTo(AttachmentCleaner.Result.ABORTED_UNRESOLVED_REFERENCE);
        assertThat(kept.exists()).isTrue();
    }

    @Test
    public void abortsOnUnreadableMetadataWithoutDeletingAnything() throws Exception {
        File kept = write("1731000000000_882134.jpg", "keep");

        AttachmentCleaner.Result result =
                AttachmentCleaner.cleanup(attachmentsRoot, 42, "{not valid json");

        assertThat(result).isEqualTo(AttachmentCleaner.Result.ABORTED_UNREADABLE_METADATA);
        assertThat(kept.exists()).isTrue();
    }

    @Test
    public void keepsSyncRestoredAttachmentsThatUseTheCanonicalUrlBuilder() throws Exception {
        String restoredName =
                "8f1d1b2c-2f3a-4c5d-8e9f-0a1b2c3d4e5f"
                        + "-e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        File restored = write(restoredName, "restored bytes");

        // Exactly the URL RoomSyncStore.restoreAttachments now stores.
        String url = AttachmentStorage.urlFor(42, restoredName);

        AttachmentCleaner.Result result = AttachmentCleaner.cleanup(attachmentsRoot, 42, json(url));

        assertThat(result).isEqualTo(AttachmentCleaner.Result.CLEANED);
        assertThat(restored.exists()).isTrue();
        assertThat(contentOf(restored)).isEqualTo("restored bytes");
    }

    @Test
    public void aRestoredAttachmentIsReachableThroughTheSameParserTheWebViewUses()
            throws Exception {
        File restored = write("1731000000000_882134.jpg", "rendered bytes");
        String url = AttachmentStorage.urlFor(42, "1731000000000_882134.jpg");

        AttachmentUrl parsed = AttachmentUrl.parse(url);

        assertThat(parsed).isNotNull();
        assertThat(url).startsWith("editorjs://attachments/");
        assertThat(parsed.resolveWithin(attachmentsRoot)).isEqualTo(restored.getCanonicalFile());
        assertThat(contentOf(parsed.resolveWithin(attachmentsRoot))).isEqualTo("rendered bytes");
    }

    @Test
    public void treatsAnEmptyReferenceListAsAFullClean() throws Exception {
        File orphan = write("1731000000009_000001.tmp", "drop");

        AttachmentCleaner.Result result = AttachmentCleaner.cleanup(attachmentsRoot, 42, "[]");

        assertThat(result).isEqualTo(AttachmentCleaner.Result.CLEANED);
        assertThat(orphan.exists()).isFalse();
    }

    @Test
    public void reportsNoFolderWhenTheNoteHasNoAttachmentDirectory() {
        AttachmentCleaner.Result result = AttachmentCleaner.cleanup(attachmentsRoot, 99, "[]");

        assertThat(result).isEqualTo(AttachmentCleaner.Result.NO_FOLDER);
    }

    @Test
    public void abortsOnANullEntryInTheAttachmentList() throws Exception {
        File kept = write("1731000000000_882134.jpg", "keep");

        AttachmentCleaner.Result result = AttachmentCleaner.cleanup(attachmentsRoot, 42, "[null]");

        assertThat(result).isEqualTo(AttachmentCleaner.Result.ABORTED_UNRESOLVED_REFERENCE);
        assertThat(kept.exists()).isTrue();
    }

    @Test
    public void treatsMissingMetadataAsNothingToClean() throws Exception {
        File orphan = write("1731000000009_000001.tmp", "drop");

        // A note that has never had an attachment stores null here.
        AttachmentCleaner.Result result = AttachmentCleaner.cleanup(attachmentsRoot, 42, null);

        assertThat(result).isEqualTo(AttachmentCleaner.Result.CLEANED);
        assertThat(orphan.exists()).isFalse();
    }

    @Test
    public void aReferenceToAnotherNotesFolderDoesNotProtectThisOne() throws Exception {
        // The reference resolves, so cleanup proceeds; it simply protects nothing here.
        File orphan = write("1731000000009_000001.tmp", "drop");

        AttachmentCleaner.Result result =
                AttachmentCleaner.cleanup(
                        attachmentsRoot, 42, json("editorjs://attachments/note_7/other.png"));

        assertThat(result).isEqualTo(AttachmentCleaner.Result.CLEANED);
        assertThat(orphan.exists()).isFalse();
    }

    private static final long NOW = 1_800_000_000_000L;

    @Test
    public void freshUploadWhoseBlockIsNotSavedYetIsKept() throws Exception {
        // The image was stored, but the block that references it has not reached the database.
        File uploaded = write("1731000000005_123456.jpg", "just picked");
        uploaded.setLastModified(NOW - AttachmentCleaner.GRACE_PERIOD_MS - 60_000);
        Set<String> recent = new HashSet<>();
        recent.add("1731000000005_123456.jpg");

        AttachmentCleaner.Result result =
                AttachmentCleaner.cleanup(attachmentsRoot, 42, json(), recent, NOW);

        assertThat(result).isEqualTo(AttachmentCleaner.Result.CLEANED);
        assertThat(uploaded.exists()).isTrue();
    }

    @Test
    public void fileInsideTheGracePeriodIsKeptEvenWhenNotRegistered() throws Exception {
        // The process that uploaded it died before the save: only its age protects it.
        File young = write("1731000000006_654321.jpg", "young");
        young.setLastModified(NOW - AttachmentCleaner.GRACE_PERIOD_MS / 2);

        AttachmentCleaner.cleanup(attachmentsRoot, 42, json(), new HashSet<>(), NOW);

        assertThat(young.exists()).isTrue();
    }

    @Test
    public void orphanOlderThanTheGracePeriodIsStillDeleted() throws Exception {
        File old = write("1731000000007_111111.jpg", "old orphan");
        old.setLastModified(NOW - AttachmentCleaner.GRACE_PERIOD_MS - 60_000);

        AttachmentCleaner.cleanup(attachmentsRoot, 42, json(), new HashSet<>(), NOW);

        assertThat(old.exists()).isFalse();
    }

    @Test
    public void uploadStopsBeingProtectedOnceASavedNoteReferencesIt() throws Exception {
        String name = "1731000000008_222222.jpg";
        File file = write(name, "inserted");
        file.setLastModified(NOW - AttachmentCleaner.GRACE_PERIOD_MS - 60_000);
        RecentAttachmentUploads.register(42, name);

        AttachmentCleaner.cleanup(
                attachmentsRoot,
                42,
                json("editorjs://attachments/note_42/" + name),
                RecentAttachmentUploads.protectedNames(42),
                NOW);

        assertThat(file.exists()).isTrue();
        assertThat(RecentAttachmentUploads.protectedNames(42)).isEmpty();

        // The user deletes the block later: from then on the file is a genuine orphan.
        AttachmentCleaner.cleanup(
                attachmentsRoot, 42, json(), RecentAttachmentUploads.protectedNames(42), NOW);

        assertThat(file.exists()).isFalse();
    }

    @After
    public void forgetRecentUploads() {
        RecentAttachmentUploads.clear();
    }

    private File write(String name, String content) throws Exception {
        File file = new File(noteFolder, name);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String contentOf(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static String json(String... urls) {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < urls.length; index++) {
            if (index > 0) result.append(',');
            result.append("{\"url\":\"").append(urls[index]).append("\"}");
        }
        return result.append(']').toString();
    }
}
