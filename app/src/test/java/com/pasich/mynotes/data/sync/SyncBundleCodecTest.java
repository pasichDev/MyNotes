package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.Test;

public class SyncBundleCodecTest {
    private static final String NOTE_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TASK_ID = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";
    private static final String TOMBSTONE_ID = "6ba7b811-9dad-11d1-80b4-00c04fd430c8";
    private static final String ATTACHMENT_ID = "7d444840-9dc0-11d1-b245-5ffdce74fad2";
    private static final String HASH =
            "d6f1f3d5d8cf9b5a4a2469787998dc45eb59f401b93b1b4cde4998dc409ebdc8";
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T12:00:00Z");

    @Test
    public void encodeAndDecode_roundTripsRecordsAndAttachments() throws Exception {
        SyncBundleCodec codec = new SyncBundleCodec();
        SyncSnapshot snapshot =
                new SyncSnapshot(
                        Arrays.asList(
                                note("Milk"),
                                task("Buy milk"),
                                SyncRecord.tombstone(
                                        SyncRecord.Type.TAG,
                                        TOMBSTONE_ID,
                                        Instant.parse("2026-08-31T12:00:03Z"),
                                        Instant.parse("2026-08-31T12:00:04Z"))));

        byte[] bundle = codec.encode(snapshot, CREATED_AT);
        Map<String, String> entries = unzipToStrings(bundle);
        SyncBundleCodec.DecodedBundle decoded = codec.decode(new ByteArrayInputStream(bundle));

        assertThat(entries.keySet())
                .containsExactly(SyncBundleCodec.ENTRY_MANIFEST, SyncBundleCodec.ENTRY_RECORDS);
        assertThat(entries.get(SyncBundleCodec.ENTRY_RECORDS)).contains("\"attachmentIds\"");
        assertThat(entries.get(SyncBundleCodec.ENTRY_RECORDS)).contains("\"attachmentNames\"");
        assertThat(entries.get(SyncBundleCodec.ENTRY_RECORDS))
                .doesNotContain("attachmentsManifest");
        assertThat(decoded.getAttachmentsByHash()).containsKey(HASH);

        SyncRecord decodedNote = decoded.getSnapshot().find(SyncRecord.Type.NOTE, NOTE_ID);
        assertThat(decodedNote).isNotNull();
        assertThat(decodedNote.getPayload().get("title").getAsString()).isEqualTo("Shopping");
        assertThat(decodedNote.getPayload().getAsJsonArray("attachmentHashes").get(0).getAsString())
                .isEqualTo(HASH);
        JsonObject attachment =
                decodedNote
                        .getPayload()
                        .getAsJsonArray("attachmentsManifest")
                        .get(0)
                        .getAsJsonObject();
        assertThat(attachment.get("id").getAsString()).isEqualTo(ATTACHMENT_ID);
        assertThat(attachment.get("path").getAsString()).isEqualTo("attachments/" + HASH);
        assertThat(attachment.get("displayName").getAsString()).isEqualTo("receipt.png");
        assertThat(decoded.getSnapshot().find(SyncRecord.Type.TASK, TASK_ID)).isNotNull();
        assertThat(decoded.getSnapshot().find(SyncRecord.Type.TAG, TOMBSTONE_ID).isTombstone())
                .isTrue();
    }

    @Test
    public void encode_rejectsConflictingAttachmentMetadataForSameHash() {
        SyncBundleCodec codec = new SyncBundleCodec();
        JsonObject first = notePayload("One", "image/png", 42L, "receipt.png");
        JsonObject second = notePayload("Two", "application/pdf", 77L, "receipt.pdf");
        SyncSnapshot snapshot =
                new SyncSnapshot(
                        Arrays.asList(
                                SyncRecord.live(
                                        SyncRecord.Type.NOTE,
                                        NOTE_ID,
                                        Instant.parse("2026-08-31T12:00:00Z"),
                                        first),
                                SyncRecord.live(
                                        SyncRecord.Type.NOTE,
                                        "6ba7b812-9dad-11d1-80b4-00c04fd430c8",
                                        Instant.parse("2026-08-31T12:00:01Z"),
                                        second)));

        try {
            codec.encode(snapshot, CREATED_AT);
        } catch (IOException error) {
            assertThat(error).hasMessageThat().contains("conflicting attachment metadata");
            return;
        }
        throw new AssertionError("Expected an IOException");
    }

    private static SyncRecord note(String body) {
        return SyncRecord.live(
                SyncRecord.Type.NOTE,
                NOTE_ID,
                Instant.parse("2026-08-31T12:00:01Z"),
                notePayload(body, "image/png", 42L, "receipt.png"));
    }

    private static SyncRecord task(String title) {
        JsonObject payload = new JsonObject();
        payload.addProperty("title", title);
        payload.addProperty("isDone", false);
        return SyncRecord.live(
                SyncRecord.Type.TASK, TASK_ID, Instant.parse("2026-08-31T12:00:02Z"), payload);
    }

    private static JsonObject notePayload(String body, String mimeType, long size, String name) {
        JsonObject payload = new JsonObject();
        payload.addProperty("title", "Shopping");
        payload.addProperty("value", body);
        JsonArray hashes = new JsonArray();
        hashes.add(HASH);
        payload.add("attachmentHashes", hashes);
        JsonArray manifest = new JsonArray();
        manifest.add(
                new SyncBundleCodec.AttachmentManifestEntry(
                                ATTACHMENT_ID, HASH, mimeType, size, "attachments/" + HASH, name)
                        .toJson(true));
        payload.add("attachmentsManifest", manifest);
        return payload;
    }

    private static Map<String, String> unzipToStrings(byte[] bundle) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bundle))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                entries.put(entry.getName(), output.toString(StandardCharsets.UTF_8.name()));
            }
        }
        return entries;
    }
}
