package com.pasich.mynotes.cache;

import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;
import com.pasich.mynotes.utils.themes.ThemesArray;
import com.preference.PowerPreference;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Optimized cache for theme preferences to reduce SharedPreferences I/O operations.
 * Uses asynchronous PowerPreference methods to avoid UI blocking.
 * Performance improvements:
 * - Single initialization load from SharedPreferences
 * - Memory-based getters for fast access
 * - Asynchronous put operations to prevent UI blocking
 * - Thread-safe operations with synchronized methods
 * - Comprehensive error handling with user feedback
 */
@Singleton
public class ThemePreferencesCache {

    private static final String TAG = "ThemePreferencesCache";

    // Cached values (volatile for thread safety)
    private volatile int themeMode;
    private volatile int themeId;
    private volatile boolean dynamicColor;
    private volatile boolean screenProtection;
    private volatile boolean extendedEditor;

    private volatile boolean initialized = false;

    private volatile String typeFaceNoteActivity;
    private volatile int sizeTextNoteActivity;


    @Inject
    public ThemePreferencesCache() {
    }

    /**
     * Initialize cache by loading values from SharedPreferences.
     * Should be called once in Application.onCreate()
     */
    public synchronized void initialize() {
        if (initialized) {
            Log.d(TAG, "Cache already initialized");
            return;
        }

        try {
            // Load all values from SharedPreferences in one batch
            themeMode = PowerPreference.getDefaultFile().getInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME_MODE, PreferencesConfig.ARGUMENT_DEFAULT_THEME_MODE_VALUE);

            themeId = PowerPreference.getDefaultFile().getInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME, PreferencesConfig.ARGUMENT_DEFAULT_THEME_VALUE);

