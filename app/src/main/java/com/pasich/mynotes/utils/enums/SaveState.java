package com.pasich.mynotes.utils.enums;

public enum SaveState {
    IDLE, // Без змін
    PENDING, // Є незбережені зміни
    SAVING, // Процес збереження
    SAVED, // Успішно збережено
    ERROR // Помилка збереження
}
