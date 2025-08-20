package com.pasich.mynotes.ui.view.activity;

import static com.pasich.mynotes.utils.actionPanel.ActionUtils.getAction;
import static com.pasich.mynotes.utils.constants.settings.TagSettings.MAX_TAG_COUNT;

import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.google.android.gms.tasks.Task;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.search.SearchView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.base.simplifications.TextWatcher;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.databinding.ActivityMainBinding;
import com.pasich.mynotes.databinding.ItemNoteBinding;
import com.pasich.mynotes.ui.contract.MainContract;
import com.pasich.mynotes.ui.presenter.MainPresenter;
import com.pasich.mynotes.ui.view.dialogs.MoreNoteDialog;
import com.pasich.mynotes.ui.view.dialogs.about.AboutDialog;
import com.pasich.mynotes.ui.view.dialogs.about.AboutOpensActivity;
import com.pasich.mynotes.ui.view.dialogs.main.DeleteTagDialog;
import com.pasich.mynotes.ui.view.dialogs.main.NameTagDialog;
import com.pasich.mynotes.ui.view.dialogs.main.SortDialog;
import com.pasich.mynotes.ui.view.dialogs.main.popupWindowsTag.PopupWindowsTag;
import com.pasich.mynotes.ui.view.dialogs.main.popupWindowsTag.PopupWindowsTagOnClickListener;
import com.pasich.mynotes.utils.ShareUtils;
import com.pasich.mynotes.utils.actionPanel.ActionUtils;
import com.pasich.mynotes.utils.actionPanel.interfaces.ManagerViewAction;
import com.pasich.mynotes.utils.actionPanel.tool.NoteActionTool;
import com.pasich.mynotes.utils.adapters.baseGenericAdapter.OnItemClickListener;
import com.pasich.mynotes.utils.adapters.notes.NoteAdapter;
import com.pasich.mynotes.utils.adapters.searchAdapter.SearchNotesAdapter;
import com.pasich.mynotes.utils.adapters.tagAdapter.OnItemClickListenerTag;
import com.pasich.mynotes.utils.adapters.tagAdapter.TagsAdapter;
import com.pasich.mynotes.utils.constants.NameTransition;
import com.pasich.mynotes.utils.constants.SnackBarInfo;
import com.pasich.mynotes.utils.recycler.SpacesItemDecoration;
import com.pasich.mynotes.utils.recycler.SwipeToListNotesCallback;
import com.pasich.mynotes.utils.tool.FormatListTool;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends BaseActivity implements MainContract.view, ManagerViewAction<Note> {

    final private ActivityResultLauncher<Intent> startThemeActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == 11) {
                    assert data != null;
                    this.redrawActivity(data.getIntExtra("updateThemeStyle", 0));
                }

            });
    public ActivityMainBinding mActivityBinding;
    @Inject
    public MainContract.presenter mainPresenter;
    @Inject
    public FormatListTool formatList;
    @Inject
    public NoteActionTool noteActionTool;
    @Inject
    public TagsAdapter tagsAdapter;
    @Inject
    public StaggeredGridLayoutManager staggeredGridLayoutManager;
    @Inject
    public ActionUtils actionUtils;
    @Inject
    public NoteAdapter<ItemNoteBinding> mNoteAdapter;
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

    private static final int REQUEST_UPDATE = 100;
    private AppUpdateManager appUpdateManager;

    // Ланчер для ActivityResult API
    private final ActivityResultLauncher<Intent> updateLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK) {
                    Toast.makeText(this, "Оновлення не вдалося!", Toast.LENGTH_SHORT).show();
                    checkForUpdate();
                }
            });

    @Override
    public void onCreate(Bundle savedInstanceState) {

        selectTheme();
        setExitSharedElementCallback(new MaterialContainerTransformSharedElementCallback());
        getWindow().setSharedElementsUseOverlay(false);

        super.onCreate(savedInstanceState);
        mActivityBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mActivityBinding.getRoot());
        mainPresenter.attachView(this);
        mainPresenter.viewIsReady();
        mActivityBinding.setPresenter((MainPresenter) mainPresenter);
        actionUtils.setMangerView(mActivityBinding.getRoot());

        // Ініціалізуйте AppUpdateManager
        appUpdateManager = AppUpdateManagerFactory.create(this);

        // Перевірте доступність оновлення
        checkForUpdate();

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
    protected void onDestroy() {
        super.onDestroy();
        if (isDestroyed()) {
            mainPresenter.detachView();
            variablesNull();

        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        appUpdateManager.getAppUpdateInfo().addOnSuccessListener(appUpdateInfo -> {
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                startUpdateFlow(appUpdateInfo);
            }
        });

    }

    private void checkForUpdate() {
        Task<AppUpdateInfo> appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();
        appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
            Log.d("AppUpdate", "Update availability: " +
            appUpdateInfo.updateAvailability());

            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                    && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                startUpdateFlow(appUpdateInfo);
            }
        }).addOnFailureListener(e -> {
            Log.d("AppUpdate", "Error checking for updates: " + e.getMessage());
        });

    }

    private void startUpdateFlow(AppUpdateInfo appUpdateInfo) {
        try {
            appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    AppUpdateType.IMMEDIATE,
                    this,
                    REQUEST_UPDATE);
        } catch (Exception e) {
             Log.d("AppUpdate", "Unable to start the update: " + e.getMessage());
        }
    }

    @Override
    public void startDeleteTagDialog(Tag tag) {
        new DeleteTagDialog(tag).show(getSupportFragmentManager(), "deleteTag");
    }

    @Override
    public void exitWhat() {
        onInfoSnack(R.string.exitWhat, mActivityBinding.newNotesButton, SnackBarInfo.Info, Snackbar.LENGTH_LONG);
    }

    @Override
    public void finishActivityOtPresenter() {
        finish();
    }

    @Override
    public void hideSearchView() {
        mActivityBinding.searchView.hide();
    }

    @Override
    public void initListeners() {
        mActivityBinding.searchView.addTransitionListener(
                (searchView, previousState, newState) -> {
                    if (newState == SearchView.TransitionState.SHOWING) {
                        mActivityBinding.listNotes.setNestedScrollingEnabled(false);
                    } else if (newState == SearchView.TransitionState.HIDDEN) {
                        mActivityBinding.listNotes.setNestedScrollingEnabled(true);

                    }
                });

        searchNotesAdapter.setItemClickListener((idNote, view) -> openNoteEdit(idNote, (MaterialCardView) view));
        mActivityBinding.searchView.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            protected void changeText(Editable s) {
                if (s.length() >= 2)
                    searchNotesAdapter.filter(s.toString());
                else {
                    searchNotesAdapter.cleanResult();
                }
            }
        });

        mActivityBinding.actionSearch.setOnMenuItemClickListener(
                menuItem -> {
                    int idItem = menuItem.getItemId();
                    if (idItem == R.id.sort) {
                        if (!getAction())
                            new SortDialog().show(getSupportFragmentManager(), "sortDialog");
                    } else if (idItem == R.id.format) {
                        if (!getAction()) {
                            formatList.formatNote(menuItem);
                            staggeredGridLayoutManager.setSpanCount(mainPresenter.getDataManager().getFormatCount());
                        }
                    } else if (idItem == R.id.more) {
                        openMoreActivity();
                    }

                    return true;
                });

        tagsAdapter.setOnItemClickListener(new OnItemClickListenerTag() {
            @Override
            public void onClick(int position) {
                if (!getAction())
                    mainPresenter.clickTag(tagsAdapter.getCurrentList().get(position), position);
            }

            @Override
            public void onLongClick(int position, View mView) {
                if (!getAction())
                    mainPresenter.clickLongTag(tagsAdapter.getCurrentList().get(position), mView);
            }
        });

        mNoteAdapter.setOnItemClickListener(new OnItemClickListener<>() {
            @Override
            public void onClick(int position, Note model) {
                if (!getAction()) {
                    openNoteEdit(model.id,
                            (MaterialCardView) staggeredGridLayoutManager.findViewByPosition(position));
                } else
                    selectItemAction(model, position, true);

            }

            @Override
            public void onLongClick(int position, Note model) {
                if (!getAction())
                    choiceNoteDialog(model, position);
            }

        });

    }

    private void openMoreActivity() {
        if (getAction())
            actionUtils.closeActionPanel();
        new AboutDialog(new AboutOpensActivity() {
            @Override
            protected void openThemeActivity() {
                startThemeActivity.launch(new Intent(MainActivity.this, ThemeActivity.class),
                        ActivityOptionsCompat.makeSceneTransitionAnimation(MainActivity.this,
                                (Pair<View, String>[]) null));
            }

            @Override
            protected void openTrash() {
                startActivity(new Intent(MainActivity.this, TrashActivity.class),
                        ActivityOptions.makeSceneTransitionAnimation(MainActivity.this).toBundle());

            }

            @Override
            protected void openAboutActivity() {
                startActivity(new Intent(MainActivity.this, AboutActivity.class),
                        ActivityOptions.makeSceneTransitionAnimation(MainActivity.this).toBundle());
            }

            @Override
            protected void openBackupActivity() {
                startActivity(new Intent(MainActivity.this, BackupActivity.class),
                        ActivityOptions.makeSceneTransitionAnimation(MainActivity.this).toBundle());
            }
        }).show(getSupportFragmentManager(), "MoreActivity");
    }

    @Override
    public void settingsSearchView() {
        formatList.init(mActivityBinding.actionSearch.getMenu().findItem(R.id.format));

    }

    @Override
    public void settingsLists() {
        mActivityBinding.listTags.addItemDecoration(itemDecorationTags);
        mActivityBinding.listTags.setLayoutManager(mLinearLayoutManager);
        mActivityBinding.listTags.setAdapter(tagsAdapter);
        mActivityBinding.listNotes.addItemDecoration(itemDecorationNotes);
        mActivityBinding.listNotes.setLayoutManager(staggeredGridLayoutManager);
        mActivityBinding.listNotes.setAdapter(mNoteAdapter);
        mActivityBinding.resultsSearchList
                .setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        mActivityBinding.resultsSearchList.addItemDecoration(itemDecorationNotes);
        mActivityBinding.resultsSearchList.setAdapter(searchNotesAdapter);
        //  mActivityBinding.searchView.findViewById(R.id.search_view_divider).setVisibility(View.GONE);

        new ItemTouchHelper(new SwipeToListNotesCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean isItemViewSwipeEnabled() {
                return !getAction() && mainPresenter.getDataManager().getFormatCount() == 1;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();

                if (direction == ItemTouchHelper.LEFT) {
                    selectItemAction(mNoteAdapter.getCurrentList().get(position), position, false);

                } else {

                    Note sNote = mNoteAdapter.getCurrentList().get(position);
                    mainPresenter.setBackupDeleteNote(sNote);
                    mainPresenter.deleteNote(sNote);
                    snackBarRestoreNote();
                }
            }
        }).attachToRecyclerView(mActivityBinding.listNotes);

    }

    public void snackBarRestoreNote() {
        Snackbar snackbar = Snackbar.make(mActivityBinding.newNotesButton, getString(R.string.noteMoveTrashSnackbar),
                Snackbar.LENGTH_LONG);
        snackbar.setAction(getString(R.string.restore),
                view -> mainPresenter.restoreNote(mainPresenter.getBackupDeleteNote()));
        if (mActivityBinding.newNotesButton.getY() >= mActivityBinding.activityMain.getHeight()) {
            snackbar.setAnchorView(mActivityBinding.newNotesButton);
        }
        snackbar.show();
    }

    @Override
    public void loadingNotes(List<Note> noteList) {
        int countNotes = mNoteAdapter.sortList(noteList, mainPresenter.getSortParam(),
                tagsAdapter.getTagSelected() == null ? "allNotes" : tagsAdapter.getTagSelected().getNameTag());
        showEmptyNotes(!(countNotes >= 1));
        searchNotesAdapter.setDefaultListNotes(noteList);
    }

    @Override
    public void loadingTags(List<Tag> tagList) {
        tagsAdapter.submitList(tagList);
        int countNotes = mNoteAdapter.setNameTagsHidden(tagList,
                tagsAdapter.getTagSelected() == null ? "allNotes" : tagsAdapter.getTagSelected().getNameTag());
        showEmptyNotes(!(countNotes >= 1));
    }

    private void showEmptyNotes(boolean flag) {
        mActivityBinding.setEmptyNotes(flag);
        if (getResources().getDisplayMetrics().density < 2.2)
            mActivityBinding.includeEmpty.imageEmpty.setVisibility(View.GONE);
        mActivityBinding.includeEmpty.emptyViewNote.setVisibility(flag ? View.VISIBLE : View.GONE);
    }

    @Override
    public void actionStartNote(Note note, int position) {
        selectItemAction(note, position, true);
    }

    @Override
    public void openCopyNote(long idNote) {
        startActivity(new Intent(this, NoteActivity.class)
                .putExtra("NewNote", false)
                .putExtra("idNote", idNote)
                .putExtra("shareText", "")
                .putExtra("tagNote", ""));

    }

    @Override
    public void callbackDeleteNote(Note mNote) {
        mainPresenter.setBackupDeleteNote(mNote);
        snackBarRestoreNote();
    }

    public void openNoteEdit(long idNote, MaterialCardView materialCardView) {
        startActivity(new Intent(this, NoteActivity.class).putExtra("NewNote", false)
                .putExtra("idNote", idNote).putExtra("shareText", "")
                .putExtra("tagNote", ""),
                ActivityOptionsCompat.makeSceneTransitionAnimation(MainActivity.this, materialCardView,
                        String.valueOf(idNote)).toBundle());
    }

    @Override
    public void startToastCheckCountTags() {
        String message = getString(R.string.countTagsError, String.valueOf(MAX_TAG_COUNT));
        Snackbar snackbar = Snackbar.make(mActivityBinding.newNotesButton, message, Snackbar.LENGTH_LONG);
        snackbar.show();
    }

    @Override
    public void newNotesButton() {
        Tag tagSelected = tagsAdapter.getTagSelected();
        String tagName = tagSelected == null ? "" : tagSelected.getSystemAction() == 2 ? "" : tagSelected.getNameTag();
        startActivity(new Intent(this, NoteActivity.class).putExtra("NewNote", true).putExtra("tagNote", tagName),
                ActivityOptionsCompat.makeSceneTransitionAnimation(this, mActivityBinding.newNotesButton,
                        NameTransition.fabTransaction).toBundle());
    }

    @Override
    public void startCreateTagDialog() {
        new NameTagDialog().show(getSupportFragmentManager(), "New Tag");
    }

    @Override
    public void choiceTagDialog(Tag tag, View mView) {
        new PopupWindowsTag(getLayoutInflater(), mView, tag, new PopupWindowsTagOnClickListener() {
            @Override
            public void deleteTag() {
                if (tagsAdapter.getTagSelected() == tag)
                    selectTagUser(tagsAdapter.getTagForName("allNotes"));
                mainPresenter.deleteTag(tag);
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
        new MoreNoteDialog(note, false, false, position).show(getSupportFragmentManager(), "ChoiceDialog");
    }

    private boolean finishActivity() {
        if (getAction()) {
            actionUtils.closeActionPanel();
            return false;
        } else {
            return mainPresenter.closeApp(mActivityBinding.searchView.isShowing());
        }
    }

    @Override
    public void sortList(String arg) {
        mNoteAdapter.sortList(arg);

    }

    @Override
    public void activateActionPanel() {
        mActivityBinding.newNotesButton.setVisibility(View.GONE);
    }

    @Override
    public void deactivationActionPanel() {
        mActivityBinding.newNotesButton.setVisibility(View.VISIBLE);
    }

    @Override
    public void deleteNotes() {
        if (noteActionTool.getArrayChecked().size() == mNoteAdapter.getItemCount()) {
            mActivityBinding.appBarMainActivity.setExpanded(true);

        }
        mainPresenter.deleteNotesArray(noteActionTool.getArrayChecked());
        actionUtils.closeActionPanel();
    }

    @Override
    public void shareNotes() {
        StringBuilder valueShare = new StringBuilder();
        for (Note note : noteActionTool.getArrayChecked()) {
            valueShare.append(note.getTitle()).append(System.lineSeparator())
                    .append(System.lineSeparator()).append(note.getValue())
                    .append(System.lineSeparator()).append(System.lineSeparator());
        }
        ShareUtils.shareNotes(this, valueShare.toString());
        actionUtils.closeActionPanel();
    }

    @Override
    public void selectItemAction(Note note, int position, boolean payloads) {

        if (note.getChecked()) {
            note.setChecked(false);
            if (!noteActionTool.isCheckedItemFalse(note))
                actionUtils.closeActionPanel();
        } else {
            noteActionTool.isCheckedItem(note);
            note.setChecked(true);
        }

        actionUtils.manageActionPanel(noteActionTool.getCountCheckedItem());
        if (payloads)
            mNoteAdapter.notifyItemChanged(position, 22);
        else
            mNoteAdapter.notifyItemChanged(position);
    }

    @Override
    public void toolCleanChecked() {
        noteActionTool.checkedClean();
    }

    @Override
    public void selectTagUser(int position) {
        tagsAdapter.chooseTag(position);
        showEmptyNotes(!(mNoteAdapter.filter(
                tagsAdapter.getTagSelected() == null ? "allNotes" : tagsAdapter.getTagSelected().getNameTag()) >= 1));
    }

    private void variablesNull() {
        mNoteAdapter = null;
        tagsAdapter = null;
    }

    @Override
    public void createShortCut() {
        onInfoSnack(R.string.addShortCutSuccessfully, mActivityBinding.newNotesButton, SnackBarInfo.Info,
                Snackbar.LENGTH_LONG);
    }

    @Override
    public void shortCutDouble() {
        onInfoSnack(R.string.shortCutCreateFallDouble, mActivityBinding.newNotesButton, SnackBarInfo.Info,
                Snackbar.LENGTH_LONG);
    }

    @Override
    public void redrawActivity(int themeStyle) {
        super.redrawActivity(themeStyle);
        setTheme(themeStyle);
        recreate();
    }
}
