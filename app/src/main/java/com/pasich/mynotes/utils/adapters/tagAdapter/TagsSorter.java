package com.pasich.mynotes.utils.adapters.tagAdapter;


import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.utils.managers.SystemTagsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TagsSorter {

    public static List<Tag> sortTags(List<Tag> tags, String sortParam) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }

        // Створюємо копію, щоб не мутувати оригінальний список
        List<Tag> sortedList = new ArrayList<>(tags);

        sortedList.sort((o1, o2) -> {
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

            // Користувацькі теги
            if (o1.getSystemAction() == 0 && o2.getSystemAction() == 0) {
                if ("TagsPositionSort".equals(sortParam)) {
                    // Сортування за позицією
                    return Integer.compare(o1.getPosition(), o2.getPosition());
                } else {
                    // Сортування за датою створення (ID)
                    return Long.compare(o2.getId(), o1.getId());
                }
            }

            // Для системних тегів за замовчуванням сортуємо за ID
            return Long.compare(o2.getId(), o1.getId());
        });

        return sortedList;
    }
}
