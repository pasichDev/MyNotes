package com.pasich.mynotes.ui.view.activity.noteEditor;

import static android.view.View.VISIBLE;
import static com.pasich.mynotes.utils.FormattedDataUtil.lastDayEditNote;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.OneShotPreDrawListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import com.google.android.material.chip.Chip;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.simplifications.TextWatcher;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.ActivityNoteBinding;
import com.pasich.mynotes.ui.presenter.NotePresenter;
import com.pasich.mynotes.utils.editor.EditableLinkMovementMethod;
import com.pasich.mynotes.utils.editor.EditorCursor;
import dagger.hilt.android.AndroidEntryPoint;

/** Activity for creating and editing a single note. */
@AndroidEntryPoint
public class NoteActivity extends BaseNoteEditorActivity<ActivityNoteBinding> {

    private static final String STATE_EDITING = "note.editing";
    private static final String STATE_SELECTION_START = "note.selectionStart";
    private static final String STATE_SELECTION_END = "note.selectionEnd";
    private static final String STATE_ANCHOR_OFFSET = "note.anchorOffset";
    private static final String STATE_DRAFT_TITLE = "note.draftTitle";
    private static final String STATE_DRAFT_VALUE = "note.draftValue";

    /**
     * How long typing has to pause before the body is copied out of the field. Copying, counting
     * words and comparing are linear in the note's length; doing them on every keystroke made
     * typing in a long note stutter.
     */
    private static final long VALUE_COMMIT_DELAY_MS = 250;

    private final Runnable commitValueRunnable = this::commitValueEdit;

    private TextWatcher titleWatcher;
    private TextWatcher valueWatcher;

    // Tracks last known cursor position
    private int lastCursorPosition = -1;

    // Tracks scroll progress when adjusting view
    private int scrollProgress = -1;

    // Tracks current keyboard visibility state
    private boolean isKeyboardVisible = false;

    // Tracks last cursor line in multiline input
    private int lastCursorLine = -1;

