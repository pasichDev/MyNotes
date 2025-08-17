package com.pasich.mynotes.utils.adapters.baseGenericAdapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;


public class GenericAdapter<T, VM extends ViewDataBinding> extends ListAdapter<T, GenericAdapter.RecyclerViewHolder> {
    private final int layoutId;
    private final GenericAdapterCallback<VM, T> bindingInterface;
    public OnItemClickListener<T> mOnItemClickListener;

    protected GenericAdapter(@NonNull DiffUtil.ItemCallback<T> diffCallback, int layoutId, GenericAdapterCallback<VM, T> bindingInterface) {
        super(diffCallback);
        this.layoutId = layoutId;
        this.bindingInterface = bindingInterface;
    }


    public void setOnItemClickListener(OnItemClickListener<T> onItemClickListener) {
        this.mOnItemClickListener = onItemClickListener;
    }

    @NonNull
    @Override
    public RecyclerViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        RecyclerViewHolder view = new RecyclerViewHolder(LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false));
        if (mOnItemClickListener != null) {
            view.itemView.setOnClickListener(v -> mOnItemClickListener.onClick(view.getAdapterPosition(),
                    getCurrentList().get(view.getAdapterPosition())));
            view.itemView.setOnLongClickListener(v -> {
                mOnItemClickListener.onLongClick(view.getAdapterPosition(), getCurrentList().get(view.getAdapterPosition()));
                return false;
            });
        }
        return view;
    }

    @Override
    public void onBindViewHolder(GenericAdapter.RecyclerViewHolder holder, int position) {
        holder.itemView.setTransitionName(getCurrentList().get(position).toString());
        holder.bindData(getCurrentList().get(position));
    }

    @Override
    public int getItemCount() {
        return getCurrentList().size();
    }

    public class RecyclerViewHolder extends RecyclerView.ViewHolder {

        VM binding;

        public RecyclerViewHolder(View view) {
            super(view);
            binding = DataBindingUtil.bind(view);

        }

        public void bindData(T model) {
            bindingInterface.bindData(binding, model);
        }

    }


}