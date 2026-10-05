package com.pasich.mynotes.utils.recycler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;
import java.util.function.BooleanSupplier;

/**
 * Item animator for the notes grid.
 *
 * <p>Change animations are off: a card whose text changed is rebound in place instead of being
 * cross-faded with a second copy of itself, which in a staggered grid with a different height
 * showed up as one note drawn over another. Whenever {@code allowed} says no (the screen is not
 * visible, a return transition is running, or the whole list is being swapped behind a fade) every
 * change is applied at once, without animation.
 */
public class NotesItemAnimator extends DefaultItemAnimator {

    private final BooleanSupplier allowed;

    public NotesItemAnimator(@NonNull BooleanSupplier allowed) {
        this.allowed = allowed;
        setSupportsChangeAnimations(false);
    }

    @Override
    public boolean animateAppearance(
            @NonNull RecyclerView.ViewHolder holder,
            @Nullable ItemHolderInfo preLayoutInfo,
            @NonNull ItemHolderInfo postLayoutInfo) {
        if (skip(holder)) return false;
        return super.animateAppearance(holder, preLayoutInfo, postLayoutInfo);
    }

    @Override
    public boolean animateDisappearance(
            @NonNull RecyclerView.ViewHolder holder,
            @NonNull ItemHolderInfo preLayoutInfo,
            @Nullable ItemHolderInfo postLayoutInfo) {
        if (skip(holder)) return false;
        return super.animateDisappearance(holder, preLayoutInfo, postLayoutInfo);
    }

    @Override
    public boolean animatePersistence(
            @NonNull RecyclerView.ViewHolder holder,
            @NonNull ItemHolderInfo preLayoutInfo,
            @NonNull ItemHolderInfo postLayoutInfo) {
        if (skip(holder)) return false;
        return super.animatePersistence(holder, preLayoutInfo, postLayoutInfo);
    }

    @Override
    public boolean animateChange(
            @NonNull RecyclerView.ViewHolder oldHolder,
            @NonNull RecyclerView.ViewHolder newHolder,
            @NonNull ItemHolderInfo preLayoutInfo,
            @NonNull ItemHolderInfo postLayoutInfo) {
        if (!allowed.getAsBoolean()) {
            dispatchAnimationFinished(oldHolder);
            if (newHolder != oldHolder) dispatchAnimationFinished(newHolder);
            return false;
        }
        return super.animateChange(oldHolder, newHolder, preLayoutInfo, postLayoutInfo);
    }

    private boolean skip(RecyclerView.ViewHolder holder) {
        if (allowed.getAsBoolean()) return false;
        holder.itemView.setAlpha(1f);
        holder.itemView.setTranslationX(0f);
        holder.itemView.setTranslationY(0f);
        dispatchAnimationFinished(holder);
        return true;
    }
}
