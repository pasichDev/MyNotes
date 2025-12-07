package com.pasich.mynotes.data.database.helpers;

import io.reactivex.Single;

public interface StatsHelper {

    Single<Integer> getNotesCount();

    Single<Integer> getNotesCreatedLastMonth();

    Single<Long> getTotalCharacters();
}
