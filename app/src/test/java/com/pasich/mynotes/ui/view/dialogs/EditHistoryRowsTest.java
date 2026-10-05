package com.pasich.mynotes.ui.view.dialogs;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.view.MoreNoteNoteActivityView;
import com.pasich.mynotes.databinding.DialogMoreNoteBinding;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Undo and Redo in the editor's More sheet. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class EditHistoryRowsTest {

    /** Stands in for a note editor. */
    private static final class Editor implements MoreNoteNoteActivityView {
        boolean editing;
        boolean canUndo;
        boolean canRedo;

        @Override
        public boolean isEditingNote() {
            return editing;
        }

        @Override
        public boolean canUndoEdit() {
            return canUndo;
        }

        @Override
        public boolean canRedoEdit() {
            return canRedo;
        }

        @Override
        public void undoLastEdit() {}

        @Override
        public void redoLastEdit() {}

        @Override
        public void setEditHistoryObserver(@Nullable Runnable observer) {}

        @Override
        public void changeTextStyle() {}

        @Override
        public void changeTextSizeOnline(int sizeText) {}

        @Override
        public void changeTextSizeOffline() {}

        @Override
        public void closeActivityNotSaved() {}

        @Override
        public void changeTag(String nameTag, boolean change) {}

        @Override
        public void openCopyNote(long idNote) {}

        @Override
        public void changeEditor(long idNote) {}

        @Override
        public void openVersionHistory() {}
    }

    private DialogMoreNoteBinding binding;
    private final Editor editor = new Editor();

    @Before
    public void setUp() {
        Context context =
                new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.DefaultTheme);
        binding = DialogMoreNoteBinding.inflate(LayoutInflater.from(context));
    }

    private void apply(@Nullable MoreNoteNoteActivityView host) {
        EditHistoryRows.apply(binding.editHistoryGroup, binding.moreUndo, binding.moreRedo, host);
    }

    private static float contentAlpha(View row) {
        return ((ViewGroup) row).getChildAt(0).getAlpha();
    }

    @Test
    public void hiddenUntilInflatedForAnEditor() {
        assertThat(binding.editHistoryGroup.getVisibility()).isEqualTo(View.GONE);

        apply(null);

        assertThat(binding.editHistoryGroup.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void hiddenWhileTheNoteIsRead() {
        editor.canUndo = true;

        apply(editor);

        assertThat(binding.editHistoryGroup.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void shownWhileEditing_enabledByTheHistory() {
        editor.editing = true;
        editor.canUndo = true;

        apply(editor);

        assertThat(binding.editHistoryGroup.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(binding.moreUndo.isEnabled()).isTrue();
        assertThat(contentAlpha(binding.moreUndo)).isEqualTo(1f);
        assertThat(binding.moreRedo.isEnabled()).isFalse();
        assertThat(contentAlpha(binding.moreRedo)).isEqualTo(EditHistoryRows.DISABLED_ALPHA);
    }

    @Test
    public void followsTheHistoryWhenAppliedAgain() {
        editor.editing = true;
        editor.canUndo = true;
        apply(editor);

        editor.canUndo = false;
        editor.canRedo = true;
        apply(editor);

        assertThat(binding.moreUndo.isEnabled()).isFalse();
        assertThat(contentAlpha(binding.moreUndo)).isEqualTo(EditHistoryRows.DISABLED_ALPHA);
        assertThat(binding.moreRedo.isEnabled()).isTrue();
        assertThat(contentAlpha(binding.moreRedo)).isEqualTo(1f);
    }

    @Test
    public void rowsAreLabelledUndoAndRedo() {
        Context context = binding.getRoot().getContext();
        TextView undoLabel = (TextView) ((ViewGroup) binding.moreUndo).getChildAt(1);
        TextView redoLabel = (TextView) ((ViewGroup) binding.moreRedo).getChildAt(1);

        assertThat(undoLabel.getText().toString())
                .isEqualTo(context.getString(R.string.editor_undo));
        assertThat(redoLabel.getText().toString())
                .isEqualTo(context.getString(R.string.editor_redo));
    }
}
