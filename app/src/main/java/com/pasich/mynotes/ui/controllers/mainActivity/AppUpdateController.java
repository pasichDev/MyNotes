package com.pasich.mynotes.ui.controllers.mainActivity;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.pasich.mynotes.ui.view.activity.ChangelogActivity;
import com.pasich.mynotes.ui.view.dialogs.UpdateChangelogDialog;
import com.pasich.mynotes.utils.UpdateChecker;

/** Handles in-app update checks and changelog display. */
public class AppUpdateController {

    private static final int REQUEST_UPDATE = 100;

    private final Activity activity;
    private final UpdateChecker updateChecker;
    private final ActivityResultLauncher<Intent> changelogLauncher;

    private AppUpdateManager updateManager;

    public AppUpdateController(
            Activity activity,
            UpdateChecker updateChecker,
            ActivityResultLauncher<Intent> changelogLauncher) {
        this.activity = activity;
        this.updateChecker = updateChecker;
        this.changelogLauncher = changelogLauncher;

        init();
    }

    private void init() {
        updateManager = AppUpdateManagerFactory.create(activity);
        updateChecker.initializeVersionCheck();
        checkForUpdate();
    }

    /** Resumes an in-progress immediate update flow if one was started. */
    public void handleOnResume() {
        updateManager
                .getAppUpdateInfo()
                .addOnSuccessListener(
                        info -> {
                            if (info.updateAvailability()
                                    == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                                startImmediateUpdate(info);
                            }
                        });
    }

    private void checkForUpdate() {
        updateManager
                .getAppUpdateInfo()
                .addOnSuccessListener(
                        info -> {
                            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                                    && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {

                                startImmediateUpdate(info);
                            }
                        })
                .addOnFailureListener(e -> Log.d("AppUpdate", "check error: " + e.getMessage()));
    }

    private void startImmediateUpdate(AppUpdateInfo info) {
        try {
            updateManager.startUpdateFlowForResult(
                    info, AppUpdateType.IMMEDIATE, activity, REQUEST_UPDATE);
        } catch (Exception e) {
            Log.d("AppUpdate", "startUpdate error: " + e.getMessage());
        }
    }

    /** Shows the changelog dialog if a new app version was detected. */
    public void showChangelogIfNeeded() {
        if (updateChecker.hasNewVersion()) {
            UpdateChangelogDialog.newInstance()
                    .show(
                            ((AppCompatActivity) activity).getSupportFragmentManager(),
                            "UpdateChangelogDialog");
        }
    }

    /** Returns whether a newer app version is available. */
    public boolean hasNewVersion() {
        return updateChecker.hasNewVersion();
    }

    /** Launches the changelog screen via the registered activity launcher. */
    public void openChangelog() {
        changelogLauncher.launch(new Intent(activity, ChangelogActivity.class));
    }
}
