package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Encodes and validates sync schema version 1 bundles.
 *
 * <p>The bundle is a ZIP that contains only metadata JSON. Attachments are immutable sibling Drive
 * files referenced from the manifest so they can be deduplicated and uploaded separately.
 */
public final class SyncBundleCodec {
    public static final String ENTRY_MANIFEST = "sync-manifest.json";
    public static final String ENTRY_RECORDS = "records.json";
    public static final String BUNDLE_FORMAT = "mynotes-sync";
    public static final int SCHEMA_VERSION = 1;
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern MIME_TYPE =
            Pattern.compile("^[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+$");
    private static final Gson GSON = new Gson();
    private final SyncBundleValidator validator = new SyncBundleValidator();

    @NonNull
    public byte[] encode(@NonNull SyncSnapshot snapshot, @NonNull Instant createdAt)
            throws IOException {
        JsonObject recordsRoot = new JsonObject();
        recordsRoot.add("notes", liveArray(snapshot, SyncRecord.Type.NOTE));
        recordsRoot.add("tasks", liveArray(snapshot, SyncRecord.Type.TASK));
        recordsRoot.add("tags", liveArray(snapshot, SyncRecord.Type.TAG));
        recordsRoot.add("categories", liveArray(snapshot, SyncRecord.Type.CATEGORY));
        recordsRoot.add("preferences", liveArray(snapshot, SyncRecord.Type.PREFERENCES));
        recordsRoot.add("tombstones", tombstones(snapshot));

        JsonArray attachments = collectAttachments(snapshot);
        byte[] recordBytes = GSON.toJson(recordsRoot).getBytes(StandardCharsets.UTF_8);
        if (recordBytes.length > SyncBundleValidator.MAX_RECORD_BYTES) {
            throw new IOException("Sync records exceed the schema-1 size limit");
        }

        JsonObject manifest = new JsonObject();
        manifest.addProperty("format", BUNDLE_FORMAT);
        manifest.addProperty("schemaVersion", SCHEMA_VERSION);
        manifest.addProperty("bundleId", UUID.randomUUID().toString());
        manifest.addProperty("createdAt", createdAt.toString());
        manifest.addProperty("recordsSha256", sha256(recordBytes));
        manifest.addProperty("recordsBytes", recordBytes.length);
        manifest.add("attachments", attachments);

        byte[] manifestBytes = GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            writeEntry(zip, ENTRY_MANIFEST, manifestBytes);
            writeEntry(zip, ENTRY_RECORDS, recordBytes);
        }
        byte[] bundle = buffer.toByteArray();
        validator.validate(new ByteArrayInputStream(bundle));
        return bundle;
    }

    @NonNull
    public DecodedBundle decode(@NonNull InputStream input) throws IOException {
        SyncBundleValidator.ValidatedBundle validated = validator.validate(input);
        JsonObject records = validated.getRecords();
        Map<String, AttachmentManifestEntry> attachmentsById = validated.getAttachmentsById();

        List<SyncRecord> result = new ArrayList<>();
        LinkedHashSet<String> identities = new LinkedHashSet<>();
        parseLiveRecords(
                records, "notes", SyncRecord.Type.NOTE, attachmentsById, identities, result);
        parseLiveRecords(
                records, "tasks", SyncRecord.Type.TASK, attachmentsById, identities, result);
        parseLiveRecords(records, "tags", SyncRecord.Type.TAG, attachmentsById, identities, result);
        parseLiveRecords(
                records,
                "categories",
                SyncRecord.Type.CATEGORY,
                attachmentsById,
                identities,
                result);
        parseLiveRecords(
                records,
                "preferences",
                SyncRecord.Type.PREFERENCES,
                attachmentsById,
                identities,
                result);
        parseTombstones(records, identities, result);
        return new DecodedBundle(new SyncSnapshot(result), validated.getAttachmentsByHash());
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] bytes)
            throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    @NonNull
    private static JsonArray liveArray(
            @NonNull SyncSnapshot snapshot, @NonNull SyncRecord.Type type) throws IOException {
        JsonArray array = new JsonArray();
        for (SyncRecord record : snapshot.getLiveRecords(type)) {
            JsonObject item = record.getPayload();
            item.addProperty("id", record.getId());
            item.addProperty("updatedAt", record.getUpdatedAt().toString());
            if (type == SyncRecord.Type.NOTE) {
                normalizeNoteAttachmentFields(item);
            }
            array.add(item);
        }
        return array;
    }

    private static void normalizeNoteAttachmentFields(JsonObject note) throws IOException {
        JsonArray manifestEntries = note.getAsJsonArray("attachmentsManifest");
        JsonArray attachmentIds = new JsonArray();
        JsonObject attachmentNames = new JsonObject();
        if (manifestEntries != null) {
            for (JsonElement element : manifestEntries) {
                AttachmentManifestEntry attachment =
                        AttachmentManifestEntry.fromJson(element.getAsJsonObject());
                attachmentIds.add(attachment.id);
                if (attachment.displayName != null && !attachment.displayName.isEmpty()) {
                    attachmentNames.addProperty(attachment.id, attachment.displayName);
                }
            }
        }
        note.remove("attachmentsManifest");
        note.remove("attachmentHashes");
        if (attachmentIds.size() > 0) {
            note.add("attachmentIds", attachmentIds);
        }
        if (attachmentNames.size() > 0) {
            note.add("attachmentNames", attachmentNames);
        }
    }

    @NonNull
    private static JsonArray tombstones(@NonNull SyncSnapshot snapshot) {
        JsonArray array = new JsonArray();
        for (SyncRecord record : snapshot.getTombstones()) {
            JsonObject item = new JsonObject();
            item.addProperty("type", record.getType().getWireValue());
            item.addProperty("id", record.getId());
            item.addProperty("updatedAt", record.getUpdatedAt().toString());
            item.addProperty("deletedAt", record.getDeletedAt().toString());
            array.add(item);
        }
        return array;
    }

    @NonNull
    private static JsonArray collectAttachments(@NonNull SyncSnapshot snapshot) throws IOException {
        JsonArray attachments = new JsonArray();
        Map<String, AttachmentManifestEntry> seenByHash = new LinkedHashMap<>();
        for (SyncRecord record : snapshot.getLiveRecords(SyncRecord.Type.NOTE)) {
            JsonArray manifestEntries = record.getPayload().getAsJsonArray("attachmentsManifest");
            if (manifestEntries == null) continue;
            for (JsonElement element : manifestEntries) {
                AttachmentManifestEntry attachment =
                        AttachmentManifestEntry.fromJson(element.getAsJsonObject());
                AttachmentManifestEntry previous =
                        seenByHash.putIfAbsent(attachment.sha256, attachment);
                if (previous != null && !previous.sameRemoteFile(attachment)) {
                    throw new IOException("Two notes reference conflicting attachment metadata");
                }
            }
        }
        if (seenByHash.size() > SyncBundleValidator.MAX_ATTACHMENT_COUNT) {
            throw new IOException("Sync bundle exceeds the schema-1 attachment limit");
        }
        for (AttachmentManifestEntry attachment : seenByHash.values()) {
            attachments.add(attachment.toJson(false));
        }
        return attachments;
    }

    private static void parseLiveRecords(
            JsonObject records,
            String field,
            SyncRecord.Type type,
            Map<String, AttachmentManifestEntry> attachmentsById,
            LinkedHashSet<String> identities,
            Collection<SyncRecord> output)
            throws IOException {
        JsonArray array = requireArray(records, field);
        for (JsonElement element : array) {
            JsonObject item = element.getAsJsonObject();
            String id = SyncBundleValidator.requireString(item, "id");
            SyncBundleValidator.validateUuid(id);
            Instant updatedAt = Instant.parse(SyncBundleValidator.requireString(item, "updatedAt"));
            JsonObject payload = item.deepCopy();
            payload.remove("id");
            payload.remove("updatedAt");
            // Bundles written before the device-local fields were stripped still carry them.
            SyncMetadata.stripDeviceLocalFields(type.getWireValue(), payload);
            if (type == SyncRecord.Type.NOTE) {
                hydrateNoteAttachments(payload, attachmentsById);
            }
            String identity = type.getWireValue() + ":" + id;
            if (!identities.add(identity)) {
                throw new IOException("Sync bundle contains duplicate record IDs");
            }
            output.add(SyncRecord.live(type, id, updatedAt, payload));
        }
    }

    private static void hydrateNoteAttachments(
            JsonObject payload, Map<String, AttachmentManifestEntry> attachmentsById)
            throws IOException {
        JsonArray attachmentIds = payload.getAsJsonArray("attachmentIds");
        JsonObject attachmentNames = payload.getAsJsonObject("attachmentNames");
        if (attachmentIds == null) return;
        JsonArray attachmentHashes = new JsonArray();
        JsonArray manifest = new JsonArray();
        // The wire keys names by attachment UUID; the local store looks them up by content hash.
        JsonObject namesByHash = new JsonObject();
        for (JsonElement element : attachmentIds) {
            String attachmentId = element.getAsString();
            AttachmentManifestEntry attachment = attachmentsById.get(attachmentId);
            if (attachment == null) {
                throw new IOException("Note references an unknown attachment manifest entry");
            }
            JsonObject value = attachment.toJson(true);
            if (attachmentNames != null && attachmentNames.has(attachmentId)) {
                value.addProperty("displayName", attachmentNames.get(attachmentId).getAsString());
            }
            manifest.add(value);
            attachmentHashes.add(attachment.sha256);
            if (value.has("displayName") && !value.get("displayName").isJsonNull()) {
                namesByHash.addProperty(attachment.sha256, value.get("displayName").getAsString());
            }
        }
        payload.add("attachmentsManifest", manifest);
        payload.add("attachmentHashes", attachmentHashes);
        // Rekeyed by hash; leaving the UUID-keyed map made every restored file land on disk
        // named after its bare SHA-256, with no extension.
        payload.add("attachmentNames", namesByHash);
    }

    private static void parseTombstones(
            JsonObject records, LinkedHashSet<String> identities, Collection<SyncRecord> output)
            throws IOException {
        JsonArray array = requireArray(records, "tombstones");
        for (JsonElement element : array) {
            JsonObject item = element.getAsJsonObject();
            SyncRecord.Type type =
                    SyncRecord.Type.fromWireValue(SyncBundleValidator.requireString(item, "type"));
            String id = SyncBundleValidator.requireString(item, "id");
            SyncBundleValidator.validateUuid(id);
            Instant updatedAt = Instant.parse(SyncBundleValidator.requireString(item, "updatedAt"));
            Instant deletedAt = Instant.parse(SyncBundleValidator.requireString(item, "deletedAt"));
            String identity = type.getWireValue() + ":" + id;
            if (!identities.add(identity)) {
                throw new IOException("Sync bundle contains duplicate live and tombstone records");
            }
            output.add(SyncRecord.tombstone(type, id, updatedAt, deletedAt));
        }
    }

    @NonNull
    private static JsonArray requireArray(@NonNull JsonObject object, @NonNull String field)
            throws IOException {
        JsonArray value = object.getAsJsonArray(field);
        if (value == null) {
            throw new IOException("Sync JSON field " + field + " is missing or invalid");
        }
        return value;
    }

    @NonNull
    private static String sha256(byte[] bytes) throws IOException {
        return SyncBundleValidator.sha256(bytes);
    }

    public static final class DecodedBundle {
        private final SyncSnapshot snapshot;
        private final Map<String, AttachmentManifestEntry> attachmentsByHash;

        DecodedBundle(
                @NonNull SyncSnapshot snapshot,
                @NonNull Map<String, AttachmentManifestEntry> attachmentsByHash) {
            this.snapshot = snapshot;
            this.attachmentsByHash = attachmentsByHash;
        }

        @NonNull
        public SyncSnapshot getSnapshot() {
            return snapshot;
        }

        @NonNull
        public Map<String, AttachmentManifestEntry> getAttachmentsByHash() {
            return attachmentsByHash;
        }
    }

    public static final class AttachmentManifestEntry {
        @NonNull public final String id;
        @NonNull public final String sha256;
        @NonNull public final String mimeType;
        public final long size;
        @NonNull public final String path;
        @NonNull public final String remoteName;
        public final String displayName;

        AttachmentManifestEntry(
                @NonNull String id,
                @NonNull String sha256,
                @NonNull String mimeType,
                long size,
                @NonNull String path,
                String displayName) {
            this.id = id;
            this.sha256 = sha256;
            this.mimeType = mimeType;
            this.size = size;
            this.path = path;
            this.remoteName = path.substring("attachments/".length());
            this.displayName = displayName;
        }

        @NonNull
        public static AttachmentManifestEntry fromJson(@NonNull JsonObject value)
                throws IOException {
            String id = SyncBundleValidator.requireString(value, "id");
            SyncBundleValidator.validateUuid(id);
            String sha256 = SyncBundleValidator.requireString(value, "sha256");
            if (!SHA_256.matcher(sha256).matches()) {
                throw new IOException("Sync bundle contains an invalid attachment hash");
            }
            String mimeType =
                    SyncBundleValidator.requireString(value, "mimeType").toLowerCase(Locale.US);
            if (!MIME_TYPE.matcher(mimeType).matches()) {
                throw new IOException("Sync bundle contains an invalid attachment MIME type");
            }
            long size = SyncBundleValidator.requireLong(value, "size");
            if (size < 0L) {
                throw new IOException("Sync bundle contains a negative attachment size");
            }
            String path = SyncBundleValidator.requireString(value, "path");
            if (!path.equals("attachments/" + sha256)) {
                throw new IOException("Sync bundle contains an invalid attachment path");
            }
            String displayName =
                    value.has("displayName")
                                    && !value.get("displayName").isJsonNull()
                                    && value.get("displayName").isJsonPrimitive()
                            ? value.get("displayName").getAsString()
                            : null;
            if (value.has("displayName")
                    && !value.get("displayName").isJsonNull()
                    && !value.get("displayName").isJsonPrimitive()) {
                throw new IOException("Sync attachment displayName is invalid");
            }
            if (displayName != null) {
                SyncBundleValidator.validateDisplayName(displayName);
            }
            return new AttachmentManifestEntry(id, sha256, mimeType, size, path, displayName);
        }

        @NonNull
        JsonObject toJson(boolean includeDisplayName) {
            JsonObject value = new JsonObject();
            value.addProperty("id", id);
            value.addProperty("sha256", sha256);
            value.addProperty("mimeType", mimeType);
            value.addProperty("size", size);
            value.addProperty("path", path);
            if (includeDisplayName && displayName != null && !displayName.isEmpty()) {
                value.addProperty("displayName", displayName);
            }
            return value;
        }

        boolean sameRemoteFile(@NonNull AttachmentManifestEntry other) {
            return sha256.equals(other.sha256)
                    && path.equals(other.path)
                    && mimeType.equals(other.mimeType)
                    && size == other.size;
        }
    }
}
