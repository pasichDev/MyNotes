package com.pasich.mynotes.data.database;

import com.pasich.mynotes.data.database.helpers.DbNotesHelper;
import com.pasich.mynotes.data.database.helpers.DbTagsHelper;
import com.pasich.mynotes.data.database.helpers.DbTasksHelper;
import com.pasich.mynotes.data.database.helpers.DbTransactionsHelper;
import com.pasich.mynotes.data.database.helpers.StatsHelper;

public interface DbHelper
        extends DbTagsHelper, DbNotesHelper, DbTransactionsHelper, StatsHelper, DbTasksHelper {}
