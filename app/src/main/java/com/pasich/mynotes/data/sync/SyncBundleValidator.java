package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Validates schema-1 sync bundles before any remote data is exposed to the app. */
public final class SyncBundleValidator {
    static final long MAX_COMPRESSED_BUNDLE_BYTES = 32L * 1024L * 1024L;
    static final long MAX_RECORD_BYTES = 25L * 1024L * 1024L;
    static final long MAX_RECORD_PAYLOAD_BYTES = 4L * 1024L * 1024L;
    static final long MAX_RECORD_COUNT = 10_000L;
    static final long MAX_ATTACHMENT_COUNT = 10_000L;
    static final long MAX_ATTACHMENTS_PER_NOTE = 1_000L;
    static final long MAX_ATTACHMENT_BYTES = 100L * 1024L * 1024L;
    static final long MAX_TOTAL_ATTACHMENT_BYTES = 500L * 1024L * 1024L;
    static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 1024L * 1024L * 1024L;
    static final long MAX_COMPRESSION_RATIO = 100L;
    private static final long MAX_MANIFEST_BYTES = 2L * 1024L * 1024L;
    private static final int MAX_METADATA_STRING_CHARS = 1_048_576;
    private static final int MAX_JSON_DEPTH = 64;
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    @NonNull
    public ValidatedBundle validate(@NonNull InputStream input) throws IOException {
        BundleEntries entries = readEntries(input);
        JsonObject manifest = parseObject(entries.manifestBytes, "Invalid sync manifest JSON");
        JsonObject records = parseObject(entries.recordBytes, "Invalid sync records JSON");

        validateManifest(manifest, entries.recordBytes);

        Map<String, SyncBundleCodec.AttachmentManifestEntry> attachmentsById =
                new LinkedHashMap<>();
        Map<String, SyncBundleCodec.AttachmentManifestEntry> attachmentsByHash =
                new LinkedHashMap<>();
        long totalAttachmentBytes = 0L;
        JsonArray attachments = manifest.getAsJsonArray("attachments");
        for (JsonElement element : attachments) {
            SyncBundleCodec.AttachmentManifestEntry attachment =
                    SyncBundleCodec.AttachmentManifestEntry.fromJson(element.getAsJsonObject());
            if (attachmentsById.put(attachment.id, attachment) != null) {
                throw new IOException("Sync bundle contains duplicate attachment IDs");
            }
            SyncBundleCodec.AttachmentManifestEntry previous =
                    attachmentsByHash.putIfAbsent(attachment.sha256, attachment);
            if (previous != null && !previous.sameRemoteFile(attachment)) {
                throw new IOException("Sync bundle contains conflicting attachment blob metadata");
            }
            if (attachment.size > MAX_ATTACHMENT_BYTES) {
                throw new IOException("Sync bundle contains an oversized attachment");
            }
            totalAttachmentBytes += attachment.size;
            if (totalAttachmentBytes > MAX_TOTAL_ATTACHMENT_BYTES) {
                throw new IOException("Sync bundle exceeds the total attachment size limit");
            }
        }

        long recordCount = 0L;
        Set<String> recordIdentities = new LinkedHashSet<>();
        Set<String> referencedAttachmentIds = new LinkedHashSet<>();
        recordCount +=
                validateLiveRecords(
                        records,
                        "notes",
                        SyncRecord.Type.NOTE,
                        recordIdentities,
                        attachmentsById,
                        referencedAttachmentIds);
        recordCount +=
                validateLiveRecords(
                        records,
                        "tasks",
                        SyncRecord.Type.TASK,
                        recordIdentities,
                        attachmentsById,
                        referencedAttachmentIds);
        recordCount +=
                validateLiveRecords(
                        records,
                        "tags",
                        SyncRecord.Type.TAG,
                        recordIdentities,
                        attachmentsById,
                        referencedAttachmentIds);
        recordCount +=
                validateLiveRecords(
                        records,
                        "categories",
                        SyncRecord.Type.CATEGORY,
                        recordIdentities,
                        attachmentsById,
                        referencedAttachmentIds);
        recordCount +=
                validateLiveRecords(
                        records,
                        "preferences",
                        SyncRecord.Type.PREFERENCES,
                        recordIdentities,
                        attachmentsById,
                        referencedAttachmentIds);
        recordCount += validateTombstones(records, recordIdentities);
        if (recordCount > MAX_RECORD_COUNT) {
            throw new IOException("Sync bundle exceeds the schema-1 record limit");
        }

        if (!attachmentsById.isEmpty()
                && !attachmentsById.keySet().equals(referencedAttachmentIds)) {
            throw new IOException("Sync bundle contains unreferenced attachment metadata");
        }

        return new ValidatedBundle(
                entries.manifestBytes,
                entries.recordBytes,
                manifest,
                records,
                attachmentsById,
                attachmentsByHash);
    }

