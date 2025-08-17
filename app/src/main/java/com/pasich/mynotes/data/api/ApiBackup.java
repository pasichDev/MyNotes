package com.pasich.mynotes.data.api;

import static com.pasich.mynotes.utils.constants.settings.BackupPreferences.ARGUMENT_DEFAULT_LAST_BACKUP_ID;
import static com.pasich.mynotes.utils.constants.settings.BackupPreferences.ARGUMENT_LAST_BACKUP_ID;
import static com.pasich.mynotes.utils.constants.settings.BackupPreferences.FILE_NAME_BACKUP;
import static com.pasich.mynotes.utils.constants.settings.BackupPreferences.FIlE_NAME_PREFERENCE_BACKUP;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;
import com.pasich.mynotes.data.model.backup.BackupCloud;
import com.pasich.mynotes.data.model.backup.JsonBackup;
import com.pasich.mynotes.data.model.backup.LastBackupCloud;
import com.pasich.mynotes.utils.backup.BackupCacheHelper;
import com.pasich.mynotes.utils.backup.ScramblerBackupHelper;
import com.pasich.mynotes.utils.constants.CloudErrors;
import com.preference.PowerPreference;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public class ApiBackup implements ApiHelper {
    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private final int PAGE_SIZE = 6;

    private final Context mContext;

    @Inject
    public ApiBackup(@ApplicationContext Context context) {
        this.mContext = context;
    }


    @Override
    public Task<LastBackupCloud> getLastBackupInfo(Drive mDriveCredential) {
        final ArrayList<LastBackupCloud> list = new ArrayList<>();
        return Tasks.call(mExecutor, () -> {
            FileList files = mDriveCredential.files().list().setSpaces("appDataFolder")
                    .setFields("files(id, modifiedTime)").setOrderBy("modifiedTime desc")
                    .setPageSize(PAGE_SIZE)
                    .execute();

            for (com.google.api.services.drive.model.File file : files.getFiles()) {
                list.add(new LastBackupCloud(file.getId(), file.getModifiedTime().getValue()));
            }

            if (list.size() == 0) {
                return new LastBackupCloud(CloudErrors.LAST_BACKUP_EMPTY_DRIVE_VIEW);
            } else {
                return list.get(0);
            }
        });
    }

    @Override
    public Task<BackupCloud> writeCloudBackup(Drive mDriveCredential, File backupTemp, MediaHttpUploaderProgressListener progressListener) {
        return Tasks.call(mExecutor, () -> {
            final com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File().setName(FILE_NAME_BACKUP).setParents(Collections.singletonList("appDataFolder"));
            final FileContent mediaContent = new FileContent("application/json", backupTemp);

            Drive.Files.Create create = mDriveCredential.files().create(fileMetadata, mediaContent).setFields("id");
            MediaHttpUploader uploader = create.getMediaHttpUploader();
            uploader.setProgressListener(progressListener);
            com.google.api.services.drive.model.File file = create.execute();
            return new BackupCloud(file.getId(), new Date().getTime());
        });
    }

    @Override
    public Task<Boolean> cleanOldBackups(Drive mDriveCredential, ArrayList<String> oldBackups) {
        return Tasks.call(mExecutor, () -> {
            for (String idDeleteBackup : oldBackups) {
                mDriveCredential.files().delete(idDeleteBackup).execute();
            }
            return true;
        });
    }

    @Override
    public Task<ArrayList<String>> getOldBackupForCLean(Drive mDriveCredential) {
        final ArrayList<String> listIdsDeleted = new ArrayList<>();
        return Tasks.call(mExecutor, () -> {

            FileList files = mDriveCredential.files().list().setSpaces("appDataFolder")
                    .setFields("files(id)").setPageSize(PAGE_SIZE).execute();

            for (com.google.api.services.drive.model.File file : files.getFiles()) {
                listIdsDeleted.add(file.getId());
            }

            return listIdsDeleted;
        });
    }

    @Override
    public Task<JsonBackup> getReadLastBackupCloud(Drive mDriveCredential) {
        final String idBackupCloud = PowerPreference.getFileByName(FIlE_NAME_PREFERENCE_BACKUP).getString(ARGUMENT_LAST_BACKUP_ID, ARGUMENT_DEFAULT_LAST_BACKUP_ID);
        return Tasks.call(mExecutor, () -> {
            OutputStream outputStream = new ByteArrayOutputStream();
            mDriveCredential.files().get(idBackupCloud).executeMediaAndDownloadTo(outputStream);


            return ScramblerBackupHelper.decodeString(outputStream.toString());
        });
    }

    @Override
    public boolean writeBackupLocalFile(BackupCacheHelper serviceCache, Uri uriLocalFile) {
        if (checkServiceCache(serviceCache)) {
            try {
                ParcelFileDescriptor descriptor = mContext.getContentResolver().openFileDescriptor(uriLocalFile, "w");
                FileOutputStream fileOutputStream = new FileOutputStream(descriptor.getFileDescriptor());
                fileOutputStream.write(ScramblerBackupHelper.encodeString(serviceCache.getJsonBackup()).getBytes());
                fileOutputStream.close();
                descriptor.close();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * Read local backup file to get data
     *
     * @param uriLocalFile - uri local file
     * @return - data gson model
     */
    @Override
    public JsonBackup readBackupLocalFile(Uri uriLocalFile) {
        try {
            final ParcelFileDescriptor descriptor = mContext.getContentResolver().openFileDescriptor(uriLocalFile, "r");
            final StringBuilder jsonFile = new StringBuilder();
            final BufferedReader bufferedReader = new BufferedReader(new FileReader(descriptor.getFileDescriptor()));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                jsonFile.append(line);
                jsonFile.append('\n');
            }
            descriptor.close();
            bufferedReader.close();

            return ScramblerBackupHelper.decodeString(jsonFile.toString());
        } catch (IOException e) {
            Log.w("BACKUP", String.valueOf(e));

            return new JsonBackup().error();
        }
    }

    /**
     * Write a temporary backup file for uploading to the cloud
     *
     * @param jsonBackup - gson data
     * @return - temp backup file
     */
    @Override
    public File writeTempBackup(JsonBackup jsonBackup) {
        final java.io.File backupTemp = new java.io.File(mContext.getFilesDir() + FILE_NAME_BACKUP);
        try {
            BufferedWriter bwNote = new BufferedWriter(new FileWriter(backupTemp));
            bwNote.write(ScramblerBackupHelper.encodeString(jsonBackup));
            bwNote.close();
            return backupTemp;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Valid service cache
     *
     * @param serviceCache - cache backup initial user data
     * @return - error check
     */
    private boolean checkServiceCache(BackupCacheHelper serviceCache) {
        if (serviceCache == null) {
            return false;
        }
        return serviceCache.getJsonBackup() != null;
    }

}
