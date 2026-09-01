package com.pasich.mynotes.cache;

import android.util.Log;
import androidx.appcompat.app.AppCompatDelegate;
import com.pasich.mynotes.data.preferences.SafePreferences;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;
import com.pasich.mynotes.utils.themes.ThemesArray;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Optimized cache for theme preferences to reduce SharedPreferences I/O operations. Uses
 * asynchronous PowerPreference methods to avoid UI blocking. Performance improvements: - Single
 * initialization load from SharedPreferences - Memory-based getters for fast access - Asynchronous
 * put operations to prevent UI blocking - Thread-safe operations with synchronized methods -
 * Comprehensive error handling with user feedback
 */
@Singleton
public class ThemePreferencesCache {

    private static final String TAG = "ThemePreferencesCache";
    private final SafePreferences prefs;
    // Cached values (volatile for thread safety)
    private volatile int themeMode;
    private volatile int themeId;
    private volatile boolean dynamicColor;
    private volatile boolean screenProtection;
    private volatile boolean extendedEditor;
    private volatile boolean initialized = false;
    private volatile String typeFaceNoteActivity;
    private volatile int sizeTextNoteActivity;
    private volatile float uiFontScale;

    @Inject
    public ThemePreferencesCache(SafePreferences prefs) {
        this.prefs = prefs;
    }

    /**
     * Initialize cache by loading values from SharedPreferences. Should be called once in
     * Application.onCreate()
     */
    public synchronized void initialize() {
        if (initialized) {
            Log.d(TAG, "Cache already initialized");
            return;
        }

        try {
            // Load all values from SharedPreferences in one batch
            themeMode =
                    prefs.getInt(
                            PreferencesConfig.ARGUMENT_PREFERENCE_THEME_MODE,
                            PreferencesConfig.ARGUMENT_DEFAULT_THEME_MODE_VALUE);

            themeId =
                    prefs.getInt(
                            PreferencesConfig.ARGUMENT_PREFERENCE_THEME,
                            PreferencesConfig.ARGUMENT_DEFAULT_THEME_VALUE);

            dynamicColor =
                    prefs.getBoolean(
                            PreferencesConfig.ARGUMENT_PREFERENCE_DYNAMIC_COLOR,
                            PreferencesConfig.ARGUMENT_DEFAULT_DYNAMIC_COLOR_VALUE);

            screenProtection =
                    prefs.getBoolean(
                            PreferencesConfig.ARGUMENT_PREFERENCE_SCREEN_PROTECTION,
                            PreferencesConfig.ARGUMENT_DEFAULT_SCREEN_PROTECTION_VALUE);

            extendedEditor =
                    prefs.getBoolean(
                            PreferencesConfig.ARGUMENT_PREFERENCE_EXTENDED_EDITOR,
                            PreferencesConfig.ARGUMENT_DEFAULT_EXTENDED_EDITOR_VALUE);

            typeFaceNoteActivity =
                    prefs.getString(
                            PreferencesConfig.ARGUMENT_PREFERENCE_TEXT_STYLE,
                            PreferencesConfig.ARGUMENT_DEFAULT_TEXT_STYLE);

            int savedTextSize =
                    prefs.getInt(
                            PreferencesConfig.ARGUMENT_PREFERENCE_TEXT_SIZE,
                            PreferencesConfig.ARGUMENT_DEFAULT_TEXT_SIZE);
            sizeTextNoteActivity = PreferencesConfig.normalizeNoteTextSize(savedTextSize);
            if (savedTextSize != sizeTextNoteActivity) {
                prefs.putInt(PreferencesConfig.ARGUMENT_PREFERENCE_TEXT_SIZE, sizeTextNoteActivity);
            }

            uiFontScale =
                    prefs.getFloat(
                            PreferencesConfig.ARGUMENT_PREFERENCE_UI_SCALING,
                            PreferencesConfig.ARGUMENT_DEFAULT_UI_SCALING_VALUE);
            initialized = true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize cache", e);
            setDefaults();
        }
    }

    /** Set default values if initialization fails */
    private void setDefaults() {
        themeMode = PreferencesConfig.ARGUMENT_DEFAULT_THEME_MODE_VALUE;
        themeId = PreferencesConfig.ARGUMENT_DEFAULT_THEME_VALUE;
        dynamicColor = PreferencesConfig.ARGUMENT_DEFAULT_DYNAMIC_COLOR_VALUE;
        screenProtection = PreferencesConfig.ARGUMENT_DEFAULT_SCREEN_PROTECTION_VALUE;
        extendedEditor = PreferencesConfig.ARGUMENT_DEFAULT_EXTENDED_EDITOR_VALUE;
        typeFaceNoteActivity = PreferencesConfig.ARGUMENT_DEFAULT_TEXT_STYLE;
        sizeTextNoteActivity = PreferencesConfig.ARGUMENT_DEFAULT_TEXT_SIZE;
        uiFontScale = PreferencesConfig.ARGUMENT_DEFAULT_UI_SCALING_VALUE;
        initialized = true;
        Log.d(TAG, "Set default values after initialization failure");
    }

