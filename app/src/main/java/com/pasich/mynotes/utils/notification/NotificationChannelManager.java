package com.pasich.mynotes.utils.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import com.pasich.mynotes.R;
import com.pasich.mynotes.cache.NotificationPreferencesCache;

public class NotificationChannelManager {

    private static final String LEGACY_CHANNEL_ID = "reminders";

    /**
     * Idempotent: creates the current versioned channel if it does not exist. On first call after
     * update from old version, deletes the legacy "reminders" channel.
     */
    public static void ensureChannel(Context ctx, NotificationPreferencesCache prefs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        String channelId = prefs.getChannelId();
        if (nm.getNotificationChannel(channelId) != null) return; // already exists

        // Remove legacy channel on first run after update
        if (nm.getNotificationChannel(LEGACY_CHANNEL_ID) != null) {
            nm.deleteNotificationChannel(LEGACY_CHANNEL_ID);
        }

        createChannel(ctx, nm, prefs);
    }

    /**
     * Deletes the current versioned channel, bumps the version, creates the new channel with
     * newSoundUri. Pass null to reset to the system default notification sound.
     */
    public static void changeSound(
            Context ctx, NotificationPreferencesCache prefs, Uri newSoundUri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            prefs.setSoundUri(newSoundUri != null ? newSoundUri.toString() : null);
            return;
        }
        NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        nm.deleteNotificationChannel(prefs.getChannelId());
        prefs.setSoundUri(newSoundUri != null ? newSoundUri.toString() : null);
        prefs.incrementAndPersistVersion();
        createChannel(ctx, nm, prefs);
    }

    private static void createChannel(
            Context ctx, NotificationManager nm, NotificationPreferencesCache prefs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationChannel channel =
                new NotificationChannel(
                        prefs.getChannelId(),
                        ctx.getString(R.string.reminder_notification_channel),
                        NotificationManager.IMPORTANCE_HIGH);

        String savedUri = prefs.getSoundUri();
        Uri soundUri;
        if (savedUri == null || savedUri.isEmpty()) {
            soundUri = Settings.System.DEFAULT_NOTIFICATION_URI;
        } else {
            soundUri = Uri.parse(savedUri);
        }

        AudioAttributes attrs =
                new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build();
        channel.setSound(soundUri, attrs);
        nm.createNotificationChannel(channel);
    }
}
