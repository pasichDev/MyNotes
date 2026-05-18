package com.pasich.mynotes.ui.view.fragment.settings;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.pasich.mynotes.cache.AppPreferencesCache;
import com.pasich.mynotes.cache.NotificationPreferencesCache;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.databinding.FragmentMediaSettingsBinding;
import com.pasich.mynotes.extendedEditor.attach.AttachmentStorage;
import com.pasich.mynotes.ui.controllers.mainActivity.RedrawThemeController;
import com.pasich.mynotes.utils.notification.NotificationChannelManager;
import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.schedulers.Schedulers;
import javax.inject.Inject;

@AndroidEntryPoint
public class MediaSettingsFragment extends Fragment {

    @Inject ThemePreferencesCache themePreferencesCache;

    @Inject AppPreferencesCache appPreferencesCache;

    @Inject NotificationPreferencesCache notificationPreferencesCache;

    private FragmentMediaSettingsBinding binding;
    private AudioManager audioManager;

    private final ActivityResultLauncher<Intent> ringtoneLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != android.app.Activity.RESULT_OK
                                || result.getData() == null) return;
                        Uri newUri =
                                result.getData()
                                        .getParcelableExtra(
                                                RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                        if (getContext() == null) return;
                        NotificationChannelManager.changeSound(
                                requireContext(), notificationPreferencesCache, newUri);
                        updateSoundNameText(newUri);
                    });

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentMediaSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        audioManager =
                (AudioManager)
                        requireContext().getSystemService(android.content.Context.AUDIO_SERVICE);
        initViews();
        initListeners();
        updateMemoryUsage();
    }

    private void initViews() {
        binding.imgOptSwitch.setChecked(appPreferencesCache.getImageOpt());
        binding.setExtendedDetailsVisible(false);

        // Sound: show current sound name
        String savedUri = notificationPreferencesCache.getSoundUri();
        Uri currentUri =
                (savedUri != null && !savedUri.isEmpty())
                        ? Uri.parse(savedUri)
                        : android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
        updateSoundNameText(currentUri);

        // Volume SeekBar
        if (audioManager != null) {
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
            int cur = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
            binding.volumeSeekBar.setMax(max);
            binding.volumeSeekBar.setProgress(cur);
        }
    }

    private void initListeners() {
        binding.imgOptSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> appPreferencesCache.setImageOpt(isChecked));

        binding.soundRow.setOnClickListener(
                v -> {
                    String savedUri = notificationPreferencesCache.getSoundUri();
                    Uri currentUri =
                            (savedUri != null && !savedUri.isEmpty())
                                    ? Uri.parse(savedUri)
                                    : android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;

                    Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                    intent.putExtra(
                            RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri);
                    ringtoneLauncher.launch(intent);
                });

        binding.volumeSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser && audioManager != null) {
                            audioManager.setStreamVolume(
                                    AudioManager.STREAM_NOTIFICATION, progress, 0);
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                });
    }

    private void updateSoundNameText(Uri uri) {
        if (binding == null || getContext() == null) return;
        String name;
        try {
            android.media.Ringtone ringtone = RingtoneManager.getRingtone(requireContext(), uri);
            name = (ringtone != null) ? ringtone.getTitle(requireContext()) : "";
        } catch (Exception e) {
            name = "";
        }
        binding.soundNameText.setText(name);
    }

    public void updateThemeColors() {
        if (getContext() == null) return;
        RedrawThemeController.styleCardBlock(
                binding.imgOpt,
                null,
                binding.imgOptDescription,
                binding.imgOptSwitch,
                requireContext());
        RedrawThemeController.styleCardBlock(
                binding.memory, binding.memoryTitle, binding.memoryValue, null, requireContext());
        RedrawThemeController.styleCardBlock(
                binding.notificationSettings,
                binding.notificationSectionTitle,
                null,
                null,
                requireContext());
    }

    @SuppressLint("DefaultLocale")
    private void updateMemoryUsage() {
        if (getContext() == null) return;
        Schedulers.io()
                .scheduleDirect(
                        () -> {
                            long usedBytes =
                                    AttachmentStorage.getTotalAttachmentsSize(getContext());
                            float usedMB = usedBytes / 1024f / 1024f;
                            new Handler(Looper.getMainLooper())
                                    .post(
                                            () -> {
                                                if (binding != null) {
                                                    binding.memoryValue.setText(
                                                            String.format("%.1f MB", usedMB));
                                                }
                                            });
                        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
