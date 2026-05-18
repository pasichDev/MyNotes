package com.pasich.mynotes.ui.view.activity;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pasich.mynotes.ui.view.dialogs.TaskReminderPickerBottomSheet;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.data.model.TaskCategory;
import com.pasich.mynotes.databinding.ActivityTasksBinding;
import com.pasich.mynotes.databinding.DialogDeleteConfirmBinding;
import com.pasich.mynotes.ui.contract.TasksContract;
import com.pasich.mynotes.ui.view.dialogs.tasks.AddCategoryDialog;
import com.pasich.mynotes.ui.view.dialogs.tasks.AddTaskDialog;
import com.pasich.mynotes.utils.adapters.tasks.TasksAdapter;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class TasksActivity extends BaseActivity implements TasksContract.view {

    @Inject
    public TasksContract.presenter tasksPresenter;

    private ActivityTasksBinding binding;
    private TasksAdapter tasksAdapter;
    private ItemTouchHelper itemTouchHelper;
    private int selectedCategoryId = 0;
    private boolean dragMoved = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selectTheme();
        binding = ActivityTasksBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        tasksAdapter = new TasksAdapter(
                task -> tasksPresenter.toggleTask(task),
                task -> showDeleteTaskDialog(task),
                task -> showEditDialog(task),
                () -> AddTaskDialog.show(this, selectedCategoryId,
                        (title, desc, catId) -> tasksPresenter.addTask(title, desc, catId)),
                holder -> itemTouchHelper.startDrag(holder),
                task -> showReminderPicker(task));

        binding.tasksList.setLayoutManager(new LinearLayoutManager(this));
        binding.tasksList.setAdapter(tasksAdapter);

        itemTouchHelper = new ItemTouchHelper(buildTouchCallback());
        itemTouchHelper.attachToRecyclerView(binding.tasksList);

        tasksPresenter.attachView(this);
        tasksPresenter.viewIsReady();

        getSupportFragmentManager().setFragmentResultListener(
                "taskReminderChanged", this,
                (requestKey, result) -> tasksPresenter.onCategorySelected(selectedCategoryId));
    }

    private ItemTouchHelper.SimpleCallback buildTouchCallback() {
        return new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean isLongPressDragEnabled() { return false; }

            @Override
            public int getDragDirs(@NonNull RecyclerView rv,
                                   @NonNull RecyclerView.ViewHolder vh) {
                if (!tasksAdapter.isActiveTask(vh.getAdapterPosition())) return 0;
                return super.getDragDirs(rv, vh);
            }

            @Override
            public int getSwipeDirs(@NonNull RecyclerView rv,
                                    @NonNull RecyclerView.ViewHolder vh) {
                if (!tasksAdapter.isActiveTask(vh.getAdapterPosition())) return 0;
                return super.getSwipeDirs(rv, vh);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                boolean moved = tasksAdapter.moveItem(
                        from.getAdapterPosition(), to.getAdapterPosition());
                if (moved) dragMoved = true;
                return moved;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                int pos = vh.getAdapterPosition();
                Task task = tasksAdapter.getTaskAt(pos);
                if (task == null) {
                    tasksAdapter.notifyItemChanged(pos);
                    return;
                }
                showDeleteDialog(
                        R.string.tasks_delete_confirm,
                        task.getTitle(),
                        () -> tasksPresenter.deleteTask(task),
                        () -> tasksAdapter.notifyItemChanged(pos));
            }

            @Override
            public void clearView(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                if (dragMoved) {
                    dragMoved = false;
                    tasksPresenter.updatePositions(tasksAdapter.getActiveTasks());
                }
            }
        };
    }

    private void showDeleteTaskDialog(Task task) {
        showDeleteDialog(
                R.string.tasks_delete_confirm,
                task.getTitle(),
                () -> tasksPresenter.deleteTask(task),
                null);
    }

    private void showDeleteDialog(@StringRes int titleRes, String name,
                                  Runnable onDelete, Runnable onCancel) {
        DialogDeleteConfirmBinding dialogBinding =
                DialogDeleteConfirmBinding.inflate(getLayoutInflater());
        dialogBinding.deleteConfirmTitle.setText(titleRes);
        dialogBinding.deleteConfirmName.setText(name);

        new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.delete, (d, w) -> {
                    if (onDelete != null) onDelete.run();
                })
                .setNegativeButton(R.string.cancel, (d, w) -> {
                    if (onCancel != null) onCancel.run();
                })
                .setOnCancelListener(d -> {
                    if (onCancel != null) onCancel.run();
                })
                .show();
    }

    private void showReminderPicker(Task task) {
        TaskReminderPickerBottomSheet.newInstance(
                task.getId(),
                task.getTitle(),
                task.getReminderTime(),
                task.getReminderIntervalMinutes()
        ).show(getSupportFragmentManager(), "taskReminderPicker");
    }

    private void showEditDialog(Task task) {
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_add_task, null);
        android.widget.EditText input = view.findViewById(R.id.add_task_input);
        android.widget.EditText descInput = view.findViewById(R.id.add_task_desc_input);
        input.setText(task.getTitle());
        input.setSelection(task.getTitle().length());
        if (task.getDescription() != null) descInput.setText(task.getDescription());

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.tasks_edit)
                .setView(view)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String text = input.getText().toString().trim();
                    String desc = descInput.getText().toString().trim();
                    if (!text.isEmpty())
                        tasksPresenter.editTask(task, text, desc.isEmpty() ? null : desc);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        input.requestFocus();
    }

    @Override
    public void initListeners() {
        binding.tasksToolbar.setNavigationOnClickListener(v -> finish());
        binding.tasksToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.tasks_add_category) {
                AddCategoryDialog.show(this, (name, colorHex) ->
                        tasksPresenter.addCategory(name, colorHex));
                return true;
            }
            return false;
        });
    }

    @Override
    public void renderTasks(List<Task> active, List<Task> completed) {
        tasksAdapter.submitList(active, completed);
        binding.tasksEmpty.setVisibility(
                active.isEmpty() && completed.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void renderCategories(List<TaskCategory> categories) {
        tasksAdapter.setCategories(categories);
        binding.tasksCategoryChips.removeAllViews();
        if (categories.isEmpty()) {
            binding.tasksCategoriesScroll.setVisibility(View.GONE);
            return;
        }
        binding.tasksCategoriesScroll.setVisibility(View.VISIBLE);

        Chip allChip = new Chip(this);
        allChip.setText(getString(R.string.search_filter_all));
        allChip.setCheckable(true);
        allChip.setChecked(selectedCategoryId == 0);
        allChip.setOnClickListener(v -> {
            selectedCategoryId = 0;
            tasksPresenter.onCategorySelected(0);
        });
        binding.tasksCategoryChips.addView(allChip);

        for (TaskCategory cat : categories) {
            Chip chip = new Chip(this);
            chip.setText(cat.getName());
            chip.setCheckable(true);
            chip.setChecked(selectedCategoryId == cat.getId());
            chip.setOnLongClickListener(v -> {
                showDeleteDialog(
                        R.string.tasks_category_delete_confirm,
                        cat.getName(),
                        () -> tasksPresenter.deleteCategory(cat),
                        null);
                return true;
            });
            chip.setOnClickListener(v -> {
                selectedCategoryId = cat.getId();
                tasksPresenter.onCategorySelected(cat.getId());
            });
            binding.tasksCategoryChips.addView(chip);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        tasksPresenter.detachView();
    }
}
