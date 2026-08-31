package com.pasich.mynotes.data.sync;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.database.entities.SyncConflictEntity;
import com.pasich.mynotes.data.database.entities.SyncMetadataEntity;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.data.model.TaskCategory;
import com.pasich.mynotes.data.preferences.PreferenceHelper;
import com.pasich.mynotes.extendedEditor.attach.AttachmentStorage;
import com.pasich.mynotes.extendedEditor.models.EditorAttachment;
import com.pasich.mynotes.utils.backup.models.PreferencesBackup;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Room-backed sync store. Stable sync IDs remain separate from local integer primary keys. */
public final class RoomSyncStore implements SyncStore {
    private static final String PREFS = "sync_state";
    private static final String STATE = "last_state";
    private static final String PREFERENCES_HASH = "preferences_hash";
    private final AppDatabase database;
    private final SharedPreferences preferences;
    private final PreferenceHelper preferenceHelper;
    private final Context context;
    private final Gson gson = new Gson();

    public RoomSyncStore(
            @NonNull Context context,
            @NonNull AppDatabase database,
            @NonNull PreferenceHelper preferenceHelper) {
        this.database = database;
        this.preferenceHelper = preferenceHelper;
        this.context = context.getApplicationContext();
        this.preferences =
                context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        database.syncMetadataDao()
                .insertIfAbsent(
                        new SyncMetadataEntity(
                                SyncMetadata.RECORD_TYPE_PREFERENCES,
                                0,
                                "00000000-0000-4000-8000-000000000000",
                                0L,
                                null));
    }

    @NonNull
    @Override
    public SyncSnapshot readSnapshot() throws IOException {
        List<SyncRecord> records = new ArrayList<>();
        for (SyncMetadataEntity metadata : database.syncMetadataDao().getAll()) {
            if (metadata.deletedAt != null) {
                records.add(
                        SyncRecord.tombstone(
                                SyncRecord.Type.fromWireValue(metadata.recordType),
                                metadata.stableId,
                                Instant.ofEpochMilli(metadata.updatedAt),
                                Instant.ofEpochMilli(metadata.deletedAt)));
                continue;
            }
            JsonObject payload = payload(metadata);
            if (payload != null) {
                SyncMetadataEntity current =
                        database.syncMetadataDao().get(metadata.recordType, metadata.localId);
                records.add(
                        SyncRecord.live(
                                SyncRecord.Type.fromWireValue(metadata.recordType),
                                metadata.stableId,
                                Instant.ofEpochMilli(
                                        current == null ? metadata.updatedAt : current.updatedAt),
                                payload));
            }
        }
        return new SyncSnapshot(records);
    }

    @Override
    public void applySnapshot(
            @NonNull SyncSnapshot snapshot, @NonNull List<SyncMergeResult.Conflict> conflicts)
            throws IOException {
        database.runInTransaction(
                () -> {
                    Map<String, SyncMetadataEntity> byStableId = new HashMap<>();
                    for (SyncMetadataEntity metadata : database.syncMetadataDao().getAll()) {
                        byStableId.put(metadata.recordType + ":" + metadata.stableId, metadata);
                    }
                    for (SyncRecord record : snapshot.getRecords()) {
                        SyncMetadataEntity metadata =
                                byStableId.get(
                                        record.getType().getWireValue() + ":" + record.getId());
                        if (metadata == null && !record.isTombstone()) {
                            long localId = insertRemoteRecord(record);
                            if (localId >= 0) {
                                database.syncMetadataDao()
                                        .insertIfAbsent(
                                                new SyncMetadataEntity(
                                                        record.getType().getWireValue(),
                                                        localId,
                                                        record.getId(),
                                                        record.getUpdatedAt().toEpochMilli(),
                                                        null));
                            }
                            continue;
                        }
                        if (metadata == null) continue;
                        if (record.isTombstone()) {
                            markDeleted(metadata);
                            database.syncMetadataDao()
                                    .setVersion(
                                            metadata.recordType,
                                            metadata.localId,
                                            record.getUpdatedAt().toEpochMilli(),
                                            record.getDeletedAt().toEpochMilli());
                            continue;
                        }
                        applyPayload(metadata, record.getPayload());
                        database.syncMetadataDao()
                                .setVersion(
                                        metadata.recordType,
                                        metadata.localId,
                                        record.getUpdatedAt().toEpochMilli(),
                                        null);
                    }
                    persistConflicts(conflicts);
                });
    }

