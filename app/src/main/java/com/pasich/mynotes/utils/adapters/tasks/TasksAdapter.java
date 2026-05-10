package com.pasich.mynotes.utils.adapters.tasks;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.data.model.TaskCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TasksAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ACTIVE = 0;
    private static final int TYPE_HEADER = 1;
    private static final int TYPE_COMPLETED = 2;
    private static final int TYPE_ADD_FOOTER = 3;

    private static final String SENTINEL_HEADER = "HEADER";
    private static final String SENTINEL_ADD = "ADD";

    public interface OnToggleListener { void onToggle(Task task); }
    public interface OnDeleteListener { void onDelete(Task task); }
    public interface OnEditListener   { void onEdit(Task task); }
    public interface OnAddListener    { void onAdd(); }
    public interface OnStartDragListener { void onStartDrag(RecyclerView.ViewHolder holder); }
    public interface OnReminderListener { void onReminder(Task task); }

    private List<Task> activeTasks = new ArrayList<>();
    private List<Task> completedTasks = new ArrayList<>();
    private boolean completedExpanded = false;
    private final Map<Integer, TaskCategory> categoryMap = new HashMap<>();

    private final OnToggleListener toggleListener;
    private final OnDeleteListener deleteListener;
    private final OnEditListener editListener;
    private final OnAddListener addListener;
    private final OnStartDragListener dragListener;
    private final OnReminderListener reminderListener;

    private List<Object> displayList = new ArrayList<>();

    public TasksAdapter(OnToggleListener toggle, OnDeleteListener delete,
                        OnEditListener edit, OnAddListener add,
                        OnStartDragListener drag, OnReminderListener reminder) {
        this.toggleListener = toggle;
        this.deleteListener = delete;
        this.editListener = edit;
        this.addListener = add;
        this.dragListener = drag;
        this.reminderListener = reminder;
    }

    public void setCategories(List<TaskCategory> categories) {
        categoryMap.clear();
        if (categories != null) {
            for (TaskCategory cat : categories) categoryMap.put(cat.getId(), cat);
        }
        notifyDataSetChanged();
    }

    public void submitList(List<Task> active, List<Task> completed) {
        this.activeTasks = active != null ? new ArrayList<>(active) : new ArrayList<>();
        this.completedTasks = completed != null ? new ArrayList<>(completed) : new ArrayList<>();
        rebuildDisplayList();
    }

    private void rebuildDisplayList() {
        List<Object> list = new ArrayList<>();
        list.addAll(activeTasks);
        list.add(SENTINEL_ADD);
        if (!completedTasks.isEmpty()) {
            list.add(SENTINEL_HEADER);
            if (completedExpanded) {
                list.addAll(completedTasks);
            }
        }
        displayList = list;
        notifyDataSetChanged();
    }

    public boolean moveItem(int fromPos, int toPos) {
        if (!isActiveTask(fromPos) || !isActiveTask(toPos)) return false;
        Collections.swap(activeTasks, fromPos, toPos);
        Collections.swap(displayList, fromPos, toPos);
        notifyItemMoved(fromPos, toPos);
        return true;
    }

    public List<Task> getActiveTasks() {
        return activeTasks;
    }

    public Task getTaskAt(int position) {
        if (position < 0 || position >= displayList.size()) return null;
        Object item = displayList.get(position);
        return (item instanceof Task) ? (Task) item : null;
    }

    public boolean isActiveTask(int position) {
        if (position < 0 || position >= displayList.size()) return false;
        Object item = displayList.get(position);
        if (!(item instanceof Task)) return false;
        return !((Task) item).isDone();
    }

    @Override
    public int getItemViewType(int position) {
        Object item = displayList.get(position);
        if (SENTINEL_HEADER.equals(item)) return TYPE_HEADER;
        if (SENTINEL_ADD.equals(item))    return TYPE_ADD_FOOTER;
        Task t = (Task) item;
        return t.isDone() ? TYPE_COMPLETED : TYPE_ACTIVE;
    }

    @Override
    public int getItemCount() { return displayList.size(); }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inf.inflate(R.layout.item_task_header, parent, false));
        }
        if (viewType == TYPE_ADD_FOOTER) {
            return new AddFooterHolder(inf.inflate(R.layout.item_task_add_footer, parent, false));
        }
        return new TaskHolder(inf.inflate(R.layout.item_task, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).bind(completedTasks.size(), completedExpanded, () -> {
                completedExpanded = !completedExpanded;
                rebuildDisplayList();
            });
        } else if (holder instanceof AddFooterHolder) {
            ((AddFooterHolder) holder).bind(addListener);
        } else {
            Task task = (Task) displayList.get(position);
            ((TaskHolder) holder).bind(task, toggleListener, editListener, dragListener, reminderListener);
        }
    }

    class TaskHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        TextView title;
        TextView description;
        TextView categoryLabel;
        ImageView bellIcon;
        ImageView dragHandle;

        TaskHolder(View v) {
            super(v);
            checkBox = v.findViewById(R.id.task_checkbox);
            title = v.findViewById(R.id.task_title);
            description = v.findViewById(R.id.task_description);
            categoryLabel = v.findViewById(R.id.task_category_label);
            bellIcon = v.findViewById(R.id.task_bell);
            dragHandle = v.findViewById(R.id.task_drag_handle);
        }

        @SuppressLint("ClickableViewAccessibility")
        void bind(Task task, OnToggleListener toggle, OnEditListener edit,
                  OnStartDragListener drag, OnReminderListener reminder) {
            checkBox.setOnCheckedChangeListener(null);
            checkBox.setChecked(task.isDone());
            title.setText(task.getTitle());

            boolean done = task.isDone();
            float alpha = done ? 0.45f : 1f;
            if (done) {
                title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                title.setPaintFlags(title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            }
            title.setAlpha(alpha);
            dragHandle.setVisibility(done ? View.GONE : View.VISIBLE);

            if (!done) {
                bellIcon.setVisibility(View.VISIBLE);
                bellIcon.setAlpha(task.getReminderTime() != null ? 1f : 0.35f);
                bellIcon.setOnClickListener(v -> reminder.onReminder(task));
            } else {
                bellIcon.setVisibility(View.GONE);
            }

            String desc = task.getDescription();
            if (desc != null && !desc.isEmpty()) {
                description.setText(desc);
                description.setAlpha(alpha);
                description.setVisibility(View.VISIBLE);
            } else {
                description.setVisibility(View.GONE);
            }

            TaskCategory cat = task.getCategoryId() != 0
                    ? categoryMap.get(task.getCategoryId()) : null;
            if (cat != null) {
                categoryLabel.setText(cat.getName());
                categoryLabel.setVisibility(View.VISIBLE);
                try {
                    int color = Color.parseColor(cat.getColorHex());
                    GradientDrawable bg = new GradientDrawable();
                    bg.setShape(GradientDrawable.RECTANGLE);
                    bg.setCornerRadius(8f);
                    bg.setColor(color);
                    categoryLabel.setBackground(bg);
                } catch (IllegalArgumentException ignored) {}
                if (done) categoryLabel.setAlpha(0.45f);
                else categoryLabel.setAlpha(1f);
            } else {
                categoryLabel.setVisibility(View.GONE);
            }

            checkBox.setOnCheckedChangeListener((btn, checked) -> toggle.onToggle(task));

            itemView.setOnLongClickListener(v -> {
                edit.onEdit(task);
                return true;
            });

            dragHandle.setOnTouchListener((v, e) -> {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    drag.onStartDrag(this);
                }
                return false;
            });
        }
    }

    static class AddFooterHolder extends RecyclerView.ViewHolder {
        AddFooterHolder(View v) { super(v); }
        void bind(OnAddListener listener) {
            itemView.setOnClickListener(v -> listener.onAdd());
        }
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        TextView label;
        TextView chevron;

        HeaderHolder(View v) {
            super(v);
            label = v.findViewById(R.id.task_header_label);
            chevron = v.findViewById(R.id.task_header_chevron);
        }

        void bind(int count, boolean expanded, Runnable onClick) {
            label.setText(label.getContext().getString(R.string.tasks_completed_header, count));
            chevron.setText(expanded ? "▲" : "▼");
            itemView.setOnClickListener(v -> onClick.run());
        }
    }
}
