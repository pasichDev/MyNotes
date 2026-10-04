package com.pasich.mynotes.utils.encly;

/**
 * The hand-off contract shared with Encly (pasichDev/MyNotes#167, pasichDev/Encly#46).
 *
 * <p>Every value here is read by the other app as well, so none of them may change without a new
 * {@link #SCHEMA} on both sides.
 */
public final class EnclyHandoff {

    public static final String ENCLY_PACKAGE = "com.pasich.encly";
    public static final String ACTION_IMPORT = "com.pasich.encly.action.IMPORT_FROM_MY_NOTES";

    /**
     * SHA-256 of Encly's signing certificate. The same certificate signs the Google Play, F-Droid
     * and GitHub builds, so one pin covers every legitimate install.
     */
    public static final String ENCLY_CERT_SHA256 =
            "6884c693354964276231e6d0336b965f690bf3f15a0707bb59505c208e5e7554";

    public static final String MIME_TYPE = "application/zip";
    public static final String ENTRY_NAME = "handoff.json";
    public static final String FORMAT = "mynotes-handoff";
    public static final int SCHEMA = 1;

    /** Subdirectory of {@code cacheDir}, exposed by the FileProvider as {@code encly_handoff}. */
    public static final String CACHE_DIR = "encly-handoff";

    // Result extras (RESULT_OK)
    public static final String EXTRA_NOTES = "notes";
    public static final String EXTRA_TASKS = "tasks";
    public static final String EXTRA_TAGS = "tags";
    public static final String EXTRA_SKIPPED = "skipped";

    // Result extra (RESULT_CANCELED)
    public static final String EXTRA_REASON = "reason";

    public static final String MARKET_URI = "market://details?id=" + ENCLY_PACKAGE;
    public static final String WEB_STORE_URI =
            "https://play.google.com/store/apps/details?id=" + ENCLY_PACKAGE;

    private EnclyHandoff() {}
}
