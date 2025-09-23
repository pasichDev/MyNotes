package com.pasich.mynotes.utils.tool;

import static com.pasich.mynotes.utils.constants.settings.PreferencesConfig.ARGUMENT_DEFAULT_TEXT_STYLE;

import android.widget.ImageButton;

import com.pasich.mynotes.R;
import com.pasich.mynotes.cache.ThemePreferencesCache;

import javax.inject.Inject;

import dagger.hilt.android.scopes.FragmentScoped;

@FragmentScoped
public class TextStyleTool {

    private ImageButton mButton;

    private final ThemePreferencesCache cache;

    @Inject
    public TextStyleTool(ThemePreferencesCache cache) {
        this.cache = cache;
        this.cache.initialize();
    }

    public void addButton(ImageButton button) {
        this.mButton = button;
        mButton.setImageResource(getLoadSrcDrawable(getArgPreference()));
    }

    private String getArgPreference() {
        return cache.getTypeFaceNoteActivity();
    }


    public void changeArgument() {
        if (mButton != null) {
            switch (getArgPreference()) {
                case ARGUMENT_DEFAULT_TEXT_STYLE -> {
                    //selected italic
                    mButton.setImageResource(getLoadSrcDrawable("italic"));
                    cache.setTypeFaceNoteActivity("italic");
                }
                case "italic" -> {
                    //selected bold
                    mButton.setImageResource(getLoadSrcDrawable("bold"));
                    cache.setTypeFaceNoteActivity("bold");
                }
                case "bold" -> {
                    //selected normal
                    mButton.setImageResource(getLoadSrcDrawable("normal"));
                    cache.setTypeFaceNoteActivity("normal");
                }
            }
        }
    }


    private int getLoadSrcDrawable(String param) {
        int NORMAL_ICON = R.drawable.ic_style_normal;
        int ITALIC_ICON = R.drawable.ic_style_italic;
        int BOLD_ICON = R.drawable.ic_style_bold;
        return switch (param) {
            case "italic" -> ITALIC_ICON;
            case "bold" -> BOLD_ICON;
            default -> NORMAL_ICON;
        };

    }
}
