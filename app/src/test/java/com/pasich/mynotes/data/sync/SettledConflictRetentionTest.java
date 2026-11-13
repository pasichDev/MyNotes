package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.room.Room;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.database.dao.SyncConflictDao;
import com.pasich.mynotes.data.database.entities.SyncConflictEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Settled conflicts are bounded by age, not by whatever order SQLite happens to return them in.
 *
 * <p>The bundle carries a capped number of settled versions. The query feeding that cap was a plain
 * UNION, so which versions survived it was arbitrary, and settled rows were never deleted.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SettledConflictRetentionTest {

    private AppDatabase db;
    private SyncConflictDao dao;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        db =
                Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                        .allowMainThreadQueries()
                        .build();
        dao = db.syncConflictDao();
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void resolvedVersionIdsComeNewestFirst() {
        // Inserted in an order unrelated to their settlement time or their ids.
        insertResolved("b", 200L);
        insertResolved("c", 300L);
        insertResolved("a", 100L);
        insertOpen("z");

        List<String> ids = dao.getResolvedVersionIds();

        assertThat(ids).hasSize(6);
        assertThat(ids.subList(0, 2)).containsExactly(id("c", 'w'), id("c", 'l'));
        assertThat(ids.subList(2, 4)).containsExactly(id("b", 'w'), id("b", 'l'));
        assertThat(ids.subList(4, 6)).containsExactly(id("a", 'w'), id("a", 'l'));
    }

    @Test
    public void deleteResolvedBeyondKeepsOnlyTheNewestSettlementsAndEveryOpenConflict() {
        insertResolved("a", 100L);
        insertResolved("b", 200L);
        insertResolved("c", 300L);
        insertOpen("z");

        dao.deleteResolvedBeyond(2);

        assertThat(dao.getResolvedVersionIds())
                .containsExactly(id("c", 'w'), id("c", 'l'), id("b", 'w'), id("b", 'l'));
        assertThat(dao.getUnresolvedCount()).isEqualTo(1);
    }

    @Test
    public void capSettledVersionIdsDropsTheOldestLocalSettlements() {
        List<String> newestFirst = new ArrayList<>();
        for (int index = 0; index < SyncService.MAX_PUBLISHED_SETTLED_IDS + 50; index++) {
            newestFirst.add(String.format("%064x", index));
        }

        Set<String> kept = SyncService.capSettledVersionIds(newestFirst, Collections.emptyList());

        assertThat(kept)
                .containsExactlyElementsIn(
                        newestFirst.subList(0, SyncService.MAX_PUBLISHED_SETTLED_IDS));
    }

    @Test
    public void capSettledVersionIdsKeepsSomeOfEachSide() {
        List<String> local = new ArrayList<>();
        List<String> remote = new ArrayList<>();
        for (int index = 0; index < SyncService.MAX_PUBLISHED_SETTLED_IDS; index++) {
            local.add(String.format("a%063x", index));
            remote.add(String.format("b%063x", index));
        }

        Set<String> kept = SyncService.capSettledVersionIds(local, remote);

        assertThat(kept).hasSize(SyncService.MAX_PUBLISHED_SETTLED_IDS);
        assertThat(kept).contains(local.get(0));
        assertThat(kept).contains(remote.get(0));
        assertThat(kept).doesNotContain(local.get(local.size() - 1));
    }

    private void insertResolved(String name, long resolvedAt) {
        dao.insertIgnoringDuplicates(Collections.singletonList(row(name, true, resolvedAt)));
    }

    private void insertOpen(String name) {
        dao.insertIgnoringDuplicates(Collections.singletonList(row(name, false, 0L)));
    }

    private static String id(String name, char side) {
        return name + side;
    }

    private static SyncConflictEntity row(String name, boolean resolved, long resolvedAt) {
        return new SyncConflictEntity(
                "note",
                "stable-" + name,
                "pair-" + name,
                "LOCAL",
                "REMOTE",
                id(name, 'w'),
                id(name, 'l'),
                "{}",
                "{}",
                1L,
                1L,
                false,
                false,
                resolved ? "KEEP_WINNER" : "PENDING",
                resolved,
                1L,
                resolvedAt);
    }
}
