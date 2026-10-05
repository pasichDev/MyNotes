package com.pasich.mynotes.ui.view.activity;

import static com.pasich.mynotes.utils.navigation.ActivityResultKeys.EXTRA_UPDATE_THEME_STYLE;
import static com.pasich.mynotes.utils.navigation.ActivityResultKeys.RESULT_CODE_THEME_UPDATE;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.cache.AppPreferencesCache;
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
import com.pasich.mynotes.ui.view.dialogs.main.SingleTagSelectDialog;
import com.pasich.mynotes.ui.view.dialogs.main.ViewOptionsDialog;
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
import com.pasich.mynotes.utils.encly.EnclyMigrationRepository;
import com.pasich.mynotes.utils.managers.SystemTagsManager;
import com.pasich.mynotes.utils.navigation.NoteNavigator;
import com.pasich.mynotes.utils.recycler.NoteDragCallback;
import com.pasich.mynotes.utils.recycler.NoteListTransition;
import com.pasich.mynotes.utils.recycler.NotesGridLayoutManager;
import com.pasich.mynotes.utils.recycler.NotesItemAnimator;
import com.pasich.mynotes.utils.recycler.SpacesItemDecoration;
import com.pasich.mynotes.utils.reminder.ReminderManager;
import com.pasich.mynotes.utils.reminder.ReminderRescheduler;
import com.pasich.mynotes.utils.search.SearchHintFitter;
import com.pasich.mynotes.utils.search.SearchHit;
import com.pasich.mynotes.utils.tool.FormatListTool;
import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;

