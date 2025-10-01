package com.pasich.mynotes.ui.view.activity;

import static com.pasich.mynotes.utils.FormattedDataUtil.lastDayEditNote;
import static com.pasich.mynotes.utils.transition.TransitionUtil.buildContainerTransform;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.Layout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.OnBackPressedCallback;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.base.simplifications.TextWatcher;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.ActivityNoteBinding;
import com.pasich.mynotes.ui.contract.NoteContract;
import com.pasich.mynotes.ui.presenter.NotePresenter;
import com.pasich.mynotes.ui.view.dialogs.MoreNoteDialog;
import com.pasich.mynotes.ui.view.dialogs.note.LinkInfoDialog;
import com.pasich.mynotes.utils.CustomLinkMovementMethod;
import com.pasich.mynotes.utils.constants.NameTransition;
import com.pasich.mynotes.utils.enums.SaveState;

import java.util.Date;
import java.util.Objects;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class NoteActivity extends BaseActivity implements NoteContract.view {

    public ActivityNoteBinding binding;
    @Inject
    public NoteContract.presenter notePresenter;

    // Меню для індикатора стану збереження
    private MenuItem saveStatusMenuItem;

    // Змінна для відстеження останньої позиції курсора
    private int lastCursorPosition = -1;

    private int scrollProgress = -1;

    // Змінна для збереження позиції скролу при роботі з клавіатурою
    private int savedScrollPosition = -1;

    // Змінні для точного відстеження стану клавіатури
    private boolean isKeyboardVisible = false;
    private int lastCursorLine = -1;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        selectTheme();
        settingsStatusBar(getWindow());
        long idNote = getIntent().getLongExtra("idNote", 0);
        binding = ActivityNoteBinding.inflate(getLayoutInflater());
        binding.noteLayout.setTransitionName(idNote == 0 ? NameTransition.fabTransaction : String.valueOf(idNote));
        setEnterSharedElementCallback(new MaterialContainerTransformSharedElementCallback());
        getWindow().setSharedElementEnterTransition(buildContainerTransform(binding.noteLayout));
        getWindow().setSharedElementReturnTransition(buildContainerTransform(binding.noteLayout));

        super.onCreate(savedInstanceState);
        setContentView(binding.getRoot());

        setupEdgeToEdgeInsetsWithKeyboard(binding.getRoot());
        binding.setPresenter((NotePresenter) notePresenter);
        notePresenter.attachView(this);
        notePresenter.getLoadIntentData(getIntent());
        notePresenter.viewIsReady();

        setupAppBarScrollListener();

        // Handle back button press with OnBackPressedDispatcher
        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                notePresenter.closeActivity();
            }
        });


    }


    /**
     * Прокручує до позиції курсора в полі вводу
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
     * Налаштовує слухач скролінгу для AppBar
     */
    private void setupAppBarScrollListener() {
        binding.scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            // Отримуємо позицію заголовка нотатки відносно scrollView
            int titleTop = binding.notesTitle.getTop();

            // Простіша логіка: якщо прокрутили більше ніж висота заголовка
            boolean shouldShowCollapsed = scrollY > titleTop;

            if (shouldShowCollapsed) {
                // Показуємо згорнутий вигляд
                if (binding.centerContent.getVisibility() == View.VISIBLE) {
                    binding.centerContent.setVisibility(View.GONE);
                    binding.endContent.setVisibility(View.VISIBLE);
                    binding.scrollProgressIndicator.setVisibility(View.VISIBLE);
                }

                // Оновлюємо прогрес скролу
                updateScrollProgress(scrollY);
            } else {
                // Показуємо розгорнутий вигляд
                if (binding.centerContent.getVisibility() == View.GONE) {
                    binding.centerContent.setVisibility(View.VISIBLE);
                    binding.endContent.setVisibility(View.GONE);
                    binding.scrollProgressIndicator.setVisibility(View.GONE);
                }
            }
        });
    }

    /**
     * Оновлює індикатор прогресу скролу
     */
    private void updateScrollProgress(int scrollY) {
        // Отримуємо загальну висоту контенту для скролу
        View child = binding.scrollView.getChildAt(0);
        if (child != null) {
            int totalScrollableHeight = child.getHeight() - binding.scrollView.getHeight();

            if (totalScrollableHeight > 0) {
                // Розраховуємо прогрес у відсотках (0-100)
                int progress = (int) ((float) scrollY / totalScrollableHeight * 100);
                progress = Math.max(0, Math.min(100, progress)); // Обмежуємо 0-100
                scrollProgress = progress;
                binding.scrollProgressIndicator.setProgress(progress);
            }
        }
    }


    /**
     * Налаштовує відступи з урахуванням клавіатури для NoteActivity
     */
    private void setupEdgeToEdgeInsetsWithKeyboard(View rootView) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {

            Insets navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars());

            // Оновлюємо марджін FAB
            CoordinatorLayout.LayoutParams fab = (CoordinatorLayout.LayoutParams) binding.editActive.getLayoutParams();
            fab.setMargins(fab.leftMargin, fab.topMargin, fab.rightMargin, 25 + navBarInsets.bottom  // додаємо висоту нижньої панелі
            );
            binding.editActive.setLayoutParams(fab);

            // Отримуємо відступи для системних барів
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // Отримуємо відступи для клавіатури (IME)
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());

            // Точно визначаємо стан клавіатури
            boolean keyboardWasVisible = isKeyboardVisible;
            boolean keyboardWillBeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());

            // Обробляємо зміну стану клавіатури
            if (!keyboardWasVisible && keyboardWillBeVisible) {
                // Клавіатура тільки з'являється - зберігаємо поточну позицію
                savedScrollPosition = binding.scrollView.getScrollY();
            }

            // Оновлюємо стан
            isKeyboardVisible = keyboardWillBeVisible;

            // Встановлюємо padding зверху для системних барів тільки для кореневого view
            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), 0);

            // Встановлюємо нижній margin для scrollView з урахуванням клавіатури
            int bottomMargin = Math.max(imeInsets.bottom, systemBars.bottom);

            // Отримуємо LayoutParams для scrollView (він в LinearLayout)
            android.widget.LinearLayout.LayoutParams params = (android.widget.LinearLayout.LayoutParams) binding.scrollView.getLayoutParams();

            // При ховані клавіатури - спочатку встановлюємо правильну позицію скролу
            if (keyboardWasVisible && !keyboardWillBeVisible && savedScrollPosition >= 0) {
                // Встановлюємо позицію ДО зміни розміру
                binding.scrollView.scrollTo(0, savedScrollPosition);
            }

            // Встановлюємо нижній margin
            params.setMargins(params.leftMargin, params.topMargin, params.rightMargin, bottomMargin);
            binding.scrollView.setLayoutParams(params);

            // Встановлюємо додатковий padding для scrollView
            binding.scrollView.setPadding(binding.scrollView.getPaddingLeft(), binding.scrollView.getPaddingTop(), binding.scrollView.getPaddingRight(), getResources().getDimensionPixelSize(R.dimen.scroll_view_bottom_margin));

            // Обробляємо появу клавіатури
            if (!keyboardWasVisible && keyboardWillBeVisible && binding.valueNote.isFocused()) {
                // Клавіатура з'являється - прокручуємо до курсора
                lastCursorPosition = -1;
                binding.valueNote.postDelayed(this::scrollToCursor, 200);
            }
            // Додаткове закріплення позиції після ховання клавіатури
            else if (keyboardWasVisible && !keyboardWillBeVisible && savedScrollPosition >= 0) {
                // Ще раз встановлюємо позицію ПІСЛЯ зміни розміру
                binding.scrollView.post(() -> {
                    if (Math.abs(binding.scrollView.getScrollY() - savedScrollPosition) > 5) {
                        binding.scrollView.scrollTo(0, savedScrollPosition);
                    }
                });
            }


            return insets;
        });
    }


    private void settingsStatusBar(Window window) {
        // Декор під edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false);

        // Контролюємо колір іконок статусбару залежно від нічного режиму
        final int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        WindowInsetsControllerCompat insetsController = new WindowInsetsControllerCompat(window, window.getDecorView());
        insetsController.setAppearanceLightStatusBars(currentNightMode == Configuration.UI_MODE_NIGHT_NO);

        // Прозорий статусбар
        window.setStatusBarColor(Color.TRANSPARENT);
    }


    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Auto-save is now handled through TextWatcher and NotePresenter
    }

    @Override
    public void onStop() {
        super.onStop();
        // КРИТИЧНО: Екстрене збереження при зупинці Activity
        if (notePresenter != null && notePresenter.getNote() != null) {
            String currentTitle = binding != null ? binding.notesTitle.getText().toString() : "";
            String currentValue = binding != null ? binding.valueNote.getText().toString() : "";

            // Оновлюємо дані в моделі
            notePresenter.getNote().setTitle(currentTitle);
            notePresenter.getNote().setValue(currentValue);
            notePresenter.getNote().setValue("");
            notePresenter.getNote().setHasRichContent(false);

            // Якщо є незбережені зміни - робимо екстрене збереження
            ((NotePresenter) notePresenter).performEmergencySaveIfNeeded();
        }
    }

    @Override
    public void initParam() {

    }

    @Override
    public void initTypeActivity() {
        if (notePresenter.getNewNotesKey()) {
            if (notePresenter.getTagNote().length() >= 2)
                changeTag(notePresenter.getTagNote(), false);

            String formattedDate = getString(R.string.lastDateEditNote, lastDayEditNote(new Date().getTime()));
            binding.titleToolbarDataCenter.setText(formattedDate);
            binding.titleToolbarDataCollapsed.setText(lastDayEditNote(new Date().getTime()));

            if (notePresenter.getShareText() != null && notePresenter.getShareText().length() > 5)
                binding.valueNote.setText(notePresenter.getShareText());

            activatedActivity();
        } else if (notePresenter.getIdKey() >= 1) {
            notePresenter.loadingData(notePresenter.getIdKey());
        }
    }


    @Override
    public void initListeners() {
        binding.notesTitle.addTextChangedListener(new TextWatcher() {
            @Override
            protected void changeText(Editable s) {
                if (s.toString().contains("\n")) {
                    binding.notesTitle.setText(s.toString().replace('\n', ' ').trim());
                    binding.valueNote.requestFocus();
                }
                // Оновлюємо заголовок у згорнутому вигляді
                String title = s.toString().trim();
                binding.titleToolbarCollapsed.setText(!title.isEmpty() ? title : getString(R.string.noteTitle));

                // Викликаємо автозбереження при зміні заголовка
                if (notePresenter != null && notePresenter.getNote() != null) {
                    notePresenter.getNote().setTitle(title);
                    notePresenter.getNote().setHasRichContent(false);
                    notePresenter.onTextChanged();
                }
            }
        });

        binding.valueNote.addTextChangedListener(new TextWatcher() {
            @Override
            protected void changeText(Editable s) {
                // Викликаємо автозбереження при зміні тексту
                if (notePresenter != null && notePresenter.getNote() != null) {
                    notePresenter.getNote().setValue(s.toString());
                    notePresenter.getNote().setValueJson("");
                    notePresenter.getNote().setHasRichContent(false);
                    notePresenter.onTextChanged();
                }
            }
        });

        // Додаємо обробник кліку для поля вводу - тільки для обробки переміщення курсора
        binding.valueNote.setOnClickListener(v -> {
            if (binding.valueNote.isFocused() && scrollProgress < 95) {
                binding.valueNote.postDelayed(this::scrollToCursor, 50);
            }
        });

    }

    @Override
    public void editIdNoteCreated(long idNote) {
        notePresenter.getNote().setId(Math.toIntExact(idNote));
    }


    @Override
    public void settingsActionBar() {
        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
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
            // Прокручуємо до курсора після показу клавіатури для нової нотатки
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
    public void onRestart() {
        super.onRestart();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_activity_toolbar_note, menu);
        saveStatusMenuItem = menu.findItem(R.id.saveStatusBut);
        return true;
    }

    @Override
    public void updateSaveStatus(SaveState saveState) {
        if (saveStatusMenuItem == null) return;

        switch (saveState) {
            case IDLE:
                saveStatusMenuItem.setVisible(false);
                break;

            case PENDING:
                saveStatusMenuItem.setVisible(true);
                saveStatusMenuItem.setIcon(R.drawable.ic_save_pending);
                saveStatusMenuItem.setTitle(getString(R.string.saveStatusPending));
                break;

            case SAVING:
                saveStatusMenuItem.setVisible(true);
                saveStatusMenuItem.setIcon(R.drawable.ic_save_saving_animated);
                saveStatusMenuItem.setTitle(getString(R.string.saveStatusSaving));
                break;

            case SAVED:
                saveStatusMenuItem.setVisible(true);
                saveStatusMenuItem.setIcon(R.drawable.ic_save_success);
                saveStatusMenuItem.setTitle(getString(R.string.saveStatusSaved));
                break;

            case ERROR:
                saveStatusMenuItem.setVisible(true);
                saveStatusMenuItem.setIcon(R.drawable.ic_save_error);
                saveStatusMenuItem.setTitle(getString(R.string.saveStatusError));
                break;
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            notePresenter.closeActivity();

        }
        if (item.getItemId() == R.id.moreBut) {
            new MoreNoteDialog(notePresenter.getNewNotesKey() ? new Note().create(binding.notesTitle.getText().toString(), binding.valueNote.getText().toString(), new Date().getTime()) : notePresenter.getNote(), notePresenter.getNewNotesKey(), true, 0).show(getSupportFragmentManager(), "MoreNote");

        }


        return true;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (notePresenter != null) {
            ((NotePresenter) notePresenter).cleanupHandlers();
            notePresenter.detachView();
        }

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
            Log.e("NoteActivity", "Received null note in loadingNote()");
            return;
        }

        String title = note.getTitle();
        String value = note.getValue();

        // Безопасная проверка title
        binding.notesTitle.setText(title != null && !title.isEmpty() ? title : "");
        // Безопасная установка value
        binding.valueNote.setText(value != null ? value : "");

        binding.valueNote.setMovementMethod(new CustomLinkMovementMethod() {
            @Override
            protected void onClickLink(String link, int type) {
                if (link != null) {
                    link = link.replaceAll("mailto:", "").replaceAll("tel:", "");
                    new LinkInfoDialog(link, type).show(getSupportFragmentManager(), "LinkInfoDialog");
                }
            }
        });

        String formattedDate = getString(R.string.lastDateEditNote, lastDayEditNote(note.getDate()));

        // Оновлюємо центровані елементи
        binding.titleToolbarDataCenter.setText(formattedDate);

        // Оновлюємо згорнуті елементи з безпечною перевіркою title
        binding.titleToolbarCollapsed.setText((title != null && !title.isEmpty()) ?
                title : getString(R.string.noteTitle));
        binding.titleToolbarDataCollapsed.setText(formattedDate);

        // Безпечна перевірка tag перед викликом changeTag
        String tag = note.getTag();
        changeTag(tag != null ? tag : "", false);
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
    public void closeActivityNotSaved() {
        notePresenter.setExitNoSave(true);
        finish();
    }

    @Override
    public void changeTag(String nameTag, boolean change) {
        if (change) {
            notePresenter.getNote().setTag(nameTag);
            notePresenter.setTagNote(nameTag);
        }
        if (!nameTag.isEmpty()) {
            String tagText = getString(R.string.tagHastag, nameTag);
            // Оновлюємо центровані елементи
            binding.titleToolbarTagCenter.setText(tagText);
            binding.titleToolbarTagCenter.setVisibility(View.VISIBLE);
            // Оновлюємо згорнуті елементи
            binding.titleToolbarTagCollapsed.setText(tagText);
            binding.titleToolbarTagCollapsed.setVisibility(View.VISIBLE);
        } else {
            binding.titleToolbarTagCenter.setVisibility(View.GONE);
            binding.titleToolbarTagCollapsed.setVisibility(View.GONE);
        }
    }

    @Override
    public void openCopyNote(long idNote) {
        finish();
        startActivity(new Intent(NoteActivity.this, NoteActivity.class).putExtra("NewNote", false).putExtra("idNote", idNote).putExtra("shareText", "").putExtra("tagNote", ""));
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

}
