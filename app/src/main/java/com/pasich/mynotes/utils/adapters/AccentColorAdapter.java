package com.pasich.mynotes.utils.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Theme;
import java.util.ArrayList;

public class AccentColorAdapter extends RecyclerView.Adapter<AccentColorAdapter.VH> {

    private final Context ctx;
    private final ArrayList<Theme> themes;
    private final int[] colors;
    private int selectedId;
    private final Listener listener;

    public interface Listener {
        void onSelect(Theme theme);
    }

    public AccentColorAdapter(
            Context ctx, ArrayList<Theme> themes, int[] colors, int selectedId, Listener listener) {
        this.ctx = ctx;
        this.themes = themes;
        this.colors = colors;
        this.selectedId = selectedId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_accent_circle, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {

        int color = ContextCompat.getColor(ctx, colors[position]);
        h.colorCircle.getBackground().setTint(color);

        boolean isSelected = themes.get(position).getId() == selectedId;

        h.checkmark.setVisibility(isSelected ? View.VISIBLE : View.GONE);

        h.itemView.setOnClickListener(
                v -> {
                    selectedId = themes.get(position).getId();
                    listener.onSelect(themes.get(position));
                    notifyDataSetChanged();
                });
    }

    @Override
    public int getItemCount() {
        return Math.min(themes.size(), colors.length);
    }

    static class VH extends RecyclerView.ViewHolder {
        View colorCircle;
        ImageView checkmark;

        public VH(@NonNull View itemView) {
            super(itemView);
            colorCircle = itemView.findViewById(R.id.colorCircle);
            checkmark = itemView.findViewById(R.id.checkmark);
        }
    }
}
