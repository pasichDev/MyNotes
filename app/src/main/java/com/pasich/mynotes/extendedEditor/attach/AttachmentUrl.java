package com.pasich.mynotes.extendedEditor.attach;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One parsed, validated reference to a note attachment.
 *
 * <p>The canonical form is {@code editorjs://attachments/note_&lt;id&gt;/&lt;file&gt;} — the shape
 * {@code EditorJSInterface.uploadFile} writes and the only shape {@code
 * EditorAttachmentsWebViewClient} serves. {@code file://attachments/...} is accepted as legacy
 * input, because sync restore wrote that form for one release, but it is never produced: {@link
 * #canonical(int, String)} is the single place a new attachment URL is built.
 *
 * <p>Deliberately free of {@code android.net.Uri}. Attachment bytes are deleted on the strength of
 * this parse, so it has to be exercised by ordinary JVM unit tests rather than only on a device.
 */
public final class AttachmentUrl {

    /** Scheme the editor and the WebView interceptor agree on. */
    public static final String SCHEME = "editorjs";

    /** Older scheme kept readable so previously stored references still resolve. */
    public static final String LEGACY_SCHEME = "file";

    public static final String AUTHORITY = AttachmentStorage.ATTACHMENTS_BASE_DIR;

    private static final Pattern NOTE_FOLDER = Pattern.compile("note_[1-9][0-9]*");
    private static final int MAX_NAME_LENGTH = 255;
    private static final char SEPARATOR = 0x5c; // backslash

    private static final String UNRESERVED =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~";

    private final String noteFolder;
    private final String fileName;

    private AttachmentUrl(@NonNull String noteFolder, @NonNull String fileName) {
        this.noteFolder = noteFolder;
        this.fileName = fileName;
    }

    /** The {@code note_<id>} directory this reference lives in. */
    @NonNull
    public String getNoteFolder() {
        return noteFolder;
    }

    /** The decoded file name, guaranteed to be a single safe path segment. */
    @NonNull
    public String getFileName() {
        return fileName;
    }

    /**
     * Parses a stored attachment URL, or returns {@code null} when it is not a safe reference.
     *
     * <p>{@code null} means "this reference could not be understood". Callers must never read that
     * as "this file is an orphan" — see {@link AttachmentCleaner}.
     */
    @Nullable
    public static AttachmentUrl parse(@Nullable String url) {
        if (url == null) {
            return null;
        }
        int schemeEnd = url.indexOf("://");
        if (schemeEnd <= 0) {
            return null;
        }
        String scheme = url.substring(0, schemeEnd).toLowerCase(Locale.ROOT);
        if (!SCHEME.equals(scheme) && !LEGACY_SCHEME.equals(scheme)) {
            return null;
        }
        String remainder = url.substring(schemeEnd + 3);
        // Strip anything after the path; a query or fragment has no meaning here.
        int cut = indexOfAny(remainder, '?', '#');
        if (cut >= 0) {
            remainder = remainder.substring(0, cut);
        }
        String prefix = AUTHORITY + "/";
        if (!remainder.startsWith(prefix)) {
            return null;
        }
        String path = remainder.substring(prefix.length());
        int separator = path.indexOf('/');
        if (separator <= 0 || separator == path.length() - 1) {
            return null;
        }
        String folder = decode(path.substring(0, separator));
        String name = decode(path.substring(separator + 1));
        if (folder == null || name == null) {
            return null;
        }
        if (!NOTE_FOLDER.matcher(folder).matches() || !isSafeSegment(name)) {
            return null;
        }
        return new AttachmentUrl(folder, name);
    }

    /** Builds the canonical URL for a file inside a note's attachment folder. */
    @NonNull
    public static String canonical(int noteId, @NonNull String fileName) {
        if (noteId <= 0) {
            throw new IllegalArgumentException("Attachment note id must be positive");
        }
        if (!isSafeSegment(fileName)) {
            throw new IllegalArgumentException("Attachment file name is not a safe path segment");
        }
        return SCHEME + "://" + AUTHORITY + "/note_" + noteId + "/" + encode(fileName);
    }

    /** Rebuilds this reference in canonical form, whichever scheme it was read from. */
    @NonNull
    public String canonical() {
        return SCHEME + "://" + AUTHORITY + "/" + noteFolder + "/" + encode(fileName);
    }

    /**
     * Resolves this reference against an attachment root, refusing anything that escapes it.
     *
     * <p>The segment checks above already forbid separators and {@code ..}, so this is the second
     * of two independent guards rather than the only one.
     */
    @Nullable
    public File resolveWithin(@NonNull File attachmentsRoot) {
        try {
            File root = attachmentsRoot.getCanonicalFile();
            File resolved = new File(new File(root, noteFolder), fileName).getCanonicalFile();
            String rootPath = root.getPath() + File.separator;
            return resolved.getPath().startsWith(rootPath) ? resolved : null;
        } catch (IOException | SecurityException error) {
            return null;
        }
    }

    private static int indexOfAny(@NonNull String value, char first, char second) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == first || current == second) {
                return index;
            }
        }
        return -1;
    }

    /**
     * True only for a name that is exactly one ordinary path segment.
     *
     * <p>The single rule for attachment names: the sync store and the bundle validator both defer
     * to it, so a name one of them accepted can never be one the other refuses to build a path
     * from.
     */
    public static boolean isSafeSegment(@Nullable String name) {
        if (name == null) {
            return false;
        }
        String value = name.trim();
        if (!value.equals(name) || value.isEmpty() || value.length() > MAX_NAME_LENGTH) {
            return false;
        }
        if (value.equals(".") || value.equals("..") || value.contains("..")) {
            return false;
        }
        if (value.indexOf('/') >= 0 || value.indexOf(SEPARATOR) >= 0) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return false;
            }
        }
        return new File(value).getName().equals(value);
    }

    /**
     * Percent-decodes one path segment as UTF-8, or returns {@code null} when it is malformed.
     *
     * <p>Decoding happens before validation on purpose: {@code %2e%2e%2f} has to be rejected as the
     * traversal it is, not accepted as an opaque name.
     */
    @Nullable
    private static String decode(@NonNull String segment) {
        if (segment.indexOf('%') < 0) {
            return segment;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(segment.length());
        StringBuilder literal = new StringBuilder();
        for (int index = 0; index < segment.length(); ) {
            char current = segment.charAt(index);
            if (current != '%') {
                literal.append(current);
                index++;
                continue;
            }
            flush(literal, bytes);
            if (index + 2 >= segment.length()) {
                return null;
            }
            int high = Character.digit(segment.charAt(index + 1), 16);
            int low = Character.digit(segment.charAt(index + 2), 16);
            if (high < 0 || low < 0) {
                return null;
            }
            bytes.write((byte) ((high << 4) + low));
            index += 3;
        }
        flush(literal, bytes);
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Moves buffered literal characters into the byte stream as UTF-8. */
    private static void flush(@NonNull StringBuilder literal, @NonNull ByteArrayOutputStream out) {
        if (literal.length() == 0) {
            return;
        }
        byte[] encoded = literal.toString().getBytes(StandardCharsets.UTF_8);
        out.write(encoded, 0, encoded.length);
        literal.setLength(0);
    }

    /** Percent-encodes everything outside the unreserved set, which every decoder agrees on. */
    @NonNull
    private static String encode(@NonNull String segment) {
        StringBuilder result = new StringBuilder(segment.length());
        for (byte value : segment.getBytes(StandardCharsets.UTF_8)) {
            char current = (char) (value & 0xff);
            if (UNRESERVED.indexOf(current) >= 0) {
                result.append(current);
            } else {
                result.append('%')
                        .append(Character.toUpperCase(Character.forDigit((value >> 4) & 0xf, 16)))
                        .append(Character.toUpperCase(Character.forDigit(value & 0xf, 16)));
            }
        }
        return result.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AttachmentUrl)) {
            return false;
        }
        AttachmentUrl value = (AttachmentUrl) other;
        return noteFolder.equals(value.noteFolder) && fileName.equals(value.fileName);
    }

    @Override
    public int hashCode() {
        return noteFolder.hashCode() * 31 + fileName.hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return canonical();
    }
}
