package com.pasich.mynotes.ui.state;

public record StatsData(int notesNow, int notesMonth, long chars) {

    public StatsData() {
        this(0, 0, 0);
    }
}
