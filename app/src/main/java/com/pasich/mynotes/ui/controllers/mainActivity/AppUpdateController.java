package com.pasich.mynotes.ui.controllers.mainActivity;

import android.app.Activity;
import android.content.Intent;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import com.pasich.mynotes.ui.view.activity.ChangelogActivity;
import com.pasich.mynotes.ui.view.dialogs.UpdateChangelogDialog;
import com.pasich.mynotes.utils.UpdateChecker;

/**
 * Shows what changed after the app was updated. It never asks the user to update: Google Play
 * updates the app on its own, and a blocking update screen on every launch drove users away.
 */
public class AppUpdateController {

    private final Activity activity;
    private final UpdateChecker updateChecker;
    private final ActivityResultLauncher<Intent> changelogLauncher;

    public AppUpdateController(
            Activity activity,
            UpdateChecker updateChecker,
            ActivityResultLauncher<Intent> changelogLauncher) {
        this.activity = activity;
        this.updateChecker = updateChecker;
        this.changelogLauncher = changelogLauncher;
        updateChecker.initializeVersionCheck();
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
