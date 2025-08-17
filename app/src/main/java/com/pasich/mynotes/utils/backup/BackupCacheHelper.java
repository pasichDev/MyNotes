package com.pasich.mynotes.utils.backup;


import com.pasich.mynotes.data.model.backup.JsonBackup;

import javax.inject.Inject;

public class BackupCacheHelper {

    private JsonBackup jsonBackup;

    @Inject
    public BackupCacheHelper() {

    }

    public JsonBackup getJsonBackup() {
        return jsonBackup;
    }

    public void setJsonBackup(JsonBackup jsonBackup) {
        this.jsonBackup = jsonBackup;
    }
}
