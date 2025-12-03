package com.pasich.mynotes.utils.adapters.tagAdapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.databinding.ItemTagBinding;
import com.pasich.mynotes.utils.recycler.TagPayloads;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

public class TagsAdapter extends ListAdapter<Tag, TagsAdapter.ViewHolder> {

    private OnItemClickListenerTag clickListener;

    @Inject
    public TagsAdapter(@NonNull @Named("Tag") DiffUtil.ItemCallback<Tag> diffCallback) {
        super(diffCallback);
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getId();
    }

    public void setOnItemClickListener(OnItemClickListenerTag listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTagBinding binding = ItemTagBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );

        ViewHolder holder = new ViewHolder(binding);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && clickListener != null) {
                clickListener.onClick(pos);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && clickListener != null) {
                clickListener.onLongClick(pos, holder.itemView);
            }
            return true;
        });

        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Tag tag = getItem(position);
        holder.bind(tag);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder,
                                 int position,
                                 @NonNull List<Object> payloads) {

        if (!payloads.isEmpty()) {
            Tag tag = getItem(position);

            for (Object payload : payloads) {
                if (payload instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> list = (List<String>) payload;

                    for (String type : list) {
                        switch (type) {
                            case TagPayloads.SELECTED:
                                holder.binding.setCheckedTag(tag.getSelected());
                                break;

                            case TagPayloads.NAME:
                            case TagPayloads.VISIBILITY:
                            case TagPayloads.SYSTEM:
                                holder.bind(tag);
                                break;
                        }
                    }
                }
            }

            return;
        }

        super.onBindViewHolder(holder, position, payloads);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        final ItemTagBinding binding;

        ViewHolder(ItemTagBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Tag tag) {
            binding.setTag(tag);
            binding.setCheckedTag(tag.getSelected());
            binding.executePendingBindings();
        }
    }
}
