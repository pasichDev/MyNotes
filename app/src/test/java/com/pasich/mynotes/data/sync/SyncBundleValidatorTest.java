package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Random;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;

public class SyncBundleValidatorTest {
    private static final String NOTE_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String ATTACHMENT_ID = "7d444840-9dc0-11d1-b245-5ffdce74fad2";
    private static final String HASH =
            "d6f1f3d5d8cf9b5a4a2469787998dc45eb59f401b93b1b4cde4998dc409ebdc8";
    private final SyncBundleCodec codec = new SyncBundleCodec();
    private final SyncBundleValidator validator = new SyncBundleValidator();

    @Test
    public void validate_rejectsChecksumMismatch() throws Exception {
        byte[] valid = codec.encode(snapshot(), Instant.parse("2026-08-31T12:00:00Z"));
        JsonObject manifest = readJsonEntry(valid, SyncBundleCodec.ENTRY_MANIFEST);
        manifest.addProperty("recordsSha256", HASH);

        try {
            validator.validate(
                    new ByteArrayInputStream(
                            rewriteEntry(valid, SyncBundleCodec.ENTRY_MANIFEST, manifest)));
        } catch (IOException error) {
            assertThat(error).hasMessageThat().contains("checksum");
            return;
        }
        throw new AssertionError("Expected an IOException");
    }

    @Test
    public void validate_rejectsUnknownAttachmentReference() throws Exception {
        byte[] valid = codec.encode(snapshot(), Instant.parse("2026-08-31T12:00:00Z"));
        JsonObject records = readJsonEntry(valid, SyncBundleCodec.ENTRY_RECORDS);
        JsonArray notes = records.getAsJsonArray("notes");
        notes.get(0)
                .getAsJsonObject()
                .getAsJsonArray("attachmentIds")
                .set(0, new com.google.gson.JsonPrimitive(NOTE_ID));

        try {
            validator.validate(
                    new ByteArrayInputStream(
                            rewriteEntry(valid, SyncBundleCodec.ENTRY_RECORDS, records)));
        } catch (IOException error) {
            assertThat(error).hasMessageThat().contains("unknown attachment");
            return;
        }
        throw new AssertionError("Expected an IOException");
    }

    @Test
    public void validate_rejectsOversizedAttachmentMetadata() throws Exception {
        byte[] valid = codec.encode(snapshot(), Instant.parse("2026-08-31T12:00:00Z"));
        JsonObject manifest = readJsonEntry(valid, SyncBundleCodec.ENTRY_MANIFEST);
        manifest.getAsJsonArray("attachments")
                .get(0)
                .getAsJsonObject()
                .addProperty("size", SyncBundleValidator.MAX_ATTACHMENT_BYTES + 1L);

        try {
            validator.validate(
                    new ByteArrayInputStream(
                            rewriteEntry(valid, SyncBundleCodec.ENTRY_MANIFEST, manifest)));
        } catch (IOException error) {
            assertThat(error).hasMessageThat().contains("oversized attachment");
            return;
        }
        throw new AssertionError("Expected an IOException");
    }

    @Test
    public void validate_rejectsPathTraversalAttachmentName() throws Exception {
        byte[] valid = codec.encode(snapshot(), Instant.parse("2026-08-31T12:00:00Z"));
        JsonObject manifest = readJsonEntry(valid, SyncBundleCodec.ENTRY_MANIFEST);
        manifest.getAsJsonArray("attachments")
                .get(0)
                .getAsJsonObject()
                .addProperty("displayName", "../../outside.txt");

        try {
            validator.validate(
                    new ByteArrayInputStream(
                            rewriteEntry(valid, SyncBundleCodec.ENTRY_MANIFEST, manifest)));
        } catch (IOException error) {
            assertThat(error).hasMessageThat().contains("safe file name");
            return;
        }
        throw new AssertionError("Expected an IOException");
    }

