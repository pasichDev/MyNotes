package com.pasich.mynotes.cache;

import android.util.Log;
import com.pasich.mynotes.data.preferences.SafePreferences;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Cache for notification channel and sound preferences. */
@Singleton
public class NotificationPreferencesCache {

    private static final String TAG = "NotificationPrefsCache";

    private final SafePreferences prefs;
    private volatile String soundUri;
    private volatile String channelId;
    private volatile int channelVersion;
    private volatile boolean initialized = false;

    @Inject
    public NotificationPreferencesCache(SafePreferences prefs) {
        this.prefs = prefs;
    }

    public synchronized void initialize() {
        if (initialized) {
            Log.d(TAG, "Cache already initialized");
            return;
        }
        try {
            soundUri =
                    prefs.getString(
                            PreferencesConfig.ARGUMENT_PREFERENCE_NOTIFICATION_SOUND_URI, null);
            channelId =
                    prefs.getString(
                            PreferencesConfig.ARGUMENT_PREFERENCE_NOTIFICATION_CHANNEL_ID,
                            PreferencesConfig.ARGUMENT_DEFAULT_NOTIFICATION_CHANNEL_ID);
            channelVersion =
                    prefs.getInt(
                            PreferencesConfig.ARGUMENT_PREFERENCE_NOTIFICATION_CHANNEL_VERSION,
                            PreferencesConfig.ARGUMENT_DEFAULT_NOTIFICATION_CHANNEL_VERSION);
            initialized = true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize cache", e);
            setDefaults();
        }
    }

    private void setDefaults() {
        soundUri = null;
        channelId = PreferencesConfig.ARGUMENT_DEFAULT_NOTIFICATION_CHANNEL_ID;
        channelVersion = PreferencesConfig.ARGUMENT_DEFAULT_NOTIFICATION_CHANNEL_VERSION;
        initialized = true;
    }

    private void ensureInitialized() {
        if (!initialized) initialize();
    }

    public String getSoundUri() {
        ensureInitialized();
        return soundUri;
    }

    public synchronized void setSoundUri(String uri) {
        soundUri = uri;
        if (uri == null) {
            prefs.putString(PreferencesConfig.ARGUMENT_PREFERENCE_NOTIFICATION_SOUND_URI, "");
        } else {
            prefs.putString(PreferencesConfig.ARGUMENT_PREFERENCE_NOTIFICATION_SOUND_URI, uri);
        }
    }

    public String getChannelId() {
        ensureInitialized();
        return channelId;
    }

    public synchronized void setChannelId(String id) {
        channelId = id;
        prefs.putString(PreferencesConfig.ARGUMENT_PREFERENCE_NOTIFICATION_CHANNEL_ID, id);
    }

    public int getChannelVersion() {
        ensureInitialized();
        return channelVersion;
    }

    /** Increments the channel version, persists it, and updates the channel ID. */
    public synchronized int incrementAndPersistVersion() {
        ensureInitialized();
        channelVersion++;
        prefs.putInt(
                PreferencesConfig.ARGUMENT_PREFERENCE_NOTIFICATION_CHANNEL_VERSION, channelVersion);
        setChannelId("reminders_v" + channelVersion);
        return channelVersion;
    }
}
