package com.pasich.mynotes.data.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
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
import com.pasich.mynotes.data.database.entities.SyncPendingPreferencesEntity;
import com.pasich.mynotes.data.database.entities.SyncStateEntity;
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
import java.util.concurrent.ConcurrentHashMap;

/** Room-backed sync store. Stable sync IDs remain separate from local integer primary keys. */
public final class RoomSyncStore implements SyncStore {
    private static final String TAG = "RoomSyncStore";
    private static final String PREFS = "sync_state";
    private static final String LEGACY_STATE = "last_state";
    private static final String PREFERENCES_HASH = "preferences_hash";
    private static final String PREFERENCES_STABLE_ID = "00000000-0000-4000-8000-000000000000";
    private final AppDatabase database;
    private final SharedPreferences preferences;
    private volatile boolean seeded;
    private final PreferenceHelper preferenceHelper;
    private final Context context;
    private final Gson gson = new Gson();
    private final AttachmentResolver attachmentResolver;
    private final AttachmentHasher attachmentHasher;
    private final TransactionFailureInjector transactionFailureInjector;

    /**
     * Content hash to the note-folder file holding it, indexed while the snapshot is built so the
     * upload path can find blobs this device owns without duplicating them into the sync cache.
     */
    private final Map<String, File> localAttachments = new ConcurrentHashMap<>();

    public RoomSyncStore(
            @NonNull Context context,
            @NonNull AppDatabase database,
            @NonNull PreferenceHelper preferenceHelper) {
        this(
                context,
                database,
                preferenceHelper,
                AttachmentStorage::resolve,
                RoomSyncStore::sha256,
                record -> {});
    }

    /**
     * Test seam for storage failures that must prevent a publish rather than drop an attachment.
     */
    public RoomSyncStore(
            @NonNull Context context,
            @NonNull AppDatabase database,
            @NonNull PreferenceHelper preferenceHelper,
            @NonNull AttachmentResolver attachmentResolver,
            @NonNull AttachmentHasher attachmentHasher) {
        this(
                context,
                database,
                preferenceHelper,
                attachmentResolver,
                attachmentHasher,
                record -> {});
    }

