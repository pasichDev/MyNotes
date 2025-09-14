package com.pasich.mynotes.utils.binding;

import android.util.Log;
import android.view.View;

import androidx.databinding.BindingAdapter;

import com.pasich.mynotes.data.model.NoteBackground;
import com.pasich.mynotes.utils.backgrounds.BackgroundApplier;

/**
 * BindingAdapter для застосування фону до View через data binding
 */
public class BackgroundBindingAdapter {
    
    private static final String TAG = "BackgroundBindingAdapter";
    
    @BindingAdapter("noteBackground")
    public static void setNoteBackground(View view, NoteBackground background) {
        Log.d(TAG, "setNoteBackground called with view: " + view + ", background: " + background);
        if (background != null) {
            Log.d(TAG, "Applying background to card: " + background.toString());
            BackgroundApplier.applyBackground(view, background, view.getContext());
        } else {
            Log.d(TAG, "Background is null, skipping");
        }
    }
}
