package com.pasich.mynotes.ui.controllers.mainActivity;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import androidx.core.view.GravityCompat;
import com.pasich.mynotes.R;
import com.pasich.mynotes.databinding.ActivityMainBinding;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DrawerNavigationTest {

    private ActivityMainBinding binding;
    private NavigationController navigation;

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity, R.style.DefaultTheme);
        binding = ActivityMainBinding.inflate(LayoutInflater.from(themed));
        activity.setContentView(binding.getRoot());
        navigation = new NavigationController(null, binding, null, null, null);
        binding.drawerLayout.openDrawer(GravityCompat.START, false);
    }

    @Test
    public void aScreenOpenedFromTheDrawer_findsItClosedOnTheWayBack() {
        AtomicBoolean opened = new AtomicBoolean();
        navigation.navigateAway(() -> opened.set(true));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150));
        assertThat(opened.get()).isTrue();

        navigation.onHostStopped();

        assertThat(binding.drawerLayout.isDrawerOpen(GravityCompat.START)).isFalse();
    }

    @Test
    public void leavingTheAppOtherwise_keepsTheDrawerAsItWas() {
        navigation.onHostStopped();

        assertThat(binding.drawerLayout.isDrawerOpen(GravityCompat.START)).isTrue();
    }
}
