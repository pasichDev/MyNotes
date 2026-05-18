package com.pasich.mynotes.data.database.helpers;

import io.reactivex.Flowable;

/** Provides reactive queries for app usage statistics. */
public interface StatsHelper {

    Flowable<Integer> getNotesCount();

    Flowable<Integer> getNotesCreatedLastMonth();

    Flowable<Long> getTotalCharacters();
}
