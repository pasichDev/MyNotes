package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Instant;
import org.junit.Test;

public class SyncRecordTest {

    private static final String NOTE_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final Instant AT = Instant.parse("2026-08-31T12:00:01Z");

    @Test
    public void aNoteWithEmptyAttachmentFieldsHashesLikeOneWithout() {
        // The local build, the encoder and the decoder each enforced "absent when empty" on
        // their own, and every time one drifted the same note hashed differently on the two
        // sides and conflicted with itself on every sync. The record is the one place now.
        JsonObject bare = new JsonObject();
        bare.addProperty("b", "Shopping");
        JsonObject withEmpties = bare.deepCopy();
        withEmpties.add("attachmentsManifest", new JsonArray());
        withEmpties.add("attachmentHashes", new JsonArray());
        withEmpties.add("attachmentNames", new JsonObject());

        SyncRecord plain = SyncRecord.live(SyncRecord.Type.NOTE, NOTE_ID, AT, bare);
        SyncRecord normalized = SyncRecord.live(SyncRecord.Type.NOTE, NOTE_ID, AT, withEmpties);

        assertThat(normalized.getCanonicalPayloadHash()).isEqualTo(plain.getCanonicalPayloadHash());
        assertThat(normalized.getPayload().has("attachmentNames")).isFalse();
    }

    @Test
    public void populatedAttachmentFieldsAreKept() {
        JsonObject payload = new JsonObject();
        JsonArray hashes = new JsonArray();
        hashes.add("d6f1f3d5d8cf9b5a4a2469787998dc45eb59f401b93b1b4cde4998dc409ebdc8");
        payload.add("attachmentHashes", hashes);

        SyncRecord record = SyncRecord.live(SyncRecord.Type.NOTE, NOTE_ID, AT, payload);

        assertThat(record.getPayload().getAsJsonArray("attachmentHashes")).hasSize(1);
    }
}
