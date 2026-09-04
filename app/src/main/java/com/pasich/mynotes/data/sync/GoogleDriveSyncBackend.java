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
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    private static final int MAX_ERROR_DETAIL_BYTES = 1024;
    private static final int MAX_ERROR_DETAIL_CHARS = 200;
    private static final Gson GSON = new Gson();

    private final String accessToken;
    private final String apiBase;
    private final String uploadBase;
    private final Clock clock;
    private final SyncBundleCodec bundleCodec;
    private final SyncMerger merger = new SyncMerger();

    /**
     * Bundles merged by this instance's {@link #readSnapshot()}, safe to delete once their content
     * has been republished. One backend instance serves exactly one sync, so this can never name a
     * bundle that arrived after the read.
     */
    @Nullable private List<String> supersededBundleIds;

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
    }

    @NonNull
    @Override
    public String getIdentifier() {
        return "google-drive";
    }

    @NonNull
    @Override
    public synchronized SyncSnapshot readSnapshot() throws IOException {
        String folderId = findFolderId();
        if (folderId == null) {
            return SyncSnapshot.empty();
        }

        SyncSnapshot merged = SyncSnapshot.empty();
        List<String> readBundleIds = new ArrayList<>();
        for (String bundleId : findBundles(folderId)) {
            byte[] bytes =
                    requestBytes(
                            "GET",
                            apiBase + "/files/" + bundleId + "?alt=media",
                            MAX_BUNDLE_RESPONSE_BYTES);
            SyncSnapshot decoded =
                    bundleCodec.decode(new ByteArrayInputStream(bytes)).getSnapshot();
            merged = merger.merge(merged, decoded).getMergedSnapshot();
            readBundleIds.add(bundleId);
        }
        supersededBundleIds = readBundleIds;
        return merged;
    }

    @Override
    public synchronized void writeSnapshot(@NonNull SyncSnapshot snapshot) throws IOException {
        String folderId = ensureFolderId();
        byte[] bundle = bundleCodec.encode(snapshot, clock.instant());
        // Every bundle is immutable. Drive offers no conditional update based on its version
        // counter, so replacing one file leaves a race where another device can be overwritten.
        // Publishing a distinct file makes each successful upload independently durable; readers
        // merge the complete set deterministically.
        uploadFile(folderId, nextBundleName(), MIME_ZIP, bundle, true);
        discardSupersededBundles();
    }

    /**
     * Removes the bundles whose content the just-published bundle already contains.
     *
     * <p>Without this, every sync that changed anything left one more full snapshot in Drive
     * forever, and each later {@link #readSnapshot()} downloaded all of them. Cost grew without
     * bound: a user syncing daily for a year would download 365 bundles per sync.
     *
     * <p>Only the IDs {@link #readSnapshot()} actually merged in this same sync are removed, so a
     * bundle another device published in the meantime is never discarded unread. Deletion is
     * best-effort: the new bundle is already durable, and a failure here only postpones cleanup.
     */
    private void discardSupersededBundles() {
        List<String> superseded = supersededBundleIds;
        supersededBundleIds = null;
        if (superseded == null) {
            return;
        }
        for (String bundleId : superseded) {
            try {
                HttpURLConnection connection = open("DELETE", apiBase + "/files/" + bundleId);
                ensureSuccess(connection);
                connection.disconnect();
            } catch (IOException ignored) {
                // Another device may have collected it already.
            }
        }
    }

    @Override
    public synchronized boolean hasAttachment(@NonNull String sha256) throws IOException {
        String folderId = findFolderId();
        return folderId != null && findAttachment(folderId, sha256) != null;
    }

    @Nullable
    @Override
    public synchronized InputStream readAttachment(@NonNull String sha256) throws IOException {
        String folderId = findFolderId();
        if (folderId == null) {
            return null;
        }

        String attachmentId = findAttachment(folderId, sha256);
        if (attachmentId == null) {
            return null;
        }
        // Streamed, not buffered: reading a 100 MB attachment into a byte[] (which the growing
        // ByteArrayOutputStream first doubled, then copied) was the largest single allocation in
        // the sync and an OutOfMemoryError on an ordinary phone.
        HttpURLConnection connection =
                open("GET", apiBase + "/files/" + attachmentId + "?alt=media");
        ensureSuccess(connection);
        return new ConnectionInputStream(connection, MAX_ATTACHMENT_RESPONSE_BYTES);
    }

    @Override
    public synchronized void writeAttachment(
            @NonNull String sha256, long sizeBytes, @NonNull InputStream content)
            throws IOException {
        String folderId = ensureFolderId();
        if (findAttachment(folderId, sha256) != null) {
            return;
        }
        if (sizeBytes >= 0L) {
            uploadStream(folderId, sha256, MIME_BINARY, content, sizeBytes);
            return;
        }
        // No declared size, so the multipart content length cannot be computed up front. Rare:
        // sizes come from the bundle manifest, which also supplies the hashes being uploaded.
        uploadFile(folderId, sha256, MIME_BINARY, readFully(content), false);
    }

    @Nullable
    private String findFolderId() throws IOException {
        JsonArray folders =
                listFiles(
                        "mimeType = '"
                                + MIME_FOLDER
                                + "' and trashed = false and "
                                + appPropertyClause("mynotesOwner", "1"),
                        "files(id,name)");
        // Two devices whose first sync overlaps both run ensureFolderId and both create a folder.
        // Throwing here made that permanent: every later sync on every device failed before it
        // could do any work, and only manual cleanup in Drive recovered it. Converging on the
        // lexicographically smallest ID instead makes all devices agree without coordination.
        return smallestId(folders);
    }

    /** Deterministic, coordination-free choice so every device selects the same file. */
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
    private String ensureFolderId() throws IOException {
        String folderId = findFolderId();
        if (folderId != null) {
            return folderId;
        }

        JsonObject metadata = new JsonObject();
        metadata.addProperty("name", FOLDER_NAME);
        metadata.addProperty("mimeType", MIME_FOLDER);
        metadata.add("appProperties", appProperties("mynotesOwner", "1"));
        return uploadMetadata(metadata);
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
            JsonObject response = requestJson("GET", url, null, null);
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
        uploadMultipart(folderId, name, mimeType, content, sizeBytes, false);
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
        HttpURLConnection connection = open(method, url);
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
    }

    private HttpURLConnection open(@NonNull String method, @NonNull String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(20_000);
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        return connection;
    }

    private static void ensureSuccess(@NonNull HttpURLConnection connection) throws IOException {
        int code = connection.getResponseCode();
        if (code >= 200 && code < 300) {
            return;
        }

        String detail = readErrorDetail(connection.getErrorStream());
        if (code == HttpURLConnection.HTTP_PRECON_FAILED) {
            throw new IOException("Drive snapshot changed since it was read");
        }
        throw new IOException("Drive API HTTP " + code + (detail.isEmpty() ? "" : ": " + detail));
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
}
