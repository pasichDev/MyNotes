package com.pasich.mynotes.utils.adapters.tagAdapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.databinding.ItemTagBinding;
import com.pasich.mynotes.utils.constants.AppPayloads;
import com.pasich.mynotes.utils.managers.SystemTagsManager;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

public class TagsAdapter extends ListAdapter<Tag, TagsAdapter.ViewHolder> {

    private OnItemClickListenerTag mOnItemClickListener;
    private Tag mTagSelected;
    private boolean isInitialized = false;

    @Inject
    public TagsAdapter(@NonNull @Named("Tag") DiffUtil.ItemCallback<Tag> diffCallback) {
        super(diffCallback);
    }

    public void setOnItemClickListener(OnItemClickListenerTag onItemClickListener) {
        this.mOnItemClickListener = onItemClickListener;
    }


    public Tag getTagSelected() {
        return this.mTagSelected;
    }


    public void setTagSelected(@Nullable Tag selected) {
        this.mTagSelected = selected;
    }


    @NonNull
    @Override
    public TagsAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder view = new ViewHolder(ItemTagBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));

        if (mOnItemClickListener != null) {
            view.itemView.setOnClickListener(v -> {
                int position = view.getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    mOnItemClickListener.onClick(position);
                }
            });

            view.itemView.setOnLongClickListener(v -> {
                int position = view.getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    Tag tag = getItem(position);
                    if (!SystemTagsManager.isSystemTag(tag) || SystemTagsManager.isAllNotesTag(tag)) {
                        mOnItemClickListener.onLongClick(position, view.itemView);
                    }
                }
                return true;
            });
        }

        return view;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Tag tag = getItem(position);
        holder.ItemBinding.setTag(tag);
        holder.ItemBinding.setCheckedTag(tag.getSelected());
    }

    @Override
    public void submitList(@Nullable List<Tag> list) {
        if (list == null) return;


        // Далі робиш свою логіку з вибором AllNotes при першій ініціалізації
        if (!isInitialized) {
            boolean hasSelectedTag = false;
            Tag allNotesTag = null;

            for (Tag tag : list) {
                if (tag.getSelected() && !SystemTagsManager.isAllNotesTag(tag) && !SystemTagsManager.isChangeLogTag(tag)) {
                    hasSelectedTag = true;
                    mTagSelected = tag;
                }
                if (SystemTagsManager.isAllNotesTag(tag)) {
                    allNotesTag = tag;
                }
            }

            if (!hasSelectedTag && allNotesTag != null) {
                mTagSelected = allNotesTag.setSelectedReturn(true);
            }

            isInitialized = true;
        }

        super.submitList(list);
    }


    @Override
    public void onBindViewHolder(@NonNull TagsAdapter.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
        } else {
            if (payloads.contains(AppPayloads.PAYLOADS_TAG_EDIT)) {
                holder.ItemBinding.setCheckedTag(getItem(position).getSelected());
            }
        }
    }

    /**
     * Метод который возвращет позицию метки по ее названию
     *
     * @return - позиция метки
     */
    public int getTagForName(String nameTagSearch) {
        for (int i = 0; i < getCurrentList().size(); i++)
            if (getItem(i).getNameTag().equals(nameTagSearch)) return i;
        return 0;
    }


    /**
     * Метод який реалізує вибір тегу з логікою взаємовиключення
     *
     * @param position - позація метки которую выбрали
     */
    public void chooseTag(int position) {
        Tag selectedTag = getItem(position);

        // Якщо це тег change або addTag - не дозволяємо їх вибирати
        if (SystemTagsManager.isChangeLogTag(selectedTag) || SystemTagsManager.isAddTag(selectedTag)) {
            return;
        }

        // Знімаємо вибір з усіх тегів (включаючи "Всі нотатки"), крім changelog
        for (int i = 0; i < getCurrentList().size(); i++) {
            Tag tag = getItem(i);
            if (tag.getSelected() && i != position && !SystemTagsManager.isChangeLogTag(tag)) {
                tag.setSelectedReturn(false);
                notifyItemChanged(i, AppPayloads.PAYLOADS_TAG_EDIT);
            }
        }

        // Встановлюємо новий вибраний тег
        setTagSelected(selectedTag.setSelectedReturn(true));
        notifyItemChanged(position, AppPayloads.PAYLOADS_TAG_EDIT);
    }


    public static class ViewHolder extends RecyclerView.ViewHolder {
        ItemTagBinding ItemBinding;

        ViewHolder(ItemTagBinding binding) {
            super(binding.getRoot());
            ItemBinding = binding;
        }
    }


}
