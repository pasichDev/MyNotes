package com.pasich.mynotes.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
    private OnProductClickListener listener;

    public DonationProductAdapter(OnProductClickListener listener) {
        this.listener = listener;
    }

    public void setProducts(List<DonationProduct> products) {
        this.products = products;
        notifyDataSetChanged();
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
        private ImageView iconImageView;
        private TextView titleTextView;
        private TextView descriptionTextView;
        private TextView priceTextView;

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

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onProductClick(product);
                }
            });
        }

        private String getLocalizedTitle(String productId) {
            switch (productId) {
                case "donate_seed_of_ideas":
                    return itemView.getContext().getString(R.string.donation_seed_title);
                case "donate_spark_of_inspiration":
                    return itemView.getContext().getString(R.string.donation_spark_title);
                case "donate_midnight_notebook":
                    return itemView.getContext().getString(R.string.donation_midnight_title);
                case "donate_wave_of_support":
                    return itemView.getContext().getString(R.string.donation_wave_title);
                case "donate_universe_of_inspiration":
                    return itemView.getContext().getString(R.string.donation_universe_title);
                default:
                    return "Unknown Product";
            }
        }

        private String getLocalizedDescription(String productId) {
            switch (productId) {
                case "donate_seed_of_ideas":
                    return itemView.getContext().getString(R.string.donation_seed_description);
                case "donate_spark_of_inspiration":
                    return itemView.getContext().getString(R.string.donation_spark_description);
                case "donate_midnight_notebook":
                    return itemView.getContext().getString(R.string.donation_midnight_description);
                case "donate_wave_of_support":
                    return itemView.getContext().getString(R.string.donation_wave_description);
                case "donate_universe_of_inspiration":
                    return itemView.getContext().getString(R.string.donation_universe_description);
                default:
                    return "Support the developer";
            }
        }

        private int getIconResource(String iconName) {
            switch (iconName) {
                case "ic_seed":
                    return R.drawable.ic_seed;
                case "ic_spark":
                    return R.drawable.ic_spark;
                case "ic_notebook":
                    return R.drawable.ic_notebook;
                case "ic_wave":
                    return R.drawable.ic_wave;
                case "ic_universe":
                    return R.drawable.ic_universe;
                default:
                    return R.drawable.ic_heart;
            }
        }
    }
}
