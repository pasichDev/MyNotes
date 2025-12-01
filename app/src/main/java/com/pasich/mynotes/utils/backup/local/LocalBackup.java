package com.pasich.mynotes.utils.backup.local;

import static com.pasich.mynotes.utils.constants.Backup.FILE_NAME_BACKUP;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.pasich.mynotes.utils.backup.BackupCacheHelper;
import com.pasich.mynotes.utils.backup.ScramblerBackupHelper;
import com.pasich.mynotes.utils.backup.models.JsonBackup;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

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
        try (ParcelFileDescriptor descriptor = mContext.getContentResolver().openFileDescriptor(uriLocalFile, "w");
             FileOutputStream fos = new FileOutputStream(Objects.requireNonNull(descriptor).getFileDescriptor())) {

            fos.write(ScramblerBackupHelper.encodeString(serviceCache.getJsonBackup())
                    .getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to write backup file", e);
            return false;
        }
    }

    @Override
    public JsonBackup readBackupLocalFile(Uri uriLocalFile) {
        try (ParcelFileDescriptor descriptor = mContext.getContentResolver().openFileDescriptor(uriLocalFile, "r");
             BufferedReader reader = new BufferedReader(new FileReader(Objects.requireNonNull(descriptor).getFileDescriptor()))) {

            StringBuilder jsonFile = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonFile.append(line).append('\n');
            }

            return ScramblerBackupHelper.decodeString(jsonFile.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to read backup file", e);
            return new JsonBackup().error();
        }
    }

    @Override
    public File writeTempBackup(JsonBackup jsonBackup) {
        File backupTemp = new File(mContext.getFilesDir(), FILE_NAME_BACKUP);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(backupTemp))) {
            bw.write(ScramblerBackupHelper.encodeString(jsonBackup));
            return backupTemp;
        } catch (IOException e) {
            Log.e(TAG, "Failed to write temp file", e);
            return null;
        }
    }

    private boolean checkServiceCache(BackupCacheHelper serviceCache) {
        return serviceCache != null && serviceCache.getJsonBackup() != null;
    }


}
