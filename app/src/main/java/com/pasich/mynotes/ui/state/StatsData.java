package com.pasich.mynotes.ui.state;

/** Aggregated statistics shown in the navigation drawer. */
public record StatsData(int notesNow, int notesMonth, long chars) {

    public StatsData() {
        this(0, 0, 0);
    }
}
