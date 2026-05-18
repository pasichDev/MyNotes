package com.pasich.mynotes.ui.contract;

import android.view.View;
import androidx.annotation.StringRes;
import com.pasich.mynotes.base.view.ActionBar;
import com.pasich.mynotes.base.view.BasePresenter;
import com.pasich.mynotes.base.view.BaseView;
import com.pasich.mynotes.data.model.Tag;
import dagger.hilt.android.scopes.ActivityScoped;
import java.util.List;

/** Contract for the tag management screen. */
public interface TagsContract {

    interface view extends BaseView, ActionBar {

        /** Initialises the tags RecyclerView and its adapter. */
        void setupRecyclerView();

        /** Submits the tag list to the adapter. */
        void loadTags(List<Tag> tags);

        /** Shows the dialog to create a tag at the given position. */
        void showCreateTagDialog(int newPosition);

        /** Shows the dialog to rename the given tag. */
        void showEditTagDialog(Tag tag);

        /** Shows the confirmation dialog to delete the given tag. */
        void showDeleteTagDialog(Tag tag);

        /** Shows the contextual options popup for the given tag. */
        void showTagOptionsDialog(Tag tag, View anchorView);

        /** Shows a short toast with the given text. */
        void showToastMessage(String message);

        /** Shows a short toast using a string resource id. */
        void showToastMessage(@StringRes int message);

        /** Notifies the user that the maximum tag count has been reached. */
        void showToastCheckCountTags();

        /** Opens the sort-order selection dialog. */
        void showSortDialog();
    }

    @ActivityScoped
    interface presenter extends BasePresenter<view> {

        /** Loads and displays the current tag list. */
        void loadTags();

        /** Toggles the visibility flag of the given tag. */
        void toggleTagVisibility(Tag tag);

        /** Handles the add-tag button click. */
        void onAddTagClick();

        /** Handles a long-press on a tag item. */
        void onTagLongClick(Tag tag, View anchorView);

        /** Applies a new sort parameter and refreshes the list. */
        void sortTags(String sortParam);

        /** Persists new positions after a drag-and-drop reorder. */
        void onDragCompleted(List<Tag> currentTagOrder);

        /** Handles the sort-menu button click. */
        void onSortMenuClick();

        /** Returns the number of notes associated with the given tag. */
        void getTagNotesCount(Tag tag, TagNotesCountCallback callback);
    }

    /** Callback for receiving the note count of a tag. */
    interface TagNotesCountCallback {
        /** Called with the number of notes that belong to the tag. */
        void onTagNotesCountReceived(int count);
    }
}
