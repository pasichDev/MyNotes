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
import com.pasich.mynotes.extendedEditor.attach.AttachmentUrl;
import com.pasich.mynotes.extendedEditor.attach.EditorAttachmentBlocks;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Room-backed sync store. Stable sync IDs remain separate from local integer primary keys. */
public final class RoomSyncStore implements SyncStore {
    private static final String TAG = "RoomSyncStore";
    private static final String PREFS = "sync_state";
    private static final String LEGACY_STATE = "last_state";
    private static final String PREFERENCES_HASH = "preferences_hash";
    private static final String PREFERENCES_STABLE_ID = "00000000-0000-4000-8000-000000000000";
    private static final String ATTACHMENT_CACHE_DIR = "sync-attachments";
    private static final String HASH_CACHE_FILE = "hash-cache.json";
    private static final String SHA_256 = "[0-9a-f]{64}";
    private final AppDatabase database;
    private final SharedPreferences preferences;
    private volatile boolean seeded;
    private final PreferenceHelper preferenceHelper;
    private final Context context;
    private final Gson gson = new Gson();
    private final AttachmentResolver attachmentResolver;
    private final AttachmentHasher attachmentHasher;
    private final TransactionFailureInjector transactionFailureInjector;
    private final AttachmentHashCache hashCache;

    /**
     * Content hash to the note-folder file holding it, indexed while the snapshot is built so the
     * upload path can find blobs this device owns without duplicating them into the sync cache.
     */
    private final Map<String, File> localAttachments = new ConcurrentHashMap<>();

    /** Whether every note folder has been indexed into {@link #localAttachments}. */
    private volatile boolean noteFoldersIndexed;

