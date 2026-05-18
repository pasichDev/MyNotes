package com.pasich.mynotes.utils.backup.local;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import com.pasich.mynotes.utils.backup.BackupCacheHelper;
import com.pasich.mynotes.utils.backup.ScramblerBackupHelper;
import com.pasich.mynotes.utils.backup.models.JsonBackup;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class LocalBackup implements LocalBackupHelper {

    private static final String TAG = "LocalBackup";
    private final Context mContext;

    @Inject
    public LocalBackup(@ApplicationContext Context context) {
        this.mContext = context;
    }

    @Override
    public boolean writeBackupLocalFile(BackupCacheHelper serviceCache, Uri uriLocalFile) {
        if (!checkServiceCache(serviceCache)) return false;

        try {

            File zip = ZipBackupHelper.writeZipBackup(mContext, serviceCache.getJsonBackup());

            try (ParcelFileDescriptor desc =
                    mContext.getContentResolver().openFileDescriptor(uriLocalFile, "w")) {
                assert desc != null;
                try (FileOutputStream fos = new FileOutputStream(desc.getFileDescriptor())) {

                    fos.write(Files.readAllBytes(zip.toPath()));
                    return true;
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to write ZIP backup", e);
            return false;
        }
    }

    @Override
    public JsonBackup readBackupLocalFile(Uri uriLocalFile) {
        try {
            byte[] raw = readBytes(uriLocalFile);

            if (ZipBackupHelper.isZip(raw)) {
                return ZipBackupHelper.readZipBackup(mContext, uriLocalFile);
            }

            return ScramblerBackupHelper.decodeString(new String(raw, StandardCharsets.UTF_8));

        } catch (Exception e) {
            Log.e(TAG, "Failed to read backup file", e);
            return new JsonBackup().error();
        }
    }

    private byte[] readBytes(Uri uri) throws IOException {
        try (InputStream is = mContext.getContentResolver().openInputStream(uri);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

            if (is == null) return new byte[0];

            byte[] data = new byte[4096];
            int nRead;
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }

            return buffer.toByteArray();
        }
    }

    private boolean checkServiceCache(BackupCacheHelper serviceCache) {
        return serviceCache != null && serviceCache.getJsonBackup() != null;
    }
}
