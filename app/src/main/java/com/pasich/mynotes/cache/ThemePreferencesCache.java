package com.pasich.mynotes.cache;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

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
    
    private final Context context;
    private final Handler mainHandler;
    
    // Cached values (volatile for thread safety)
    private volatile int themeMode;
    private volatile int themeId;
    private volatile boolean dynamicColor;
    private volatile boolean screenProtection;
    private volatile boolean initialized = false;
    
    @Inject
    public ThemePreferencesCache(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
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
            themeMode = PowerPreference.getDefaultFile().getInt(
                PreferencesConfig.ARGUMENT_PREFERENCE_THEME_MODE, 
                PreferencesConfig.ARGUMENT_DEFAULT_THEME_MODE_VALUE
            );
            
            themeId = PowerPreference.getDefaultFile().getInt(
                PreferencesConfig.ARGUMENT_PREFERENCE_THEME, 
                PreferencesConfig.ARGUMENT_DEFAULT_THEME_VALUE
            );
            
            dynamicColor = PowerPreference.getDefaultFile().getBoolean(
                PreferencesConfig.ARGUMENT_PREFERENCE_DYNAMIC_COLOR, 
                PreferencesConfig.ARGUMENT_DEFAULT_DYNAMIC_COLOR_VALUE
            );
            
            screenProtection = PowerPreference.getDefaultFile().getBoolean(
                PreferencesConfig.ARGUMENT_PREFERENCE_SCREEN_PROTECTION, 
                PreferencesConfig.ARGUMENT_DEFAULT_SCREEN_PROTECTION_VALUE
            );
            
            initialized = true;
            Log.d(TAG, "Cache initialized successfully - Theme Mode: " + themeMode + 
                      ", Theme ID: " + themeId + ", Dynamic Color: " + dynamicColor + 
                      ", Screen Protection: " + screenProtection);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize cache", e);
            // Set default values on failure
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
        initialized = true;
        Log.d(TAG, "Set default values after initialization failure");
    }
    
    // Fast memory-based getters
    
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
    
    // Optimized setters with asynchronous persistence
    
    /**
     * Set theme mode with asynchronous persistence
     */
    public synchronized void setThemeMode(int themeMode) {
        try {
            this.themeMode = themeMode;
            // Use asynchronous put method to avoid UI blocking
            PowerPreference.getDefaultFile().putInt(
                PreferencesConfig.ARGUMENT_PREFERENCE_THEME_MODE, 
                themeMode
            );
            Log.d(TAG, "Theme mode updated to: " + themeMode);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set theme mode", e);
            showErrorToast("Failed to save theme mode");
        }
    }
    
    /**
     * Set theme ID with asynchronous persistence
     */
    public synchronized void setThemeId(int themeId) {
        try {
            this.themeId = themeId;
            // Use asynchronous put method to avoid UI blocking
            PowerPreference.getDefaultFile().putInt(
                PreferencesConfig.ARGUMENT_PREFERENCE_THEME, 
                themeId
            );
            Log.d(TAG, "Theme ID updated to: " + themeId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set theme ID", e);
            showErrorToast("Failed to save theme color");
        }
    }
    
    /**
     * Set dynamic color with asynchronous persistence
     */
    public synchronized void setDynamicColor(boolean enabled) {
        try {
            this.dynamicColor = enabled;
            // Use asynchronous put method to avoid UI blocking
            PowerPreference.getDefaultFile().putBoolean(
                PreferencesConfig.ARGUMENT_PREFERENCE_DYNAMIC_COLOR, 
                enabled
            );
            Log.d(TAG, "Dynamic color updated to: " + enabled);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set dynamic color", e);
            showErrorToast("Failed to save dynamic color setting");
        }
    }
    
    /**
     * Set screen protection with asynchronous persistence
     */
    public synchronized void setScreenProtection(boolean enabled) {
        try {
            this.screenProtection = enabled;
            // Use asynchronous put method to avoid UI blocking
            PowerPreference.getDefaultFile().putBoolean(
                PreferencesConfig.ARGUMENT_PREFERENCE_SCREEN_PROTECTION, 
                enabled
            );
            Log.d(TAG, "Screen protection updated to: " + enabled);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set screen protection", e);
            showErrorToast("Failed to save screen protection setting");
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
                int fallbackThemeMode = PowerPreference.getDefaultFile().getInt(
                    PreferencesConfig.ARGUMENT_PREFERENCE_THEME_MODE, 
                    PreferencesConfig.ARGUMENT_DEFAULT_THEME_MODE_VALUE
                );
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
    
    /**
     * Show error toast on main thread
     */
    private void showErrorToast(String message) {
        if (context != null && mainHandler != null) {
            mainHandler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
        }
    }

}
