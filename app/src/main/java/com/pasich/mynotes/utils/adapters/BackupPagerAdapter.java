package com.pasich.mynotes.utils.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.pasich.mynotes.ui.contract.BackupContract;
import com.pasich.mynotes.ui.view.fragment.BackupExportFragment;
import com.pasich.mynotes.ui.view.fragment.ImportDataFragment;

public class BackupPagerAdapter extends FragmentStateAdapter {

    private final BackupContract.presenter presenter;

    public BackupPagerAdapter(@NonNull FragmentActivity fragmentActivity, BackupContract.presenter presenter) {
        super(fragmentActivity);
        this.presenter = presenter;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return BackupExportFragment.newInstance(presenter);
            case 1:
                return ImportDataFragment.newInstance();
            default:
                return BackupExportFragment.newInstance(presenter);
        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}