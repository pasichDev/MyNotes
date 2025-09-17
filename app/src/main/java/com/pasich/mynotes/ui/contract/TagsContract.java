package com.pasich.mynotes.ui.contract;

import android.view.View;

import androidx.annotation.StringRes;

import com.pasich.mynotes.base.view.ActionBar;
import com.pasich.mynotes.base.view.BasePresenter;
import com.pasich.mynotes.base.view.BaseView;
import com.pasich.mynotes.data.model.Tag;

import java.util.List;

import dagger.hilt.android.scopes.ActivityScoped;

public interface TagsContract {

    interface view extends BaseView, ActionBar {
        
        void setupRecyclerView();
        
        void loadTags(List<Tag> tags);
        
        void showCreateTagDialog();
        
        void showEditTagDialog(Tag tag);
        
        void showDeleteTagDialog(Tag tag);
        
        void showTagOptionsDialog(Tag tag, View anchorView);
        
        void showToastMessage(String message);

        void showToastMessage(@StringRes int message);
        void showToastCheckCountTags();

        void showSortDialog();
    }

    @ActivityScoped
    interface presenter extends BasePresenter<view> {
        
        void loadTags();
        
        void createTag(String tagName);

        void deleteTag(Tag tag);
        
        void toggleTagVisibility(Tag tag);
        
        void onAddTagClick();
        
        void onTagLongClick(Tag tag, View anchorView);

        void sortTags(String sortParam);

        void onDragCompleted(List<Tag> currentTagOrder);

        void onSortMenuClick();


    }
}