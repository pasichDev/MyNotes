package com.pasich.mynotes.data.database.helpers;

import com.pasich.mynotes.data.model.Tag;

import java.util.List;

import io.reactivex.Completable;

public interface DbTransactionsHelper {


    Completable clearTagInNotes(Tag tag);

    Completable deleteTagAndMoveNotesToTrash(Tag tag);

    Completable renameTag(Tag mTag, String newName);

    Completable restoreNotesAndFixTags(List<Integer> ids);
}
