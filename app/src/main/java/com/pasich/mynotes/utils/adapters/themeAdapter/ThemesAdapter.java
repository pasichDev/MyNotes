package com.pasich.mynotes.utils.adapters.themeAdapter;


import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.pasich.mynotes.data.model.Theme;
import com.pasich.mynotes.databinding.ItemThemeBinding;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;

import java.util.ArrayList;
import java.util.List;

public class ThemesAdapter extends RecyclerView.Adapter<ThemesAdapter.ViewHolder> {

    private final ArrayList<Theme> themes;
    private final int PAYLOAD_SET_SELECTED = 44;
    private SelectThemesListener selectThemesListener;
    private Theme mSelectTheme;


    public ThemesAdapter(ArrayList<Theme> list, int idSelectThemeUser) {
        this.themes = list;
        mSelectTheme = getThemeToId(idSelectThemeUser).setCheckReturn(true);
    }


    public ArrayList<Theme> getThemes() {
        return themes;
    }

    public void setSelectLabelListener(SelectThemesListener selectLabelListener) {
        this.selectThemesListener = selectLabelListener;
    }


    public Theme getSelectTheme() {
        return this.mSelectTheme == null ? PreferencesConfig.Theme_DEFAULT : this.mSelectTheme;
    }


    public void setThemeSelected(@Nullable Theme labelSelect) {
        this.mSelectTheme = labelSelect;
    }

    public void selectTheme(int position) {
        notifyItemChanged(getCheckedPosition(getSelectTheme().setCheckReturn(false)), PAYLOAD_SET_SELECTED);
        setThemeSelected(themes.get(position).setCheckReturn(true));
        notifyItemChanged(position, PAYLOAD_SET_SELECTED);
    }

    public int getCheckedPosition(Theme theme) {
        for (int i = 0; i < getThemes().size(); i++)
            if (themes.get(i).getId() == theme.getId()) return i;
        return 0;
    }

    public Theme getThemeToId(int mId) {
        for (Theme mTheme : getThemes()) {
            if (mTheme.getId() == mId) return mTheme;
        }
        return PreferencesConfig.Theme_DEFAULT;
    }

    @NonNull
    @Override
    public ThemesAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder view = new ThemesAdapter.ViewHolder(ItemThemeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        if (selectThemesListener != null) {
            view.itemView.setOnClickListener(v -> selectThemesListener.onSelect(view.getAdapterPosition()));

        }
        return view;
    }


    @Override
    public void onBindViewHolder(@NonNull ThemesAdapter.ViewHolder holder, int position) {
        holder.itemThemeBinding.setTheme(getThemes().get(position));
    }


    @Override
    public void onBindViewHolder(@NonNull ThemesAdapter.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
        } else {
            if (payloads.contains(PAYLOAD_SET_SELECTED)) {
                holder.itemThemeBinding.setTheme(getThemes().get(position));
            }
        }
    }

    @Override
    public int getItemCount() {
        return themes.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ItemThemeBinding itemThemeBinding;

        public ViewHolder(@NonNull ItemThemeBinding binding) {
            super(binding.getRoot());
            itemThemeBinding = binding;
        }
    }
}