    @Nullable
    private JsonObject payload(SyncMetadataEntity metadata) {
        Object value = null;
        if ("note".equals(metadata.recordType))
            value = database.noteDao().getNoteSync((int) metadata.localId);
        else if ("task".equals(metadata.recordType))
            value = database.taskDao().getTaskSync((int) metadata.localId);
        else if (SyncMetadata.RECORD_TYPE_CATEGORY.equals(metadata.recordType))
            value = database.taskCategoryDao().getCategorySync((int) metadata.localId);
        else if ("tag".equals(metadata.recordType))
            value = database.tagsDao().getTagSync(metadata.localId);
        else if (SyncMetadata.RECORD_TYPE_PREFERENCES.equals(metadata.recordType)) {
            value = preferenceHelper.getListPreferences();
        }
        if (value == null) return null;
        JsonObject result = gson.toJsonTree(value).getAsJsonObject();
        if (SyncMetadata.RECORD_TYPE_PREFERENCES.equals(metadata.recordType)) {
            String hash = result.toString();
            String previous = preferences.getString(PREFERENCES_HASH, null);
            if (!hash.equals(previous)) {
                database.syncMetadataDao()
                        .touch(metadata.recordType, metadata.localId, System.currentTimeMillis());
                preferences.edit().putString(PREFERENCES_HASH, hash).apply();
            }
        }
        if ("task".equals(metadata.recordType)) {
            JsonElement category = result.get("categoryId");
            if (category != null && !category.isJsonNull()) {
                SyncMetadataEntity categoryMetadata =
                        database.syncMetadataDao().get("category", category.getAsInt());
                if (categoryMetadata != null)
                    result.addProperty("categoryStableId", categoryMetadata.stableId);
            }
        }
        if ("note".equals(metadata.recordType)) addAttachmentMetadata(result);
        return result;
    }

    private void applyPayload(SyncMetadataEntity metadata, JsonObject payload) {
        if ("note".equals(metadata.recordType)) {
            Note note = gson.fromJson(payload, Note.class);
            note.setId((int) metadata.localId);
            restoreAttachments(note, payload);
            database.noteDao().addNote(note);
        } else if ("task".equals(metadata.recordType)) {
            Task task = gson.fromJson(payload, Task.class);
            task.setId((int) metadata.localId);
            if (payload.has("categoryStableId")) {
                SyncMetadataEntity category =
                        database.syncMetadataDao()
                                .getByStableId(
                                        SyncMetadata.RECORD_TYPE_CATEGORY,
                                        payload.get("categoryStableId").getAsString());
                if (category != null) task.setCategoryId((int) category.localId);
            }
            database.taskDao().updateTask(task);
        } else if (SyncMetadata.RECORD_TYPE_CATEGORY.equals(metadata.recordType)) {
            TaskCategory category = gson.fromJson(payload, TaskCategory.class);
            category.setId((int) metadata.localId);
            database.taskCategoryDao().updateCategory(category);
        } else if ("tag".equals(metadata.recordType)) {
            Tag tag = gson.fromJson(payload, Tag.class);
            tag.id = metadata.localId;
            database.tagsDao().updateTag(tag);
        } else if (SyncMetadata.RECORD_TYPE_PREFERENCES.equals(metadata.recordType)) {
            preferenceHelper.setListPreferences(gson.fromJson(payload, PreferencesBackup.class));
        }
    }

    private long insertRemoteRecord(SyncRecord record) {
        if (record.getType() == SyncRecord.Type.NOTE) {
            Note note = gson.fromJson(record.getPayload(), Note.class);
            note.setId(0);
            note.setAttachments(null);
            long localId = database.noteDao().addNote(note);
            note.setId((int) localId);
            restoreAttachments(note, record.getPayload());
            database.noteDao()
                    .updateNoteContent(
                            note.getId(),
                            note.getTitle(),
                            note.getValue(),
                            note.getValueJson(),
                            note.getDate(),
                            note.getTag(),
                            note.getAttachments());
            return localId;
        }
        if (record.getType() == SyncRecord.Type.TASK) {
            Task task = gson.fromJson(record.getPayload(), Task.class);
            task.setId(0);
            if (record.getPayload().has("categoryStableId")) {
                SyncMetadataEntity category =
                        database.syncMetadataDao()
                                .getByStableId(
                                        SyncMetadata.RECORD_TYPE_CATEGORY,
                                        record.getPayload().get("categoryStableId").getAsString());
                if (category != null) task.setCategoryId((int) category.localId);
            }
            return database.taskDao().insertTask(task);
        }
        if (record.getType() == SyncRecord.Type.CATEGORY) {
            TaskCategory category = gson.fromJson(record.getPayload(), TaskCategory.class);
            category.setId(0);
            return database.taskCategoryDao().insertCategory(category);
        }
        if (record.getType() == SyncRecord.Type.TAG) {
            Tag tag = gson.fromJson(record.getPayload(), Tag.class);
            tag.id = 0;
            return database.tagsDao().addTag(tag);
        }
        return -1;
    }

