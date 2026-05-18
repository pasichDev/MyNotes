package com.pasich.mynotes.utils.backup.local;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import com.pasich.mynotes.R;

/**
 * Small helper class for validating selected backup files. Ensures that the file exists and has a
 * supported extension.
 */
public class BackupFileValidator {

    private static final String TAG = "BackupFileValidator";

    private static final String EXT_JSON = ".json";
    private static final String EXT_ZIP = ".zip";
    private static final String EXT_MNBK = ".mnbkn";

    /**
     * Validate backup file based on its filename and extension.
     *
     * <p>- If user cancels selection → return silently (no errors shown). - If filename cannot be
     * determined → callback.onInvalid(...) - If extension unsupported → callback.onInvalid(...) -
     * If valid → callback.onValid(filename)
     */
    public static void isValidBackupFile(Context ctx, Uri uri, BackupValidatorCallback callback) {

        // User canceled the picker → do nothing
        if (uri == null) return;

        String name = getFileName(ctx, uri);
        if (name == null) {
            callback.onInvalid(ctx.getString(R.string.file_not_selected));
            return;
        }

        String lower = name.toLowerCase();

        boolean ok =
                lower.endsWith(EXT_JSON) || lower.endsWith(EXT_ZIP) || lower.endsWith(EXT_MNBK);

        if (!ok) {
            callback.onInvalid(ctx.getString(R.string.file_wrong_format));
            return;
        }

        callback.onValid(name);
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
