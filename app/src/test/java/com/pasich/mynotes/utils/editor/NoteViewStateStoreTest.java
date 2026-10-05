package com.pasich.mynotes.utils.editor;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NoteViewStateStoreTest {

    private Context context;
    private long now = 1_000L;
    private NoteViewStateStore store;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        store = new NoteViewStateStore(context, () -> now++);
        store.clear();
    }

    private static NoteViewState.Simple simple(int caret) {
        return PositionRestorer.captureSimple("some text in a note body", caret, caret, 0, 0, 0f);
    }

    @Test
    public void positionSurvivesANewInstance() {
        store.putSimple(7, simple(5));

        NoteViewState read = new NoteViewStateStore(context).get(7);

        assertThat(read).isNotNull();
        assertThat(read.simple).isNotNull();
        assertThat(read.simple.selectionStart).isEqualTo(5);
    }

    @Test
    public void eachEditorKeepsItsOwnSection() {
        store.putSimple(3, simple(2));
        NoteViewState.Extended extended = new NoteViewState.Extended();
        extended.caretBlockId = "block";
        store.putExtended(3, extended);

        NoteViewState read = store.get(3);

        assertThat(read.simple.selectionStart).isEqualTo(2);
        assertThat(read.extended.caretBlockId).isEqualTo("block");
    }

    @Test
    public void removeForgetsOnlyThatNote() {
        store.putSimple(1, simple(1));
        store.putSimple(2, simple(2));

        store.remove(1);

        assertThat(store.get(1)).isNull();
        assertThat(store.get(2)).isNotNull();
    }

    @Test
    public void removeAllForgetsEveryListedNote() {
        store.putSimple(1, simple(1));
        store.putSimple(2, simple(2));
        store.putSimple(3, simple(3));

        store.removeAll(Arrays.asList(1, 3));

        assertThat(store.get(1)).isNull();
        assertThat(store.get(2)).isNotNull();
        assertThat(store.get(3)).isNull();
    }

    @Test
    public void keepsOnlyTheMostRecentlyUsedNotes() {
        for (int id = 1; id <= NoteViewStateStore.MAX_ENTRIES + 5; id++) {
            store.putSimple(id, simple(1));
        }

        assertThat(store.get(1)).isNull();
        assertThat(store.get(5)).isNull();
        assertThat(store.get(6)).isNotNull();
        assertThat(store.get(NoteViewStateStore.MAX_ENTRIES + 5)).isNotNull();
    }

    @Test
    public void rewritingAnOldEntryMakesItRecent() {
        for (int id = 1; id <= NoteViewStateStore.MAX_ENTRIES; id++) {
            store.putSimple(id, simple(1));
        }
        store.putSimple(1, simple(2));

        store.putSimple(NoteViewStateStore.MAX_ENTRIES + 1, simple(1));

        assertThat(store.get(1)).isNotNull();
        assertThat(store.get(2)).isNull();
    }

    @Test
    public void unreadableEntryIsTreatedAsMissing() {
        context.getSharedPreferences(NoteViewStateStore.FILE, Context.MODE_PRIVATE)
                .edit()
                .putString("n_9", "{not json")
                .commit();

        assertThat(store.get(9)).isNull();
    }

    @Test
    public void invalidIdsAreIgnored() {
        store.putSimple(0, simple(1));

        assertThat(store.get(0)).isNull();
        assertThat(store.contains(0)).isFalse();
    }

    @Test
    public void leastRecentlyUsed_ordersByTimeThenKey() {
        Map<String, Long> usedAt = new HashMap<>();
        usedAt.put("n_3", 30L);
        usedAt.put("n_1", 10L);
        usedAt.put("n_2", 10L);

        assertThat(NoteViewStateStore.leastRecentlyUsed(usedAt, 2))
                .containsExactly("n_1", "n_2")
                .inOrder();
        assertThat(NoteViewStateStore.leastRecentlyUsed(usedAt, 0)).isEmpty();
    }
}