    private void markDeleted(SyncMetadataEntity metadata) {
        if ("note".equals(metadata.recordType))
            database.noteDao().deleteById((int) metadata.localId);
        else if ("task".equals(metadata.recordType))
            database.taskDao().deleteById((int) metadata.localId);
        else if (SyncMetadata.RECORD_TYPE_CATEGORY.equals(metadata.recordType))
            database.taskCategoryDao().deleteById((int) metadata.localId);
        else if ("tag".equals(metadata.recordType)) database.tagsDao().deleteById(metadata.localId);
    }

    public List<SyncConflictEntity> getConflicts() {
        return database.syncConflictDao().getAll();
    }

    public List<SyncConflictEntity> getUnresolvedConflicts() {
        return database.syncConflictDao().getUnresolved();
    }

    public void resolveConflict(long conflictId, @NonNull SyncResolution resolution)
            throws IOException {
        if (resolution == SyncResolution.PENDING) return;
        try {
            database.runInTransaction(
                    () -> {
                        SyncConflictEntity conflict =
                                database.syncConflictDao().getById(conflictId);
                        if (conflict == null || conflict.resolved) return;

                        long resolvedAt = System.currentTimeMillis();
                        boolean keepWinner =
                                (resolution == SyncResolution.KEEP_LOCAL
                                                && "LOCAL".equals(conflict.winnerSource))
                                        || (resolution == SyncResolution.KEEP_DRIVE
                                                && "REMOTE".equals(conflict.winnerSource));
                        if (!keepWinner) {
                            try {
                                applyResolvedRecord(conflict, resolution, resolvedAt);
                            } catch (IOException error) {
                                throw new SyncRuntimeException(error);
                            }
                        }
                        database.syncConflictDao()
                                .markResolved(conflictId, resolution.name(), resolvedAt);
                    });
        } catch (SyncRuntimeException error) {
            throw error.ioException;
        }
    }

    private void persistConflicts(@NonNull List<SyncMergeResult.Conflict> conflicts) {
        if (conflicts.isEmpty()) return;

        long createdAt = System.currentTimeMillis();
        List<SyncConflictEntity> rows = new ArrayList<>(conflicts.size());
        for (SyncMergeResult.Conflict conflict : conflicts) {
            rows.add(
                    new SyncConflictEntity(
                            conflict.getType().getWireValue(),
                            conflict.getId(),
                            conflict.getWinnerSource().name(),
                            conflict.getWinner().canonicalSerializedPayload(),
                            conflict.getLoser().canonicalSerializedPayload(),
                            conflict.getWinner().getUpdatedAt().toEpochMilli(),
                            conflict.getLoser().getUpdatedAt().toEpochMilli(),
                            conflict.isWinnerTombstone(),
                            conflict.isLoserTombstone(),
                            SyncResolution.PENDING.name(),
                            false,
                            createdAt,
                            0L));
        }
        database.syncConflictDao().replaceAll(rows);
    }

    private void applyResolvedRecord(
            @NonNull SyncConflictEntity conflict,
            @NonNull SyncResolution resolution,
            long resolvedAt)
            throws IOException {
        SyncRecord selected = selectRecordForResolution(conflict, resolution);
        SyncMetadataEntity metadata =
                database.syncMetadataDao().getByStableId(conflict.recordType, conflict.stableId);
        long updatedAt =
                metadata == null ? resolvedAt : Math.max(resolvedAt, metadata.updatedAt + 1L);

        if (selected.isTombstone()) {
            if (metadata != null) {
                markDeleted(metadata);
                database.syncMetadataDao()
                        .setVersion(conflict.recordType, metadata.localId, updatedAt, updatedAt);
            }
            return;
        }

        if (metadata == null) {
            long localId = insertRemoteRecord(selected);
            if (localId >= 0) {
                database.syncMetadataDao()
                        .insertIfAbsent(
                                new SyncMetadataEntity(
                                        conflict.recordType,
                                        localId,
                                        conflict.stableId,
                                        updatedAt,
                                        null));
            } else if (SyncMetadata.RECORD_TYPE_PREFERENCES.equals(conflict.recordType)) {
                SyncMetadataEntity preferencesMetadata =
                        database.syncMetadataDao()
                                .getByStableId(conflict.recordType, conflict.stableId);
                if (preferencesMetadata != null) {
                    applyPayload(preferencesMetadata, selected.getPayload());
                    database.syncMetadataDao()
                            .setVersion(
                                    conflict.recordType,
                                    preferencesMetadata.localId,
                                    updatedAt,
                                    null);
                }
            }
            return;
        }

        applyPayload(metadata, selected.getPayload());
        database.syncMetadataDao()
                .setVersion(conflict.recordType, metadata.localId, updatedAt, null);
    }

