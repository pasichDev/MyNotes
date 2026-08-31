package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
        this.payload = Objects.requireNonNull(payload, "payload").deepCopy();
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
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte byteValue : digest) {
                hex.append(String.format("%02x", byteValue & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
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
