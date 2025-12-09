package com.pasich.mynotes.ui.view.activity;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.ActivityTrashBinding;
import com.pasich.mynotes.ui.contract.TrashContract;
import com.pasich.mynotes.ui.controllers.SelectionController;
import com.pasich.mynotes.ui.presenter.TrashPresenter;
import com.pasich.mynotes.ui.view.dialogs.TrashInfoDialog;
import com.pasich.mynotes.utils.adapters.notes.NoteAdapter;
import com.pasich.mynotes.utils.recycler.SpacesItemDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class TrashActivity extends BaseActivity implements TrashContract.view {

    @Inject
    public TrashPresenter trashPresenter;
    public ActivityTrashBinding binding;
    @Inject
    public NoteAdapter mNotesTrashAdapter;
    @Named("NotesItemSpaceDecoration")
    @Inject
    public SpacesItemDecoration itemDecorationNotes;
    private SelectionController selectionController;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selectTheme();
        binding = ActivityTrashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // set selection trash
        selectionController = new SelectionController(mNotesTrashAdapter, binding.getRoot());
        mNotesTrashAdapter.setSelectionController(selectionController);
        selectionController.setPanelMode(SelectionController.Mode.RESTORE);


        setupEdgeToEdgeInsets(binding.getRoot());
        trashPresenter.attachView(this);
        trashPresenter.viewIsReady();
        binding.setPresenter(trashPresenter);

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (selectionController.isInSelectionMode()) {
                    selectionController.clearSelection();
                } else {
                    finishActivity();
                }
            }
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void initListeners() {
        mNotesTrashAdapter.setOnItemClickListener((position, model) -> selectItemAction(model));

    }

    @Override
    public void settingsActionBar() {
        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        trashPresenter.detachView();
        if (mNotesTrashAdapter != null) {
            mNotesTrashAdapter.setOnItemClickListener(null);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_trash_toolbar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == android.R.id.home) {
            if (selectionController.isInSelectionMode()) {
                selectionController.clearSelection();
            } else {
                finishActivity();
            }
        }
        if (item.getItemId() == R.id.helpTrash) {
            TrashInfoDialog.show(this);
        }
        return true;
    }


    private void finishActivity() {
        supportFinishAfterTransition();
    }

    @Override
    public void settingsNotesList() {
        binding.ListTrash.addItemDecoration(itemDecorationNotes);
        binding.ListTrash.setAdapter(mNotesTrashAdapter);
        selectionController.setListener(new SelectionController.Listener() {
            @Override
            public void onSelectionModeChanged(boolean active) {
                binding.cleanTrash.setVisibility(active ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onRestoreRequested() {
                restoreNotes();
            }

        });

    }

    @Override
    public void loadData(List<Note> trashList) {
        mNotesTrashAdapter.submitList(trashList);
        if (trashList.isEmpty()) showEmptyTrash();
    }


    private void showEmptyTrash() {
        binding.setEmptyNotesTrash(true);
        if (getResources().getDisplayMetrics().density < 2.2)
            binding.includeEmpty.imageEmpty.setVisibility(View.GONE);

        binding.includeEmpty.emptyViewTrash.setVisibility(View.VISIBLE);
    }

    @Override
    public void cleanTrashDialogShow() {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.trashClean).setMessage(R.string.cleanTrashMessage).setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss()).setPositiveButton(R.string.yesCleanTrash, (dialog, which) -> {
            trashPresenter.clearTrash();
            dialog.dismiss();
        }).show();

    }


    public void restoreNotes() {

        trashPresenter.restoreNotesArray(new ArrayList<>(selectionController.getSelectedNotes()));
        selectionController.clearSelection();
    }


    public void selectItemAction(Note note) {
        if (!selectionController.isInSelectionMode()) {
            selectionController.startSelection(note);
        } else {
            selectionController.toggle(note);
        }
    }

}
