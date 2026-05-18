package com.pasich.mynotes.ui.controllers.mainActivity;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.search.SearchView;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.simplifications.TextWatcher;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.databinding.ActivityMainBinding;
import com.pasich.mynotes.utils.adapters.searchAdapter.SearchNotesAdapter;
import com.pasich.mynotes.utils.recycler.SpacesItemDecoration;
import java.util.List;

public class SearchController {

    private static final String TAG = "SearchController";
    private static final int DEBOUNCE_DELAY = 350;
    private final ActivityMainBinding binding;
    private final SearchNotesAdapter searchAdapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private Runnable searchRunnable;

    public SearchController(
            ActivityMainBinding binding, SearchNotesAdapter searchAdapter, Listener listener) {
        this.binding = binding;
        this.searchAdapter = searchAdapter;
        this.listener = listener;

        setup();
    }

    private void setup() {
        setupSearchList();
        setupSearchBehavior();
        setupSearchInput();
    }

    private void setupSearchList() {
        binding.resultsSearchList.setLayoutManager(
                new LinearLayoutManager(binding.getRoot().getContext()));

        binding.resultsSearchList.addItemDecoration(new SpacesItemDecoration(15, 10));
        binding.resultsSearchList.setAdapter(searchAdapter);
        binding.resultsSearchList.setOverScrollMode(ViewGroup.OVER_SCROLL_NEVER);
    }

    private void setupSearchBehavior() {
        binding.searchView.addTransitionListener(
                (searchView, oldState, newState) -> {
                    if (newState == SearchView.TransitionState.SHOWING) {
                        listener.onSearchOpen();
                        ensureFullScreen();
                    }

                    if (newState == SearchView.TransitionState.HIDDEN) {
                        listener.onSearchClose();
                        clearSearch();
                    }
                });

        binding.actionSearch.setOnClickListener(v -> binding.searchView.show());
    }

    private void setupSearchInput() {
        binding.searchView
                .getEditText()
                .addTextChangedListener(
                        new TextWatcher() {
                            @Override
                            protected void changeText(Editable s) {

                                if (searchRunnable != null) handler.removeCallbacks(searchRunnable);

                                String query = s.toString();

                                searchRunnable = () -> listener.onSearchQuery(query);

                                handler.postDelayed(searchRunnable, DEBOUNCE_DELAY);
                            }
                        });
    }

    private void ensureFullScreen() {
        try {
            ViewGroup.LayoutParams params = binding.searchView.getLayoutParams();

            if (params instanceof CoordinatorLayout.LayoutParams coordinatorParams) {
                coordinatorParams.setAnchorId(ViewGroup.NO_ID);
                coordinatorParams.setBehavior(null);
                binding.searchView.setLayoutParams(coordinatorParams);
            }

            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            binding.searchView.setLayoutParams(params);

            binding.searchView.requestLayout();

        } catch (Exception e) {
            Log.e(TAG, "ensureFullScreen error: " + e.getMessage());
        }
    }

    public void setAvailableTags(List<Tag> tags) {
        binding.searchTagChips.removeAllViews();
        if (tags == null || tags.isEmpty()) {
            binding.searchTagsScroll.setVisibility(View.GONE);
            return;
        }

        Context ctx = binding.getRoot().getContext();

        Chip allChip = new Chip(ctx);
        allChip.setText(ctx.getString(R.string.search_filter_all));
        allChip.setCheckable(true);
        allChip.setChecked(true);
        allChip.setTag(null);
        binding.searchTagChips.addView(allChip);

        for (Tag tag : tags) {
            Chip chip = new Chip(ctx);
            chip.setText(tag.getNameTag());
            chip.setCheckable(true);
            chip.setTag(tag.getNameTag());
            binding.searchTagChips.addView(chip);
        }

        binding.searchTagChips.setOnCheckedStateChangeListener(
                (group, checkedIds) -> {
                    if (checkedIds.isEmpty()) return;
                    View checked = group.findViewById(checkedIds.get(0));
                    if (checked != null) {
                        Object tag = checked.getTag();
                        listener.onTagFilterChanged(tag instanceof String ? (String) tag : null);
                    }
                });

        binding.searchTagsScroll.setVisibility(View.VISIBLE);
    }

    public void clearSearch() {
        binding.searchView.getEditText().setText("");
        listener.onSearchQuery("");
        // Reset chip selection to "All"
        if (binding.searchTagChips.getChildCount() > 0) {
            View first = binding.searchTagChips.getChildAt(0);
            if (first instanceof Chip) ((Chip) first).setChecked(true);
        }
        listener.onTagFilterChanged(null);
    }

    public void destroy() {
        if (searchRunnable != null) handler.removeCallbacks(searchRunnable);
        searchRunnable = null;
    }

    public interface Listener {
        void onSearchOpen();

        void onSearchClose();

        void onSearchQuery(String query);

        void onTagFilterChanged(String tagName);
    }
}
