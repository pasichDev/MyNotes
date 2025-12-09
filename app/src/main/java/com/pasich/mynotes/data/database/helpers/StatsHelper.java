package com.pasich.mynotes.data.database.helpers;

import io.reactivex.Flowable;

public interface StatsHelper {

    Flowable<Integer> getNotesCount();

    Flowable<Integer> getNotesCreatedLastMonth();

    Flowable<Long> getTotalCharacters();
}
