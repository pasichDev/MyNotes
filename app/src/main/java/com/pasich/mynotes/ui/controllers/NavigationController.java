package com.pasich.mynotes.ui.controllers;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.pasich.mynotes.R;
import com.pasich.mynotes.databinding.ActivityMainBinding;
import com.pasich.mynotes.ui.view.activity.AboutActivity;
import com.pasich.mynotes.ui.view.activity.BackupActivity;
import com.pasich.mynotes.ui.view.activity.SettingsActivity;
import com.pasich.mynotes.ui.view.activity.SupportActivity;
import com.pasich.mynotes.ui.view.activity.TagsActivity;
import com.pasich.mynotes.ui.view.activity.TrashActivity;

public class NavigationController {

    private final AppCompatActivity activity;
    private final ActivityMainBinding binding;

    private final ActivityResultLauncher<Intent> themeActivityLauncher;

    private final AppUpdateController appUpdateController;
    private final BackHandler backHandler;
    private DrawerLayout drawerLayout;

    private int swipeClose = 0;

    public NavigationController(AppCompatActivity activity,
                                ActivityMainBinding binding,
                                ActivityResultLauncher<Intent> themeActivityLauncher,
                                AppUpdateController appUpdateController,
                                BackHandler backHandler) {
        this.activity = activity;
        this.binding = binding;
        this.themeActivityLauncher = themeActivityLauncher;
        this.appUpdateController = appUpdateController;
        this.backHandler = backHandler;
    }

    public void init() {
        drawerLayout = binding.drawerLayout;

        setupEdgeToEdge();
        setupDrawerButton();
        setupNavigationMenu();
        setupHeaderButtons();
        handleBackPressed();
    }

    /**
     * Edge-to-Edge for Drawer/Root
     */
    private void setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            ViewCompat.setOnApplyWindowInsetsListener(binding.activityMain, (mainView, mainInsets) -> {
                Insets systemBars = mainInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                mainView.setPadding(0, systemBars.top, 0, systemBars.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
            return insets;
        });
    }

    private void setupDrawerButton() {
        binding.actionSearch.setNavigationOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });
    }

    private void setupNavigationMenu() {
        binding.navigationView.setNavigationItemSelectedListener(this::onNavigationItemSelected);
    }

    private boolean onNavigationItemSelected(@NonNull MenuItem item) {
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private void setupHeaderButtons() {
        View header = binding.navigationView.getHeaderView(0);

        header.findViewById(R.id.nav_tags).setOnClickListener(v ->
                delay(() -> activity.startActivity(new Intent(activity, TagsActivity.class)))
        );

        header.findViewById(R.id.nav_trash).setOnClickListener(v ->
                delay(() -> activity.startActivity(new Intent(activity, TrashActivity.class)))
        );

        header.findViewById(R.id.nav_settings).setOnClickListener(v ->
                delay(() -> themeActivityLauncher.launch(new Intent(activity, SettingsActivity.class)))
        );

        header.findViewById(R.id.nav_backups).setOnClickListener(v ->
                delay(() -> themeActivityLauncher.launch(new Intent(activity, BackupActivity.class)))
        );

        header.findViewById(R.id.nav_about).setOnClickListener(v ->
                delay(() -> activity.startActivity(new Intent(activity, AboutActivity.class)))
        );

        View navSupport = header.findViewById(R.id.nav_support);
        if (navSupport != null) {
            navSupport.setOnClickListener(v ->
                    delay(() -> activity.startActivity(new Intent(activity, SupportActivity.class)))
            );
        }

        bindHeaderNewVersion(header);
    }

    private void bindHeaderNewVersion(View header) {
        View newVersionView = header.findViewById(R.id.newVersion);

        boolean hasNew = appUpdateController.hasNewVersion();
        newVersionView.setVisibility(hasNew ? View.VISIBLE : View.GONE);

        if (hasNew) {
            newVersionView.setOnClickListener(v ->
                    appUpdateController.openChangelog()
            );
        }
    }

    public void updateNewVersionIndicator(boolean visible) {
        View header = binding.navigationView.getHeaderView(0);
        if (header == null) return;

        View newVersionView = header.findViewById(R.id.newVersion);
        if (newVersionView == null) return;

        newVersionView.setVisibility(visible ? View.VISIBLE : View.GONE);

        if (visible) {
            newVersionView.setOnClickListener(v -> appUpdateController.openChangelog());
        } else {
            newVersionView.setOnClickListener(null);
        }
    }

    private void delay(Runnable r) {
        new Handler(Looper.getMainLooper()).postDelayed(r, 100);
    }

    private void handleBackPressed() {
        activity.getOnBackPressedDispatcher().addCallback(activity, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Спочатку — drawer
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return;
                }

                // Далі — делегуємо в Activity (finishActivity)
                if (backHandler != null) {
                    boolean shouldFinish = backHandler.onRootBack();
                    if (shouldFinish) {
                        activity.finish();
                    }
                } else {
                    activity.finish();
                }
            }
        });
    }

    /**
     * Processing App Shortcuts (open_search)
     */
    public void handleShortcuts(Intent intent) {
        if (intent != null && intent.getBooleanExtra("open_search", false)) {
            new Handler(Looper.getMainLooper()).postDelayed(binding.searchView::show, 100);
        }
    }

    public void destroy() {
        binding.navigationView.setNavigationItemSelectedListener(null);

        View header = binding.navigationView.getHeaderView(0);
        if (header != null) {
            header.findViewById(R.id.nav_tags).setOnClickListener(null);
            header.findViewById(R.id.nav_trash).setOnClickListener(null);
            header.findViewById(R.id.nav_settings).setOnClickListener(null);
            header.findViewById(R.id.nav_backups).setOnClickListener(null);
            header.findViewById(R.id.nav_about).setOnClickListener(null);

            View navSupport = header.findViewById(R.id.nav_support);
            if (navSupport != null) {
                navSupport.setOnClickListener(null);
            }
            View newVersion = header.findViewById(R.id.newVersion);
            if (newVersion != null) {
                newVersion.setOnClickListener(null);
            }
        }


        binding.actionSearch.setNavigationOnClickListener(null);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), null);
        ViewCompat.setOnApplyWindowInsetsListener(binding.activityMain, null);

        drawerLayout = null;
    }

    public void addSwipeClose(int delta) {
        this.swipeClose += delta;
    }

    public int getSwipeClose() {
        return swipeClose;
    }

    /**
     * true – if the activity needs to be closed, false – if only back has been processed (like closing the panel/snack)
     */
    public interface BackHandler {
        boolean onRootBack();
    }

}
