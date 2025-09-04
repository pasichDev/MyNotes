package com.pasich.mynotes.utils.file;

import android.content.Context;

import com.pasich.mynotes.data.model.Note;

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
            .append(getHtmlStyles())
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
    
    /**
     * Get HTML styles
     */
    private static String getHtmlStyles() {
        return """
                        * {
                            margin: 0;
                            padding: 0;
                            box-sizing: border-box;
                        }
                
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
                            line-height: 1.6;
                            color: #333;
                            background-color: #f8f9fa;
                            padding: 1rem;
                            max-width: 900px;
                            margin: 0 auto;
                        }
                
                        .header {
                            text-align: center;
                            margin-bottom: 3rem;
                            margin-top: 3rem;
                            padding-bottom: 1rem;
                            border-bottom: 3px solid #e9ecef;
                        }
                
                        .header h1 {
                            font-size: 2.5rem;
                            font-weight: 700;
                            color: #2c3e50;
                            margin-bottom: 0.5rem;
                        }
                
                        .header .subtitle {
                            color: #6c757d;
                            font-size: 1rem;
                        }
                
                        .note {
                            background: white;
                            border-radius: 8px;
                            padding: 2rem;
                            margin: 1rem;
                            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                            border-left: 4px solid #007bff;
                        }
                
                        .note-title {
                            font-size: 1.5rem;
                            font-weight: 600;
                            margin-bottom: 1rem;
                            color: #2c3e50;
                        }
                
                        .note-content {
                            font-size: 1rem;
                            line-height: 1.7;
                            white-space: pre-wrap;
                            word-wrap: break-word;
                            color: #495057;
                        }
                
                        @media (max-width: 768px) {
                            body {
                                padding: 0.75rem;
                            }
                           \s
                            .header h1 {
                                font-size: 2rem;
                            }
                           \s
                            .note {
                                margin-bottom: 1.5rem;
                            }
                           \s
                            .note-title {
                                font-size: 1.25rem;
                            }
                           \s
                            .note-content {
                                font-size: 0.95rem;
                            }
                        }
                
                        @media (max-width: 480px) {
                            body {
                                padding: 0.5rem;
                            }
                           \s
                            .header h1 {
                                font-size: 1.75rem;
                            }
                           \s
                            .note {
                                padding: 1rem;
                            }
                           \s
                            .note-title {
                                font-size: 1.1rem;
                            }
                        }
                
                        /* Темна тема */
                        @media (prefers-color-scheme: dark) {
                            body {
                                background-color: #121212;
                                color: #e0e0e0;
                            }
                           \s
                            .header h1 {
                                color: #ffffff;
                            }
                           \s
                            .header {
                                border-bottom-color: #404040;
                            }
                           \s
                            .note {
                                background-color: #1e1e1e;
                                border-left-color: #0d6efd;
                            }
                           \s
                            .note-title {
                                color: #ffffff;
                            }
                           \s
                            .note-content {
                                color: #d0d0d0;
                            }
                        }
                
                        /* Для друку */
                        @media print {
                            body {
                                padding: 0;
                                max-width: none;
                                background-color: white;
                            }
                           \s
                            .header h1 {
                                font-size: 24pt;
                            }
                           \s
                            .note {
                                box-shadow: none;
                                border: 1px solid #ddd;
                                page-break-inside: avoid;
                                margin-bottom: 1rem;
                            }
                           \s
                            .note-title {
                                font-size: 16pt;
                            }
                           \s
                            .note-content {
                                font-size: 12pt;
                            }
                        }
                """;
    }
}
