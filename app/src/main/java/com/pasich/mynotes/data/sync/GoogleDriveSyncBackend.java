package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
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
    private static final int MAX_BUNDLE_RESPONSE_BYTES = 32 * 1024 * 1024;
    private static final int MAX_ATTACHMENT_RESPONSE_BYTES = 100 * 1024 * 1024;
    private static final int RESUMABLE_CHUNK_BYTES = 256 * 1024;
    private static final int HTTP_RESUME_INCOMPLETE = 308;
    private static final int MAX_ERROR_DETAIL_BYTES = 1024;
    private static final int MAX_ERROR_DETAIL_CHARS = 200;
    private static final Gson GSON = new Gson();

    private final String accessToken;
    private final String apiBase;
    private final String uploadBase;
    private final Clock clock;
    private final SyncBundleCodec bundleCodec;
    private final DriveRequestExecutor requestExecutor;
    private final SyncMerger merger = new SyncMerger();
    private List<String> lastReadFrontierBundleIds = Collections.emptyList();

    public GoogleDriveSyncBackend(@NonNull String accessToken) {
        this(accessToken, DEFAULT_API, DEFAULT_UPLOAD, Clock.systemUTC(), new SyncBundleCodec());
    }

    GoogleDriveSyncBackend(
            @NonNull String accessToken,
            @NonNull String apiBase,
            @NonNull String uploadBase,
            @NonNull Clock clock,
            @NonNull SyncBundleCodec bundleCodec) {
        if (accessToken.trim().isEmpty()) {
            throw new IllegalArgumentException("accessToken is empty");
        }
        this.accessToken = accessToken;
        this.apiBase = apiBase;
        this.uploadBase = uploadBase;
        this.clock = clock;
        this.bundleCodec = bundleCodec;
        this.requestExecutor = new DriveRequestExecutor();
    }

    @NonNull
    @Override
    public String getIdentifier() {
        return "google-drive";
    }

    @NonNull
    @Override
    public synchronized SyncSnapshot readSnapshot() throws IOException {
        return readSnapshotResult().getSnapshot();
    }

    @Override
    public synchronized RemoteSnapshot readSnapshotResult() throws IOException {
        List<String> folderIds = findFolderIds();
        if (folderIds.isEmpty()) {
            lastReadFrontierBundleIds = Collections.emptyList();
            return RemoteSnapshot.of(SyncSnapshot.empty());
        }

        Map<String, SyncBundleCodec.DecodedBundle> bundlesByLogicalId = new HashMap<>();
        Map<String, byte[]> bytesByLogicalId = new HashMap<>();
        for (String folderId : folderIds) {
            for (String bundleId : findBundles(folderId)) {
                byte[] bytes =
                        requestBytes(
                                "GET",
                                apiBase + "/files/" + bundleId + "?alt=media",
                                MAX_BUNDLE_RESPONSE_BYTES);
                SyncBundleCodec.DecodedBundle decoded =
                        bundleCodec.decode(new ByteArrayInputStream(bytes));
                byte[] previousBytes = bytesByLogicalId.putIfAbsent(decoded.getBundleId(), bytes);
                if (previousBytes != null) {
                    if (!java.util.Arrays.equals(previousBytes, bytes)) {
                        throw new IOException("Drive contains conflicting physical copies of one bundle");
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
            SyncMergeResult result = merger.merge(merged, bundlesByLogicalId.get(bundleId).getSnapshot());
            merged = result.getMergedSnapshot();
            conflicts.addAll(result.getConflicts());
        }
        lastReadFrontierBundleIds = Collections.unmodifiableList(new ArrayList<>(frontier));
        return new RemoteSnapshot(merged, conflicts, frontier);
    }

    @Override
    public synchronized void writeSnapshot(@NonNull SyncSnapshot snapshot) throws IOException {
        String folderId = ensureCanonicalFolderId();
        // A first-sync race can leave valid bundles and immutable blobs in two owned folders.
        // The read path always merges all roots. Before canonical publication, materialize every
        // referenced blob in the canonical root as well, so no future cleanup decision can make
        // the canonical bundle point at an object that exists only in a duplicate root.
        ensureCanonicalAttachments(folderId, snapshot);
        byte[] bundle = bundleCodec.encode(snapshot, clock.instant(), lastReadFrontierBundleIds);
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
            if (!hasBundleNamed(folderId, bundleName)) {
                throw uploadFailure;
            }
        }
    }

    @Override
    public synchronized boolean hasAttachment(@NonNull String sha256) throws IOException {
        for (String folderId : findFolderIds()) {
            if (findVerifiedAttachment(folderId, sha256, null) != null) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    @Override
    public synchronized InputStream readAttachment(@NonNull String sha256) throws IOException {
        for (String folderId : findFolderIds()) {
            String attachmentId = findVerifiedAttachment(folderId, sha256, null);
            if (attachmentId == null) {
                continue;
            }
            // Streamed, not buffered: reading a 100 MB attachment into a byte[] (which the growing
            // ByteArrayOutputStream first doubled, then copied) was the largest single allocation
            // in
            // the sync and an OutOfMemoryError on an ordinary phone.
            HttpURLConnection connection =
                    requestExecutor.executeIdempotent(
                            () ->
                                    openSuccessful(
                                            "GET",
                                            apiBase + "/files/" + attachmentId + "?alt=media"));
            try {
                return new ConnectionInputStream(connection, MAX_ATTACHMENT_RESPONSE_BYTES);
            } catch (IOException failure) {
                connection.disconnect();
                throw failure;
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
        if (sizeBytes >= 0L) {
            if (sizeBytes > MAX_ATTACHMENT_RESPONSE_BYTES) {
                throw new IOException("Attachment exceeds the 100 MiB sync upload limit");
            }
            uploadAttachmentOrConfirm(folderId, sha256, content, sizeBytes);
            return;
        }
        // No declared size, so the multipart content length cannot be computed up front. Rare:
        // sizes come from the bundle manifest, which also supplies the hashes being uploaded.
        uploadAttachmentOrConfirm(
                folderId, sha256, readFullyLimited(content, MAX_ATTACHMENT_RESPONSE_BYTES));
    }

    private List<String> findFolderIds() throws IOException {
        JsonArray folders =
                listFiles(
                        "mimeType = '"
                                + MIME_FOLDER
                                + "' and trashed = false and "
                                + appPropertyClause("mynotesOwner", "1"),
                        "files(id,name)");
        List<String> result = new ArrayList<>(folders.size());
        for (int index = 0; index < folders.size(); index++) {
            result.add(folders.get(index).getAsJsonObject().get("id").getAsString());
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }

    /** Deterministically selects one byte-identical content-addressed attachment duplicate. */
    @Nullable
    private static String smallestId(@NonNull JsonArray files) {
        String selected = null;
        for (int index = 0; index < files.size(); index++) {
            String id = files.get(index).getAsJsonObject().get("id").getAsString();
            if (selected == null || id.compareTo(selected) < 0) {
                selected = id;
            }
        }
        return selected;
    }

    @NonNull
    private String ensureCanonicalFolderId() throws IOException {
        List<String> folderIds = findFolderIds();
        if (!folderIds.isEmpty()) {
            return folderIds.get(0);
        }

        JsonObject metadata = new JsonObject();
        metadata.addProperty("name", FOLDER_NAME);
        metadata.addProperty("mimeType", MIME_FOLDER);
        metadata.add("appProperties", appProperties("mynotesOwner", "1"));
        try {
            return uploadMetadata(metadata);
        } catch (IOException createFailure) {
            // Folder POST can have committed before a lost response. Duplicate roots are a
            // supported read state; rediscovery avoids a blind retry creating another one.
            folderIds = findFolderIds();
            if (!folderIds.isEmpty()) {
                return folderIds.get(0);
            }
            throw createFailure;
        }
    }

    private void ensureCanonicalAttachments(
            @NonNull String canonicalRootId, @NonNull SyncSnapshot snapshot) throws IOException {
        Map<String, Long> sizes = attachmentSizes(snapshot);
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
            try (VerifiedAttachmentInputStream input =
                    new VerifiedAttachmentInputStream(source, hash, attachment.getValue())) {
                uploadAttachmentOrConfirm(canonicalRootId, hash, input, attachment.getValue());
                input.verifyEndOfStream();
            }
        }
    }

    @NonNull
    private static Map<String, Long> attachmentSizes(@NonNull SyncSnapshot snapshot)
            throws IOException {
        Map<String, Long> sizes = new HashMap<>();
        for (SyncRecord record : snapshot.getLiveRecords(SyncRecord.Type.NOTE)) {
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
                if (size < 0L || size > MAX_ATTACHMENT_RESPONSE_BYTES) {
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

    private static void validateBundleDag(
            @NonNull Map<String, SyncBundleCodec.DecodedBundle> bundles) throws IOException {
        for (SyncBundleCodec.DecodedBundle bundle : bundles.values()) {
            for (String parent : bundle.getParentBundleIds()) {
                if (!bundles.containsKey(parent)) {
                    throw new IOException("Drive bundle references an unavailable ancestor");
                }
            }
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String bundleId : bundles.keySet()) {
            validateAcyclic(bundleId, bundles, visiting, visited);
        }
    }

    private static void validateAcyclic(
            @NonNull String bundleId,
            @NonNull Map<String, SyncBundleCodec.DecodedBundle> bundles,
            @NonNull Set<String> visiting,
            @NonNull Set<String> visited)
            throws IOException {
        if (visited.contains(bundleId)) return;
        if (!visiting.add(bundleId)) throw new IOException("Drive bundle ancestry contains a cycle");
        for (String parent : bundles.get(bundleId).getParentBundleIds()) {
            validateAcyclic(parent, bundles, visiting, visited);
        }
        visiting.remove(bundleId);
        visited.add(bundleId);
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

    @NonNull
    private List<String> findBundles(@NonNull String folderId) throws IOException {
        JsonArray bundles =
                listFiles(
                        "'"
                                + folderId
                                + "' in parents and trashed = false and "
                                + appPropertyClause("mynotesBundle", "1"),
                        "files(id,name)");
        List<String> result = new ArrayList<>(bundles.size());
        for (int index = 0; index < bundles.size(); index++) {
            result.add(bundles.get(index).getAsJsonObject().get("id").getAsString());
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }

    @Nullable
    private String findAttachment(@NonNull String folderId, @NonNull String sha256)
            throws IOException {
        JsonArray files =
                listFiles(
                        "'"
                                + folderId
                                + "' in parents and trashed = false and "
                                + appPropertyClause("mynotesAttachmentSha256", sha256),
                        "files(id,name)");
        // Attachments are content-addressed, so duplicates uploaded by two devices racing on the
        // same hash are byte-identical and either one will do. Rejecting them used to break every
        // subsequent sync permanently.
        return smallestId(files);
    }

    /**
     * An app property is only an index. Read and verify every candidate before it may satisfy a
     * content-addressed reference; corrupt candidates remain harmless Drive orphans.
     */
    @Nullable
    private String findVerifiedAttachment(
            @NonNull String folderId, @NonNull String sha256, @Nullable Long expectedSize)
            throws IOException {
        JsonArray files =
                listFiles(
                        "'"
                                + folderId
                                + "' in parents and trashed = false and "
                                + appPropertyClause("mynotesAttachmentSha256", sha256),
                        "files(id,name)");
        List<String> candidateIds = new ArrayList<>(files.size());
        for (int index = 0; index < files.size(); index++) {
            candidateIds.add(files.get(index).getAsJsonObject().get("id").getAsString());
        }
        candidateIds.sort(Comparator.naturalOrder());
        for (String candidateId : candidateIds) {
            try (InputStream candidate = openAttachment(candidateId)) {
                verifyAttachment(candidate, sha256, expectedSize);
                return candidateId;
            } catch (AttachmentIntegrityException corrupt) {
                // A second content-addressed duplicate may be valid. Never accept the property
                // alone and never delete this object during a correctness path.
            }
        }
        return null;
    }

    @NonNull
    private InputStream openAttachment(@NonNull String attachmentId) throws IOException {
        HttpURLConnection connection =
                requestExecutor.executeIdempotent(
                        () ->
                                openSuccessful(
                                        "GET", apiBase + "/files/" + attachmentId + "?alt=media"));
        try {
            return new ConnectionInputStream(connection, MAX_ATTACHMENT_RESPONSE_BYTES);
        } catch (IOException failure) {
            connection.disconnect();
            throw failure;
        }
    }

    private static void verifyAttachment(
            @NonNull InputStream input, @NonNull String expectedHash, @Nullable Long expectedSize)
            throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("SHA-256 is unavailable", error);
        }
        long size = 0L;
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
            size += read;
            if (size > MAX_ATTACHMENT_RESPONSE_BYTES) {
                throw new AttachmentIntegrityException("Attachment exceeds the sync size limit");
            }
        }
        String actual = toHex(digest.digest());
        if (!expectedHash.equals(actual)) {
            throw new AttachmentIntegrityException(
                    "Attachment checksum does not match its declared hash");
        }
        if (expectedSize != null && expectedSize.longValue() != size) {
            throw new AttachmentIntegrityException("Attachment size does not match its declared size");
        }
    }

    @NonNull
    private static String toHex(@NonNull byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte byteValue : bytes) {
            value.append(String.format(Locale.US, "%02x", byteValue & 0xff));
        }
        return value.toString();
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

    private boolean hasBundleNamed(@NonNull String folderId, @NonNull String name)
            throws IOException {
        JsonArray bundles =
                listFiles(
                        "'"
                                + folderId
                                + "' in parents and trashed = false and name = '"
                                + escapeQuery(name)
                                + "' and "
                                + appPropertyClause("mynotesBundle", "1"),
                        "files(id)");
        return bundles.size() > 0;
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
        uploadResumableAttachment(folderId, name, mimeType, content, sizeBytes);
    }

    /**
     * Uploads a bounded attachment in resumable chunks. Only one chunk is retained in heap, so a
     * dropped connection can be probed and the unacknowledged chunk replayed without re-reading the
     * source stream.
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
        byte[] chunk = new byte[RESUMABLE_CHUNK_BYTES];
        long offset = 0L;
        while (offset < sizeBytes) {
            throwIfInterrupted();
            int chunkSize =
                    readChunk(content, chunk, (int) Math.min(chunk.length, sizeBytes - offset));
            if (chunkSize <= 0) {
                throw new IOException("Attachment ended before its declared size");
            }
            long acknowledged =
                    uploadChunk(sessionUrl, mimeType, chunk, chunkSize, offset, sizeBytes);
            if (acknowledged < offset - 1L || acknowledged >= offset + chunkSize) {
                throw new IOException(
                        "Drive resumable upload returned an invalid acknowledged range");
            }
            if (acknowledged < offset + chunkSize - 1L) {
                // The server received only a prefix. The unread suffix remains in this one chunk;
                // replay it rather than advancing the source stream.
                int consumed = (int) (acknowledged - offset + 1L);
                System.arraycopy(chunk, consumed, chunk, 0, chunkSize - consumed);
                int remaining = chunkSize - consumed;
                while (remaining > 0) {
                    acknowledged =
                            uploadChunk(
                                    sessionUrl,
                                    mimeType,
                                    chunk,
                                    remaining,
                                    acknowledged + 1L,
                                    sizeBytes);
                    if (acknowledged < offset + chunkSize - 1L) {
                        int newlyConsumed = (int) (acknowledged - offset - consumed + 1L);
                        System.arraycopy(chunk, newlyConsumed, chunk, 0, remaining - newlyConsumed);
                        remaining -= newlyConsumed;
                        consumed += newlyConsumed;
                    }
                }
            }
            offset += chunkSize;
        }
        if (content.read() != -1) {
            throw new IOException("Attachment exceeds its declared size");
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

    private long uploadChunk(
            @NonNull String sessionUrl,
            @NonNull String mimeType,
            @NonNull byte[] chunk,
            int chunkSize,
            long start,
            long total)
            throws IOException {
        HttpURLConnection connection = open("PUT", sessionUrl);
        connection.setRequestProperty("Content-Type", mimeType);
        connection.setRequestProperty(
                "Content-Range", "bytes " + start + "-" + (start + chunkSize - 1L) + "/" + total);
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(chunkSize);
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(chunk, 0, chunkSize);
            }
            int status = connection.getResponseCode();
            if (status >= 200 && status < 300) {
                return total - 1L;
            }
            if (status == HTTP_RESUME_INCOMPLETE) {
                String range = connection.getHeaderField("Range");
                return resumableRangeEnd(range);
            }
            String detail = readErrorDetail(connection.getErrorStream());
            throw new DriveRequestExecutor.DriveHttpException(
                    status, connection.getHeaderField("Retry-After"), detail);
        } finally {
            connection.disconnect();
        }
    }

    private static long resumableRangeEnd(@Nullable String range) throws IOException {
        if (range == null || !range.startsWith("bytes=0-")) {
            return -1L;
        }
        try {
            return Long.parseLong(range.substring("bytes=0-".length()));
        } catch (NumberFormatException error) {
            throw new IOException("Drive returned an invalid resumable upload range", error);
        }
    }

    private static int readChunk(@NonNull InputStream input, @NonNull byte[] buffer, int maximum)
            throws IOException {
        int offset = 0;
        while (offset < maximum) {
            int read = input.read(buffer, offset, maximum - offset);
            if (read == -1) {
                break;
            }
            offset += read;
        }
        return offset;
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
        properties.addProperty("mynotesAttachmentSha256", sha256);
        metadata.add("appProperties", properties);
        return metadata;
    }

    private void uploadAttachmentOrConfirm(
            @NonNull String folderId,
            @NonNull String sha256,
            @NonNull InputStream content,
            long sizeBytes)
            throws IOException {
        try {
            if (sizeBytes >= 0L) {
                uploadStream(folderId, sha256, MIME_BINARY, content, sizeBytes);
            } else {
                uploadFile(folderId, sha256, MIME_BINARY, readFully(content), false);
            }
        } catch (IOException uploadFailure) {
            // Attachment identity is its SHA-256. A successful request whose response was lost is
            // confirmed by discovery, not repeated with an already-consumed stream.
            if (!isAmbiguousTransportFailure(uploadFailure)
                    || findVerifiedAttachment(folderId, sha256, sizeBytes) == null) {
                throw uploadFailure;
            }
        }
    }

    private void uploadAttachmentOrConfirm(
            @NonNull String folderId, @NonNull String sha256, @NonNull byte[] content)
            throws IOException {
        try {
            uploadFile(folderId, sha256, MIME_BINARY, content, false);
        } catch (IOException uploadFailure) {
            if (!isAmbiguousTransportFailure(uploadFailure)
                    || findVerifiedAttachment(folderId, sha256, (long) content.length) == null) {
                throw uploadFailure;
            }
        }
    }

    private static boolean isAmbiguousTransportFailure(@NonNull IOException failure) {
        if (failure instanceof AttachmentIntegrityException
                || failure instanceof java.io.InterruptedIOException) {
            return false;
        }
        if (failure instanceof DriveRequestExecutor.DriveHttpException) {
            int status = ((DriveRequestExecutor.DriveHttpException) failure).statusCode;
            return status >= 500 && status <= 599;
        }
        return failure instanceof java.net.SocketException
                || failure instanceof java.net.SocketTimeoutException
                || failure instanceof java.net.ConnectException;
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
            appProperties.addProperty("mynotesBundle", "1");
            appProperties.addProperty("mynotesBundlePublishedAt", Long.toString(clock.millis()));
        } else {
            appProperties.addProperty("mynotesAttachmentSha256", name);
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
                    new String(readFully(input), StandardCharsets.UTF_8), JsonObject.class);
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
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (output.size() > maxBytes - read) {
                        throw new IOException("Drive response exceeds the sync size limit");
                    }
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
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

    @NonNull
    private static byte[] readFully(@NonNull InputStream input) throws IOException {
        try (InputStream stream = input;
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    @NonNull
    private static byte[] readFullyLimited(@NonNull InputStream input, int maxBytes)
            throws IOException {
        try (InputStream stream = input;
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                if (output.size() > maxBytes - read) {
                    throw new IOException("Attachment exceeds the 100 MiB sync upload limit");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
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

    /**
     * A response body that stays attached to its connection until the reader is done.
     *
     * <p>Lets an attachment be piped straight from the socket to disk while still enforcing the
     * response ceiling, and releases the connection on close.
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
                throw new IOException("Drive response exceeds the sync size limit");
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

    /** Verifies an untrusted remote blob before it may support canonical bundle publication. */
    private static final class VerifiedAttachmentInputStream extends FilterInputStream {
        private final String expectedHash;
        private final long expectedSize;
        private final MessageDigest digest;
        private long size;
        private boolean reachedEnd;

        private VerifiedAttachmentInputStream(
                @NonNull InputStream source, @NonNull String expectedHash, long expectedSize)
                throws IOException {
            super(source);
            this.expectedHash = expectedHash;
            this.expectedSize = expectedSize;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException error) {
                throw new IOException("SHA-256 is unavailable", error);
            }
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value == -1) {
                reachedEnd = true;
            } else {
                digest.update((byte) value);
                size++;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read == -1) {
                reachedEnd = true;
            } else if (read > 0) {
                digest.update(buffer, offset, read);
                size += read;
            }
            return read;
        }

        private void verifyEndOfStream() throws IOException {
            if (!reachedEnd) {
                throw new IOException("Attachment upload ended before the source was verified");
            }
            if (size != expectedSize) {
                throw new AttachmentIntegrityException("Attachment size does not match sync metadata");
            }
            StringBuilder actualHash = new StringBuilder(64);
            for (byte value : digest.digest()) {
                actualHash.append(String.format(java.util.Locale.US, "%02x", value & 0xff));
            }
            if (!expectedHash.equals(actualHash.toString())) {
                throw new AttachmentIntegrityException("Attachment checksum does not match sync metadata");
            }
        }
    }
}
