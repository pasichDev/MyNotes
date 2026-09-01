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
