package com.pasich.mynotes.utils.adapters;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.lib.LibItem;
import com.pasich.mynotes.data.model.lib.LibSection;

import java.util.List;

public class LibsSectionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 1;
    private static final int TYPE_ITEM = 2;

    private final List<LibSection> sections;

    public LibsSectionAdapter(List<LibSection> sections) {
        this.sections = sections;
    }

    @Override
    public int getItemViewType(int position) {
        int count = 0;

        for (LibSection s : sections) {
            if (position == count) return TYPE_HEADER;
            count++;

            int size = s.items().size();
            if (position < count + size) return TYPE_ITEM;

            count += size;
        }
        return TYPE_ITEM;
    }

    @Override
    public int getItemCount() {
        int total = 0;
        for (LibSection s : sections) {
            total += 1;
            total += s.items().size();
        }
        return total;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {

        if (type == TYPE_HEADER) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_lib_header, parent, false);
            return new HeaderHolder(v);
        } else {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_lib, parent, false);
            return new ItemHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vh, int position) {

        int index = 0;

        for (LibSection section : sections) {

            if (position == index) {
                ((HeaderHolder) vh).bind(section.title());
                return;
            }
            index++;

            for (LibItem item : section.items()) {
                if (position == index) {
                    ((ItemHolder) vh).bind(item);
                    return;
                }
                index++;
            }
        }
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        TextView title;

        HeaderHolder(View v) {
            super(v);
            title = v.findViewById(R.id.sectionTitle);
        }

        void bind(String t) {
            title.setText(t);
        }
    }

    static class ItemHolder extends RecyclerView.ViewHolder {

        TextView id, version;

        ItemHolder(View v) {
            super(v);
            id = v.findViewById(R.id.libId);
            version = v.findViewById(R.id.libVersion);
        }

        @SuppressLint("SetTextI18n")
        void bind(LibItem it) {
            id.setText(it.id());
            version.setText("Version: " + (it.version() != null ? it.version() : "-"));
        }
    }
}