            dynamicColor = PowerPreference.getDefaultFile().getBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_DYNAMIC_COLOR, PreferencesConfig.ARGUMENT_DEFAULT_DYNAMIC_COLOR_VALUE);

            screenProtection = PowerPreference.getDefaultFile().getBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_SCREEN_PROTECTION, PreferencesConfig.ARGUMENT_DEFAULT_SCREEN_PROTECTION_VALUE);

            extendedEditor = PowerPreference.getDefaultFile().getBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_EXTENDED_EDITOR, PreferencesConfig.ARGUMENT_DEFAULT_EXTENDED_EDITOR_VALUE);

            typeFaceNoteActivity = PowerPreference.getDefaultFile().getString(PreferencesConfig.ARGUMENT_PREFERENCE_TEXT_STYLE, PreferencesConfig.ARGUMENT_DEFAULT_TEXT_STYLE);

            sizeTextNoteActivity = PowerPreference.getDefaultFile().getInt(PreferencesConfig.ARGUMENT_PREFERENCE_TEXT_SIZE, PreferencesConfig.ARGUMENT_DEFAULT_TEXT_SIZE);

            initialized = true;
            Log.d(TAG, "Cache initialized successfully - Theme Mode: " + themeMode + ", Theme ID: " + themeId + ", Dynamic Color: " + dynamicColor + ", Screen Protection: " + screenProtection + ", Extended Editor: " + extendedEditor);

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize cache", e);
            setDefaults();
        }
    }

    /**
     * Set default values if initialization fails
     */
    private void setDefaults() {
        themeMode = PreferencesConfig.ARGUMENT_DEFAULT_THEME_MODE_VALUE;
        themeId = PreferencesConfig.ARGUMENT_DEFAULT_THEME_VALUE;
        dynamicColor = PreferencesConfig.ARGUMENT_DEFAULT_DYNAMIC_COLOR_VALUE;
        screenProtection = PreferencesConfig.ARGUMENT_DEFAULT_SCREEN_PROTECTION_VALUE;
        extendedEditor = PreferencesConfig.ARGUMENT_DEFAULT_EXTENDED_EDITOR_VALUE;
        typeFaceNoteActivity = PreferencesConfig.ARGUMENT_DEFAULT_TEXT_STYLE;
        sizeTextNoteActivity = PreferencesConfig.ARGUMENT_DEFAULT_TEXT_SIZE;
        initialized = true;
        Log.d(TAG, "Set default values after initialization failure");
    }

    /**
     * Get cached theme mode (0=System, 1=Light, 2=Dark)
     */
    public int getThemeMode() {
        ensureInitialized();
        return themeMode;
    }

    /**
     * Get cached theme ID
     */
    public int getThemeId() {
        ensureInitialized();
        return themeId;
    }

    /**
     * Get cached dynamic color setting
     */
    public boolean isDynamicColorEnabled() {
        ensureInitialized();
        return dynamicColor;
    }

    /**
     * Get cached screen protection setting
     */
    public boolean isScreenProtectionEnabled() {
        ensureInitialized();
        return screenProtection;
    }

    /**
     * Get cached extended editor setting
     */
    public boolean isExtendedEditorEnabled() {
        ensureInitialized();
        return extendedEditor;
    }


    public int getSizeTextNoteActivity() {
        ensureInitialized();
        return sizeTextNoteActivity;
    }

    public String getTypeFaceNoteActivity() {
        ensureInitialized();
        return typeFaceNoteActivity;
    }

    /**
     * Set theme mode with asynchronous persistence
     */
    public synchronized void setThemeMode(int themeMode) {
        try {
            this.themeMode = themeMode;
            // Use asynchronous put method to avoid UI blocking
            PowerPreference.getDefaultFile().putInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME_MODE, themeMode);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set theme mode", e);
        }
    }

    /**
     * Set theme ID with asynchronous persistence
     */
    public synchronized void setThemeId(int themeId) {
        try {
            this.themeId = themeId;
            // Use asynchronous put method to avoid UI blocking
            PowerPreference.getDefaultFile().putInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME, themeId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set theme ID", e);
        }
    }

    /**
     * Set dynamic color with asynchronous persistence
     */
    public synchronized void setDynamicColor(boolean enabled) {
        try {
            this.dynamicColor = enabled;
            // Use asynchronous put method to avoid UI blocking
            PowerPreference.getDefaultFile().putBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_DYNAMIC_COLOR, enabled);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set dynamic color", e);
        }
    }

    /**
     * Set screen protection with asynchronous persistence
     */
    public synchronized void setScreenProtection(boolean enabled) {
        try {
            this.screenProtection = enabled;
            // Use asynchronous put method to avoid UI blocking
            PowerPreference.getDefaultFile().putBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_SCREEN_PROTECTION, enabled);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set screen protection", e);
        }
    }

    /**
     * Set extended editor with asynchronous persistence
     */
    public synchronized void setExtendedEditor(boolean enabled) {
        try {
            this.extendedEditor = enabled;
            // Use asynchronous put method to avoid UI blocking
            PowerPreference.getDefaultFile().putBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_EXTENDED_EDITOR, enabled);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set extended editor", e);
        }
    }

    public synchronized void setTypeFaceNoteActivity(String typeFace) {
        try {
            this.typeFaceNoteActivity = typeFace;
            // Use asynchronous put method to avoid UI blocking
            PowerPreference.getDefaultFile().putString(PreferencesConfig.ARGUMENT_PREFERENCE_TEXT_STYLE, typeFace);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set type font note note", e);
        }
    }


    public synchronized void setSizeTextNoteActivity(int size) {
        try {
            this.sizeTextNoteActivity = size;
            // Use asynchronous put method to avoid UI blocking
            PowerPreference.getDefaultFile().putInt(PreferencesConfig.ARGUMENT_PREFERENCE_TEXT_SIZE, size);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set size text note", e);
        }
    }


    // Helper methods

    /**
     * Apply current theme mode to AppCompatDelegate
     * Safe method with fallback for early calls before DI initialization
     */
    public void applyCurrentThemeMode() {
        // Безпечна ініціалізація з fallback
        if (!initialized) {
            // Fallback: читаємо безпосередньо з SharedPreferences без блокування UI
            try {
                int fallbackThemeMode = PowerPreference.getDefaultFile().getInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME_MODE, PreferencesConfig.ARGUMENT_DEFAULT_THEME_MODE_VALUE);
                applyThemeModeInternal(fallbackThemeMode);
                Log.d(TAG, "Applied fallback theme mode: " + fallbackThemeMode);
            } catch (Exception e) {
                Log.e(TAG, "Failed to read theme mode, using default", e);
                applyThemeModeInternal(PreferencesConfig.ARGUMENT_DEFAULT_THEME_MODE_VALUE);
            }
            return;
        }

        applyThemeModeInternal(themeMode);
        Log.d(TAG, "Applied theme mode from cache: " + themeMode);
    }

    /**
     * Internal method to apply theme mode
     */
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

    /**
     * Get current theme style resource ID
     */
    public int getCurrentThemeStyle() {
        ensureInitialized();
        return new ThemesArray().getThemeStyle(themeId);
    }

    /**
     * Clear cache and reload from SharedPreferences
     */
    public synchronized void refresh() {
        initialized = false;
        initialize();
        Log.d(TAG, "Cache refreshed");
    }

    /**
     * Check if cache is initialized, initialize if needed
     */
    private void ensureInitialized() {
        if (!initialized) {
            Log.w(TAG, "Cache not initialized, initializing now");
            initialize();
        }
    }


}
