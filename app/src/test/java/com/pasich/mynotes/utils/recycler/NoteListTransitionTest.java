package com.pasich.mynotes.utils.recycler;

import static com.google.common.truth.Truth.assertThat;

import com.pasich.mynotes.data.model.Note;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class NoteListTransitionTest {

    private static List<Note> notes(int... ids) {
        List<Note> list = new ArrayList<>();
        for (int id : ids) {
            Note n = new Note().create("t" + id, "v" + id, id, "");
            n.setId(id);
            list.add(n);
        }
        return list;
    }

    @Test
    public void sameOrder_noCrossfade() {
        assertThat(NoteListTransition.needsCrossfade(notes(3, 2, 1), notes(3, 2, 1), 2)).isFalse();
    }

    @Test
    public void reorder_crossfadesInAnyLayout() {
        assertThat(NoteListTransition.needsCrossfade(notes(3, 2, 1), notes(1, 3, 2), 1)).isTrue();
        assertThat(NoteListTransition.needsCrossfade(notes(3, 2, 1), notes(1, 3, 2), 2)).isTrue();
    }

    @Test
    public void insertOrRemove_crossfadesOnlyInGrid() {
        assertThat(NoteListTransition.needsCrossfade(notes(3, 2, 1), notes(4, 3, 2, 1), 1))
                .isFalse();
        assertThat(NoteListTransition.needsCrossfade(notes(3, 2, 1), notes(3, 1), 1)).isFalse();
        assertThat(NoteListTransition.needsCrossfade(notes(3, 2, 1), notes(4, 3, 2, 1), 2))
                .isTrue();
        assertThat(NoteListTransition.needsCrossfade(notes(3, 2, 1), notes(3, 1), 2)).isTrue();
    }

    @Test
    public void fromOrToEmpty_neverCrossfades() {
        assertThat(NoteListTransition.needsCrossfade(notes(), notes(1, 2), 2)).isFalse();
        assertThat(NoteListTransition.needsCrossfade(notes(1, 2), notes(), 2)).isFalse();
    }
}
