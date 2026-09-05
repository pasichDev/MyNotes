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

    @Test
    public void encode_preservesTwoLogicalAttachmentsThatShareOneBlob() throws Exception {
        SyncBundleCodec codec = new SyncBundleCodec();
        JsonObject first = notePayload("One", "image/png", 42L, "first.png");
        JsonObject second = notePayload("Two", "image/png", 42L, "second.png");
        second.getAsJsonArray("attachmentsManifest")
                .get(0)
                .getAsJsonObject()
                .addProperty("id", "550e8400-e29b-41d4-a716-446655440099");
        SyncSnapshot decoded =
                codec.decode(
                                new ByteArrayInputStream(
                                        codec.encode(
                                                new SyncSnapshot(
                                                        Arrays.asList(
                                                                SyncRecord.live(
                                                                        SyncRecord.Type.NOTE,
                                                                        NOTE_ID,
                                                                        CREATED_AT,
                                                                        first),
                                                                SyncRecord.live(
                                                                        SyncRecord.Type.NOTE,
                                                                        "6ba7b812-9dad-11d1-80b4-00c04fd430c8",
                                                                        CREATED_AT,
                                                                        second))),
                                                CREATED_AT)))
                        .getSnapshot();

        assertThat(
                        decoded.find(SyncRecord.Type.NOTE, NOTE_ID)
                                .getPayload()
                                .getAsJsonArray("attachmentsManifest"))
                .hasSize(1);
        assertThat(
                        decoded.find(SyncRecord.Type.NOTE, "6ba7b812-9dad-11d1-80b4-00c04fd430c8")
                                .getPayload()
                                .getAsJsonArray("attachmentsManifest"))
                .hasSize(1);
    }

    @Test
    public void decode_keysAttachmentNamesByHashSoTheStoreCanResolveThem() throws Exception {
        SyncBundleCodec codec = new SyncBundleCodec();
        byte[] bundle = codec.encode(new SyncSnapshot(Arrays.asList(note("Milk"))), CREATED_AT);

        SyncRecord decoded =
                codec.decode(new ByteArrayInputStream(bundle))
                        .getSnapshot()
                        .find(SyncRecord.Type.NOTE, NOTE_ID);

        // The name restoreAttachments actually reads is the one on the manifest entry.
        JsonObject entry =
                decoded.getPayload().getAsJsonArray("attachmentsManifest").get(0).getAsJsonObject();
        assertThat(entry.get("displayName").getAsString()).isEqualTo("receipt.png");

        // The map itself stays keyed by logical attachment id, the same shape RoomSyncStore
        // builds, so a decoded record hashes equal to the identical local one.
        JsonObject names = decoded.getPayload().getAsJsonObject("attachmentNames");
        assertThat(names.has(HASH)).isFalse();
        assertThat(names.get(ATTACHMENT_ID).getAsString()).isEqualTo("receipt.png");
    }

    @Test
    public void decode_dropsDeviceLocalFieldsWrittenByOlderReleases() throws Exception {
        SyncBundleCodec codec = new SyncBundleCodec();
        JsonObject legacy = new JsonObject();
        legacy.addProperty("title", "Buy milk");
        legacy.addProperty("isDone", false);
        legacy.addProperty("id", 7); // Room primary key, meaningless on any other device
        legacy.addProperty("categoryId", 3);
        SyncRecord task =
                SyncRecord.live(
                        SyncRecord.Type.TASK,
                        TASK_ID,
                        Instant.parse("2026-08-31T12:00:02Z"),
                        legacy);

        byte[] bundle = codec.encode(new SyncSnapshot(Arrays.asList(task)), CREATED_AT);
        SyncRecord decoded =
                codec.decode(new ByteArrayInputStream(bundle))
                        .getSnapshot()
                        .find(SyncRecord.Type.TASK, TASK_ID);

        assertThat(decoded.getPayload().has("id")).isFalse();
        assertThat(decoded.getPayload().has("categoryId")).isFalse();
        assertThat(decoded.getPayload().get("title").getAsString()).isEqualTo("Buy milk");
    }

    @Test
    public void decodedRecordMatchesLocalRecordThatNeverCarriedLocalKeys() throws Exception {
        // Two devices hold the same logical task under different Room primary keys. Once the
        // device-local fields are stripped on both sides the canonical hashes agree, so the
        // equal-timestamp tiebreaker in SyncMerger no longer invents a conflict on every sync.
        SyncBundleCodec codec = new SyncBundleCodec();
        JsonObject remotePayload = new JsonObject();
        remotePayload.addProperty("title", "Buy milk");
        remotePayload.addProperty("isDone", false);
        remotePayload.addProperty("id", 7);
        Instant updatedAt = Instant.parse("2026-08-31T12:00:02Z");
        byte[] bundle =
                codec.encode(
                        new SyncSnapshot(
                                Arrays.asList(
                                        SyncRecord.live(
                                                SyncRecord.Type.TASK,
                                                TASK_ID,
                                                updatedAt,
                                                remotePayload))),
                        CREATED_AT);
        SyncRecord decoded =
                codec.decode(new ByteArrayInputStream(bundle))
                        .getSnapshot()
                        .find(SyncRecord.Type.TASK, TASK_ID);

        JsonObject localPayload = new JsonObject();
        localPayload.addProperty("title", "Buy milk");
        localPayload.addProperty("isDone", false);
        localPayload.addProperty("id", 12);
        SyncMetadata.stripDeviceLocalFields(SyncMetadata.RECORD_TYPE_TASK, localPayload);
        SyncRecord local = SyncRecord.live(SyncRecord.Type.TASK, TASK_ID, updatedAt, localPayload);

        assertThat(decoded.getCanonicalPayloadHash()).isEqualTo(local.getCanonicalPayloadHash());
        assertThat(new SyncMerger().merge(snapshotOf(local), snapshotOf(decoded)).getConflicts())
                .isEmpty();
    }

    private static SyncSnapshot snapshotOf(SyncRecord record) {
        return new SyncSnapshot(Arrays.asList(record));
    }

    private static SyncRecord note(String body) {
        return SyncRecord.live(
                SyncRecord.Type.NOTE,
                NOTE_ID,
                Instant.parse("2026-08-31T12:00:01Z"),
                notePayload(body, "image/png", 42L, "receipt.png"));
    }

    @Test
    public void roundTrip_ofALocallyBuiltNoteWithAnAttachment_hashesIdentically() throws Exception {
        // Exactly the payload shape RoomSyncStore.addAttachmentMetadata produces: a manifest,
        // the hash list, and names keyed by logical attachment id.
        JsonObject local = notePayload("Body", "image/png", 12L, "receipt.png");
        JsonObject names = new JsonObject();
        names.addProperty(ATTACHMENT_ID, "receipt.png");
        local.add("attachmentNames", names);
        SyncRecord localRecord =
                SyncRecord.live(
                        SyncRecord.Type.NOTE,
                        NOTE_ID,
                        Instant.parse("2026-08-31T12:00:01Z"),
                        local);

        SyncBundleCodec codec = new SyncBundleCodec();
        byte[] bundle =
                codec.encode(
                        new SyncSnapshot(java.util.Collections.singletonList(localRecord)),
                        Instant.parse("2026-08-31T12:00:00Z"));
        SyncRecord decoded =
                codec.decode(new ByteArrayInputStream(bundle))
                        .getSnapshot()
                        .find(SyncRecord.Type.NOTE, NOTE_ID);

        // The invariant the merge engine depends on: a record that made the round trip is the
        // same version as the one that went in. While these differed, every note with an
        // attachment conflicted with itself on every sync and republished a bundle each time.
        assertThat(decoded.getCanonicalPayloadHash())
                .isEqualTo(localRecord.getCanonicalPayloadHash());
    }

    @Test
    public void encode_dropsEmptyAttachmentFieldsInsteadOfLeakingThemToTheWire() throws Exception {
        // The shape an older client wrote for a note with no attachments.
        JsonObject payload = new JsonObject();
        payload.addProperty("title", "Shopping");
        payload.addProperty("value", "Body");
        payload.add("attachmentsManifest", new JsonArray());
        payload.add("attachmentHashes", new JsonArray());
        payload.add("attachmentNames", new JsonObject());
        SyncRecord local =
                SyncRecord.live(
                        SyncRecord.Type.NOTE,
                        NOTE_ID,
                        Instant.parse("2026-08-31T12:00:01Z"),
                        payload);

        SyncBundleCodec codec = new SyncBundleCodec();
        byte[] bundle =
                codec.encode(
                        new SyncSnapshot(java.util.Collections.singletonList(local)),
                        Instant.parse("2026-08-31T12:00:00Z"));
        SyncRecord decoded =
                codec.decode(new ByteArrayInputStream(bundle))
                        .getSnapshot()
                        .find(SyncRecord.Type.NOTE, NOTE_ID);

        // None of the three may survive: a decoded record carries no attachment fields for a
        // note without attachments, so leaving one behind makes the two shapes hash differently.
        assertThat(decoded.getPayload().has("attachmentNames")).isFalse();
        assertThat(decoded.getPayload().has("attachmentsManifest")).isFalse();
        assertThat(decoded.getPayload().has("attachmentHashes")).isFalse();
        assertThat(unzipToStrings(bundle).get(SyncBundleCodec.ENTRY_RECORDS))
                .doesNotContain("attachmentNames");
    }

    @Test
    public void encode_carriesAnAlternativeWhoseAttachmentIdMapsToDifferentContent()
            throws Exception {
        // A live note and one of its unresolved alternatives can describe different bytes under
        // one logical attachment id — the file was replaced in place, or two devices derived the
        // same id. Refusing to encode that failed every publish for the account, before the
        // conflict could even be stored for the user to settle.
        String otherHash = "0000000000000000000000000000000000000000000000000000000000000001";
        // Both payloads in the shape RoomSyncStore builds, names included.
        JsonObject livePayload = notePayload("Live", "image/png", 42L, "receipt.png");
        JsonObject names = new JsonObject();
        names.addProperty(ATTACHMENT_ID, "receipt.png");
        livePayload.add("attachmentNames", names.deepCopy());
        SyncRecord live =
                SyncRecord.live(
                        SyncRecord.Type.NOTE,
                        NOTE_ID,
                        Instant.parse("2026-08-31T12:00:05Z"),
                        livePayload);
        JsonObject alternativePayload = notePayload("Older", "image/png", 77L, "receipt.png");
        alternativePayload.add("attachmentNames", names.deepCopy());
        alternativePayload
                .getAsJsonArray("attachmentsManifest")
                .get(0)
                .getAsJsonObject()
                .addProperty("sha256", otherHash);
        alternativePayload
                .getAsJsonArray("attachmentsManifest")
                .get(0)
                .getAsJsonObject()
                .addProperty("path", "attachments/" + otherHash);
        alternativePayload
                .getAsJsonArray("attachmentHashes")
                .set(0, new com.google.gson.JsonPrimitive(otherHash));
        SyncRecord alternative =
                SyncRecord.live(
                        SyncRecord.Type.NOTE,
                        NOTE_ID,
                        Instant.parse("2026-08-31T12:00:04Z"),
                        alternativePayload);

        SyncBundleCodec codec = new SyncBundleCodec();
        byte[] bundle =
                codec.encode(
                        new SyncSnapshot(java.util.Collections.singletonList(live)),
                        CREATED_AT,
                        java.util.Collections.emptyList(),
                        java.util.Collections.singletonList(alternative),
                        java.util.Collections.emptySet());
        SyncBundleCodec.DecodedBundle decoded = codec.decode(new ByteArrayInputStream(bundle));

        // Both blobs are described, and the decoded alternative is the version that went in:
        // same logical id, same content, same identity for the resolution bookkeeping.
        assertThat(decoded.getAttachmentsByHash().keySet()).containsExactly(HASH, otherHash);
        SyncRecord decodedAlternative = decoded.getAlternatives().get(0);
        JsonObject entry =
                decodedAlternative
                        .getPayload()
                        .getAsJsonArray("attachmentsManifest")
                        .get(0)
                        .getAsJsonObject();
        assertThat(entry.get("id").getAsString()).isEqualTo(ATTACHMENT_ID);
        assertThat(entry.get("sha256").getAsString()).isEqualTo(otherHash);
        assertThat(decodedAlternative.getCanonicalPayloadHash())
                .isEqualTo(alternative.getCanonicalPayloadHash());
        assertThat(
                        decoded.getSnapshot()
                                .find(SyncRecord.Type.NOTE, NOTE_ID)
                                .getCanonicalPayloadHash())
                .isEqualTo(live.getCanonicalPayloadHash());
    }

    @Test
    public void encode_keepsBothBlobsWhenOneNoteCarriesOneIdWithTwoContents() throws Exception {
        // A duplicated block whose file was later replaced: one logical id, two hashes, in ONE
        // record. Keying the re-keying by id sent both references to the second blob, so every
        // receiver wrote its bytes for both and the first attachment's content was lost.
        String otherHash = "0000000000000000000000000000000000000000000000000000000000000002";
        JsonObject payload = notePayload("Body", "image/png", 42L, "first.png");
        JsonObject second =
                new SyncBundleCodec.AttachmentManifestEntry(
                                ATTACHMENT_ID,
                                otherHash,
                                "image/png",
                                43L,
                                "attachments/" + otherHash,
                                "second.png")
                        .toJson(true);
        payload.getAsJsonArray("attachmentsManifest").add(second);
        payload.getAsJsonArray("attachmentHashes").add(otherHash);
        JsonObject names = new JsonObject();
        names.addProperty(ATTACHMENT_ID, "second.png");
        payload.add("attachmentNames", names);
        SyncRecord local =
                SyncRecord.live(
                        SyncRecord.Type.NOTE,
                        NOTE_ID,
                        Instant.parse("2026-08-31T12:00:01Z"),
                        payload);

        SyncBundleCodec codec = new SyncBundleCodec();
        SyncBundleCodec.DecodedBundle decoded =
                codec.decode(
                        new ByteArrayInputStream(
                                codec.encode(
                                        new SyncSnapshot(
                                                java.util.Collections.singletonList(local)),
                                        CREATED_AT)));

        assertThat(decoded.getAttachmentsByHash().keySet()).containsExactly(HASH, otherHash);
        SyncRecord note = decoded.getSnapshot().find(SyncRecord.Type.NOTE, NOTE_ID);
        JsonArray manifest = note.getPayload().getAsJsonArray("attachmentsManifest");
        assertThat(manifest).hasSize(2);
        assertThat(manifest.get(0).getAsJsonObject().get("sha256").getAsString()).isEqualTo(HASH);
        assertThat(manifest.get(1).getAsJsonObject().get("sha256").getAsString())
                .isEqualTo(otherHash);
        assertThat(manifest.get(0).getAsJsonObject().get("id").getAsString())
                .isEqualTo(ATTACHMENT_ID);
        assertThat(manifest.get(1).getAsJsonObject().get("id").getAsString())
                .isEqualTo(ATTACHMENT_ID);
        assertThat(note.getCanonicalPayloadHash()).isEqualTo(local.getCanonicalPayloadHash());
    }

    @Test
    public void encode_republishesANoteThatRepeatsOneAttachmentTheWay2650Did() throws Exception {
        // 2.6.50 receivers hold manifests with one entry repeated — a duplicated block whose file
        // was not replaced — and republish them as conflict alternatives after the upgrade.
        // Refusing the shape at encode failed every publish until the conflict was resolved;
        // collapsing it changed the version's hash. Each repeat travels under its own wire id.
        JsonObject payload = notePayload("Body", "image/png", 42L, "first.png");
        payload.getAsJsonArray("attachmentsManifest")
                .add(
                        payload.getAsJsonArray("attachmentsManifest")
                                .get(0)
                                .getAsJsonObject()
                                .deepCopy());
        payload.getAsJsonArray("attachmentHashes").add(HASH);
        JsonObject names = new JsonObject();
        names.addProperty(ATTACHMENT_ID, "first.png");
        payload.add("attachmentNames", names);
        SyncRecord repeated =
                SyncRecord.live(
                        SyncRecord.Type.NOTE,
                        NOTE_ID,
                        Instant.parse("2026-08-31T12:00:01Z"),
                        payload);
        SyncRecord live =
                SyncRecord.live(
                        SyncRecord.Type.NOTE,
                        NOTE_ID,
                        Instant.parse("2026-08-31T12:00:05Z"),
                        notePayload("Newer", "image/png", 42L, "first.png"));

        SyncBundleCodec codec = new SyncBundleCodec();
        byte[] bundle =
                codec.encode(
                        new SyncSnapshot(java.util.Collections.singletonList(live)),
                        CREATED_AT,
                        java.util.Collections.emptyList(),
                        java.util.Collections.singletonList(repeated),
                        java.util.Collections.emptySet());
        SyncBundleCodec.DecodedBundle decoded = codec.decode(new ByteArrayInputStream(bundle));

        SyncRecord alternative = decoded.getAlternatives().get(0);
        JsonArray manifest = alternative.getPayload().getAsJsonArray("attachmentsManifest");
        assertThat(manifest).hasSize(2);
        assertThat(manifest.get(1).getAsJsonObject().get("id").getAsString())
                .isEqualTo(ATTACHMENT_ID);
        assertThat(alternative.getCanonicalPayloadHash())
                .isEqualTo(repeated.getCanonicalPayloadHash());
        // On the wire exactly as 2.6.50 published it — two references to the one entry and no
        // alias field — so a device still on 2.6.50 reads it without stumbling over a field it
        // does not know.
        JsonObject records =
                com.google.gson.JsonParser.parseString(
                                unzipToStrings(bundle).get(SyncBundleCodec.ENTRY_RECORDS))
                        .getAsJsonObject();
        JsonObject wireAlternative =
                records.getAsJsonArray("alternatives").get(0).getAsJsonObject();
        JsonArray wireIds = wireAlternative.getAsJsonArray("attachmentIds");
        assertThat(wireIds.get(0).getAsString()).isEqualTo(ATTACHMENT_ID);
        assertThat(wireIds.get(1).getAsString()).isEqualTo(ATTACHMENT_ID);
        assertThat(wireAlternative.has(SyncBundleCodec.FIELD_ATTACHMENT_ID_ALIASES)).isFalse();
    }

    @Test
    public void decode_acceptsA2650BundleThatReferencesOneAttachmentTwice() throws Exception {
        // 2.6.50 published attachmentIds [X, X] for a duplicated block with unchanged content, and
        // such bundles sit on Drive in closed-testing accounts. A read decodes every bundle in
        // every root, so refusing this shape failed every sync forever with no way to publish a
        // successor that would let the bundle be pruned.
        SyncBundleCodec codec = new SyncBundleCodec();
        byte[] valid = codec.encode(new SyncSnapshot(Arrays.asList(note("Milk"))), CREATED_AT);
        JsonObject records = readRecords(valid);
        JsonObject wireNote = records.getAsJsonArray("notes").get(0).getAsJsonObject();
        wireNote.getAsJsonArray("attachmentIds").add(ATTACHMENT_ID);

        SyncBundleCodec.DecodedBundle decoded =
                codec.decode(new ByteArrayInputStream(rebuild(valid, records)));

        SyncRecord decodedNote = decoded.getSnapshot().find(SyncRecord.Type.NOTE, NOTE_ID);
        JsonArray manifest = decodedNote.getPayload().getAsJsonArray("attachmentsManifest");
        assertThat(manifest).hasSize(2);
        assertThat(manifest.get(1).getAsJsonObject().get("sha256").getAsString()).isEqualTo(HASH);
        // And it round-trips as the same version, so it never conflicts with itself.
        SyncRecord again =
                codec.decode(
                                new ByteArrayInputStream(
                                        codec.encode(
                                                new SyncSnapshot(
                                                        java.util.Collections.singletonList(
                                                                decodedNote)),
                                                CREATED_AT)))
                        .getSnapshot()
                        .find(SyncRecord.Type.NOTE, NOTE_ID);
        assertThat(again.getCanonicalPayloadHash())
                .isEqualTo(decodedNote.getCanonicalPayloadHash());
    }

    @Test
    public void decode_refusesANoteWhoseRepeatedReferenceIsARekeyedOne() throws Exception {
        // The shape an encoder produced when it collapsed two different blobs under one alias:
        // the same re-keyed id twice. Only one blob is described, so the receiver cannot restore
        // the note as it was; refusing is the only safe answer.
        SyncBundleCodec codec = new SyncBundleCodec();
        byte[] valid = codec.encode(new SyncSnapshot(Arrays.asList(note("Milk"))), CREATED_AT);
        JsonObject records = readRecords(valid);
        JsonObject wireNote = records.getAsJsonArray("notes").get(0).getAsJsonObject();
        String alias = "550e8400-e29b-41d4-a716-446655440077";
        JsonArray ids = new JsonArray();
        ids.add(alias);
        ids.add(alias);
        wireNote.add("attachmentIds", ids);
        JsonObject aliases = new JsonObject();
        aliases.addProperty(alias, ATTACHMENT_ID);
        wireNote.add(SyncBundleCodec.FIELD_ATTACHMENT_ID_ALIASES, aliases);
        JsonObject manifest = readManifest(valid);
        manifest.getAsJsonArray("attachments").get(0).getAsJsonObject().addProperty("id", alias);

        try {
            codec.decode(new ByteArrayInputStream(rebuild(manifest, records)));
            throw new AssertionError("Expected the collapsed shape to be refused");
        } catch (IOException expected) {
            assertThat(expected).hasMessageThat().contains("collapses two attachments");
        }
    }

    private static JsonObject readRecords(byte[] bundle) throws IOException {
        return com.google.gson.JsonParser.parseString(
                        unzipToStrings(bundle).get(SyncBundleCodec.ENTRY_RECORDS))
                .getAsJsonObject();
    }

    private static JsonObject readManifest(byte[] bundle) throws IOException {
        return com.google.gson.JsonParser.parseString(
                        unzipToStrings(bundle).get(SyncBundleCodec.ENTRY_MANIFEST))
                .getAsJsonObject();
    }

    /** Re-zips a bundle around edited records, refreshing the manifest checksum and length. */
    private static byte[] rebuild(byte[] bundle, JsonObject records) throws IOException {
        return rebuild(readManifest(bundle), records);
    }

    private static byte[] rebuild(JsonObject manifest, JsonObject records) throws IOException {
        byte[] recordBytes = records.toString().getBytes(StandardCharsets.UTF_8);
        manifest.addProperty("recordsSha256", SyncBundleValidator.sha256(recordBytes));
        manifest.addProperty("recordsBytes", recordBytes.length);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip =
                new java.util.zip.ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(SyncBundleCodec.ENTRY_MANIFEST));
            zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(SyncBundleCodec.ENTRY_RECORDS));
            zip.write(recordBytes);
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    @Test
    public void decode_trimsADisplayNameSoEveryConsumerSeesTheNameThatWasValidated()
            throws Exception {
        SyncBundleCodec codec = new SyncBundleCodec();
        SyncRecord local =
                SyncRecord.live(
                        SyncRecord.Type.NOTE,
                        NOTE_ID,
                        Instant.parse("2026-08-31T12:00:01Z"),
                        notePayload("Body", "application/pdf", 12L, "report.pdf "));

        SyncRecord decoded =
                codec.decode(
                                new ByteArrayInputStream(
                                        codec.encode(
                                                new SyncSnapshot(
                                                        java.util.Collections.singletonList(local)),
                                                CREATED_AT)))
                        .getSnapshot()
                        .find(SyncRecord.Type.NOTE, NOTE_ID);

        // The validator judged the trimmed name; a consumer building a file name from the
        // untrimmed one would have been refused by the URL parser.
        JsonObject entry =
                decoded.getPayload().getAsJsonArray("attachmentsManifest").get(0).getAsJsonObject();
        assertThat(entry.get("displayName").getAsString()).isEqualTo("report.pdf");
        assertThat(
                        decoded.getPayload()
                                .getAsJsonObject("attachmentNames")
                                .get(ATTACHMENT_ID)
                                .getAsString())
                .isEqualTo("report.pdf");
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
