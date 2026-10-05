package com.pasich.mynotes.ui.view.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.pasich.mynotes.R;
import com.pasich.mynotes.utils.editor.KeyboardBarState;
import java.util.List;

/**
 * The bar docked above the on-screen keyboard in both note editors: Undo and Redo at the start,
 * Hide keyboard at the end, room for further actions between them.
 *
 * <p>It is shown only while the note is edited and the keyboard is up (see {@link
 * KeyboardBarState}). The host places it at the bottom of its root, which must not be padded at the
 * bottom, and passes every window insets dispatch to {@link #onWindowInsets}; the bar then sits on
 * top of the keyboard, or of the navigation bar, and the host keeps its content clear of it by
 * {@link #getReservedHeight()}.
 *
 * <p>While the keyboard slides in or out the bar is laid out where it ends and translated to follow
 * the keyboard frame by frame, so it moves with the keyboard instead of jumping ahead of it. With
 * animations turned off the keyboard has no animation and neither has the bar.
 */
public class EditorKeyboardBar extends LinearLayout {

    /** What the bar's buttons ask the editor to do. */
    public interface Actions {
        void onUndo();

        void onRedo();

        void onHideKeyboard();
    }

    private final KeyboardBarState state = new KeyboardBarState();
    private View row;
    private MaterialButton undoButton;
    private MaterialButton redoButton;
    private MaterialButton hideButton;
    @Nullable private Actions actions;

    // Distance from the bottom of the parent to the bottom of the bar when it rests: the keyboard,
    // or the navigation bar while the keyboard is down.
    private int restingOffset = 0;
    // A keyboard animation is running; hiding the bar waits for its end so the bar goes down with
    // the keyboard rather than vanishing above it.
    private boolean imeAnimating = false;

    public EditorKeyboardBar(Context context) {
        super(context);
        init(context);
    }

    public EditorKeyboardBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public EditorKeyboardBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        inflate(context, R.layout.view_editor_keyboard_bar, this);
        row = findViewById(R.id.keyboardBarRow);
        undoButton = findViewById(R.id.keyboardBarUndo);
        redoButton = findViewById(R.id.keyboardBarRedo);
        hideButton = findViewById(R.id.keyboardBarHide);
        undoButton.setOnClickListener(
                v -> {
                    if (actions != null) actions.onUndo();
                });
        redoButton.setOnClickListener(
                v -> {
                    if (actions != null) actions.onRedo();
                });
        hideButton.setOnClickListener(
                v -> {
                    if (actions != null) actions.onHideKeyboard();
                });
        setBackgroundColor(
                MaterialColors.getColor(
                        this, com.google.android.material.R.attr.colorSurfaceContainer));
        // Touches on the bar's empty space must not reach the note underneath.
        setClickable(true);
        setVisibility(GONE);
        ViewCompat.setWindowInsetsAnimationCallback(this, new FollowKeyboard());
        applyHistory();
    }

    public void setActions(@Nullable Actions actions) {
        this.actions = actions;
    }

    /** The note is being edited (true) or read. */
    public void setEditing(boolean editing) {
        boolean visibilityChanged = state.setEditing(editing);
        applyHistory();
        if (visibilityChanged) {
            applyVisibility();
            // The host gives the content back, or takes it, in its insets listener.
            ViewCompat.requestApplyInsets(this);
        }
    }

    /** Enables Undo and Redo according to the editor's history. */
    public void setHistoryState(boolean canUndo, boolean canRedo) {
        if (state.setHistory(canUndo, canRedo)) applyHistory();
    }

    /**
     * Takes the window insets the host has just been given: places the bar on top of the keyboard
     * or the navigation bar and shows or hides it with the keyboard. Call it before reading {@link
     * #getReservedHeight()}.
     */
    public void onWindowInsets(@NonNull WindowInsetsCompat insets) {
        Insets bars =
                insets.getInsets(
                        WindowInsetsCompat.Type.systemBars()
                                | WindowInsetsCompat.Type.displayCutout());
        Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
        restingOffset = Math.max(ime.bottom, bars.bottom);

        ViewGroup.LayoutParams params = getLayoutParams();
        if (params instanceof MarginLayoutParams
                && ((MarginLayoutParams) params).bottomMargin != restingOffset) {
            ((MarginLayoutParams) params).bottomMargin = restingOffset;
            setLayoutParams(params);
        }
        // In landscape the navigation bar or a cutout may sit at a side; the buttons stay clear of
        // it while the background still runs edge to edge.
        int side = getResources().getDimensionPixelSize(R.dimen.editor_keyboard_bar_side);
        row.setPadding(bars.left + side, 0, bars.right + side, 0);

        if (state.setImeVisible(insets.isVisible(WindowInsetsCompat.Type.ime()))) {
            applyVisibility();
        }
    }

    /**
     * Height the host keeps free above the keyboard so the bar never covers the caret line: the
     * bar's height while it is shown, otherwise 0.
     */
    public int getReservedHeight() {
        if (!state.isShown()) return 0;
        return getResources().getDimensionPixelSize(R.dimen.editor_keyboard_bar_row_height)
                + getResources().getDimensionPixelSize(R.dimen.editor_keyboard_bar_divider);
    }

    /** Whether the bar is meant to be on screen (it may still be finishing its way out). */
    public boolean isBarShown() {
        return state.isShown();
    }

    @VisibleForTesting
    KeyboardBarState getState() {
        return state;
    }

    private void applyHistory() {
        undoButton.setEnabled(state.isUndoEnabled());
        redoButton.setEnabled(state.isRedoEnabled());
    }

    private void applyVisibility() {
        if (state.isShown()) {
            setVisibility(VISIBLE);
        } else if (!imeAnimating) {
            setTranslationY(0f);
            setVisibility(GONE);
        }
    }

    /**
     * Keeps the bar on top of the keyboard while it animates. The bar is laid out at the keyboard's
     * final position as soon as the animation starts, so it is translated by the distance between
     * that and the keyboard's current top.
     */
    private final class FollowKeyboard extends WindowInsetsAnimationCompat.Callback {

        // Where the bar was drawn when the animation was prepared, measured from the bottom.
        private int startOffset;

        FollowKeyboard() {
            super(DISPATCH_MODE_CONTINUE_ON_SUBTREE);
        }

        @Override
        public void onPrepare(@NonNull WindowInsetsAnimationCompat animation) {
            if (!isIme(animation)) return;
            imeAnimating = true;
            startOffset = restingOffset - Math.round(getTranslationY());
        }

        @NonNull
        @Override
        public WindowInsetsAnimationCompat.BoundsCompat onStart(
                @NonNull WindowInsetsAnimationCompat animation,
                @NonNull WindowInsetsAnimationCompat.BoundsCompat bounds) {
            if (isIme(animation)) setTranslationY(restingOffset - startOffset);
            return bounds;
        }

        @NonNull
        @Override
        public WindowInsetsCompat onProgress(
                @NonNull WindowInsetsCompat insets,
                @NonNull List<WindowInsetsAnimationCompat> runningAnimations) {
            if (!imeAnimating) return insets;
            int ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            setTranslationY(restingOffset - Math.max(ime, bars));
            return insets;
        }

        @Override
        public void onEnd(@NonNull WindowInsetsAnimationCompat animation) {
            if (!isIme(animation)) return;
            imeAnimating = false;
            setTranslationY(0f);
            applyVisibility();
        }

        private boolean isIme(WindowInsetsAnimationCompat animation) {
            return (animation.getTypeMask() & WindowInsetsCompat.Type.ime()) != 0;
        }
    }
}
