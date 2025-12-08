package com.pasich.mynotes.ui.view.activity;

import static com.pasich.mynotes.utils.navigation.ActivityResultKeys.EXTRA_UPDATE_THEME_STYLE;
import static com.pasich.mynotes.utils.navigation.ActivityResultKeys.RESULT_CODE_THEME_UPDATE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.databinding.ActivityMainBinding;
import com.pasich.mynotes.ui.contract.MainContract;
import com.pasich.mynotes.ui.controllers.SelectionController;
import com.pasich.mynotes.ui.controllers.mainActivity.AppUpdateController;
import com.pasich.mynotes.ui.controllers.mainActivity.MainRenderListsController;
import com.pasich.mynotes.ui.controllers.mainActivity.NavigationController;
import com.pasich.mynotes.ui.controllers.mainActivity.SearchController;
import com.pasich.mynotes.ui.presenter.MainPresenter;
import com.pasich.mynotes.ui.state.MainViewState;
import com.pasich.mynotes.ui.state.StatsData;
import com.pasich.mynotes.ui.state.UiEvent;
import com.pasich.mynotes.ui.view.dialogs.MoreNoteDialog;
import com.pasich.mynotes.ui.view.dialogs.ShareOptionsDialog;
import com.pasich.mynotes.ui.view.dialogs.main.AllTagSelectDialog;
import com.pasich.mynotes.ui.view.dialogs.main.DeleteTagDialog;
import com.pasich.mynotes.ui.view.dialogs.main.NameTagDialog;
import com.pasich.mynotes.ui.view.dialogs.main.SortDialog;
import com.pasich.mynotes.ui.view.dialogs.main.popupWindowsTag.PopupWindowsTag;
import com.pasich.mynotes.ui.view.dialogs.main.popupWindowsTag.PopupWindowsTagOnClickListener;
import com.pasich.mynotes.utils.UpdateChecker;
import com.pasich.mynotes.utils.adapters.notes.NoteAdapter;
import com.pasich.mynotes.utils.adapters.notes.OnItemClickListener;
import com.pasich.mynotes.utils.adapters.searchAdapter.SearchNotesAdapter;
import com.pasich.mynotes.utils.adapters.tagAdapter.OnItemClickListenerTag;
import com.pasich.mynotes.utils.adapters.tagAdapter.TagsAdapter;
import com.pasich.mynotes.utils.constants.NameTransition;
import com.pasich.mynotes.utils.constants.SnackBarInfo;
import com.pasich.mynotes.utils.managers.SystemTagsManager;
import com.pasich.mynotes.utils.navigation.NoteNavigator;
import com.pasich.mynotes.utils.recycler.SpacesItemDecoration;
import com.pasich.mynotes.utils.recycler.SwipeToListNotesCallback;
import com.pasich.mynotes.utils.tool.FormatListTool;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends BaseActivity implements MainContract.view {

    // Update theme listener
    final private ActivityResultLauncher<Intent> themeUpdateListener = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Intent data = result.getData();
        if (result.getResultCode() == RESULT_CODE_THEME_UPDATE) {
            assert data != null;
            if (data.hasExtra(EXTRA_UPDATE_THEME_STYLE)) {
                this.redrawActivity();
            }
        }
    });
    public ActivityMainBinding mActivityBinding;
    @Inject
    public MainContract.presenter mainPresenter;
    @Inject
    public FormatListTool formatList;
    @Inject
    public TagsAdapter tagsAdapter;
    @Inject
    public StaggeredGridLayoutManager gridLayoutManager;

    @Inject
    public NoteAdapter mNoteAdapter;
    @Named("TagsItemSpaceDecoration")
    @Inject
    public SpacesItemDecoration itemDecorationTags;
    @Named("NotesItemSpaceDecoration")
    @Inject
    public SpacesItemDecoration itemDecorationNotes;
    @Inject
    public LinearLayoutManager mLinearLayoutManager;
    @Inject
    SearchNotesAdapter searchNotesAdapter;
    @Inject
    UpdateChecker updateChecker;
    @Inject
    ThemePreferencesCache themePreferencesCache;
    private SearchController searchController;
    private AppUpdateController appUpdateController;
    private NavigationController navigationController;
    private final ActivityResultLauncher<Intent> changelogLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            boolean hasNewVersion = updateChecker.hasNewVersion();
            if (navigationController != null)
                navigationController.updateNewVersionIndicator(hasNewVersion);
        }
    });
    private MainRenderListsController mainRenderListsController;
    private Tag currentSelectedTag = null;

    private SelectionController selectionController;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setExitSharedElementCallback(new MaterialContainerTransformSharedElementCallback());
        getWindow().setSharedElementsUseOverlay(false);
        super.onCreate(savedInstanceState);
        selectTheme();
        mActivityBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mActivityBinding.getRoot());

        // set selection
        selectionController = new SelectionController(mNoteAdapter, mActivityBinding.getRoot());
        mNoteAdapter.setSelectionController(selectionController);
        selectionController.setPanelMode(SelectionController.Mode.NORMAL);


        mainPresenter.attachView(this);
        mainPresenter.viewIsReady();
        mActivityBinding.setPresenter((MainPresenter) mainPresenter);


        searchController = new SearchController(mActivityBinding, searchNotesAdapter, new SearchController.Listener() {
            @Override
            public void onSearchOpen() {
                mActivityBinding.listNotes.setNestedScrollingEnabled(false);
            }

            @Override
            public void onSearchClose() {
                mActivityBinding.listNotes.setNestedScrollingEnabled(true);
            }

            @Override
            public void onSearchQuery(String query) {
                mainPresenter.updateSearchQuery(query);
            }
        });
        appUpdateController = new AppUpdateController(this, updateChecker, changelogLauncher);
        appUpdateController.showChangelogIfNeeded();

        navigationController = new NavigationController(this, mActivityBinding, themeUpdateListener, appUpdateController, this::finishActivity);
        navigationController.init();
        navigationController.handleShortcuts(getIntent());

        mainRenderListsController = new MainRenderListsController(mActivityBinding);


    }

    @Override
    public void render(MainViewState state) {
        if (selectionController.isInSelectionMode()) {
            return;
        }

        currentSelectedTag = state.selectedTag();
        // render notes list
        renderNotes(state.notes(), state.selectedTag(), state.uiEvent());
        // render tags list
        renderTags(state.tags());
    }


    private void renderTags(List<Tag> tags) {
        mainRenderListsController.renderListTags(tags);
        tagsAdapter.submitList(tags);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void renderNotes(List<Note> notes, Tag currentSelectedTag, UiEvent event) {
        switch (event) {
            case SORT_CHANGED, TAG_CHANGED ->
                    mNoteAdapter.submitList(new ArrayList<>(notes), () -> {
                        mNoteAdapter.notifyDataSetChanged();
                        mainRenderListsController.animateNoteListChange();
                    });
            default -> {
                boolean shouldScrollUp = event == UiEvent.NOTE_CREATED;
                mNoteAdapter.submitList(notes, () -> {
                    mainRenderListsController.showStateNoteList(currentSelectedTag, notes.size());
                    if (shouldScrollUp) {
                        mainRenderListsController.scrollUpNoteList();
                    }
                });
            }
        }

        mainPresenter.clearUiEvent();
    }

    @Override
    public void renderDrawerStats(StatsData stats) {

        navigationController.getHeaderBinding().drawerStatsNotesNow.setText(String.valueOf(stats.notesNow()));
        navigationController.getHeaderBinding().drawerStatsNotesMonth.setText(String.valueOf(stats.notesMonth()));
        navigationController.getHeaderBinding().drawerStatsChars.setText(String.valueOf(stats.chars()));

    }


    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        navigationController.handleShortcuts(intent);
    }


    @Override
    protected void onResume() {
        super.onResume();
        appUpdateController.handleOnResume();
    }

    @Override
    public void startDeleteTagDialog(Tag tag) {
        new DeleteTagDialog(tag).show(getSupportFragmentManager(), "deleteTag");
    }

    @Override
    public void allTagSelectDialog(List<Tag> tagsList) {
        AllTagSelectDialog.show(this, tagsList, tag -> mainPresenter.onTagSelected(tag));
    }


    @Override
    public void renderSearch(List<Note> filtered) {
        searchNotesAdapter.submitList(filtered);
    }

    @Override
    public void initListeners() {
        mActivityBinding.actionSearch.setOnClickListener(v -> mActivityBinding.searchView.show());
        searchNotesAdapter.setItemClickListener(this::openNoteEdit);
        mActivityBinding.actionSearch.setOnMenuItemClickListener(menuItem -> {
            int idItem = menuItem.getItemId();
            if (idItem == R.id.sort) {
                if (!selectionController.isInSelectionMode()) showSortDialog();
            } else if (idItem == R.id.format) {
                if (!selectionController.isInSelectionMode()) {
                    formatList.formatNote(menuItem);
                    gridLayoutManager.setSpanCount(mainPresenter.getDataManager().getFormatCount());
                }
            }

            return true;
        });

        tagsAdapter.setOnItemClickListener(new OnItemClickListenerTag() {
            @Override
            public void onClick(int position) {
                if (!selectionController.isInSelectionMode()) {
                    Tag clickedTag = tagsAdapter.getCurrentList().get(position);
                    if (clickedTag.getSelected()) {
                        shakeTagAt(position);
                        return;
                    }
                    mainPresenter.onTagSelected(clickedTag);
                }
            }

            @Override
            public void onLongClick(int position, View mView) {
                if (selectionController.isInSelectionMode()) return;
                Tag tag = tagsAdapter.getCurrentList().get(position);
                if (SystemTagsManager.isSystemTag(tag)) {
                    mainPresenter.requestTagSelection();
                    return;
                }

                choiceTagDialog(tag, mView);
            }

        });

        mNoteAdapter.setOnItemClickListener(new OnItemClickListener<>() {
            @Override
            public void onClick(int position, Note model) {
                if (!selectionController.isInSelectionMode()) {
                    openNoteEdit(model, gridLayoutManager.findViewByPosition(position));
                } else selectionController.toggle(model);

            }

            @Override
            public void onLongClick(int position, Note model) {
                if (!selectionController.isInSelectionMode()) choiceNoteDialog(model, position);
            }

        });
        selectionController.setListener(new SelectionController.Listener() {
            @Override
            public void onSelectionModeChanged(boolean active) {
                mActivityBinding.newNotesButton.setVisibility(active ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onDeleteRequested() {
                deleteNotes();
            }

            @Override
            public void onShareRequested() {
                shareNotes();
            }

        });

    }

    private void shakeTagAt(int position) {
        assert mActivityBinding.listTags.getLayoutManager() != null;
        View tagView = mActivityBinding.listTags.getLayoutManager().findViewByPosition(position);
        if (tagView != null) {
            Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake_gentle);
            tagView.startAnimation(shake);
        }
    }

    void showSortDialog() {
        SortDialog dialog = SortDialog.newInstance(false);
        dialog.setListener(new SortDialog.SortListener() {
            @Override
            public void onSortSelected(String sortParam) {
                mainPresenter.onSortChanged(sortParam);
            }

            @Override
            public void onTagsSortSelected(String tagsSortParam) {
            }
        });
        dialog.show(getSupportFragmentManager(), "SortDialog");

    }

    @Override
    public void settingsLists() {
        mActivityBinding.listTags.addItemDecoration(itemDecorationTags);
        mActivityBinding.listTags.setLayoutManager(mLinearLayoutManager);
        mActivityBinding.listTags.setAdapter(tagsAdapter);
        mActivityBinding.listNotes.addItemDecoration(itemDecorationNotes);
        mActivityBinding.listNotes.setLayoutManager(gridLayoutManager);
        mActivityBinding.listNotes.setAdapter(mNoteAdapter);
        mActivityBinding.listNotes.setItemAnimator(new DefaultItemAnimator());

        new ItemTouchHelper(new SwipeToListNotesCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean isItemViewSwipeEnabled() {
                return !selectionController.isInSelectionMode() && mainPresenter.getDataManager().getFormatCount() == 1;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();

                if (direction == ItemTouchHelper.LEFT) {
                    selectItemAction(mNoteAdapter.getCurrentList().get(position));
                    mNoteAdapter.notifyItemChanged(position);
                } else {
                    Note sNote = mNoteAdapter.getCurrentList().get(position);
                    mainPresenter.setBackupDeleteNote(sNote);
                    mainPresenter.noteMoveToTrash(sNote);
                    snackBarRestoreNote();
                }
            }
        }).attachToRecyclerView(mActivityBinding.listNotes);
    }

    public void snackBarRestoreNote() {
        Snackbar snackbar = Snackbar.make(mActivityBinding.drawerLayout, getString(R.string.noteMoveTrashSnackbar), Snackbar.LENGTH_LONG);
        snackbar.setAction(getString(R.string.restore), view -> mainPresenter.restoreNoteLastMoveToTrash(mainPresenter.getBackupDeleteNote()));
        snackbar.setAnchorView(mActivityBinding.newNotesButton);
        snackbar.show();
    }

    @Override
    public void actionStartNote(Note note, int position) {
        selectItemAction(note);
    }

    @Override
    public void openCopyNote(long idNote) {
        new NoteNavigator(this, themePreferencesCache).openNote(idNote, false, "", null, String.valueOf(idNote), false);


    }

    @Override
    public void callbackDeleteNote(Note mNote) {
        mainPresenter.setBackupDeleteNote(mNote);
        snackBarRestoreNote();
    }

    public void openNoteEdit(Note note, View view) {
        new NoteNavigator(this, themePreferencesCache).openNote(note, false, "", view, String.valueOf(note.getId()));
    }


    @Override
    public void openNewNoteWithId(long id) {
        Tag tagSelected = currentSelectedTag;
        String tagName = tagSelected == null ? "" : tagSelected.getSystemAction() == 2 ? "" : tagSelected.getNameTag();

        new NoteNavigator(this, themePreferencesCache).openNote(id, true, tagName, mActivityBinding.newNotesButton, NameTransition.fabTransaction, false);
    }

    @Override
    public void choiceTagDialog(Tag tag, View mView) {
        new PopupWindowsTag(getLayoutInflater(), mView, tag, new PopupWindowsTagOnClickListener() {
            @Override
            public void deleteTag() {
                mainPresenter.requestDeleteTag(tag);
            }

            @Override
            public void renameTag() {
                new NameTagDialog(tag).show(getSupportFragmentManager(), "RenameTag");
            }

            @Override
            public void visibleEditTag() {
                mainPresenter.editVisibleTag(tag.setVisibilityReturn(tag.getVisibility() == 1 ? 0 : 1));
            }
        });

    }

    @Override
    public void choiceNoteDialog(Note note, int position) {
        new MoreNoteDialog(note, MoreNoteDialog.RootActivity.MainActivity, position).show(getSupportFragmentManager(), "ChoiceDialog");
    }


    private boolean finishActivity() {
        if (mActivityBinding.searchView.isShowing()) {
            mActivityBinding.searchView.hide();
            return false;
        }
        if (selectionController.isInSelectionMode()) {
            selectionController.clearSelection();
            return false;
        }

        navigationController.addSwipeClose(1);
        if (navigationController.getSwipeClose() < 2) {
            if (!isDestroyed() && mActivityBinding != null) {
                onInfoSnack(R.string.exitWhat, mActivityBinding.drawerLayout, SnackBarInfo.Info, Snackbar.LENGTH_LONG);
            }
            return false;
        }

        finish();
        return true;


    }

    public void deleteNotes() {
        List<Note> selected = selectionController.getSelectedNotes();

        if (selected.size() == mNoteAdapter.getItemCount()) {
            mActivityBinding.appBarMainActivity.setExpanded(true);
        }

        if (!selected.isEmpty()) {
            mainPresenter.deleteNotesArray(new ArrayList<>(selectionController.getSelectedNotes()));
        }

        selectionController.clearSelection();
    }


    public void shareNotes() {
        List<Note> selected = selectionController.getSelectedNotes();

        if (!selected.isEmpty()) {
            ShareOptionsDialog dialog = new ShareOptionsDialog(selected);
            dialog.show(getSupportFragmentManager(), "ShareOptionsDialog");
        }

        selectionController.clearSelection();
    }


    public void selectItemAction(Note note) {
        if (!selectionController.isInSelectionMode()) {
            selectionController.startSelection(note);
        } else {
            selectionController.toggle(note);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (navigationController != null) {
            navigationController.destroy();
            navigationController = null;
        }

        if (searchController != null) {
            searchController.destroy();
            searchController = null;
        }

        if (isDestroyed()) {
            mainPresenter.detachView();
            variablesNull();
        }
    }

    private void variablesNull() {
        if (mNoteAdapter != null) {
            mNoteAdapter.setOnItemClickListener(null);
        }
        if (tagsAdapter != null) {
            tagsAdapter.setOnItemClickListener(null);
        }
        if (searchNotesAdapter != null) {
            searchNotesAdapter.setItemClickListener(null);
        }

        if (selectionController != null) {
            selectionController.cleanup();
        }

        mNoteAdapter = null;
        tagsAdapter = null;
        searchNotesAdapter = null;
    }


    @Override
    public void redrawActivity() {
        super.redrawActivity();
        recreate();
    }

}