package com.pasich.mynotes.data.api;

import android.net.Uri;

import com.google.android.gms.tasks.Task;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.services.drive.Drive;
import com.pasich.mynotes.data.model.backup.BackupCloud;
import com.pasich.mynotes.data.model.backup.JsonBackup;
import com.pasich.mynotes.data.model.backup.LastBackupCloud;
import com.pasich.mynotes.utils.backup.BackupCacheHelper;

import java.util.ArrayList;

public interface ApiHelper {
    Task<LastBackupCloud> getLastBackupInfo(Drive mDriveCredential);

    Task<BackupCloud> writeCloudBackup(Drive mDriveCredential, java.io.File backupTemp, MediaHttpUploaderProgressListener progressListener);

    Task<Boolean> cleanOldBackups(Drive mDriveCredential, ArrayList<String> oldBackups);

    Task<ArrayList<String>> getOldBackupForCLean(Drive mDriveCredential);

    Task<JsonBackup> getReadLastBackupCloud(Drive mDriveCredential);

    boolean writeBackupLocalFile(BackupCacheHelper serviceCache, Uri uriLocalFile);

    JsonBackup readBackupLocalFile(Uri uriLocalFile);

    java.io.File writeTempBackup(JsonBackup jsonBackup);


}
