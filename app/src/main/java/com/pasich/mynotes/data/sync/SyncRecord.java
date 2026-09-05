package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Immutable version of one synchronizable object.
 *
 * <p>The {@code payload} contains only domain fields. Identity, type, timestamps and tombstone
 * state are deliberately kept outside it so the merge algorithm cannot accidentally match a Room
 * primary key or other local-only field.
 */
public final class SyncRecord {

    /** Types that have their own stable-ID namespace in a sync snapshot. */
    public enum Type {
        NOTE("note"),
        CATEGORY("category"),
        TASK("task"),
        TAG("tag"),
        PREFERENCES("preferences");

        private final String wireValue;

        Type(String wireValue) {
            this.wireValue = wireValue;
        }

        @NonNull
        public String getWireValue() {
            return wireValue;
        }

        @NonNull
        public static Type fromWireValue(@NonNull String wireValue) {
            for (Type type : values()) {
                if (type.wireValue.equals(wireValue)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unsupported sync record type: " + wireValue);
        }
    }

    private static final Gson GSON = new Gson();

    private final Type type;
    private final String id;
    private final Instant updatedAt;
    @Nullable private final Instant deletedAt;
    private final JsonObject payload;

    private SyncRecord(
            @NonNull Type type,
            @NonNull String id,
            @NonNull Instant updatedAt,
            @Nullable Instant deletedAt,
            @NonNull JsonObject payload) {
        this.type = Objects.requireNonNull(type, "type");
        validateCanonicalUuid(id);
        this.id = id;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (deletedAt != null && deletedAt.isBefore(updatedAt)) {
            throw new IllegalArgumentException("deletedAt must not be before updatedAt");
        }
        this.deletedAt = deletedAt;
        this.payload = normalize(type, Objects.requireNonNull(payload, "payload").deepCopy());
    }

    /**
     * Puts a payload into the one shape its version hash is taken from.
     *
     * <p>A note's attachment fields are present exactly when it has attachments. The local build,
     * the bundle encoder and the bundle decoder each used to enforce that separately, and every
     * time one of them drifted — an empty {@code attachmentNames} left on the wire, three empty
     * arrays emitted for a note with none — a decoded record hashed differently from the identical
     * local one and every affected note conflicted with itself on every sync. Every record passes
     * through here, whichever side built it, so the three sites can no longer disagree.
     */
    @NonNull
    private static JsonObject normalize(@NonNull Type type, @NonNull JsonObject payload) {
        if (type != Type.NOTE) {
            return payload;
        }
        for (String field : new String[] {"attachmentsManifest", "attachmentHashes"}) {
            JsonElement value = payload.get(field);
            if (value != null
                    && (value.isJsonNull()
                            || !value.isJsonArray()
                            || value.getAsJsonArray().size() == 0)) {
                payload.remove(field);
            }
        }
        JsonElement names = payload.get("attachmentNames");
        if (names != null
                && (names.isJsonNull()
                        || !names.isJsonObject()
                        || names.getAsJsonObject().size() == 0)) {
            payload.remove("attachmentNames");
        }
        return payload;
    }

    @NonNull
    public static SyncRecord live(
            @NonNull Type type,
            @NonNull String id,
            @NonNull Instant updatedAt,
            @NonNull JsonObject payload) {
        return new SyncRecord(type, id, updatedAt, null, payload);
    }

    @NonNull
    public static SyncRecord tombstone(
            @NonNull Type type,
            @NonNull String id,
            @NonNull Instant updatedAt,
            @NonNull Instant deletedAt) {
        return new SyncRecord(type, id, updatedAt, deletedAt, new JsonObject());
    }

    @NonNull
    public Type getType() {
        return type;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Nullable
    public Instant getDeletedAt() {
        return deletedAt;
    }

    public boolean isTombstone() {
        return deletedAt != null;
    }

    /** Returns a defensive copy so callers cannot alter the record after it has been merged. */
    @NonNull
    public JsonObject getPayload() {
        return payload.deepCopy();
    }

    /**
     * SHA-256 for a canonical JSON representation of the complete version. It is used only as the
     * deterministic equal-timestamp tiebreaker; it is not an attachment checksum.
     */
    @NonNull
    public String getCanonicalPayloadHash() {
        return sha256(canonicalSerializedPayload());
    }

    @NonNull
    String canonicalSerializedPayload() {
        JsonObject record = new JsonObject();
        record.addProperty("type", type.getWireValue());
        record.addProperty("id", id);
        record.addProperty("updatedAt", updatedAt.toString());
        if (deletedAt == null) {
            record.add("deletedAt", JsonNull.INSTANCE);
        } else {
            record.addProperty("deletedAt", deletedAt.toString());
        }
        record.add("payload", payload);
        return canonicalJson(record);
    }

    private static void validateCanonicalUuid(String id) {
        Objects.requireNonNull(id, "id");
        try {
            String canonical = UUID.fromString(id).toString();
            if (!canonical.equals(id)) {
                throw new IllegalArgumentException(
                        "Sync record ID must be a lowercase canonical UUID");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Sync record ID must be a lowercase canonical UUID", exception);
        }
    }

    @NonNull
    private static String sha256(String value) {
        return Sha256.of(value);
    }

    @NonNull
    private static String canonicalJson(@NonNull JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "null";
        }
        if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isString()) {
                return GSON.toJson(primitive.getAsString());
            }
            return primitive.toString();
        }
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            List<String> entries = new ArrayList<>(array.size());
            for (JsonElement entry : array) {
                entries.add(canonicalJson(entry));
            }
            return "[" + String.join(",", entries) + "]";
        }

        Map<String, JsonElement> sorted = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
            sorted.put(entry.getKey(), entry.getValue());
        }
        List<String> entries = new ArrayList<>(sorted.size());
        for (Map.Entry<String, JsonElement> entry : sorted.entrySet()) {
            entries.add(GSON.toJson(entry.getKey()) + ":" + canonicalJson(entry.getValue()));
        }
        return "{" + String.join(",", entries) + "}";
    }
}
