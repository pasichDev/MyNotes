package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Google Drive REST backend for the provider-independent sync protocol. */
public final class GoogleDriveSyncBackend implements SyncBackend {
    private static final String DEFAULT_API = "https://www.googleapis.com/drive/v3";
    private static final String DEFAULT_UPLOAD = "https://www.googleapis.com/upload/drive/v3/files";
    private static final String FOLDER_NAME = "MyNotes Sync";
    private static final String BUNDLE_NAME = "MyNotes.sync.v1.zip";
    private static final String MIME_FOLDER = "application/vnd.google-apps.folder";
    private static final String MIME_JSON = "application/json; charset=UTF-8";
    private static final String MIME_ZIP = "application/zip";
    private static final String MIME_BINARY = "application/octet-stream";
    private static final String PROPERTY_OWNER = "mynotesOwner";
    private static final String PROPERTY_BUNDLE = "mynotesBundle";
    private static final String PROPERTY_BUNDLE_PUBLISHED_AT = "mynotesBundlePublishedAt";
    private static final String PROPERTY_ATTACHMENT_SHA256 = "mynotesAttachmentSha256";

    /**
     * Set on a bundle the first time a read finds it outside the frontier. Drive stamps the update
     * with its own {@code modifiedTime}, which is therefore the moment the bundle was seen to be
     * superseded — measured by Drive's clock, not by whichever device happened to publish.
     */
    private static final String PROPERTY_BUNDLE_SUPERSEDED = "mynotesBundleSuperseded";

    private static final int MAX_BUNDLE_RESPONSE_BYTES = 32 * 1024 * 1024;
    private static final long MAX_ATTACHMENT_RESPONSE_BYTES =
            SyncBundleValidator.MAX_ATTACHMENT_BYTES;
    private static final int RESUMABLE_CHUNK_BYTES = 256 * 1024;
    private static final int HTTP_RESUME_INCOMPLETE = 308;
    private static final int MAX_STALLED_CHUNK_ATTEMPTS = 3;
    private static final int MAX_ERROR_DETAIL_BYTES = 1024;
    private static final int MAX_ERROR_DETAIL_CHARS = 200;

    /**
     * How long a superseded bundle stays after it was first seen to be superseded.
     *
     * <p>A device that listed the folder just before the successor was published may still be
     * fetching the old bundle; an hour outlives any read, including the six-hourly worker's, which
     * WorkManager stops after ten minutes. Measured from the supersession mark, not from the
     * bundle's creation: a head created days ago and superseded seconds ago is exactly the file
     * another device is most likely to be reading right now.
     */
    static final long BUNDLE_PRUNE_GRACE_MILLIS = 60L * 60L * 1000L;

    private static final Gson GSON = new Gson();

    private final String accessToken;
    private final String apiBase;
    private final String uploadBase;
    private final Clock clock;
    private final SyncBundleCodec bundleCodec;
    private final DriveRequestExecutor requestExecutor;
    private final SyncMerger merger = new SyncMerger();
    private final long maxAttachmentBytes;
    private String lastReadToken = "";

    /** Every bundle file the last read saw, so a publish can retire the ones it supersedes. */
    private List<BundleFile> lastReadBundles = Collections.emptyList();

    /**
     * The owned root folders, listed once per sync.
     *
     * <p>Every attachment question used to re-list them, so an account with N attachments issued
     * several times N folder listings per sync before moving a byte — which is what surfaced as
     * Drive rate-limit errors on larger libraries. A backend instance lives for one sync; a root
     * created concurrently by another device is the duplicate-root case the read path merges on the
     * next sync anyway.
     */
    @Nullable private List<String> folderIds;

    /**
     * Blobs already verified during this sync, keyed by candidate and hash, with their size.
     *
     * <p>One attachment used to be downloaded in full two or three times per sync: once by
     * hasAttachment, once by the service re-verifying it, and once more while materializing it in
     * the canonical root. The verification itself is the point, so it still happens — once.
     */
    private final Map<String, Long> verifiedAttachments = new HashMap<>();

    public GoogleDriveSyncBackend(@NonNull String accessToken) {
        this(accessToken, DEFAULT_API, DEFAULT_UPLOAD, Clock.systemUTC(), new SyncBundleCodec());
    }

    GoogleDriveSyncBackend(
            @NonNull String accessToken,
            @NonNull String apiBase,
            @NonNull String uploadBase,
            @NonNull Clock clock,
            @NonNull SyncBundleCodec bundleCodec) {
        this(accessToken, apiBase, uploadBase, clock, bundleCodec, MAX_ATTACHMENT_RESPONSE_BYTES);
    }

    /** Test seam: a small attachment ceiling makes the oversize paths reachable in a test. */
    GoogleDriveSyncBackend(
            @NonNull String accessToken,
            @NonNull String apiBase,
            @NonNull String uploadBase,
            @NonNull Clock clock,
            @NonNull SyncBundleCodec bundleCodec,
            long maxAttachmentBytes) {
        if (accessToken.trim().isEmpty()) {
            throw new IllegalArgumentException("accessToken is empty");
        }
        this.accessToken = accessToken;
        this.apiBase = apiBase;
        this.uploadBase = uploadBase;
        this.clock = clock;
        this.bundleCodec = bundleCodec;
        this.maxAttachmentBytes = maxAttachmentBytes;
        this.requestExecutor = new DriveRequestExecutor();
    }

    @NonNull
    @Override
    public String getIdentifier() {
        return "google-drive";
    }

    /** The merged remote records alone; a convenience for tests, not part of the protocol. */
    @NonNull
    synchronized SyncSnapshot readSnapshot() throws IOException {
        return readSnapshotResult().getSnapshot();
    }

