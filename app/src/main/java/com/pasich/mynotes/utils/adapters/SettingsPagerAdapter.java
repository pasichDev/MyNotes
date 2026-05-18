package com.pasich.mynotes.utils.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.pasich.mynotes.ui.view.fragment.settings.InteractionSettingsFragment;
import com.pasich.mynotes.ui.view.fragment.settings.InterfaceSettingsFragment;
import com.pasich.mynotes.ui.view.fragment.settings.MediaSettingsFragment;

public class SettingsPagerAdapter extends FragmentStateAdapter {

    public SettingsPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return switch (position) {
            case 2 -> new MediaSettingsFragment();
            case 1 -> new InteractionSettingsFragment();
            default -> new InterfaceSettingsFragment();
        };
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
