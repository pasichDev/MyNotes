package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Google Drive REST backend for the provider-independent sync protocol. */
public final class GoogleDriveSyncBackend implements SyncBackend {
    private static final String API = "https://www.googleapis.com/drive/v3";
    private static final String UPLOAD = "https://www.googleapis.com/upload/drive/v3/files";
    private static final String MANIFEST = "MyNotes.sync.json";
    private static final String ATTACHMENT_PREFIX = "MyNotes.attachment.";
    private static final String JSON = "application/json";
    private static final Gson GSON = new Gson();

    private final String accessToken;

    public GoogleDriveSyncBackend(@NonNull String accessToken) {
        if (accessToken.trim().isEmpty())
            throw new IllegalArgumentException("accessToken is empty");
        this.accessToken = accessToken;
    }

    @NonNull
    @Override
    public String getIdentifier() {
        return "google-drive";
    }

    @NonNull
    @Override
    public SyncSnapshot readSnapshot() throws IOException {
        String id = findFile(MANIFEST);
        if (id == null) return SyncSnapshot.empty();
        String json = request("GET", API + "/files/" + id + "?alt=media", null, null);
        return decodeSnapshot(json);
    }

    @Override
    public void writeSnapshot(@NonNull SyncSnapshot snapshot) throws IOException {
        byte[] body = encodeSnapshot(snapshot).getBytes(StandardCharsets.UTF_8);
        String id = findFile(MANIFEST);
        if (id == null) {
            upload(MANIFEST, JSON, body, null);
        } else {
            upload(MANIFEST, JSON, body, id);
        }
    }

    @Nullable
    @Override
    public InputStream readAttachment(@NonNull String sha256) throws IOException {
        String id = findFile(ATTACHMENT_PREFIX + sha256);
        if (id == null) return null;
        byte[] bytes = requestBytes("GET", API + "/files/" + id + "?alt=media");
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public void writeAttachment(@NonNull String sha256, @NonNull InputStream content)
            throws IOException {
        byte[] bytes = readFully(content);
        String name = ATTACHMENT_PREFIX + sha256;
        String id = findFile(name);
        upload(name, "application/octet-stream", bytes, id);
    }

    @Nullable
    private String findFile(String name) throws IOException {
        String escaped = name.replace("'", "\\'");
        String url =
                API
                        + "/files?q=name%3D%27"
                        + escaped
                        + "%27+and+trashed%3Dfalse"
                        + "&spaces=drive&fields=files(id,name)&pageSize=1";
        JsonObject result = GSON.fromJson(request("GET", url, null, null), JsonObject.class);
        JsonArray files = result == null ? null : result.getAsJsonArray("files");
        return files == null || files.isEmpty()
                ? null
                : files.get(0).getAsJsonObject().get("id").getAsString();
    }

    private void upload(String name, String mime, byte[] data, @Nullable String fileId)
            throws IOException {
        String boundary = "mynotes-" + System.nanoTime();
        String metadata = "{\"name\":\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        String path =
                fileId == null ? "?uploadType=multipart" : "/" + fileId + "?uploadType=multipart";
        HttpURLConnection connection = open(fileId == null ? "POST" : "PATCH", UPLOAD + path);
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);
        connection.setDoOutput(true);
        try (OutputStream out = connection.getOutputStream()) {
            writePart(out, boundary, JSON, metadata.getBytes(StandardCharsets.UTF_8));
            writePart(out, boundary, mime, data);
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }
        ensureSuccess(connection);
    }

    private static void writePart(OutputStream out, String boundary, String mime, byte[] data)
            throws IOException {
        out.write(
                ("--" + boundary + "\r\nContent-Type: " + mime + "\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private String request(String method, String url, @Nullable String type, @Nullable byte[] body)
            throws IOException {
        HttpURLConnection connection = open(method, url);
        if (type != null) connection.setRequestProperty("Content-Type", type);
        if (body != null) {
            connection.setDoOutput(true);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
        }
        ensureSuccess(connection);
        return new String(readFully(connection.getInputStream()), StandardCharsets.UTF_8);
    }

    private byte[] requestBytes(String method, String url) throws IOException {
        HttpURLConnection connection = open(method, url);
        ensureSuccess(connection);
        return readFully(connection.getInputStream());
    }

    private HttpURLConnection open(String method, String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(60_000);
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        return connection;
    }

    private static void ensureSuccess(HttpURLConnection connection) throws IOException {
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            String detail = "";
            InputStream error = connection.getErrorStream();
            if (error != null) detail = new String(readFully(error), StandardCharsets.UTF_8);
            throw new IOException(
                    "Drive API HTTP " + code + (detail.isEmpty() ? "" : ": " + detail));
        }
    }

    private static byte[] readFully(InputStream input) throws IOException {
        try (InputStream in = input;
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toByteArray();
        }
    }

    private static String encodeSnapshot(SyncSnapshot snapshot) {
        JsonArray records = new JsonArray();
        for (SyncRecord record : snapshot.getRecords()) {
            JsonObject item = new JsonObject();
            item.addProperty("type", record.getType().getWireValue());
            item.addProperty("id", record.getId());
            item.addProperty("updatedAt", record.getUpdatedAt().toString());
            if (record.getDeletedAt() != null)
                item.addProperty("deletedAt", record.getDeletedAt().toString());
            item.add("payload", record.getPayload());
            records.add(item);
        }
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        root.add("records", records);
        return GSON.toJson(root);
    }

    private static SyncSnapshot decodeSnapshot(String json) throws IOException {
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            JsonArray records = root.getAsJsonArray("records");
            List<SyncRecord> result = new ArrayList<>();
            if (records == null) return SyncSnapshot.empty();
            for (JsonElement element : records) {
                JsonObject item = element.getAsJsonObject();
                SyncRecord.Type type =
                        SyncRecord.Type.fromWireValue(item.get("type").getAsString());
                String id = item.get("id").getAsString();
                Instant updated = Instant.parse(item.get("updatedAt").getAsString());
                JsonElement deleted = item.get("deletedAt");
                if (deleted != null && !deleted.isJsonNull()) {
                    result.add(
                            SyncRecord.tombstone(
                                    type, id, updated, Instant.parse(deleted.getAsString())));
                } else {
                    result.add(SyncRecord.live(type, id, updated, item.getAsJsonObject("payload")));
                }
            }
            return new SyncSnapshot(result);
        } catch (RuntimeException error) {
            throw new IOException("Invalid MyNotes sync manifest", error);
        }
    }
}
