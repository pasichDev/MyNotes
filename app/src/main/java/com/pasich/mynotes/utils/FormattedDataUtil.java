package com.pasich.mynotes.utils;


import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

public class FormattedDataUtil {


    /**
     * This method determines what result to display on the last change day or time
     *
     * @param date - last modified date
     * @return - formatted date
     */

    public static String lastDayEditNote(long date) {
        final GregorianCalendar newDate = new GregorianCalendar();
        final int dayNote = Integer.parseInt(new SimpleDateFormat("dd", Locale.getDefault()).format(date));
        final int montNote = Integer.parseInt(new SimpleDateFormat("MM", Locale.getDefault()).format(date));

        if (newDate.get(Calendar.DAY_OF_MONTH) == dayNote && newDate.get(Calendar.MONTH) + 1 == montNote)
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(date);
        else return new SimpleDateFormat("d MMM", Locale.getDefault()).format(date);

    }

    public static String lastDataCloudBackup(long date) {
        return new SimpleDateFormat("d.MM.yyyy HH:mm", Locale.getDefault()).format(date);
    }
}