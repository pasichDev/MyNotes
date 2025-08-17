package com.pasich.mynotes.ui.view.activity;

import static com.pasich.mynotes.utils.actionPanel.ActionUtils.getAction;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.transition.platform.MaterialFade;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.data.model.TrashNote;
import com.pasich.mynotes.databinding.ActivityTrashBinding;
import com.pasich.mynotes.databinding.ItemNoteTrashBinding;
import com.pasich.mynotes.ui.contract.TrashContract;
import com.pasich.mynotes.ui.presenter.TrashPresenter;
import com.pasich.mynotes.utils.actionPanel.ActionUtils;
import com.pasich.mynotes.utils.actionPanel.interfaces.ManagerViewAction;
import com.pasich.mynotes.utils.actionPanel.tool.TrashNoteActionTool;
import com.pasich.mynotes.utils.adapters.notes.TrashAdapter;
import com.pasich.mynotes.utils.recycler.SpacesItemDecoration;

import java.util.List;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class TrashActivity extends BaseActivity implements TrashContract.view, ManagerViewAction<TrashNote> {

    @Inject
    public TrashPresenter trashPresenter;
    public ActivityTrashBinding binding;
    @Inject
    public TrashNoteActionTool trashNoteActionTool;
    @Inject
    public TrashAdapter<ItemNoteTrashBinding> mNotesTrashAdapter;
    @Inject
    public ActionUtils actionUtils;

    @Named("NotesItemSpaceDecoration")
    @Inject
    public SpacesItemDecoration itemDecorationNotes;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        selectTheme();
        binding = ActivityTrashBinding.inflate(getLayoutInflater());
        getWindow().setEnterTransition(new MaterialFade().addTarget(binding.activityTrash));
        getWindow().setAllowEnterTransitionOverlap(true);
        super.onCreate(savedInstanceState);
        setContentView(binding.getRoot());
        trashPresenter.attachView(this);
        trashPresenter.viewIsReady();
        binding.setPresenter(trashPresenter);
        actionUtils.setMangerView(binding.getRoot());
        actionUtils.setTrash();
        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                setEnabled(finishActivity());
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
        mNotesTrashAdapter.setOnItemClickListener((position, model) -> selectItemAction(model, position, true));

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
        mNotesTrashAdapter.setOnItemClickListener(null);

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_activity_toolbar, menu);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (getAction()) actionUtils.closeActionPanel();
        finishActivity();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == android.R.id.home) {
            if (getAction()) {
                actionUtils.closeActionPanel();
            } else finishActivity();
        }

        return true;
    }


    private boolean finishActivity() {
        supportFinishAfterTransition();
        return true;
    }

    @Override
    public void settingsNotesList() {
        binding.ListTrash.addItemDecoration(itemDecorationNotes);
        binding.ListTrash.setAdapter(mNotesTrashAdapter);

    }

    @Override
    public void loadData(List<TrashNote> trashList) {
        mNotesTrashAdapter.sortListTrash(trashList);
        if (trashList.size() == 0) showEmptyTrash();
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


    @Override
    public void activateActionPanel() {
        binding.cleanTrash.setVisibility(View.GONE);
    }

    @Override
    public void deactivationActionPanel() {
        binding.cleanTrash.setVisibility(View.VISIBLE);
    }

    @Override
    public void toolCleanChecked() {
        trashNoteActionTool.checkedClean();
    }


    @Override
    public void restoreNotes() {
        trashPresenter.restoreNotesArray(trashNoteActionTool.getArrayChecked());
        actionUtils.closeActionPanel();
    }

    @Override
    public void selectItemAction(TrashNote note, int position, boolean payloads) {
        if (note.getChecked()) {
            note.setChecked(false);
            if (!trashNoteActionTool.isCheckedItemFalse(note)) actionUtils.closeActionPanel();
        } else {
            trashNoteActionTool.isCheckedItem(note);
            note.setChecked(true);
        }

        actionUtils.manageActionPanel(trashNoteActionTool.getCountCheckedItem());
        mNotesTrashAdapter.notifyItemChanged(position, 22);
    }

}
