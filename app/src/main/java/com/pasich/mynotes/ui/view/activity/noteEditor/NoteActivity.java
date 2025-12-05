package com.pasich.mynotes.ui.view.activity.noteEditor;

import static android.view.View.VISIBLE;
import static com.pasich.mynotes.utils.FormattedDataUtil.lastDayEditNote;
import static com.pasich.mynotes.utils.navigation.NoteExtras.EXTRA_ID_NOTE;

import android.content.Context;
import android.content.Intent;
import android.text.Editable;
import android.text.Layout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.pasich.mynotes.R;
import com.pasich.mynotes.base.simplifications.TextWatcher;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.ActivityNoteBinding;
import com.pasich.mynotes.ui.presenter.NotePresenter;
import com.pasich.mynotes.utils.linkMovement.CustomLinkMovementMethod;
import com.pasich.mynotes.utils.navigation.NoteExtras;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class NoteActivity extends BaseNoteEditorActivity<ActivityNoteBinding> {


    // Tracks last known cursor position
    private int lastCursorPosition = -1;

    // Tracks scroll progress when adjusting view
    private int scrollProgress = -1;

    // Saves scroll position when keyboard opens/closes
    private int savedScrollPosition = -1;

    // Tracks current keyboard visibility state
    private boolean isKeyboardVisible = false;

    // Tracks last cursor line in multiline input
    private int lastCursorLine = -1;

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


    /**
     * Scrolls the view to keep the cursor visible when the keyboard is open.
     */
    private void scrollToCursor() {
        if (!binding.valueNote.isFocused()) return;

        binding.valueNote.post(() -> {
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

            int visibleHeight = binding.getRoot().getHeight() - imeInsets.bottom - systemInsets.top;

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


    /**
     * Sets up a scroll listener for AppBar
     */
    private void setupAppBarScrollListener() {
        binding.scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {

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

    /**
     * Updates the scroll progress indicator
     */
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
     * Handles system indents and keyboard appearance/disappearance:
     * - correctly sets top inset for root layout
     * - raises FAB above the navbar
     * - adapts the bottom margin of ScrollView to the keyboard or system navbar
     * - saves and restores the scroll position when opening/closing the keyboard
     * - automatically scrolls to the cursor when the keyboard appears
     */
    @Override
    protected void applyEdgeToEdgeInsets(View rootView) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {

            Insets navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars());

            CoordinatorLayout.LayoutParams fab = (CoordinatorLayout.LayoutParams) binding.editActive.getLayoutParams();
            fab.setMargins(fab.leftMargin, fab.topMargin, fab.rightMargin, 25 + navBarInsets.bottom  // додаємо висоту нижньої панелі
            );
            binding.editActive.setLayoutParams(fab);

            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());

            boolean keyboardWasVisible = isKeyboardVisible;
            boolean keyboardWillBeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());

            if (!keyboardWasVisible && keyboardWillBeVisible) {
                savedScrollPosition = binding.scrollView.getScrollY();
            }

            isKeyboardVisible = keyboardWillBeVisible;

            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), 0);

            int bottomMargin = Math.max(imeInsets.bottom, systemBars.bottom);

            android.widget.LinearLayout.LayoutParams params = (android.widget.LinearLayout.LayoutParams) binding.scrollView.getLayoutParams();

            if (keyboardWasVisible && !keyboardWillBeVisible && savedScrollPosition >= 0) {
                binding.scrollView.scrollTo(0, savedScrollPosition);
            }

            params.setMargins(params.leftMargin, params.topMargin, params.rightMargin, bottomMargin);
            binding.scrollView.setLayoutParams(params);

            binding.scrollView.setPadding(binding.scrollView.getPaddingLeft(), binding.scrollView.getPaddingTop(), binding.scrollView.getPaddingRight(), getResources().getDimensionPixelSize(R.dimen.scroll_view_bottom_margin));

            if (!keyboardWasVisible && keyboardWillBeVisible && binding.valueNote.isFocused()) {
                lastCursorPosition = -1;
                binding.valueNote.postDelayed(this::scrollToCursor, 200);
            } else if (keyboardWasVisible && !keyboardWillBeVisible && savedScrollPosition >= 0) {
                binding.scrollView.post(() -> {
                    if (Math.abs(binding.scrollView.getScrollY() - savedScrollPosition) > 5) {
                        binding.scrollView.scrollTo(0, savedScrollPosition);
                    }
                });
            }

            return insets;
        });
    }


    @Override
    public void onStop() {
        super.onStop();
        // CRITICAL: Emergency saving when stopping Activity
        if (notePresenter != null && notePresenter.getNote() != null) {
            String currentTitle = binding != null ? binding.notesTitle.getText().toString() : "";
            String currentValue = binding != null ? binding.valueNote.getText().toString() : "";
            // If there are unsaved changes, perform an emergency save.
            notePresenter.simpleNoteChange(currentTitle, currentValue, true);
        }
    }

    @Override
    public void initListeners() {
        binding.notesTitle.addTextChangedListener(new TextWatcher() {
            @Override
            protected void changeText(Editable s) {
                if (!notePresenter.hasNote()) return;
                if (s.toString().contains("\n")) {
                    binding.notesTitle.setText(s.toString().replace('\n', ' ').trim());
                    binding.valueNote.requestFocus();
                }
                String title = s.toString().trim();
                binding.titleToolbarCollapsed.setText(!title.isEmpty() ? title : getString(R.string.noteTitle));
                notePresenter.simpleNoteChange(title, null, false);

            }
        });

        binding.valueNote.addTextChangedListener(new TextWatcher() {
            @Override
            protected void changeText(Editable s) {
                if (!notePresenter.hasNote()) return;
                String newValue = s.toString();
                if (!newValue.equals(notePresenter.getNote().getValue())) {
                    notePresenter.simpleNoteChange(null, newValue, false);
                }
            }
        });

        // Add a click handler for the input field - only for cursor movement processing
        binding.valueNote.setOnClickListener(v -> {
            if (binding.valueNote.isFocused() && scrollProgress < 95) {
                binding.valueNote.postDelayed(this::scrollToCursor, 50);
            }
        });

    }

    @Override
    public void activatedActivity() {
        binding.setActivateEdit(true);
        binding.valueNote.setEnabled(true);
        binding.valueNote.setFocusable(true);
        if (!notePresenter.getNewNotesKey())
            binding.valueNote.setSelection(binding.valueNote.getText().length());
        binding.valueNote.setFocusableInTouchMode(true);
        binding.valueNote.requestFocus();

        if (notePresenter.getNewNotesKey()) {
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).toggleSoftInputFromWindow(binding.valueNote.getApplicationWindowToken(), InputMethodManager.SHOW_IMPLICIT, 0);
            lastCursorPosition = -1;
            binding.valueNote.postDelayed(this::scrollToCursor, 300);

        } else {
            if (binding.valueNote.requestFocus()) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(binding.valueNote, InputMethodManager.SHOW_IMPLICIT);
            }
        }


    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (binding != null) {
            binding.notesTitle.addTextChangedListener(null);
            binding.titleToolbarTagCenter.setOnClickListener(null);
            binding.titleToolbarTagCollapsed.setOnClickListener(null);
            binding.valueNote.setOnFocusChangeListener(null);
            binding.valueNote.setOnClickListener(null);
            binding.scrollProgressIndicator.setProgress(0);
        }
        lastCursorPosition = -1;
        savedScrollPosition = -1;
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

        binding.notesTitle.setText(title != null && !title.isEmpty() ? title : "");

        binding.valueNote.setText(value != null ? value : "");
        binding.valueNote.setMovementMethod(CustomLinkMovementMethod.getInstance());


        String formattedDate = getString(
                R.string.lastDateEditNote,
                lastDayEditNote(note.getDate())
        );

        binding.titleToolbarDataCenter.setText(formattedDate);

        // Collapsed title
        binding.titleToolbarCollapsed.setText(
                title != null && !title.isEmpty()
                        ? title
                        : getString(R.string.noteTitle)
        );
        binding.titleToolbarDataCollapsed.setText(formattedDate);

        // Tag
        String tag = note.getTag();
        changeTag(tag != null ? tag : "", false);

        if (notePresenter.getNewNotesKey()) {
            activatedActivity();
        }
    }

    @Override
    public void closeNoteActivity() {
        if (binding == null || notePresenter == null) {
            supportFinishAfterTransition();
            return;
        }

        binding.getRoot().clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (binding != null) {
            imm.hideSoftInputFromWindow(binding.valueNote.getWindowToken(), 0);
        }
        supportFinishAfterTransition();
    }

    @Override
    public void changeTag(String nameTag, boolean change) {
        if (change) {
            notePresenter.getNote().setTag(nameTag);
            notePresenter.setAssignedTagNote(nameTag);
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
        binding.valueNote.setTypeface(null, notePresenter.getTypeFace(notePresenter.getDataManager().getTypeFaceNoteActivity()));
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
