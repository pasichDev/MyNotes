package com.pasich.mynotes.db;

import static com.google.common.truth.Truth.assertThat;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.database.dao.TagsDao;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.utils.managers.SystemTagsManager;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TagsRepositoryTest {

    private AppDatabase db;
    private TagsDao tagsDao;

    @Before
    public void setUp() {
        db =
                Room.inMemoryDatabaseBuilder(
                                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                                AppDatabase.class)
                        .allowMainThreadQueries()
                        .build();
        tagsDao = db.tagsDao();
    }

    @After
    public void tearDown() {
        db.close();
    }

    private Tag makeUserTag(String name) {
        return new Tag().create(name);
    }

    @Test
    public void addTag_appearsInList() {
        tagsDao.addTag(makeUserTag("Work"));
        List<Tag> tags = tagsDao.getTags().blockingFirst();
        boolean found = tags.stream().anyMatch(t -> "Work".equals(t.getNameTag()));
        assertThat(found).isTrue();
    }

    @Test
    public void deleteTag_removesFromList() {
        tagsDao.addTag(makeUserTag("Temp"));
        List<Tag> before = tagsDao.getTags().blockingFirst();
        Tag inserted =
                before.stream()
                        .filter(t -> "Temp".equals(t.getNameTag()))
                        .findFirst()
                        .orElseThrow(RuntimeException::new);
        tagsDao.deleteTag(inserted);
        List<Tag> after = tagsDao.getTags().blockingFirst();
        boolean stillExists = after.stream().anyMatch(t -> "Temp".equals(t.getNameTag()));
        assertThat(stillExists).isFalse();
    }

    @Test
    public void updateTag_changesName() {
        tagsDao.addTag(makeUserTag("OldName"));
        List<Tag> list = tagsDao.getTags().blockingFirst();
        Tag tag =
                list.stream()
                        .filter(t -> "OldName".equals(t.getNameTag()))
                        .findFirst()
                        .orElseThrow(RuntimeException::new);
        tag.setNameTag("NewName");
        tagsDao.updateTag(tag);
        List<Tag> updated = tagsDao.getTags().blockingFirst();
        boolean newNameExists = updated.stream().anyMatch(t -> "NewName".equals(t.getNameTag()));
        assertThat(newNameExists).isTrue();
    }

    @Test
    public void getTagsUser_returnsOnlyUserTags() {
        tagsDao.addTag(makeUserTag("Personal"));
        tagsDao.addTag(makeUserTag("Work"));
        List<Tag> userTags = tagsDao.getTagsUser().blockingFirst();
        for (Tag t : userTags) {
            assertThat(t.getSystemAction()).isEqualTo(SystemTagsManager.SYSTEM_ACTION_USER_TAG);
        }
    }
}
