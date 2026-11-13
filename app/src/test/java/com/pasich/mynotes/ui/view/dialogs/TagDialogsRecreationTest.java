package com.pasich.mynotes.ui.view.dialogs;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentFactory;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.ui.view.dialogs.main.DeleteTagDialog;
import com.pasich.mynotes.ui.view.dialogs.main.NameTagDialog;
import com.pasich.mynotes.ui.view.dialogs.main.TagArgs;
import com.pasich.mynotes.ui.view.dialogs.main.TagOptionsBottomSheet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * After a rotation or process death the system rebuilds a dialog through its no-argument
 * constructor and hands it the saved arguments; everything a dialog needs must be in them.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TagDialogsRecreationTest {

    private static Tag tag() {
        Tag tag = new Tag().create("Work");
        tag.id = 42;
        tag.setVisibility(1);
        tag.setPosition(3);
        return tag;
    }

    /** Rebuilds {@code original} the way the FragmentManager does. */
    private static Fragment recreate(Fragment original) {
        Fragment fragment =
                new FragmentFactory()
                        .instantiate(
                                TagDialogsRecreationTest.class.getClassLoader(),
                                original.getClass().getName());
        Bundle args = original.getArguments();
        fragment.setArguments(args == null ? null : new Bundle(args));
        return fragment;
    }

    private static void assertSameTag(Tag actual) {
        assertThat(actual).isNotNull();
        assertThat(actual.getId()).isEqualTo(42);
        assertThat(actual.getNameTag()).isEqualTo("Work");
        assertThat(actual.getVisibility()).isEqualTo(1);
        assertThat(actual.getPosition()).isEqualTo(3);
    }

    @Test
    public void deleteTagDialog_isRebuiltWithItsTag() {
        Fragment fragment = recreate(DeleteTagDialog.newInstance(tag()));

        assertThat(fragment).isInstanceOf(DeleteTagDialog.class);
        assertSameTag(TagArgs.get(fragment.getArguments()));
    }

    @Test
    public void renameTagDialog_isRebuiltWithItsTag() {
        Fragment fragment = recreate(NameTagDialog.newRenameInstance(tag()));

        assertThat(fragment).isInstanceOf(NameTagDialog.class);
        assertSameTag(TagArgs.get(fragment.getArguments()));
    }

    @Test
    public void createTagDialog_isRebuiltWithoutATag() {
        Fragment fragment = recreate(NameTagDialog.newCreateInstance(5));

        assertThat(fragment).isInstanceOf(NameTagDialog.class);
        assertThat(TagArgs.get(fragment.getArguments())).isNull();
    }

    @Test
    public void tagOptionsSheet_isRebuiltWithItsTag() {
        Fragment fragment = recreate(TagOptionsBottomSheet.newInstance(tag(), 7));

        assertThat(fragment).isInstanceOf(TagOptionsBottomSheet.class);
        assertSameTag(TagArgs.get(fragment.getArguments()));
        assertThat(fragment.requireArguments().getInt("notesCount")).isEqualTo(7);
    }
}
