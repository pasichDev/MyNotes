package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GoogleDriveSyncBackendTest {
    private static final String NOTE_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String SECOND_NOTE_ID = "550e8400-e29b-41d4-a716-446655440001";
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC);

    private FakeDriveServer server;

    @Before
    public void setUp() throws Exception {
        server = new FakeDriveServer();
    }

    @After
    public void tearDown() {
        server.close();
    }

    @Test
    public void writeSnapshot_createsOwnedFolderBundleAndAttachment() throws Exception {
        GoogleDriveSyncBackend backend =
                new GoogleDriveSyncBackend(
                        "token",
                        server.apiBase(),
                        server.uploadBase(),
                        CLOCK,
                        new SyncBundleCodec());
        byte[] attachmentBytes = "photo".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(attachmentBytes);

        assertThat(backend.readSnapshot().getRecords()).isEmpty();

        backend.writeAttachment(hash, new ByteArrayInputStream(attachmentBytes));
        backend.writeAttachment(hash, new ByteArrayInputStream(attachmentBytes));
        backend.writeSnapshot(snapshot(hash));

        assertThat(server.ownedFolderCount()).isEqualTo(1);
        assertThat(server.bundleCount()).isEqualTo(1);
        assertThat(server.ownedAttachmentCount(hash)).isEqualTo(1);
        assertThat(server.readAttachment(hash)).isEqualTo(attachmentBytes);
        assertThat(backend.hasAttachment(hash)).isTrue();

        SyncSnapshot remoteSnapshot =
                new SyncBundleCodec()
                        .decode(new ByteArrayInputStream(server.readBundleBytes()))
                        .getSnapshot();
        assertThat(remoteSnapshot.find(SyncRecord.Type.NOTE, NOTE_ID)).isNotNull();
    }

    @Test
    public void readSnapshot_ignoresUnownedFiles() throws Exception {
        server.seedUnownedBundle(
                snapshot("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        GoogleDriveSyncBackend backend =
                new GoogleDriveSyncBackend(
                        "token",
                        server.apiBase(),
                        server.uploadBase(),
                        CLOCK,
                        new SyncBundleCodec());

        assertThat(backend.readSnapshot().getRecords()).isEmpty();
    }

    @Test
    public void writeSnapshot_createsNewBundleWhenLegacyBundleChanges() throws Exception {
        String hash = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        server.seedOwnedBundle(snapshot(hash));
        GoogleDriveSyncBackend backend =
                new GoogleDriveSyncBackend(
                        "token",
                        server.apiBase(),
                        server.uploadBase(),
                        CLOCK,
                        new SyncBundleCodec());

        assertThat(backend.readSnapshot().find(SyncRecord.Type.NOTE, NOTE_ID)).isNotNull();
        server.forceConcurrentBundleUpdate(
                snapshot("cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"));

        backend.writeSnapshot(snapshot(hash));

        assertThat(server.bundleCount()).isEqualTo(2);
    }

    @Test
    public void writeSnapshot_preservesUpdateThatArrivesBetweenReadAndPublish() throws Exception {
        String firstHash = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        String secondHash = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
        server.seedOwnedBundle(snapshot(NOTE_ID, firstHash));
        GoogleDriveSyncBackend backend =
                new GoogleDriveSyncBackend(
                        "token",
                        server.apiBase(),
                        server.uploadBase(),
                        CLOCK,
                        new SyncBundleCodec());

        backend.readSnapshot();
        server.updateBundleImmediatelyBeforeNextUpload(snapshot(SECOND_NOTE_ID, secondHash));
        backend.writeSnapshot(snapshot(NOTE_ID, firstHash));

        SyncSnapshot remote =
                new GoogleDriveSyncBackend(
                                "token",
                                server.apiBase(),
                                server.uploadBase(),
                                CLOCK,
                                new SyncBundleCodec())
                        .readSnapshot();
        assertThat(remote.find(SyncRecord.Type.NOTE, NOTE_ID)).isNotNull();
        assertThat(remote.find(SyncRecord.Type.NOTE, SECOND_NOTE_ID)).isNotNull();
    }

    private static SyncSnapshot snapshot(String hash) throws IOException {
        return snapshot(NOTE_ID, hash);
    }

    private static SyncSnapshot snapshot(String noteId, String hash) throws IOException {
        JsonObject note = new JsonObject();
        note.addProperty("title", "Shopping");
        note.addProperty("value", "Milk");
        JsonArray hashes = new JsonArray();
        hashes.add(hash);
        note.add("attachmentHashes", hashes);
        JsonArray manifest = new JsonArray();
        manifest.add(
                new SyncBundleCodec.AttachmentManifestEntry(
                                UUID.nameUUIDFromBytes(hash.getBytes(StandardCharsets.UTF_8))
                                        .toString(),
                                hash,
                                "image/png",
                                5L,
                                "attachments/" + hash,
                                "photo.png")
                        .toJson(true));
        note.add("attachmentsManifest", manifest);
        return new SyncSnapshot(
                Collections.singletonList(
                        SyncRecord.live(
                                SyncRecord.Type.NOTE,
                                noteId,
                                Instant.parse("2026-08-31T12:00:00Z"),
                                note)));
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder value = new StringBuilder(digest.length * 2);
        for (byte byteValue : digest) {
            value.append(String.format("%02x", byteValue & 0xff));
        }
        return value.toString();
    }

    private static final class FakeDriveServer implements AutoCloseable {
        private static final Pattern PARENT_PATTERN = Pattern.compile("'([^']+)' in parents");
        private static final Pattern APP_PROPERTY_PATTERN =
                Pattern.compile("appProperties has \\{ key='([^']+)' and value='([^']+)' \\}");

        private final ServerSocket serverSocket;
        private final Thread thread;
        private final Map<String, DriveFile> files = new LinkedHashMap<>();
        private volatile boolean running = true;
        private SyncSnapshot updateBeforeNextPatch;
        private int nextId = 1;

        FakeDriveServer() throws IOException {
            serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            thread =
                    new Thread(
                            () -> {
                                while (running) {
                                    try {
                                        Socket socket = serverSocket.accept();
                                        handle(socket);
                                    } catch (IOException ignored) {
                                        if (running) {
                                            // Keep the fake server lightweight for tests.
                                        }
                                    }
                                }
                            },
                            "fake-drive-server");
            thread.start();
        }

        String apiBase() {
            return "http://127.0.0.1:" + serverSocket.getLocalPort() + "/drive/v3";
        }

        String uploadBase() {
            return "http://127.0.0.1:" + serverSocket.getLocalPort() + "/upload/drive/v3/files";
        }

        int ownedFolderCount() {
            int count = 0;
            for (DriveFile file : files.values()) {
                if ("application/vnd.google-apps.folder".equals(file.mimeType)
                        && "1".equals(file.appProperties.get("mynotesOwner"))) {
                    count++;
                }
            }
            return count;
        }

        int bundleCount() {
            int count = 0;
            for (DriveFile file : files.values()) {
                if ("1".equals(file.appProperties.get("mynotesBundle"))) {
                    count++;
                }
            }
            return count;
        }

        int ownedAttachmentCount(String hash) {
            int count = 0;
            for (DriveFile file : files.values()) {
                if (hash.equals(file.appProperties.get("mynotesAttachmentSha256"))) {
                    count++;
                }
            }
            return count;
        }

        byte[] readAttachment(String hash) {
            for (DriveFile file : files.values()) {
                if (hash.equals(file.appProperties.get("mynotesAttachmentSha256"))) {
                    return file.content;
                }
            }
            return null;
        }

        byte[] readBundleBytes() {
            for (DriveFile file : files.values()) {
                if ("1".equals(file.appProperties.get("mynotesBundle"))) {
                    return file.content;
                }
            }
            return null;
        }

        void seedOwnedBundle(SyncSnapshot snapshot) throws IOException {
            DriveFile folder =
                    createFile("MyNotes Sync", "application/vnd.google-apps.folder", null);
            folder.appProperties.put("mynotesOwner", "1");
            DriveFile bundle = createFile("MyNotes.sync.v1.zip", "application/zip", folder.id);
            bundle.appProperties.put("mynotesBundle", "1");
            bundle.content = new SyncBundleCodec().encode(snapshot, CLOCK.instant());
        }

        void seedUnownedBundle(SyncSnapshot snapshot) throws IOException {
            DriveFile folder = createFile("Elsewhere", "application/vnd.google-apps.folder", null);
            DriveFile bundle = createFile("MyNotes.sync.v1.zip", "application/zip", folder.id);
            bundle.content = new SyncBundleCodec().encode(snapshot, CLOCK.instant());
        }

        void forceConcurrentBundleUpdate(SyncSnapshot snapshot) throws IOException {
            for (DriveFile file : files.values()) {
                if ("1".equals(file.appProperties.get("mynotesBundle"))) {
                    file.content = new SyncBundleCodec().encode(snapshot, CLOCK.instant());
                    file.version++;
                    return;
                }
            }
            throw new IOException("No bundle to update");
        }

        void updateBundleImmediatelyBeforeNextUpload(SyncSnapshot snapshot) {
            updateBeforeNextPatch = snapshot;
        }

        private void handle(Socket socket) throws IOException {
            try (Socket current = socket;
                    BufferedInputStream input = new BufferedInputStream(current.getInputStream());
                    OutputStream output = current.getOutputStream()) {
                Request request = readRequest(input);
                Response response = dispatch(request);
                writeResponse(output, response);
            }
        }

        private Response dispatch(Request request) throws IOException {
            URI uri = URI.create("http://localhost" + request.target);
            String path = uri.getPath();
            if ("/drive/v3/files".equals(path)) {
                if ("GET".equals(request.method)) {
                    return handleList(uri);
                }
                if ("POST".equals(request.method)) {
                    return handleCreateMetadata(request.body);
                }
                return Response.json(405, "{}");
            }
            if (path.startsWith("/drive/v3/files/")) {
                return handleFileRead(uri, path.substring("/drive/v3/files/".length()));
            }
            if ("/upload/drive/v3/files".equals(path) && "POST".equals(request.method)) {
                return handleUpload(request, null);
            }
            if (path.startsWith("/upload/drive/v3/files/")) {
                return handleUpload(request, path.substring("/upload/drive/v3/files/".length()));
            }
            return Response.json(404, "{}");
        }

        private Response handleList(URI uri) {
            String query = parseQuery(uri).get("q");
            JsonArray array = new JsonArray();
            for (DriveFile file : files.values()) {
                if (matchesQuery(file, query)) {
                    JsonObject value = new JsonObject();
                    value.addProperty("id", file.id);
                    value.addProperty("name", file.name);
                    array.add(value);
                }
            }
            JsonObject response = new JsonObject();
            response.add("files", array);
            return Response.json(200, response.toString());
        }

        private Response handleCreateMetadata(byte[] body) throws IOException {
            JsonObject metadata = readJson(body);
            DriveFile file =
                    createFile(
                            metadata.get("name").getAsString(),
                            metadata.get("mimeType").getAsString(),
                            null);
            applyMetadata(file, metadata);
            return Response.json(200, fileMetadata(file).toString(), file.eTag());
        }

        private Response handleFileRead(URI uri, String id) {
            DriveFile file = files.get(id);
            if (file == null) {
                return Response.json(404, "{}");
            }
            if ("media".equals(parseQuery(uri).get("alt"))) {
                return Response.binary(200, file.content, file.eTag());
            }
            return Response.json(200, fileMetadata(file).toString(), file.eTag());
        }

        private Response handleUpload(Request request, String fileId) throws IOException {
            DriveFile existing = fileId == null ? null : files.get(fileId);
            if (fileId != null && existing == null) {
                return Response.json(404, "{}");
            }
            if (updateBeforeNextPatch != null) {
                SyncSnapshot concurrent = updateBeforeNextPatch;
                updateBeforeNextPatch = null;
                forceConcurrentBundleUpdate(concurrent);
            }

            MultipartPayload payload = readMultipart(request);
            DriveFile file =
                    existing == null
                            ? createFile(
                                    payload.metadata.get("name").getAsString(),
                                    payload.metadata.has("mimeType")
                                            ? payload.metadata.get("mimeType").getAsString()
                                            : "application/octet-stream",
                                    firstParent(payload.metadata))
                            : existing;
            file.content = payload.data;
            applyMetadata(file, payload.metadata);
            if (existing != null) {
                file.version++;
            }
            return Response.json(200, fileMetadata(file).toString(), file.eTag());
        }

        private void applyMetadata(DriveFile file, JsonObject metadata) {
            if (metadata.has("name")) {
                file.name = metadata.get("name").getAsString();
            }
            if (metadata.has("mimeType")) {
                file.mimeType = metadata.get("mimeType").getAsString();
            }
            if (metadata.has("parents")) {
                file.parents.clear();
                JsonArray parents = metadata.getAsJsonArray("parents");
                for (int i = 0; i < parents.size(); i++) {
                    file.parents.add(parents.get(i).getAsString());
                }
            }
            if (metadata.has("appProperties")) {
                file.appProperties.clear();
                JsonObject appProperties = metadata.getAsJsonObject("appProperties");
                for (Map.Entry<String, com.google.gson.JsonElement> entry :
                        appProperties.entrySet()) {
                    file.appProperties.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        }

        private DriveFile createFile(String name, String mimeType, String parentId) {
            DriveFile file = new DriveFile(Integer.toString(nextId++), name, mimeType);
            if (parentId != null) {
                file.parents.add(parentId);
            }
            files.put(file.id, file);
            return file;
        }

        private boolean matchesQuery(DriveFile file, String query) {
            if (query == null || query.isEmpty()) {
                return true;
            }
            if (query.contains("mimeType = 'application/vnd.google-apps.folder'")
                    && !"application/vnd.google-apps.folder".equals(file.mimeType)) {
                return false;
            }
            Matcher parent = PARENT_PATTERN.matcher(query);
            if (parent.find() && !file.parents.contains(parent.group(1))) {
                return false;
            }
            Matcher properties = APP_PROPERTY_PATTERN.matcher(query);
            while (properties.find()) {
                if (!properties.group(2).equals(file.appProperties.get(properties.group(1)))) {
                    return false;
                }
            }
            return true;
        }

        private static MultipartPayload readMultipart(Request request) throws IOException {
            String contentType = request.headers.get("content-type");
            int boundaryIndex = contentType.indexOf("boundary=");
            if (boundaryIndex < 0) {
                throw new IOException("Missing multipart boundary");
            }
            String boundary = contentType.substring(boundaryIndex + 9);
            String body = new String(request.body, StandardCharsets.ISO_8859_1);
            String[] segments = body.split("--" + Pattern.quote(boundary));
            List<String> parts = new ArrayList<>();
            for (String segment : segments) {
                if (segment == null || segment.trim().isEmpty() || segment.equals("--")) {
                    continue;
                }
                parts.add(segment);
            }
            if (parts.size() < 2) {
                throw new IOException("Invalid multipart payload");
            }
            return new MultipartPayload(
                    JsonParser.parseString(extractMultipartBody(parts.get(0))).getAsJsonObject(),
                    extractMultipartBody(parts.get(1)).getBytes(StandardCharsets.ISO_8859_1));
        }

        private static String extractMultipartBody(String part) throws IOException {
            int bodyIndex = part.indexOf("\r\n\r\n");
            if (bodyIndex < 0) {
                throw new IOException("Multipart part is malformed");
            }
            String value = part.substring(bodyIndex + 4);
            if (value.endsWith("\r\n")) {
                value = value.substring(0, value.length() - 2);
            }
            if (value.endsWith("--")) {
                value = value.substring(0, value.length() - 2);
            }
            return value;
        }

        private static String firstParent(JsonObject metadata) {
            JsonArray parents = metadata.getAsJsonArray("parents");
            return parents == null || parents.size() == 0 ? null : parents.get(0).getAsString();
        }

        private static JsonObject fileMetadata(DriveFile file) {
            JsonObject response = new JsonObject();
            response.addProperty("id", file.id);
            response.addProperty("name", file.name);
            // Drive v3 reports the change counter as a string-encoded long.
            response.addProperty("version", String.valueOf(file.version));
            JsonObject appProperties = new JsonObject();
            for (Map.Entry<String, String> entry : file.appProperties.entrySet()) {
                appProperties.addProperty(entry.getKey(), entry.getValue());
            }
            response.add("appProperties", appProperties);
            return response;
        }

        private static JsonObject readJson(byte[] bytes) {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }

        private static Map<String, String> parseQuery(URI uri) {
            Map<String, String> values = new LinkedHashMap<>();
            String rawQuery = uri.getRawQuery();
            if (rawQuery == null || rawQuery.isEmpty()) {
                return values;
            }
            for (String pair : rawQuery.split("&")) {
                int separator = pair.indexOf('=');
                if (separator < 0) {
                    values.put(pair, "");
                    continue;
                }
                values.put(
                        URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
            }
            return values;
        }

        private static Request readRequest(BufferedInputStream input) throws IOException {
            String requestLine = readLine(input);
            if (requestLine == null || requestLine.isEmpty()) {
                throw new IOException("Missing request line");
            }
            String[] pieces = requestLine.split(" ");
            Map<String, String> headers = new LinkedHashMap<>();
            String line;
            while ((line = readLine(input)) != null && !line.isEmpty()) {
                int separator = line.indexOf(':');
                if (separator < 0) {
                    continue;
                }
                headers.put(
                        line.substring(0, separator).trim().toLowerCase(),
                        line.substring(separator + 1).trim());
            }
            int contentLength = 0;
            if (headers.containsKey("content-length")) {
                contentLength = Integer.parseInt(headers.get("content-length"));
            }
            byte[] body = new byte[contentLength];
            int offset = 0;
            while (offset < contentLength) {
                int read = input.read(body, offset, contentLength - offset);
                if (read < 0) {
                    throw new IOException("Unexpected end of stream");
                }
                offset += read;
            }
            return new Request(pieces[0], pieces[1], headers, body);
        }

        private static String readLine(BufferedInputStream input) throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int current;
            while ((current = input.read()) != -1) {
                if (current == '\r') {
                    int next = input.read();
                    if (next != '\n' && next != -1) {
                        output.write(next);
                    }
                    break;
                }
                if (current == '\n') {
                    break;
                }
                output.write(current);
            }
            if (current == -1 && output.size() == 0) {
                return null;
            }
            return output.toString(StandardCharsets.ISO_8859_1.name());
        }

        private static void writeResponse(OutputStream output, Response response)
                throws IOException {
            StringBuilder headers = new StringBuilder();
            headers.append("HTTP/1.1 ").append(response.code).append(" OK\r\n");
            headers.append("Content-Length: ").append(response.body.length).append("\r\n");
            headers.append("Connection: close\r\n");
            headers.append("Content-Type: ").append(response.contentType).append("\r\n");
            // Deliberately no ETag header. Drive API v3 dropped the ETags that v2 sent; a fake
            // that returns one lets code depending on the header pass its tests and fail against
            // the real API, which is exactly what happened before.
            headers.append("\r\n");
            output.write(headers.toString().getBytes(StandardCharsets.ISO_8859_1));
            output.write(response.body);
            output.flush();
        }

        @Override
        public void close() {
            running = false;
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            try {
                thread.join(2000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class DriveFile {
        private final String id;
        private byte[] content = new byte[0];
        private String name;
        private String mimeType;
        private int version = 1;
        private final List<String> parents = new ArrayList<>();
        private final Map<String, String> appProperties = new LinkedHashMap<>();

        private DriveFile(String id, String name, String mimeType) {
            this.id = id;
            this.name = name;
            this.mimeType = mimeType;
        }

        private String eTag() {
            return "\"etag-" + id + "-" + version + "\"";
        }
    }

    private static final class Request {
        private final String method;
        private final String target;
        private final Map<String, String> headers;
        private final byte[] body;

        private Request(String method, String target, Map<String, String> headers, byte[] body) {
            this.method = method;
            this.target = target;
            this.headers = headers;
            this.body = body;
        }
    }

    private static final class Response {
        private final int code;
        private final String contentType;
        private final byte[] body;
        private final String eTag;

        private Response(int code, String contentType, byte[] body, String eTag) {
            this.code = code;
            this.contentType = contentType;
            this.body = body;
            this.eTag = eTag;
        }

        private static Response json(int code, String body) {
            return json(code, body, null);
        }

        private static Response json(int code, String body, String eTag) {
            return new Response(
                    code, "application/json", body.getBytes(StandardCharsets.UTF_8), eTag);
        }

        private static Response binary(int code, byte[] body, String eTag) {
            return new Response(code, "application/octet-stream", body, eTag);
        }
    }

    private static final class MultipartPayload {
        private final JsonObject metadata;
        private final byte[] data;

        private MultipartPayload(JsonObject metadata, byte[] data) {
            this.metadata = metadata;
            this.data = data;
        }
    }
}
