package com.pasich.mynotes.utils.backup.local;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.pasich.mynotes.R;
import com.pasich.mynotes.utils.constants.Backup;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Decides whether a picked document is a backup this app can restore.
 *
 * <p>The decision used to rest on the display name's extension alone. Android's document picker
 * appends " (2)" when a second backup is saved into a folder that already holds one, so the app
 * refused its own freshly written file. The content decides now: an archive holding the backup JSON
 * is a backup whatever it is called; the extension is only kept as the fast path for the legacy
 * non-archive formats.
 */
public class BackupFileValidator {

    private static final String TAG = "BackupFileValidator";

    private static final String EXT_JSON = ".json";
    private static final String EXT_ZIP = ".zip";
    private static final String EXT_MNBK = ".mnbkn";

    /** Opens the picked document, so the content can be inspected. */
    public interface ContentOpener {
        @Nullable
        InputStream open() throws IOException;
    }

    /**
     * Validate a picked backup file.
     *
     * <p>- If user cancels selection → return silently (no errors shown). - If filename cannot be
     * determined → callback.onInvalid(...) - If neither name nor content is a backup →
     * callback.onInvalid(...) - If valid → callback.onValid(filename)
     */
    public static void isValidBackupFile(Context ctx, Uri uri, BackupValidatorCallback callback) {

        // User canceled the picker → do nothing
        if (uri == null) return;

        String name = getFileName(ctx, uri);
        if (name == null) {
            callback.onInvalid(ctx.getString(R.string.file_not_selected));
            return;
        }

        if (!isAcceptable(name, () -> ctx.getContentResolver().openInputStream(uri))) {
            callback.onInvalid(ctx.getString(R.string.file_wrong_format));
            return;
        }

        callback.onValid(name);
    }

    /**
     * The decision itself, free of {@code android.*} so every branch runs under a plain JVM test.
     *
     * @param name the document's display name, which the picker may have decorated.
     * @param content the document's bytes, consulted when the name alone does not settle it.
     */
    static boolean isAcceptable(@NonNull String name, @NonNull ContentOpener content) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(EXT_JSON) || lower.endsWith(EXT_ZIP) || lower.endsWith(EXT_MNBK)) {
            return true;
        }
        try (InputStream input = content.open()) {
            return input != null && isBackupArchive(input);
        } catch (IOException | RuntimeException unreadable) {
            Log.w(TAG, "Could not inspect the picked document", unreadable);
            return false;
        }
    }

    /** True when the bytes are a ZIP archive holding the backup JSON entry. */
    static boolean isBackupArchive(@NonNull InputStream input) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (Backup.FILE_NAME_BACKUP.equals(entry.getName())) {
                    return true;
                }
                zip.closeEntry();
            }
        } catch (RuntimeException notAnArchive) {
            // A ZipInputStream over arbitrary bytes throws on a malformed entry header.
            return false;
        }
        return false;
    }

    /**
     * Extract filename from content Uri via OpenableColumns.
     *
     * @return filename or null if cannot be resolved
     */
    public static String getFileName(Context ctx, Uri uri) {
        try (Cursor cursor = ctx.getContentResolver().query(uri, null, null, null, null)) {

            if (cursor == null) {
                Log.e(TAG, "Cursor is null → cannot read filename");
                return null;
            }

            if (cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index != -1) {
                    return cursor.getString(index);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Exception reading filename", e);
        }

        return null;
    }

    /** Result callback for validation. */
    public interface BackupValidatorCallback {
        void onValid(String fileName);

        void onInvalid(String errorMessage);
    }
}
