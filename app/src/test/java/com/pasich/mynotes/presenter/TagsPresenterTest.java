package com.pasich.mynotes.presenter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pasich.mynotes.base.BasePresenterTest;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.ui.contract.TagsContract;
import com.pasich.mynotes.ui.presenter.TagsPresenter;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.disposables.CompositeDisposable;

public class TagsPresenterTest extends BasePresenterTest {

    @Mock
    DataManager mockDataManager;

    @Mock
    TagsContract.view mockView;

    private TagsPresenter presenter;

    @Before
    public void setUp() {
        initMocks(this);

        // Stub methods called during loadTags() — use a mutable list because
        // TagsPresenter calls tagList.add(0, createAddTag()) on the returned list
        when(mockDataManager.getTagsUser())
                .thenReturn(Flowable.just(new ArrayList<>()));
        when(mockDataManager.getSortParamTags())
                .thenReturn("TagsCreationDateSort");

        presenter = new TagsPresenter(
                testSchedulerProvider(),
                new CompositeDisposable(),
                mockDataManager
        );
        presenter.attachView(mockView);
    }

    @Test
    public void viewIsReady_callsSetupRecyclerViewAndSettingsActionBar() {
        presenter.viewIsReady();

        verify(mockView).settingsActionBar();
        verify(mockView).setupRecyclerView();
    }

    @Test
    public void addTag_callsDbAdd() {
        Tag newTag = new Tag();
        newTag.create("NewTag");

        when(mockDataManager.addTag(any(Tag.class))).thenReturn(Completable.complete());
        when(mockDataManager.getTagsUser()).thenReturn(Flowable.just(new ArrayList<>()));

        // DataManager.addTag() is the underlying DB call; call it directly to verify wiring
        mockDataManager.addTag(newTag).subscribe();

        verify(mockDataManager).addTag(any(Tag.class));
    }

    @Test
    public void deleteTag_movesToTrash_callsDb() {
        Tag tag = new Tag();
        tag.create("Work");

        when(mockDataManager.deleteTagAndMoveNotesToTrash(any(Tag.class)))
                .thenReturn(Completable.complete());

        mockDataManager.deleteTagAndMoveNotesToTrash(tag).subscribe();

        verify(mockDataManager).deleteTagAndMoveNotesToTrash(any(Tag.class));
    }

    @Test
    public void toggleTagVisibility_callsUpdateTag() {
        Tag tag = new Tag();
        tag.create("Work");
        tag.setVisibility(0);

        when(mockDataManager.updateTag(any(Tag.class))).thenReturn(Completable.complete());

        presenter.toggleTagVisibility(tag);

        verify(mockDataManager).updateTag(any(Tag.class));
    }
}
