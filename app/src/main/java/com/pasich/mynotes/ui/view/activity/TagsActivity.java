package com.pasich.mynotes.ui.view.activity;

import static com.pasich.mynotes.utils.constants.settings.TagSettings.MAX_TAG_COUNT;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.databinding.ActivityTagsBinding;
import com.pasich.mynotes.ui.contract.TagsContract;
import com.pasich.mynotes.ui.presenter.TagsPresenter;
import com.pasich.mynotes.utils.adapters.TagsManagementAdapter;
import com.pasich.mynotes.ui.view.dialogs.main.DeleteTagDialog;
import com.pasich.mynotes.ui.view.dialogs.main.NameTagDialog;
import com.pasich.mynotes.ui.view.dialogs.main.popupWindowsTag.PopupWindowsTag;
import com.pasich.mynotes.ui.view.dialogs.main.popupWindowsTag.PopupWindowsTagOnClickListener;
import com.pasich.mynotes.utils.managers.SystemTagsManager;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class TagsActivity extends BaseActivity implements TagsContract.view {

    private ActivityTagsBinding binding;
    
    @Inject
    public TagsPresenter presenter;
    
    private TagsManagementAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        selectTheme();
        super.onCreate(savedInstanceState);
        binding = ActivityTagsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupEdgeToEdgeInsets(binding.getRoot());
        presenter.attachView(this);
        presenter.viewIsReady();
    }

    @Override
    public void settingsActionBar() {
        binding.actionBar.setNavigationOnClickListener(v -> finish());
    }

    @Override
    public void initListeners() {
        // Basic listeners are set up in setupRecyclerView()
    }

    @Override
    public void setupRecyclerView() {
        adapter = new TagsManagementAdapter();
        binding.tagsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.tagsRecyclerView.setAdapter(adapter);
        
        adapter.setOnTagClickListener(new TagsManagementAdapter.OnTagClickListener() {
            @Override
            public void onTagClick(Tag tag, int position) {
                if (SystemTagsManager.isAddTag(tag)) {
                    presenter.onAddTagClick();
                }
            }

            @Override
            public void onTagLongClick(Tag tag, View anchorView) {
                presenter.onTagLongClick(tag, anchorView);
            }

            @Override
            public void onOptionsClick(Tag tag, View anchorView) {
                presenter.onTagLongClick(tag, anchorView);
            }
        });
    }

    @Override
    public void loadTags(List<Tag> tags) {
        List<Tag> displayTags = new ArrayList<>();
        
        // Створюємо спеціальний тег для кнопки "Додати"
        Tag addTag = new Tag().create("", SystemTagsManager.SYSTEM_ACTION_ADD_TAG);
        displayTags.add(addTag);
        
        for (Tag tag : tags) {
            if (tag.getSystemAction() == 0) {
                displayTags.add(tag);
            }
        }
        
        adapter.submitList(displayTags);
    }

    @Override
    public void showCreateTagDialog() {
      new NameTagDialog().show(getSupportFragmentManager(), "CreateTagDialog");
    }

    @Override
    public void showEditTagDialog(Tag tag) {
       new NameTagDialog(tag).show(getSupportFragmentManager(), "RenameTag");
    }

    @Override
    public void showDeleteTagDialog(Tag tag) {
       new DeleteTagDialog(tag).show(getSupportFragmentManager(), "DeleteTagDialog");
    }

    @Override
    public void showTagOptionsDialog(Tag tag, View anchorView) {
      new PopupWindowsTag(
            getLayoutInflater(), 
            anchorView, 
            tag,
            new PopupWindowsTagOnClickListener() {
                @Override
                public void deleteTag() {
                    showDeleteTagDialog(tag);
                }

                @Override
                public void renameTag() {
                    showEditTagDialog(tag);
                }

                @Override
                public void visibleEditTag() {
                    presenter.toggleTagVisibility(tag);
                }
            }
        );
    }

    @Override
    public void refreshTagsList() {
        presenter.loadTags();
    }

    @Override
    public void showToastMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showToastMessage(int message) {
        showToastMessage(getString(message));
    }

    @SuppressLint("StringFormatMatches")
    @Override
    public void showToastCheckCountTags() {
        Toast.makeText(this, getString(R.string.countTagsError, MAX_TAG_COUNT), Toast.LENGTH_LONG).show();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (presenter != null) {
            presenter.detachView();
        }
    }

}