package com.pasich.mynotes.ui.controllers;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.util.Log;
import android.view.ViewGroup;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.search.SearchView;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.ActivityMainBinding;
import com.pasich.mynotes.utils.adapters.searchAdapter.SearchNotesAdapter;
import com.pasich.mynotes.base.simplifications.TextWatcher;

import java.util.List;

public class SearchController {

    private static final int DEBOUNCE_DELAY = 400;

    private final ActivityMainBinding binding;
    private final SearchNotesAdapter searchAdapter;

    private final Handler handler = new Handler(Looper.getMainLooper());
      private Runnable searchRunnable;

    public interface Listener {
        void onSearchOpen();
        void onSearchClose();
    }

    private final Listener listener;

    public SearchController(ActivityMainBinding binding,
                            SearchNotesAdapter searchAdapter,
                            Listener listener) {
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
                new LinearLayoutManager(binding.getRoot().getContext())
        );
        binding.resultsSearchList.setAdapter(searchAdapter);
        binding.resultsSearchList.setOverScrollMode(ViewGroup.OVER_SCROLL_NEVER);
    }

    private void setupSearchBehavior() {
        binding.searchView.addTransitionListener((searchView, oldState, newState) -> {

            if (newState == SearchView.TransitionState.SHOWING) {
                listener.onSearchOpen();
                ensureFullScreen();
            }

            if (newState == SearchView.TransitionState.HIDDEN) {
                listener.onSearchClose();
                clearSearch();
            }
        });

        binding.actionSearch.setOnClickListener(v ->
                binding.searchView.show()
        );
    }

    private void setupSearchInput() {
        binding.searchView.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            protected void changeText(Editable s) {

                if (searchRunnable != null)
                    handler.removeCallbacks(searchRunnable);

                searchRunnable = () -> {
                    if (s.length() >= 2) {
                        searchAdapter.filter(s.toString());
                    } else {
                        searchAdapter.cleanResult();
                    }
                };

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
            Log.e("SearchController", "ensureFullScreen error: " + e.getMessage());
        }
    }

    public void setDefaultNotesList(List<Note> notes) {
        searchAdapter.setDefaultListNotes(notes);
    }

    public void clearSearch() {
        binding.searchView.getEditText().setText("");
        searchAdapter.cleanResult();
    }

    public void destroy() {
        if (searchRunnable != null)
            handler.removeCallbacks(searchRunnable);
        searchRunnable = null;
    }
}
