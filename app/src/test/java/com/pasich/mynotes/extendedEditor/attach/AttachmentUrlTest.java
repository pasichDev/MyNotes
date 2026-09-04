package com.pasich.mynotes.extendedEditor.attach;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Parsing rules for stored attachment references.
 *
 * <p>Fixtures use the shape the editor actually writes ({@code editorjs://attachments/...}); the
 * legacy {@code file://} form is covered separately rather than standing in for production.
 */
public class AttachmentUrlTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void parsesTheProductionEditorUrl() {
        AttachmentUrl parsed =
                AttachmentUrl.parse("editorjs://attachments/note_146/1731000000000_882134.jpg");

        assertThat(parsed).isNotNull();
        assertThat(parsed.getNoteFolder()).isEqualTo("note_146");
        assertThat(parsed.getFileName()).isEqualTo("1731000000000_882134.jpg");
    }

    @Test
    public void parsesTheLegacyFileUrlAndNormalizesItToTheCanonicalScheme() {
        AttachmentUrl parsed = AttachmentUrl.parse("file://attachments/note_7/photo.png");

        assertThat(parsed).isNotNull();
        assertThat(parsed.canonical()).isEqualTo("editorjs://attachments/note_7/photo.png");
    }

    @Test
    public void parsesTheSyncRestoredFileNameShape() {
        String name =
                "8f1d1b2c-2f3a-4c5d-8e9f-0a1b2c3d4e5f"
                        + "-"
                        + "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        AttachmentUrl parsed = AttachmentUrl.parse("editorjs://attachments/note_3/" + name);

        assertThat(parsed).isNotNull();
        assertThat(parsed.getFileName()).isEqualTo(name);
    }

    @Test
    public void decodesPercentEncodedNames() {
        AttachmentUrl parsed =
                AttachmentUrl.parse("editorjs://attachments/note_2/report%20final.pdf");

        assertThat(parsed).isNotNull();
        assertThat(parsed.getFileName()).isEqualTo("report final.pdf");
    }

    @Test
    public void roundTripsNonAsciiNamesThroughCanonicalForm() {
        String canonical = AttachmentUrl.canonical(12, "звіт.pdf");
        AttachmentUrl parsed = AttachmentUrl.parse(canonical);

        assertThat(parsed).isNotNull();
        assertThat(parsed.getFileName()).isEqualTo("звіт.pdf");
        assertThat(canonical).doesNotContain("звіт");
    }

    @Test
    public void rejectsTraversalInTheFileName() {
        assertThat(AttachmentUrl.parse("editorjs://attachments/note_1/../../secret.txt")).isNull();
        assertThat(AttachmentUrl.parse("editorjs://attachments/note_1/..")).isNull();
    }

    @Test
    public void rejectsPercentEncodedTraversal() {
        assertThat(AttachmentUrl.parse("editorjs://attachments/note_1/%2e%2e%2fsecret.txt"))
                .isNull();
        assertThat(AttachmentUrl.parse("editorjs://attachments/%2e%2e/note_1/x.png")).isNull();
    }

    @Test
    public void rejectsForeignSchemesAuthoritiesAndShapes() {
        assertThat(AttachmentUrl.parse("https://attachments/note_1/x.png")).isNull();
        assertThat(AttachmentUrl.parse("editorjs://elsewhere/note_1/x.png")).isNull();
        assertThat(AttachmentUrl.parse("editorjs://attachments/notes_1/x.png")).isNull();
        assertThat(AttachmentUrl.parse("editorjs://attachments/note_0/x.png")).isNull();
        assertThat(AttachmentUrl.parse("editorjs://attachments/note_1")).isNull();
        assertThat(AttachmentUrl.parse("editorjs://attachments/note_1/")).isNull();
        assertThat(AttachmentUrl.parse("editorjs://attachments/note_1/a/b.png")).isNull();
        assertThat(AttachmentUrl.parse("/data/data/pkg/files/attachments/note_1/x.png")).isNull();
        assertThat(AttachmentUrl.parse(null)).isNull();
        assertThat(AttachmentUrl.parse("")).isNull();
    }

    @Test
    public void rejectsControlCharactersAndMalformedEscapes() {
        assertThat(AttachmentUrl.parse("editorjs://attachments/note_1/a%00b.png")).isNull();
        assertThat(AttachmentUrl.parse("editorjs://attachments/note_1/a%zz.png")).isNull();
        assertThat(AttachmentUrl.parse("editorjs://attachments/note_1/a%2.png")).isNull();
    }

    @Test
    public void resolvesInsideTheAttachmentRoot() throws Exception {
        File root = temporaryFolder.newFolder("attachments");
        File noteFolder = new File(root, "note_5");
        assertThat(noteFolder.mkdirs()).isTrue();
        File file = new File(noteFolder, "photo.png");
        assertThat(file.createNewFile()).isTrue();

        AttachmentUrl parsed = AttachmentUrl.parse("editorjs://attachments/note_5/photo.png");

        assertThat(parsed).isNotNull();
        assertThat(parsed.resolveWithin(root)).isEqualTo(file.getCanonicalFile());
    }

    @Test
    public void ignoresAQueryOrFragmentAfterThePath() {
        AttachmentUrl withQuery =
                AttachmentUrl.parse("editorjs://attachments/note_4/photo.png?v=2");
        AttachmentUrl withFragment =
                AttachmentUrl.parse("editorjs://attachments/note_4/photo.png#top");

        assertThat(withQuery).isNotNull();
        assertThat(withQuery.getFileName()).isEqualTo("photo.png");
        assertThat(withFragment).isNotNull();
        assertThat(withFragment.getFileName()).isEqualTo("photo.png");
    }

    @Test
    public void acceptsAnUppercaseScheme() {
        AttachmentUrl parsed = AttachmentUrl.parse("EDITORJS://attachments/note_4/photo.png");

        assertThat(parsed).isNotNull();
        assertThat(parsed.canonical()).isEqualTo("editorjs://attachments/note_4/photo.png");
    }

    @Test
    public void twoReferencesToTheSameFileAreEqual() {
        AttachmentUrl fromCanonical = AttachmentUrl.parse("editorjs://attachments/note_4/a.png");
        AttachmentUrl fromLegacy = AttachmentUrl.parse("file://attachments/note_4/a.png");

        assertThat(fromCanonical).isEqualTo(fromLegacy);
        assertThat(fromCanonical.hashCode()).isEqualTo(fromLegacy.hashCode());
        assertThat(fromCanonical.toString()).isEqualTo("editorjs://attachments/note_4/a.png");
        assertThat(fromCanonical)
                .isNotEqualTo(AttachmentUrl.parse("editorjs://attachments/note_4/b.png"));
    }

    @Test
    public void resolvingAgainstAMissingRootStillStaysInsideIt() throws Exception {
        File root = new File(temporaryFolder.getRoot(), "not-created-yet");
        AttachmentUrl parsed = AttachmentUrl.parse("editorjs://attachments/note_9/photo.png");

        assertThat(parsed).isNotNull();
        File resolved = parsed.resolveWithin(root);
        assertThat(resolved).isNotNull();
        assertThat(resolved.getPath()).startsWith(root.getCanonicalPath() + File.separator);
    }

    @Test
    public void canonicalRefusesToBuildAnUnsafeReference() {
        try {
            AttachmentUrl.canonical(1, "../escape.png");
            throw new AssertionError("Expected an unsafe file name to be rejected");
        } catch (IllegalArgumentException expected) {
            // The single URL producer must not be able to emit a traversal.
        }
        try {
            AttachmentUrl.canonical(0, "photo.png");
            throw new AssertionError("Expected a non-positive note id to be rejected");
        } catch (IllegalArgumentException expected) {
            // Note ids are SQLite row ids and start at 1.
        }
    }
}
