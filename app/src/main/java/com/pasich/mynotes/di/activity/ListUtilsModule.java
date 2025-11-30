package com.pasich.mynotes.di.activity;


import android.content.Context;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.pasich.mynotes.R;
import com.pasich.mynotes.cache.AppPreferencesCache;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.databinding.ItemNoteBinding;
import com.pasich.mynotes.utils.adapters.notes.NoteAdapter;
import com.pasich.mynotes.utils.recycler.SpacesItemDecoration;
import com.pasich.mynotes.utils.recycler.diffutil.DiffUtilNote;
import com.pasich.mynotes.utils.recycler.diffutil.DiffUtilTag;

import javax.inject.Named;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.android.scopes.ActivityScoped;

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
    NoteAdapter<ItemNoteBinding> providerGenericAdapter(@Named("Note") DiffUtil.ItemCallback<Note> diff) {
        return new NoteAdapter<>((DiffUtilNote) diff, R.layout.item_note, ItemNoteBinding::setNote);
    }

    @Named("Note")
    @Provides
    @ActivityScoped
    DiffUtil.ItemCallback<Note> providesDiffUtilCallbackNote(DiffUtilNote diffUtil) {
        return diffUtil;
    }

    @Named("Tag")
    @Provides
    @ActivityScoped
    DiffUtil.ItemCallback<Tag> providesDiffUtilCallbackTag(DiffUtilTag diffUtil) {
        return diffUtil;
    }

    @Provides
    @ActivityScoped
    StaggeredGridLayoutManager providesStaggeredGridLayoutManager(int spanCount) {
        return new StaggeredGridLayoutManager(spanCount, LinearLayoutManager.VERTICAL);
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