    private static long validateLiveRecords(
            @NonNull JsonObject records,
            @NonNull String field,
            @NonNull SyncRecord.Type type,
            @NonNull Set<String> identities,
            @NonNull Map<String, SyncBundleCodec.AttachmentManifestEntry> attachmentsById,
            @NonNull Set<String> referencedAttachmentIds)
            throws IOException {
        JsonArray array = requireArray(records, field);
        for (JsonElement element : array) {
            JsonObject item = element.getAsJsonObject();
            String id = requireString(item, "id");
            validateUuid(id);
            parseInstant(requireString(item, "updatedAt"), "updatedAt");
            validatePayloadLimits(item);
            if (type == SyncRecord.Type.NOTE) {
                validateAttachmentReferences(item, attachmentsById, referencedAttachmentIds);
            }
            if (!identities.add(type.getWireValue() + ":" + id)) {
                throw new IOException("Sync bundle contains duplicate record IDs");
            }
        }
        return array.size();
    }

    private static void validateAttachmentReferences(
            @NonNull JsonObject note,
            @NonNull Map<String, SyncBundleCodec.AttachmentManifestEntry> attachmentsById,
            @NonNull Set<String> referencedAttachmentIds)
            throws IOException {
        JsonArray attachmentIds = note.getAsJsonArray("attachmentIds");
        if (attachmentIds == null) {
            return;
        }
        if (attachmentIds.size() > MAX_ATTACHMENTS_PER_NOTE) {
            throw new IOException("Sync note exceeds the attachment limit");
        }
        JsonObject attachmentNames = note.getAsJsonObject("attachmentNames");
        for (JsonElement element : attachmentIds) {
            if (element == null || !element.isJsonPrimitive()) {
                throw new IOException("Sync note attachmentIds entry is invalid");
            }
            String attachmentId = element.getAsString();
            SyncBundleCodec.AttachmentManifestEntry attachment = attachmentsById.get(attachmentId);
            if (attachment == null) {
                throw new IOException("Note references an unknown attachment manifest entry");
            }
            if (attachmentNames != null
                    && attachmentNames.has(attachmentId)
                    && !attachmentNames.get(attachmentId).isJsonPrimitive()) {
                throw new IOException("Sync note attachmentNames entry is invalid");
            }
            if (attachmentNames != null && attachmentNames.has(attachmentId)) {
                validateDisplayName(attachmentNames.get(attachmentId).getAsString());
            }
            referencedAttachmentIds.add(attachmentId);
        }
    }

    private static long validateTombstones(
            @NonNull JsonObject records, @NonNull Set<String> identities) throws IOException {
        JsonArray array = requireArray(records, "tombstones");
        for (JsonElement element : array) {
            JsonObject item = element.getAsJsonObject();
            SyncRecord.Type type = SyncRecord.Type.fromWireValue(requireString(item, "type"));
            String id = requireString(item, "id");
            validateUuid(id);
            Instant updatedAt = parseInstant(requireString(item, "updatedAt"), "updatedAt");
            Instant deletedAt = parseInstant(requireString(item, "deletedAt"), "deletedAt");
            if (deletedAt.isBefore(updatedAt)) {
                throw new IOException("Sync tombstone deletedAt must not be before updatedAt");
            }
            if (!identities.add(type.getWireValue() + ":" + id)) {
                throw new IOException("Sync bundle contains duplicate live and tombstone records");
            }
        }
        return array.size();
    }

    private static void validateManifest(@NonNull JsonObject manifest, @NonNull byte[] recordBytes)
            throws IOException {
        requireString(manifest, "format", SyncBundleCodec.BUNDLE_FORMAT);
        if (requireLong(manifest, "schemaVersion") != SyncBundleCodec.SCHEMA_VERSION) {
            throw new IOException("Unsupported sync bundle schema version");
        }
        validateUuid(requireString(manifest, "bundleId"));
        parseInstant(requireString(manifest, "createdAt"), "createdAt");
        String recordsSha = requireString(manifest, "recordsSha256");
        if (!SHA_256.matcher(recordsSha).matches()) {
            throw new IOException("Sync manifest contains an invalid records checksum");
        }
        long recordsBytes = requireLong(manifest, "recordsBytes");
        if (recordsBytes != recordBytes.length) {
            throw new IOException("Sync manifest records size does not match records.json");
        }
        if (recordBytes.length > MAX_RECORD_BYTES) {
            throw new IOException("Sync records exceed the schema-1 size limit");
        }
        if (!recordsSha.equals(sha256(recordBytes))) {
            throw new IOException("Sync manifest records checksum does not match records.json");
        }
        JsonArray attachments = requireArray(manifest, "attachments");
        if (attachments.size() > MAX_ATTACHMENT_COUNT) {
            throw new IOException("Sync manifest exceeds the attachment limit");
        }
    }

