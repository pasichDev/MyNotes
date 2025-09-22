package com.pasich.mynotes.ui.view.activity;

import static com.pasich.mynotes.utils.constants.settings.TagSettings.MAX_TAG_COUNT;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.base.view.TagsSortView;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.databinding.ActivityTagsBinding;
import com.pasich.mynotes.ui.contract.TagsContract;
import com.pasich.mynotes.ui.presenter.TagsPresenter;
import com.pasich.mynotes.ui.view.dialogs.main.DeleteTagDialog;
import com.pasich.mynotes.ui.view.dialogs.main.NameTagDialog;
import com.pasich.mynotes.ui.view.dialogs.main.SortDialog;
import com.pasich.mynotes.ui.view.dialogs.main.TagOptionsBottomSheet;
import com.pasich.mynotes.utils.adapters.TagsManagementAdapter;
import com.pasich.mynotes.utils.managers.SystemTagsManager;
import com.pasich.mynotes.utils.recycler.TagDragCallback;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class TagsActivity extends BaseActivity implements TagsContract.view, TagsSortView {

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
        binding.actionBar.setOnMenuItemClickListener(this::onMenuItemClick);
    }

    private boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.action_sort_tags) {
            presenter.onSortMenuClick();
            return true;
        }
        return false;
    }

    @Override
    public void initListeners() {
    }

    @Override
    public void setupRecyclerView() {
        adapter = new TagsManagementAdapter();
        binding.tagsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.tagsRecyclerView.setAdapter(adapter);

        // Setup drag&drop
        adapter.setOnTagMoveListener(currentTagOrder -> presenter.onDragCompleted(currentTagOrder));

        // Create and attach ItemTouchHelper
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new TagDragCallback(adapter));
        itemTouchHelper.attachToRecyclerView(binding.tagsRecyclerView);

        adapter.setOnTagClickListener(new TagsManagementAdapter.OnTagClickListener() {
            @Override
            public void onTagClick(Tag tag, int position) {
                if (SystemTagsManager.isAddTag(tag)) {
                    presenter.onAddTagClick();
                }
            }

            @Override
            public void onTagLongClick(Tag tag, View anchorView) {
            }

            @Override
            public void onOptionsClick(Tag tag, View anchorView) {
                presenter.onTagLongClick(tag, anchorView);
            }
        });
    }

    @Override
    public void loadTags(List<Tag> tags) {
        adapter.submitList(tags);
    }

    @Override
    public void showCreateTagDialog(int newPosition) {
        new NameTagDialog(newPosition).show(getSupportFragmentManager(), "CreateTagDialog");
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
        // Получаем количество заметок для этого тега
        presenter.getTagNotesCount(tag, count -> {
            TagOptionsBottomSheet bottomSheet = new TagOptionsBottomSheet(tag, count, new TagOptionsBottomSheet.TagOptionsListener() {
                @Override
                public void onDeleteTagClick(Tag tag) {
                    showDeleteTagDialog(tag);
                }

                @Override
                public void onRenameTagClick(Tag tag) {
                    showEditTagDialog(tag);
                }

                @Override
                public void onToggleVisibilityClick(Tag tag) {
                    presenter.toggleTagVisibility(tag);
                }
            });
            bottomSheet.show(getSupportFragmentManager(), "TagOptionsBottomSheet");
        });
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

    @Override
    public void sortTags(String sortParam) {
        presenter.sortTags(sortParam);
    }

    @Override
    public void showSortDialog() {
        SortDialog dialog = SortDialog.newInstance(true);
        dialog.setListener(new SortDialog.SortListener() {
            @Override
            public void onSortSelected(String sortParam) {
            }

            @Override
            public void onTagsSortSelected(String tagsSortParam) {
                sortTags(tagsSortParam);
            }
        });
        dialog.show(getSupportFragmentManager(), "SortDialog");


    }

}