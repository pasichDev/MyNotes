package com.pasich.mynotes.utils.backup.local;

import android.net.Uri;

import com.pasich.mynotes.utils.backup.models.JsonBackup;
import com.pasich.mynotes.utils.backup.BackupCacheHelper;

import java.io.File;

public interface LocalBackupHelper {

    boolean writeBackupLocalFile(BackupCacheHelper serviceCache, Uri uriLocalFile);

    JsonBackup readBackupLocalFile(Uri uriLocalFile);

}
