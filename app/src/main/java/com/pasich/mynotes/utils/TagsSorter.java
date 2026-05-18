package com.pasich.mynotes.utils;

import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.utils.constants.settings.SortParam;
import com.pasich.mynotes.utils.managers.SystemTagsManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TagsSorter {

    // Unified sorting logic for system tags (by weight) and user tags (by position or creation
    // date)
    public static List<Tag> sortTags(List<Tag> tags, String sortParam) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }

        List<Tag> sortedList = new ArrayList<>(tags);

        sortedList.sort(
                (o1, o2) -> {
                    int x1 = o1.getSystemAction();
                    int x2 = o2.getSystemAction();

                    if (o1.getSystemAction() == SystemTagsManager.SYSTEM_ACTION_ALL_NOTES) {
                        x1 = 98; // allNotes 1
                    }
                    if (o2.getSystemAction() == SystemTagsManager.SYSTEM_ACTION_ALL_NOTES) {
                        x2 = 98;
                    }

                    int sComp = Integer.compare(x2, x1);
                    if (sComp != 0) {
                        return sComp;
                    }

                    if (o1.getSystemAction() == 0 && o2.getSystemAction() == 0) {
                        if (SortParam.TagsPositionSort.equals(sortParam)) {
                            return Integer.compare(o1.getPosition(), o2.getPosition());
                        } else {
                            return Long.compare(o2.getId(), o1.getId());
                        }
                    }

                    return Long.compare(o2.getId(), o1.getId());
                });

        return sortedList;
    }
}
