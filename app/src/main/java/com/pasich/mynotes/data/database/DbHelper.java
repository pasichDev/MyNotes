package com.pasich.mynotes.data.database;


import com.pasich.mynotes.data.database.helpers.DbNotesHelper;
import com.pasich.mynotes.data.database.helpers.DbTagsHelper;
import com.pasich.mynotes.data.database.helpers.DbTransactionsHelper;
import com.pasich.mynotes.data.database.helpers.DbTrashHelper;

public interface DbHelper extends DbTagsHelper, DbTrashHelper, DbNotesHelper, DbTransactionsHelper {


}
