package com.pasich.mynotes.ui.view.widgets;

import android.content.Context;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * The More sheet's quick actions: an icon over a label each, side by side. When a label would need
 * more than {@link #MAX_LINES} lines, or a word is wider than its cell (a long word in a large
 * font), the actions go two to a row instead, so a label is never cut off or broken inside a word.
 */
public class QuickActionsRow extends ViewGroup {

    static final int MAX_LINES = 2;

    private final List<View> shown = new ArrayList<>();
    private int columns = 1;
    private final List<Integer> rowHeights = new ArrayList<>();

    public QuickActionsRow(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int inner = width - getPaddingLeft() - getPaddingRight();
        shown.clear();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) shown.add(child);
        }
        columns = Math.max(1, shown.size());
        if (columns > 2 && !allFit(inner / columns)) columns = 2;
        int cell = inner / columns;
        rowHeights.clear();
        int height = getPaddingTop() + getPaddingBottom();
        for (int i = 0; i < shown.size(); i += columns) {
            int rowHeight = 0;
            for (int j = i; j < Math.min(shown.size(), i + columns); j++) {
                View child = shown.get(j);
                child.measure(
                        MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
            }
            rowHeights.add(rowHeight);
            height += rowHeight;
        }
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int inner = getWidth() - getPaddingLeft() - getPaddingRight();
        int cell = inner / columns;
        int y = getPaddingTop();
        for (int row = 0; row < rowHeights.size(); row++) {
            int first = row * columns;
            int count = Math.min(columns, shown.size() - first);
            // A last row that is not full is centred.
            int x = getPaddingLeft() + (inner - count * cell) / 2;
            for (int j = first; j < first + count; j++) {
                View child = shown.get(j);
                child.layout(x, y, x + cell, y + rowHeights.get(row));
                x += cell;
            }
            y += rowHeights.get(row);
        }
    }

    private boolean allFit(int cell) {
        for (View child : shown) {
            TextView label = labelOf(child);
            if (label == null) continue;
            int width = cell - child.getPaddingLeft() - child.getPaddingRight();
            TextPaint paint = label.getPaint();
            if (!fits(label.getText().toString(), width, paint::measureText)) return false;
        }
        return true;
    }

    @Nullable
    private static TextView labelOf(View child) {
        if (child instanceof TextView text) return text;
        if (child instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                if (group.getChildAt(i) instanceof TextView text) return text;
            }
        }
        return null;
    }

    /**
     * Whether {@code text}, wrapped only between words, takes at most {@link #MAX_LINES} lines of
     * {@code width} with no word wider than a line.
     */
    @VisibleForTesting
    public static boolean fits(
            @NonNull String text, int width, @NonNull ToDoubleFunction<String> measure) {
        String[] words = text.trim().split("\\s+");
        int lines = 1;
        String line = "";
        for (String word : words) {
            if (measure.applyAsDouble(word) > width) return false;
            String joined = line.isEmpty() ? word : line + " " + word;
            if (measure.applyAsDouble(joined) <= width) {
                line = joined;
            } else {
                lines++;
                line = word;
            }
        }
        return lines <= MAX_LINES;
    }
}
