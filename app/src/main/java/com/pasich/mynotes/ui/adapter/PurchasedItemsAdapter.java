package com.pasich.mynotes.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.PurchasedItem;

import java.util.ArrayList;
import java.util.List;

public class PurchasedItemsAdapter extends RecyclerView.Adapter<PurchasedItemsAdapter.ViewHolder> {

    private List<PurchasedItem> items = new ArrayList<>();

    public void setItems(List<PurchasedItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_purchased_collection, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PurchasedItem item = items.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView iconImageView;
        private final TextView titleTextView;
        private final TextView countBadge;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            iconImageView = itemView.findViewById(R.id.item_icon);
            titleTextView = itemView.findViewById(R.id.item_title);
            countBadge = itemView.findViewById(R.id.item_count_badge);
        }

        public void bind(PurchasedItem item) {
            titleTextView.setText(getLocalizedTitle(item.getProductId()));
            
            // Set icon based on product type
            int iconRes = getIconResource(item.getIconResource());
            iconImageView.setImageResource(iconRes);

            // Show/hide count badge
            if (item.getCount() > 1) {
                countBadge.setVisibility(View.VISIBLE);
                countBadge.setText(String.valueOf(item.getCount()));
            } else {
                countBadge.setVisibility(View.GONE);
            }
        }

        private String getLocalizedTitle(String productId) {
            return switch (productId) {
                case "donate_seed_of_ideas" ->
                        itemView.getContext().getString(R.string.donation_seed_short);
                case "donate_spark_of_inspiration" ->
                        itemView.getContext().getString(R.string.donation_spark_short);
                case "donate_midnight_notebook" ->
                        itemView.getContext().getString(R.string.donation_midnight_short);
                case "donate_wave_of_support" ->
                        itemView.getContext().getString(R.string.donation_wave_short);
                case "donate_universe_of_inspiration" ->
                        itemView.getContext().getString(R.string.donation_universe_short);
                default -> itemView.getContext().getString(R.string.donation_legacy_friend);
            };
        }

        private int getIconResource(String iconName) {
            return switch (iconName) {
                case "ic_seed" -> R.drawable.ic_seed;
                case "ic_spark" -> R.drawable.ic_spark;
                case "ic_notebook" -> R.drawable.ic_notebook;
                case "ic_wave" -> R.drawable.ic_wave;
                case "ic_universe" -> R.drawable.ic_universe;
                default -> R.drawable.ic_heart;
            };
        }
    }
}
