package com.pasich.mynotes.data.database.helpers;

import com.pasich.mynotes.data.model.Tag;
import io.reactivex.Completable;
import java.util.List;

public interface DbTransactionsHelper {

    Completable clearTagInNotes(Tag tag);

    Completable deleteTagAndMoveNotesToTrash(Tag tag);

    Completable renameTag(Tag mTag, String newName);

    Completable restoreNotesAndFixTags(List<Integer> ids);
}
