package com.pasich.mynotes.utils.file;


import android.content.Context;

import com.pasich.mynotes.data.model.Note;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Utility class for generating HTML templates and content
 */
public class HtmlTemplateGenerator {

    /**
     * Generate complete HTML content from notes
     */
    public static String generateHtmlContent(Context context, String noteTitle, String noteContent, List<Note> notes) {
        StringBuilder html = new StringBuilder();

        // Get current date
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMMM yyyy", getCurrentLocale(context));
        String currentDate = dateFormat.format(new Date());

        // Get localized strings
        String notesTitle = getLocalizedNotesTitle(context);
        String exportFromText = getLocalizedExportFromText(context);

        // HTML template start
        html.append("<!DOCTYPE html>\n")
                .append("<html lang=\"").append(getCurrentLanguageCode(context)).append("\">\n")
                .append("<head>\n")
                .append("    <meta charset=\"UTF-8\">\n")
                .append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                .append("    <title>").append(escapeHtml(notesTitle)).append("</title>\n")
                .append("    <style>\n")
                .append(loadCssFromAssets(context))
                .append("    </style>\n")
                .append("</head>\n")
                .append("<body>\n")
                .append("    <div class=\"header\">\n")
                .append("        <h1>").append(escapeHtml(notesTitle)).append("</h1>\n")
                .append("        <div class=\"subtitle\">").append(escapeHtml(exportFromText)).append(" ").append(escapeHtml(currentDate)).append("</div>\n")
                .append("    </div>\n\n");

        // Add notes content
        if (notes != null && !notes.isEmpty()) {
            // Multiple notes
            for (Note note : notes) {
                addNoteToHtml(html, note.getTitle(), note.getValue());
            }
        } else {
            // Single note
            addNoteToHtml(html, noteTitle, noteContent);
        }

        // HTML template end
        html.append("</body>\n")
                .append("</html>");

        return html.toString();
    }

    /**
     * Add single note to HTML
     */
    private static void addNoteToHtml(StringBuilder html, String title, String content) {
        String noteTitle = (title == null || title.trim().isEmpty()) ? "***" : title.trim();
        String noteContent = (content == null) ? "" : content.trim();

        html.append("    <div class=\"note\">\n")
                .append("        <div class=\"note-title\">").append(escapeHtml(noteTitle)).append("</div>\n")
                .append("        <div class=\"note-content\">").append(escapeHtml(noteContent)).append("</div>\n")
                .append("    </div>\n\n");
    }

    /**
     * Escape HTML special characters
     */
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("\n", "<br>");
    }

    /**
     * Get current locale
     */
    private static Locale getCurrentLocale(Context context) {
        return context.getResources().getConfiguration().getLocales().get(0);
    }

    /**
     * Get current language code
     */
    private static String getCurrentLanguageCode(Context context) {
        return getCurrentLocale(context).getLanguage();
    }

    /**
     * Get localized "My Notes" title
     */
    private static String getLocalizedNotesTitle(Context context) {
        String language = getCurrentLanguageCode(context);
        return switch (language) {
            case "uk" -> "Мої нотатки";
            case "ru" -> "Мои заметки";
            case "de" -> "Meine Notizen";
            case "fr" -> "Mes notes";
            case "es" -> "Mis notas";
            case "it" -> "Le mie note";
            case "pl" -> "Moje notatki";
            case "be" -> "Мае нататкі";
            case "kk" -> "Менің жазбаларым";
            default -> "My Notes";
        };
    }

    /**
     * Get localized "Export from" text
     */
    private static String getLocalizedExportFromText(Context context) {
        String language = getCurrentLanguageCode(context);
        return switch (language) {
            case "uk" -> "Експорт від";
            case "ru" -> "Экспорт от";
            case "de" -> "Export vom";
            case "fr" -> "Export du";
            case "es" -> "Exportar del";
            case "it" -> "Esporta da";
            case "pl" -> "Eksport z";
            case "be" -> "Экспарт ад";
            case "kk" -> "Экспорт";
            default -> "Export from";
        };
    }

    private static String loadCssFromAssets(Context context) {
        try {
            assert context != null;
            try (InputStream is = context.getAssets().open("notes_styles.css");
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            }
        } catch (IOException e) {


            return ""; // fallback
        }
    }

}