    /** Get cached theme mode (0=System, 1=Light, 2=Dark) */
    public int getThemeMode() {
        ensureInitialized();
        return themeMode;
    }

    /** Set theme mode with asynchronous persistence */
    public synchronized void setThemeMode(int themeMode) {
        try {
            this.themeMode = themeMode;
            // Use asynchronous put method to avoid UI blocking
            prefs.putInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME_MODE, themeMode);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set theme mode", e);
        }
    }

    /** Get cached theme ID */
    public int getThemeId() {
        ensureInitialized();
        return themeId;
    }

    /** Set theme ID with asynchronous persistence */
    public synchronized void setThemeId(int themeId) {
        try {
            this.themeId = themeId;
            // Use asynchronous put method to avoid UI blocking
            prefs.putInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME, themeId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set theme ID", e);
        }
    }

    /** Get cached dynamic color setting */
    public boolean isDynamicColorEnabled() {
        ensureInitialized();
        return dynamicColor;
    }

    /** Get cached screen protection setting */
    public boolean isScreenProtectionEnabled() {
        ensureInitialized();
        return screenProtection;
    }

    /** Get cached extended editor setting */
    public boolean isExtendedEditorEnabled() {
        ensureInitialized();
        return extendedEditor;
    }

    public int getSizeTextNoteActivity() {
        ensureInitialized();
        return sizeTextNoteActivity;
    }

    public synchronized void setSizeTextNoteActivity(int size) {
        try {
            this.sizeTextNoteActivity = PreferencesConfig.normalizeNoteTextSize(size);
            prefs.putInt(
                    PreferencesConfig.ARGUMENT_PREFERENCE_TEXT_SIZE, this.sizeTextNoteActivity);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set size text note", e);
        }
    }

    public float getUiFontScale() {
        ensureInitialized();
        return uiFontScale;
    }

    public synchronized void setUiFontScale(float value) {
        try {
            this.uiFontScale = value;
            prefs.putFloat(PreferencesConfig.ARGUMENT_PREFERENCE_UI_SCALING, uiFontScale);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set Ui Font Scale note", e);
        }
    }

    public String getTypeFaceNoteActivity() {
        ensureInitialized();
        return typeFaceNoteActivity;
    }

    public synchronized void setTypeFaceNoteActivity(String typeFace) {
        try {
            this.typeFaceNoteActivity = typeFace;
            prefs.putString(PreferencesConfig.ARGUMENT_PREFERENCE_TEXT_STYLE, typeFace);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set type font note note", e);
        }
    }

    /** Set dynamic color with asynchronous persistence */
    public synchronized void setDynamicColor(boolean enabled) {
        try {
            this.dynamicColor = enabled;
            prefs.putBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_DYNAMIC_COLOR, enabled);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set dynamic color", e);
        }
    }

    /** Set screen protection with asynchronous persistence */
    public synchronized void setScreenProtection(boolean enabled) {
        try {
            this.screenProtection = enabled;
            prefs.putBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_SCREEN_PROTECTION, enabled);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set screen protection", e);
        }
    }

    /** Set extended editor with asynchronous persistence */
    public synchronized void setExtendedEditor(boolean enabled) {
        try {
            this.extendedEditor = enabled;
            prefs.putBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_EXTENDED_EDITOR, enabled);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set extended editor", e);
        }
    }

    // Helper methods

    /**
     * Apply current theme mode to AppCompatDelegate Safe method with fallback for early calls
     * before DI initialization
     */
    public void applyCurrentThemeMode() {
        if (!initialized) {
            try {
                int fallbackThemeMode =
                        prefs.getInt(
                                PreferencesConfig.ARGUMENT_PREFERENCE_THEME_MODE,
                                PreferencesConfig.ARGUMENT_DEFAULT_THEME_MODE_VALUE);
                applyThemeModeInternal(fallbackThemeMode);
                Log.d(TAG, "Applied fallback theme mode: " + fallbackThemeMode);
            } catch (Exception e) {
                Log.e(TAG, "Failed to read theme mode, using default", e);
                applyThemeModeInternal(PreferencesConfig.ARGUMENT_DEFAULT_THEME_MODE_VALUE);
            }
            return;
        }

        applyThemeModeInternal(themeMode);
    }

    /** Internal method to apply theme mode */
    private void applyThemeModeInternal(int themeModeValue) {
        switch (themeModeValue) {
            case 0: // Follow System
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case 1: // Light
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case 2: // Dark
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    /** Get current theme style resource ID */
    public int getCurrentThemeStyle() {
        ensureInitialized();
        return new ThemesArray().getThemeStyle(themeId);
    }

    /** Clear cache and reload from SharedPreferences */
    public synchronized void refresh() {
        initialized = false;
        initialize();
        Log.d(TAG, "Cache refreshed");
    }

    /** Check if cache is initialized, initialize if needed */
    private void ensureInitialized() {
        if (!initialized) {
            Log.w(TAG, "Cache not initialized, initializing now");
            initialize();
        }
    }
}
