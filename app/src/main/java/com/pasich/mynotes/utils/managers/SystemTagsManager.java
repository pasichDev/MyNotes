package com.pasich.mynotes.utils.managers;

import com.pasich.mynotes.data.model.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Менеджер для управління системними мітками
 */
public class SystemTagsManager {

    // Константи для системних дій
    public static final int SYSTEM_ACTION_ADD_TAG = 1;
    public static final int SYSTEM_ACTION_ALL_NOTES = 2;
    public static final int SYSTEM_ACTION_CHANGE_LOG = 3;
    public static final int SYSTEM_ACTION_USER_TAG = 0;

    /**
     * Отримати список системних міток
     * @return Список системних міток
     */
    public static List<Tag> getSystemTags() {
        return getSystemTags(false);
    }

    /**
     * Отримати список системних міток з опціональним тегом оновлень
     * @param includeChangeLog чи включати тег оновлень
     * @return Список системних міток
     */
    public static List<Tag> getSystemTags(boolean includeChangeLog) {
        List<Tag> systemTags = new ArrayList<>();
        
        // Системна мітка для додавання нової мітки
        Tag addTag = new Tag();
        addTag.setNameTag("");
        addTag.setVisibility(0);
        addTag.setSystemAction(SYSTEM_ACTION_ADD_TAG);
        systemTags.add(addTag);
        
        // Опціонально додаємо тег оновлень
        if (includeChangeLog) {
            Tag changeLogTag = new Tag();
            changeLogTag.setNameTag("change");
            changeLogTag.setVisibility(0);
            changeLogTag.setSystemAction(SYSTEM_ACTION_CHANGE_LOG);
            changeLogTag.setSelected(true);
            systemTags.add(changeLogTag);
        }
        
        // Системна мітка "всі нотатки"
        Tag allNotesTag = new Tag();
        allNotesTag.setNameTag("allNotes");
        allNotesTag.setVisibility(0);
        allNotesTag.setSystemAction(SYSTEM_ACTION_ALL_NOTES);
        allNotesTag.setSelected(true);
        systemTags.add(allNotesTag);
        
        return systemTags;
    }

    /**
     * Перевірити, чи є мітка системною
     * @param tag Мітка для перевірки
     * @return true, якщо мітка системна
     */
    public static boolean isSystemTag(Tag tag) {
        return tag.getSystemAction() != SYSTEM_ACTION_USER_TAG;
    }

    /**
     * Перевірити, чи є мітка міткою для додавання нової мітки
     * @param tag Мітка для перевірки
     * @return true, якщо це мітка для додавання
     */
    public static boolean isAddTag(Tag tag) {
        return tag.getSystemAction() == SYSTEM_ACTION_ADD_TAG;
    }

    /**
     * Перевірити, чи є мітка міткою "всі нотатки"
     * @param tag Мітка для перевірки
     * @return true, якщо це мітка "всі нотатки"
     */
    public static boolean isAllNotesTag(Tag tag) {
        return tag.getSystemAction() == SYSTEM_ACTION_ALL_NOTES;
    }

    /**
     * Перевірити, чи є мітка міткою "changelog/оновлення"
     * @param tag Мітка для перевірки
     * @return true, якщо це мітка changelog
     */
    public static boolean isChangeLogTag(Tag tag) {
        return tag.getSystemAction() == SYSTEM_ACTION_CHANGE_LOG;
    }

    /**
     * Створити системну мітку для додавання нової мітки
     * @return Системна мітка для додавання
     */
    public static Tag createAddTag() {
        Tag tag = new Tag();
        tag.setNameTag("");
        tag.setVisibility(0);
        tag.setSystemAction(SYSTEM_ACTION_ADD_TAG);
        return tag;
    }

    /**
     * Створити системну мітку "всі нотатки"
     * @return Системна мітка "всі нотатки"
     */
    public static Tag createAllNotesTag() {
        Tag tag = new Tag();
        tag.setNameTag("allNotes");
        tag.setVisibility(0);
        tag.setSystemAction(SYSTEM_ACTION_ALL_NOTES);
        tag.setSelected(true);
        return tag;
    }

    /**
     * Створити системну мітку "changelog/оновлення"
     * @return Системна мітка changelog
     */
    public static Tag createChangeLogTag() {
        Tag tag = new Tag();
        tag.setNameTag("change");
        tag.setVisibility(0);
        tag.setSystemAction(SYSTEM_ACTION_CHANGE_LOG);
        tag.setSelected(true);
        return tag;
    }
}