/** Main screen showing the notes list and tag navigation. */
@AndroidEntryPoint
public class MainActivity extends BaseActivity
        implements MainContract.view, ViewOptionsDialog.Listener {

    private final ActivityResultLauncher<Intent> themeUpdateListener =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        Intent data = result.getData();
                        if (result.getResultCode() == RESULT_CODE_THEME_UPDATE) {
                            assert data != null;
                            if (data.hasExtra(EXTRA_UPDATE_THEME_STYLE)) {
                                this.redrawActivity();
                            }
                        }
                    });
    public ActivityMainBinding mActivityBinding;
    @Inject public MainContract.presenter mainPresenter;
    @Inject public FormatListTool formatList;
    @Inject public TagsAdapter tagsAdapter;
    @Inject public NotesGridLayoutManager gridLayoutManager;

    @Inject public NoteAdapter mNoteAdapter;

    @Named("TagsItemSpaceDecoration")
    @Inject
    public SpacesItemDecoration itemDecorationTags;

    @Named("NotesItemSpaceDecoration")
    @Inject
    public SpacesItemDecoration itemDecorationNotes;

    @Inject public LinearLayoutManager mLinearLayoutManager;
    @Inject SearchNotesAdapter searchNotesAdapter;
    @Inject UpdateChecker updateChecker;
    @Inject ThemePreferencesCache themePreferencesCache;
    @Inject EnclyMigrationRepository enclyMigrationRepository;
    @Inject AppPreferencesCache appPreferencesCache;
    @Inject ReminderRescheduler reminderRescheduler;

    /** The exact-alarm hint is offered once per app process, not on every rotation. */
    private static boolean exactAlarmHintOffered;

    private Disposable reminderCheck;
    private SearchController searchController;
    private AppUpdateController appUpdateController;
    private NavigationController navigationController;
    private final ActivityResultLauncher<Intent> changelogLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            boolean hasNewVersion = updateChecker.hasNewVersion();
                            if (navigationController != null)
                                navigationController.updateNewVersionIndicator(hasNewVersion);
                        }
                    });

    /** Version history opened from a note's menu; a restore is confirmed here. */
    private final ActivityResultLauncher<Intent> versionHistoryLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK
                                || mActivityBinding == null) return;
                        Snackbar snackbar =
                                Snackbar.make(
                                        mActivityBinding.drawerLayout,
                                        R.string.version_restored,
                                        Snackbar.LENGTH_SHORT);
                        snackbar.setAnchorView(mActivityBinding.newNotesButton);
                        snackbar.show();
                    });

    /** How long a return transition from the editor may take (300 ms) plus a margin. */
    private static final long RETURN_SETTLE_MS = 380;

    private static final String TAG = "MainActivity";
    private static final int EXACT_ALARM_HINT_MS = 8000;

    private final Handler settleHandler = new Handler(Looper.getMainLooper());
    private MainRenderListsController mainRenderListsController;

    /** Latest state not applied yet (selection mode, or settling after a return). */
    private MainViewState pendingState;

    private boolean stopped;
    private boolean settling;
    private final Runnable endSettling =
            () -> {
                settling = false;
                applyPendingState();
            };
    private Tag currentSelectedTag = null;
    private List<Tag> currentTags = new ArrayList<>();

    private SelectionController selectionController;

    /** Drives swipes on the list and, in the custom order, drags. */
    private ItemTouchHelper noteTouchHelper;

    /** From the moment a card is picked up until the dropped order is behind the adapter. */
    private boolean dragging;

    /**
     * What the list swap in progress puts on screen once the list has faded out: the newest notes
     * (null when only the layout changes) with their category, and whether the list then starts at
     * the top. A swap requested while another is still fading replaces the pending callback, so
     * everything it has to apply is kept here and read at that moment instead of being captured by
     * each request; a later state or layout change then adds to the swap rather than dropping it.
     */
    @Nullable private List<Note> swapNotes;

    @Nullable private Tag swapTag;
    private boolean swapToTop;

    /** Whether the card picked up has actually moved. */
    private boolean dragMoved;

    /** The note of the card being dragged, -1 when none. */
    private int draggedNoteId = -1;

    /** Set by {@code onSelectedChanged} so a long press can tell that its drag started. */
    private boolean dragStarted;

    /**
     * The note whose menu opens if the card picked up by a long press is let go without moving: in
     * the custom order, press and hold both picks a card up and opens its menu.
     */
    @Nullable private Note pendingMenuNote;

    private int pendingMenuPosition;

    /** The cards' "move up" and "move down" accessibility actions in the custom order. */
    private final NoteAdapter.ReorderListener accessibilityReorder = this::moveByAccessibility;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setExitSharedElementCallback(new MaterialContainerTransformSharedElementCallback());
        getWindow().setSharedElementsUseOverlay(false);
        super.onCreate(savedInstanceState);
        selectTheme();
        mActivityBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mActivityBinding.getRoot());
        setupEdgeToEdgeInsets(mActivityBinding.getRoot());

        selectionController = new SelectionController(mNoteAdapter, mActivityBinding.getRoot());
        mNoteAdapter.setSelectionController(selectionController);
        selectionController.setPanelMode(SelectionController.Mode.NORMAL);
        mainRenderListsController = new MainRenderListsController(mActivityBinding);

        mainPresenter.attachView(this);
        mainPresenter.viewIsReady();
        mActivityBinding.setPresenter((MainPresenter) mainPresenter);

        searchController =
                new SearchController(
                        mActivityBinding,
                        searchNotesAdapter,
                        new SearchController.Listener() {
                            @Override
                            public void onSearchOpen() {
                                mActivityBinding.listNotes.setNestedScrollingEnabled(false);
                                List<Tag> userTags = new ArrayList<>();
                                for (Tag t : currentTags) {
                                    if (!SystemTagsManager.isSystemTag(t)) {
                                        userTags.add(t);
                                    }
                                }
                                searchController.setAvailableTags(userTags);
                            }

                            @Override
                            public void onSearchClose() {
                                mActivityBinding.listNotes.setNestedScrollingEnabled(true);
                            }

                            @Override
                            public void onSearchQuery(String query) {
                                mainPresenter.updateSearchQuery(query);
                            }

                            @Override
                            public void onTagFilterChanged(String tagName) {
                                mainPresenter.updateSearchTagFilter(tagName);
                            }
                        });
        if (savedInstanceState == null) {
            sweepEnclyHandoffFiles();
            rescheduleReminders();
        }

        appUpdateController = new AppUpdateController(this, updateChecker, changelogLauncher);
        appUpdateController.showChangelogIfNeeded();

        navigationController =
                new NavigationController(
                        this,
                        mActivityBinding,
                        themeUpdateListener,
                        appUpdateController,
                        this::finishActivity);
        navigationController.init();
        navigationController.handleShortcuts(getIntent());
    }

    /**
     * Re-arms reminders at app start, which also restores those lost to a force stop, and asks once
     * for "Alarms & reminders" when upcoming reminders can only be armed to ring late.
     */
    private void rescheduleReminders() {
        reminderCheck =
                Single.fromCallable(
                                () -> {
                                    long now = System.currentTimeMillis();
                                    reminderRescheduler.rescheduleAll(now);
                                    return reminderRescheduler.countUpcoming(now);
                                })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                upcoming -> {
                                    if (upcoming > 0
                                            && !exactAlarmHintOffered
                                            && !ReminderManager.canScheduleExact(this)) {
                                        exactAlarmHintOffered = true;
                                        showExactAlarmHint();
                                    }
                                },
                                e -> Log.w(TAG, "rescheduling reminders failed", e));
    }

    private void showExactAlarmHint() {
        Intent settings = ReminderManager.exactAlarmSettingsIntent(this);
        if (settings == null || isFinishing() || mActivityBinding == null) return;
        Snackbar snackbar =
                Snackbar.make(
                        mActivityBinding.drawerLayout,
                        R.string.reminder_exact_alarm_needed,
                        EXACT_ALARM_HINT_MS);
        snackbar.setAction(
                R.string.reminder_exact_alarm_allow,
                v -> {
                    try {
                        startActivity(settings);
                    } catch (android.content.ActivityNotFoundException e) {
                        Log.w(TAG, "no exact alarm settings screen", e);
                    }
                });
        snackbar.setAnchorView(mActivityBinding.newNotesButton);
        snackbar.show();
    }

    /**
     * A hand-off to Encly is a plain-text copy of every note. It is deleted when Encly answers;
     * this sweeps one left behind when the process died before that.
     */
    private void sweepEnclyHandoffFiles() {
        Completable.fromAction(enclyMigrationRepository::clearArchives)
                .subscribeOn(Schedulers.io())
                .onErrorComplete()
                .subscribe();
    }

    /**
     * Receives every new state. It is applied at once unless the user is selecting notes (the
     * selection is kept in step with the new list and the state waits for selection to end) or the
     * screen has just come back from the editor (the state waits for the return transition).
     */
    @Override
    public void render(MainViewState state) {
        pendingState = state;
        if (selectionController.isInSelectionMode()) {
            List<Integer> ids = new ArrayList<>(state.notes().size());
            for (Note n : state.notes()) ids.add(n.getId());
            // May end selection mode, which applies the pending state.
            selectionController.retainOnly(ids);
            return;
        }
        applyPendingState();
    }

    private void applyPendingState() {
        MainViewState state = pendingState;
        if (state == null || settling || dragging || selectionController.isInSelectionMode())
            return;
        pendingState = null;

        currentSelectedTag = state.selectedTag();
        currentTags = state.tags();
        renderNotes(state.notes(), state.selectedTag(), state.uiEvent());
        renderTags(state.tags());
    }

    /** Item and visibility animations only run while the user is actually looking at the list. */
    private boolean isListInteractive() {
        return !stopped && !settling;
    }

    private void renderTags(List<Tag> tags) {
        mainRenderListsController.renderListTags(tags, isListInteractive());
        tagsAdapter.submitList(tags);
    }

    private void renderNotes(List<Note> notes, Tag selectedTag, UiEvent event) {
        boolean animate = isListInteractive();
        List<Note> previous = mNoteAdapter.getCurrentList();
        int previousTopId = previous.isEmpty() ? -1 : previous.get(0).getId();
        boolean topChanged = !notes.isEmpty() && notes.get(0).getId() != previousTopId;
        boolean datasetChanged = event == UiEvent.SORT_CHANGED || event == UiEvent.TAG_CHANGED;
        // A state that arrives while a swap is still fading joins it: submitted on its own, it
        // would race the swap's pending list and the older one could land last.
        boolean swap =
                datasetChanged
                        || mainRenderListsController.isSwapping()
                        || NoteListTransition.needsCrossfade(
                                previous, notes, gridLayoutManager.getSpanCount());
        List<Note> next = new ArrayList<>(notes);
        int count = notes.size();

        if (swap) {
            swapNotes = next;
            swapTag = selectedTag;
            swapToTop |= datasetChanged || topChanged || event == UiEvent.NOTE_CREATED;
            mainRenderListsController.swapListContent(animate, this::commitListSwap);
        } else {
            mNoteAdapter.submitList(
                    next,
                    () -> {
                        mainRenderListsController.showStateNoteList(selectedTag, count, animate);
                        // A new note jumps rather than scrolls: the editor opens over the list
                        // right away and a smooth scroll stopped half-way left it there.
                        if (topChanged || event == UiEvent.NOTE_CREATED) {
                            mainRenderListsController.jumpToTop();
                        }
                    });
        }

        mNoteAdapter.setReorderListener(
                mainPresenter.isCustomOrder() ? accessibilityReorder : null);
        mainPresenter.clearUiEvent();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (stopped) {
            stopped = false;
            // Coming back, usually from the editor with a shared-element return transition.
            // Moving cards under it makes the returning note land on a neighbour, so new states
            // wait until the transition is over.
            long settleMs = (long) (RETURN_SETTLE_MS * animatorDurationScale());
            if (settleMs > 0) {
                settling = true;
                settleHandler.removeCallbacks(endSettling);
                settleHandler.postDelayed(endSettling, settleMs);
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopped = true;
        if (navigationController != null) navigationController.onHostStopped();
        settleHandler.removeCallbacks(endSettling);
        settling = false;
        // Anything held back is applied now, without animation, while nobody is looking.
        applyPendingState();
    }

    private float animatorDurationScale() {
        if (!ValueAnimator.areAnimatorsEnabled()) return 0f;
        return Settings.Global.getFloat(
                getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
    }

    @Override
    public void renderDrawerStats(StatsData stats) {

        navigationController
                .getHeaderBinding()
                .drawerStatsNotesNow
                .setText(String.valueOf(stats.notesNow()));
        navigationController
                .getHeaderBinding()
                .drawerStatsNotesMonth
                .setText(String.valueOf(stats.notesMonth()));
        navigationController
                .getHeaderBinding()
                .drawerStatsChars
                .setText(String.valueOf(stats.chars()));
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        navigationController.handleShortcuts(intent);
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
    public void multipleTagChangerDialog(List<Tag> tagsList) {
        SingleTagSelectDialog.show(
                this,
                tagsList,
                new SingleTagSelectDialog.Callback() {

                    @Override
                    public void onTagSelected(String tag) {
                        List<Integer> ids = new ArrayList<>(selectionController.getSelectedIds());
                        mainPresenter.requestTagChangeMultipleNotes(tag, ids);
                        selectionController.clearSelection();
                    }

                    @Override
                    public void onCancel() {
                        selectionController.clearSelection();
                    }
                });
    }

    @Override
    public void renderSearch(List<SearchHit> hits, boolean titlesOnly) {
        searchNotesAdapter.submitList(hits);

        boolean hasResults = !hits.isEmpty();
        mActivityBinding.resultsSearchList.setVisibility(hasResults ? View.VISIBLE : View.GONE);
        mActivityBinding.searchEmptyState.setVisibility(hasResults ? View.GONE : View.VISIBLE);
        if (!hasResults) {
            // One character only searches titles, so "nothing found" would be premature.
            mActivityBinding.searchEmptyText.setText(
                    titlesOnly
                            ? R.string.search_hint_keep_typing
                            : R.string.search_empty_no_results);
        }
    }

    @Override
    public void initListeners() {
        mActivityBinding.actionSearch.setOnClickListener(v -> mActivityBinding.searchView.show());
        searchNotesAdapter.setItemClickListener(this::openNoteEdit);
        bindViewOptionsAction(formatList.getFormat());
        SearchHintFitter.attach(
                mActivityBinding.actionSearch,
                getString(R.string.search),
                getString(R.string.search_short));
        mActivityBinding.actionSearch.setOnMenuItemClickListener(
                menuItem -> {
                    if (menuItem.getItemId() == R.id.viewOptions
                            && !selectionController.isInSelectionMode()) {
                        showViewOptions();
                    }
                    return true;
                });

        tagsAdapter.setOnItemClickListener(
                new OnItemClickListenerTag() {
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
                            mainPresenter.requestTagSelection(false);
                            return;
                        }

                        choiceTagDialog(tag, mView);
                    }
                });

        mNoteAdapter.setOnItemClickListener(
                new OnItemClickListener<>() {
                    @Override
                    public void onClick(int position, Note model) {
                        if (!selectionController.isInSelectionMode()) {
                            openNoteEdit(model, gridLayoutManager.findViewByPosition(position));
                        } else selectionController.toggle(model);
                    }

                    @Override
                    public void onLongClick(int position, Note model) {
                        if (selectionController.isInSelectionMode()) return;
                        if (pickUpForDrag(position, model)) return;
                        choiceNoteDialog(model, position);
                    }
                });
        selectionController.setListener(
                new SelectionController.Listener() {
                    @Override
                    public void onSelectionModeChanged(boolean active) {
                        mActivityBinding.newNotesButton.setVisibility(
                                active ? View.GONE : View.VISIBLE);
                        // States that arrived while selecting were held back.
                        if (!active) applyPendingState();
                    }

                    @Override
                    public void onDeleteRequested() {
                        deleteNotes();
                    }

                    @Override
                    public void onChangeTagRequested() {
                        mainPresenter.requestTagSelection(true);
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

    private void showViewOptions() {
        if (getSupportFragmentManager().findFragmentByTag(ViewOptionsDialog.TAG) != null) return;
        ViewOptionsDialog.newInstance(appPreferencesCache.getSortPref(), formatList.getFormat())
                .show(getSupportFragmentManager(), ViewOptionsDialog.TAG);
    }

    @Override
    public void onViewSortSelected(String sortParam) {
        appPreferencesCache.setSortPref(sortParam);
        mainPresenter.onSortChanged(sortParam);
    }

    @Override
    public void onViewLayoutSelected(int format) {
        if (!formatList.setFormat(format, viewOptionsItem())) return;
        bindViewOptionsAction(format);
        // Changing the column count moves every card at once; doing it behind the list's fade
        // keeps cards from sliding over one another, the same way a new sort order is shown.
        mainRenderListsController.swapListContent(isListInteractive(), this::commitListSwap);
    }

    /**
     * Applies everything the swap in progress is waiting for while the list is not visible: the
     * saved layout, then the newest notes, if any, and finally reveals the list again.
     */
    private void commitListSwap() {
        int format = formatList.getFormat();
        if (gridLayoutManager.getSpanCount() != format) gridLayoutManager.setSpanCount(format);
        List<Note> notes = swapNotes;
        swapNotes = null;
        if (notes == null) {
            mainRenderListsController.prepareSwappedList(mNoteAdapter.getItemCount(), false);
            mainRenderListsController.showStateNoteList(
                    currentSelectedTag, mNoteAdapter.getItemCount(), isListInteractive());
            return;
        }
        Tag tag = swapTag;
        mNoteAdapter.submitList(
                notes,
                () -> {
                    // Nothing is visible here: rebuild the columns from scratch so the grid
                    // comes back without gaps (not while empty, see prepareSwappedList).
                    boolean toTop = swapToTop;
                    swapToTop = false;
                    mainRenderListsController.prepareSwappedList(notes.size(), toTop);
                    mainRenderListsController.showStateNoteList(
                            tag, notes.size(), isListInteractive());
                });
    }

    @Nullable
    private MenuItem viewOptionsItem() {
        return mActivityBinding.actionSearch.getMenu().findItem(R.id.viewOptions);
    }

    /** Shows the current layout on the toolbar action, for sighted and TalkBack users alike. */
    private void bindViewOptionsAction(int format) {
        MenuItem item = viewOptionsItem();
        if (item == null) return;
        formatList.init(item);
        item.setContentDescription(getString(FormatListTool.contentDescriptionFor(format)));
    }

    @Override
    public void settingsLists() {
        mActivityBinding.listTags.addItemDecoration(itemDecorationTags);
        mActivityBinding.listTags.setLayoutManager(mLinearLayoutManager);
        mActivityBinding.listTags.setAdapter(tagsAdapter);
        mActivityBinding.listNotes.addItemDecoration(itemDecorationNotes);
        mActivityBinding.listNotes.setLayoutManager(gridLayoutManager);
        mActivityBinding.listNotes.setAdapter(mNoteAdapter);
        mActivityBinding.listNotes.setItemAnimator(
                new NotesItemAnimator(
                        () -> isListInteractive() && !mainRenderListsController.isSwapping()));

        noteTouchHelper =
                new ItemTouchHelper(
                        new NoteDragCallback(
                                new NoteDragCallback.Host() {
                                    @Override
                                    public boolean canDrag() {
                                        return canDragNotes();
                                    }

                                    @Override
                                    public boolean canSwipe() {
                                        return !selectionController.isInSelectionMode()
                                                && mainPresenter.getDataManager().getFormatCount()
                                                        == 1;
                                    }

                                    @Override
                                    public boolean canTrade(int first, int second) {
                                        return sameSection(first, second);
                                    }

                                    @Override
                                    public boolean isDragging() {
                                        return dragging;
                                    }

                                    @Override
                                    public void move(int from, int to) {
                                        mNoteAdapter.moveDuringDrag(from, to);
                                        dragMoved = true;
                                        pendingMenuNote = null;
                                    }

                                    @Override
                                    public void onDragStarted(
                                            @NonNull RecyclerView.ViewHolder holder) {
                                        dragStarted = true;
                                        dragging = true;
                                        dragMoved = false;
                                        mNoteAdapter.beginDrag();
                                        int position = holder.getBindingAdapterPosition();
                                        List<Note> list = mNoteAdapter.getCurrentList();
                                        draggedNoteId =
                                                position >= 0 && position < list.size()
                                                        ? list.get(position).getId()
                                                        : -1;
                                        liftCard(holder.itemView, true);
                                    }

                                    @Override
                                    public void onDragEnded(
                                            @NonNull RecyclerView.ViewHolder holder) {
                                        liftCard(holder.itemView, false);
                                        if (mNoteAdapter.isDragging()) dropCard();
                                    }

                                    @Override
                                    public void onSwiped(
                                            @NonNull RecyclerView.ViewHolder viewHolder,
                                            int direction) {
                                        swipeNote(viewHolder, direction);
                                    }
                                }));
        noteTouchHelper.attachToRecyclerView(mActivityBinding.listNotes);
    }

    private void swipeNote(RecyclerView.ViewHolder viewHolder, int direction) {
        int position = viewHolder.getBindingAdapterPosition();
        List<Note> list = mNoteAdapter.getCurrentList();
        if (position < 0 || position >= list.size()) return;
        if (direction == ItemTouchHelper.LEFT) {
            selectItemAction(list.get(position));
            mNoteAdapter.notifyItemChanged(position);
        } else {
            Note sNote = list.get(position);
            mainPresenter.setBackupDeleteNote(sNote);
            mainPresenter.noteMoveToTrash(sNote);
            snackBarRestoreNote();
        }
    }

    /** Notes can be dragged in the custom order, outside selection and search. */
    private boolean canDragNotes() {
        return mainPresenter.isCustomOrder()
                && !selectionController.isInSelectionMode()
                && !mActivityBinding.searchView.isShowing();
    }

    /** Pinned and other notes keep their own sections; a card moves only within its own. */
    private boolean sameSection(int first, int second) {
        List<Note> list = mNoteAdapter.getCurrentList();
        if (first < 0 || second < 0 || first >= list.size() || second >= list.size()) return false;
        return list.get(first).isPinned() == list.get(second).isPinned();
    }

    /**
     * Picks a card up in the custom order. Its menu opens instead if it is let go without moving,
     * and at once for TalkBack, whose long press has no finger to follow; it moves notes with the
     * cards' own actions.
     *
     * @return true when the card was picked up.
     */
    private boolean pickUpForDrag(int position, Note note) {
        // The last drop may still be settling into the adapter; this press opens the menu.
        if (dragging || !canDragNotes() || isTouchExplorationEnabled()) return false;
        RecyclerView.ViewHolder holder =
                mActivityBinding.listNotes.findViewHolderForAdapterPosition(position);
        if (holder == null) return false;
        dragStarted = false;
        pendingMenuNote = note;
        pendingMenuPosition = position;
        noteTouchHelper.startDrag(holder);
        if (!dragStarted) pendingMenuNote = null;
        return dragStarted;
    }

    private boolean isTouchExplorationEnabled() {
        android.view.accessibility.AccessibilityManager manager =
                (android.view.accessibility.AccessibilityManager)
                        getSystemService(ACCESSIBILITY_SERVICE);
        return manager != null && manager.isTouchExplorationEnabled();
    }

    /** Raises a picked-up card a little, and puts it back down. */
    private void liftCard(View card, boolean lifted) {
        float scale = lifted ? 1.03f : 1f;
        if (ValueAnimator.areAnimatorsEnabled()) {
            card.animate().scaleX(scale).scaleY(scale).setDuration(150).start();
        } else {
            card.setScaleX(scale);
            card.setScaleY(scale);
        }
    }

    /**
     * Stores where a dragged card was dropped, or opens its menu if it never moved. The card is
     * found by its note in the order on screen: its view may not have a position yet when the last
     * move is still to be laid out.
     */
    private void dropCard() {
        List<Note> order = mNoteAdapter.getCurrentList();
        int position = -1;
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).getId() == draggedNoteId) {
                position = i;
                break;
            }
        }
        draggedNoteId = -1;
        if (dragMoved && position >= 0) {
            Note note = order.get(position);
            mainPresenter.moveNoteInCustomOrder(
                    note.getId(),
                    neighbourId(order, position - 1, note),
                    neighbourId(order, position + 1, note));
        }
        Note menuNote = dragMoved ? null : pendingMenuNote;
        int menuPosition = pendingMenuPosition;
        pendingMenuNote = null;
        boolean moved = dragMoved;
        mNoteAdapter.endDrag(
                () -> {
                    dragging = false;
                    // The cards moved one by one under the finger; laying the grid's columns out
                    // again closes the gaps that leaves.
                    if (moved) gridLayoutManager.invalidateSpanAssignments();
                    // States that arrived during the drag were held back.
                    applyPendingState();
                });
        if (menuNote != null) choiceNoteDialog(menuNote, menuPosition);
    }

    /** The id of the note at {@code position} if it is in the same section, otherwise null. */
    @Nullable
    private static Integer neighbourId(List<Note> order, int position, Note note) {
        if (position < 0 || position >= order.size()) return null;
        Note neighbour = order.get(position);
        return neighbour.isPinned() == note.isPinned() ? neighbour.getId() : null;
    }

    /** "Move up" or "Move down" from TalkBack or another accessibility service. */
    private void moveByAccessibility(@NonNull Note note, int step) {
        List<Note> order = mNoteAdapter.getCurrentList();
        int position = -1;
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).getId() == note.getId()) {
                position = i;
                break;
            }
        }
        int target = position + step;
        if (position < 0 || !sameSection(position, target)) {
            mActivityBinding.listNotes.announceForAccessibility(
                    getString(step < 0 ? R.string.note_move_at_top : R.string.note_move_at_bottom));
            return;
        }
        Integer upper;
        Integer lower;
        if (step < 0) {
            upper = neighbourId(order, target - 1, note);
            lower = order.get(target).getId();
        } else {
            upper = order.get(target).getId();
            lower = neighbourId(order, target + 1, note);
        }
        mainPresenter.moveNoteInCustomOrder(note.getId(), upper, lower);
        mActivityBinding.listNotes.announceForAccessibility(
                getString(R.string.note_moved_to, target + 1, order.size()));
    }

    public void snackBarRestoreNote() {
        Snackbar snackbar =
                Snackbar.make(
                        mActivityBinding.drawerLayout,
                        getString(R.string.noteMoveTrashSnackbar),
                        Snackbar.LENGTH_LONG);
        snackbar.setAction(
                getString(R.string.restore),
                view ->
                        mainPresenter.restoreNoteLastMoveToTrash(
                                mainPresenter.getBackupDeleteNote()));
        snackbar.setAnchorView(mActivityBinding.newNotesButton);
        snackbar.show();
    }

    @Override
    public void actionStartNote(Note note, int position) {
        selectItemAction(note);
    }

    @Override
    public void openCopyNote(long idNote) {
        new NoteNavigator(this, themePreferencesCache)
                .openNote(idNote, false, "", null, String.valueOf(idNote), false);
    }

    @Override
    public void callbackDeleteNote(Note mNote) {
        mainPresenter.setBackupDeleteNote(mNote);
        snackBarRestoreNote();
    }

    @Override
    public void openVersionHistory(int noteId) {
        versionHistoryLauncher.launch(NoteHistoryActivity.intent(this, noteId));
    }

    public void openNoteEdit(Note note, View view) {
        new NoteNavigator(this, themePreferencesCache)
                .openNote(note, false, "", view, String.valueOf(note.getId()));
    }

    @Override
    public void openNewNoteWithId(long id) {
        Tag tagSelected = currentSelectedTag;
        String tagName =
                tagSelected == null
                        ? ""
                        : tagSelected.getSystemAction() == 2 ? "" : tagSelected.getNameTag();

        new NoteNavigator(this, themePreferencesCache)
                .openNote(
                        id,
                        true,
                        tagName,
                        mActivityBinding.newNotesButton,
                        NameTransition.fabTransaction,
                        false);
    }

    @Override
    public void choiceTagDialog(Tag tag, View mView) {
        new PopupWindowsTag(
                getLayoutInflater(),
                mView,
                tag,
                new PopupWindowsTagOnClickListener() {
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
                        mainPresenter.editVisibleTag(
                                tag.setVisibilityReturn(tag.getVisibility() == 1 ? 0 : 1));
                    }
                });
    }

    @Override
    public void choiceNoteDialog(Note note, int position) {
        MoreNoteDialog dialog =
                MoreNoteDialog.newInstance(
                        note.getId(), MoreNoteDialog.RootActivity.MainActivity, position);

        dialog.show(getSupportFragmentManager(), "ChoiceDialog");
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
                onInfoSnack(
                        R.string.exitWhat,
                        mActivityBinding.drawerLayout,
                        SnackBarInfo.Info,
                        Snackbar.LENGTH_LONG);
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
        settleHandler.removeCallbacksAndMessages(null);
        if (reminderCheck != null) reminderCheck.dispose();
        if (mainRenderListsController != null) mainRenderListsController.release();
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
