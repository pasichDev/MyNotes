package com.pasich.mynotes.ui.view.dialogs.about;

import static com.pasich.mynotes.utils.constants.settings.BackupPreferences.ARGUMENT_AUTO_BACKUP_CLOUD;
import static com.pasich.mynotes.utils.constants.settings.BackupPreferences.ARGUMENT_LAST_BACKUP_ID;
import static com.pasich.mynotes.utils.constants.settings.BackupPreferences.ARGUMENT_LAST_BACKUP_TIME;
import static com.pasich.mynotes.utils.constants.settings.BackupPreferences.FIlE_NAME_PREFERENCE_BACKUP;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.material.snackbar.Snackbar;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.dialog.BaseDialogBottomSheets;
import com.pasich.mynotes.databinding.DialogAboutBinding;
import com.pasich.mynotes.utils.backup.CloudAuthHelper;
import com.pasich.mynotes.utils.backup.CloudCacheHelper;
import com.pasich.mynotes.utils.constants.DriveScope;
import com.pasich.mynotes.utils.constants.SnackBarInfo;
import com.preference.PowerPreference;
import com.preference.Preference;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class AboutDialog extends BaseDialogBottomSheets {
    private final AboutOpensActivity aboutOpensActivity;
    @Inject
    public GoogleSignInClient googleSignInClient;
    @Inject
    public CloudCacheHelper cloudCacheHelper;
    @Inject
    public CloudAuthHelper cloudAuthHelper;
    public DialogAboutBinding binding;
    final private ActivityResultLauncher<Intent> startAuthIntent = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {

            cloudAuthHelper.getResultAuth(result.getData()).addOnFailureListener((GoogleSignInAccount) -> onInfoSnack(R.string.errorAuth, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG)).addOnSuccessListener((GoogleSignInAccount) -> {
                cloudCacheHelper.update(GoogleSignInAccount, GoogleSignIn.hasPermissions(GoogleSignInAccount, DriveScope.ACCESS_DRIVE_SCOPE), true);
                loadingDataUser(true);
            });
        }
    });


    public AboutDialog(AboutOpensActivity aboutOpensActivity) {
        this.aboutOpensActivity = aboutOpensActivity;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogAboutBinding.inflate(getLayoutInflater());
        initAccountInfo();
        initListeners();
        return binding.getRoot();
    }


    private void initAccountInfo() {
        binding.loginPage.viewLoginRoot.setVisibility(cloudCacheHelper.isInstallPlayMarket() ? View.VISIBLE : View.GONE);
        loadingDataUser(cloudCacheHelper.isAuth());
    }

    public void initListeners() {

        binding.loginPage.exitUser.setOnClickListener(v -> signOut());

        binding.loginPage.loginUser.setOnClickListener(v -> startAuthIntent.launch(googleSignInClient.getSignInIntent()));

        binding.trashActivityLayout.setOnClickListener(v -> {
            dismiss();
            aboutOpensActivity.openTrash();
        });
        binding.backups.setOnClickListener(v -> {
            dismiss();
            aboutOpensActivity.openBackupActivity();
        });

        binding.themeApp.setOnClickListener(v -> {
            aboutOpensActivity.openThemeActivity();
            dismiss();
        });


        binding.aboutApp.setOnClickListener(v -> {
            dismiss();
            aboutOpensActivity.openAboutActivity();

        });
    }


    private void loadingDataUser(boolean isAuth) {

        if (isAuth) {

            String nameUser = cloudCacheHelper.getGoogleSignInAccount().getDisplayName();
            binding.loginPage.nameUser.setText(nameUser);
            binding.loginPage.emailUSer.setText(cloudCacheHelper.getGoogleSignInAccount().getEmail());
            Glide.with(requireContext()).load(cloudCacheHelper.getGoogleSignInAccount().getPhotoUrl()).placeholder(R.drawable.ic_no_avatar).into(binding.loginPage.userAvatar);
            binding.loginPage.loginUser.setVisibility(View.GONE);
            binding.loginPage.loginPageRoot.setVisibility(View.VISIBLE);

        } else {
            binding.loginPage.loginPageRoot.setVisibility(View.GONE);
            binding.loginPage.loginUser.setVisibility(View.VISIBLE);
        }


    }


    // TODO: 20.01.2023 Добавить удаление фоновой службы AutoBackupWorker
    void signOut() {
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            final Preference preference = PowerPreference.getFileByName(FIlE_NAME_PREFERENCE_BACKUP);
            binding.loginPage.loginUser.setVisibility(View.VISIBLE);
            binding.loginPage.loginPageRoot.setVisibility(View.GONE);
            preference.removeAsync(ARGUMENT_AUTO_BACKUP_CLOUD);
            preference.removeAsync(ARGUMENT_LAST_BACKUP_ID);
            preference.removeAsync(ARGUMENT_LAST_BACKUP_TIME);
            cloudCacheHelper.clean();
        });

    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        binding.trashActivityLayout.setOnClickListener(null);
        binding.loginPage.exitUser.setOnClickListener(null);
        binding.loginPage.loginUser.setOnClickListener(null);
        binding.backups.setOnClickListener(null);
        binding.aboutApp.setOnClickListener(null);
        binding.themeApp.setOnClickListener(null);
    }
}
