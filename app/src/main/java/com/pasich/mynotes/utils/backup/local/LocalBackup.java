package com.pasich.mynotes.utils.backup.local;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import com.pasich.mynotes.utils.backup.BackupCacheHelper;
import com.pasich.mynotes.utils.backup.ScramblerBackupHelper;
import com.pasich.mynotes.utils.backup.models.JsonBackup;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Reads and writes backup files the user picked.
 *
 * <p>Both directions do file I/O over a whole library, attachments included, and must be called off
 * the main thread.
 */
@Singleton
public class LocalBackup implements LocalBackupHelper {

    private static final String TAG = "LocalBackup";
    private static final int COPY_BUFFER = 64 * 1024;
    private final Context mContext;

    @Inject
    public LocalBackup(@ApplicationContext Context context) {
        this.mContext = context;
    }

    @WorkerThread
    @Override
    public boolean writeBackupLocalFile(BackupCacheHelper serviceCache, Uri uriLocalFile) {
        if (!checkServiceCache(serviceCache)) return false;

        File zip = null;
        try {
            zip = ZipBackupHelper.writeZipBackup(mContext, serviceCache.getJsonBackup());

            try (ParcelFileDescriptor desc =
                    mContext.getContentResolver().openFileDescriptor(uriLocalFile, "w")) {
                if (desc == null) {
                    Log.e(TAG, "The picked document could not be opened for writing");
                    return false;
                }
                try (InputStream in = new FileInputStream(zip);
                        FileOutputStream out = new FileOutputStream(desc.getFileDescriptor())) {
                    // In chunks: the archive carries every attachment and can run to gigabytes.
                    copy(in, out);
                    out.flush();
                }
            }
            serviceCache.setJsonBackup(null);
            return true;
        } catch (Exception | OutOfMemoryError e) {
            Log.e(TAG, "Failed to write ZIP backup", e);
            return false;
        } finally {
            if (zip != null && zip.exists() && !zip.delete()) {
                Log.w(TAG, "Could not remove the temporary backup archive");
            }
        }
    }

    @WorkerThread
    @Override
    public JsonBackup readBackupLocalFile(Uri uriLocalFile) {
        try {
            boolean zip;
            try (InputStream is = mContext.getContentResolver().openInputStream(uriLocalFile)) {
                if (is == null) return new JsonBackup().error();
                // Only the first two bytes decide; the archive itself is streamed below, never
                // held in memory whole.
                zip = ZipBackupHelper.isZip(is);
            }

            if (zip) {
                return ZipBackupHelper.readZipBackup(mContext, uriLocalFile);
            }

            return ScramblerBackupHelper.decodeString(readText(uriLocalFile));

        } catch (Exception | OutOfMemoryError e) {
            Log.e(TAG, "Failed to read backup file", e);
            return new JsonBackup().error();
        }
    }

    /** Reads a legacy, JSON-only backup; these carry no attachments and stay small. */
    @NonNull
    private String readText(Uri uri) throws IOException {
        try (InputStream raw = mContext.getContentResolver().openInputStream(uri)) {
            if (raw == null) return "";
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            copy(new BufferedInputStream(raw), buffer);
            return buffer.toString(StandardCharsets.UTF_8.name());
        }
    }

    static void copy(@NonNull InputStream in, @NonNull OutputStream out) throws IOException {
        byte[] chunk = new byte[COPY_BUFFER];
        int n;
        while ((n = in.read(chunk)) != -1) {
            out.write(chunk, 0, n);
        }
    }

    private boolean checkServiceCache(BackupCacheHelper serviceCache) {
        return serviceCache != null && serviceCache.getJsonBackup() != null;
    }
}
