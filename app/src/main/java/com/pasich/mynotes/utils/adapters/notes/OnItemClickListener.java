package com.pasich.mynotes.utils.adapters.notes;

public interface OnItemClickListener<T> {
    void onClick(int position, T model);

    default void onLongClick(int position, T model) {}
}