    @Test
    public void validate_rejectsZipTraversalEntries() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("../sync-manifest.json"));
            zip.write("{}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(SyncBundleCodec.ENTRY_RECORDS));
            zip.write("{}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        try {
            validator.validate(new ByteArrayInputStream(output.toByteArray()));
        } catch (IOException error) {
            assertThat(error).hasMessageThat().contains("invalid ZIP path");
            return;
        }
        throw new AssertionError("Expected an IOException");
    }

    @Test
    public void validate_rejectsNoteWithTooManyAttachmentReferences() throws Exception {
        byte[] valid = codec.encode(snapshot(), Instant.parse("2026-08-31T12:00:00Z"));
        JsonObject records = readJsonEntry(valid, SyncBundleCodec.ENTRY_RECORDS);
        JsonArray attachmentIds =
                records.getAsJsonArray("notes")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonArray("attachmentIds");
        for (int index = 1; index <= SyncBundleValidator.MAX_ATTACHMENTS_PER_NOTE; index++) {
            attachmentIds.add(UUID.randomUUID().toString());
        }

        try {
            validator.validate(
                    new ByteArrayInputStream(
                            rewriteEntry(valid, SyncBundleCodec.ENTRY_RECORDS, records)));
        } catch (IOException error) {
            assertThat(error).hasMessageThat().contains("note exceeds the attachment limit");
            return;
        }
        throw new AssertionError("Expected an IOException");
    }

    @Test
    public void validate_rejectsOversizedRecordPayload() throws Exception {
        byte[] valid = codec.encode(snapshot(), Instant.parse("2026-08-31T12:00:00Z"));
        JsonObject records = readJsonEntry(valid, SyncBundleCodec.ENTRY_RECORDS);
        StringBuilder oversized = new StringBuilder();
        Random random = new Random(0L);
        for (int index = 0; index <= SyncBundleValidator.MAX_RECORD_PAYLOAD_BYTES; index++) {
            oversized.append((char) ('a' + random.nextInt(26)));
        }
        records.getAsJsonArray("notes")
                .get(0)
                .getAsJsonObject()
                .addProperty("value", oversized.toString());

        try {
            validator.validate(
                    new ByteArrayInputStream(
                            rewriteEntry(valid, SyncBundleCodec.ENTRY_RECORDS, records)));
        } catch (IOException error) {
            assertThat(error).hasMessageThat().contains("payload size limit");
            return;
        }
        throw new AssertionError("Expected an IOException");
    }

    private SyncSnapshot snapshot() throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("title", "Shopping");
        payload.addProperty("value", "Milk");
        JsonArray hashes = new JsonArray();
        hashes.add(HASH);
        payload.add("attachmentHashes", hashes);
        JsonArray manifest = new JsonArray();
        manifest.add(
                new SyncBundleCodec.AttachmentManifestEntry(
                                ATTACHMENT_ID,
                                HASH,
                                "image/png",
                                42L,
                                "attachments/" + HASH,
                                "receipt.png")
                        .toJson(true));
        payload.add("attachmentsManifest", manifest);
        return new SyncSnapshot(
                Collections.singletonList(
                        SyncRecord.live(
                                SyncRecord.Type.NOTE,
                                NOTE_ID,
                                Instant.parse("2026-08-31T12:00:00Z"),
                                payload)));
    }

    @Test
    public void validate_acceptsABundleCarryingAnUnresolvedAlternative() throws Exception {
        byte[] bundle = bundleWithAlternatives(alternative("A losing version"), null);

        SyncBundleValidator.ValidatedBundle validated =
                validator.validate(new ByteArrayInputStream(bundle));

        assertThat(validated.getRecords().getAsJsonArray("alternatives")).hasSize(1);
    }

    @Test
    public void validate_rejectsAnAlternativeWithAnInvalidRecordType() throws Exception {
        JsonObject bad = alternative("Bad type");
        bad.addProperty("type", "not-a-record-type");

        assertRejects(bundleWithAlternatives(bad, null), "Unsupported sync record type");
    }

    @Test
    public void validate_rejectsAnAlternativeWithANonCanonicalId() throws Exception {
        JsonObject bad = alternative("Bad id");
        bad.addProperty("id", "NOT-A-UUID");

        assertRejects(bundleWithAlternatives(bad, null), "UUID");
    }

    @Test
    public void validate_rejectsAnAlternativeDeletedBeforeItWasUpdated() throws Exception {
        JsonObject bad = alternative("Impossible tombstone");
        bad.addProperty("updatedAt", "2026-08-31T12:00:10Z");
        bad.addProperty("deletedAt", "2026-08-31T12:00:00Z");

        assertRejects(bundleWithAlternatives(bad, null), "deletedAt must not be before updatedAt");
    }

    @Test
    public void validate_rejectsDuplicateAlternatives() throws Exception {
        byte[] bundle =
                bundleWithAlternatives(
                        alternative("Same version"), null, alternative("Same version"));

        assertRejects(bundle, "duplicate conflict alternatives");
    }

    @Test
    public void validate_rejectsAResolvedVersionIdThatIsNotASha256() throws Exception {
        assertRejects(bundleWithAlternatives(null, "not-a-digest"), "invalid resolved version id");
    }

    @Test
    public void validate_rejectsDuplicateResolvedVersionIds() throws Exception {
        JsonObject records = recordsOfAValidBundle();
        JsonArray resolved = new JsonArray();
        resolved.add(HASH);
        resolved.add(HASH);
        records.add("resolvedAlternatives", resolved);

        assertRejects(rebuild(records), "duplicate resolved version ids");
    }

    private void assertRejects(byte[] bundle, String expectedMessage) {
        try {
            validator.validate(new ByteArrayInputStream(bundle));
            throw new AssertionError("Expected the bundle to be rejected: " + expectedMessage);
        } catch (IOException | RuntimeException error) {
            assertThat(error).hasMessageThat().contains(expectedMessage);
        }
    }

    /** A minimal live-note alternative entry, in the shape the codec writes. */
    private static JsonObject alternative(String value) {
        JsonObject item = new JsonObject();
        item.addProperty("type", "note");
        item.addProperty("id", NOTE_ID);
        item.addProperty("updatedAt", "2026-08-31T12:00:00Z");
        item.addProperty("title", "Shopping");
        item.addProperty("value", value);
        return item;
    }

    private JsonObject recordsOfAValidBundle() throws IOException {
        byte[] valid = codec.encode(snapshot(), Instant.parse("2026-08-31T12:00:00Z"));
        return readJsonEntry(valid, SyncBundleCodec.ENTRY_RECORDS);
    }

    private byte[] bundleWithAlternatives(JsonObject first, String resolvedId, JsonObject... more)
            throws IOException {
        JsonObject records = recordsOfAValidBundle();
        JsonArray alternatives = new JsonArray();
        if (first != null) alternatives.add(first);
        for (JsonObject extra : more) alternatives.add(extra);
        records.add("alternatives", alternatives);
        if (resolvedId != null) {
            JsonArray resolved = new JsonArray();
            resolved.add(resolvedId);
            records.add("resolvedAlternatives", resolved);
        }
        return rebuild(records);
    }

    /** Re-zips a bundle around edited records, refreshing the manifest checksum and length. */
    private byte[] rebuild(JsonObject records) throws IOException {
        byte[] valid = codec.encode(snapshot(), Instant.parse("2026-08-31T12:00:00Z"));
        byte[] recordBytes = records.toString().getBytes(StandardCharsets.UTF_8);
        JsonObject manifest = readJsonEntry(valid, SyncBundleCodec.ENTRY_MANIFEST);
        manifest.addProperty("recordsSha256", SyncBundleValidator.sha256(recordBytes));
        manifest.addProperty("recordsBytes", recordBytes.length);
        return zip(manifest.toString(), records.toString());
    }

    private static JsonObject readJsonEntry(byte[] bundle, String entryName) throws IOException {
        try (java.util.zip.ZipInputStream input =
                new java.util.zip.ZipInputStream(new ByteArrayInputStream(bundle))) {
            java.util.zip.ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                if (entryName.equals(entry.getName())) {
                    return JsonParser.parseString(output.toString(StandardCharsets.UTF_8.name()))
                            .getAsJsonObject();
                }
            }
        }
        throw new IOException("Missing entry " + entryName);
    }

    private static byte[] rewriteEntry(byte[] bundle, String entryName, JsonObject replacement)
            throws IOException {
        JsonObject manifest = readJsonEntry(bundle, SyncBundleCodec.ENTRY_MANIFEST);
        JsonObject records = readJsonEntry(bundle, SyncBundleCodec.ENTRY_RECORDS);
        if (SyncBundleCodec.ENTRY_MANIFEST.equals(entryName)) {
            manifest = replacement;
        } else if (SyncBundleCodec.ENTRY_RECORDS.equals(entryName)) {
            records = replacement;
            byte[] recordBytes = records.toString().getBytes(StandardCharsets.UTF_8);
            manifest.addProperty("recordsSha256", SyncBundleValidator.sha256(recordBytes));
            manifest.addProperty("recordsBytes", recordBytes.length);
        }
        return zip(manifest.toString(), records.toString());
    }

    private static byte[] zip(String manifest, String records) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(SyncBundleCodec.ENTRY_MANIFEST));
            zip.write(manifest.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(SyncBundleCodec.ENTRY_RECORDS));
            zip.write(records.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}
