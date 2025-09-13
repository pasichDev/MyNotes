package com.pasich.mynotes.utils.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.NoteBackground;
import com.pasich.mynotes.utils.backgrounds.BackgroundApplier;

import java.util.ArrayList;
import java.util.List;

/**
 * Адаптер для відображення списку фонів нотаток
 */
public class BackgroundAdapter extends RecyclerView.Adapter<BackgroundAdapter.BackgroundViewHolder> {
    
    private List<NoteBackground> backgrounds = new ArrayList<>();
    private NoteBackground selectedBackground;
    private final OnBackgroundClickListener listener;
    
    public interface OnBackgroundClickListener {
        void onBackgroundClick(NoteBackground background);
    }
    
    public BackgroundAdapter(OnBackgroundClickListener listener) {
        this.listener = listener;
    }
    
    public void setBackgrounds(List<NoteBackground> backgrounds, NoteBackground selectedBackground) {
        this.backgrounds = backgrounds != null ? backgrounds : new ArrayList<>();
        this.selectedBackground = selectedBackground;
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public BackgroundViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_background_selector, parent, false);
        return new BackgroundViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull BackgroundViewHolder holder, int position) {
        NoteBackground background = backgrounds.get(position);
        holder.bind(background, isSelected(background));
    }
    
    @Override
    public int getItemCount() {
        return backgrounds.size();
    }
    
    private boolean isSelected(NoteBackground background) {
        if (selectedBackground == null && background.getType() == NoteBackground.BackgroundType.DEFAULT) {
            return true;
        }
        return background.equals(selectedBackground);
    }
    
    class BackgroundViewHolder extends RecyclerView.ViewHolder {
        private final View backgroundPreview;
        private final ImageView selectionIndicator;
        
        public BackgroundViewHolder(@NonNull View itemView) {
            super(itemView);
            backgroundPreview = itemView.findViewById(R.id.backgroundPreview);
            selectionIndicator = itemView.findViewById(R.id.selectionIndicator);
            
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    NoteBackground clickedBackground = backgrounds.get(position);
                    selectedBackground = clickedBackground;
                    listener.onBackgroundClick(clickedBackground);
                    notifyDataSetChanged(); // Оновлюємо відображення вибору
                }
            });
        }
        
        public void bind(NoteBackground background, boolean isSelected) {
            // Застосовуємо фон до preview
            BackgroundApplier.applyBackground(backgroundPreview, background, itemView.getContext());
            
            // Показуємо/приховуємо індикатор вибору
            selectionIndicator.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            
            // Встановлюємо стан вибору для itemView
            itemView.setSelected(isSelected);
            
            // Додаємо особливе оформлення для стандартного фону
            if (background.getType() == NoteBackground.BackgroundType.DEFAULT) {
                // Можна додати спеціальне оформлення для стандартного фону
                backgroundPreview.setBackgroundResource(R.drawable.default_background_preview);
            }
        }
    }
}
