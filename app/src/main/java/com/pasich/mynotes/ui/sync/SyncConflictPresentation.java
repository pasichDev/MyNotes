package com.pasich.mynotes.ui.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pasich.mynotes.data.database.entities.SyncConflictEntity;
import com.pasich.mynotes.data.sync.SyncMetadata;

/**
 * Turns a stored conflict into the two versions a person actually compares.
 *
 * <p>The dialog used to render both versions into one string, with no timestamps and a fragment cut
 * at a fixed 120 characters — so a difference near the end of a note never reached the screen and
 * "which one is mine" was unanswerable. Everything here comes from fields the conflict row already
 * carries; nothing new is read from the database.
 *
 * <p>Free of {@code android.*} on purpose: which text is shown, where the difference is, and which
 * side is newer are the parts worth testing, and they are testable only if they live outside the
 * Activity.
 */
public final class SyncConflictPresentation {

    /** How a version should be described, so the caller supplies the localized wording. */
    public enum Kind {
        /** Ordinary text taken from the payload. */
        TEXT,
        /** The version is a deletion. */
        DELETED,
        /** A settings payload, which has no single readable title. */
        SETTINGS,
        /** Readable, but every candidate field was empty. */
        UNTITLED
    }

    /** One side of the conflict, ready to render. */
    public static final class Version {
        /** True only when this version genuinely came from this device. */
        public final boolean local;

        public final long updatedAt;
        public final boolean newer;
        @NonNull public final Kind kind;

        /** Preview text, already windowed around the difference. Empty unless {@link Kind#TEXT}. */
        @NonNull public final String preview;

        /** Range within {@link #preview} that differs from the other version. */
        public final int highlightStart;

        public final int highlightEnd;

        Version(
                boolean local,
                long updatedAt,
                boolean newer,
                @NonNull Kind kind,
                @NonNull String preview,
                int highlightStart,
                int highlightEnd) {
            this.local = local;
            this.updatedAt = updatedAt;
            this.newer = newer;
            this.kind = kind;
            this.preview = preview;
            this.highlightStart = highlightStart;
            this.highlightEnd = highlightEnd;
        }

        public boolean hasHighlight() {
            return highlightEnd > highlightStart;
        }
    }

    /** Longest preview shown in the dialog before it is windowed. */
    static final int PREVIEW_LIMIT = 140;

    @NonNull public final Version winner;
    @NonNull public final Version alternative;
    @NonNull public final String recordType;

    private SyncConflictPresentation(
            @NonNull Version winner, @NonNull Version alternative, @NonNull String recordType) {
        this.winner = winner;
        this.alternative = alternative;
        this.recordType = recordType;
    }

    @NonNull
    public static SyncConflictPresentation of(@NonNull SyncConflictEntity conflict) {
        Kind winnerKind =
                kindOf(conflict.recordType, conflict.winnerTombstone, conflict.winnerJson);
        Kind loserKind = kindOf(conflict.recordType, conflict.loserTombstone, conflict.loserJson);

        String winnerText =
                winnerKind == Kind.TEXT ? readable(conflict.recordType, conflict.winnerJson) : "";
        String loserText =
                loserKind == Kind.TEXT ? readable(conflict.recordType, conflict.loserJson) : "";

        int[] range = differenceRange(winnerText, loserText);
        Window winnerWindow = window(winnerText, range[0], range[1]);
        Window loserWindow = window(loserText, range[0], range[2]);

        boolean winnerNewer = conflict.winnerUpdatedAt >= conflict.loserUpdatedAt;
        return new SyncConflictPresentation(
                new Version(
                        "LOCAL".equals(conflict.winnerSource),
                        conflict.winnerUpdatedAt,
                        winnerNewer,
                        winnerKind,
                        winnerWindow.text,
                        winnerWindow.start,
                        winnerWindow.end),
                new Version(
                        "LOCAL".equals(conflict.loserSource),
                        conflict.loserUpdatedAt,
                        !winnerNewer,
                        loserKind,
                        loserWindow.text,
                        loserWindow.start,
                        loserWindow.end),
                conflict.recordType);
    }

    @NonNull
    private static Kind kindOf(
            @NonNull String recordType, boolean tombstone, @Nullable String recordJson) {
        if (tombstone) {
            return Kind.DELETED;
        }
        if (SyncMetadata.RECORD_TYPE_PREFERENCES.equals(recordType)) {
            return Kind.SETTINGS;
        }
        String text = readable(recordType, recordJson);
        return text.isEmpty() ? Kind.UNTITLED : Kind.TEXT;
    }