    @NonNull
    private static Instant parseInstant(@NonNull String value, @NonNull String field)
            throws IOException {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException error) {
            throw new IOException("Sync bundle contains an invalid " + field + " timestamp", error);
        }
    }

    @NonNull
    private static BundleEntries readEntries(@NonNull InputStream input) throws IOException {
        byte[] manifest = null;
        byte[] records = null;
        long totalUncompressedBytes = 0L;
        Set<String> names = new LinkedHashSet<>();
        try (ZipInputStream zip =
                new ZipInputStream(
                        new BoundedInputStream(input, MAX_COMPRESSED_BUNDLE_BYTES),
                        StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                validateEntryName(name);
                if (!names.add(name)) {
                    throw new IOException("Sync bundle contains duplicate ZIP entries");
                }

                long maxBytes =
                        SyncBundleCodec.ENTRY_RECORDS.equals(name)
                                ? MAX_RECORD_BYTES
                                : MAX_MANIFEST_BYTES;
                byte[] bytes = readBounded(zip, maxBytes);
                totalUncompressedBytes += bytes.length;
                if (totalUncompressedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                    throw new IOException("Sync bundle exceeds the total uncompressed size limit");
                }
                long compressedSize = entry.getCompressedSize();
                if (compressedSize > 0L && bytes.length > compressedSize * MAX_COMPRESSION_RATIO) {
                    throw new IOException("Sync bundle entry exceeded the compression ratio limit");
                }

                if (SyncBundleCodec.ENTRY_MANIFEST.equals(name)) {
                    manifest = bytes;
                } else if (SyncBundleCodec.ENTRY_RECORDS.equals(name)) {
                    records = bytes;
                } else {
                    throw new IOException("Unexpected ZIP entry in sync bundle");
                }
                zip.closeEntry();
            }
        }
        if (manifest == null || records == null) {
            throw new IOException("Sync bundle is missing required entries");
        }
        return new BundleEntries(manifest, records);
    }

    private static void validateEntryName(String name) throws IOException {
        if (name == null
                || name.isEmpty()
                || name.startsWith("/")
                || name.contains("..")
                || name.contains("\\")) {
            throw new IOException("Sync bundle contains an invalid ZIP path");
        }
    }

    @NonNull
    private static byte[] readBounded(@NonNull InputStream input, long maxBytes)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
            if (output.size() > maxBytes) {
                throw new IOException("Sync bundle entry exceeds the schema-1 size limit");
            }
        }
        return output.toByteArray();
    }

    @NonNull
    private static JsonObject parseObject(byte[] bytes, @NonNull String errorMessage)
            throws IOException {
        try {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (RuntimeException error) {
            throw new IOException(errorMessage, error);
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
    static String requireString(@NonNull JsonObject object, @NonNull String field)
            throws IOException {
        JsonElement value = object.get(field);
        if (value == null
                || value.isJsonNull()
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IOException("Sync JSON field " + field + " is missing or invalid");
        }
        String result = value.getAsString();
        if (result.length() > MAX_METADATA_STRING_CHARS) {
            throw new IOException("Sync JSON field " + field + " exceeds the string limit");
        }
        return result;
    }

    private static void requireString(
            @NonNull JsonObject object, @NonNull String field, @NonNull String expected)
            throws IOException {
        if (!expected.equals(requireString(object, field))) {
            throw new IOException("Sync JSON field " + field + " has an unexpected value");
        }
    }

    static long requireLong(@NonNull JsonObject object, @NonNull String field) throws IOException {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            throw new IOException("Sync JSON field " + field + " is missing or invalid");
        }
        try {
            return value.getAsLong();
        } catch (RuntimeException error) {
            throw new IOException("Sync JSON field " + field + " is not a valid integer", error);
        }
    }

    private static void validatePayloadLimits(@NonNull JsonObject record) throws IOException {
        long serializedBytes = record.toString().getBytes(StandardCharsets.UTF_8).length;
        if (serializedBytes > MAX_RECORD_PAYLOAD_BYTES) {
            throw new IOException("Sync record exceeds the payload size limit");
        }
        validateJsonValue(record, 0);
    }

    private static void validateJsonValue(@NonNull JsonElement value, int depth)
            throws IOException {
        if (depth > MAX_JSON_DEPTH) {
            throw new IOException("Sync JSON exceeds the nesting limit");
        }
        if (value.isJsonPrimitive()) {
            if (value.getAsJsonPrimitive().isString()
                    && value.getAsString().length() > MAX_METADATA_STRING_CHARS) {
                throw new IOException("Sync JSON string exceeds the size limit");
            }
            return;
        }
        if (value.isJsonArray()) {
            for (JsonElement element : value.getAsJsonArray()) {
                validateJsonValue(element, depth + 1);
            }
            return;
        }
        if (value.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                if (entry.getKey().length() > MAX_METADATA_STRING_CHARS) {
                    throw new IOException("Sync JSON field name exceeds the size limit");
                }
                validateJsonValue(entry.getValue(), depth + 1);
            }
        }
    }

    static void validateDisplayName(@NonNull String name) throws IOException {
        String value = name.trim();
        if (value.isEmpty()
                || value.equals(".")
                || value.equals("..")
                || value.length() > 255
                || value.contains("..")
                || value.contains("/")
                || value.contains("\\")
                || new java.io.File(value).getName().equals(value) == false) {
            throw new IOException("Sync attachment name is not a safe file name");
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                throw new IOException("Sync attachment name contains a control character");
            }
        }
    }

    static void validateUuid(@NonNull String value) throws IOException {
        try {
            if (!UUID.fromString(value).toString().equals(value)) {
                throw new IOException("Sync bundle contains a non-canonical UUID");
            }
        } catch (IllegalArgumentException error) {
            throw new IOException("Sync bundle contains an invalid UUID", error);
        }
    }

    @NonNull
    static String sha256(@NonNull byte[] bytes) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte byteValue : digest) {
                value.append(String.format(Locale.US, "%02x", byteValue & 0xff));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("SHA-256 is unavailable", error);
        }
    }

    /** Caps compressed input before ZIP parsing to make bundle-size limits independent of Drive. */
    private static final class BoundedInputStream extends FilterInputStream {
        private final long maxBytes;
        private long bytesRead;

        private BoundedInputStream(@NonNull InputStream input, long maxBytes) {
            super(input);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value != -1) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                count(read);
            }
            return read;
        }

        private void count(long read) throws IOException {
            bytesRead += read;
            if (bytesRead > maxBytes) {
                throw new IOException("Sync bundle exceeds the compressed size limit");
            }
        }
    }

    private static final class BundleEntries {
        private final byte[] manifestBytes;
        private final byte[] recordBytes;

        private BundleEntries(byte[] manifestBytes, byte[] recordBytes) {
            this.manifestBytes = manifestBytes;
            this.recordBytes = recordBytes;
        }
    }

    public static final class ValidatedBundle {
        private final byte[] manifestBytes;
        private final byte[] recordBytes;
        private final JsonObject manifest;
        private final JsonObject records;
        private final Map<String, SyncBundleCodec.AttachmentManifestEntry> attachmentsById;
        private final Map<String, SyncBundleCodec.AttachmentManifestEntry> attachmentsByHash;

        private ValidatedBundle(
                @NonNull byte[] manifestBytes,
                @NonNull byte[] recordBytes,
                @NonNull JsonObject manifest,
                @NonNull JsonObject records,
                @NonNull Map<String, SyncBundleCodec.AttachmentManifestEntry> attachmentsById,
                @NonNull Map<String, SyncBundleCodec.AttachmentManifestEntry> attachmentsByHash) {
            this.manifestBytes = manifestBytes;
            this.recordBytes = recordBytes;
            this.manifest = manifest;
            this.records = records;
            this.attachmentsById = attachmentsById;
            this.attachmentsByHash = attachmentsByHash;
        }

        @NonNull
        public byte[] getManifestBytes() {
            return manifestBytes.clone();
        }

        @NonNull
        public byte[] getRecordBytes() {
            return recordBytes.clone();
        }

        @NonNull
        public JsonObject getManifest() {
            return manifest.deepCopy();
        }

        @NonNull
        public JsonObject getRecords() {
            return records.deepCopy();
        }

        @NonNull
        public Map<String, SyncBundleCodec.AttachmentManifestEntry> getAttachmentsById() {
            return new LinkedHashMap<>(attachmentsById);
        }

        @NonNull
        public Map<String, SyncBundleCodec.AttachmentManifestEntry> getAttachmentsByHash() {
            return new LinkedHashMap<>(attachmentsByHash);
        }
    }
}