    /**
     * Set when an apply actually changed the visible settings, so the screen can redraw.
     *
     * <p>Theme, dynamic colour and UI scale are read when an activity is created, so a version
     * arriving from another device was stored correctly but only became visible after the user
     * navigated away and back.
     */
    private final java.util.concurrent.atomic.AtomicBoolean appliedPreferencesChange =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public RoomSyncStore(
            @NonNull Context context,
            @NonNull AppDatabase database,
            @NonNull PreferenceHelper preferenceHelper) {
        this(
                context,
                database,
                preferenceHelper,
                AttachmentStorage::resolve,
                Sha256::of,
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
        this.hashCache =
                new AttachmentHashCache(
                        new File(
                                new File(this.context.getFilesDir(), ATTACHMENT_CACHE_DIR),
                                HASH_CACHE_FILE));
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
        try {
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
                                            current == null
                                                    ? metadata.updatedAt
                                                    : current.updatedAt),
                                    payload));
                }
            }
        } finally {
            hashCache.flush();
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
        if (stagedPreferences != null && preferencesChangedSinceBuild()) {
            // The settings screens write SharedPreferences directly and nothing touches the sync
            // record for them, so the stale-record guard below cannot protect a setting changed
            // while the sync was in flight. Committing the merged version would overwrite it and
            // record its digest as the baseline, hiding the loss from the next build. Leaving the
            // live values alone means the next build sees them differ from the baseline and
            // publishes them as the local edit they are.
            Log.w(TAG, "Skipping synchronized preferences; they were edited during this sync");
            stagedPreferences = null;
        }
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
        boolean applyPreferences = stagedPreferences != null;
        try {
            database.runInTransaction(
                    () -> {
                        try {
                            Map<String, SyncMetadataEntity> byStableId = new HashMap<>();
                            for (SyncMetadataEntity metadata :
                                    database.syncMetadataDao().getAll()) {
                                byStableId.put(recordKey(metadata), metadata);
                            }
                            // Records the merge decided about but this apply left untouched. A
                            // conflict for one of them names versions that no longer describe
                            // the local record, so it must not be stored for the user to apply.
                            Set<String> skippedKeys = new HashSet<>();
                            for (SyncRecord record : snapshot.getRecords()) {
                                String key = recordKey(record);
                                SyncMetadataEntity metadata = byStableId.get(key);
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
                                if (record.getType() == SyncRecord.Type.PREFERENCES
                                        && !record.isTombstone()
                                        && !applyPreferences) {
                                    // Invalid payload or edited mid-sync: the version is not
                                    // going to be committed, so it must not be recorded as the
                                    // one this device holds either.
                                    skippedKeys.add(key);
                                    continue;
                                }
                                // The snapshot was built before Drive was read and every blob
                                // transferred, which can take minutes, and the six-hourly worker
                                // does it while the user is in the editor. If the record moved on
                                // locally since then, the merge chose between versions one of
                                // which no longer exists, so applying its result would silently
                                // drop the newer edit. Leave it; the next sync merges the real
                                // current version.
                                if (metadata.updatedAt > record.getUpdatedAt().toEpochMilli()) {
                                    Log.w(
                                            TAG,
                                            "Skipping a stale sync result for "
                                                    + metadata.recordType
                                                    + "; it was edited during this sync");
                                    skippedKeys.add(key);
                                    continue;
                                }
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
                            persistConflicts(conflicts, skippedKeys);
                            if (stagedPreferencesJson != null) {
                                database.syncPendingPreferencesDao()
                                        .upsert(
                                                new SyncPendingPreferencesEntity(
                                                        1,
                                                        stagedPreferencesJson,
                                                        stagedPreferencesTarget,
                                                        preferencesBaseline,
                                                        stagedPreferencesUpdatedAt,
                                                        false,
                                                        0L,
                                                        ""));
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

    @NonNull
    private static String recordKey(@NonNull SyncMetadataEntity metadata) {
        return metadata.recordType + ":" + metadata.stableId;
    }

    @NonNull
    private static String recordKey(@NonNull SyncRecord record) {
        return record.getType().getWireValue() + ":" + record.getId();
    }

    /**
     * Whether the live settings differ from what the snapshot was built from.
     *
     * <p>Every build records the live digest as the baseline, so a baseline that no longer matches
     * means the user changed a setting after the build and before this apply.
     */
    private boolean preferencesChangedSinceBuild() {
        String baseline = preferences.getString(PREFERENCES_HASH, null);
        return baseline != null && !baseline.equals(livePreferencesDigest());
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
     * running, or on it finishing. An unreadable conflict row throws out of the collection below,
     * which lands here and skips the whole pass: a row that cannot be read must never authorize a
     * deletion.
     */
    private void pruneAttachmentCache(@NonNull SyncSnapshot applied) {
        try {
            LinkedHashSet<String> required = new LinkedHashSet<>(getAttachmentHashes(applied));
            for (SyncConflictEntity conflict : database.syncConflictDao().getUnresolved()) {
                collectConflictAttachmentHashes(conflict.winnerJson, required);
                collectConflictAttachmentHashes(conflict.loserJson, required);
            }
            File[] cached = attachmentCacheDir().listFiles();
            if (cached == null) {
                return;
            }
            for (File file : cached) {
                String name = file.getName();
                if (!file.isFile() || !name.matches(SHA_256) || required.contains(name)) {
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
    }

    /**
     * The preferences version this apply should commit, or {@code null} when there is none.
     *
     * <p>An unusable payload is skipped rather than thrown: it came from Drive, every device sees
     * the same one, and throwing here failed every sync on every device until somebody happened to
     * change a setting locally. Skipping leaves the local record's version untouched, so the local
     * values keep winning the next merge and the bad version is replaced the moment any device
     * publishes a real one.
     */
    @Nullable
    private PreferencesBackup selectedPreferences(@NonNull SyncSnapshot snapshot) {
        SyncRecord record = snapshot.find(SyncRecord.Type.PREFERENCES, PREFERENCES_STABLE_ID);
        if (record == null || record.isTombstone()) return null;
        try {
            return requirePreferences(record.getPayload());
        } catch (IOException invalid) {
            Log.w(TAG, "Skipping an unreadable synchronized preferences payload", invalid);
            return null;
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

        String target =
                pending == null || pending.targetHash == null || pending.targetHash.isEmpty()
                        ? preferencesDigest(backup)
                        : pending.targetHash;

        switch (action) {
            case NOTHING:
                return;
            case QUARANTINE:
                Log.w(TAG, "Quarantining an unreadable pending preferences journal");
                database.runInTransaction(() -> database.syncPendingPreferencesDao().quarantine());
                return;
            case CLEAR_ALREADY_APPLIED:
                // The values landed but the digest may not have: commitPendingPreferences writes
                // it after the adapter returns. Without it the next snapshot build reads the
                // stale baseline, calls this a local edit and touches the record to now, which
                // lets an unchanged copy outrank a genuine edit made on another device.
                preferences.edit().putString(PREFERENCES_HASH, target).commit();
                finishJournal(pending);
                return;
            case DISCARD_STALE:
                Log.w(
                        TAG,
                        "Discarding a stale pending preferences journal; local settings changed");
                database.runInTransaction(() -> database.syncPendingPreferencesDao().clear());
                return;
            case REPLAY:
            default:
                commitPendingPreferences(backup, target);
                finishJournal(pending);
        }
    }

    /** Clears the journal, completing the conflict bookkeeping when it names one. */
    private void finishJournal(@NonNull SyncPendingPreferencesEntity pending) {
        if (pending.conflictId > 0) {
            finalizeResolvedPreferencesConflict(pending.conflictId, pending.conflictResolution);
            return;
        }
        database.runInTransaction(() -> database.syncPendingPreferencesDao().clear());
    }

    /**
     * Applies one journaled preferences payload, failing loudly when it is not durable.
     *
     * @param expectedDigest digest the live preferences must show afterwards.
     */
    private void commitPendingPreferences(
            @NonNull PreferencesBackup backup, @NonNull String expectedDigest) throws IOException {
        String before = livePreferencesDigest();
        boolean committed;
        try {
            committed = preferenceHelper.commitListPreferences(backup);
        } catch (RuntimeException error) {
            throw new IOException("Could not commit synchronized preferences", error);
        }
        if (!committed) {
            throw new IOException("Could not commit synchronized preferences");
        }
        if (!expectedDigest.equals(before)) {
            appliedPreferencesChange.set(true);
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
        PreferencesBaselineDecision.Action action =
                PreferencesBaselineDecision.decide(
                        preferences.getString(PREFERENCES_HASH, null),
                        digest,
                        legacyPreferencesFingerprint(live));
        switch (action) {
            case UNCHANGED:
                return;
            case MIGRATE_BASELINE:
                // 2.6.49 stored the payload JSON here. Same values, older spelling: rewrite it
                // without touching the record, or every upgraded device claims a local edit.
                preferences.edit().putString(PREFERENCES_HASH, digest).commit();
                return;
            case LOCAL_EDIT:
            default:
                database.syncMetadataDao()
                        .touch(metadata.recordType, metadata.localId, System.currentTimeMillis());
                preferences.edit().putString(PREFERENCES_HASH, digest).commit();
        }
    }

    /** Digest of the preferences currently visible to the app. */
    @NonNull
    private String livePreferencesDigest() {
        return preferencesDigest(preferenceHelper.getListPreferences());
    }

    /** Stable digest of one preferences payload, used for the journal and the build baseline. */
    @NonNull
    private String preferencesDigest(@Nullable PreferencesBackup backup) {
        return Sha256.of(backup == null ? "" : gson.toJson(backup));
    }

    /** The baseline exactly as 2.6.49 wrote it: the payload object's own JSON. */
    @NonNull
    private String legacyPreferencesFingerprint(@Nullable PreferencesBackup backup) {
        return backup == null ? "" : gson.toJsonTree(backup).getAsJsonObject().toString();
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
                && !addAttachmentMetadata(
                        result, metadata, ((Note) value).getTitle(), snapshotProblems)) {
            return null;
        }
        // Runs last: the blocks above still need the local categoryId and attachment paths.
        SyncMetadata.stripDeviceLocalFields(metadata.recordType, result);
        return result;
    }

    private void applyPayload(SyncMetadataEntity metadata, JsonObject payload) throws IOException {
        // A record that was deleted here and then edited on another device has no row left to
        // update; @Update on a missing row is a silent no-op, and the tombstone was cleared
        // regardless, so tasks, categories and tags marked live never came back. The REPLACE
        // inserts put the row back under its own local id.
        boolean revive = metadata.deletedAt != null;
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
            if (revive || database.taskDao().getTaskSync(task.getId()) == null) {
                database.taskDao().insertTask(task);
            } else {
                database.taskDao().updateTask(task);
            }
        } else if (SyncMetadata.RECORD_TYPE_CATEGORY.equals(metadata.recordType)) {
            TaskCategory category = gson.fromJson(payload, TaskCategory.class);
            category.setId((int) metadata.localId);
            if (revive || database.taskCategoryDao().getCategorySync(category.getId()) == null) {
                database.taskCategoryDao().insertCategory(category);
            } else {
                database.taskCategoryDao().updateCategory(category);
            }
        } else if ("tag".equals(metadata.recordType)) {
            Tag tag = gson.fromJson(payload, Tag.class);
            tag.id = metadata.localId;
            if (revive || database.tagsDao().getTagSync(tag.id) == null) {
                database.tagsDao().addTag(tag);
            } else {
                database.tagsDao().updateTag(tag);
            }
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
            return insertRemoteTag(record);
        }
        return -1;
    }

    /**
     * Inserts a tag another device created, unless this device already has it by name.
     *
     * <p>A note stores its tag by name and the table has no unique index on it, so a tag created as
     * "Work" on two devices before their first sync arrived here as two stable ids and became two
     * rows the user saw twice. The name is the tag's real identity; the two stable ids are
     * reconciled deterministically so every device ends up with the same one: the smaller id wins,
     * the loser's row is deleted and tombstoned, and the tombstone retires the other id on every
     * device at the next sync. The device already holding the winning id simply keeps it.
     *
     * @return the local row bound to the record's stable id, or -1 when the record is skipped.
     */
    private long insertRemoteTag(@NonNull SyncRecord record) {
        Tag tag = gson.fromJson(record.getPayload(), Tag.class);
        tag.id = 0;
        String name = tag.getNameTag();
        Tag existing =
                name == null || name.isEmpty() ? null : database.tagsDao().getTagByNameSync(name);
        if (existing == null) {
            return database.tagsDao().addTag(tag);
        }
        SyncMetadataEntity existingMetadata =
                database.syncMetadataDao().get(SyncMetadata.RECORD_TYPE_TAG, existing.getId());
        if (existingMetadata == null) {
            // A row sync has never described; the remote identity becomes its identity.
            return existing.getId();
        }
        if (existingMetadata.stableId.compareTo(record.getId()) < 0) {
            // This device holds the winning identity; the other one is retired by whichever
            // device holds it once it sees ours.
            return -1;
        }
        database.tagsDao().deleteById(existing.getId());
        database.syncMetadataDao()
                .markDeleted(
                        SyncMetadata.RECORD_TYPE_TAG, existing.getId(), System.currentTimeMillis());
        return database.tagsDao().addTag(tag);
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

    /**
     * Whether the last apply changed the settings, clearing the flag as it reports.
     *
     * <p>The caller is the visible screen, which redraws itself so a received theme takes effect at
     * once rather than at the next activity creation.
     */
    public boolean consumeAppliedPreferencesChange() {
        return appliedPreferencesChange.getAndSet(false);
    }

    public List<SyncConflictEntity> getConflicts() {
        return database.syncConflictDao().getAll();
    }

    /**
     * Drops every trace of the account being disconnected.
     *
     * <p>Record identity in {@code sync_metadata} is deliberately kept: it is local, and discarding
     * it would make the whole library look brand new to the next account. What goes is the sync
     * status, the conflict queue, the preferences journal and the blobs downloaded from the
     * disconnected account's Drive. The journal matters: left behind, a fresh store replayed the
     * old account's settings onto the device at its next seeding.
     *
     * <p>Clearing the status also repairs a dead end: the Backup screen decided whether to ask for
     * first-sync consent from {@code lastSuccessfulSyncAt}, which survived a sign-out, while {@code
     * SyncCoordinator} gated the sync on a preference the sign-out reset. The dialog was skipped
     * and the sync refused, with no way to reach the consent again.
     *
     * <p>Waits for a sync in flight rather than racing it: the six-hourly worker holds the sync
     * lock for minutes, and clearing under it left the worker writing the old account's state and
     * conflicts back after the wipe, into a cache directory that had just been deleted.
     */
    public void clearAfterDisconnect() {
        SyncService.runWhileNoSyncRuns(
                () -> {
                    database.runInTransaction(
                            () -> {
                                database.syncStateDao().clear();
                                database.syncConflictDao().clearAll();
                                database.syncPendingPreferencesDao().clear();
                            });
                    preferences.edit().remove(PREFERENCES_HASH).remove(LEGACY_STATE).apply();
                    localAttachments.clear();
                    noteFoldersIndexed = false;
                    hashCache.clear();
                    deleteAttachmentCache();
                });
    }

    /** Removes the download cache only; the notes' own attachment folders are untouched. */
    private void deleteAttachmentCache() {
        File[] cached = attachmentCacheDir().listFiles();
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

        if (isSuperseded(pending)) {
            // The record moved on after this conflict was recorded — an edit here, or a newer
            // version applied from another device — so both stored versions are older than what
            // the user now has. Applying either would overwrite the newer edit with a version
            // that was never offered against it. The alternative still travels with the bundle
            // and comes back as a fresh conflict against the current version at the next sync.
            Log.w(TAG, "Dropping a conflict that the record's newer version has superseded");
            database.runInTransaction(() -> database.syncConflictDao().deleteById(conflictId));
            return;
        }

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

    /** True when the local record is newer than both versions the conflict offers. */
    private boolean isSuperseded(@NonNull SyncConflictEntity conflict) {
        SyncMetadataEntity metadata =
                database.syncMetadataDao().getByStableId(conflict.recordType, conflict.stableId);
        return metadata != null
                && metadata.updatedAt > Math.max(conflict.winnerUpdatedAt, conflict.loserUpdatedAt);
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
                                            false,
                                            conflictId,
                                            resolution.name()));
                });

        // Throws when the write is not durable, leaving the journal in place and the conflict
        // unresolved so the user can try again.
        commitPendingPreferences(chosen, target);

        try {
            finalizeResolvedPreferencesConflict(conflictId, resolution.name());
        } catch (RuntimeException error) {
            throw new IOException("Could not finalize the resolved preferences conflict", error);
        }
    }

    /**
     * Records that a preferences conflict is settled, once its value is durably applied.
     *
     * <p>Also reached from recovery: a crash between the adapter commit and this step used to leave
     * the chosen value in place but unversioned and the conflict still pending, so the next sync
     * could quietly put the rejected version back.
     */
    private void finalizeResolvedPreferencesConflict(long conflictId, @NonNull String resolution) {
        database.runInTransaction(
                () -> {
                    SyncConflictEntity conflict = database.syncConflictDao().getById(conflictId);
                    database.syncPendingPreferencesDao().clear();
                    if (conflict == null || conflict.resolved) return;
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
                    database.syncConflictDao().markResolved(conflictId, resolution, resolvedAt);
                });
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

    /**
     * Stores the conflicts this apply produced and retires the ones it makes meaningless.
     *
     * @param skippedKeys records this apply left untouched; a conflict for one of them offers
     *     versions that no longer describe the local record and is not stored.
     */
    private void persistConflicts(
            @NonNull List<SyncMergeResult.Conflict> conflicts, @NonNull Set<String> skippedKeys) {
        if (conflicts.isEmpty()) return;

        long createdAt = System.currentTimeMillis();
        List<SyncConflictEntity> rows = new ArrayList<>(conflicts.size());
        Map<String, Set<String>> winnersByRecord = new HashMap<>();
        for (SyncMergeResult.Conflict conflict : conflicts) {
            String key = conflict.getType().getWireValue() + ":" + conflict.getId();
            if (skippedKeys.contains(key)) {
                continue;
            }
            winnersByRecord
                    .computeIfAbsent(key, ignored -> new LinkedHashSet<>())
                    .add(conflict.getWinnerVersionId());
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
        if (rows.isEmpty()) return;
        // Every conflict for a record names the version this sync applies as its winner. An
        // older unresolved row for the same record therefore offers a winner that is no longer
        // the live version, and applying it — which "keep the version the merge selected" did
        // with one tap — reverted the user's newer edit. Its alternative is not lost: it still
        // travels with the bundle and is among the rows stored here, now against the current
        // version.
        for (SyncMergeResult.Conflict conflict : conflicts) {
            String key = conflict.getType().getWireValue() + ":" + conflict.getId();
            Set<String> winners = winnersByRecord.remove(key);
            if (winners == null || winners.size() != 1) {
                continue;
            }
            database.syncConflictDao()
                    .deleteSupersededUnresolved(
                            conflict.getType().getWireValue(),
                            conflict.getId(),
                            winners.iterator().next());
        }
        database.syncConflictDao().insertIgnoringDuplicates(rows);
    }

    @NonNull
    private static String conflictVersionPairHash(@NonNull SyncMergeResult.Conflict conflict) {
        return Sha256.of(
                conflict.getType().getWireValue()
                        + "\n"
                        + conflict.getId()
                        + "\n"
                        + conflict.getWinner().canonicalSerializedPayload()
                        + "\n"
                        + conflict.getLoser().canonicalSerializedPayload());
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

    @Override
    public boolean hasDurableAttachment(@NonNull String sha256, long sizeBytes) {
        File cached = attachmentFile(sha256);
        return cached.isFile() && (sizeBytes < 0L || cached.length() == sizeBytes);
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
     * cache, so a large attachment set is not stored twice. A store that never built a snapshot —
     * the Backup screen's, resolving a conflict the worker's store found — indexes them on its
     * first miss instead, so a version whose blob sits in a note folder is not reported missing.
     */
    @Nullable
    private File resolveLocalAttachment(@NonNull String sha256) {
        File cached = attachmentFile(sha256);
        if (cached.isFile()) {
            return cached;
        }
        File owned = localAttachments.get(sha256);
        if (owned != null && owned.isFile()) {
            return owned;
        }
        if (noteFoldersIndexed) {
            return null;
        }
        indexNoteFolders();
        owned = localAttachments.get(sha256);
        return owned != null && owned.isFile() ? owned : null;
    }

    /** Hashes every file under every note folder into {@link #localAttachments}, once. */
    private synchronized void indexNoteFolders() {
        if (noteFoldersIndexed) {
            return;
        }
        File[] folders = AttachmentStorage.baseDirPath(context).listFiles();
        if (folders != null) {
            for (File folder : folders) {
                File[] files = folder.isDirectory() ? folder.listFiles() : null;
                if (files == null) continue;
                for (File file : files) {
                    if (!file.isFile()) continue;
                    try {
                        localAttachments.putIfAbsent(
                                hashCache.sha256(file, attachmentHasher::sha256), file);
                    } catch (IOException | RuntimeException unreadable) {
                        // A file that cannot be hashed cannot satisfy a reference either.
                    }
                }
            }
        }
        hashCache.flush();
        noteFoldersIndexed = true;
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

    private File attachmentCacheDir() {
        return new File(context.getFilesDir(), ATTACHMENT_CACHE_DIR);
    }

    private File attachmentFile(String sha256) {
        File dir = attachmentCacheDir();
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, sha256);
    }

    /**
     * Describes a note's attachments for the wire and puts its editor blocks into wire form.
     *
     * <p>The attachments column names files by this device's row id and file name; the wire
     * describes each attachment by a logical id, its content hash and size. A file that is gone
     * from disk is still describable when the column remembers its hash and size — which every
     * attachment that ever came through a sync does — so such a note is published from that
     * metadata and {@link SyncService} fetches the bytes from the cache or Drive; the following
     * apply then puts the file back. Only an attachment nothing but this device ever knew about
     * fails the build, and then the problem names the note.
     */
    private boolean addAttachmentMetadata(
            JsonObject payload,
            SyncMetadataEntity metadata,
            @Nullable String noteTitle,
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
                    snapshotProblems,
                    SnapshotProblem.Kind.INVALID_ATTACHMENT_METADATA,
                    metadata,
                    noteTitle);
            return false;
        }

        JsonArray manifest = new JsonArray();
        JsonArray hashes = new JsonArray();
        JsonObject names = new JsonObject();
        Map<String, String> logicalIdByUrl = new HashMap<>();
        boolean complete = true;
        for (int attachmentIndex = 0; attachmentIndex < attachments.size(); attachmentIndex++) {
            JsonElement element = attachments.get(attachmentIndex);
            EditorAttachment attachment = null;
            if (element.isJsonObject()) {
                try {
                    attachment = gson.fromJson(element, EditorAttachment.class);
                } catch (RuntimeException error) {
                    attachment = null;
                }
            }
            if (attachment == null || attachment.url == null || attachment.url.trim().isEmpty()) {
                addSnapshotProblem(
                        snapshotProblems,
                        SnapshotProblem.Kind.INVALID_ATTACHMENT_METADATA,
                        metadata,
                        noteTitle);
                complete = false;
                continue;
            }
            JsonObject column = element.getAsJsonObject();
            File file;
            try {
                file = attachmentResolver.resolve(context, attachment);
            } catch (RuntimeException error) {
                addSnapshotProblem(
                        snapshotProblems,
                        SnapshotProblem.Kind.INVALID_ATTACHMENT_METADATA,
                        metadata,
                        noteTitle);
                complete = false;
                continue;
            }
            String rememberedHash = optionalString(column, "sha256");
            long rememberedSize = optionalLong(column, "size");
            String hash;
            long size;
            if (file == null || !file.isFile()) {
                if (rememberedHash == null
                        || !rememberedHash.matches(SHA_256)
                        || rememberedSize < 0L) {
                    addSnapshotProblem(
                            snapshotProblems,
                            SnapshotProblem.Kind.MISSING_ATTACHMENT,
                            metadata,
                            noteTitle);
                    complete = false;
                    continue;
                }
                Log.w(
                        TAG,
                        "An attachment file is missing; it will be restored from the sync cache"
                                + " or Drive");
                file = null;
                hash = rememberedHash;
                size = rememberedSize;
            } else {
                if (!file.canRead()) {
                    addSnapshotProblem(
                            snapshotProblems,
                            SnapshotProblem.Kind.UNREADABLE_ATTACHMENT,
                            metadata,
                            noteTitle);
                    complete = false;
                    continue;
                }
                try {
                    hash = hashCache.sha256(file, attachmentHasher::sha256);
                } catch (IOException error) {
                    addSnapshotProblem(
                            snapshotProblems,
                            SnapshotProblem.Kind.ATTACHMENT_HASH_FAILED,
                            metadata,
                            noteTitle);
                    complete = false;
                    continue;
                }
                size = file.length();
                localAttachments.put(hash, file);
            }
            String displayName = displayNameFor(attachment, file, hash);
            String logicalId = attachment.id;
            if (!isCanonicalUuid(logicalId)) {
                // Existing editor data predates logical attachment IDs. Deriving from the stable
                // note, source URL, position and content keeps the migration deterministic while
                // allowing equal-content references to remain distinct logical attachments. The
                // content hash is part of it so that one id can never describe two different
                // blobs: a bundle manifest is keyed by id, and two versions of a note disagreeing
                // about an id's content used to fail every publish for the account.
                //
                // The check is canonical-UUID rather than a loose 36-character pattern: the
                // bundle manifest only accepts canonical lowercase UUIDs, so an uppercase or
                // otherwise non-canonical id used to pass here and then throw during encode,
                // failing every publish for the whole account while that note existed.
                logicalId =
                        UUID.nameUUIDFromBytes(
                                        (metadata.stableId
                                                        + "\n"
                                                        + attachmentIndex
                                                        + "\n"
                                                        + attachment.url
                                                        + "\n"
                                                        + displayName
                                                        + "\n"
                                                        + hash)
                                                .getBytes(StandardCharsets.UTF_8))
                                .toString();
            }
            hashes.add(hash);
            names.addProperty(logicalId, displayName);
            logicalIdByUrl.put(comparableUrl(attachment.url), logicalId);

            String rememberedMimeType = optionalString(column, "mimeType");
            JsonObject manifestEntry = new JsonObject();
            manifestEntry.addProperty("id", logicalId);
            manifestEntry.addProperty("sha256", hash);
            manifestEntry.addProperty(
                    "mimeType",
                    rememberedMimeType != null
                            ? rememberedMimeType
                            : detectMimeType(file, attachment, displayName));
            manifestEntry.addProperty("size", size);
            manifestEntry.addProperty("path", "attachments/" + hash);
            manifestEntry.addProperty("displayName", displayName);
            manifest.add(manifestEntry);
        }
        if (!complete) return false;
        // Empty ones are dropped again by SyncRecord, the one place that rule lives.
        payload.add("attachmentsManifest", manifest);
        payload.add("attachmentHashes", hashes);
        payload.add("attachmentNames", names);
        // The blocks name this device's files. On the wire they name the logical attachment,
        // which every device agrees on; otherwise the same note hashed differently on every
        // device that had ever restored it and conflicted with itself on every sync.
        JsonElement valueJson = payload.get("f");
        if (valueJson != null && valueJson.isJsonPrimitive()) {
            String local = valueJson.getAsString();
            String wire =
                    EditorAttachmentBlocks.rewriteUrls(
                            local,
                            url -> {
                                String logicalId = logicalIdByUrl.get(comparableUrl(url));
                                return logicalId == null
                                        ? null
                                        : AttachmentWireUrl.forLogicalId(logicalId);
                            });
            if (wire != null && !wire.equals(local)) {
                payload.addProperty("f", wire);
            }
        }
        return true;
    }

    /** A URL in the one spelling both the legacy and the canonical scheme reduce to. */
    @NonNull
    private static String comparableUrl(@NonNull String url) {
        AttachmentUrl parsed = AttachmentUrl.parse(url);
        return parsed == null ? url.trim() : parsed.canonical();
    }

    @NonNull
    private static String displayNameFor(
            @NonNull EditorAttachment attachment, @Nullable File file, @NonNull String hash) {
        if (attachment.name != null && !attachment.name.trim().isEmpty()) {
            return attachment.name.trim();
        }
        if (file != null) {
            return file.getName();
        }
        AttachmentUrl parsed = AttachmentUrl.parse(attachment.url);
        return parsed == null ? hash : parsed.getFileName();
    }

    @Nullable
    private static String optionalString(@NonNull JsonObject object, @NonNull String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return null;
        }
        String text = value.getAsString().trim();
        return text.isEmpty() ? null : text;
    }

    private static long optionalLong(@NonNull JsonObject object, @NonNull String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return -1L;
        }
        try {
            return value.getAsLong();
        } catch (RuntimeException notALong) {
            return -1L;
        }
    }

    /** True only for a lowercase canonical UUID, which is all the bundle manifest accepts. */
    private static boolean isCanonicalUuid(@Nullable String value) {
        if (value == null) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException notAUuid) {
            return false;
        }
    }

    private static void addSnapshotProblem(
            @NonNull List<SnapshotProblem> problems,
            @NonNull SnapshotProblem.Kind kind,
            @NonNull SyncMetadataEntity metadata,
            @Nullable String label) {
        problems.add(new SnapshotProblem(kind, metadata.recordType, metadata.stableId, label));
    }

    /**
     * Materializes every attachment before changing the Room row. Targets use the immutable
     * logical-ID/content-ID pair rather than a display name, so a rollback can leave only harmless
     * new files and can never alter bytes addressed by the pre-transaction note.
     *
     * <p>The column written here remembers the logical id, hash, size and MIME type alongside the
     * local file, which is what lets the next build describe the attachment identically to the
     * device that sent it — and describe it at all should the file go missing.
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
        Map<String, String> localUrlByLogicalId = new HashMap<>();
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
                    || !entry.sha256.matches(SHA_256)
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
            // Canonical editorjs:// form, the only shape EditorAttachmentsWebViewClient serves.
            // Writing file:// here left every synced attachment unrenderable on the receiver.
            String url = AttachmentStorage.urlFor(note.getId(), target.getName());
            JsonObject attachment = new JsonObject();
            attachment.addProperty("url", url);
            attachment.addProperty("name", displayName);
            attachment.addProperty("id", entry.id);
            attachment.addProperty("sha256", entry.sha256);
            attachment.addProperty("size", entry.size);
            attachment.addProperty("mimeType", entry.mimeType);
            restored.add(attachment);
            localUrlByLogicalId.put(entry.id, url);
        }
        note.setAttachments(gson.toJson(restored));
        note.setValueJson(
                rewriteEditorAttachmentUrls(note.getValueJson(), restored, localUrlByLogicalId));
    }

    /**
     * Points the editor's own blocks at the files this device just wrote.
     *
     * <p>Restoring rebuilt the note's {@code attachments} column but left {@code valueJson}
     * verbatim, so every attachment and image block still named the sending device's {@code
     * note_<senderId>/<senderFile>}. The column is what the file list reads; the blocks are what
     * the editor renders, so a received rich note showed its attachments as broken.
     *
     * <p>A block written by this release names its attachment's logical id, which maps directly. A
     * bundle from an older client still names the sender's files, and for those the mapping is
     * positional, which is exactly how such a manifest was built: the sender's attachments column
     * came from {@code EditorJsonUtils} walking these same blocks in document order, and the
     * manifest walked that column in the same order. If the two do not line up the JSON is returned
     * untouched rather than guessed at — a note that renders the old broken URL is recoverable, one
     * whose content was rewritten wrongly is not.
     */
    @Nullable
    private String rewriteEditorAttachmentUrls(
            @Nullable String valueJson,
            @NonNull JsonArray restored,
            @NonNull Map<String, String> localUrlByLogicalId) {
        if (valueJson == null || valueJson.trim().isEmpty() || restored.size() == 0) {
            return valueJson;
        }
        List<String> urls = EditorAttachmentBlocks.fileUrls(valueJson);
        boolean wireForm = false;
        for (String url : urls) {
            if (AttachmentWireUrl.logicalIdOf(url) != null) {
                wireForm = true;
                break;
            }
        }
        if (wireForm) {
            return EditorAttachmentBlocks.rewriteUrls(
                    valueJson,
                    url -> {
                        String logicalId = AttachmentWireUrl.logicalIdOf(url);
                        return logicalId == null ? null : localUrlByLogicalId.get(logicalId);
                    });
        }
        if (urls.size() != restored.size()) {
            Log.w(TAG, "Editor blocks do not match the restored attachments; leaving them");
            return valueJson;
        }
        int[] position = {0};
        return EditorAttachmentBlocks.rewriteUrls(
                valueJson,
                url -> restored.get(position[0]++).getAsJsonObject().get("url").getAsString());
    }

    private boolean isVerifiedAttachmentFile(
            @NonNull File file, @NonNull String expectedHash, long expectedSize)
            throws IOException {
        return file.isFile()
                && file.length() == expectedSize
                && expectedHash.equals(hashCache.sha256(file, Sha256::of));
    }

    private static void copyVerifiedAttachment(
            @NonNull File source,
            @NonNull File target,
            @NonNull String expectedHash,
            long expectedSize)
            throws IOException {
        File temporary =
                new File(target.getParentFile(), target.getName() + ".tmp-" + UUID.randomUUID());
        try (VerifyingInputStream in =
                        new VerifyingInputStream(
                                new FileInputStream(source), expectedHash, expectedSize);
                OutputStream out = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.verifyEndOfStream();
        } catch (IOException failure) {
            if (temporary.exists() && !temporary.delete()) {
                Log.w(TAG, "Could not remove failed staged attachment");
            }
            throw failure;
        }
        if (!temporary.renameTo(target)) {
            if (!temporary.delete()) Log.w(TAG, "Could not remove uncommitted staged attachment");
            throw new IOException("Could not finalize staged attachment");
        }
    }

    private static boolean isSafeAttachmentName(@NonNull String name) {
        return AttachmentUrl.isSafeSegment(name.trim());
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
            @Nullable File file, EditorAttachment attachment, @NonNull String displayName) {
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

        mimeType = file == null ? null : URLConnection.guessContentTypeFromName(file.getName());
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
