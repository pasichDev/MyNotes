package com.pasich.mynotes.di.activity;

import android.content.Context;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import com.pasich.mynotes.cache.AppPreferencesCache;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.utils.recycler.SpacesItemDecoration;
import com.pasich.mynotes.utils.recycler.diffutil.DiffUtilTag;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.android.scopes.ActivityScoped;
import javax.inject.Named;

@Module
@InstallIn(ActivityComponent.class)
public class ListUtilsModule {
    @Named("NotesItemSpaceDecoration")
    @Provides
    @ActivityScoped
    SpacesItemDecoration providerSpaceItemDecorationNotes() {
        return new SpacesItemDecoration(15, 10);
    }

    @Provides
    @ActivityScoped
    StaggeredGridLayoutManager providesStaggeredGridLayoutManager(int spanCount) {
        return new StaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL);
    }

    @Named("Tag")
    @Provides
    @ActivityScoped
    DiffUtil.ItemCallback<Tag> providesDiffUtilCallbackTag(DiffUtilTag diffUtil) {
        return diffUtil;
    }

    @Provides
    @ActivityScoped
    LinearLayoutManager providesLinearLayoutManager(@ApplicationContext Context context) {
        return new LinearLayoutManager(context, RecyclerView.HORIZONTAL, false);
    }

    @Provides
    @ActivityScoped
    int providesSpanCountStaggerGridLayout(AppPreferencesCache appPreferencesCache) {
        return appPreferencesCache.getFormatPref();
    }

    @Named("TagsItemSpaceDecoration")
    @Provides
    @ActivityScoped
    SpacesItemDecoration providerSpaceItemDecorationTags() {
        return new SpacesItemDecoration(8, 8);
    }
}
