package com.pasich.mynotes.ui.view.activity.noteEditor;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.view.Menu;
import android.view.View;
import android.widget.PopupMenu;
import androidx.appcompat.view.ContextThemeWrapper;
import com.pasich.mynotes.R;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Undo and Redo moved to the bar above the keyboard; the note toolbars no longer carry them. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NoteToolbarMenuTest {

    private final Context context =
            new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.DefaultTheme);

    private Menu inflate(int menuRes) {
        PopupMenu popup = new PopupMenu(context, new View(context));
        popup.getMenuInflater().inflate(menuRes, popup.getMenu());
        return popup.getMenu();
    }

    private static List<Integer> ids(Menu menu) {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < menu.size(); i++) ids.add(menu.getItem(i).getItemId());
        return ids;
    }

    private void assertNoHistoryItems(Menu menu) {
        String undo = context.getString(R.string.editor_undo);
        String redo = context.getString(R.string.editor_redo);
        for (int i = 0; i < menu.size(); i++) {
            CharSequence title = menu.getItem(i).getTitle();
            assertThat(String.valueOf(title)).isNotEqualTo(undo);
            assertThat(String.valueOf(title)).isNotEqualTo(redo);
        }
    }

    @Test
    public void simpleEditorToolbar_isSaveStatusAndMore() {
        Menu menu = inflate(R.menu.menu_activity_toolbar_note);

        assertThat(ids(menu)).containsExactly(R.id.saveStatusBut, R.id.moreBut).inOrder();
        assertNoHistoryItems(menu);
    }

    @Test
    public void extendedEditorToolbar_isSaveStatusReadAndMore() {
        Menu menu = inflate(R.menu.menu_activity_toolbar_note_extendes);

        assertThat(ids(menu))
                .containsExactly(R.id.saveStatusBut, R.id.actionRead, R.id.moreBut)
                .inOrder();
        assertNoHistoryItems(menu);
    }
}
