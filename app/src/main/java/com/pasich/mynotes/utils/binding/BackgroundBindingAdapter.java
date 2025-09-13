package com.pasich.mynotes.utils.binding;

import android.view.View;

import androidx.databinding.BindingAdapter;

import com.pasich.mynotes.data.model.NoteBackground;
import com.pasich.mynotes.utils.backgrounds.BackgroundApplier;

/**
 * BindingAdapter для застосування фону до View через data binding
 */
public class BackgroundBindingAdapter {

    @BindingAdapter("noteBackground")
    public static void setNoteBackground(View view, NoteBackground background) {
        if (background != null) {
            BackgroundApplier.applyBackground(view, background, view.getContext());
        }
    }
}
