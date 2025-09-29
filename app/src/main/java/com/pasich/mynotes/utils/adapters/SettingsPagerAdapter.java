package com.pasich.mynotes.utils.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.pasich.mynotes.ui.view.fragment.InteractionSettingsFragment;
import com.pasich.mynotes.ui.view.fragment.InterfaceSettingsFragment;

public class SettingsPagerAdapter extends FragmentStateAdapter {

    public SettingsPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return switch (position) {
            case 1 -> new InteractionSettingsFragment();
            default -> new InterfaceSettingsFragment();
        };
    }

    @Override
    public int getItemCount() {
        return 2; // Два фрагменти: Інтерфейс та Взаємодія
    }
}