    // State carried over from a previous instance (rotation, process restore), applied once the
    // note has been loaded.
    private boolean restoredEditing = false;
    private int restoredSelectionStart = EditorCursor.NONE;
    private int restoredSelectionEnd = EditorCursor.NONE;
    private int restoredAnchorOffset = EditorCursor.NONE;
    private String draftTitle;
    private String draftValue;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        readRestoredState(savedInstanceState);
        super.onCreate(savedInstanceState);
    }

    private void readRestoredState(@Nullable Bundle state) {
        if (state == null) return;
        restoredEditing = state.getBoolean(STATE_EDITING, false);
        restoredSelectionStart = state.getInt(STATE_SELECTION_START, EditorCursor.NONE);
        restoredSelectionEnd = state.getInt(STATE_SELECTION_END, EditorCursor.NONE);
        restoredAnchorOffset = state.getInt(STATE_ANCHOR_OFFSET, EditorCursor.NONE);
        draftTitle = state.getString(STATE_DRAFT_TITLE);
        draftValue = state.getString(STATE_DRAFT_VALUE);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (binding == null) return;
        boolean editing = binding.valueNote.isEnabled();
        outState.putBoolean(STATE_EDITING, editing);
        if (editing) {
            outState.putInt(STATE_SELECTION_START, binding.valueNote.getSelectionStart());
            outState.putInt(STATE_SELECTION_END, binding.valueNote.getSelectionEnd());
        }
        outState.putInt(STATE_ANCHOR_OFFSET, readingAnchorOffset());
        // The fields do not save their own text (see initListeners); a draft is kept only while a
        // save is still on its way, so the next instance never shows older text than was typed.
        if (notePresenter != null && notePresenter.hasUnsavedChanges()) {
            outState.putString(STATE_DRAFT_TITLE, binding.notesTitle.getText().toString());
            outState.putString(STATE_DRAFT_VALUE, binding.valueNote.getText().toString());
        }
    }

    @Override
    protected void onNewNoteInit(Note note) {
        // Simple version
    }

    @Override
    protected void setNewNoteTitle() {
        binding.titleToolbarDataCollapsed.setText(getString(R.string.new_note));
    }

    @Override
    protected int getMenuResId() {
        return R.menu.menu_activity_toolbar_note;
    }

    @Override
    protected Toolbar getToolbar() {
        return binding.toolbar;
    }

    @Override
    protected Chip getReminderChip() {
        return binding.reminderChip;
    }

    @Override
    protected ActivityNoteBinding inflateBinding(LayoutInflater inflater) {
        return ActivityNoteBinding.inflate(inflater);
    }

    @Override
    protected void bindingSetPresenter(ActivityNoteBinding binding) {
        binding.setPresenter((NotePresenter) notePresenter);
    }

    @Override
    protected void onAfterPresenterReady() {
        setupAppBarScrollListener();
    }

    /** Scrolls the view to keep the cursor visible when the keyboard is open. */
    private void scrollToCursor() {
        if (!binding.valueNote.isFocused()) return;

        binding.valueNote.post(
                () -> {
                    WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(binding.getRoot());
                    if (insets == null || !insets.isVisible(WindowInsetsCompat.Type.ime())) {
                        return;
                    }

                    Layout layout = binding.valueNote.getLayout();
                    if (layout == null) {
                        return;
                    }

                    int cursorPosition = binding.valueNote.getSelectionStart();
                    int line = layout.getLineForOffset(cursorPosition);

                    if (cursorPosition == lastCursorPosition && line == lastCursorLine) {
                        return;
                    }

                    int lineTop = layout.getLineTop(line);
                    int editTextTop = binding.valueNote.getTop();
                    int absoluteLineTop = editTextTop + lineTop;

                    int currentScrollY = binding.scrollView.getScrollY();

                    Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
                    Insets systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());

                    int visibleHeight =
                            binding.getRoot().getHeight() - imeInsets.bottom - systemInsets.top;

                    int lineHeight = layout.getLineBottom(line) - layout.getLineTop(line);
                    int lineVisibleTop = absoluteLineTop - currentScrollY;
                    int lineVisibleBottom = lineVisibleTop + lineHeight;

                    float ratio = (float) lineVisibleBottom / (float) visibleHeight;

                    if (ratio > 0.9f) {
                        int targetScrollY = absoluteLineTop - (int) (visibleHeight * 0.7f);
                        binding.scrollView.smoothScrollTo(0, Math.max(0, targetScrollY));
                    }
                    lastCursorPosition = cursorPosition;
                    lastCursorLine = line;
                });
    }

    /** Sets up a scroll listener for AppBar */
    private void setupAppBarScrollListener() {
        binding.scrollView.setOnScrollChangeListener(
                (NestedScrollView.OnScrollChangeListener)
                        (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                            int titleTop = binding.notesTitle.getTop();

                            boolean shouldShowCollapsed = scrollY > titleTop;

                            if (shouldShowCollapsed) {
                                if (binding.centerContent.getVisibility() == View.VISIBLE) {
                                    binding.centerContent.setVisibility(View.GONE);
                                    binding.endContent.setVisibility(View.VISIBLE);
                                    binding.scrollProgressIndicator.setVisibility(View.VISIBLE);
                                }
                                updateScrollProgress(scrollY);
                            } else {
                                if (binding.centerContent.getVisibility() == View.GONE) {
                                    binding.centerContent.setVisibility(View.VISIBLE);
                                    binding.endContent.setVisibility(View.GONE);
                                    binding.scrollProgressIndicator.setVisibility(View.GONE);
                                }
                            }
                        });
    }

    /** Updates the scroll progress indicator */
    private void updateScrollProgress(int scrollY) {
        View child = binding.scrollView.getChildAt(0);
        if (child != null) {
            int totalScrollableHeight = child.getHeight() - binding.scrollView.getHeight();

            if (totalScrollableHeight > 0) {
                int progress = (int) ((float) scrollY / totalScrollableHeight * 100);
                progress = Math.max(0, Math.min(100, progress));
                scrollProgress = progress;
                binding.scrollProgressIndicator.setProgress(progress);
            }
        }
    }

    /**
     * Handles system insets and the keyboard: the root takes the status bar, the FAB clears the
     * navigation bar, and the scroll view ends above the keyboard or the navigation bar. When the
     * keyboard opens, the caret is brought into view; when it closes nothing is moved, so the text
     * stays where the user left it.
     */
    @Override
    protected void applyEdgeToEdgeInsets(View rootView) {
        ViewCompat.setOnApplyWindowInsetsListener(
                rootView,
                (v, insets) -> {
                    Insets navBarInsets =
                            insets.getInsets(WindowInsetsCompat.Type.navigationBars());

                    CoordinatorLayout.LayoutParams fab =
                            (CoordinatorLayout.LayoutParams) binding.editActive.getLayoutParams();
                    fab.setMargins(
                            fab.leftMargin,
                            fab.topMargin,
                            fab.rightMargin,
                            25 + navBarInsets.bottom // додаємо висоту нижньої панелі
                            );
                    binding.editActive.setLayoutParams(fab);

                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());

                    boolean keyboardWasVisible = isKeyboardVisible;
                    boolean keyboardWillBeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
                    isKeyboardVisible = keyboardWillBeVisible;

                    v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), 0);

                    int bottomMargin = Math.max(imeInsets.bottom, systemBars.bottom);
                    android.widget.LinearLayout.LayoutParams params =
                            (android.widget.LinearLayout.LayoutParams)
                                    binding.scrollView.getLayoutParams();
                    if (params.bottomMargin != bottomMargin) {
                        params.setMargins(
                                params.leftMargin,
                                params.topMargin,
                                params.rightMargin,
                                bottomMargin);
                        binding.scrollView.setLayoutParams(params);
                    }

                    binding.scrollView.setPadding(
                            binding.scrollView.getPaddingLeft(),
                            binding.scrollView.getPaddingTop(),
                            binding.scrollView.getPaddingRight(),
                            getResources()
                                    .getDimensionPixelSize(R.dimen.scroll_view_bottom_margin));

                    if (!keyboardWasVisible
                            && keyboardWillBeVisible
                            && binding.valueNote.isFocused()) {
                        lastCursorPosition = -1;
                        binding.valueNote.postDelayed(this::scrollToCursor, 200);
                    }

                    return insets;
                });
    }

    /**
     * Offset of the text at the top of the viewport; 0 while the start of the note is on screen.
     */
    private int firstVisibleOffset() {
        Layout layout = binding.valueNote.getLayout();
        if (layout == null) return EditorCursor.NONE;
        int top = binding.scrollView.getScrollY() - valueTextTop();
        if (top <= 0) return 0;
        return layout.getLineStart(layout.getLineForVertical(top));
    }

    /**
     * Reading position to restore after a recreation, or {@link EditorCursor#NONE} while the title
     * area is still on screen. Unlike a pixel scroll position it survives a rotation, where the
     * text re-wraps to a different width.
     */
    private int readingAnchorOffset() {
        if (binding.scrollView.getScrollY() - valueTextTop() <= 0) return EditorCursor.NONE;
        return firstVisibleOffset();
    }

    /** Offset of the text at the bottom of the viewport, or {@link EditorCursor#NONE}. */
    private int lastVisibleOffset() {
        Layout layout = binding.valueNote.getLayout();
        if (layout == null) return EditorCursor.NONE;
        int bottom = binding.scrollView.getScrollY() + binding.scrollView.getHeight();
        int y = bottom - valueTextTop();
        if (y < 0) return EditorCursor.NONE;
        return layout.getLineEnd(layout.getLineForVertical(y));
    }

    /** Top of the note's first text line in the scroll view's content coordinates. */
    private int valueTextTop() {
        Rect rect = new Rect();
        binding.valueNote.getDrawingRect(rect);
        binding.scrollView.offsetDescendantRectToMyCoords(binding.valueNote, rect);
        return rect.top + binding.valueNote.getTotalPaddingTop();
    }

    /** Scrolls so the line holding {@code offset} sits at the top, once the text is laid out. */
    private void scrollToOffsetWhenLaidOut(int offset) {
        OneShotPreDrawListener.add(
                binding.valueNote,
                () -> {
                    Layout layout = binding.valueNote.getLayout();
                    if (layout == null) return;
                    int clamped = EditorCursor.clamp(offset, binding.valueNote.length());
                    int line = layout.getLineForOffset(clamped);
                    binding.scrollView.scrollTo(0, valueTextTop() + layout.getLineTop(line));
                });
    }

    @Override
    public void onStop() {
        super.onStop();
        // Leaving the screen (home, rotation, another app) writes what is still waiting in the
        // debounce, so the last seconds of typing are never lost.
        if (binding == null || notePresenter == null || !notePresenter.hasNote()) return;
        commitPendingValueEdit();
        notePresenter.flushPending();
    }

    /** Copies the body out of the field and hands it to the presenter if it changed. */
    private void commitValueEdit() {
        if (binding == null || notePresenter == null || !notePresenter.hasNote()) return;
        String newValue = binding.valueNote.getText().toString();
        updateWordCount(newValue);
        if (!newValue.equals(notePresenter.getNote().getValue())) {
            notePresenter.simpleNoteChange(null, newValue, false);
        }
    }

    /** Runs a body commit that is still waiting for typing to pause, right now. */
    private void commitPendingValueEdit() {
        binding.valueNote.removeCallbacks(commitValueRunnable);
        commitValueEdit();
    }

    @Override
    public void initListeners() {
        titleWatcher =
                new TextWatcher() {
                    @Override
                    protected void changeText(Editable s) {
                        if (!notePresenter.hasNote()) return;
                        if (s.toString().contains("\n")) {
                            binding.notesTitle.setText(s.toString().replace('\n', ' ').trim());
                            binding.valueNote.requestFocus();
                        }
                        String title = s.toString().trim();
                        binding.titleToolbarCollapsed.setText(
                                !title.isEmpty() ? title : getString(R.string.noteTitle));
                        notePresenter.simpleNoteChange(title, null, false);
                    }
                };
        binding.notesTitle.addTextChangedListener(titleWatcher);

        valueWatcher =
                new TextWatcher() {
                    @Override
                    protected void changeText(Editable s) {
                        // Nothing linear in the note's length runs per keystroke: the body is
                        // copied, counted and compared once typing pauses.
                        binding.valueNote.removeCallbacks(commitValueRunnable);
                        binding.valueNote.postDelayed(commitValueRunnable, VALUE_COMMIT_DELAY_MS);
                    }
                };
        binding.valueNote.addTextChangedListener(valueWatcher);

        // The note is reloaded from the database on recreation and an unsaved draft is kept in
        // onSaveInstanceState, so the fields' own copies would only be a second, stale snapshot
        // of the same text in the saved state.
        binding.notesTitle.setSaveEnabled(false);
        binding.valueNote.setSaveEnabled(false);

        // Add a click handler for the input field - only for cursor movement processing
        binding.valueNote.setOnClickListener(
                v -> {
                    if (binding.valueNote.isFocused() && scrollProgress < 95) {
                        binding.valueNote.postDelayed(this::scrollToCursor, 50);
                    }
                });
    }

    @Override
    public void activatedActivity() {
        int[] selection =
                EditorCursor.activationSelection(
                        restoredSelectionStart,
                        restoredSelectionEnd,
                        firstVisibleOffset(),
                        lastVisibleOffset(),
                        binding.valueNote.length());
        restoredSelectionStart = EditorCursor.NONE;
        restoredSelectionEnd = EditorCursor.NONE;

        binding.setActivateEdit(true);
        binding.valueNote.setEnabled(true);
        binding.valueNote.setFocusable(true);
        binding.valueNote.setFocusableInTouchMode(true);
        binding.valueNote.setSelection(selection[0], selection[1]);
        binding.valueNote.requestFocus();

        if (notePresenter.getNewNotesKey()) {
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                    .toggleSoftInputFromWindow(
                            binding.valueNote.getApplicationWindowToken(),
                            InputMethodManager.SHOW_IMPLICIT,
                            0);
            lastCursorPosition = -1;
            binding.valueNote.postDelayed(this::scrollToCursor, 300);

        } else {
            if (binding.valueNote.requestFocus()) {
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(binding.valueNote, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (binding != null) {
            binding.notesTitle.removeTextChangedListener(titleWatcher);
            binding.valueNote.removeTextChangedListener(valueWatcher);
            binding.titleToolbarTagCenter.setOnClickListener(null);
            binding.titleToolbarTagCollapsed.setOnClickListener(null);
            binding.valueNote.setOnFocusChangeListener(null);
            binding.valueNote.setOnClickListener(null);
            binding.valueNote.removeCallbacks(commitValueRunnable);
            binding.scrollProgressIndicator.setProgress(0);
        }
        lastCursorPosition = -1;
        isKeyboardVisible = false;
    }

    @Override
    public void loadingNote(Note note) {
        if (note == null) {
            finish();
            return;
        }

        String title = note.getTitle();
        String value = note.getValue();

        // Text typed before a recreation that had not reached the database yet.
        String pendingTitle = draftTitle;
        String pendingValue = draftValue;
        draftTitle = null;
        draftValue = null;
        if (pendingTitle != null) title = pendingTitle;
        if (pendingValue != null) value = pendingValue;

        binding.notesTitle.setText(title != null && !title.isEmpty() ? title : "");

        binding.valueNote.setText(value != null ? value : "");
        // After setText: with autoLink, a field that is not editable yet gets a link movement from
        // setText, which clears the selection on focus and sent the caret to the start of the
        // note. An editable field needs the editing movement.
        binding.valueNote.setMovementMethod(EditableLinkMovementMethod.getInstance());

        String formattedDate =
                getString(R.string.lastDateEditNote, lastDayEditNote(note.getDate()));

        binding.titleToolbarDataCenter.setText(formattedDate);

        // Collapsed title
        binding.titleToolbarCollapsed.setText(
                title != null && !title.isEmpty() ? title : getString(R.string.noteTitle));
        binding.titleToolbarDataCollapsed.setText(formattedDate);

        // Tag
        String tag = note.getTag();
        changeTag(tag != null ? tag : "", false);

        updateWordCount(value);
        updateReminderChip(note);

        if (pendingTitle != null || pendingValue != null) {
            // The presenter takes the loaded note right after this call; hand it the draft once
            // it has, so the draft is compared against what is stored and saved.
            String restoredTitle = pendingTitle;
            String restoredValue = pendingValue;
            binding.getRoot()
                    .post(
                            () ->
                                    notePresenter.simpleNoteChange(
                                            restoredTitle, restoredValue, false));
        }

        if (restoredAnchorOffset != EditorCursor.NONE) {
            scrollToOffsetWhenLaidOut(restoredAnchorOffset);
            restoredAnchorOffset = EditorCursor.NONE;
        }

        if (notePresenter.getNewNotesKey() || restoredEditing) {
            restoredEditing = false;
            activatedActivity();
        }
    }

    private void updateWordCount(String text) {
        if (text == null || text.trim().isEmpty()) {
            binding.wordCountCenter.setVisibility(View.GONE);
            return;
        }
        int count = text.trim().split("\\s+").length;
        binding.wordCountCenter.setText(
                getResources().getQuantityString(R.plurals.wordCount, count, count));
        binding.wordCountCenter.setVisibility(View.VISIBLE);
    }

    @Override
    public void closeNoteActivity() {
        if (binding == null || notePresenter == null) {
            supportFinishAfterTransition();
            return;
        }

        binding.getRoot().clearFocus();
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (binding != null) {
            imm.hideSoftInputFromWindow(binding.valueNote.getWindowToken(), 0);
        }
        supportFinishAfterTransition();
    }

    @Override
    public void changeTag(String nameTag, boolean change) {
        if (change) {
            notePresenter.getNote().setTag(nameTag);
        }
        if (!nameTag.isEmpty()) {
            String tagText = getString(R.string.tagHastag, nameTag);
            binding.titleToolbarTagCenter.setText(tagText);
            binding.titleToolbarTagCenter.setVisibility(View.VISIBLE);

            binding.titleToolbarTagCollapsed.setText(tagText);
            binding.titleToolbarTagCollapsed.setVisibility(View.VISIBLE);
        } else {
            binding.titleToolbarTagCenter.setVisibility(View.GONE);
            binding.titleToolbarTagCollapsed.setVisibility(View.GONE);
        }
    }

    @Override
    public void changeTextStyle() {
        binding.valueNote.setTypeface(
                null,
                notePresenter.getTypeFace(
                        notePresenter.getDataManager().getTypeFaceNoteActivity()));
    }

    @Override
    public void changeTextSizeOnline(int sizeText) {
        binding.valueNote.setTextSize(sizeText == 0 ? 16 : sizeText);
        binding.notesTitle.setTextSize(sizeText == 0 ? 20 : sizeText + 4);
    }

    @Override
    public void changeTextSizeOffline() {
        changeTextSizeOnline(notePresenter.getDataManager().getSizeTextNoteActivity());
    }

    @Override
    public void runAttachmentsCleanup(Note note) {
        // Not implemented extended
    }

    @Override
    public void reloadExtendedEditor() {
        // Not implemented extended
    }

    @Override
    public void onNoteCopied(long newNoteId) {
        binding.duplicateTag.setTag("#" + getString(R.string.duplicateTag));
        binding.duplicateTag.setVisibility(VISIBLE);
    }
}
