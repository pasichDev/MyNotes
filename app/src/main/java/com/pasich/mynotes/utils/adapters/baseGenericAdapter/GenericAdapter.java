package com.pasich.mynotes.utils.adapters.baseGenericAdapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

public class GenericAdapter<T, VM extends ViewDataBinding>
        extends ListAdapter<T, GenericAdapter<T, VM>.RecyclerViewHolder> {

    private final int layoutId;
    private final GenericAdapterCallback<VM, T> bindingInterface;
    private OnItemClickListener<T> mOnItemClickListener;

    public GenericAdapter(
            @NonNull DiffUtil.ItemCallback<T> diffCallback,
            int layoutId,
            GenericAdapterCallback<VM, T> bindingInterface
    ) {
        super(diffCallback);
        this.layoutId = layoutId;
        this.bindingInterface = bindingInterface;
    }

    public void setOnItemClickListener(OnItemClickListener<T> listener) {
        this.mOnItemClickListener = listener;
    }

    @NonNull
    @Override
    public RecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        VM binding = DataBindingUtil.inflate(
                LayoutInflater.from(parent.getContext()),
                layoutId,
                parent,
                false
        );

        return new RecyclerViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerViewHolder holder, int position) {
        T item = getItem(position);

        holder.bindData(item);

        holder.itemView.setTransitionName(item.toString());

        if (mOnItemClickListener != null) {
            holder.itemView.setOnClickListener(v ->
                    mOnItemClickListener.onClick(holder.getBindingAdapterPosition(), item)
            );

            holder.itemView.setOnLongClickListener(v -> {
                mOnItemClickListener.onLongClick(holder.getBindingAdapterPosition(), item);
                return true;
            });
        }
    }

    public class RecyclerViewHolder extends RecyclerView.ViewHolder {

        private final VM binding;

        public RecyclerViewHolder(VM binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bindData(T model) {
            bindingInterface.bindData(binding, model);
            binding.executePendingBindings();
        }
    }
}
