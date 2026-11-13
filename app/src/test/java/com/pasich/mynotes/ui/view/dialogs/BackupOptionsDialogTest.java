package com.pasich.mynotes.ui.view.dialogs;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class BackupOptionsDialogTest {

    @Test
    public void canBeRecreatedByTheFramework() {
        // What the fragment manager does after a rotation or process death.
        Fragment recreated =
                new FragmentFactory()
                        .instantiate(
                                getClass().getClassLoader(), BackupOptionsDialog.class.getName());

        assertTrue(recreated instanceof BackupOptionsDialog);
    }

    @Test
    public void keepsTheModeInItsArguments() {
        BackupOptionsDialog dialog = BackupOptionsDialog.newInstance(true);

        assertNotNull(dialog.getArguments());
        assertTrue(dialog.getArguments().getBoolean("isRestore"));
    }
}