    @NonNull
    private static SyncRecord selectRecordForResolution(
            @NonNull SyncConflictEntity conflict, @NonNull SyncResolution resolution)
            throws IOException {
        boolean keepWinner =
                (resolution == SyncResolution.KEEP_LOCAL && "LOCAL".equals(conflict.winnerSource))
                        || (resolution == SyncResolution.KEEP_DRIVE
                                && "REMOTE".equals(conflict.winnerSource));
        String selectedJson = keepWinner ? conflict.winnerJson : conflict.loserJson;
        JsonObject root = JsonParser.parseString(selectedJson).getAsJsonObject();
        SyncRecord.Type type = SyncRecord.Type.fromWireValue(root.get("type").getAsString());
        String id = root.get("id").getAsString();
        Instant updatedAt = Instant.parse(root.get("updatedAt").getAsString());
        JsonElement deletedAt = root.get("deletedAt");
        if (deletedAt != null && !deletedAt.isJsonNull()) {
            return SyncRecord.tombstone(
                    type, id, updatedAt, Instant.parse(deletedAt.getAsString()));
        }
        JsonObject payload = root.getAsJsonObject("payload");
        return SyncRecord.live(type, id, updatedAt, payload == null ? new JsonObject() : payload);
    }

    private static final class SyncRuntimeException extends RuntimeException {
        private final IOException ioException;

        private SyncRuntimeException(@NonNull IOException ioException) {
            super(ioException);
            this.ioException = ioException;
        }
    }

    @NonNull
    @Override
    public Collection<String> getAttachmentHashes(@NonNull SyncSnapshot snapshot) {
        LinkedHashSet<String> hashes = new LinkedHashSet<>();
        for (SyncRecord record : snapshot.getLiveRecords(SyncRecord.Type.NOTE)) {
            JsonArray values = record.getPayload().getAsJsonArray("attachmentHashes");
            if (values != null) {
                for (JsonElement value : values) {
                    hashes.add(value.getAsString());
                }
                continue;
            }

            JsonArray manifestEntries = record.getPayload().getAsJsonArray("attachmentsManifest");
            if (manifestEntries == null) continue;
            for (JsonElement value : manifestEntries) {
                JsonObject manifestEntry = value.getAsJsonObject();
                if (manifestEntry.has("sha256")) {
                    hashes.add(manifestEntry.get("sha256").getAsString());
                }
            }
        }
        return hashes;
    }

    @Override
    public boolean hasAttachment(@NonNull String sha256) {
        return attachmentFile(sha256).isFile();
    }

    @NonNull
    @Override
    public InputStream readAttachment(@NonNull String sha256) throws IOException {
        return new FileInputStream(attachmentFile(sha256));
    }

    @Override
    public void writeAttachment(@NonNull String sha256, @NonNull InputStream content)
            throws IOException {
        File target = attachmentFile(sha256);
        File temp = new File(target.getParentFile(), sha256 + ".tmp");
        try (InputStream in = content;
                FileOutputStream out = new FileOutputStream(temp)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
        if (!temp.renameTo(target)) throw new IOException("Cannot store attachment");
    }

    private File attachmentFile(String sha256) {
        File dir = new File(context.getFilesDir(), "sync-attachments");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, sha256);
    }

    private void addAttachmentMetadata(JsonObject payload) {
        String json =
                payload.has("h") && !payload.get("h").isJsonNull()
                        ? payload.get("h").getAsString()
                        : null;
        if (json == null || json.trim().isEmpty()) return;
        try {
            JsonArray attachments = JsonParser.parseString(json).getAsJsonArray();
            JsonArray manifest = new JsonArray();
            JsonArray hashes = new JsonArray();
            JsonObject names = new JsonObject();
            for (JsonElement element : attachments) {
                EditorAttachment attachment = gson.fromJson(element, EditorAttachment.class);
                File file = AttachmentStorage.resolve(context, attachment.url);
                if (file == null || !file.isFile()) continue;
                String hash = sha256(file);
                String displayName =
                        attachment.name == null || attachment.name.trim().isEmpty()
                                ? file.getName()
                                : attachment.name.trim();
                hashes.add(hash);
                names.addProperty(hash, displayName);

                JsonObject manifestEntry = new JsonObject();
                manifestEntry.addProperty("id", stableAttachmentId(hash));
                manifestEntry.addProperty("sha256", hash);
                manifestEntry.addProperty(
                        "mimeType", detectMimeType(file, attachment, displayName));
                manifestEntry.addProperty("size", file.length());
                manifestEntry.addProperty("path", "attachments/" + hash);
                manifestEntry.addProperty("displayName", displayName);
                manifest.add(manifestEntry);
            }
            if (!hashes.isEmpty()) {
                payload.add("attachmentsManifest", manifest);
                payload.add("attachmentHashes", hashes);
                payload.add("attachmentNames", names);
            }
        } catch (Exception ignored) {
        }
    }

