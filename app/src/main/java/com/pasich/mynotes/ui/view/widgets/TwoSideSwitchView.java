package com.pasich.mynotes.ui.view.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

import com.pasich.mynotes.R;

public class TwoSideSwitchView extends LinearLayout {

    private ImageView leftIcon;
    private ImageView rightIcon;
    private SwitchCompat switchView;
    @ColorInt
    private int leftActiveColor;
    @ColorInt
    private int rightActiveColor;
    @ColorInt
    private int inactiveColor;
    private int currentThumbColor;
    private Mode currentMode = Mode.INACTIVE;
    private OnModeChangedListener modeChangedListener;
    private boolean suppressCallback = false;


    public TwoSideSwitchView(Context context) {
        super(context);
        init(context, null);
    }

    public TwoSideSwitchView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public TwoSideSwitchView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public void setOnModeChangedListener(OnModeChangedListener listener) {
        this.modeChangedListener = listener;
    }

    private void notifyModeChanged() {
        if (suppressCallback) return;
        if (modeChangedListener != null) {
            modeChangedListener.onModeChanged(currentMode);
        }
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        setOrientation(HORIZONTAL);
        inflate(context, R.layout.view_two_side_switch, this);

        leftIcon = findViewById(R.id.leftIcon);
        rightIcon = findViewById(R.id.rightIcon);
        switchView = findViewById(R.id.centerSwitch);

        leftActiveColor = Color.parseColor("#4CAF50");
        rightActiveColor = Color.parseColor("#F44336");
        inactiveColor = Color.parseColor("#9E9E9E");

        // ---------- READ ATTRIBUTES ----------
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TwoSideSwitchView);

            if (a.hasValue(R.styleable.TwoSideSwitchView_leftIcon)) {
                leftIcon.setImageDrawable(a.getDrawable(R.styleable.TwoSideSwitchView_leftIcon));
            }

            if (a.hasValue(R.styleable.TwoSideSwitchView_rightIcon)) {
                rightIcon.setImageDrawable(a.getDrawable(R.styleable.TwoSideSwitchView_rightIcon));
            }

            leftActiveColor = a.getColor(R.styleable.TwoSideSwitchView_leftActiveColor, leftActiveColor);
            rightActiveColor = a.getColor(R.styleable.TwoSideSwitchView_rightActiveColor, rightActiveColor);
            inactiveColor = a.getColor(R.styleable.TwoSideSwitchView_inactiveColor, inactiveColor);

            int modeValue = a.getInt(R.styleable.TwoSideSwitchView_modeEditor, 0);
            switch (modeValue) {
                case 1:
                    currentMode = Mode.SIMPLE;
                    break;
                case 2:
                    currentMode = Mode.EXTENDED;
                    break;
                default:
                    currentMode = Mode.INACTIVE;
            }

            a.recycle();
        }

        // ---------- APPLY STARTUP STATE ----------
        switch (currentMode) {
            case EXTENDED:
                switchView.setChecked(true);
                currentThumbColor = rightActiveColor;
                break;

            case SIMPLE:
                switchView.setChecked(false);
                currentThumbColor = leftActiveColor;
                break;

            case INACTIVE:
            default:
                switchView.setChecked(false);
                currentThumbColor = inactiveColor;
                break;
        }

        switchView.getTrackDrawable().setTint(inactiveColor);

        post(() -> applyCurrentState(true));

        // ---------- LISTENERS ----------
        switchView.setOnCheckedChangeListener((btn, checked) -> {
            if (currentMode == Mode.INACTIVE) return;
            currentMode = checked ? Mode.EXTENDED : Mode.SIMPLE;
            applyCurrentState(false);
            notifyModeChanged();
        });

        leftIcon.setOnClickListener(v -> {
            if (currentMode == Mode.INACTIVE) return;
            currentMode = Mode.SIMPLE;
            switchView.setChecked(false);
            applyCurrentState(false);
            notifyModeChanged();
        });

        rightIcon.setOnClickListener(v -> {
            if (currentMode == Mode.INACTIVE) return;
            currentMode = Mode.EXTENDED;
            switchView.setChecked(true);
            applyCurrentState(false);
            notifyModeChanged();
        });
        applyInteractionState();
    }

    private void applyInteractionState() {
        boolean active = currentMode != Mode.INACTIVE;

        leftIcon.setEnabled(active);
        rightIcon.setEnabled(active);
        switchView.setEnabled(active);

        setEnabled(active);
        setClickable(active);
        setFocusable(active);
    }

    private void applyCurrentState(boolean instant) {
        switch (currentMode) {

            case EXTENDED:
                animateIconTint(leftIcon, getIconTint(leftIcon), inactiveColor, instant);
                animateIconTint(rightIcon, getIconTint(rightIcon), rightActiveColor, instant);
                animateThumbTint(currentThumbColor, rightActiveColor, instant);
                currentThumbColor = rightActiveColor;
                break;

            case SIMPLE:
                animateIconTint(leftIcon, getIconTint(leftIcon), leftActiveColor, instant);
                animateIconTint(rightIcon, getIconTint(rightIcon), inactiveColor, instant);
                animateThumbTint(currentThumbColor, leftActiveColor, instant);
                currentThumbColor = leftActiveColor;
                break;

            case INACTIVE:
            default:
                animateIconTint(leftIcon, getIconTint(leftIcon), inactiveColor, instant);
                animateIconTint(rightIcon, getIconTint(rightIcon), inactiveColor, instant);
                animateThumbTint(currentThumbColor, inactiveColor, instant);
                currentThumbColor = inactiveColor;
                break;
        }
    }

    private void animateIconTint(ImageView v, @ColorInt int from, @ColorInt int to, boolean instant) {
        if (instant) {
            v.setImageTintList(ColorStateList.valueOf(to));
            return;
        }
        ValueAnimator animator = ValueAnimator.ofArgb(from, to);
        animator.setDuration(200);
        animator.addUpdateListener(a ->
                v.setImageTintList(ColorStateList.valueOf((int) a.getAnimatedValue()))
        );
        animator.start();
    }

    private int getIconTint(ImageView v) {
        ColorStateList tint = v.getImageTintList();
        return tint != null ? tint.getDefaultColor() : inactiveColor;
    }

    private void animateThumbTint(@ColorInt int from, @ColorInt int to, boolean instant) {
        if (instant) {
            switchView.getThumbDrawable().setTint(to);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofArgb(from, to);
        animator.setDuration(200);
        animator.addUpdateListener(a ->
                switchView.getThumbDrawable().setTint((int) a.getAnimatedValue())
        );
        animator.start();
    }

    // Public API
    public Mode getMode() {
        return currentMode;
    }

    public void setMode(Mode mode) {
        suppressCallback = true;
        this.currentMode = mode;
        switchView.setChecked(mode == Mode.EXTENDED);
        applyCurrentState(true);
        applyInteractionState();
        suppressCallback = false;
    }

    public enum Mode {
        INACTIVE,
        SIMPLE,
        EXTENDED
    }

    public interface OnModeChangedListener {
        void onModeChanged(Mode mode);
    }
}
