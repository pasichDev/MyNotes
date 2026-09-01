package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
    private static final Gson GSON = new Gson();

    private final String accessToken;
    private final String apiBase;
    private final String uploadBase;
    private final Clock clock;
    private final SyncBundleCodec bundleCodec;
    private final SyncMerger merger = new SyncMerger();

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
        for (RemoteFileRef bundle : findBundles(folderId)) {
            byte[] bytes =
                    requestBytes(
                            "GET",
                            apiBase + "/files/" + bundle.id + "?alt=media",
                            MAX_BUNDLE_RESPONSE_BYTES);
            SyncSnapshot decoded = bundleCodec.decode(new ByteArrayInputStream(bytes)).getSnapshot();
            merged = merger.merge(merged, decoded).getMergedSnapshot();
        }
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
        uploadFile(folderId, nextBundleName(), MIME_ZIP, bundle, null, null, true);
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

        RemoteFileRef attachment = findAttachment(folderId, sha256);
        if (attachment == null) {
            return null;
        }
        return new ByteArrayInputStream(
                requestBytes(
                        "GET",
                        apiBase + "/files/" + attachment.id + "?alt=media",
                        MAX_ATTACHMENT_RESPONSE_BYTES));
    }

    @Override
    public synchronized void writeAttachment(@NonNull String sha256, @NonNull InputStream content)
            throws IOException {
        String folderId = ensureFolderId();
        if (findAttachment(folderId, sha256) != null) {
            return;
        }
        uploadFile(folderId, sha256, MIME_BINARY, readFully(content), null, null, false);
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
        if (folders.size() > 1) {
            throw new IOException("Drive sync folder is duplicated");
        }
        return folders.size() == 0
                ? null
                : folders.get(0).getAsJsonObject().get("id").getAsString();
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
        return uploadMetadata(metadata).id;
    }

    @Nullable
    private List<RemoteFileRef> findBundles(@NonNull String folderId) throws IOException {
        JsonArray bundles =
                listFiles(
                        "'"
                                + folderId
                                + "' in parents and trashed = false and "
                                + appPropertyClause("mynotesBundle", "1"),
                        "files(id,name)");
        List<RemoteFileRef> result = new ArrayList<>(bundles.size());
        for (int index = 0; index < bundles.size(); index++) {
            JsonObject item = bundles.get(index).getAsJsonObject();
            result.add(fetchFileRef(item.get("id").getAsString(), item.get("name").getAsString()));
        }
        result.sort(Comparator.comparing(ref -> ref.id));
        return result;
    }

    @Nullable
    private RemoteFileRef findAttachment(@NonNull String folderId, @NonNull String sha256)
            throws IOException {
        JsonArray files =
                listFiles(
                        "'"
                                + folderId
                                + "' in parents and trashed = false and "
                                + appPropertyClause("mynotesAttachmentSha256", sha256),
                        "files(id,name)");
        if (files.size() == 0) {
            return null;
        }
        if (files.size() > 1) {
            throw new IOException("Drive attachment is duplicated: " + sha256);
        }
        JsonObject item = files.get(0).getAsJsonObject();
        return fetchFileRef(item.get("id").getAsString(), item.get("name").getAsString());
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
                url += "&pageToken=" + URLEncoder.encode(nextPageToken, StandardCharsets.UTF_8.name());
            }
            JsonObject response = requestJson("GET", url, null, null, null);
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
    private RemoteFileRef uploadMetadata(@NonNull JsonObject metadata) throws IOException {
        JsonObject created =
                requestJson(
                        "POST",
                        apiBase + "/files?fields=id,name",
                        MIME_JSON,
                        jsonBytes(metadata),
                        null);
        String id = created.get("id").getAsString();
        String name = created.get("name").getAsString();
        return fetchFileRef(id, name);
    }

    @NonNull
    private RemoteFileRef uploadFile(
            @NonNull String folderId,
            @NonNull String name,
            @NonNull String mimeType,
            @NonNull byte[] data,
            @Nullable String fileId,
            @Nullable String ifMatch,
            boolean bundleFile)
            throws IOException {
        String boundary = "mynotes-" + System.nanoTime();
        JsonObject metadata = new JsonObject();
        metadata.addProperty("name", name);
        if (fileId == null) {
            JsonArray parents = new JsonArray();
            parents.add(folderId);
            metadata.add("parents", parents);
        }

        JsonObject appProperties = new JsonObject();
        if (bundleFile) {
            appProperties.addProperty("mynotesBundle", "1");
            appProperties.addProperty("mynotesBundlePublishedAt", Long.toString(clock.millis()));
        } else {
            appProperties.addProperty("mynotesAttachmentSha256", name);
        }
        metadata.add("appProperties", appProperties);

        String path =
                fileId == null
                        ? "?uploadType=multipart&fields=id,name"
                        : "/" + fileId + "?uploadType=multipart&fields=id,name";
        HttpURLConnection connection = open(fileId == null ? "POST" : "PATCH", uploadBase + path);
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);
        if (ifMatch != null) {
            connection.setRequestProperty("If-Match", ifMatch);
        }
        connection.setDoOutput(true);
        try (OutputStream out = connection.getOutputStream()) {
            writePart(out, boundary, MIME_JSON, jsonBytes(metadata));
            writePart(out, boundary, mimeType, data);
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }
        JsonObject response = readJsonResponse(connection);
        return fetchFileRef(response.get("id").getAsString(), response.get("name").getAsString());
    }

    @NonNull
    private static String nextBundleName() {
        return BUNDLE_NAME.substring(0, BUNDLE_NAME.length() - ".zip".length())
                + "."
                + UUID.randomUUID()
                + ".zip";
    }

    @NonNull
    private RemoteFileRef fetchFileRef(@NonNull String id, @NonNull String fallbackName)
            throws IOException {
        HttpURLConnection connection =
                open("GET", apiBase + "/files/" + id + "?fields=id,name,version,appProperties");
        JsonObject response = readJsonResponse(connection);
        String name =
                response.has("name") && !response.get("name").isJsonNull()
                        ? response.get("name").getAsString()
                        : fallbackName;
        String version =
                response.has("version") && !response.get("version").isJsonNull()
                        ? response.get("version").getAsString()
                        : null;
        if (version == null || version.trim().isEmpty()) {
            throw new IOException("Drive file metadata response is missing a version");
        }
        return new RemoteFileRef(id, name, version);
    }

    @NonNull
    private JsonObject requestJson(
            @NonNull String method,
            @NonNull String url,
            @Nullable String contentType,
            @Nullable byte[] body,
            @Nullable String ifMatch)
            throws IOException {
        HttpURLConnection connection = open(method, url);
        if (contentType != null) {
            connection.setRequestProperty("Content-Type", contentType);
        }
        if (ifMatch != null) {
            connection.setRequestProperty("If-Match", ifMatch);
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

        String detail = "";
        InputStream error = connection.getErrorStream();
        if (error != null) {
            detail = new String(readFully(error), StandardCharsets.UTF_8);
        }
        if (code == HttpURLConnection.HTTP_PRECON_FAILED) {
            throw new IOException("Drive snapshot changed since it was read");
        }
        throw new IOException("Drive API HTTP " + code + (detail.isEmpty() ? "" : ": " + detail));
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

    private static void writePart(
            @NonNull OutputStream out,
            @NonNull String boundary,
            @NonNull String mimeType,
            byte[] data)
            throws IOException {
        out.write(
                ("--" + boundary + "\r\nContent-Type: " + mimeType + "\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static final class RemoteFileRef {
        private final String id;
        private final String name;
        private final String eTag;

        private RemoteFileRef(@NonNull String id, @NonNull String name, @NonNull String eTag) {
            this.id = id;
            this.name = name;
            this.eTag = eTag;
        }
    }
}
