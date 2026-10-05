package com.pasich.mynotes.utils;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import com.pasich.mynotes.cache.AppPreferencesCache;
import com.pasich.mynotes.data.preferences.SafePreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The one-time "Meet Encly" sheet: shown on the first start after an update, then never again. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class UpdateCheckerMeetEnclyTest {

    private Context app;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
    }

    /** A fresh checker over the same stored preferences, as after a process restart. */
    private UpdateChecker start(String installedVersion) throws Exception {
        PackageInfo info = new PackageInfo();
        info.versionName = installedVersion;
        PackageManager packageManager = mock(PackageManager.class);
        when(packageManager.getPackageInfo(anyString(), anyInt())).thenReturn(info);
        Context context = mock(Context.class);
        when(context.getPackageManager()).thenReturn(packageManager);
        when(context.getPackageName()).thenReturn("com.pasich.mynotes");
        return new UpdateChecker(context, new AppPreferencesCache(new SafePreferences(app)));
    }

    @Test
    public void firstStartAfterUpdate_showsOnce_thenNeverAgain() throws Exception {
        // The previous version was started and its changelog read.
        start("2.6.55").markVersionAsRead();

        UpdateChecker updated = start("2.7.0");
        assertThat(updated.shouldShowMeetEncly()).isTrue();
        updated.markMeetEnclyShown();
        assertThat(updated.shouldShowMeetEncly()).isFalse();

        // Restarts, and later updates, never bring it back.
        assertThat(start("2.7.0").shouldShowMeetEncly()).isFalse();
        assertThat(start("2.8.0").shouldShowMeetEncly()).isFalse();
    }

    @Test
    public void openingThePageFirst_marksTheIntroductionSeen() throws Exception {
        // The previous version was started and its changelog read.
        start("2.6.55").markVersionAsRead();

        // MeetEnclyActivity marks it on open, before MainActivity could show the sheet.
        start("2.7.0").markMeetEnclyShown();

        assertThat(start("2.7.0").shouldShowMeetEncly()).isFalse();
    }
}
