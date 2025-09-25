package com.pasich.mynotes.data;

import com.pasich.mynotes.utils.backup.local.LocalBackupHelper;
import com.pasich.mynotes.data.database.DbHelper;
import com.pasich.mynotes.data.database.helpers.DbNotesHelper;
import com.pasich.mynotes.data.preferences.PreferenceHelper;

public interface DataManager extends DbHelper, PreferenceHelper, DbNotesHelper, LocalBackupHelper {


}
