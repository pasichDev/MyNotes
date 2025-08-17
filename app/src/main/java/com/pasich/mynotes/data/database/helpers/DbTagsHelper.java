package com.pasich.mynotes.data.database.helpers;

import com.pasich.mynotes.data.model.Tag;

import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;

public interface DbTagsHelper {
    /**
     * Tags
     */
    Flowable<List<Tag>> getTags();

    Flowable<List<Tag>> getTagsUser();

    Single<Integer> getCountTagAll();

    Completable addTag(Tag tag);

    Completable addTags(List<Tag> tags);

    Completable deleteTag(Tag tag);

    Completable updateTag(Tag tag);
}
