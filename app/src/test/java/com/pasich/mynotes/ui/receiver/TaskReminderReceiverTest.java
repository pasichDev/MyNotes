package com.pasich.mynotes.ui.receiver;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import androidx.room.Room;
import com.pasich.mynotes.cache.NotificationPreferencesCache;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.model.Task;
import io.reactivex.Completable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;

/** A task reminder is checked against the stored task before it is announced. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TaskReminderReceiverTest {

    private static final long NOW = 1_780_000_000_000L;

    private Application context;
    private AppDatabase db;
    private DataManager dataManager;
    private TaskReminderReceiver receiver;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        ShadowAlarmManager.setCanScheduleExactAlarms(true);
        db =
                Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                        .allowMainThreadQueries()
                        .build();
        dataManager = mock(DataManager.class);
        when(dataManager.clearTaskReminder(anyInt())).thenReturn(Completable.complete());
        when(dataManager.setTaskReminderFull(anyInt(), anyLong(), anyInt()))
                .thenReturn(Completable.complete());
        NotificationPreferencesCache channels = mock(NotificationPreferencesCache.class);
        when(channels.getChannelId()).thenReturn("reminders");

        receiver = new TaskReminderReceiver();
        receiver.dataManager = dataManager;
        receiver.database = db;
        receiver.notificationPreferencesCache = channels;
    }

    @After
    public void tearDown() {
        db.close();
        ShadowAlarmManager.reset();
    }

    private int task(long time, int intervalMinutes, boolean done) {
        Task task = new Task("Buy milk", 0);
        task.setReminderTime(time);
        task.setReminderIntervalMinutes(intervalMinutes);
        task.setDone(done);
        return (int) db.taskDao().insertTask(task);
    }

    private int posted() {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return shadowOf(nm).getAllNotifications().size();
    }

    private int armed() {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return shadowOf(am).getScheduledAlarms().size();
    }

    @Test
    public void deletedTask_isNotAnnouncedNorRearmed() {
        receiver.handle(context, 404, NOW);

        assertThat(posted()).isEqualTo(0);
        assertThat(armed()).isEqualTo(0);
        verify(dataManager, never()).setTaskReminderFull(anyInt(), anyLong(), anyInt());
    }

    @Test
    public void completedTask_isNotAnnounced() {
        int id = task(NOW, 15, true);

        receiver.handle(context, id, NOW);

        assertThat(posted()).isEqualTo(0);
        assertThat(armed()).isEqualTo(0);
    }

    @Test
    public void oneTimeReminder_isAnnouncedAndCleared() {
        int id = task(NOW, 0, false);

        receiver.handle(context, id, NOW);

        assertThat(posted()).isEqualTo(1);
        verify(dataManager).clearTaskReminder(id);
        assertThat(armed()).isEqualTo(0);
    }

    @Test
    public void repeatingReminder_isAnnouncedAndArmedAgain() {
        int id = task(NOW, 15, false);

        receiver.handle(context, id, NOW);

        assertThat(posted()).isEqualTo(1);
        verify(dataManager).setTaskReminderFull(eq(id), eq(NOW + 15 * 60_000L), eq(15));
        assertThat(armed()).isEqualTo(1);
    }

    @Test
    public void reminderMovedLater_waitsForItsNewTime() {
        int id = task(NOW + 3_600_000L, 0, false);

        receiver.handle(context, id, NOW);

        assertThat(posted()).isEqualTo(0);
        assertThat(armed()).isEqualTo(1);
    }
}