    /** Test seam used to prove that Room rolls back a partially applied remote snapshot. */
    public RoomSyncStore(
            @NonNull Context context,
            @NonNull AppDatabase database,
            @NonNull PreferenceHelper preferenceHelper,
            @NonNull AttachmentResolver attachmentResolver,
            @NonNull AttachmentHasher attachmentHasher,
            @NonNull TransactionFailureInjector transactionFailureInjector) {
        this.database = database;
        this.preferenceHelper = preferenceHelper;
        this.context = context.getApplicationContext();
        this.attachmentResolver = attachmentResolver;
        this.attachmentHasher = attachmentHasher;
        this.transactionFailureInjector = transactionFailureInjector;
        this.preferences =
                context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Creates the singleton preferences metadata row.
     *
     * <p>This used to run from the constructor, which meant every caller hit Room on whatever
     * thread it happened to construct the store on; on the main thread Room throws. Seeding is now
     * deferred to the operations that already run in the background.
     */
    private void ensureSeeded() throws IOException {
        if (seeded) {
            return;
        }
        database.syncMetadataDao()
                .insertIfAbsent(
                        new SyncMetadataEntity(
                                SyncMetadata.RECORD_TYPE_PREFERENCES,
                                0,
                                PREFERENCES_STABLE_ID,
                                0L,
                                null));
        recoverPendingPreferences();
        seeded = true;
    }

    @NonNull
    @Override
    public SyncSnapshot readSnapshot() throws IOException {
        return buildSnapshot().requireSnapshot();
    }

    /**
     * Builds a local snapshot without ever treating an unresolved attachment as absent.
     *
     * <p>Returning an incomplete result leaves the database and the note attachment JSON exactly as
     * they were. {@link SyncService} refuses to publish such a result before it talks to Drive.
     */
    @NonNull
    @Override
    public SnapshotBuildResult buildSnapshot() throws IOException {
        ensureSeeded();
        List<SyncRecord> records = new ArrayList<>();
        List<SnapshotProblem> problems = new ArrayList<>();
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
            JsonObject payload = payload(metadata, problems);
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
        SyncSnapshot snapshot = new SyncSnapshot(records);
        return problems.isEmpty()
                ? SnapshotBuildResult.publishable(snapshot)
                : SnapshotBuildResult.incomplete(snapshot, problems);
    }

    @Override
    public void applySnapshot(
            @NonNull SyncSnapshot snapshot, @NonNull List<SyncMergeResult.Conflict> conflicts)
            throws IOException {
        applySnapshotInternal(snapshot, conflicts, null);
    }

    @Override
    public void applySnapshot(
            @NonNull SyncSnapshot snapshot,
            @NonNull List<SyncMergeResult.Conflict> conflicts,
            @NonNull SyncState finalState)
            throws IOException {
        applySnapshotInternal(snapshot, conflicts, finalState);
    }

    private void applySnapshotInternal(
            @NonNull SyncSnapshot snapshot,
            @NonNull List<SyncMergeResult.Conflict> conflicts,
            @Nullable SyncState finalState)
            throws IOException {
        PreferencesBackup stagedPreferences = selectedPreferences(snapshot);
        String stagedPreferencesJson =
                stagedPreferences == null ? null : gson.toJson(stagedPreferences);
        String stagedPreferencesTarget =
                stagedPreferences == null ? "" : preferencesDigest(stagedPreferences);
        String preferencesBaseline = stagedPreferences == null ? "" : livePreferencesDigest();
        SyncRecord preferencesRecord =
                snapshot.find(SyncRecord.Type.PREFERENCES, PREFERENCES_STABLE_ID);
        long stagedPreferencesUpdatedAt =
                preferencesRecord == null ? 0L : preferencesRecord.getUpdatedAt().toEpochMilli();
        boolean deferFinalState = stagedPreferences != null && finalState != null;
        try {
            database.runInTransaction(
                    () -> {
                        try {
                            Map<String, SyncMetadataEntity> byStableId = new HashMap<>();
                            for (SyncMetadataEntity metadata :
                                    database.syncMetadataDao().getAll()) {
                                byStableId.put(
                                        metadata.recordType + ":" + metadata.stableId, metadata);
                            }
                            for (SyncRecord record : snapshot.getRecords()) {
                                SyncMetadataEntity metadata =
                                        byStableId.get(
                                                record.getType().getWireValue()
                                                        + ":"
                                                        + record.getId());
                                if (metadata == null && !record.isTombstone()) {
                                    long localId = insertRemoteRecord(record);
                                    if (localId >= 0) {
                                        database.syncMetadataDao()
                                                .insertIfAbsent(
                                                        new SyncMetadataEntity(
                                                                record.getType().getWireValue(),
                                                                localId,
                                                                record.getId(),
                                                                record.getUpdatedAt()
                                                                        .toEpochMilli(),
                                                                null));
                                    }
                                    transactionFailureInjector.afterRecordApplied(record);
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
                                    transactionFailureInjector.afterRecordApplied(record);
                                    continue;
                                }
                                applyPayload(metadata, record.getPayload());
                                database.syncMetadataDao()
                                        .setVersion(
                                                metadata.recordType,
                                                metadata.localId,
                                                record.getUpdatedAt().toEpochMilli(),
                                                null);
                                transactionFailureInjector.afterRecordApplied(record);
                            }
                            persistConflicts(conflicts);
                            if (stagedPreferencesJson != null) {
                                database.syncPendingPreferencesDao()
                                        .upsert(
                                                new SyncPendingPreferencesEntity(
                                                        1,
                                                        stagedPreferencesJson,
                                                        stagedPreferencesTarget,
                                                        preferencesBaseline,
                                                        stagedPreferencesUpdatedAt,
                                                        false));
                            }
                            if (finalState != null && !deferFinalState) {
                                database.syncStateDao().upsert(toEntity(finalState));
                            }
                        } catch (IOException error) {
                            throw new SyncRuntimeException(error);
                        }
                    });
        } catch (SyncRuntimeException error) {
            throw error.ioException;
        }
        if (stagedPreferences != null) {
            // The journal is only dropped once the adapter reports a durable commit; a failure
            // here leaves it in place for recoverPendingPreferences and keeps the sync state
            // retryable rather than claiming success.
            commitPendingPreferences(stagedPreferences, stagedPreferencesTarget);
            database.runInTransaction(
                    () -> {
                        database.syncPendingPreferencesDao().clear();
                        if (finalState != null)
                            database.syncStateDao().upsert(toEntity(finalState));
                    });
        }
        pruneAttachmentCache(snapshot);
    }

    /**
     * Drops cached blobs nothing can still need.
     *
     * <p>Runs only after the snapshot, its conflicts and any preference journal have all been
     * committed, so "still needed" is answered from durable state rather than from work in
     * progress. A blob survives if the applied snapshot references it or if any unresolved conflict
     * does — a losing version the user has not chosen between yet is exactly the case where
     * deleting the bytes would be unrecoverable.
     *
     * <p>Best effort by design: this is a space optimization, and correctness must not depend on it
     * running, or on it finishing.
     */
    private void pruneAttachmentCache(@NonNull SyncSnapshot applied) {
        try {
            LinkedHashSet<String> required = new LinkedHashSet<>(getAttachmentHashes(applied));
            for (SyncConflictEntity conflict : database.syncConflictDao().getUnresolved()) {
                collectConflictAttachmentHashes(conflict.winnerJson, required);
                collectConflictAttachmentHashes(conflict.loserJson, required);
            }
            File dir = new File(context.getFilesDir(), "sync-attachments");
            File[] cached = dir.listFiles();
            if (cached == null) {
                return;
            }
            for (File file : cached) {
                String name = file.getName();
                if (!file.isFile() || !name.matches("[0-9a-f]{64}") || required.contains(name)) {
                    continue;
                }
                if (!file.delete()) {
                    Log.w(TAG, "Could not remove the unreferenced cached blob " + name);
                }
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Skipping attachment cache cleanup", error);
        }
    }

    /** Adds every content hash a stored conflict version references. */
    private void collectConflictAttachmentHashes(
            @Nullable String recordJson, @NonNull LinkedHashSet<String> into) {
        if (recordJson == null || recordJson.isEmpty()) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(recordJson).getAsJsonObject();
            JsonObject payload = root.getAsJsonObject("payload");
            if (payload == null) {
                return;
            }
            JsonArray manifest = payload.getAsJsonArray("attachmentsManifest");
            if (manifest != null) {
                for (JsonElement element : manifest) {
                    if (!element.isJsonObject()) continue;
                    JsonObject entry = element.getAsJsonObject();
                    if (entry.has("sha256")) into.add(entry.get("sha256").getAsString());
                }
            }
            JsonArray hashes = payload.getAsJsonArray("attachmentHashes");
            if (hashes != null) {
                for (JsonElement element : hashes) into.add(element.getAsString());
            }
        } catch (RuntimeException unreadable) {
            // An unreadable conflict row must never authorize a deletion, so fail closed by
            // keeping everything: the caller only removes blobs nothing claimed.
            throw unreadable;
        }
    }

    @Nullable
    private PreferencesBackup selectedPreferences(@NonNull SyncSnapshot snapshot)
            throws IOException {
        SyncRecord record = snapshot.find(SyncRecord.Type.PREFERENCES, PREFERENCES_STABLE_ID);
        if (record == null || record.isTombstone()) return null;
        try {
            PreferencesBackup parsed = gson.fromJson(record.getPayload(), PreferencesBackup.class);
            if (parsed == null || !parsed.isCreated()) {
                throw new IOException("Sync preferences payload is invalid");
            }
            return parsed;
        } catch (RuntimeException error) {
            throw new IOException("Sync preferences payload is invalid", error);
        }
    }

    /**
     * Completes, discards or quarantines a journal left behind by an earlier attempt.
     *
     * <p>Three outcomes, decided from the two digests rather than applied blindly:
     *
     * <ul>
     *   <li>the live preferences already match the target — the write did land, clear the journal;
     *   <li>they still match the baseline — nothing has changed since, so replay is safe;
     *   <li>they match neither — the user has changed these settings since, and their newer choice
     *       outranks a stale remote payload, so the journal is dropped without being applied.
     * </ul>
     *
     * <p>An unreadable payload is quarantined instead of thrown: this runs from {@code
     * ensureSeeded}, which gates snapshot building and the status read alike, so throwing made one
     * bad row disable sync permanently.
     */
    private void recoverPendingPreferences() throws IOException {
        SyncPendingPreferencesEntity pending = database.syncPendingPreferencesDao().get();

        PreferencesBackup backup = null;
        if (pending != null) {
            try {
                backup = gson.fromJson(pending.payloadJson, PreferencesBackup.class);
            } catch (RuntimeException unreadable) {
                backup = null;
            }
            if (backup != null && !backup.isCreated()) {
                backup = null;
            }
        }

        PendingPreferencesDecision.Action action =
                PendingPreferencesDecision.decide(
                        pending != null,
                        backup != null,
                        pending == null ? null : pending.targetHash,
                        pending == null ? null : pending.baselineHash,
                        livePreferencesDigest());

        switch (action) {
            case NOTHING:
                return;
            case QUARANTINE:
                Log.w(TAG, "Quarantining an unreadable pending preferences journal");
                database.runInTransaction(() -> database.syncPendingPreferencesDao().quarantine());
                return;
            case CLEAR_ALREADY_APPLIED:
                database.runInTransaction(() -> database.syncPendingPreferencesDao().clear());
                return;
            case DISCARD_STALE:
                Log.w(
                        TAG,
                        "Discarding a stale pending preferences journal; local settings changed");
                database.runInTransaction(() -> database.syncPendingPreferencesDao().clear());
                return;
            case REPLAY:
            default:
                String target =
                        pending.targetHash == null || pending.targetHash.isEmpty()
                                ? preferencesDigest(backup)
                                : pending.targetHash;
                commitPendingPreferences(backup, target);
                database.runInTransaction(() -> database.syncPendingPreferencesDao().clear());
        }
    }

    /**
     * Applies one journaled preferences payload, failing loudly when it is not durable.
     *
     * @param expectedDigest digest the live preferences must show afterwards.
     */
    private void commitPendingPreferences(
            @NonNull PreferencesBackup backup, @NonNull String expectedDigest) throws IOException {
        boolean committed;
        try {
            committed = preferenceHelper.commitListPreferences(backup);
        } catch (RuntimeException error) {
            throw new IOException("Could not commit synchronized preferences", error);
        }
        if (!committed) {
            throw new IOException("Could not commit synchronized preferences");
        }
        // The digest doubles as the snapshot-build baseline, so recording it here keeps the next
        // build from treating a freshly received version as a local edit.
        preferences.edit().putString(PREFERENCES_HASH, expectedDigest).commit();
    }

    /**
     * Records that the live preferences diverged from the last value sync knows about.
     *
     * <p>SharedPreferences has no mutation hook and the settings screens write it directly, so a
     * local edit can only be noticed by comparing digests here. It now fires only for a genuine
     * local change: the apply and conflict-resolution paths record the digest they committed, so a
     * version received from another device is no longer mistaken for a local edit and cannot become
     * artificially newer than the version it was received from.
     */
    private void noteLocalPreferenceEdit(
            @NonNull SyncMetadataEntity metadata, @Nullable PreferencesBackup live) {
        String digest = preferencesDigest(live);
        String baseline = preferences.getString(PREFERENCES_HASH, null);
        if (digest.equals(baseline)) {
            return;
        }
        // No baseline at all means sync has never seen these settings; treating them as a local
        // edit is the conservative reading, because the alternative silently loses a fresh
        // install's configuration to an older version already on Drive.
        database.syncMetadataDao()
                .touch(metadata.recordType, metadata.localId, System.currentTimeMillis());
        preferences.edit().putString(PREFERENCES_HASH, digest).commit();
    }

    /** Digest of the preferences currently visible to the app. */
    @NonNull
    private String livePreferencesDigest() {
        return preferencesDigest(preferenceHelper.getListPreferences());
    }

    /** Stable digest of one preferences payload, used for the journal and the build baseline. */
    @NonNull
    private String preferencesDigest(@Nullable PreferencesBackup backup) {
        String json = backup == null ? "" : gson.toJson(backup);
        try {
            return sha256(new java.io.ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException impossible) {
            throw new IllegalStateException("Could not digest preferences", impossible);
        }
    }

    /** Reads a preferences payload, refusing anything that is not a usable settings snapshot. */
    @NonNull
    private PreferencesBackup requirePreferences(@NonNull JsonObject payload) throws IOException {
        try {
            PreferencesBackup parsed = gson.fromJson(payload, PreferencesBackup.class);
            if (parsed == null || !parsed.isCreated()) {
                throw new IOException("Sync preferences payload is invalid");
            }
            return parsed;
        } catch (RuntimeException error) {
            throw new IOException("Sync preferences payload is invalid", error);
        }
    }

    @Nullable
    private JsonObject payload(
            SyncMetadataEntity metadata, @NonNull List<SnapshotProblem> snapshotProblems) {
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
            noteLocalPreferenceEdit(metadata, (PreferencesBackup) value);
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
        if ("note".equals(metadata.recordType)
                && !addAttachmentMetadata(result, metadata, snapshotProblems)) {
            return null;
        }
        // Runs last: the blocks above still need the local categoryId and attachment paths.
        SyncMetadata.stripDeviceLocalFields(metadata.recordType, result);
        return result;
    }

    private void applyPayload(SyncMetadataEntity metadata, JsonObject payload) throws IOException {
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
            // SharedPreferences is outside Room. applySnapshotInternal journals and commits this
            // payload only after the Room transaction succeeds.
        }
    }

    private long insertRemoteRecord(SyncRecord record) throws IOException {
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

    /**
     * Drops every trace of the account being disconnected.
     *
     * <p>Record identity in {@code sync_metadata} is deliberately kept: it is local, and discarding
     * it would make the whole library look brand new to the next account. What goes is the sync
     * status, the conflict queue and the blobs downloaded from the disconnected account's Drive.
     *
     * <p>Clearing the status also repairs a dead end: the Backup screen decided whether to ask for
     * first-sync consent from {@code lastSuccessfulSyncAt}, which survived a sign-out, while {@code
     * SyncCoordinator} gated the sync on a preference the sign-out reset. The dialog was skipped
     * and the sync refused, with no way to reach the consent again.
     */
    public void clearAfterDisconnect() {
        database.runInTransaction(
                () -> {
                    database.syncStateDao().clear();
                    database.syncConflictDao().clearAll();
                });
        preferences.edit().remove(PREFERENCES_HASH).remove(LEGACY_STATE).apply();
        localAttachments.clear();
        deleteAttachmentCache();
    }

    /** Removes the download cache only; the notes' own attachment folders are untouched. */
    private void deleteAttachmentCache() {
        File dir = new File(context.getFilesDir(), "sync-attachments");
        File[] cached = dir.listFiles();
        if (cached == null) {
            return;
        }
        for (File file : cached) {
            if (file.isFile() && !file.delete()) {
                Log.w(TAG, "Could not remove cached attachment " + file.getName());
            }
        }
    }

    public List<SyncConflictEntity> getUnresolvedConflicts() {
        return database.syncConflictDao().getUnresolved();
    }

    public void resolveConflict(long conflictId, @NonNull SyncResolution resolution)
            throws IOException {
        if (resolution == SyncResolution.PENDING) return;
        SyncConflictEntity pending = database.syncConflictDao().getById(conflictId);
        if (pending == null || pending.resolved) return;

        // Resolution is a user-visible mutation. Verify and pin the selected version before its
        // conflict row can be marked resolved; a missing blob must leave both the note and the
        // conflict untouched, including when the winner happens to already be visible in Room.
        SyncRecord selected = selectRecordForResolution(pending, resolution);
        pinResolvedConflictAttachments(selected);

        if (SyncMetadata.RECORD_TYPE_PREFERENCES.equals(pending.recordType)
                && !selected.isTombstone()) {
            resolvePreferencesConflict(conflictId, resolution, selected);
            return;
        }

        try {
            database.runInTransaction(
                    () -> {
                        SyncConflictEntity conflict =
                                database.syncConflictDao().getById(conflictId);
                        if (conflict == null || conflict.resolved) return;

                        long resolvedAt = System.currentTimeMillis();
                        try {
                            applyResolvedRecord(conflict, resolution, resolvedAt);
                        } catch (IOException error) {
                            throw new SyncRuntimeException(error);
                        }
                        database.syncConflictDao()
                                .markResolved(conflictId, resolution.name(), resolvedAt);
                    });
        } catch (SyncRuntimeException error) {
            throw error.ioException;
        }
    }

    /**
     * Applies a chosen preferences version through the same journal a snapshot apply uses.
     *
     * <p>The old path called {@code applyPayload}, whose preferences branch is a no-op, then marked
     * the conflict resolved and bumped the record's timestamp. Nothing was written, the conflict
     * left the queue, and the untouched local values — now carrying the newest timestamp —
     * overwrote the chosen version on every other device at the next sync.
     *
     * <p>The version bump is deliberately in the second phase. Bumping it before the adapter has
     * committed would, on a failed write, publish the value the user rejected under a fresh
     * timestamp; leaving it until after the commit means a failure changes nothing at all.
     */
    private void resolvePreferencesConflict(
            long conflictId, @NonNull SyncResolution resolution, @NonNull SyncRecord selected)
            throws IOException {
        PreferencesBackup chosen = requirePreferences(selected.getPayload());
        String target = preferencesDigest(chosen);
        String baseline = livePreferencesDigest();
        String payloadJson = gson.toJson(chosen);
        long recordUpdatedAt = selected.getUpdatedAt().toEpochMilli();

        database.runInTransaction(
                () -> {
                    SyncConflictEntity conflict = database.syncConflictDao().getById(conflictId);
                    if (conflict == null || conflict.resolved) return;
                    database.syncPendingPreferencesDao()
                            .upsert(
                                    new SyncPendingPreferencesEntity(
                                            1,
                                            payloadJson,
                                            target,
                                            baseline,
                                            recordUpdatedAt,
                                            false));
                });

        // Throws when the write is not durable, leaving the journal in place and the conflict
        // unresolved so the user can try again.
        commitPendingPreferences(chosen, target);

        try {
            database.runInTransaction(
                    () -> {
                        SyncConflictEntity conflict =
                                database.syncConflictDao().getById(conflictId);
                        if (conflict == null || conflict.resolved) return;
                        database.syncPendingPreferencesDao().clear();
                        long resolvedAt = System.currentTimeMillis();
                        SyncMetadataEntity metadata =
                                database.syncMetadataDao()
                                        .getByStableId(conflict.recordType, conflict.stableId);
                        if (metadata != null) {
                            database.syncMetadataDao()
                                    .setVersion(
                                            conflict.recordType,
                                            metadata.localId,
                                            Math.max(resolvedAt, metadata.updatedAt + 1L),
                                            null);
                        }
                        database.syncConflictDao()
                                .markResolved(conflictId, resolution.name(), resolvedAt);
                    });
        } catch (RuntimeException error) {
            throw new IOException("Could not finalize the resolved preferences conflict", error);
        }
    }

    private void pinResolvedConflictAttachments(@NonNull SyncRecord selected) throws IOException {
        if (selected.isTombstone() || selected.getType() != SyncRecord.Type.NOTE) return;
        JsonArray manifest = selected.getPayload().getAsJsonArray("attachmentsManifest");
        if (manifest == null) return;
        for (JsonElement element : manifest) {
            if (!element.isJsonObject()) {
                throw new IOException("Attachment manifest entry is invalid");
            }
            SyncBundleCodec.AttachmentManifestEntry entry =
                    SyncBundleCodec.AttachmentManifestEntry.fromJson(element.getAsJsonObject());
            File source = resolveLocalAttachment(entry.sha256);
            if (source == null || !isVerifiedAttachmentFile(source, entry.sha256, entry.size)) {
                throw new IOException(
                        "Required conflict attachment is unavailable: " + entry.sha256);
            }
            File cache = attachmentFile(entry.sha256);
            if (!isVerifiedAttachmentFile(cache, entry.sha256, entry.size)) {
                copyVerifiedAttachment(source, cache, entry.sha256, entry.size);
            }
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
                            conflictVersionPairHash(conflict),
                            conflict.getWinnerSource().name(),
                            conflict.getLoserSource().name(),
                            conflict.getWinnerVersionId(),
                            conflict.getLoserVersionId(),
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
        database.syncConflictDao().insertIgnoringDuplicates(rows);
    }

    @NonNull
    private static String conflictVersionPairHash(@NonNull SyncMergeResult.Conflict conflict) {
        String source =
                conflict.getType().getWireValue()
                        + "\n"
                        + conflict.getId()
                        + "\n"
                        + conflict.getWinner().canonicalSerializedPayload()
                        + "\n"
                        + conflict.getLoser().canonicalSerializedPayload();
        try {
            return sha256(
                    new java.io.ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException impossible) {
            throw new IllegalStateException("Could not hash sync conflict identity", impossible);
        }
    }

    /** True when {@code resolution} names the version the merge selected. */
    private static boolean keepsWinner(
            @NonNull SyncConflictEntity conflict, @NonNull SyncResolution resolution) {
        if (resolution == SyncResolution.KEEP_ALTERNATIVE) {
            return false;
        }
        if (resolution == SyncResolution.KEEP_WINNER) {
            return true;
        }
        String wanted = resolution == SyncResolution.KEEP_LOCAL ? "LOCAL" : "REMOTE";
        if (wanted.equals(conflict.winnerSource)) {
            return true;
        }
        // Only select the alternative when it genuinely is the endpoint the user named.
        return !wanted.equals(conflict.loserSource);
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

    /**
     * Picks the version the user chose, addressing it by position rather than by origin.
     *
     * <p>The deprecated endpoint-addressed values are still mapped, because a stored row may carry
     * one, but they can no longer silently select the wrong side: when neither version came from
     * the named endpoint — the Drive-vs-Drive case — the deterministic winner is kept rather than
     * the alternative, which is what the old expression did by accident.
     */
    @NonNull
    private static SyncRecord selectRecordForResolution(
            @NonNull SyncConflictEntity conflict, @NonNull SyncResolution resolution)
            throws IOException {
        String selectedJson =
                keepsWinner(conflict, resolution) ? conflict.winnerJson : conflict.loserJson;
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
    public java.util.Set<String> getResolvedAlternativeIds() {
        return new LinkedHashSet<>(database.syncConflictDao().getResolvedVersionIds());
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
        return resolveLocalAttachment(sha256) != null;
    }

    @NonNull
    @Override
    public InputStream readAttachment(@NonNull String sha256) throws IOException {
        File source = resolveLocalAttachment(sha256);
        if (source == null) {
            throw new java.io.FileNotFoundException("No local attachment for " + sha256);
        }
        return new FileInputStream(source);
    }

    /**
     * Finds a blob this device already holds, in the download cache or in a note's own folder.
     *
     * <p>Only the cache directory used to be consulted, and nothing but the download path ever
     * wrote to it. On the device that owns an attachment the lookup therefore returned false,
     * {@code SyncService} asked the backend for a blob nobody had uploaded yet, and the sync failed
     * with "Required attachment is unavailable". Since the upload branch is reachable only when
     * this returns true, that failure was permanent for any account holding a single attachment.
     *
     * <p>The note folders are indexed while the snapshot is built rather than copied into the
     * cache, so a large attachment set is not stored twice.
     */
    @Nullable
    private File resolveLocalAttachment(@NonNull String sha256) {
        File cached = attachmentFile(sha256);
        if (cached.isFile()) {
            return cached;
        }
        File owned = localAttachments.get(sha256);
        return owned != null && owned.isFile() ? owned : null;
    }

    @Override
    public void writeAttachment(
            @NonNull String sha256, long sizeBytes, @NonNull InputStream content)
            throws IOException {
        File target = attachmentFile(sha256);
        File temp = new File(target.getParentFile(), sha256 + ".tmp");
        // Written to a temporary file and only then renamed, so a stream that fails part-way —
        // including a checksum mismatch, which SyncService raises at end of stream, inside this
        // very loop — never leaves a half-written blob under the hash's name.
        try (InputStream in = content;
                FileOutputStream out = new FileOutputStream(temp)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        } catch (IOException error) {
            if (temp.exists() && !temp.delete()) {
                Log.w(TAG, "Could not remove the partial attachment " + temp.getName());
            }
            throw error;
        }
        if (!temp.renameTo(target)) throw new IOException("Cannot store attachment");
    }

    private File attachmentFile(String sha256) {
        File dir = new File(context.getFilesDir(), "sync-attachments");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, sha256);
    }

    private boolean addAttachmentMetadata(
            JsonObject payload,
            SyncMetadataEntity metadata,
            @NonNull List<SnapshotProblem> snapshotProblems) {
        String json =
                payload.has("h") && !payload.get("h").isJsonNull()
                        ? payload.get("h").getAsString()
                        : null;
        if (json == null || json.trim().isEmpty()) return true;
        JsonArray attachments;
        try {
            attachments = JsonParser.parseString(json).getAsJsonArray();
        } catch (RuntimeException error) {
            addSnapshotProblem(
                    snapshotProblems, SnapshotProblem.Kind.INVALID_ATTACHMENT_METADATA, metadata);
            return false;
        }

        JsonArray manifest = new JsonArray();
        JsonArray hashes = new JsonArray();
        JsonObject names = new JsonObject();
        boolean complete = true;
        for (int attachmentIndex = 0; attachmentIndex < attachments.size(); attachmentIndex++) {
            JsonElement element = attachments.get(attachmentIndex);
            if (!element.isJsonObject()) {
                addSnapshotProblem(
                        snapshotProblems,
                        SnapshotProblem.Kind.INVALID_ATTACHMENT_METADATA,
                        metadata);
                complete = false;
                continue;
            }
            EditorAttachment attachment;
            try {
                attachment = gson.fromJson(element, EditorAttachment.class);
            } catch (RuntimeException error) {
                addSnapshotProblem(
                        snapshotProblems,
                        SnapshotProblem.Kind.INVALID_ATTACHMENT_METADATA,
                        metadata);
                complete = false;
                continue;
            }
            if (attachment == null || attachment.url == null || attachment.url.trim().isEmpty()) {
                addSnapshotProblem(
                        snapshotProblems,
                        SnapshotProblem.Kind.INVALID_ATTACHMENT_METADATA,
                        metadata);
                complete = false;
                continue;
            }
            File file;
            try {
                file = attachmentResolver.resolve(context, attachment);
            } catch (RuntimeException error) {
                addSnapshotProblem(
                        snapshotProblems,
                        SnapshotProblem.Kind.INVALID_ATTACHMENT_METADATA,
                        metadata);
                complete = false;
                continue;
            }
            if (file == null || !file.isFile()) {
                addSnapshotProblem(
                        snapshotProblems, SnapshotProblem.Kind.MISSING_ATTACHMENT, metadata);
                complete = false;
                continue;
            }
            if (!file.canRead()) {
                addSnapshotProblem(
                        snapshotProblems, SnapshotProblem.Kind.UNREADABLE_ATTACHMENT, metadata);
                complete = false;
                continue;
            }
            String hash;
            try {
                hash = attachmentHasher.sha256(file);
            } catch (IOException error) {
                addSnapshotProblem(
                        snapshotProblems, SnapshotProblem.Kind.ATTACHMENT_HASH_FAILED, metadata);
                complete = false;
                continue;
            }
            localAttachments.put(hash, file);
            String displayName =
                    attachment.name == null || attachment.name.trim().isEmpty()
                            ? file.getName()
                            : attachment.name.trim();
            String logicalId = attachment.id;
            if (logicalId == null || !logicalId.matches("[0-9a-fA-F-]{36}")) {
                // Existing editor data predates logical attachment IDs. Deriving from the stable
                // note, source URL and position keeps the migration deterministic while allowing
                // equal-content references to remain distinct logical attachments.
                logicalId =
                        UUID.nameUUIDFromBytes(
                                        (metadata.stableId
                                                        + "\n"
                                                        + attachmentIndex
                                                        + "\n"
                                                        + attachment.url
                                                        + "\n"
                                                        + displayName)
                                                .getBytes(StandardCharsets.UTF_8))
                                .toString();
            }
            hashes.add(hash);
            names.addProperty(logicalId, displayName);

            JsonObject manifestEntry = new JsonObject();
            manifestEntry.addProperty("id", logicalId);
            manifestEntry.addProperty("sha256", hash);
            manifestEntry.addProperty("mimeType", detectMimeType(file, attachment, displayName));
            manifestEntry.addProperty("size", file.length());
            manifestEntry.addProperty("path", "attachments/" + hash);
            manifestEntry.addProperty("displayName", displayName);
            manifest.add(manifestEntry);
        }
        if (!complete) return false;
        payload.add("attachmentsManifest", manifest);
        payload.add("attachmentHashes", hashes);
        payload.add("attachmentNames", names);
        return true;
    }

    private static void addSnapshotProblem(
            @NonNull List<SnapshotProblem> problems,
            @NonNull SnapshotProblem.Kind kind,
            @NonNull SyncMetadataEntity metadata) {
        problems.add(new SnapshotProblem(kind, metadata.recordType, metadata.stableId));
    }

    /**
     * Materializes every attachment before changing the Room row. Targets use the immutable
     * logical-ID/content-ID pair rather than a display name, so a rollback can leave only harmless
     * new files and can never alter bytes addressed by the pre-transaction note.
     */
    private void restoreAttachments(Note note, JsonObject payload) throws IOException {
        JsonArray manifest = payload.getAsJsonArray("attachmentsManifest");
        if (manifest == null) {
            if (payload.has("attachmentHashes")) {
                throw new IOException("Attachment manifest is missing");
            }
            return;
        }
        File folder = AttachmentStorage.noteFolder(context, note.getId());
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("Could not create attachment folder");
        }
        JsonArray restored = new JsonArray();
        for (JsonElement element : manifest) {
            if (!element.isJsonObject()) {
                throw new IOException("Attachment manifest entry is invalid");
            }
            SyncBundleCodec.AttachmentManifestEntry entry;
            try {
                entry = SyncBundleCodec.AttachmentManifestEntry.fromJson(element.getAsJsonObject());
            } catch (RuntimeException error) {
                throw new IOException("Attachment manifest entry is invalid", error);
            }
            if (entry.id == null
                    || entry.sha256 == null
                    || !entry.id.matches("[0-9a-fA-F-]{36}")
                    || !entry.sha256.matches("[0-9a-f]{64}")
                    || entry.size < 0L) {
                throw new IOException("Attachment manifest entry is invalid");
            }
            String displayName = entry.displayName == null ? entry.id : entry.displayName;
            if (!isSafeAttachmentName(displayName)) {
                throw new IOException("Attachment display name is invalid");
            }
            File source = resolveLocalAttachment(entry.sha256);
            if (source == null || !source.isFile() || !source.canRead()) {
                throw new IOException("Required attachment is unavailable: " + entry.sha256);
            }
            File target = new File(folder, entry.id + "-" + entry.sha256);
            if (!isVerifiedAttachmentFile(target, entry.sha256, entry.size)) {
                // A corrupted old target may still be referenced by the pre-sync note. Preserve it
                // and use a fresh opaque immutable name for this candidate state instead.
                if (target.exists()) {
                    target =
                            new File(
                                    folder,
                                    entry.id + "-" + entry.sha256 + "-" + UUID.randomUUID());
                }
                copyVerifiedAttachment(source, target, entry.sha256, entry.size);
            }
            if (note.getId() <= 0) {
                throw new IOException("Cannot restore attachments for an unsaved note");
            }
            JsonObject attachment = new JsonObject();
            // Canonical editorjs:// form, the only shape EditorAttachmentsWebViewClient serves.
            // Writing file:// here left every synced attachment unrenderable on the receiver.
            attachment.addProperty("url", AttachmentStorage.urlFor(note.getId(), target.getName()));
            attachment.addProperty("name", displayName);
            attachment.addProperty("id", entry.id);
            restored.add(attachment);
        }
        note.setAttachments(gson.toJson(restored));
    }

    private static boolean isVerifiedAttachmentFile(
            @NonNull File file, @NonNull String expectedHash, long expectedSize)
            throws IOException {
        return file.isFile() && file.length() == expectedSize && expectedHash.equals(sha256(file));
    }

    private static void copyVerifiedAttachment(
            @NonNull File source,
            @NonNull File target,
            @NonNull String expectedHash,
            long expectedSize)
            throws IOException {
        File temporary =
                new File(target.getParentFile(), target.getName() + ".tmp-" + UUID.randomUUID());
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IOException("SHA-256 is unavailable", error);
        }
        long copied = 0L;
        try (InputStream in = new FileInputStream(source);
                OutputStream out = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                copied += read;
                if (copied > expectedSize) {
                    throw new AttachmentIntegrityException("Attachment exceeds its declared size");
                }
            }
        } catch (IOException failure) {
            if (temporary.exists() && !temporary.delete()) {
                Log.w(TAG, "Could not remove failed staged attachment");
            }
            throw failure;
        }
        StringBuilder hash = new StringBuilder(64);
        for (byte value : digest.digest()) hash.append(String.format("%02x", value & 0xff));
        String actual = hash.toString();
        if (copied != expectedSize || !expectedHash.equals(actual)) {
            if (!temporary.delete()) Log.w(TAG, "Could not remove invalid staged attachment");
            throw new AttachmentIntegrityException(
                    "Attachment checksum does not match sync metadata");
        }
        if (!temporary.renameTo(target)) {
            if (!temporary.delete()) Log.w(TAG, "Could not remove uncommitted staged attachment");
            throw new IOException("Could not finalize staged attachment");
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

    private static String sha256(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IOException("SHA-256 is unavailable", error);
        }
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
    private static String sha256(@NonNull InputStream input) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IOException("SHA-256 is unavailable", error);
        }
        try (InputStream in = input) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) hex.append(String.format("%02x", value & 0xff));
        return hex.toString();
    }

    /** Resolves a serialized note attachment to its app-private file. */
    public interface AttachmentResolver {
        @Nullable
        File resolve(@NonNull Context context, @NonNull EditorAttachment attachment);
    }

    /** Hashes an attachment after it has passed basic filesystem checks. */
    public interface AttachmentHasher {
        @NonNull
        String sha256(@NonNull File file) throws IOException;
    }

    /** Throws from tests after a Room mutation but before the enclosing transaction commits. */
    public interface TransactionFailureInjector {
        void afterRecordApplied(@NonNull SyncRecord record);
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
        ensureSeeded();
        SyncStateEntity entity = database.syncStateDao().get();
        if (entity == null) {
            SyncState legacy = readLegacyState();
            if (legacy.getStatus() != SyncState.Status.IDLE) {
                database.syncStateDao().upsert(toEntity(legacy));
            }
            return legacy;
        }
        try {
            SyncState.Status status = SyncState.Status.valueOf(entity.status);
            String backend = entity.backendIdentifier;
            if (status == SyncState.Status.IDLE) return SyncState.idle();
            if (backend == null || backend.trim().isEmpty()) return SyncState.idle();
            if (status == SyncState.Status.SYNCING) {
                if (entity.attemptStartedAt == null) return SyncState.idle();
                return SyncState.syncing(
                        backend,
                        Instant.ofEpochMilli(entity.attemptStartedAt),
                        entity.lastSuccessfulSyncAt == null
                                ? null
                                : Instant.ofEpochMilli(entity.lastSuccessfulSyncAt));
            }
            if (status == SyncState.Status.SUCCESS) {
                if (entity.lastSuccessfulSyncAt == null) return SyncState.idle();
                return SyncState.success(
                        backend,
                        Instant.ofEpochMilli(entity.lastSuccessfulSyncAt),
                        entity.conflictCount);
            }
            if (entity.errorMessage == null) return SyncState.idle();
            Instant last =
                    entity.lastSuccessfulSyncAt == null
                            ? null
                            : Instant.ofEpochMilli(entity.lastSuccessfulSyncAt);
            return SyncState.error(backend, last, entity.errorMessage);
        } catch (RuntimeException error) {
            return SyncState.idle();
        }
    }

    @NonNull
    private SyncState readLegacyState() {
        String json = preferences.getString(LEGACY_STATE, null);
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
            if ("SYNCING".equals(status) && value.has("attemptStartedAt")) {
                return SyncState.syncing(
                        backend,
                        Instant.parse(value.get("attemptStartedAt").getAsString()),
                        value.has("lastSuccessfulSyncAt")
                                        && !value.get("lastSuccessfulSyncAt").isJsonNull()
                                ? Instant.parse(value.get("lastSuccessfulSyncAt").getAsString())
                                : null);
            }
            return SyncState.idle();
        } catch (RuntimeException error) {
            return SyncState.idle();
        }
    }

    @Override
    public void writeState(@NonNull SyncState state) {
        database.syncStateDao().upsert(toEntity(state));
    }

    @NonNull
    private static SyncStateEntity toEntity(@NonNull SyncState state) {
        return new SyncStateEntity(
                1,
                state.getStatus().name(),
                state.getBackendIdentifier(),
                state.getLastSuccessfulSyncAt() == null
                        ? null
                        : state.getLastSuccessfulSyncAt().toEpochMilli(),
                state.getAttemptStartedAt() == null
                        ? null
                        : state.getAttemptStartedAt().toEpochMilli(),
                state.getErrorMessage(),
                state.getConflictCount());
    }
}
