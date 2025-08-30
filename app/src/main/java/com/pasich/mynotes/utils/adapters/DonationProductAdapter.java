package com.pasich.mynotes.utils.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.DonationProduct;

import java.util.ArrayList;
import java.util.List;

public class DonationProductAdapter extends RecyclerView.Adapter<DonationProductAdapter.ViewHolder> {

    public interface OnProductClickListener {
        void onProductClick(DonationProduct product);
    }

    private List<DonationProduct> products = new ArrayList<>();
    private final OnProductClickListener listener;

    public DonationProductAdapter(OnProductClickListener listener) {
        this.listener = listener;
    }

    public void setProducts(List<DonationProduct> newProducts) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return products.size();
            }

            @Override
            public int getNewListSize() {
                return newProducts.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return products.get(oldItemPosition).getId().equals(newProducts.get(newItemPosition).getId());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                DonationProduct oldProduct = products.get(oldItemPosition);
                DonationProduct newProduct = newProducts.get(newItemPosition);
                return oldProduct.equals(newProduct);
            }
        });

        this.products = new ArrayList<>(newProducts);
        diffResult.dispatchUpdatesTo(this);
    }

    public void updatePurchasedProducts(List<String> purchasedProductIds) {
        setProducts(new ArrayList<>(products));
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_donation_product, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DonationProduct product = products.get(position);
        holder.bind(product, listener);
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView iconImageView;
        private final TextView titleTextView;
        private final TextView descriptionTextView;
        private final TextView priceTextView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            iconImageView = itemView.findViewById(R.id.product_icon);
            titleTextView = itemView.findViewById(R.id.product_title);
            descriptionTextView = itemView.findViewById(R.id.product_description);
            priceTextView = itemView.findViewById(R.id.product_price);
        }

        public void bind(DonationProduct product, OnProductClickListener listener) {
            titleTextView.setText(getLocalizedTitle(product.getId()));
            descriptionTextView.setText(getLocalizedDescription(product.getId()));
            priceTextView.setText(product.getPrice());
            
            // Set icon based on product type
            int iconRes = getIconResource(product.getIconResource());
            iconImageView.setImageResource(iconRes);

            // Apply purchased state styling
            if (product.isPurchased()) {
                // Make item look purchased/disabled
                itemView.setAlpha(0.5f);
                priceTextView.setText(itemView.getContext().getString(R.string.purchased));
                itemView.setOnClickListener(null); // Disable click
            } else {
                // Normal state
                itemView.setAlpha(1.0f);
                itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onProductClick(product);
                    }
                });
            }
        }

        private String getLocalizedTitle(String productId) {
            return switch (productId) {
                case "donate_seed_of_ideas" ->
                        itemView.getContext().getString(R.string.donation_seed_title);
                case "donate_spark_of_inspiration" ->
                        itemView.getContext().getString(R.string.donation_spark_title);
                case "donate_midnight_notebook" ->
                        itemView.getContext().getString(R.string.donation_midnight_title);
                case "donate_wave_of_support" ->
                        itemView.getContext().getString(R.string.donation_wave_title);
                case "donate_universe_of_inspiration" ->
                        itemView.getContext().getString(R.string.donation_universe_title);
                default -> "Unknown Product";
            };
        }

        private String getLocalizedDescription(String productId) {
            return switch (productId) {
                case "donate_seed_of_ideas" ->
                        itemView.getContext().getString(R.string.donation_seed_description);
                case "donate_spark_of_inspiration" ->
                        itemView.getContext().getString(R.string.donation_spark_description);
                case "donate_midnight_notebook" ->
                        itemView.getContext().getString(R.string.donation_midnight_description);
                case "donate_wave_of_support" ->
                        itemView.getContext().getString(R.string.donation_wave_description);
                case "donate_universe_of_inspiration" ->
                        itemView.getContext().getString(R.string.donation_universe_description);
                default -> "Support the developer";
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
