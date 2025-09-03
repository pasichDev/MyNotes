package com.pasich.mynotes.utils.adapters.notes;


import androidx.annotation.NonNull;
import androidx.databinding.ViewDataBinding;

import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.utils.adapters.baseGenericAdapter.GenericAdapter;
import com.pasich.mynotes.utils.adapters.baseGenericAdapter.GenericAdapterCallback;
import com.pasich.mynotes.utils.recycler.diffutil.DiffUtilNote;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.inject.Inject;

public class NoteAdapter<VM extends ViewDataBinding> extends GenericAdapter<Note, VM> {

    private final List<String> nameTagsHidden = new ArrayList<>();
    private List<Note> defaultList = new ArrayList<>();
    private androidx.recyclerview.widget.RecyclerView recyclerView;
    private boolean shouldScrollToTop = false;


    @Inject
    public NoteAdapter(@NonNull DiffUtilNote diffCallback, int layoutId, GenericAdapterCallback<VM, Note> bindingInterface) {
        super(diffCallback, layoutId, bindingInterface);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        this.recyclerView = null;
    }

    /**
     * Встановлює флаг для прокручування до верху при наступному оновленні списку
     */
    public void setScrollToTopOnNextUpdate(boolean scrollToTop) {
        this.shouldScrollToTop = scrollToTop;
    }


    public int setNameTagsHidden(List<Tag> tagList, String nameTag) {
        nameTagsHidden.clear();
        for (Tag tag : tagList) {
            if (tag.getVisibility() == 1) nameTagsHidden.add(tag.getNameTag());
        }
        if (nameTag.equals("allNotes")) return updateFromVisibilityTags();
        else return getItemCount();
    }


    public void sortList(String arg) {
        ArrayList<Note> newList = new ArrayList<>(getCurrentList());
        newList.sort(new NoteComparator().getComparator(arg));
        submitList(newList);
        submitList(newList, () -> {
            if (shouldScrollToTop && recyclerView != null && !newList.isEmpty()) {
                recyclerView.post(() -> {
                    androidx.recyclerview.widget.StaggeredGridLayoutManager layoutManager =
                        (androidx.recyclerview.widget.StaggeredGridLayoutManager) recyclerView.getLayoutManager();
                    if (layoutManager != null) {
                        layoutManager.scrollToPositionWithOffset(0, 0);
                    }
                });
                shouldScrollToTop = false; // Reset flag after use
            }
        });
    }

    public int sortList(List<Note> notesList, String arg, String tagSelected) {
        notesList.sort(new NoteComparator().getComparator(arg));
        defaultList = notesList;
        return filter(tagSelected);
    }


    public int updateFromVisibilityTags() {

        ArrayList<Note> newList = new ArrayList<>();
        if (!defaultList.isEmpty()) {
            for (Note item : defaultList) {
                if (!nameTagsHidden.contains(item.getTag())) {
                    newList.add(item);
                }
            }
            submitList(newList);
        }
        return newList.size();

    }


    public int filter(String tagSelected) {
        return filter(tagSelected, true);
    }
    
    public int filter(String tagSelected, boolean updateList) {
        ArrayList<Note> newFilter = new ArrayList<>();

        if (tagSelected.equals("allNotes")) {
            for (Note item : defaultList) {
                if (!nameTagsHidden.contains(item.getTag())) {
                    newFilter.add(item);
                }
            }

            if (!nameTagsHidden.isEmpty()) {
                if (updateList) {
                    submitList(newFilter);
                }
                return newFilter.size();
            } else {
                if (updateList) {
                    submitList(defaultList, () -> {
                       if (shouldScrollToTop && recyclerView != null && !getCurrentList().isEmpty()) {
                            recyclerView.postDelayed(() -> {
                                androidx.recyclerview.widget.StaggeredGridLayoutManager layoutManager = 
                                    (androidx.recyclerview.widget.StaggeredGridLayoutManager) recyclerView.getLayoutManager();
                                if (layoutManager != null) {
                                    int[] firstVisibleItemPositions = layoutManager.findFirstVisibleItemPositions(null);
                                    boolean isFirstItemVisible = firstVisibleItemPositions.length > 0 && firstVisibleItemPositions[0] == 0;
                                    
                                    if (!isFirstItemVisible) {
                                        layoutManager.scrollToPositionWithOffset(0, 0);
                                    }
                                }
                            }, 50); // Зменшили затримку для швидшої реакції
                            shouldScrollToTop = false; // Скидаємо флаг після використання
                        }
                    });
                }
                return defaultList.size();
            }
        } else {
            for (Note item : defaultList) {

                if (item.getTag().equals(tagSelected)) {
                    newFilter.add(item);
                }
            }

            if (updateList) {
                submitList(newFilter);
            }
            return newFilter.size();
        }

    }
    
    public void clearList() {
        submitList(new ArrayList<>());
    }


    public static class NoteComparator {
        public Comparator<Note> getComparator(String arg) {
            return switch (arg) {
                case "DataSort" -> (e1, e2) -> Long.compare(e2.getDate(), e1.getDate());
                case "TitleSort" ->
                        (e1, e2) -> e1.getTitle().toLowerCase().compareTo(e2.getTitle().toLowerCase());
                case "TitleReserve" ->
                        (e1, e2) -> e2.getTitle().toLowerCase().compareTo(e1.getTitle().toLowerCase());
                default -> (e1, e2) -> Long.compare(e1.getDate(), e2.getDate());
            };
        }
    }

}
