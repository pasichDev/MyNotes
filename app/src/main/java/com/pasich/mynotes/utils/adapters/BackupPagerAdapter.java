package com.pasich.mynotes.utils.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.pasich.mynotes.ui.contract.BackupContract;
import com.pasich.mynotes.ui.view.fragment.BackupExportFragment;
import com.pasich.mynotes.ui.view.fragment.ImportDataFragment;

public class BackupPagerAdapter extends FragmentStateAdapter {

    public BackupPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return switch (position) {
            case 1 -> ImportDataFragment.newInstance();
            default -> BackupExportFragment.newInstance();
        };
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}