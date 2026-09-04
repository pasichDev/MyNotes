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
import java.util.Collections;
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

    /** Wire field mapping a re-keyed manifest id back to the record's own attachment id. */
    static final String FIELD_ATTACHMENT_ID_ALIASES = "attachmentIdAliases";

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern MIME_TYPE =
            Pattern.compile("^[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+$");
    private static final Gson GSON = new Gson();
    private final SyncBundleValidator validator = new SyncBundleValidator();

    @NonNull
    public byte[] encode(@NonNull SyncSnapshot snapshot, @NonNull Instant createdAt)
            throws IOException {
        return encode(snapshot, createdAt, Collections.emptyList());
    }

    @NonNull
    public byte[] encode(
            @NonNull SyncSnapshot snapshot,
            @NonNull Instant createdAt,
            @NonNull Collection<String> parentBundleIds)
            throws IOException {
        return encode(
                snapshot,
                createdAt,
                parentBundleIds,
                Collections.emptyList(),
                Collections.emptySet());
    }

    /**
     * Encodes one bundle, including the conflict versions that are still unresolved.
     *
     * <p>A merged descendant used to carry only the deterministic winner, so publishing it made
     * every losing version unreachable: the bundles holding them stopped being frontier heads and
     * nothing else referenced them. A device that had never seen the conflict could not discover
     * it, and a device that had seen it held the only copy. Unresolved alternatives now travel in
     * the bundle itself and are carried forward until some device records that the conflict was
     * resolved, which makes them replicated durable state rather than one device's local queue.
     *
     * @param unresolvedAlternatives losing versions that must remain recoverable.
     * @param resolvedAlternativeIds version identities a user has explicitly settled.
     */
    @NonNull
    public byte[] encode(
            @NonNull SyncSnapshot snapshot,
            @NonNull Instant createdAt,
            @NonNull Collection<String> parentBundleIds,
            @NonNull Collection<SyncRecord> unresolvedAlternatives,
            @NonNull Collection<String> resolvedAlternativeIds)
            throws IOException {
        List<SyncRecord> alternatives = dedupeAlternatives(unresolvedAlternatives);
        AttachmentPlan attachmentPlan = planAttachments(snapshot, alternatives);
        JsonObject recordsRoot = new JsonObject();
        recordsRoot.add("notes", liveArray(snapshot, SyncRecord.Type.NOTE, attachmentPlan));
        recordsRoot.add("tasks", liveArray(snapshot, SyncRecord.Type.TASK, attachmentPlan));
        recordsRoot.add("tags", liveArray(snapshot, SyncRecord.Type.TAG, attachmentPlan));
        recordsRoot.add(
                "categories", liveArray(snapshot, SyncRecord.Type.CATEGORY, attachmentPlan));
        recordsRoot.add(
                "preferences", liveArray(snapshot, SyncRecord.Type.PREFERENCES, attachmentPlan));
        recordsRoot.add("tombstones", tombstones(snapshot));
        recordsRoot.add("alternatives", alternativeArray(alternatives, attachmentPlan));
        JsonArray resolved = new JsonArray();
        for (String versionId : new java.util.TreeSet<>(resolvedAlternativeIds)) {
            if (!SHA_256.matcher(versionId).matches()) {
                throw new IOException("Sync bundle contains an invalid resolved version id");
            }
            resolved.add(versionId);
        }
        recordsRoot.add("resolvedAlternatives", resolved);

        JsonArray attachments = attachmentPlan.manifest();
        byte[] recordBytes = GSON.toJson(recordsRoot).getBytes(StandardCharsets.UTF_8);
        if (recordBytes.length > SyncBundleValidator.MAX_RECORD_BYTES) {
            throw new IOException("Sync records exceed the schema-1 size limit");
        }

        JsonObject manifest = new JsonObject();
        manifest.addProperty("format", BUNDLE_FORMAT);
        manifest.addProperty("schemaVersion", SCHEMA_VERSION);
        manifest.addProperty("bundleId", UUID.randomUUID().toString());
        JsonArray parents = new JsonArray();
        LinkedHashSet<String> uniqueParents = new LinkedHashSet<>(parentBundleIds);
        if (uniqueParents.size() > SyncBundleValidator.MAX_PARENT_BUNDLE_COUNT) {
            throw new IOException("Sync bundle exceeds the parent frontier limit");
        }
        for (String parent : uniqueParents) {
            SyncBundleValidator.validateUuid(parent);
            parents.add(parent);
        }
        manifest.add("parentBundleIds", parents);
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
        List<SyncRecord> alternatives = parseAlternatives(records, attachmentsById);
        java.util.Set<String> resolvedAlternativeIds = parseResolvedAlternatives(records);
        JsonObject manifest = validated.getManifest();
        JsonArray parents = manifest.getAsJsonArray("parentBundleIds");
        List<String> parentBundleIds = new ArrayList<>();
        if (parents != null) {
            for (JsonElement parent : parents) parentBundleIds.add(parent.getAsString());
        }
        return new DecodedBundle(
                new SyncSnapshot(result),
                validated.getAttachmentsByHash(),
                manifest.get("bundleId").getAsString(),
                parentBundleIds,
                alternatives,
                resolvedAlternativeIds);
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
            @NonNull SyncSnapshot snapshot,
            @NonNull SyncRecord.Type type,
            @NonNull AttachmentPlan attachmentPlan)
            throws IOException {
        JsonArray array = new JsonArray();
        for (SyncRecord record : snapshot.getLiveRecords(type)) {
            JsonObject item = record.getPayload();
            item.addProperty("id", record.getId());
            item.addProperty("updatedAt", record.getUpdatedAt().toString());
            if (type == SyncRecord.Type.NOTE) {
                normalizeNoteAttachmentFields(item, attachmentPlan.wireIdsFor(record));
            }
            array.add(item);
        }
        return array;
    }

    /**
     * Replaces the local attachment fields with the wire references.
     *
     * @param wireIds the manifest id each of this record's logical attachment ids travels under;
     *     identical to the logical id except for a re-keyed collision.
     */
    private static void normalizeNoteAttachmentFields(
            JsonObject note, @NonNull Map<String, String> wireIds) throws IOException {
        JsonArray manifestEntries = note.getAsJsonArray("attachmentsManifest");
        JsonArray attachmentIds = new JsonArray();
        JsonObject attachmentNames = new JsonObject();
        JsonObject aliases = new JsonObject();
        if (manifestEntries != null) {
            for (JsonElement element : manifestEntries) {
                AttachmentManifestEntry attachment =
                        AttachmentManifestEntry.fromJson(element.getAsJsonObject());
                String wireId = wireIds.getOrDefault(attachment.id, attachment.id);
                attachmentIds.add(wireId);
                if (!wireId.equals(attachment.id)) {
                    aliases.addProperty(wireId, attachment.id);
                }
                if (attachment.displayName != null && !attachment.displayName.isEmpty()) {
                    attachmentNames.addProperty(wireId, attachment.displayName);
                }
            }
        }
        note.remove("attachmentsManifest");
        note.remove("attachmentHashes");
        // Cleared as well as rebuilt. Only the two above were removed, so a payload that already
        // carried an attachmentNames key kept it on the wire even when the rebuilt map was
        // empty, and a decoded record then hashed differently from the local one that produced
        // it — a conflict against itself on every sync.
        note.remove("attachmentNames");
        note.remove(FIELD_ATTACHMENT_ID_ALIASES);
        if (attachmentIds.size() > 0) {
            note.add("attachmentIds", attachmentIds);
        }
        if (attachmentNames.size() > 0) {
            note.add("attachmentNames", attachmentNames);
        }
        if (aliases.size() > 0) {
            note.add(FIELD_ATTACHMENT_ID_ALIASES, aliases);
        }
    }

    /**
     * Orders alternatives deterministically and drops exact duplicates.
     *
     * <p>Two devices publishing the same alternative must produce byte-identical bundles for the
     * duplicate-copy check in the read path to keep working.
     */
    @NonNull
    private static List<SyncRecord> dedupeAlternatives(
            @NonNull Collection<SyncRecord> alternatives) {
        Map<String, SyncRecord> byVersion = new java.util.TreeMap<>();
        for (SyncRecord alternative : alternatives) {
            byVersion.putIfAbsent(
                    alternative.getType().getWireValue()
                            + ":"
                            + alternative.getId()
                            + ":"
                            + alternative.getCanonicalPayloadHash(),
                    alternative);
        }
        return new ArrayList<>(byVersion.values());
    }

    @NonNull
    private static JsonArray alternativeArray(
            @NonNull List<SyncRecord> alternatives, @NonNull AttachmentPlan attachmentPlan)
            throws IOException {
        JsonArray array = new JsonArray();
        for (SyncRecord alternative : alternatives) {
            JsonObject item =
                    alternative.isTombstone() ? new JsonObject() : alternative.getPayload();
            item.addProperty("type", alternative.getType().getWireValue());
            item.addProperty("id", alternative.getId());
            item.addProperty("updatedAt", alternative.getUpdatedAt().toString());
            if (alternative.isTombstone()) {
                item.addProperty("deletedAt", alternative.getDeletedAt().toString());
            } else if (alternative.getType() == SyncRecord.Type.NOTE) {
                normalizeNoteAttachmentFields(item, attachmentPlan.wireIdsFor(alternative));
            }
            array.add(item);
        }
        return array;
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

    /**
     * Lays out the bundle's attachment manifest for every version it carries.
     *
     * <p>The manifest is keyed by logical attachment id and a note references its entries by that
     * id, so one id can describe only one blob per bundle. A live note and one of its unresolved
     * alternatives may nonetheless carry different content under one id — the file was replaced in
     * place, or two devices derived the same id — and refusing to encode that used to fail every
     * publish for the account, before the conflict could even be stored for the user to settle. The
     * later version's entry now travels under a derived id and the record carries the mapping back,
     * so the decoded version is byte-for-byte the one that was published and keeps the identity
     * every device's resolution bookkeeping refers to.
     */
    @NonNull
    private static AttachmentPlan planAttachments(
            @NonNull SyncSnapshot snapshot, @NonNull List<SyncRecord> alternatives)
            throws IOException {
        AttachmentPlan plan = new AttachmentPlan();
        Map<String, AttachmentManifestEntry> seenByHash = new LinkedHashMap<>();
        List<SyncRecord> notes = new ArrayList<>(snapshot.getLiveRecords(SyncRecord.Type.NOTE));
        // An unresolved alternative is only recoverable if its blobs are described here too.
        for (SyncRecord alternative : alternatives) {
            if (!alternative.isTombstone() && alternative.getType() == SyncRecord.Type.NOTE) {
                notes.add(alternative);
            }
        }
        for (SyncRecord record : notes) {
            JsonArray manifestEntries = record.getPayload().getAsJsonArray("attachmentsManifest");
            if (manifestEntries == null) continue;
            for (JsonElement element : manifestEntries) {
                AttachmentManifestEntry attachment =
                        AttachmentManifestEntry.fromJson(element.getAsJsonObject());
                String wireId = attachment.id;
                AttachmentManifestEntry sameId = plan.byWireId.get(wireId);
                if (sameId != null && !sameId.sameRemoteFile(attachment)) {
                    wireId = aliasFor(attachment);
                    sameId = plan.byWireId.get(wireId);
                    if (sameId != null && !sameId.sameRemoteFile(attachment)) {
                        throw new IOException(
                                "Two notes reference conflicting attachment metadata");
                    }
                    plan.wireIdsFor(record).put(attachment.id, wireId);
                }
                if (sameId == null) {
                    plan.byWireId.put(wireId, attachment.withId(wireId));
                }
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
        return plan;
    }

    /** Deterministic, so two devices publishing the same collision write the same bundle. */
    @NonNull
    private static String aliasFor(@NonNull AttachmentManifestEntry attachment) {
        return UUID.nameUUIDFromBytes(
                        ("attachment-alias\n" + attachment.id + "\n" + attachment.sha256)
                                .getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    /** The manifest entries by wire id, and each record's logical-to-wire id mapping. */
    private static final class AttachmentPlan {
        private final Map<String, AttachmentManifestEntry> byWireId = new LinkedHashMap<>();
        private final Map<SyncRecord, Map<String, String>> wireIdsByRecord =
                new java.util.IdentityHashMap<>();

        @NonNull
        Map<String, String> wireIdsFor(@NonNull SyncRecord record) {
            Map<String, String> wireIds = wireIdsByRecord.get(record);
            if (wireIds == null) {
                wireIds = new LinkedHashMap<>();
                wireIdsByRecord.put(record, wireIds);
            }
            return wireIds;
        }

        @NonNull
        JsonArray manifest() {
            JsonArray attachments = new JsonArray();
            for (AttachmentManifestEntry attachment : byWireId.values()) {
                attachments.add(attachment.toJson(false));
            }
            return attachments;
        }
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
        JsonObject aliases = payload.getAsJsonObject(FIELD_ATTACHMENT_ID_ALIASES);
        // Wire-only, all three: the local store never produces them, and leaving one behind made
        // a decoded record hash differently from the identical local one.
        payload.remove("attachmentIds");
        payload.remove(FIELD_ATTACHMENT_ID_ALIASES);
        if (attachmentIds == null) {
            payload.remove("attachmentNames");
            return;
        }
        JsonArray attachmentHashes = new JsonArray();
        JsonArray manifest = new JsonArray();
        // Keyed by logical attachment UUID, exactly as the wire carries it and exactly as
        // RoomSyncStore builds it locally. Rekeying this map by content hash made a decoded
        // record hash differently from the identical locally built one, so every note with an
        // attachment reported a conflict against itself on every sync, forever.
        JsonObject namesById = new JsonObject();
        for (JsonElement element : attachmentIds) {
            String wireId = element.getAsString();
            AttachmentManifestEntry attachment = attachmentsById.get(wireId);
            if (attachment == null) {
                throw new IOException("Note references an unknown attachment manifest entry");
            }
            // A re-keyed collision travels under a derived id; the record's own id is restored
            // so the decoded version is the one that was published.
            String attachmentId =
                    aliases != null && aliases.has(wireId)
                            ? aliases.get(wireId).getAsString()
                            : wireId;
            JsonObject value = attachment.withId(attachmentId).toJson(true);
            if (attachmentNames != null && attachmentNames.has(wireId)) {
                value.addProperty("displayName", attachmentNames.get(wireId).getAsString());
            }
            manifest.add(value);
            attachmentHashes.add(attachment.sha256);
            if (value.has("displayName") && !value.get("displayName").isJsonNull()) {
                namesById.addProperty(attachmentId, value.get("displayName").getAsString());
            }
        }
        payload.add("attachmentsManifest", manifest);
        payload.add("attachmentHashes", attachmentHashes);
        // The display name restoreAttachments actually uses travels on the manifest entry above;
        // this map exists only so the payload matches the one the local store builds.
        payload.add("attachmentNames", namesById);
    }

    @NonNull
    private static List<SyncRecord> parseAlternatives(
            @NonNull JsonObject records,
            @NonNull Map<String, AttachmentManifestEntry> attachmentsById)
            throws IOException {
        List<SyncRecord> alternatives = new ArrayList<>();
        JsonArray array = records.getAsJsonArray("alternatives");
        if (array == null) {
            // Written by a client that predates durable alternatives.
            return alternatives;
        }
        for (JsonElement element : array) {
            JsonObject item = element.getAsJsonObject();
            SyncRecord.Type type =
                    SyncRecord.Type.fromWireValue(SyncBundleValidator.requireString(item, "type"));
            String id = SyncBundleValidator.requireString(item, "id");
            SyncBundleValidator.validateUuid(id);
            Instant updatedAt = Instant.parse(SyncBundleValidator.requireString(item, "updatedAt"));
            JsonElement deletedAt = item.get("deletedAt");
            if (deletedAt != null && !deletedAt.isJsonNull()) {
                alternatives.add(
                        SyncRecord.tombstone(
                                type, id, updatedAt, Instant.parse(deletedAt.getAsString())));
                continue;
            }
            JsonObject payload = item.deepCopy();
            payload.remove("type");
            payload.remove("id");
            payload.remove("updatedAt");
            payload.remove("deletedAt");
            SyncMetadata.stripDeviceLocalFields(type.getWireValue(), payload);
            if (type == SyncRecord.Type.NOTE) {
                hydrateNoteAttachments(payload, attachmentsById);
            }
            alternatives.add(SyncRecord.live(type, id, updatedAt, payload));
        }
        return alternatives;
    }

    @NonNull
    private static java.util.Set<String> parseResolvedAlternatives(@NonNull JsonObject records)
            throws IOException {
        java.util.Set<String> resolved = new LinkedHashSet<>();
        JsonArray array = records.getAsJsonArray("resolvedAlternatives");
        if (array == null) {
            return resolved;
        }
        for (JsonElement element : array) {
            if (element == null || !element.isJsonPrimitive()) {
                throw new IOException("Sync bundle contains an invalid resolved version id");
            }
            String versionId = element.getAsString();
            if (!SHA_256.matcher(versionId).matches()) {
                throw new IOException("Sync bundle contains an invalid resolved version id");
            }
            resolved.add(versionId);
        }
        return resolved;
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
    private static String sha256(byte[] bytes) {
        return Sha256.of(bytes);
    }

    public static final class DecodedBundle {
        private final SyncSnapshot snapshot;
        private final Map<String, AttachmentManifestEntry> attachmentsByHash;
        private final String bundleId;
        private final List<String> parentBundleIds;
        private final List<SyncRecord> alternatives;
        private final java.util.Set<String> resolvedAlternativeIds;

        DecodedBundle(
                @NonNull SyncSnapshot snapshot,
                @NonNull Map<String, AttachmentManifestEntry> attachmentsByHash,
                @NonNull String bundleId,
                @NonNull List<String> parentBundleIds,
                @NonNull List<SyncRecord> alternatives,
                @NonNull java.util.Set<String> resolvedAlternativeIds) {
            this.snapshot = snapshot;
            this.attachmentsByHash = attachmentsByHash;
            this.bundleId = bundleId;
            this.parentBundleIds = Collections.unmodifiableList(new ArrayList<>(parentBundleIds));
            this.alternatives = Collections.unmodifiableList(new ArrayList<>(alternatives));
            this.resolvedAlternativeIds =
                    Collections.unmodifiableSet(new LinkedHashSet<>(resolvedAlternativeIds));
        }

        /** Losing versions this bundle keeps recoverable. */
        @NonNull
        public List<SyncRecord> getAlternatives() {
            return alternatives;
        }

        /** Version identities some device recorded as explicitly resolved. */
        @NonNull
        public java.util.Set<String> getResolvedAlternativeIds() {
            return resolvedAlternativeIds;
        }

        @NonNull
        public SyncSnapshot getSnapshot() {
            return snapshot;
        }

        @NonNull
        public Map<String, AttachmentManifestEntry> getAttachmentsByHash() {
            return attachmentsByHash;
        }

        @NonNull
        public String getBundleId() {
            return bundleId;
        }

        @NonNull
        public List<String> getParentBundleIds() {
            return parentBundleIds;
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
            // Trimmed here, once, so every consumer sees the name the validator judged.
            String displayName =
                    value.has("displayName")
                                    && !value.get("displayName").isJsonNull()
                                    && value.get("displayName").isJsonPrimitive()
                            ? value.get("displayName").getAsString().trim()
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
            return sha256.equals(other.sha256) && path.equals(other.path) && size == other.size;
        }

        /** The same blob under another logical id. */
        @NonNull
        AttachmentManifestEntry withId(@NonNull String newId) {
            return newId.equals(id)
                    ? this
                    : new AttachmentManifestEntry(newId, sha256, mimeType, size, path, displayName);
        }
    }
}