    private void restoreAttachments(Note note, JsonObject payload) {
        if (!payload.has("attachmentHashes") || !payload.has("attachmentNames")) return;
        try {
            JsonArray hashes = payload.getAsJsonArray("attachmentHashes");
            JsonObject names = payload.getAsJsonObject("attachmentNames");
            JsonArray attachments = new JsonArray();
            File folder = AttachmentStorage.noteFolder(context, note.getId());
            for (JsonElement item : hashes) {
                String hash = item.getAsString();
                File source = attachmentFile(hash);
                if (!source.isFile()) continue;
                String name = names.has(hash) ? names.get(hash).getAsString() : hash;
                if (!isSafeAttachmentName(name)) {
                    continue;
                }
                File target = new File(folder, name);
                try (InputStream in = new FileInputStream(source);
                        OutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                }
                JsonObject attachment = new JsonObject();
                attachment.addProperty(
                        "url", "file://attachments/note_" + note.getId() + "/" + name);
                attachment.addProperty("name", name);
                attachments.add(attachment);
            }
            note.setAttachments(gson.toJson(attachments));
        } catch (Exception ignored) {
        }
    }

    private static boolean isSafeAttachmentName(@NonNull String name) {
        String value = name.trim();
        if (value.isEmpty() || value.equals(".") || value.equals("..")) return false;
        if (value.length() > 255 || value.contains("/") || value.contains("\\")) return false;
        if (value.contains("..") || value.indexOf('\u0000') >= 0) return false;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) return false;
        }
        return new File(value).getName().equals(value);
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) hex.append(String.format("%02x", value & 0xff));
        return hex.toString();
    }

    @NonNull
    private static String stableAttachmentId(@NonNull String hash) {
        return UUID.nameUUIDFromBytes(hash.getBytes(StandardCharsets.UTF_8)).toString();
    }

    @NonNull
    private static String detectMimeType(
            @NonNull File file, EditorAttachment attachment, @NonNull String displayName) {
        String mimeType = URLConnection.guessContentTypeFromName(displayName);
        if (mimeType != null && !mimeType.trim().isEmpty()) {
            return mimeType;
        }

        if (attachment != null
                && attachment.extension != null
                && !attachment.extension.trim().isEmpty()) {
            String candidate =
                    URLConnection.guessContentTypeFromName(
                            "file." + attachment.extension.replace(".", "").trim());
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate;
            }
        }

        mimeType = URLConnection.guessContentTypeFromName(file.getName());
        return mimeType == null || mimeType.trim().isEmpty()
                ? "application/octet-stream"
                : mimeType;
    }

    @NonNull
    @Override
    public SyncState readState() throws IOException {
        String json = preferences.getString(STATE, null);
        if (json == null) return SyncState.idle();
        try {
            JsonObject value = JsonParser.parseString(json).getAsJsonObject();
            String status = value.get("status").getAsString();
            String backend =
                    value.has("backendIdentifier") && !value.get("backendIdentifier").isJsonNull()
                            ? value.get("backendIdentifier").getAsString()
                            : "google-drive";
            if ("SUCCESS".equals(status)) {
                return SyncState.success(
                        backend,
                        Instant.parse(value.get("lastSuccessfulSyncAt").getAsString()),
                        value.has("conflictCount") ? value.get("conflictCount").getAsInt() : 0);
            }
            if ("ERROR".equals(status)) {
                Instant last =
                        value.has("lastSuccessfulSyncAt")
                                        && !value.get("lastSuccessfulSyncAt").isJsonNull()
                                ? Instant.parse(value.get("lastSuccessfulSyncAt").getAsString())
                                : null;
                return SyncState.error(backend, last, value.get("errorMessage").getAsString());
            }
            return SyncState.idle();
        } catch (RuntimeException error) {
            return SyncState.idle();
        }
    }

    @Override
    public void writeState(@NonNull SyncState state) {
        preferences.edit().putString(STATE, gson.toJson(state)).apply();
    }
}
