package com.pasich.mynotes.utils.adapters.labelAdapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.RecyclerView;

import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Label;
import com.pasich.mynotes.utils.constants.AppPayloads;

import java.util.ArrayList;
import java.util.List;

public class LabelAdapter extends RecyclerView.Adapter<LabelAdapter.ViewHolder> {

    private final ArrayList<Label> labels;
    private final Context context;
    private final Label DEFAULT_LABEL = new Label(R.mipmap.ic_launcher_note);
    private SelectLabelListener mSelectLabelListener;
    private Label mSelectLabel;
    private boolean oneCheckedAll = false;


    public LabelAdapter(Context context, ArrayList<Label> list) {
        this.labels = list;
        this.context = context;
    }


    public void setSelectLabelListener(SelectLabelListener selectLabelListener) {
        this.mSelectLabelListener = selectLabelListener;
    }


    public Label getSelectLabel() {
        return this.mSelectLabel == null ? DEFAULT_LABEL : this.mSelectLabel;
    }


    public void setLabelSelected(@Nullable Label labelSelect) {
        this.mSelectLabel = labelSelect;
    }

    public void selectLabel(int position) {
        notifyItemChanged(getCheckedPosition(getSelectLabel().setCheckReturn(false)), AppPayloads.PAYLOADS_LABEL_CHECK);
        setLabelSelected(labels.get(position).setCheckReturn(true));
        notifyItemChanged(position, AppPayloads.PAYLOADS_LABEL_CHECK);
    }

    public int getCheckedPosition(Label label) {
        for (int i = 0; i < labels.size(); i++)
            if (labels.get(i).getImage() == label.getImage()) return i;
        return 0;
    }

    @NonNull
    @Override
    public LabelAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder view = new LabelAdapter.ViewHolder(LayoutInflater.from(context).inflate(R.layout.item_label, parent, false));
        if (mSelectLabelListener != null) {
            view.itemView.setOnClickListener(v -> mSelectLabelListener.onSelect(view.getAdapterPosition()));

        }

        return view;
    }


    @Override
    public void onBindViewHolder(@NonNull LabelAdapter.ViewHolder holder, int position) {
        Label label = labels.get(position);
        if (!oneCheckedAll && label.getImage() == R.mipmap.ic_launcher_note) {
            mSelectLabel = label.setCheckReturn(true);
            oneCheckedAll = true;
        }
        holder.images.setImageResource(label.getImage());
        setCheckView(holder, label);
    }


    @Override
    public void onBindViewHolder(@NonNull LabelAdapter.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
        } else {
            if (payloads.contains(AppPayloads.PAYLOADS_LABEL_CHECK))
                setCheckView(holder, labels.get(position));
        }
    }


    private void setCheckView(LabelAdapter.ViewHolder holder, Label label) {
        if (label.isCheck()) {
            holder.item_label.setBackground(AppCompatResources.getDrawable(context, R.drawable.item_label_check));
        } else {
            holder.item_label.setBackground(AppCompatResources.getDrawable(context, R.drawable.item_label_uncheck));
        }
    }


    @Override
    public int getItemCount() {
        return labels.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView images;
        View item_label;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            images = itemView.findViewById(R.id.imageLabel);
            item_label = itemView.findViewById(R.id.itemLabel);
        }
    }
}
