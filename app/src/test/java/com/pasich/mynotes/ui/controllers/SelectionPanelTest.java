package com.pasich.mynotes.ui.controllers;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import com.pasich.mynotes.R;
import com.pasich.mynotes.utils.adapters.notes.NoteAdapter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SelectionPanelTest {

    private Context context;
    private FrameLayout root;
    private SelectionController controller;

    @Before
    public void setUp() {
        context =
                new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.DefaultTheme);
        root = new FrameLayout(context);
        View panel = LayoutInflater.from(context).inflate(R.layout.action_panel, root, false);
        panel.setId(R.id.actionInclude);
        root.addView(panel);
        controller = new SelectionController(new NoteAdapter(), root);
    }

    @Test
    public void inTheTrash_thereIsNoTagButton() {
        controller.setPanelMode(SelectionController.Mode.RESTORE);

        assertThat(root.findViewById(R.id.action_tag_change).getVisibility()).isEqualTo(View.GONE);
        assertThat(root.findViewById(R.id.action_restore).getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onTheNotes_theTagButtonSaysWhatItDoes() {
        controller.setPanelMode(SelectionController.Mode.NORMAL);

        View tag = root.findViewById(R.id.action_tag_change);
        assertThat(tag.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(tag.getContentDescription().toString())
                .isEqualTo(context.getString(R.string.selection_change_tag));
    }
}
