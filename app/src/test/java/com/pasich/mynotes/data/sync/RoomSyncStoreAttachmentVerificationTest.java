package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import android.content.Context;
import androidx.room.Room;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.preferences.PreferenceHelper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Verifying a local blob goes through the digest cache rather than re-hashing it every sync. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RoomSyncStoreAttachmentVerificationTest {

    private AppDatabase db;
    private RoomSyncStore store;
    private final int[] hashes = {0};

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        db =
                Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                        .allowMainThreadQueries()
                        .build();
        store =
                new RoomSyncStore(
                        context,
                        db,
                        mock(PreferenceHelper.class),
                        (ignored, attachment) -> null,
                        file -> {
                            hashes[0]++;
                            return Sha256.of(file);
                        });
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void anUnchangedBlobIsHashedOnceAcrossRepeatedVerifications() throws Exception {
        byte[] bytes = "photo".getBytes(StandardCharsets.UTF_8);
        String hash = Sha256.of(bytes);
        store.writeAttachment(hash, bytes.length, new ByteArrayInputStream(bytes));

        assertThat(store.hasVerifiedAttachment(hash, (long) bytes.length)).isTrue();
        assertThat(store.hasVerifiedAttachment(hash, (long) bytes.length)).isTrue();
        assertThat(store.hasVerifiedAttachment(hash, null)).isTrue();

        assertThat(hashes[0]).isEqualTo(1);
    }

    @Test
    public void aBlobOfTheWrongSizeOrMissingIsNotVerified() throws Exception {
        byte[] bytes = "photo".getBytes(StandardCharsets.UTF_8);
        String hash = Sha256.of(bytes);
        store.writeAttachment(hash, bytes.length, new ByteArrayInputStream(bytes));

        assertThat(store.hasVerifiedAttachment(hash, (long) bytes.length + 1)).isFalse();
        assertThat(store.hasVerifiedAttachment(Sha256.of("absent".getBytes()), null)).isFalse();
    }
}
