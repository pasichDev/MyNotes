package com.pasich.mynotes.ui.view.activity;


import static android.view.View.VISIBLE;
import static com.pasich.mynotes.utils.navigation.ActivityResultKeys.EXTRA_UPDATE_FONT_SCALE;
import static com.pasich.mynotes.utils.navigation.ActivityResultKeys.EXTRA_UPDATE_THEME_MODE;
import static com.pasich.mynotes.utils.navigation.ActivityResultKeys.EXTRA_UPDATE_THEME_STYLE;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.databinding.ActivityMainBinding;
import com.pasich.mynotes.databinding.ItemNoteBinding;
import com.pasich.mynotes.ui.contract.MainContract;
import com.pasich.mynotes.ui.controllers.AppUpdateController;
import com.pasich.mynotes.ui.controllers.SearchController;
import com.pasich.mynotes.ui.presenter.MainPresenter;
import com.pasich.mynotes.ui.view.dialogs.MoreNoteDialog;
import com.pasich.mynotes.ui.view.dialogs.ShareOptionsDialog;
import com.pasich.mynotes.ui.view.dialogs.main.DeleteTagDialog;
import com.pasich.mynotes.ui.view.dialogs.main.NameTagDialog;
import com.pasich.mynotes.ui.view.dialogs.main.SortDialog;
import com.pasich.mynotes.ui.view.dialogs.main.popupWindowsTag.PopupWindowsTag;
import com.pasich.mynotes.ui.view.dialogs.main.popupWindowsTag.PopupWindowsTagOnClickListener;
import com.pasich.mynotes.utils.UpdateChecker;
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
import com.pasich.mynotes.utils.managers.SystemTagsManager;
import com.pasich.mynotes.utils.navigation.NoteNavigator;
import com.pasich.mynotes.utils.recycler.SpacesItemDecoration;
import com.pasich.mynotes.utils.recycler.SwipeToListNotesCallback;
import com.pasich.mynotes.utils.tool.FormatListTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends BaseActivity implements MainContract.view, ManagerViewAction<Note> {

    // Update theme listener
    final private ActivityResultLauncher<Intent> themeUpdateListener = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Intent data = result.getData();
        if (result.getResultCode() == 11) {
            assert data != null;
            if (data.getBooleanExtra(EXTRA_UPDATE_THEME_MODE, false) || data.getBooleanExtra(EXTRA_UPDATE_FONT_SCALE, false)) {
                recreate();
            } else {
                this.redrawActivity(data.getIntExtra(EXTRA_UPDATE_THEME_STYLE, 0));
            }
        }
    });
    public ActivityMainBinding mActivityBinding;
    @Inject
    public MainContract.presenter mainPresenter;
    // Tags Activity launcher - reloads data when tags are modified
    final private ActivityResultLauncher<Intent> startTagsActivity = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (mainPresenter != null) {
            mainPresenter.loadingData();
        }
    });
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
    @Inject
    UpdateChecker updateChecker;
    @Inject
    ThemePreferencesCache themePreferencesCache;

    private int previousNotesCount = 0;
    // Navigation Drawer variables
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    /**
     * New CODE
     */
    private SearchController searchController;
    private AppUpdateController appUpdateController;


    private final ActivityResultLauncher<Intent> changelogLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            boolean hasNewVersion = updateChecker.hasNewVersion();
                            navigationView.getHeaderView(0).findViewById(R.id.newVersion).setVisibility(hasNewVersion ? VISIBLE : View.GONE);
                        }
                    });


    @Override
    public void onCreate(Bundle savedInstanceState) {
        setExitSharedElementCallback(new MaterialContainerTransformSharedElementCallback());
        getWindow().setSharedElementsUseOverlay(false);
        super.onCreate(savedInstanceState);
        selectTheme();
        mActivityBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mActivityBinding.getRoot());

        setupEdgeToEdgeForDrawer();
        mainPresenter.attachView(this);
        mainPresenter.viewIsReady();
        mActivityBinding.setPresenter((MainPresenter) mainPresenter);
        actionUtils.setMangerView(mActivityBinding.getRoot());

        /**
         * NEWW
         */

        searchController = new SearchController(
                mActivityBinding,
                searchNotesAdapter,
                new SearchController.Listener() {
                    @Override
                    public void onSearchOpen() {
                        mActivityBinding.listNotes.setNestedScrollingEnabled(false);
                    }

                    @Override
                    public void onSearchClose() {
                        mActivityBinding.listNotes.setNestedScrollingEnabled(true);
                    }
                }
        );
        appUpdateController = new AppUpdateController(
                this,
                updateChecker,
                changelogLauncher
        );
        appUpdateController.showChangelogIfNeeded();

        // Ініціалізуємо Navigation Drawer
        setupNavigationDrawer();

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(finishActivity());
                }
            }
        });

        handleShortcuts(getIntent());



    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShortcuts(intent);
    }

    /**
     * Обробка App Shortcuts
     */
    private void handleShortcuts(Intent intent) {
        if (intent != null) {
            // Обробка пошукового shortcuts
            if (intent.getBooleanExtra("open_search", false)) {

                // Відкриваємо пошук з невеликою затримкою після створення активності
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (mActivityBinding != null) {
                        mActivityBinding.searchView.show();
                    }
                }, 100);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searchController.destroy();

        if (isDestroyed()) {
            mainPresenter.detachView();
            variablesNull();
        }
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
    public void exitWhat() {
        if (!isDestroyed() && mActivityBinding != null) {
            onInfoSnack(R.string.exitWhat, mActivityBinding.drawerLayout, SnackBarInfo.Info, Snackbar.LENGTH_LONG);
        }
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
        mActivityBinding.actionSearch.setOnClickListener(v -> mActivityBinding.searchView.show());

        searchNotesAdapter.setItemClickListener(this::openNoteEdit);

        mActivityBinding.actionSearch.setOnMenuItemClickListener(menuItem -> {
            int idItem = menuItem.getItemId();
            if (idItem == R.id.sort) {
                if (!actionUtils.getAction())
                    showSortDialog();
            } else if (idItem == R.id.format) {
                if (!actionUtils.getAction()) {
                    formatList.formatNote(menuItem);
                    staggeredGridLayoutManager.setSpanCount(mainPresenter.getDataManager().getFormatCount());
                }
            }

            return true;
        });

        tagsAdapter.setOnItemClickListener(new OnItemClickListenerTag() {
            @Override
            public void onClick(int position) {
                if (!actionUtils.getAction()) {
                    Tag clickedTag = tagsAdapter.getCurrentList().get(position);
                    if (clickedTag.getSelected() && !SystemTagsManager.isAddTag(clickedTag)) {
                        assert mActivityBinding.listTags.getLayoutManager() != null;
                        View tagView = mActivityBinding.listTags.getLayoutManager().findViewByPosition(position);
                        Animation shake = AnimationUtils.loadAnimation(MainActivity.this, R.anim.shake_gentle);
                        Objects.requireNonNullElseGet(tagView, () -> mActivityBinding.listTags).startAnimation(shake);
                        return;
                    }

                    mainPresenter.clickTag(clickedTag, position);
                }
            }

            @Override
            public void onLongClick(int position, View mView) {
                if (!actionUtils.getAction())
                    mainPresenter.clickLongTag(tagsAdapter.getCurrentList().get(position), mView);
            }
        });

        mNoteAdapter.setOnItemClickListener(new OnItemClickListener<>() {
            @Override
            public void onClick(int position, Note model) {
                if (!actionUtils.getAction()) {
                    openNoteEdit(model, staggeredGridLayoutManager.findViewByPosition(position));
                } else selectItemAction(model, position, true);

            }

            @Override
            public void onLongClick(int position, Note model) {
                if (!actionUtils.getAction()) choiceNoteDialog(model, position);
            }

        });

    }

    void showSortDialog() {
        SortDialog dialog = SortDialog.newInstance(false);
        dialog.setListener(new SortDialog.SortListener() {
            @Override
            public void onSortSelected(String sortParam) {
                sortList(sortParam);
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
        mActivityBinding.listNotes.setLayoutManager(staggeredGridLayoutManager);
        mActivityBinding.listNotes.setAdapter(mNoteAdapter);
        mActivityBinding.listNotes.setItemAnimator(new DefaultItemAnimator());


        mActivityBinding.resultsSearchList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        mActivityBinding.resultsSearchList.addItemDecoration(itemDecorationNotes);
        mActivityBinding.resultsSearchList.setAdapter(searchNotesAdapter);

        new ItemTouchHelper(new SwipeToListNotesCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean isItemViewSwipeEnabled() {
                return !actionUtils.getAction() && mainPresenter.getDataManager().getFormatCount() == 1;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();

                if (direction == ItemTouchHelper.LEFT) {
                    selectItemAction(mNoteAdapter.getCurrentList().get(position), position, false);

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
    /// TODO Перенести обробку в презентер
    public void loadingNotes(List<Note> noteList, String sortParam) {
        // Перевіряємо чи додалася нова нотатка
        boolean isNewNoteAdded = noteList.size() > previousNotesCount;
        if (isNewNoteAdded) {
            // Встановлюємо флаг для прокручування до верху
            mNoteAdapter.setScrollToTopOnNextUpdate(true);
        }

        int countNotes = mNoteAdapter.sortList(noteList, sortParam, tagsAdapter.getTagSelected() == null ? "allNotes" : tagsAdapter.getTagSelected().getNameTag());

        // Оновлюємо лічильник для наступної перевірки
        previousNotesCount = noteList.size();

        // Використовуємо анімований метод тільки якщо активність вже створена
        if (mActivityBinding.listNotes.getAnimation() != null || mActivityBinding.includeEmpty.emptyViewNote.getAnimation() != null) {
            showEmptyNotesAnimated(!(countNotes >= 1));
        } else {
            showEmptyNotes(!(countNotes >= 1));
        }

        searchController.setDefaultNotesList(noteList);
    }

    @Override
    /// TODO Перенести обробку в презентер
    public void loadingTags(List<Tag> tagList) {
        tagsAdapter.submitList(tagList);

        // Перевіряємо чи є користувацькі теги для відображення
        boolean hasUserTags = false;
        if (tagList != null) {
            for (Tag tag : tagList) {
                if (!SystemTagsManager.isSystemTag(tag)) {
                    hasUserTags = true;
                    break;
                }
            }
        }
        // Приховуємо або показуємо список тегів залежно від наявності користувацьких тегів
        if (hasUserTags) {
            mActivityBinding.listTags.setVisibility(VISIBLE);
        } else {
            mActivityBinding.listTags.setVisibility(View.GONE);
        }

        int countNotes = mNoteAdapter.setNameTagsHidden(Objects.requireNonNull(tagList), tagsAdapter.getTagSelected() == null ? "allNotes" : tagsAdapter.getTagSelected().getNameTag());

        if (mActivityBinding.listNotes.getAnimation() != null || mActivityBinding.includeEmpty.emptyViewNote.getAnimation() != null) {
            showEmptyNotesAnimated(!(countNotes >= 1));
        } else {
            showEmptyNotes(!(countNotes >= 1));
        }
    }

    private void showEmptyNotes(boolean flag) {
        mActivityBinding.setEmptyNotes(flag);
        if (getResources().getDisplayMetrics().density < 2.2)
            mActivityBinding.includeEmpty.imageEmpty.setVisibility(View.GONE);
        mActivityBinding.includeEmpty.emptyViewNote.setVisibility(flag ? VISIBLE : View.GONE);

        // Управління поведінкою AppBar залежно від наявності нотаток
        setAppBarScrollBehavior(!flag);

        if (flag) {
            Tag selectedTag = tagsAdapter.getTagSelected();
            if (selectedTag != null && selectedTag.getSystemAction() != 2 && !selectedTag.getNameTag().equals("allNotes")) {
                // Якщо вибраний конкретний тег (не "Всі нотатки")
                mActivityBinding.includeEmpty.emptyNotesText.setText(getString(R.string.emptyNotesForTag, selectedTag.getNameTag()));
            } else {
                // Якщо вибрано "Всі нотатки" або немає тегу
                mActivityBinding.includeEmpty.emptyNotesText.setText(getString(R.string.emptyNotes));
            }
        }
    }

    private void showEmptyNotesAnimated(boolean flag) {
        mActivityBinding.setEmptyNotes(flag);
        if (getResources().getDisplayMetrics().density < 2.2)
            mActivityBinding.includeEmpty.imageEmpty.setVisibility(View.GONE);

        // Управління поведінкою AppBar залежно від наявності нотаток
        setAppBarScrollBehavior(!flag);

        if (flag) {
            Tag selectedTag = tagsAdapter.getTagSelected();
            if (selectedTag != null && selectedTag.getSystemAction() != 2 && !selectedTag.getNameTag().equals("allNotes")) {
                mActivityBinding.includeEmpty.emptyNotesText.setText(getString(R.string.emptyNotesForTag, selectedTag.getNameTag()));
            } else {
                mActivityBinding.includeEmpty.emptyNotesText.setText(getString(R.string.emptyNotes));
            }

            // Використовуємо ViewPropertyAnimator для кращої продуктивності
            mActivityBinding.includeEmpty.emptyViewNote.setVisibility(VISIBLE);
            mActivityBinding.includeEmpty.emptyViewNote.setAlpha(0f);
            mActivityBinding.includeEmpty.emptyViewNote.animate().alpha(1f).setDuration(300).start();
        } else {
            mActivityBinding.includeEmpty.emptyViewNote.setVisibility(View.GONE);
            mActivityBinding.listNotes.setVisibility(VISIBLE);
            mActivityBinding.listNotes.setAlpha(0f);
            mActivityBinding.listNotes.animate().alpha(1f).setDuration(300).start();
        }
    }

    @Override
    public void actionStartNote(Note note, int position) {
        selectItemAction(note, position, true);
    }

    @Override
    public void openCopyNote(long idNote) {
        new NoteNavigator(this, themePreferencesCache)
                .openNote(idNote, false, "",
                        null, String.valueOf(idNote), false);


    }

    @Override
    public void callbackDeleteNote(Note mNote) {
        mainPresenter.setBackupDeleteNote(mNote);
        snackBarRestoreNote();
    }

    public void openNoteEdit(Note note, View view) {
        new NoteNavigator(this, themePreferencesCache)
                .openNote(note, false, "",
                        view, String.valueOf(note.getId()));
    }


    @Override
    public void openNewNoteWithId(long id) {
        Tag tagSelected = tagsAdapter.getTagSelected();
        String tagName = tagSelected == null
                ? ""
                : tagSelected.getSystemAction() == 2
                ? ""
                : tagSelected.getNameTag();

        new NoteNavigator(this, themePreferencesCache)
                .openNote(id, true, tagName,
                        mActivityBinding.newNotesButton, NameTransition.fabTransaction, false);

    }


    @Override
    public void choiceTagDialog(Tag tag, View mView) {
        new PopupWindowsTag(getLayoutInflater(), mView, tag, new PopupWindowsTagOnClickListener() {
            @Override
            public void deleteTag() {
                if (tagsAdapter.getTagSelected() == tag)
                    selectTagUser(tagsAdapter.getTagForName("allNotes"));
                new Handler(Looper.getMainLooper()).postDelayed(() -> mainPresenter.deleteTag(tag), 700);


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
        if (actionUtils.getAction()) {
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
        mActivityBinding.newNotesButton.setVisibility(VISIBLE);
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
        List<Note> selectedNotes = noteActionTool.getArrayChecked();
        if (!selectedNotes.isEmpty()) {
            // Create a copy of the list to avoid issues with clearing
            List<Note> notesCopy = new ArrayList<>(selectedNotes);
            ShareOptionsDialog shareDialog = new ShareOptionsDialog(notesCopy);
            shareDialog.show(getSupportFragmentManager(), "ShareOptionsDialog");

            // Close action panel after dialog is shown
            actionUtils.closeActionPanel();
        } else {
            Log.w("MainActivity", "No notes selected for sharing");
            actionUtils.closeActionPanel();
        }
    }

    @Override
    public void selectItemAction(Note note, int position, boolean payloads) {

        if (note.getChecked()) {
            note.setChecked(false);
            if (!noteActionTool.isCheckedItemFalse(note)) actionUtils.closeActionPanel();
        } else {
            noteActionTool.isCheckedItem(note);
            note.setChecked(true);
        }

        actionUtils.manageActionPanel(noteActionTool.getCountCheckedItem());
        if (payloads) mNoteAdapter.notifyItemChanged(position, 22);
        else mNoteAdapter.notifyItemChanged(position);
    }

    @Override
    public void toolCleanChecked() {
        noteActionTool.checkedClean();
    }

    @Override
    public void selectTagUser(int position) {
        hideCurrentContent(() -> {
            tagsAdapter.chooseTag(position);

            int noteCount = mNoteAdapter.filter(tagsAdapter.getTagSelected() == null ? "allNotes" : tagsAdapter.getTagSelected().getNameTag(), false);

            // Оновлюємо AppBar поведінку залежно від кількості нотаток
            setAppBarScrollBehavior(noteCount >= 1);

            showNewContent(!(noteCount >= 1));
        });
    }

    private void hideCurrentContent(Runnable onComplete) {
        if (mActivityBinding.listNotes.getVisibility() == VISIBLE) {
            // Використовуємо ViewPropertyAnimator замість Animation
            mActivityBinding.listNotes.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                mActivityBinding.listNotes.setVisibility(View.INVISIBLE);
                mActivityBinding.listNotes.setAlpha(1f); // Скидаємо alpha
                // Очищаємо список після приховування, щоб запобігти миготінню
                mNoteAdapter.clearList();
                if (onComplete != null) {
                    onComplete.run();
                }
            }).start();
        } else if (mActivityBinding.includeEmpty.emptyViewNote.getVisibility() == VISIBLE) {
            // Використовуємо ViewPropertyAnimator замість Animation
            mActivityBinding.includeEmpty.emptyViewNote.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                mActivityBinding.includeEmpty.emptyViewNote.setVisibility(View.INVISIBLE);
                mActivityBinding.includeEmpty.emptyViewNote.setAlpha(1f); // Скидаємо alpha
                if (onComplete != null) {
                    onComplete.run();
                }
            }).start();
        } else {
            mNoteAdapter.clearList();
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    private void showNewContent(boolean showEmpty) {
        // Невелика затримка для плавності переходу
        mActivityBinding.getRoot().postDelayed(() -> {
            if (!showEmpty) {
                // Оновлюємо список нотаток перед показом
                mNoteAdapter.filter(tagsAdapter.getTagSelected() == null ? "allNotes" : tagsAdapter.getTagSelected().getNameTag(), true);
            }
            showEmptyNotesAnimated(showEmpty);
        }, 50);
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

        // Очистка ActionPanel ресурсов
        if (actionUtils != null) {
            actionUtils.cleanup();
        }
        if (noteActionTool != null) {
            noteActionTool.cleanup();
        }

        mNoteAdapter = null;
        tagsAdapter = null;
        searchNotesAdapter = null;
    }

    /**
     * Управління поведінкою AppBar - дозволяє або забороняє прокручування
     *
     * @param canScroll true - дозволити прокручування, false - заборонити
     */
    private void setAppBarScrollBehavior(boolean canScroll) {
        AppBarLayout.LayoutParams params = (AppBarLayout.LayoutParams) mActivityBinding.actionSearch.getLayoutParams();

        if (canScroll) {
            // Дозволяємо прокручування - встановлюємо scroll|enterAlways
            params.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL | AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS);
        } else {
            // Забороняємо прокручування - прибираємо всі scroll flags
            params.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL);
            // Розширюємо AppBar до повного розміру
            mActivityBinding.appBarMainActivity.setExpanded(true, true);
        }

        mActivityBinding.actionSearch.setLayoutParams(params);
    }

    @Override
    public void redrawActivity(int themeStyle) {
        super.redrawActivity(themeStyle);
        selectTheme();
        recreate();
    }

    @Override
    public void openChangelogActivity() {
        changelogLauncher.launch(new Intent(this, ChangelogActivity.class));

    }


    /**
     * Налаштування Navigation Drawer
     */
    private void setupNavigationDrawer() {
        drawerLayout = mActivityBinding.drawerLayout;
        navigationView = mActivityBinding.navigationView;

        // Налаштовуємо burger іконку для відкриття drawer
        mActivityBinding.actionSearch.setNavigationOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        // Налаштовуємо menu click listener для Navigation Drawer
        navigationView.setNavigationItemSelectedListener(this::onNavigationItemSelected);

        // Налаштування listeners для header Navigation Drawer
        setupNavigationHeaderListeners();
    }

    /**
     * Обробка кліків по пунктах меню Navigation Drawer
     */
    private boolean onNavigationItemSelected(@NonNull MenuItem item) {
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * Налаштування listeners для header Navigation Drawer
     */
    private void setupNavigationHeaderListeners() {
        View headerView = navigationView.getHeaderView(0);

        drawerLayout.closeDrawer(GravityCompat.START);
        // Основні кнопки
        headerView.findViewById(R.id.nav_tags).setOnClickListener(v -> new Handler(Looper.getMainLooper()).postDelayed(() -> startTagsActivity.launch(new Intent(this, TagsActivity.class)), 100));

        headerView.findViewById(R.id.nav_trash).setOnClickListener(v -> new Handler(Looper.getMainLooper()).postDelayed(() -> startActivity(new Intent(this, TrashActivity.class)), 100));

        // Налаштування / управління
        headerView.findViewById(R.id.nav_settings).setOnClickListener(v -> new Handler(Looper.getMainLooper()).postDelayed(() -> themeUpdateListener.launch(new Intent(this, SettingsActivity.class)), 100));

        headerView.findViewById(R.id.nav_backups).setOnClickListener(v -> new Handler(Looper.getMainLooper()).postDelayed(() -> themeUpdateListener.launch(new Intent(this, BackupActivity.class)), 100));

        // About з описом
        headerView.findViewById(R.id.nav_about).setOnClickListener(v -> new Handler(Looper.getMainLooper()).postDelayed(() -> startActivity(new Intent(this, AboutActivity.class)), 100));

        // Support кнопка
        View navSupport = headerView.findViewById(R.id.nav_support);
        if (navSupport != null) {
            navSupport.setOnClickListener(v -> new Handler(Looper.getMainLooper()).postDelayed(() -> startActivity(new Intent(this, SupportActivity.class)), 100));
        }

        // Нова версія додатку
        appUpdateController.bindHeaderNewVersion(headerView);

    }

    /**
     * Налаштовує Edge-to-Edge для DrawerLayout
     */
    private void setupEdgeToEdgeForDrawer() {
        ViewCompat.setOnApplyWindowInsetsListener(mActivityBinding.getRoot(), (v, insets) -> {
            ViewCompat.setOnApplyWindowInsetsListener(mActivityBinding.activityMain, (mainView, mainInsets) -> {
                Insets systemBars = mainInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                mainView.setPadding(0, systemBars.top, 0, systemBars.bottom);
                return WindowInsetsCompat.CONSUMED;
            });

            return insets;
        });
    }


}