    /**
     * Pulls the most descriptive text out of a stored version.
     *
     * <p>Notes and tags are serialized through Gson's short field aliases, so probing "title" and
     * "name" never matched them.
     */
    @NonNull
    static String readable(@NonNull String recordType, @Nullable String recordJson) {
        if (recordJson == null || recordJson.isEmpty()) {
            return "";
        }
        try {
            JsonObject root = JsonParser.parseString(recordJson).getAsJsonObject();
            if (root.has("deletedAt") && !root.get("deletedAt").isJsonNull()) {
                return "";
            }
            JsonObject payload = root.getAsJsonObject("payload");
            if (payload == null) {
                return "";
            }
            StringBuilder result = new StringBuilder();
            for (String key : labelKeys(recordType)) {
                if (!payload.has(key) || payload.get(key).isJsonNull()) continue;
                if (!payload.get(key).isJsonPrimitive()) continue;
                String value = payload.get(key).getAsString().trim();
                if (value.isEmpty()) continue;
                if (result.length() > 0) result.append(" — ");
                result.append(value);
            }
            return result.toString();
        } catch (RuntimeException unreadable) {
            return "";
        }
    }

    /** Payload keys carrying human-readable text, most specific first. */
    @NonNull
    static String[] labelKeys(@NonNull String recordType) {
        if (SyncMetadata.RECORD_TYPE_NOTE.equals(recordType)) {
            return new String[] {"b", "c"}; // Note.title, Note.value
        }
        if (SyncMetadata.RECORD_TYPE_TAG.equals(recordType)) {
            return new String[] {"b"}; // Tag.nameTag
        }
        if (SyncMetadata.RECORD_TYPE_TASK.equals(recordType)) {
            return new String[] {"title", "description"};
        }
        if (SyncMetadata.RECORD_TYPE_CATEGORY.equals(recordType)) {
            return new String[] {"name"};
        }
        return new String[0];
    }

    /**
     * Locates where two versions stop agreeing.
     *
     * @return {@code {start, endInFirst, endInSecond}} — the shared prefix length and, for each
     *     side, where its differing part ends. Equal strings give a zero-length range.
     */
    @NonNull
    static int[] differenceRange(@NonNull String first, @NonNull String second) {
        int prefix = 0;
        int shortest = Math.min(first.length(), second.length());
        while (prefix < shortest && first.charAt(prefix) == second.charAt(prefix)) {
            prefix++;
        }
        if (prefix == first.length() && prefix == second.length()) {
            return new int[] {0, 0, 0};
        }
        int suffix = 0;
        while (suffix < shortest - prefix
                && first.charAt(first.length() - 1 - suffix)
                        == second.charAt(second.length() - 1 - suffix)) {
            suffix++;
        }
        return new int[] {prefix, first.length() - suffix, second.length() - suffix};
    }

    /** Preview text plus the highlight range inside it. */
    static final class Window {
        @NonNull final String text;
        final int start;
        final int end;

        Window(@NonNull String text, int start, int end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }
    }

    /**
     * Trims a version to preview length, keeping the difference on screen.
     *
     * <p>A fixed head-of-string cut is what hid the difference whenever it fell past the limit, so
     * the window is centred on the differing range instead and marked with ellipses.
     */
    @NonNull
    static Window window(@NonNull String text, int diffStart, int diffEnd) {
        if (text.length() <= PREVIEW_LIMIT) {
            return new Window(text, clamp(diffStart, text.length()), clamp(diffEnd, text.length()));
        }
        int start = clamp(diffStart, text.length());
        int end = clamp(diffEnd, text.length());
        int centre = (start + end) / 2;
        int from = Math.max(0, centre - PREVIEW_LIMIT / 2);
        int to = Math.min(text.length(), from + PREVIEW_LIMIT);
        from = Math.max(0, to - PREVIEW_LIMIT);

        String head = from > 0 ? "…" : "";
        String tail = to < text.length() ? "…" : "";
        String body = text.substring(from, to);
        int shift = head.length() - from;
        return new Window(
                head + body + tail,
                clamp(start + shift, head.length() + body.length()),
                clamp(end + shift, head.length() + body.length()));
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(value, max));
    }
}
