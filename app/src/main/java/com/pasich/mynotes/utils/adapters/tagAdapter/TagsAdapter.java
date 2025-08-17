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

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

public class TagsAdapter extends ListAdapter<Tag, TagsAdapter.ViewHolder> {

    private OnItemClickListenerTag mOnItemClickListener;
    private Tag mTagSelected;
    private boolean oneCheckedAllNotes = false;

    @Inject
    public TagsAdapter(@NonNull @Named("Tag") DiffUtil.ItemCallback<Tag> diffCallback) {
        super(diffCallback);
    }

    public void setOnItemClickListener(OnItemClickListenerTag onItemClickListener) {
        this.mOnItemClickListener = onItemClickListener;
    }


    public Tag getTagSelected() {
        return this.mTagSelected == null ? new Tag().create("allNotes") : this.mTagSelected;
    }


    public void setTagSelected(@Nullable Tag selected) {
        this.mTagSelected = selected;
    }


    @NonNull
    @Override
    public TagsAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder view = new ViewHolder(ItemTagBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));

        if (mOnItemClickListener != null) {
            view.itemView.setOnClickListener(v -> mOnItemClickListener.onClick(view.getAdapterPosition()));

            view.itemView.setOnLongClickListener(v -> {
                mOnItemClickListener.onLongClick(view.getAdapterPosition(), view.itemView);
                return false;
            });

        }
        return view;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Tag tag = getItem(position);
        holder.ItemBinding.setTag(tag);
        if (!oneCheckedAllNotes && tag.getSystemAction() == 2) {
            mTagSelected = tag.setSelectedReturn(true);
            oneCheckedAllNotes = true;
        }

        holder.ItemBinding.setCheckedTag(tag.getSelected());
    }


    @Override
    public void submitList(@Nullable List<Tag> list) {

        assert list != null;
        Collections.sort(list, (o1, o2) -> {

            int x1 = o1.getSystemAction();
            int x2 = o2.getSystemAction();


            if (o2.getSystemAction() == 1) x2 = x2 + 2;
            int sComp = Math.toIntExact(x2 - x1);

            if (sComp != 0) {
                return sComp;
            }

            return Math.toIntExact(o2.getId() - o1.getId());
        });
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
     * Метод который возвращет позицию отмеченой метки в list, по ее модели
     *
     * @return - позиция метки
     */
    public int getCheckedPosition(Tag tagSearch) {
        for (int i = 0; i < getCurrentList().size(); i++)
            if (getItem(i).getId() == tagSearch.getId()) return i;
        return 0;
    }


    /**
     * Метод который реализует выбор метки
     *
     * @param position - позация метки которую выбрали
     */
    public void chooseTag(int position) {
        notifyItemChanged(getCheckedPosition(getTagSelected().setSelectedReturn(false)), AppPayloads.PAYLOADS_TAG_EDIT);
        setTagSelected(getItem(position).setSelectedReturn(true));
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
