package com.pasich.mynotes.ui.view.dialogs.note;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;
import com.pasich.mynotes.base.dialog.BaseDialogBottomSheets;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.NoteBackground;
import com.pasich.mynotes.databinding.DialogNoteBackgroundBinding;
import com.pasich.mynotes.utils.adapters.BackgroundAdapter;
import com.pasich.mynotes.utils.backgrounds.BackgroundPresets;

import java.util.List;

/**
 * Діалог для вибору фону нотатки
 */
public class NoteBackgroundDialog extends BaseDialogBottomSheets {
    
    private final Note note;
    private final OnBackgroundSelectedListener listener;
    private DialogNoteBackgroundBinding binding;
    private BackgroundAdapter adapter;
    private boolean isDarkTheme;
    private int currentCategoryIndex = 0;
    
    public interface OnBackgroundSelectedListener {
        void onBackgroundSelected(NoteBackground background);
    }
    
    public NoteBackgroundDialog(Note note, OnBackgroundSelectedListener listener) {
        this.note = note;
        this.listener = listener;
    }
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        vibrateOpenDialog(true);
        setState((BottomSheetDialog) requireDialog());
        
        binding = DialogNoteBackgroundBinding.inflate(getLayoutInflater(), container, false);
        
        isDarkTheme = (getResources().getConfiguration().uiMode & 
                      Configuration.UI_MODE_NIGHT_MASK) ==
                      Configuration.UI_MODE_NIGHT_YES;
        
        setupTabs();
        setupRecyclerView();
        loadBackgrounds(0); // Завантажуємо кольори за замовчуванням
        initListeners(); // Ініціалізуємо слухачі
        
        return binding.getRoot();
    }
    
    private void setupTabs() {
        String[] categoryNames = BackgroundPresets.getCategoryNames();
        
        for (String categoryName : categoryNames) {
            TabLayout.Tab tab = binding.tabLayout.newTab();
            tab.setText(categoryName);
            binding.tabLayout.addTab(tab);
        }
        
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentCategoryIndex = tab.getPosition();
                loadBackgrounds(currentCategoryIndex);
            }
            
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }
    
    private void setupRecyclerView() {
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 4);
        binding.recyclerView.setLayoutManager(layoutManager);
        
        adapter = new BackgroundAdapter(background -> {
            if (listener != null) {
                listener.onBackgroundSelected(background);
            }
            dismiss();
        });
        
        binding.recyclerView.setAdapter(adapter);
    }
    
    private void loadBackgrounds(int categoryIndex) {
        List<List<NoteBackground>> allBackgrounds = BackgroundPresets.getAllBackgroundsByCategory(isDarkTheme);
        
        if (categoryIndex >= 0 && categoryIndex < allBackgrounds.size()) {
            List<NoteBackground> backgrounds = allBackgrounds.get(categoryIndex);
            
            // Встановлюємо поточний фон нотатки як вибраний
            NoteBackground currentBackground = note != null ? note.getBackground() : null;
            adapter.setBackgrounds(backgrounds, currentBackground);
        }
    }
    
    @Override
    public void setState(BottomSheetDialog dialog) {
        super.setState(dialog);
    }

    @Override
    public void initListeners() {
        // Listener для кнопки скасування
        if (binding != null && binding.cancelButton != null) {
            binding.cancelButton.setOnClickListener(v -> dismiss());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
