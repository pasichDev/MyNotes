package com.pasich.mynotes.ui.view.activity;

import static com.pasich.mynotes.utils.constants.settings.TagSettings.MAX_TAG_COUNT;

import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.tasks.Task;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.navigation.NavigationView;
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
import com.pasich.mynotes.databinding.ViewLoginPageBinding;
import com.pasich.mynotes.ui.contract.MainContract;
import com.pasich.mynotes.ui.presenter.MainPresenter;
import com.pasich.mynotes.ui.view.dialogs.MoreNoteDialog;
import com.pasich.mynotes.ui.view.dialogs.main.DeleteTagDialog;
import com.pasich.mynotes.ui.view.dialogs.main.NameTagDialog;
import com.pasich.mynotes.ui.view.dialogs.main.SortDialog;
import com.pasich.mynotes.ui.view.dialogs.ShareOptionsDialog;
import com.pasich.mynotes.ui.view.dialogs.main.popupWindowsTag.PopupWindowsTag;
import com.pasich.mynotes.ui.view.dialogs.main.popupWindowsTag.PopupWindowsTagOnClickListener;
import com.pasich.mynotes.utils.actionPanel.ActionUtils;
import com.pasich.mynotes.utils.actionPanel.interfaces.ManagerViewAction;
import com.pasich.mynotes.utils.actionPanel.tool.NoteActionTool;
import com.pasich.mynotes.utils.adapters.baseGenericAdapter.OnItemClickListener;
import com.pasich.mynotes.utils.adapters.notes.NoteAdapter;
import com.pasich.mynotes.utils.adapters.searchAdapter.SearchNotesAdapter;
import com.pasich.mynotes.utils.adapters.tagAdapter.OnItemClickListenerTag;
import com.pasich.mynotes.utils.adapters.tagAdapter.TagsAdapter;
import com.pasich.mynotes.utils.backup.CloudAuthHelper;
import com.pasich.mynotes.utils.backup.CloudCacheHelper;
import com.pasich.mynotes.utils.constants.DriveScope;
import com.pasich.mynotes.utils.constants.NameTransition;
import com.pasich.mynotes.utils.constants.SnackBarInfo;
import com.pasich.mynotes.utils.constants.settings.BackupPreferences;
import com.pasich.mynotes.utils.recycler.SpacesItemDecoration;
import com.pasich.mynotes.utils.recycler.SwipeToListNotesCallback;
import com.pasich.mynotes.utils.tool.FormatListTool;
import com.pasich.mynotes.utils.UpdateChecker;
import com.pasich.mynotes.utils.managers.SystemTagsManager;
import com.preference.PowerPreference;
import com.preference.Preference;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends BaseActivity implements MainContract.view, ManagerViewAction<Note> {

    final private ActivityResultLauncher<Intent> startSettingsActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == 11) {
                    assert data != null;
                    if (data.getBooleanExtra("updateThemeMode", false)) {
                        recreate();
                    } else {
                        this.redrawActivity(data.getIntExtra("updateThemeStyle", 0));
                    }
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
    
    @Inject
    UpdateChecker updateChecker;

    // Navigation Drawer variables
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private int pendingNavigationMenuItemId = -1;

    // Google Sign-In variables (moved from AboutDialog)
    @Inject
    public GoogleSignInClient googleSignInClient;
    @Inject
    public CloudCacheHelper cloudCacheHelper;
    @Inject
    public CloudAuthHelper cloudAuthHelper;

    private static final int REQUEST_UPDATE = 100;
    private AppUpdateManager appUpdateManager;
    private int previousNotesCount = 0;

    // Google Sign-In launcher
    final private ActivityResultLauncher<Intent> startAuthIntent = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    cloudAuthHelper.getResultAuth(result.getData())
                            .addOnFailureListener((GoogleSignInAccount) -> 
                                    onInfoSnack(R.string.errorAuth, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG))
                            .addOnSuccessListener((GoogleSignInAccount) -> {
                                cloudCacheHelper.update(GoogleSignInAccount, 
                                       GoogleSignIn.hasPermissions(GoogleSignInAccount,
                                               DriveScope.ACCESS_DRIVE_SCOPE), true);
                                loadingDataUser(true);
                            });
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

        setupEdgeToEdgeForDrawer();
        mainPresenter.attachView(this);
        mainPresenter.viewIsReady();
        mActivityBinding.setPresenter((MainPresenter) mainPresenter);

        // Ініціалізуємо перевірку версій
        updateChecker.initializeVersionCheck();
        actionUtils.setMangerView(mActivityBinding.getRoot());

        // Ініціалізуйте AppUpdateManager
        appUpdateManager = AppUpdateManagerFactory.create(this);

        // Перевірте доступність оновлення
        checkForUpdate();

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
        }).addOnFailureListener(e -> Log.d("AppUpdate", "Error checking for updates: " + e.getMessage()));

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
        mActivityBinding.actionSearch.setOnClickListener(v -> mActivityBinding.searchView.show());
        
        mActivityBinding.searchView.addTransitionListener(
                (searchView, previousState, newState) -> {
                    if (newState == SearchView.TransitionState.SHOWING) {
                        mActivityBinding.listNotes.setNestedScrollingEnabled(false);
                        ensureSearchViewFullScreen();
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
                        if (!actionUtils.getAction())
                            new SortDialog().show(getSupportFragmentManager(), "sortDialog");
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
                    // Якщо тег вже вибраний і це не спеціальний тег для додавання та не changelog
                    if (clickedTag.getSelected() && !SystemTagsManager.isAddTag(clickedTag) && !SystemTagsManager.isChangeLogTag(clickedTag)) {
                        assert mActivityBinding.listTags.getLayoutManager() != null;
                        View tagView = mActivityBinding.listTags.getLayoutManager().findViewByPosition(position);
                        Animation shake = AnimationUtils
                                  .loadAnimation(MainActivity.this, R.anim.shake_gentle);
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
                    openNoteEdit(model.id,
                            (MaterialCardView) staggeredGridLayoutManager.findViewByPosition(position));
                } else
                    selectItemAction(model, position, true);

            }

            @Override
            public void onLongClick(int position, Note model) {
                if (!actionUtils.getAction())
                    choiceNoteDialog(model, position);
            }

        });

    }

    @Override
    public void settingsSearchView() {
        formatList.init(mActivityBinding.actionSearch.getMenu().findItem(R.id.format));
        setupSearchBarAndView();
    }
    
    private void setupSearchBarAndView() {
        try {
            mActivityBinding.actionSearch.setHint(getString(R.string.search));

            // Програмно налаштовуємо зв'язок без layout_anchor для повноекранного режиму
            mActivityBinding.searchView.setupWithSearchBar(mActivityBinding.actionSearch);

            // Переконуємося, що SearchView займає весь екран
            ViewGroup.LayoutParams params = mActivityBinding.searchView.getLayoutParams();
            if (params instanceof CoordinatorLayout.LayoutParams coordinatorParams) {
                // Видаляємо будь-які anchor behavior, щоб SearchView був повноекранним
                coordinatorParams.setAnchorId(View.NO_ID);
                coordinatorParams.setBehavior(null);

                mActivityBinding.searchView.setLayoutParams(coordinatorParams);
            }
        } catch (Exception e) {
            Log.e("SearchSetup", "Error setting up SearchBar and SearchView: " + e.getMessage());
        }
    }

    /**
     * Забезпечує повноекранний режим для SearchView
     */
    private void ensureSearchViewFullScreen() {
        ViewGroup.LayoutParams params = mActivityBinding.searchView.getLayoutParams();
        if (params != null) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            mActivityBinding.searchView.setLayoutParams(params);
            mActivityBinding.searchView.requestLayout();
        }
    }

    @Override
    public void settingsLists() {
        mActivityBinding.listTags.addItemDecoration(itemDecorationTags);
        mActivityBinding.listTags.setLayoutManager(mLinearLayoutManager);
        mActivityBinding.listTags.setAdapter(tagsAdapter);
        mActivityBinding.listNotes.addItemDecoration(itemDecorationNotes);
        mActivityBinding.listNotes.setLayoutManager(staggeredGridLayoutManager);
        mActivityBinding.listNotes.setAdapter(mNoteAdapter);

        mActivityBinding.listNotes.setItemAnimator(new androidx.recyclerview.widget.DefaultItemAnimator());
        mActivityBinding.resultsSearchList
                .setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        mActivityBinding.resultsSearchList.addItemDecoration(itemDecorationNotes);
        mActivityBinding.resultsSearchList.setAdapter(searchNotesAdapter);
        
        // Додаткові налаштування для результатів пошуку в повноекранному SearchView
        mActivityBinding.resultsSearchList.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mActivityBinding.resultsSearchList.setNestedScrollingEnabled(true);
        new ItemTouchHelper(new SwipeToListNotesCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean isItemViewSwipeEnabled() {
                return !actionUtils.getAction() && mainPresenter.getDataManager().getFormatCount() == 1;
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
        // Перевіряємо чи додалася нова нотатка
        boolean isNewNoteAdded = noteList.size() > previousNotesCount;
        if (isNewNoteAdded) {
            // Встановлюємо флаг для прокручування до верху
            mNoteAdapter.setScrollToTopOnNextUpdate(true);
        }
        
        int countNotes = mNoteAdapter.sortList(noteList, mainPresenter.getSortParam(),
                tagsAdapter.getTagSelected() == null ? "allNotes" : tagsAdapter.getTagSelected().getNameTag());

        // Оновлюємо лічильник для наступної перевірки
        previousNotesCount = noteList.size();

        // Використовуємо анімований метод тільки якщо активність вже створена
        if (mActivityBinding.listNotes.getAnimation() != null ||
                mActivityBinding.includeEmpty.emptyViewNote.getAnimation() != null) {
            showEmptyNotesAnimated(!(countNotes >= 1));
        } else {
            showEmptyNotes(!(countNotes >= 1));
        }

        searchNotesAdapter.setDefaultListNotes(noteList);
    }

    @Override
    public void loadingTags(List<Tag> tagList) {
        tagsAdapter.submitList(tagList);
        int countNotes = mNoteAdapter.setNameTagsHidden(tagList,
                tagsAdapter.getTagSelected() == null ? "allNotes" : tagsAdapter.getTagSelected().getNameTag());

        if (mActivityBinding.listNotes.getAnimation() != null ||
                mActivityBinding.includeEmpty.emptyViewNote.getAnimation() != null) {
            showEmptyNotesAnimated(!(countNotes >= 1));
        } else {
            showEmptyNotes(!(countNotes >= 1));
        }
    }

    private void showEmptyNotes(boolean flag) {
        mActivityBinding.setEmptyNotes(flag);
        if (getResources().getDisplayMetrics().density < 2.2)
            mActivityBinding.includeEmpty.imageEmpty.setVisibility(View.GONE);
        mActivityBinding.includeEmpty.emptyViewNote.setVisibility(flag ? View.VISIBLE : View.GONE);

        // Управління поведінкою AppBar залежно від наявності нотаток
        setAppBarScrollBehavior(!flag);

        if (flag) {
            Tag selectedTag = tagsAdapter.getTagSelected();
            if (selectedTag != null && selectedTag.getSystemAction() != 2
                    && !selectedTag.getNameTag().equals("allNotes")) {
                // Якщо вибраний конкретний тег (не "Всі нотатки")
                mActivityBinding.includeEmpty.emptyNotesText.setText(
                        getString(R.string.emptyNotesForTag, selectedTag.getNameTag()));
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
            if (selectedTag != null && selectedTag.getSystemAction() != 2
                    && !selectedTag.getNameTag().equals("allNotes")) {
                mActivityBinding.includeEmpty.emptyNotesText.setText(
                        getString(R.string.emptyNotesForTag, selectedTag.getNameTag()));
            } else {
                mActivityBinding.includeEmpty.emptyNotesText.setText(getString(R.string.emptyNotes));
            }

            // Використовуємо ViewPropertyAnimator для кращої продуктивності
            mActivityBinding.includeEmpty.emptyViewNote.setVisibility(View.VISIBLE);
            mActivityBinding.includeEmpty.emptyViewNote.setAlpha(0f);
            mActivityBinding.includeEmpty.emptyViewNote.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start();
        } else {
            mActivityBinding.includeEmpty.emptyViewNote.setVisibility(View.GONE);
            mActivityBinding.listNotes.setVisibility(View.VISIBLE);
            mActivityBinding.listNotes.setAlpha(0f);
            mActivityBinding.listNotes.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start();
        }
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
        hideCurrentContent(() -> {
            tagsAdapter.chooseTag(position);

            int noteCount = mNoteAdapter.filter(
                    tagsAdapter.getTagSelected() == null ? "allNotes" : tagsAdapter.getTagSelected().getNameTag(),
                    false);

            // Оновлюємо AppBar поведінку залежно від кількості нотаток
            setAppBarScrollBehavior(noteCount >= 1);
            
            showNewContent(!(noteCount >= 1));
        });
    }

    private void hideCurrentContent(Runnable onComplete) {
        if (mActivityBinding.listNotes.getVisibility() == View.VISIBLE) {
            // Використовуємо ViewPropertyAnimator замість Animation
            mActivityBinding.listNotes.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> {
                        mActivityBinding.listNotes.setVisibility(View.INVISIBLE);
                        mActivityBinding.listNotes.setAlpha(1f); // Скидаємо alpha
                        // Очищаємо список після приховування, щоб запобігти миготінню
                        mNoteAdapter.clearList();
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    })
                    .start();
        } else if (mActivityBinding.includeEmpty.emptyViewNote.getVisibility() == View.VISIBLE) {
            // Використовуємо ViewPropertyAnimator замість Animation
            mActivityBinding.includeEmpty.emptyViewNote.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> {
                        mActivityBinding.includeEmpty.emptyViewNote.setVisibility(View.INVISIBLE);
                        mActivityBinding.includeEmpty.emptyViewNote.setAlpha(1f); // Скидаємо alpha
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    })
                    .start();
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
                mNoteAdapter.filter(
                        tagsAdapter.getTagSelected() == null ? "allNotes" : tagsAdapter.getTagSelected().getNameTag(),
                        true);
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
     * @param canScroll true - дозволити прокручування, false - заборонити
     */
    private void setAppBarScrollBehavior(boolean canScroll) {
        AppBarLayout.LayoutParams params =
                (AppBarLayout.LayoutParams) mActivityBinding.actionSearch.getLayoutParams();

        if (canScroll) {
            // Дозволяємо прокручування - встановлюємо scroll|enterAlways
            params.setScrollFlags(
                AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL |
                AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS);
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
        Intent intent = new Intent(this, ChangelogActivity.class);
        startActivityForResult(intent, REQUEST_CODE_CHANGELOG);
    }

    private static final int REQUEST_CODE_CHANGELOG = 1001;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_CHANGELOG && resultCode == RESULT_OK) {
            // Користувач ознайомився з оновленням, оновлюємо список тегів
            mainPresenter.loadingData();
        }
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

        // Додаємо DrawerListener для обробки завершення анімації
        drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
            }

            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                // Коли drawer закрився, відкриваємо активність
                if (pendingNavigationMenuItemId != -1) {
                    // Невелика затримка для плавності
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        handleNavigationItemAction(pendingNavigationMenuItemId);
                        pendingNavigationMenuItemId = -1; // Скидаємо
                    }, 50); // Мінімальна затримка
                }
            }

            @Override
            public void onDrawerStateChanged(int newState) {
            }
        });

        // Ініціалізуємо дані користувача в header
        initNavigationHeader();
    }

    /**
     * Обробка кліків по пунктах меню Navigation Drawer
     */
    private boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Зберігаємо ID обраного пункту та закриваємо drawer
        pendingNavigationMenuItemId = item.getItemId();
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * Виконання дії після закриття drawer'а
     */
    private void handleNavigationItemAction(int itemId) {
        if (itemId == R.id.nav_trash) {
            startActivity(new Intent(this, TrashActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        } else if (itemId == R.id.nav_settings) {
            startSettingsActivity.launch(new Intent(this, SettingsActivity.class));
        } else if (itemId == R.id.nav_backups) {
            startActivity(new Intent(this, BackupActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        } else if (itemId == R.id.nav_help) {
            startActivity(new Intent(this, HelpActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        } else if (itemId == R.id.nav_about) {
            startActivity(new Intent(this, AboutActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        }
    }

    /**
     * Ініціалізація header Navigation Drawer з інформацією користувача
     */
    private void initNavigationHeader() {
        View headerView = navigationView.getHeaderView(0);
        com.pasich.mynotes.databinding.ViewLoginPageBinding loginPageBinding = 
                com.pasich.mynotes.databinding.ViewLoginPageBinding.bind(headerView.findViewById(R.id.login_page));

        // Налаштовуємо видимість елементів для Google Play Market
        loginPageBinding.viewLoginRoot.setVisibility(cloudCacheHelper.isInstallPlayMarket() ? View.VISIBLE : View.GONE);
        
        // Завантажуємо дані користувача
        loadingDataUser(cloudCacheHelper.isAuth(), loginPageBinding);

        // Налаштовуємо listeners
        setupNavigationHeaderListeners(loginPageBinding);
    }

    /**
     * Налаштування listeners для header Navigation Drawer
     */
    private void setupNavigationHeaderListeners(com.pasich.mynotes.databinding.ViewLoginPageBinding loginPageBinding) {
        loginPageBinding.exitUser.setOnClickListener(v -> signOut(loginPageBinding));
        loginPageBinding.loginUser.setOnClickListener(v -> startAuthIntent.launch(googleSignInClient.getSignInIntent()));
        
        // Отримуємо доступ до nav_support через headerView
        navigationView.getHeaderView(0).findViewById(R.id.nav_support).setOnClickListener(v -> {
            // Закриваємо drawer і відкриваємо SupportActivity
            drawerLayout.closeDrawer(GravityCompat.START);
            new Handler(Looper.getMainLooper()).postDelayed(() -> startActivity(new Intent(this, SupportActivity.class),
                    ActivityOptions.makeSceneTransitionAnimation(this).toBundle()), 100);
        });
    }

    /**
     * Завантаження даних користувача
     * TODO Update logic
     */
    private void loadingDataUser(boolean isAuth, ViewLoginPageBinding loginPageBinding) {
        if (isAuth) {
            String nameUser = cloudCacheHelper.getGoogleSignInAccount().getDisplayName();
            loginPageBinding.nameUser.setText(nameUser);
            loginPageBinding.emailUSer.setText(cloudCacheHelper.getGoogleSignInAccount().getEmail());
            Glide.with(this)
                    .load(cloudCacheHelper.getGoogleSignInAccount().getPhotoUrl())
                    .placeholder(R.drawable.ic_no_avatar)
                    .into(loginPageBinding.userAvatar);
            loginPageBinding.loginUser.setVisibility(View.GONE);
            loginPageBinding.loginPageRoot.setVisibility(View.VISIBLE);
        } else {
            loginPageBinding.loginPageRoot.setVisibility(View.GONE);
            loginPageBinding.loginUser.setVisibility(View.VISIBLE);
        }
    }

    /**
     *
     */
    private void loadingDataUser(boolean isAuth) {
        if (navigationView != null) {
            View headerView = navigationView.getHeaderView(0);
            ViewLoginPageBinding loginPageBinding =
                   ViewLoginPageBinding.bind(headerView.findViewById(R.id.login_page));
            loadingDataUser(isAuth, loginPageBinding);
        }
    }

    /**
     * Вихід з Google акаунта
     * TODO Update logic
     */
    private void signOut(com.pasich.mynotes.databinding.ViewLoginPageBinding loginPageBinding) {
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            // Оновлюємо UI в main потоці
            loginPageBinding.loginUser.setVisibility(View.VISIBLE);
            loginPageBinding.loginPageRoot.setVisibility(View.GONE);
            // Виконуємо операції з preferences асинхронно
            new Thread(() -> {
                try {
                    final Preference preference = PowerPreference.getFileByName(
                            com.pasich.mynotes.utils.constants.settings.BackupPreferences.FIlE_NAME_PREFERENCE_BACKUP);
                    preference.removeAsync(BackupPreferences.ARGUMENT_AUTO_BACKUP_CLOUD);
                    preference.removeAsync(BackupPreferences.ARGUMENT_LAST_BACKUP_ID);
                    preference.removeAsync(BackupPreferences.ARGUMENT_LAST_BACKUP_TIME);
                    cloudCacheHelper.clean();
                } catch (Exception e) {
                    Log.e("MainActivity", "Error during sign out preferences cleanup", e);
                }
            }).start();
        });
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
