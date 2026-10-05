package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import androidx.room.Room;
import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.database.entities.SyncMetadataEntity;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.RepeatRule;
import com.pasich.mynotes.data.preferences.PreferenceHelper;
import com.pasich.mynotes.utils.reminder.ReminderManager;
import com.pasich.mynotes.utils.reminder.SnoozeStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;

/**
 * A reminder set, moved or turned off on another device arrives here only as a note row; the alarms
 * on this device must follow it as soon as the sync applies, not at the next app start.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RoomSyncStoreReminderTest {

    private static final String NOTE_ID = "11111111-1111-4111-8111-111111111111";
    private static final String REMOTE_ID = "66666666-6666-4666-8666-666666666666";
    private static final long HOUR = 3_600_000L;

    private Context context;
    private AppDatabase db;
    private RoomSyncStore store;
    private ShadowAlarmManager alarms;
    private int noteId;
    private long future;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        db =
                Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                        .allowMainThreadQueries()
                        .build();
        store = new RoomSyncStore(context, db, mock(PreferenceHelper.class));
        alarms = shadowOf((AlarmManager) context.getSystemService(Context.ALARM_SERVICE));
        ShadowAlarmManager.setCanScheduleExactAlarms(true);
        future = System.currentTimeMillis() + 5 * HOUR;

        Note note = new Note().create("Pills", "Twice a day", 1_000L, "");
        noteId = db.noteDao().addNote(note).intValue();
        db.syncMetadataDao()
                .insertIfAbsent(
                        new SyncMetadataEntity(
                                SyncMetadata.RECORD_TYPE_NOTE, noteId, NOTE_ID, 1_000L, null));
        store.applySnapshot(store.readSnapshot(), Collections.emptyList());
    }

    @After
    public void tearDown() {
        db.close();
        ShadowAlarmManager.reset();
        context.getSharedPreferences("reminder_snoozes", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void aReminderSetElsewhereIsArmedHere() throws Exception {
        String rule = RepeatRule.every(3, RepeatRule.Unit.HOURS, future).serialize();
        applyNote(withReminder(future, rule));

        Note stored = db.noteDao().getNoteSync(noteId);
        assertThat(stored.getReminderRepeat()).isEqualTo(rule);
        List<ShadowAlarmManager.ScheduledAlarm> armed = alarmsFor(noteId, null);
        assertThat(armed).hasSize(1);
        assertThat(armed.get(0).getTriggerAtMs()).isEqualTo(future);
    }

    @Test
    public void aReminderTurnedOffElsewhereIsCancelledHere() throws Exception {
        applyNote(withReminder(future, "DAILY"));
        assertThat(alarmsFor(noteId, null)).hasSize(1);

        applyNote(withReminder(null, "NONE"));

        assertThat(alarmsFor(noteId, null)).isEmpty();
    }

    @Test
    public void aNoteDeletedElsewhereLosesEveryAlarm() throws Exception {
        applyNote(withReminder(future, "DAILY"));
        ReminderManager.scheduleSnooze(context, noteId, future - HOUR, 0);
        SyncRecord built = store.readSnapshot().find(SyncRecord.Type.NOTE, NOTE_ID);
        Instant deletedAt = built.getUpdatedAt().plusSeconds(1);

        store.applySnapshot(
                new SyncSnapshot(
                        Collections.singletonList(
                                SyncRecord.tombstone(
                                        SyncRecord.Type.NOTE, NOTE_ID, deletedAt, deletedAt))),
                Collections.emptyList());

        assertThat(alarmsFor(noteId, null)).isEmpty();
        assertThat(alarmsFor(noteId, ReminderManager.ACTION_SNOOZE)).isEmpty();
        assertThat(new SnoozeStore(context).get(noteId)).isNull();
    }

    @Test
    public void aNewNoteWithAReminderFromElsewhereIsArmed() throws Exception {
        Note remote = new Note().create("Rent", "Pay", 2_000L, "");
        remote.setReminderTime(future);
        remote.setReminderRepeat("MONTHLY");
        JsonObject payload = new Gson().toJsonTree(remote).getAsJsonObject();
        List<SyncRecord> records = new ArrayList<>(store.readSnapshot().getRecords());
        records.add(
                SyncRecord.live(
                        SyncRecord.Type.NOTE, REMOTE_ID, Instant.ofEpochMilli(3_000L), payload));

        store.applySnapshot(new SyncSnapshot(records), Collections.emptyList());

        long localId =
                db.syncMetadataDao()
                        .getByStableId(SyncMetadata.RECORD_TYPE_NOTE, REMOTE_ID)
                        .localId;
        assertThat(alarmsFor((int) localId, null)).hasSize(1);
        assertThat(alarmsFor((int) localId, null).get(0).getTriggerAtMs()).isEqualTo(future);
    }

    // ---- helpers ----

    private SyncRecord withReminder(Long time, String repeat) throws Exception {
        SyncRecord note = store.readSnapshot().find(SyncRecord.Type.NOTE, NOTE_ID);
        JsonObject payload = note.getPayload().deepCopy();
        if (time == null) payload.add("j", JsonNull.INSTANCE);
        else payload.addProperty("j", time);
        payload.addProperty("k", repeat);
        return SyncRecord.live(
                SyncRecord.Type.NOTE, NOTE_ID, note.getUpdatedAt().plusSeconds(1), payload);
    }

    private void applyNote(SyncRecord record) throws Exception {
        store.applySnapshot(
                new SyncSnapshot(Collections.singletonList(record)), Collections.emptyList());
    }

    private List<ShadowAlarmManager.ScheduledAlarm> alarmsFor(int id, String action) {
        List<ShadowAlarmManager.ScheduledAlarm> found = new ArrayList<>();
        for (ShadowAlarmManager.ScheduledAlarm alarm : alarms.getScheduledAlarms()) {
            Intent intent = shadowOf(alarm.operation).getSavedIntent();
            if (intent.getIntExtra(ReminderManager.EXTRA_NOTE_ID, -1) != id) continue;
            String a = intent.getAction();
            if (action == null ? a == null : action.equals(a)) found.add(alarm);
        }
        return found;
    }
}