    @NonNull
    @Override
    public synchronized RemoteSnapshot readSnapshotResult() throws IOException {
        // A read begins a sync, so it sees the roots as they are now; the listing then serves
        // every attachment question until the next read.
        folderIds = listFolderIds();
        List<String> roots = folderIds;
        if (roots.isEmpty()) {
            lastReadBundles = Collections.emptyList();
            lastReadToken = UUID.randomUUID().toString();
            return new RemoteSnapshot(
                    SyncSnapshot.empty(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptySet(),
                    lastReadToken);
        }

        Map<String, SyncBundleCodec.DecodedBundle> bundlesByLogicalId = new HashMap<>();
        // Only a digest per logical bundle is retained for the duplicate-copy check; holding
        // every bundle's bytes for the whole read grew with the account's history.
        Map<String, String> digestByLogicalId = new HashMap<>();
        List<BundleFile> bundleFiles = new ArrayList<>();
        for (String folderId : roots) {
            for (BundleFile file : findBundles(folderId)) {
                byte[] bytes =
                        requestBytes(
                                "GET",
                                apiBase + "/files/" + file.fileId + "?alt=media",
                                MAX_BUNDLE_RESPONSE_BYTES);
                SyncBundleCodec.DecodedBundle decoded =
                        bundleCodec.decode(new ByteArrayInputStream(bytes));
                String digest = Sha256.of(bytes);
                bundleFiles.add(file.withLogicalId(decoded.getBundleId()));
                String previousDigest =
                        digestByLogicalId.putIfAbsent(decoded.getBundleId(), digest);
                if (previousDigest != null) {
                    if (!previousDigest.equals(digest)) {
                        throw new IOException(
                                "Drive contains conflicting physical copies of one bundle");
                    }
                    continue;
                }
                bundlesByLogicalId.put(decoded.getBundleId(), decoded);
            }
        }
        validateBundleDag(bundlesByLogicalId);
        List<String> frontier = computeFrontier(bundlesByLogicalId);
        SyncSnapshot merged = SyncSnapshot.empty();
        List<SyncMergeResult.Conflict> conflicts = new ArrayList<>();
        for (String bundleId : frontier) {
            // Both sides are Drive bundle heads. Naming them explicitly stops the accumulator
            // being reported to the user as "this device".
            SyncMergeResult result =
                    merger.merge(
                            merged,
                            bundlesByLogicalId.get(bundleId).getSnapshot(),
                            SyncMergeResult.Source.REMOTE,
                            SyncMergeResult.Source.REMOTE);
            merged = result.getMergedSnapshot();
            conflicts.addAll(result.getConflicts());
        }
        // Alternatives and the resolutions that retire them travel with the bundles, so a device
        // that has never seen a conflict still discovers it and a device that resolved one still
        // retires it everywhere.
        Set<String> resolvedAlternativeIds = new HashSet<>();
        for (String bundleId : frontier) {
            resolvedAlternativeIds.addAll(
                    bundlesByLogicalId.get(bundleId).getResolvedAlternativeIds());
        }
        Map<String, SyncRecord> alternativesByVersion = new java.util.LinkedHashMap<>();
        for (String bundleId : frontier) {
            for (SyncRecord alternative : bundlesByLogicalId.get(bundleId).getAlternatives()) {
                String versionId = alternative.getCanonicalPayloadHash();
                if (resolvedAlternativeIds.contains(versionId)) continue;
                SyncRecord winner = merged.find(alternative.getType(), alternative.getId());
                if (winner == null || winner.getCanonicalPayloadHash().equals(versionId)) {
                    // Nothing to choose between: the alternative is the current value, or its
                    // record no longer exists at all.
                    continue;
                }
                alternativesByVersion.putIfAbsent(versionId, alternative);
            }
        }
        List<SyncRecord> alternatives = new ArrayList<>(alternativesByVersion.values());
        for (SyncRecord alternative : alternatives) {
            SyncRecord winner = merged.find(alternative.getType(), alternative.getId());
            conflicts.add(
                    new SyncMergeResult.Conflict(
                            winner,
                            alternative,
                            SyncMergeResult.Source.REMOTE,
                            SyncMergeResult.Source.REMOTE));
        }

        markSupersededBundles(bundleFiles, frontier);
        lastReadBundles = Collections.unmodifiableList(bundleFiles);
        lastReadToken = UUID.randomUUID().toString();
        return new RemoteSnapshot(
                merged, conflicts, frontier, alternatives, resolvedAlternativeIds, lastReadToken);
    }

    /**
     * Stamps every bundle that has left the frontier, once, so its grace period starts now.
     *
     * <p>Best effort: a bundle that cannot be marked is never pruned, which costs a download per
     * sync and nothing else.
     */
    private void markSupersededBundles(
            @NonNull List<BundleFile> bundles, @NonNull Collection<String> frontier) {
        for (BundleFile bundle : bundles) {
            if (frontier.contains(bundle.logicalId) || bundle.supersededAtMillis != null) {
                continue;
            }
            try {
                JsonObject patch = new JsonObject();
                patch.add("appProperties", appProperties(PROPERTY_BUNDLE_SUPERSEDED, "1"));
                // HttpURLConnection has no PATCH; Google's APIs honour the override header.
                HttpURLConnection connection =
                        open("POST", apiBase + "/files/" + bundle.fileId + "?fields=id");
                connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                connection.setRequestProperty("Content-Type", MIME_JSON);
                connection.setDoOutput(true);
                try {
                    try (OutputStream output = connection.getOutputStream()) {
                        output.write(jsonBytes(patch));
                    }
                    ensureSuccess(connection);
                } finally {
                    connection.disconnect();
                }
            } catch (IOException ignored) {
                // Marked at the next read instead; the grace period simply starts later.
            }
        }
    }

    @Override
    public synchronized void publish(@NonNull SyncPublication publication) throws IOException {
        // Causal parents used to come from a mutable field, so a write with no preceding read
        // published a parentless root that permanently forked the DAG. The read that produced
        // this publication has to be this backend's most recent one, and it is that read — not a
        // second copy of its frontier kept on this object — that names the parents.
        RemoteSnapshot readContext = publication.getReadContext();
        String token = readContext.getReadToken();
        if (token.isEmpty() || !token.equals(lastReadToken)) {
            throw new IOException(
                    "Drive publish is not derived from this backend's latest remote read");
        }
        SyncSnapshot snapshot = publication.getSnapshot();
        String folderId = ensureCanonicalFolderId();
        // A first-sync race can leave valid bundles and immutable blobs in two owned folders.
        // The read path always merges all roots. Before canonical publication, materialize every
        // referenced blob in the canonical root as well, so no future cleanup decision can make
        // the canonical bundle point at an object that exists only in a duplicate root.
        ensureCanonicalAttachments(folderId, snapshot);
        ensureCanonicalAlternativeAttachments(folderId, publication.getUnresolvedAlternatives());
        byte[] bundle =
                bundleCodec.encode(
                        snapshot,
                        clock.instant(),
                        readContext.getFrontierBundleIds(),
                        publication.getUnresolvedAlternatives(),
                        publication.getResolvedAlternativeIds());
        // Every bundle is immutable. Drive offers no conditional update based on its version
        // counter, so replacing one file leaves a race where another device can be overwritten.
        // Publishing a distinct file makes each successful upload independently durable; readers
        // merge the complete set deterministically.
        String bundleName = nextBundleName();
        try {
            uploadFile(folderId, bundleName, MIME_ZIP, bundle, true);
        } catch (IOException uploadFailure) {
            // POST is deliberately not blindly retried. The server may have accepted the upload
            // before the client lost its response; rediscovering the unique name makes that
            // outcome successful without publishing a second logical bundle.
            if (!mayHaveCommitted(uploadFailure) || !hasBundleNamed(folderId, bundleName)) {
                throw uploadFailure;
            }
        }
        pruneSupersededBundles(readContext.getFrontierBundleIds());
    }

    /**
     * Retires the bundles the one just published makes redundant.
     *
     * <p>Nothing ever deleted a bundle, so an account accumulated one full snapshot per changed
     * sync, and every later sync downloaded, unzipped and validated all of them to compute a
     * frontier of one or two heads. A bundle is a complete snapshot, so everything a superseded
     * bundle held — records, tombstones, unresolved alternatives — lives on in its descendants, and
     * the read path already tolerates a missing ancestor. A bundle goes only once it has been
     * marked superseded for the whole grace period; the heads this publish descended from are not
     * even marked yet, and are what a concurrent publisher is about to name as parents.
     *
     * <p>Best effort by design: a bundle that cannot be removed costs a download next time, never
     * correctness.
     */
    private void pruneSupersededBundles(@NonNull Collection<String> frontierBundleIds) {
        long cutoff = clock.millis() - BUNDLE_PRUNE_GRACE_MILLIS;
        for (BundleFile bundle : lastReadBundles) {
            // Only a bundle that was already marked superseded when this sync read it, with the
            // mark's Drive-side timestamp older than the grace, may go. A bundle marked during
            // this very read, or one whose mark Drive does not date, stays.
            if (frontierBundleIds.contains(bundle.logicalId)
                    || bundle.supersededAtMillis == null
                    || bundle.supersededAtMillis > cutoff) {
                continue;
            }
            try {
                deleteFile(bundle.fileId);
            } catch (IOException ignored) {
                // Still there next time; the read path copes with it either way.
            }
        }
    }

    /**
     * Whether any root indexes a blob under this hash, without reading it.
     *
     * <p>Deliberately an index lookup: this used to download and hash the whole blob, and its only
     * caller then downloaded it a second time to verify it. Existence and verification are separate
     * questions now, and {@link #hasVerifiedAttachment} answers the second one once.
     */
    @Override
    public synchronized boolean hasAttachment(@NonNull String sha256) throws IOException {
        for (String folderId : findFolderIds()) {
            if (!listAttachmentCandidates(folderId, sha256).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Opens one copy of the blob, choosing a verified candidate where one is known.
     *
     * <p>The stream is not verified here: every caller wraps it in its own verifier, and doing it
     * here as well meant downloading the blob twice — once to check it and once to hand it over.
     * Only when several unverified copies exist is one read ahead of time, so that a corrupt
     * duplicate cannot be the one handed to the caller.
     */
    @Nullable
    @Override
    public synchronized InputStream readAttachment(@NonNull String sha256) throws IOException {
        List<String> roots = findFolderIds();
        Map<String, List<AttachmentCandidate>> candidatesByRoot = new java.util.LinkedHashMap<>();
        int total = 0;
        for (String folderId : roots) {
            List<AttachmentCandidate> candidates = listAttachmentCandidates(folderId, sha256);
            candidatesByRoot.put(folderId, candidates);
            total += candidates.size();
            for (AttachmentCandidate candidate : candidates) {
                if (isVerifiedWithoutReading(candidate, sha256, null)) {
                    return openAttachment(candidate.id);
                }
            }
        }
        if (total == 1) {
            // The only copy in the whole account: a corrupt one fails the caller's verifier
            // exactly as it would fail one here, and there is nothing else to fall back to. The
            // difference is one download instead of two.
            for (List<AttachmentCandidate> candidates : candidatesByRoot.values()) {
                if (!candidates.isEmpty()) {
                    return openAttachment(candidates.get(0).id);
                }
            }
        }
        // Several unverified copies, across duplicate roots or within one: read ahead of time so
        // a corrupt copy in one root cannot shadow the good copy in another.
        for (String folderId : roots) {
            String chosen = findVerifiedAttachment(folderId, sha256, null);
            if (chosen != null) {
                return openAttachment(chosen);
            }
        }
        return null;
    }

    @Override
    public synchronized void writeAttachment(
            @NonNull String sha256, long sizeBytes, @NonNull InputStream content)
            throws IOException {
        String folderId = ensureCanonicalFolderId();
        if (findVerifiedAttachment(folderId, sha256, sizeBytes >= 0L ? sizeBytes : null) != null) {
            return;
        }
        if (sizeBytes < 0L) {
            // No declared size, so the content length cannot be computed up front. Rare: sizes
            // come from the bundle manifest, which also supplies the hashes being uploaded.
            byte[] buffered =
                    readBounded(
                            content,
                            maxAttachmentBytes,
                            "Attachment exceeds the 100 MiB sync upload limit");
            uploadAttachmentOrConfirm(
                    folderId, sha256, new ByteArrayInputStream(buffered), buffered.length);
            return;
        }
        if (sizeBytes > maxAttachmentBytes) {
            throw new IOException("Attachment exceeds the 100 MiB sync upload limit");
        }
        uploadAttachmentOrConfirm(folderId, sha256, content, sizeBytes);
    }

    @NonNull
    private List<String> findFolderIds() throws IOException {
        if (folderIds == null) {
            folderIds = listFolderIds();
        }
        return folderIds;
    }

    @NonNull
    private List<String> listFolderIds() throws IOException {
        JsonArray folders =
                listFiles(
                        "mimeType = '"
                                + MIME_FOLDER
                                + "' and trashed = false and "
                                + appPropertyClause(PROPERTY_OWNER, "1"),
                        "files(id,name)");
        List<String> result = new ArrayList<>(folders.size());
        for (int index = 0; index < folders.size(); index++) {
            result.add(folders.get(index).getAsJsonObject().get("id").getAsString());
        }
        result.sort(Comparator.naturalOrder());
        return Collections.unmodifiableList(result);
    }

    @NonNull
    private String ensureCanonicalFolderId() throws IOException {
        List<String> roots = findFolderIds();
        if (roots.isEmpty()) {
            // The cached answer may predate another device's first sync; only a fresh listing
            // may justify creating a root.
            roots = listFolderIds();
            folderIds = roots;
        }
        if (!roots.isEmpty()) {
            return roots.get(0);
        }

        JsonObject metadata = new JsonObject();
        metadata.addProperty("name", FOLDER_NAME);
        metadata.addProperty("mimeType", MIME_FOLDER);
        metadata.add("appProperties", appProperties(PROPERTY_OWNER, "1"));
        try {
            String created = uploadMetadata(metadata);
            folderIds = Collections.singletonList(created);
            return created;
        } catch (IOException createFailure) {
            // Folder POST can have committed before a lost response. Duplicate roots are a
            // supported read state; rediscovery avoids a blind retry creating another one.
            roots = listFolderIds();
            folderIds = roots;
            if (!roots.isEmpty()) {
                return roots.get(0);
            }
            throw createFailure;
        }
    }

    private void ensureCanonicalAttachments(
            @NonNull String canonicalRootId, @NonNull SyncSnapshot snapshot) throws IOException {
        materializeAttachmentsInCanonicalRoot(
                canonicalRootId, attachmentSizes(snapshot.getLiveRecords(SyncRecord.Type.NOTE)));
    }

    private void materializeAttachmentsInCanonicalRoot(
            @NonNull String canonicalRootId, @NonNull Map<String, Long> sizes) throws IOException {
        if (sizes.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Long> attachment : sizes.entrySet()) {
            String hash = attachment.getKey();
            if (findVerifiedAttachment(canonicalRootId, hash, attachment.getValue()) != null) {
                continue;
            }
            InputStream source = readAttachment(hash);
            if (source == null) {
                throw new IOException("Required attachment is unavailable in any Drive root");
            }
            try (VerifyingInputStream input =
                    new VerifyingInputStream(source, hash, attachment.getValue())) {
                uploadAttachmentOrConfirm(canonicalRootId, hash, input, attachment.getValue());
                input.verifyEndOfStream();
            }
        }
    }

    /**
     * Makes every blob an unresolved alternative needs available in the canonical root.
     *
     * <p>Works from a plain record list rather than a {@link SyncSnapshot}: one record can have
     * several unresolved alternatives at once — three-way edits, or a second conflict on a note
     * that already had one — and a snapshot deliberately refuses to hold two versions of one ID.
     */
    private void ensureCanonicalAlternativeAttachments(
            @NonNull String canonicalRootId, @NonNull List<SyncRecord> alternatives)
            throws IOException {
        List<SyncRecord> notes = new ArrayList<>();
        for (SyncRecord alternative : alternatives) {
            if (!alternative.isTombstone() && alternative.getType() == SyncRecord.Type.NOTE) {
                notes.add(alternative);
            }
        }
        if (notes.isEmpty()) {
            return;
        }
        materializeAttachmentsInCanonicalRoot(canonicalRootId, attachmentSizes(notes));
    }

    @NonNull
    private Map<String, Long> attachmentSizes(@NonNull Collection<SyncRecord> notes)
            throws IOException {
        Map<String, Long> sizes = new HashMap<>();
        for (SyncRecord record : notes) {
            JsonArray manifest = record.getPayload().getAsJsonArray("attachmentsManifest");
            if (manifest == null) {
                continue;
            }
            for (int index = 0; index < manifest.size(); index++) {
                JsonObject entry = manifest.get(index).getAsJsonObject();
                if (!entry.has("sha256") || !entry.has("size")) {
                    throw new IOException("Attachment metadata is incomplete");
                }
                String hash = entry.get("sha256").getAsString();
                long size = entry.get("size").getAsLong();
                if (size < 0L || size > maxAttachmentBytes) {
                    throw new IOException("Attachment size exceeds the sync limit");
                }
                Long previous = sizes.putIfAbsent(hash, size);
                if (previous != null && previous.longValue() != size) {
                    throw new IOException("Attachment metadata has conflicting sizes");
                }
            }
        }
        return sizes;
    }

    /**
     * Checks the ancestry graph without requiring every historical bundle to still exist.
     *
     * <p>A missing ancestor used to be fatal, which inverted the rule that cleanup must never be
     * needed for correctness: one bundle trashed by hand, or aged out of Drive's own trash, and
     * sync failed forever with no way back. It is safe to tolerate because a bundle is a complete
     * snapshot rather than a delta — every descendant already contains everything its ancestors
     * held, including their unresolved alternatives — so an absent ancestor removes nothing from
     * the state a head describes. It also cannot be a frontier head itself, since a head is a
     * bundle no present bundle claims as a parent.
     */
    private static void validateBundleDag(
            @NonNull Map<String, SyncBundleCodec.DecodedBundle> bundles) throws IOException {
        Map<String, List<String>> parentsById = new HashMap<>();
        for (Map.Entry<String, SyncBundleCodec.DecodedBundle> entry : bundles.entrySet()) {
            parentsById.put(entry.getKey(), entry.getValue().getParentBundleIds());
        }
        validateAncestry(parentsById);
    }

    /**
     * Rejects a cycle in the parent graph.
     *
     * <p>Iterative on purpose: the recursive walk went one frame deeper per ancestor, so a long
     * linear history — exactly what an account that syncs after every edit accumulates — could
     * overflow the worker's stack, and a {@code StackOverflowError} is not an {@code IOException}
     * the sync knows how to report.
     */
    static void validateAncestry(@NonNull Map<String, ? extends Collection<String>> parentsById)
            throws IOException {
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        Deque<Frame> stack = new ArrayDeque<>();
        for (String root : parentsById.keySet()) {
            if (visited.contains(root)) {
                continue;
            }
            visiting.add(root);
            stack.push(new Frame(root, parentsById.get(root).iterator()));
            while (!stack.isEmpty()) {
                Frame frame = stack.peek();
                if (!frame.parents.hasNext()) {
                    stack.pop();
                    visiting.remove(frame.bundleId);
                    visited.add(frame.bundleId);
                    continue;
                }
                String parent = frame.parents.next();
                Collection<String> grandparents = parentsById.get(parent);
                if (grandparents == null || visited.contains(parent)) {
                    // An ancestor that is no longer stored, or one already walked.
                    continue;
                }
                if (!visiting.add(parent)) {
                    throw new IOException("Drive bundle ancestry contains a cycle");
                }
                stack.push(new Frame(parent, grandparents.iterator()));
            }
        }
    }

    private static final class Frame {
        private final String bundleId;
        private final Iterator<String> parents;

        private Frame(String bundleId, Iterator<String> parents) {
            this.bundleId = bundleId;
            this.parents = parents;
        }
    }

    @NonNull
    private static List<String> computeFrontier(
            @NonNull Map<String, SyncBundleCodec.DecodedBundle> bundles) {
        Set<String> ancestors = new HashSet<>();
        for (SyncBundleCodec.DecodedBundle bundle : bundles.values()) {
            ancestors.addAll(bundle.getParentBundleIds());
        }
        List<String> frontier = new ArrayList<>();
        for (String bundleId : bundles.keySet()) {
            if (!ancestors.contains(bundleId)) frontier.add(bundleId);
        }
        frontier.sort(Comparator.naturalOrder());
        return frontier;
    }

    /** One physical bundle file in a root; the logical id is known once it has been decoded. */
    private static final class BundleFile {
        private final String fileId;
        @Nullable private final String logicalId;

        /** Drive's modifiedTime of the supersession mark, or null when unmarked or undated. */
        @Nullable private final Long supersededAtMillis;

        private BundleFile(
                @NonNull String fileId,
                @Nullable String logicalId,
                @Nullable Long supersededAtMillis) {
            this.fileId = fileId;
            this.logicalId = logicalId;
            this.supersededAtMillis = supersededAtMillis;
        }

        @NonNull
        private BundleFile withLogicalId(@NonNull String id) {
            return new BundleFile(fileId, id, supersededAtMillis);
        }
    }

    @NonNull
    private List<BundleFile> findBundles(@NonNull String folderId) throws IOException {
        JsonArray bundles =
                listFiles(
                        ownedFilesQuery(folderId, PROPERTY_BUNDLE, "1"),
                        "files(id,name,modifiedTime,appProperties)");
        List<BundleFile> result = new ArrayList<>(bundles.size());
        for (int index = 0; index < bundles.size(); index++) {
            JsonObject file = bundles.get(index).getAsJsonObject();
            JsonObject properties = file.getAsJsonObject("appProperties");
            boolean marked = properties != null && properties.has(PROPERTY_BUNDLE_SUPERSEDED);
            result.add(
                    new BundleFile(
                            file.get("id").getAsString(),
                            null,
                            marked ? instantOf(optionalString(file, "modifiedTime")) : null));
        }
        result.sort(Comparator.comparing(file -> file.fileId));
        return result;
    }

    /** Drive reports times as RFC 3339; anything else counts as unknown. */
    @Nullable
    private static Long instantOf(@Nullable String time) {
        if (time == null) {
            return null;
        }
        try {
            return java.time.Instant.parse(time).toEpochMilli();
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    /** One Drive object indexed under a hash, with what Drive itself says about its bytes. */
    private static final class AttachmentCandidate {
        private final String id;
        @Nullable private final Long size;
        @Nullable private final String sha256Checksum;

        private AttachmentCandidate(
                @NonNull String id, @Nullable Long size, @Nullable String sha256Checksum) {
            this.id = id;
            this.size = size;
            this.sha256Checksum = sha256Checksum;
        }
    }

    /** Every object in the root indexed under {@code sha256}, smallest id first. */
    @NonNull
    private List<AttachmentCandidate> listAttachmentCandidates(
            @NonNull String folderId, @NonNull String sha256) throws IOException {
        JsonArray files =
                listFiles(
                        ownedFilesQuery(folderId, PROPERTY_ATTACHMENT_SHA256, sha256),
                        "files(id,name,size,sha256Checksum)");
        List<AttachmentCandidate> candidates = new ArrayList<>(files.size());
        for (int index = 0; index < files.size(); index++) {
            JsonObject file = files.get(index).getAsJsonObject();
            candidates.add(
                    new AttachmentCandidate(
                            file.get("id").getAsString(),
                            optionalLong(file, "size"),
                            optionalString(file, "sha256Checksum")));
        }
        // Attachments are content-addressed, so duplicates uploaded by two devices racing on the
        // same hash are byte-identical and either one will do. Rejecting them used to break every
        // subsequent sync permanently.
        candidates.sort(Comparator.comparing(candidate -> candidate.id));
        return candidates;
    }

    /**
     * An app property is only an index. Every candidate is verified before it may satisfy a
     * content-addressed reference; corrupt candidates remain harmless Drive orphans.
     *
     * <p>Drive computes a checksum over the bytes it stores and reports it with the listing, so a
     * candidate is usually verified without a download; only an object Drive has not checksummed is
     * read. The old version read every candidate in full on every sync, which for a library of a
     * few hundred megabytes meant re-downloading all of it on every six-hourly run.
     */
    @Nullable
    private String findVerifiedAttachment(
            @NonNull String folderId, @NonNull String sha256, @Nullable Long expectedSize)
            throws IOException {
        for (AttachmentCandidate candidate : listAttachmentCandidates(folderId, sha256)) {
            if (isVerifiedWithoutReading(candidate, sha256, expectedSize)) {
                return candidate.id;
            }
            if (candidate.sha256Checksum != null && !candidate.sha256Checksum.equals(sha256)) {
                // Drive's own digest disagrees with the index; reading would only confirm it.
                // Anything less definite — a matching digest without a usable size, or one the
                // expected size disagrees with — is read and verified like an unlisted object,
                // rather than counted as corrupt and re-uploaded on every sync.
                continue;
            }
            try (InputStream content = openAttachment(candidate.id)) {
                long size = VerifyingInputStream.verify(content, sha256, expectedSize);
                verifiedAttachments.put(memoKey(candidate.id, sha256), size);
                return candidate.id;
            } catch (AttachmentIntegrityException corrupt) {
                // A second content-addressed duplicate may be valid. Never accept the property
                // alone and never delete this object during a correctness path.
            }
        }
        return null;
    }

    /** True when this sync, or Drive's own checksum, already vouches for the candidate's bytes. */
    private boolean isVerifiedWithoutReading(
            @NonNull AttachmentCandidate candidate,
            @NonNull String sha256,
            @Nullable Long expectedSize) {
        Long verifiedSize = verifiedAttachments.get(memoKey(candidate.id, sha256));
        if (verifiedSize == null
                && sha256.equals(candidate.sha256Checksum)
                && candidate.size != null
                && candidate.size <= maxAttachmentBytes) {
            verifiedSize = candidate.size;
            verifiedAttachments.put(memoKey(candidate.id, sha256), verifiedSize);
        }
        return verifiedSize != null && (expectedSize == null || expectedSize.equals(verifiedSize));
    }

    @NonNull
    private static String memoKey(@NonNull String candidateId, @NonNull String sha256) {
        return candidateId + " " + sha256;
    }

    /**
     * True only when a remote blob exists and its actual bytes hash to {@code sha256}.
     *
     * <p>Drive's {@code appProperties} index is a claim, not proof, so the bytes are checked. The
     * result is remembered for this sync so the caller does not have to download the blob again
     * purely to repeat the same check.
     */
    @Override
    public synchronized boolean hasVerifiedAttachment(
            @NonNull String sha256, @Nullable Long expectedSize) throws IOException {
        for (String folderId : findFolderIds()) {
            if (findVerifiedAttachment(folderId, sha256, expectedSize) != null) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private InputStream openAttachment(@NonNull String attachmentId) throws IOException {
        // Streamed, not buffered: reading a 100 MB attachment into a byte[] (which the growing
        // ByteArrayOutputStream first doubled, then copied) was the largest single allocation in
        // the sync and an OutOfMemoryError on an ordinary phone.
        HttpURLConnection connection =
                requestExecutor.executeIdempotent(
                        () ->
                                openSuccessful(
                                        "GET", apiBase + "/files/" + attachmentId + "?alt=media"));
        try {
            return new ConnectionInputStream(connection, maxAttachmentBytes);
        } catch (IOException failure) {
            connection.disconnect();
            throw failure;
        }
    }

    @NonNull
    private JsonArray listFiles(@NonNull String query, @NonNull String fields) throws IOException {
        JsonArray result = new JsonArray();
        String nextPageToken = null;
        do {
            String url =
                    apiBase
                            + "/files?q="
                            + URLEncoder.encode(query, StandardCharsets.UTF_8.name())
                            + "&spaces=drive&fields="
                            + URLEncoder.encode(
                                    "nextPageToken," + fields, StandardCharsets.UTF_8.name())
                            + "&pageSize=1000";
            if (nextPageToken != null) {
                url +=
                        "&pageToken="
                                + URLEncoder.encode(nextPageToken, StandardCharsets.UTF_8.name());
            }
            JsonObject response = requestJsonIdempotent("GET", url, null, null);
            JsonArray files = response.getAsJsonArray("files");
            if (files != null) {
                for (int index = 0; index < files.size(); index++) {
                    result.add(files.get(index));
                }
            }
            nextPageToken =
                    response.has("nextPageToken") && !response.get("nextPageToken").isJsonNull()
                            ? response.get("nextPageToken").getAsString()
                            : null;
        } while (nextPageToken != null && !nextPageToken.isEmpty());
        return result;
    }

    @NonNull
    private String uploadMetadata(@NonNull JsonObject metadata) throws IOException {
        JsonObject created =
                requestJson(
                        "POST", apiBase + "/files?fields=id,name", MIME_JSON, jsonBytes(metadata));
        return created.get("id").getAsString();
    }

    /** Permanently removes one file this app created; bundles are complete, so nothing is lost. */
    private void deleteFile(@NonNull String fileId) throws IOException {
        HttpURLConnection connection = open("DELETE", apiBase + "/files/" + fileId);
        try {
            int code = connection.getResponseCode();
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                return;
            }
            ensureSuccess(connection);
        } finally {
            connection.disconnect();
        }
    }

    private boolean hasBundleNamed(@NonNull String folderId, @NonNull String name)
            throws IOException {
        JsonArray bundles =
                listFiles(
                        "'"
                                + folderId
                                + "' in parents and trashed = false and name = '"
                                + escapeQuery(name)
                                + "' and "
                                + appPropertyClause(PROPERTY_BUNDLE, "1"),
                        "files(id)");
        return bundles.size() > 0;
    }

    /** The one spelling of "our file, in this root, indexed under this property". */
    @NonNull
    private static String ownedFilesQuery(
            @NonNull String folderId, @NonNull String key, @NonNull String value) {
        return "'"
                + folderId
                + "' in parents and trashed = false and "
                + appPropertyClause(key, value);
    }

    private void uploadFile(
            @NonNull String folderId,
            @NonNull String name,
            @NonNull String mimeType,
            @NonNull byte[] data,
            boolean bundleFile)
            throws IOException {
        uploadMultipart(
                folderId, name, mimeType, new ByteArrayInputStream(data), data.length, bundleFile);
    }

    /** Uploads an attachment of known length without ever holding it in memory. */
    private void uploadStream(
            @NonNull String folderId,
            @NonNull String name,
            @NonNull String mimeType,
            @NonNull InputStream content,
            long sizeBytes)
            throws IOException {
        if (sizeBytes == 0L) {
            // A resumable session has no chunk to send and therefore no way to finalize; the
            // loop below would exit having created nothing while reporting success. An empty
            // blob is valid user data, so it takes the multipart path, whose Content-Length is
            // exact and whose empty body part Drive commits as a zero-byte file.
            uploadEmptyAttachment(folderId, name, mimeType, content);
            return;
        }
        uploadResumableAttachment(folderId, name, mimeType, content, sizeBytes);
    }

    /** Publishes a zero-length blob and proves the source really was empty. */
    private void uploadEmptyAttachment(
            @NonNull String folderId,
            @NonNull String name,
            @NonNull String mimeType,
            @NonNull InputStream content)
            throws IOException {
        if (content.read() != -1) {
            throw new AttachmentIntegrityException("Attachment exceeds its declared size");
        }
        uploadMultipart(folderId, name, mimeType, new ByteArrayInputStream(new byte[0]), 0L, false);
    }

    /**
     * Uploads a bounded attachment in resumable chunks.
     *
     * <p>Progress is tracked as one absolute count of bytes Drive has committed, {@code
     * acknowledgedExclusive}, and every request starts at exactly that offset. An earlier version
     * derived progress from a mutable {@code remaining} counter that could desynchronize from the
     * absolute Drive offset: once a partially acknowledged chunk was completed by a retry the loop
     * never terminated, and it replayed the buffer under offsets past the end of the file. Nothing
     * here is derived — the buffer window is recomputed from absolute offsets on every pass, so a
     * byte can only ever be sent under the one offset it occupies in the source.
     *
     * <p>Every request but the last carries a whole 256 KiB window, which is what Drive's resumable
     * protocol requires. After a partial acknowledgement the window therefore slides: the
     * unacknowledged tail moves to the front of the buffer and the window is refilled from the
     * source. Sending only the tail — a short chunk that is not the last — was answered with a 400
     * that nothing retries.
     */
    private void uploadResumableAttachment(
            @NonNull String folderId,
            @NonNull String sha256,
            @NonNull String mimeType,
            @NonNull InputStream content,
            long sizeBytes)
            throws IOException {
        String sessionUrl =
                initiateResumableAttachmentUpload(folderId, sha256, mimeType, sizeBytes);
        byte[] buffer = new byte[RESUMABLE_CHUNK_BYTES];
        long acknowledgedExclusive = 0L;
        long bufferStart = 0L;
        int bufferLength = 0;
        int stalledAttempts = 0;

        while (acknowledgedExclusive < sizeBytes) {
            throwIfInterrupted();
            if (acknowledgedExclusive > bufferStart) {
                // Everything before the acknowledged offset is durable; keep only the tail.
                int consumed = (int) (acknowledgedExclusive - bufferStart);
                if (consumed >= bufferLength) {
                    bufferLength = 0;
                } else {
                    System.arraycopy(buffer, consumed, buffer, 0, bufferLength - consumed);
                    bufferLength -= consumed;
                }
                bufferStart = acknowledgedExclusive;
            }
            int wanted = (int) Math.min(buffer.length, sizeBytes - bufferStart);
            if (bufferLength < wanted) {
                bufferLength += readChunk(content, buffer, bufferLength, wanted - bufferLength);
                if (bufferLength < wanted) {
                    // The source disagrees with its own manifest: an integrity failure, so it
                    // is never mistaken for a lost response worth confirming by discovery.
                    throw new AttachmentIntegrityException(
                            "Attachment ended before its declared size");
                }
            }

            int length = bufferLength;
            long chunkStart = bufferStart;
            long chunkEndExclusive = chunkStart + length;

            // A chunk PUT is idempotent: it is addressed by an absolute Content-Range, so a
            // replay of the identical range either lands at the same offset or is already
            // committed. Retrying is therefore safe, and it keeps one transient 5xx between
            // chunks from discarding a large upload that is nearly complete.
            long reported =
                    requestExecutor.executeIdempotent(
                            () ->
                                    uploadChunk(
                                            sessionUrl,
                                            mimeType,
                                            buffer,
                                            0,
                                            length,
                                            chunkStart,
                                            sizeBytes));

            if (reported < acknowledgedExclusive) {
                throw new IOException(
                        "Drive resumable upload moved its acknowledged range backwards");
            }
            if (reported > sizeBytes) {
                throw new IOException("Drive acknowledged more bytes than the attachment declares");
            }
            if (reported > chunkEndExclusive) {
                throw new IOException("Drive acknowledged bytes that were never sent");
            }
            if (reported == acknowledgedExclusive) {
                // A 308 that commits nothing is tolerable once or twice; forever is the bug
                // this loop exists to make impossible.
                if (++stalledAttempts > MAX_STALLED_CHUNK_ATTEMPTS) {
                    throw new IOException("Drive resumable upload stopped making progress");
                }
                continue;
            }
            stalledAttempts = 0;
            acknowledgedExclusive = reported;
        }

        if (content.read() != -1) {
            throw new AttachmentIntegrityException("Attachment exceeds its declared size");
        }
    }

    @NonNull
    private String initiateResumableAttachmentUpload(
            @NonNull String folderId,
            @NonNull String sha256,
            @NonNull String mimeType,
            long sizeBytes)
            throws IOException {
        JsonObject metadata = attachmentMetadata(folderId, sha256);
        HttpURLConnection connection =
                open("POST", uploadBase + "?uploadType=resumable&fields=id,name");
        connection.setRequestProperty("Content-Type", MIME_JSON);
        connection.setRequestProperty("X-Upload-Content-Type", mimeType);
        connection.setRequestProperty("X-Upload-Content-Length", Long.toString(sizeBytes));
        connection.setDoOutput(true);
        byte[] body = jsonBytes(metadata);
        connection.setFixedLengthStreamingMode(body.length);
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            ensureSuccess(connection);
            String location = connection.getHeaderField("Location");
            if (location == null || location.trim().isEmpty()) {
                throw new IOException("Drive did not return a resumable upload session");
            }
            return location;
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Sends one range and returns the absolute number of bytes Drive has committed afterwards.
     *
     * <p>Exclusive, not the inclusive index the {@code Range} header carries, so the caller never
     * has to convert between the two conventions.
     */
    private long uploadChunk(
            @NonNull String sessionUrl,
            @NonNull String mimeType,
            @NonNull byte[] buffer,
            int offset,
            int length,
            long start,
            long total)
            throws IOException {
        if (length <= 0) {
            throw new IOException("Drive resumable upload attempted an empty chunk");
        }
        HttpURLConnection connection = open("PUT", sessionUrl);
        connection.setRequestProperty("Content-Type", mimeType);
        connection.setRequestProperty(
                "Content-Range", "bytes " + start + "-" + (start + length - 1L) + "/" + total);
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(length);
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(buffer, offset, length);
            }
            int status = connection.getResponseCode();
            if (status >= 200 && status < 300) {
                return total;
            }
            if (status == HTTP_RESUME_INCOMPLETE) {
                return resumableAcknowledgedExclusive(connection.getHeaderField("Range"));
            }
            String detail = readErrorDetail(connection.getErrorStream());
            throw new DriveRequestExecutor.DriveHttpException(
                    status, connection.getHeaderField("Retry-After"), detail);
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Reads {@code Range: bytes=0-N} as an exclusive committed-byte count.
     *
     * <p>A 308 with no {@code Range} header means Drive holds nothing yet, which is zero — not a
     * negative sentinel the caller then has to special-case at offset zero.
     */
    private static long resumableAcknowledgedExclusive(@Nullable String range) throws IOException {
        if (range == null || range.trim().isEmpty()) {
            return 0L;
        }
        String value = range.trim();
        if (!value.startsWith("bytes=0-")) {
            throw new IOException("Drive returned an unsupported resumable upload range: " + value);
        }
        try {
            long inclusiveEnd = Long.parseLong(value.substring("bytes=0-".length()));
            if (inclusiveEnd < 0L) {
                throw new IOException("Drive returned a negative resumable upload range");
            }
            return inclusiveEnd + 1L;
        } catch (NumberFormatException error) {
            throw new IOException("Drive returned an invalid resumable upload range", error);
        }
    }

    /**
     * Fills {@code buffer} from {@code offset} with up to {@code count} bytes, or to end of input.
     */
    private static int readChunk(
            @NonNull InputStream input, @NonNull byte[] buffer, int offset, int count)
            throws IOException {
        int filled = 0;
        while (filled < count) {
            int read = input.read(buffer, offset + filled, count - filled);
            if (read == -1) {
                break;
            }
            filled += read;
        }
        return filled;
    }

    private static void throwIfInterrupted() throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            java.io.InterruptedIOException interrupted =
                    new java.io.InterruptedIOException("Drive resumable upload interrupted");
            throw interrupted;
        }
    }

    @NonNull
    private static JsonObject attachmentMetadata(@NonNull String folderId, @NonNull String sha256) {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("name", sha256);
        JsonArray parents = new JsonArray();
        parents.add(folderId);
        metadata.add("parents", parents);
        JsonObject properties = new JsonObject();
        properties.addProperty(PROPERTY_ATTACHMENT_SHA256, sha256);
        metadata.add("appProperties", properties);
        return metadata;
    }

    private void uploadAttachmentOrConfirm(
            @NonNull String folderId,
            @NonNull String sha256,
            @NonNull InputStream content,
            long sizeBytes)
            throws IOException {
        if (sizeBytes < 0L) {
            throw new IllegalArgumentException("An attachment upload needs a declared size");
        }
        try {
            uploadStream(folderId, sha256, MIME_BINARY, content, sizeBytes);
        } catch (IOException uploadFailure) {
            // Attachment identity is its SHA-256. A successful request whose response was lost is
            // confirmed by discovery, not repeated with an already-consumed stream.
            if (!mayHaveCommitted(uploadFailure)
                    || findVerifiedAttachment(folderId, sha256, sizeBytes) == null) {
                throw uploadFailure;
            }
        }
    }

    /**
     * Whether a failed create may nonetheless have been committed by Drive.
     *
     * <p>The one answer for bundle and attachment uploads alike; they used to classify differently,
     * so a 429 on the bundle POST was followed by rediscovery while the same 429 on an attachment
     * POST failed the sync outright. A response Drive definitely never acted on — a rejected
     * request, a bad token, a blob that failed its own checksum — is a plain failure. Everything
     * else, including a read timeout that arrived after the whole body was sent, is worth one
     * listing to find out.
     */
    static boolean mayHaveCommitted(@NonNull IOException failure) {
        if (failure instanceof AttachmentIntegrityException) {
            return false;
        }
        // A subclass of InterruptedIOException, so it has to be asked about first: a timeout
        // waiting for the response is precisely the case where the upload may have landed.
        if (failure instanceof java.net.SocketTimeoutException) {
            return true;
        }
        if (failure instanceof java.io.InterruptedIOException) {
            return false;
        }
        if (failure instanceof DriveRequestExecutor.DriveHttpException) {
            DriveRequestExecutor.DriveHttpException http =
                    (DriveRequestExecutor.DriveHttpException) failure;
            int status = http.statusCode;
            if (status >= 500 || status == 408 || status == 429) {
                return true;
            }
            return status == 403 && http.isRateLimit();
        }
        return true;
    }

    /**
     * Writes one {@code multipart/related} upload straight to the socket.
     *
     * <p>The body length is computed from the declared size so {@link
     * HttpURLConnection#setFixedLengthStreamingMode(long)} can be used. Without it {@code
     * HttpURLConnection} buffers the entire request in memory to work out a Content-Length, which
     * would put the whole attachment back on the heap and defeat the streaming read path.
     */
    private void uploadMultipart(
            @NonNull String folderId,
            @NonNull String name,
            @NonNull String mimeType,
            @NonNull InputStream content,
            long sizeBytes,
            boolean bundleFile)
            throws IOException {
        String boundary = "mynotes-" + System.nanoTime();
        JsonObject metadata = new JsonObject();
        metadata.addProperty("name", name);
        JsonArray parents = new JsonArray();
        parents.add(folderId);
        metadata.add("parents", parents);

        JsonObject appProperties = new JsonObject();
        if (bundleFile) {
            appProperties.addProperty(PROPERTY_BUNDLE, "1");
            appProperties.addProperty(PROPERTY_BUNDLE_PUBLISHED_AT, Long.toString(clock.millis()));
        } else {
            appProperties.addProperty(PROPERTY_ATTACHMENT_SHA256, name);
        }
        metadata.add("appProperties", appProperties);

        byte[] head = partHeader(boundary, MIME_JSON);
        byte[] metadataBytes = jsonBytes(metadata);
        byte[] separator = "\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] contentHeader = partHeader(boundary, mimeType);
        byte[] closing = ("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

        HttpURLConnection connection =
                open("POST", uploadBase + "?uploadType=multipart&fields=id,name");
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(
                (long) head.length
                        + metadataBytes.length
                        + separator.length
                        + contentHeader.length
                        + sizeBytes
                        + separator.length
                        + closing.length);

        try {
            try (OutputStream out = connection.getOutputStream()) {
                out.write(head);
                out.write(metadataBytes);
                out.write(separator);
                out.write(contentHeader);
                copy(content, out);
                out.write(separator);
                out.write(closing);
            }
            readJsonResponse(connection);
        } finally {
            connection.disconnect();
        }
    }

    @NonNull
    private static byte[] partHeader(@NonNull String boundary, @NonNull String mimeType) {
        return ("--" + boundary + "\r\nContent-Type: " + mimeType + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static void copy(@NonNull InputStream source, @NonNull OutputStream target)
            throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = source.read(buffer)) != -1) {
            target.write(buffer, 0, read);
        }
    }

    @NonNull
    private static String nextBundleName() {
        return BUNDLE_NAME.substring(0, BUNDLE_NAME.length() - ".zip".length())
                + "."
                + UUID.randomUUID()
                + ".zip";
    }

    @NonNull
    private JsonObject requestJson(
            @NonNull String method,
            @NonNull String url,
            @Nullable String contentType,
            @Nullable byte[] body)
            throws IOException {
        HttpURLConnection connection = open(method, url);
        try {
            if (contentType != null) {
                connection.setRequestProperty("Content-Type", contentType);
            }
            if (body != null) {
                connection.setDoOutput(true);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body);
                }
            }
            return readJsonResponse(connection);
        } finally {
            connection.disconnect();
        }
    }

    @NonNull
    private JsonObject requestJsonIdempotent(
            @NonNull String method,
            @NonNull String url,
            @Nullable String contentType,
            @Nullable byte[] body)
            throws IOException {
        return requestExecutor.executeIdempotent(() -> requestJson(method, url, contentType, body));
    }

    @NonNull
    private JsonObject readJsonResponse(@NonNull HttpURLConnection connection) throws IOException {
        ensureSuccess(connection);
        try (InputStream input = connection.getInputStream()) {
            return GSON.fromJson(
                    new String(
                            readBounded(
                                    input,
                                    MAX_BUNDLE_RESPONSE_BYTES,
                                    "Drive response exceeds the sync size limit"),
                            StandardCharsets.UTF_8),
                    JsonObject.class);
        }
    }

    @NonNull
    private byte[] requestBytes(@NonNull String method, @NonNull String url, int maxBytes)
            throws IOException {
        return requestExecutor.executeIdempotent(() -> requestBytesOnce(method, url, maxBytes));
    }

    @NonNull
    private byte[] requestBytesOnce(@NonNull String method, @NonNull String url, int maxBytes)
            throws IOException {
        HttpURLConnection connection = open(method, url);
        try {
            ensureSuccess(connection);
            try (InputStream input = connection.getInputStream()) {
                return readBounded(input, maxBytes, "Drive response exceeds the sync size limit");
            }
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(@NonNull String method, @NonNull String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(20_000);
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        return connection;
    }

    private HttpURLConnection openSuccessful(@NonNull String method, @NonNull String url)
            throws IOException {
        HttpURLConnection connection = open(method, url);
        try {
            ensureSuccess(connection);
            return connection;
        } catch (IOException failure) {
            connection.disconnect();
            throw failure;
        }
    }

    private static void ensureSuccess(@NonNull HttpURLConnection connection) throws IOException {
        int code = connection.getResponseCode();
        if (code >= 200 && code < 300) {
            return;
        }

        String detail = readErrorDetail(connection.getErrorStream());
        throw new DriveRequestExecutor.DriveHttpException(
                code, connection.getHeaderField("Retry-After"), detail);
    }

    /**
     * Reads a short, single-line excuse out of a Drive error response.
     *
     * <p>The whole body used to end up in this exception's message, which is shown in a Snackbar
     * and persisted as {@code sync_state.errorMessage} — where the account screen then renders it
     * as the sync status. A quota or permission response is a multi-line JSON document, so the
     * status label became an unreadable blob that stayed until the next successful sync.
     */
    @NonNull
    private static String readErrorDetail(@Nullable InputStream error) {
        if (error == null) {
            return "";
        }
        try (InputStream stream = error) {
            byte[] buffer = new byte[MAX_ERROR_DETAIL_BYTES];
            int read = 0;
            while (read < buffer.length) {
                int count = stream.read(buffer, read, buffer.length - read);
                if (count == -1) {
                    break;
                }
                read += count;
            }
            String detail =
                    new String(buffer, 0, read, StandardCharsets.UTF_8)
                            .replaceAll("\\s+", " ")
                            .trim();
            return detail.length() > MAX_ERROR_DETAIL_CHARS
                    ? detail.substring(0, MAX_ERROR_DETAIL_CHARS) + "…"
                    : detail;
        } catch (IOException ignored) {
            return "";
        }
    }

    /** Reads to end of stream, refusing anything past {@code maxBytes}; never closes the input. */
    @NonNull
    private static byte[] readBounded(
            @NonNull InputStream input, long maxBytes, @NonNull String limitMessage)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() > maxBytes - read) {
                throw new IOException(limitMessage);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    @NonNull
    private static byte[] jsonBytes(@NonNull JsonObject object) {
        return GSON.toJson(object).getBytes(StandardCharsets.UTF_8);
    }

    @NonNull
    private static JsonObject appProperties(@NonNull String key, @NonNull String value) {
        JsonObject appProperties = new JsonObject();
        appProperties.addProperty(key, value);
        return appProperties;
    }

    @NonNull
    private static String appPropertyClause(@NonNull String key, @NonNull String value) {
        return "appProperties has { key='"
                + escapeQuery(key)
                + "' and value='"
                + escapeQuery(value)
                + "' }";
    }

    @NonNull
    private static String escapeQuery(@NonNull String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    @Nullable
    private static String optionalString(@NonNull JsonObject object, @NonNull String field) {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive()
                ? null
                : value.getAsString();
    }

    @Nullable
    private static Long optionalLong(@NonNull JsonObject object, @NonNull String field) {
        String value = optionalString(object, field);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException malformed) {
            return null;
        }
    }

    /**
     * A response body that stays attached to its connection until the reader is done.
     *
     * <p>Lets an attachment be piped straight from the socket to disk while still enforcing the
     * response ceiling, and releases the connection on close. Exceeding the ceiling is an integrity
     * failure like any other: a candidate that grows past it is skipped in favour of the next copy
     * rather than failing the sync, as a plain I/O error used to.
     */
    private static final class ConnectionInputStream extends FilterInputStream {
        private final HttpURLConnection connection;
        private final long maxBytes;
        private long byteCount;

        private ConnectionInputStream(@NonNull HttpURLConnection connection, long maxBytes)
                throws IOException {
            super(connection.getInputStream());
            this.connection = connection;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
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

        private void count(int read) throws IOException {
            byteCount += read;
            if (byteCount > maxBytes) {
                throw new AttachmentIntegrityException("Attachment exceeds the sync size limit");
            }
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                connection.disconnect();
            }
        }
    }
}
