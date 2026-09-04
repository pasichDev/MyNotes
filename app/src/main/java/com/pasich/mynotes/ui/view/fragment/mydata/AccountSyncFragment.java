package com.pasich.mynotes.ui.view.fragment.mydata;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.sync.SyncState;
import com.pasich.mynotes.databinding.FragmentAccountSyncBinding;
import com.pasich.mynotes.ui.sync.SyncCoordinator;

/**
 * The "Account" tab: identity, sync status and the sync controls.
 *
 * <p>It renders only. The activity owns the {@link SyncCoordinator} because Drive authorization
 * comes back through {@code onActivityResult}, so every action is delegated to {@link Host} and the
 * resulting state is pushed back in through {@link #render}.
 */
public class AccountSyncFragment extends Fragment {

    /** Implemented by the hosting activity. */
    public interface Host {
        void onAccountTabAttached(@NonNull AccountSyncFragment fragment);

        void onAccountTabDetached();

        void onAccountSignInClicked();

        void onAccountSignOutClicked();

        void onAccountSyncClicked();

        void onAccountBackgroundSyncChanged(boolean enabled);

        void onAccountConflictsClicked();
    }

    private FragmentAccountSyncBinding binding;
    private Host host;
    private boolean updatingControls;

    public AccountSyncFragment() {}

    public static AccountSyncFragment newInstance() {
        return new AccountSyncFragment();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        host = context instanceof Host ? (Host) context : null;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentAccountSyncBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.googleSignInButtonSignedOut.setOnClickListener(
                v -> {
                    if (host != null) host.onAccountSignInClicked();
                });
        binding.googleSignInButton.setOnClickListener(
                v -> {
                    if (host != null) host.onAccountSignOutClicked();
                });
        binding.syncButton.setOnClickListener(
                v -> {
                    if (host != null) host.onAccountSyncClicked();
                });
        binding.syncConflictsButton.setOnClickListener(
                v -> {
                    if (host != null) host.onAccountConflictsClicked();
                });
        binding.syncBackgroundSwitch.setOnCheckedChangeListener(
                (button, checked) -> {
                    // Programmatic updates must not be mistaken for a user toggle.
                    if (!updatingControls && host != null) {
                        host.onAccountBackgroundSyncChanged(checked);
                    }
                });
        if (host != null) host.onAccountTabAttached(this);
    }

    @Override
    public void onDestroyView() {
        if (host != null) host.onAccountTabDetached();
        binding = null;
        super.onDestroyView();
    }

    /** True while the views exist; the activity checks before pushing state in. */
    public boolean isBound() {
        return binding != null;
    }

    /** Shows the sync progress in place of the button's label. */
    public void setSyncing(boolean syncing) {
        if (binding == null) return;
        binding.syncProgress.setVisibility(syncing ? View.VISIBLE : View.GONE);
        binding.syncButton.setText(syncing ? "" : getString(R.string.sync_now));
        binding.syncButton.setEnabled(!syncing);
    }

    /** Draws one complete state; the activity computes all of it off the main thread. */
    public void render(
            @NonNull SyncCoordinator.Profile profile,
            @Nullable SyncState state,
            int unresolvedConflicts,
            boolean backgroundSyncEnabled,
            @NonNull CharSequence statusText,
            @NonNull CharSequence lastSyncText) {
        if (binding == null) return;
        boolean signedIn = profile.isSignedIn();
        // The three groups are mutually exclusive; rendering real state always clears the
        // unavailable notice so a recreated view cannot show both.
        binding.syncUnavailableGroup.setVisibility(View.GONE);
        binding.signedInGroup.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        binding.signedOutGroup.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        if (!signedIn) {
            return;
        }
        binding.googleName.setText(profile.getDisplayName());
        binding.googleEmail.setText(profile.getEmail());
        binding.googleAvatar.setText(profile.getAvatarLabel());
        binding.syncStatusText.setText(statusText);
        binding.syncLastSyncText.setText(lastSyncText);

        updatingControls = true;
        binding.syncBackgroundSwitch.setChecked(backgroundSyncEnabled);
        updatingControls = false;

        binding.syncConflictsButton.setVisibility(
                unresolvedConflicts > 0 ? View.VISIBLE : View.GONE);
        if (unresolvedConflicts > 0) {
            binding.syncConflictsButton.setText(
                    getString(R.string.sync_review_conflicts_count, unresolvedConflicts));
        }
    }

    /** Hides everything when the build has no Firebase configuration. */
    public void showUnavailable() {
        if (binding == null) return;
        binding.signedInGroup.setVisibility(View.GONE);
        binding.signedOutGroup.setVisibility(View.GONE);
        binding.syncUnavailableGroup.setVisibility(View.VISIBLE);
    }
}
