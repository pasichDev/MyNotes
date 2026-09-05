package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pasich.mynotes.extendedEditor.attach.EditorAttachmentBlocks;
import java.time.Instant;
import org.junit.Test;

/**
 * A note published by 2.6.50 has to come out of the decoder in the shape the upgraded store builds
 * for the same, unchanged note. Otherwise the two hash differently at the same timestamp and the
 * merge reports a conflict against the note itself — on every sync, when the old version wins.
 */
public class LegacyNotePayloadTest {

    private static final String NOTE_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String HASH =
            "d6f1f3d5d8cf9b5a4a2469787998dc45eb59f401b93b1b4cde4998dc409ebdc8";
    private static final String LOCAL_URL = "editorjs://attachments/note_7/1700000000000_1.png";

    @Test
    public void upgradesA2650PayloadToTheCurrentDerivationAndWireForm() {
        JsonObject legacy = legacyPayload(LOCAL_URL, "photo.png");
        String expectedId = AttachmentLogicalIds.derive(NOTE_ID, 0, LOCAL_URL, "photo.png", HASH);

        assertThat(LegacyNotePayload.upgrade(NOTE_ID, legacy)).isTrue();

        JsonObject entry = legacy.getAsJsonArray("attachmentsManifest").get(0).getAsJsonObject();
        assertThat(entry.get("id").getAsString()).isEqualTo(expectedId);
        assertThat(legacy.getAsJsonObject("attachmentNames").get(expectedId).getAsString())
                .isEqualTo("photo.png");
        assertThat(EditorAttachmentBlocks.fileUrls(legacy.get("f").getAsString()))
                .containsExactly(AttachmentWireUrl.forLogicalId(expectedId));
        assertThat(legacy.getAsJsonArray("attachmentHashes").get(0).getAsString()).isEqualTo(HASH);
    }

    @Test
    public void theUpgradedPayloadHashesLikeTheCurrentBuildOfTheSameNote() {
        // What RoomSyncStore builds today for the same unchanged note: the same derivation over
        // the same inputs, the block already in wire form, names keyed by the new id.
        String id = AttachmentLogicalIds.derive(NOTE_ID, 0, LOCAL_URL, "photo.png", HASH);
        JsonObject current = legacyPayload(LOCAL_URL, "photo.png");
        current.getAsJsonArray("attachmentsManifest")
                .get(0)
                .getAsJsonObject()
                .addProperty("id", id);
        JsonObject names = new JsonObject();
        names.addProperty(id, "photo.png");
        current.add("attachmentNames", names);
        current.addProperty(
                "f",
                EditorAttachmentBlocks.rewriteUrls(
                        current.get("f").getAsString(), url -> AttachmentWireUrl.forLogicalId(id)));
        JsonObject legacy = legacyPayload(LOCAL_URL, "photo.png");

        LegacyNotePayload.upgrade(NOTE_ID, legacy);

        Instant at = Instant.parse("2026-08-31T12:00:01Z");
        assertThat(
                        SyncRecord.live(SyncRecord.Type.NOTE, NOTE_ID, at, legacy)
                                .getCanonicalPayloadHash())
                .isEqualTo(
                        SyncRecord.live(SyncRecord.Type.NOTE, NOTE_ID, at, current)
                                .getCanonicalPayloadHash());
    }

    @Test
    public void keepsACanonicalIdButStillPutsTheBlocksIntoWireForm() {
        // An id a receiving 2.6.50 device restored, repeated because the block was duplicated. The
        // upgraded store keeps such ids as they are and maps the blocks by position; the decoded
        // note has to come out the same way, or the two hash differently at the same timestamp
        // and the note conflicts with itself on every sync.
        String restoredId = "7d444840-9dc0-11d1-b245-5ffdce74fad2";
        JsonObject payload = legacyPayload(LOCAL_URL, "photo.png");
        JsonObject entry = payload.getAsJsonArray("attachmentsManifest").get(0).getAsJsonObject();
        entry.addProperty("id", restoredId);
        payload.getAsJsonArray("attachmentsManifest").add(entry.deepCopy());
        payload.getAsJsonArray("attachmentHashes").add(HASH);
        payload.addProperty(
                "f",
                "[{\"type\":\"attaches\",\"data\":{\"file\":{\"url\":\""
                        + LOCAL_URL
                        + "\"}}},{\"type\":\"attaches\",\"data\":{\"file\":{\"url\":\""
                        + LOCAL_URL
                        + "\"}}}]");

        assertThat(LegacyNotePayload.upgrade(NOTE_ID, payload)).isTrue();

        JsonArray manifest = payload.getAsJsonArray("attachmentsManifest");
        assertThat(manifest.get(0).getAsJsonObject().get("id").getAsString()).isEqualTo(restoredId);
        assertThat(manifest.get(1).getAsJsonObject().get("id").getAsString()).isEqualTo(restoredId);
        assertThat(EditorAttachmentBlocks.fileUrls(payload.get("f").getAsString()))
                .containsExactly(
                        AttachmentWireUrl.forLogicalId(restoredId),
                        AttachmentWireUrl.forLogicalId(restoredId));
        assertThat(payload.getAsJsonObject("attachmentNames").entrySet()).hasSize(1);
    }

    @Test
    public void leavesAPayloadWhoseBlocksDoNotLineUpWithItsManifestAlone() {
        JsonObject payload = legacyPayload(LOCAL_URL, "photo.png");
        payload.addProperty(
                "f",
                "[{\"type\":\"attaches\",\"data\":{\"file\":{\"url\":\""
                        + LOCAL_URL
                        + "\"}}},{\"type\":\"image\",\"data\":{\"file\":{\"url\":\"editorjs://attachments/note_7/other.png\"}}}]");
        String before = payload.toString();

        assertThat(LegacyNotePayload.upgrade(NOTE_ID, payload)).isFalse();
        assertThat(payload.toString()).isEqualTo(before);
    }

    @Test
    public void leavesAPayloadAlreadyInWireFormAlone() {
        JsonObject payload = legacyPayload(AttachmentWireUrl.forLogicalId(NOTE_ID), "photo.png");

        assertThat(LegacyNotePayload.upgrade(NOTE_ID, payload)).isFalse();
    }

    /** Exactly what 2.6.50's store built: local block URL, hash-less id, names keyed by it. */
    private static JsonObject legacyPayload(String blockUrl, String displayName) {
        String legacyId = AttachmentLogicalIds.deriveLegacy(NOTE_ID, 0, blockUrl, displayName);
        JsonObject payload = new JsonObject();
        payload.addProperty("b", "Shopping");
        payload.addProperty("c", "Milk");
        payload.addProperty(
                "f",
                "[{\"id\":\"blk\",\"type\":\"attaches\",\"data\":{\"file\":{\"url\":\""
                        + blockUrl
                        + "\",\"name\":\""
                        + displayName
                        + "\"}}}]");
        JsonObject entry = new JsonObject();
        entry.addProperty("id", legacyId);
        entry.addProperty("sha256", HASH);
        entry.addProperty("mimeType", "image/png");
        entry.addProperty("size", 42L);
        entry.addProperty("path", "attachments/" + HASH);
        entry.addProperty("displayName", displayName);
        JsonArray manifest = new JsonArray();
        manifest.add(entry);
        payload.add("attachmentsManifest", manifest);
        JsonArray hashes = new JsonArray();
        hashes.add(HASH);
        payload.add("attachmentHashes", hashes);
        JsonObject names = new JsonObject();
        names.addProperty(legacyId, displayName);
        payload.add("attachmentNames", names);
        return payload;
    }
}
