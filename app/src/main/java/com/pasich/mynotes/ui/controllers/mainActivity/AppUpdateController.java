package com.pasich.mynotes.ui.controllers.mainActivity;

import android.app.Activity;
import android.content.Intent;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import com.pasich.mynotes.ui.view.activity.ChangelogActivity;
import com.pasich.mynotes.ui.view.activity.MeetEnclyActivity;
import com.pasich.mynotes.ui.view.dialogs.MeetEnclyDialog;
import com.pasich.mynotes.ui.view.dialogs.UpdateChangelogDialog;
import com.pasich.mynotes.utils.UpdateChecker;

/**
 * Shows what changed after the app was updated. It never asks the user to update: Google Play
 * updates the app on its own, and a blocking update screen on every launch drove users away.
 */
public class AppUpdateController {

    private static final String CHANGELOG_TAG = "UpdateChangelogDialog";
    private static final String MEET_ENCLY_TAG = "MeetEnclyDialog";

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

    /**
     * Shows the changelog dialog if a new app version was detected. On the first start of the
     * version that introduces Encly, the one-time "Meet Encly" dialog takes its place; the
     * changelog stays unread and follows on the next start.
     */
    public void showChangelogIfNeeded() {
        FragmentManager fragmentManager =
                ((AppCompatActivity) activity).getSupportFragmentManager();
        // A recreated activity gets its open dialog back from the fragment manager.
        if (fragmentManager.findFragmentByTag(MEET_ENCLY_TAG) != null
                || fragmentManager.findFragmentByTag(CHANGELOG_TAG) != null) {
            return;
        }
        if (updateChecker.shouldShowMeetEncly()) {
            updateChecker.markMeetEnclyShown();
            MeetEnclyDialog.newInstance().show(fragmentManager, MEET_ENCLY_TAG);
            return;
        }
        if (updateChecker.hasNewVersion()) {
            UpdateChangelogDialog.newInstance().show(fragmentManager, CHANGELOG_TAG);
        }
    }

    /** Opens the "Meet Encly" page. */
    public void openMeetEncly() {
        activity.startActivity(new Intent(activity, MeetEnclyActivity.class));
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
