package com.pasich.mynotes.utils.file;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.FileProvider;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Note;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Utility class for exporting notes to different file formats */
public class FileExportUtils {

    private static final String TAG = "FileExportUtils";
    public static final String GOOGLE_DRIVE_PACKAGE = "com.google.android.apps.docs";

    /** Create intent for saving TXT file with system file picker */
    public static Intent createSaveTxtIntent(String noteTitle) {
        String fileName = generateFileName(noteTitle, "txt");

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);

        return intent;
    }

    /** Create intent for saving PDF file with system file picker */
    public static Intent createSavePdfIntent(String noteTitle) {
        String fileName = generateFileName(noteTitle, "pdf");

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);

        return intent;
    }

    /** Create intent for saving HTML file with system file picker */
    public static Intent createSaveHtmlIntent(String noteTitle) {
        String fileName = generateFileName(noteTitle, "html");

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/html");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);

        return intent;
    }

    /** Open Google Drive intent to save file */
    public static void saveToGoogleDrive(Context context, String noteTitle, String noteContent) {
        try {
            if (!isAppInstalled(context)) {
                Toast.makeText(
                                context,
                                context.getString(R.string.googleDriveNotInstalled),
                                Toast.LENGTH_LONG)
                        .show();
                return;
            }

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, noteContent);
            intent.putExtra(Intent.EXTRA_SUBJECT, noteTitle);
            intent.setPackage(GOOGLE_DRIVE_PACKAGE);
            context.startActivity(intent);

        } catch (Exception e) {
            Log.e(TAG, "Error saving to Google Drive", e);
            Toast.makeText(context, context.getString(R.string.errorSavingFile), Toast.LENGTH_SHORT)
                    .show();
        }
    }

    /** Writes backup JSON to cache and delivers its URI via {@code callback}. */
    public static void saveBackupToGoogleDrive(
            Context context, String jsonContent, DriveProcess callback) {
        String fileName = generateBackupFileName();
        try {
            if (!isAppInstalled(context)) {
                Toast.makeText(
                                context,
                                context.getString(R.string.googleDriveNotInstalled),
                                Toast.LENGTH_LONG)
                        .show();
                return;
            }

            File dir = context.getCacheDir();
            for (File f : Objects.requireNonNull(dir.listFiles())) {
                if (f.getName().startsWith("MyNotes_Backup_") && f.getName().endsWith(".json")) {
                    f.delete();
                }
            }

            File file = new File(context.getCacheDir(), fileName + ".json");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(jsonContent.getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }

            Uri fileUri =
                    FileProvider.getUriForFile(
                            context,
                            context.getPackageName() + ".provider", // authorities
                            file);
            callback.onSuccess(fileUri, fileName);

        } catch (Exception e) {
            callback.onError(context.getString(R.string.errorSavingFile));
            Log.e("DriveExport", "Error saving to Google Drive", e);
        }
    }

    /** Share note through other apps */
    public static void shareViaOtherApps(Context context, String noteTitle, String noteContent) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, noteContent);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, noteTitle);

        Intent chooser =
                Intent.createChooser(shareIntent, context.getString(R.string.shareViaApps));
        context.startActivity(chooser);
    }

    /**
     * Saves TXT content to the selected URI. Runs on a background thread and shows nothing; returns
     * whether the file was written.
     */
    @WorkerThread
    public static boolean saveTxtToUri(
            Context context, Uri uri, String noteTitle, String noteContent) {
        try {
            String formattedContent = formatNoteContent(noteTitle, noteContent);

            return writeUtf8WithBom(context, uri, formattedContent);
        } catch (Exception e) {
            Log.e(TAG, "Error saving TXT file", e);
            return false;
        }
    }

    /** Measures how wide a piece of text is drawn. */
    interface TextWidth {
        float of(String text);
    }

    private static final int PDF_TOP = 50;

    /** Lowest baseline a body line may be drawn at before the next page starts. */
    private static final int PDF_BOTTOM = 800;

    // A4 in points.
    private static final int PDF_PAGE_WIDTH = 595;
    private static final int PDF_PAGE_HEIGHT = 842;
    private static final int PDF_LEFT = 50;
    private static final int PDF_LINE_HEIGHT = 20;
    private static final int PDF_TEXT_WIDTH = 500;

    /**
     * Writes text as UTF-8 behind a byte-order mark, closing the stream even when a write fails.
     *
     * @return false when the document could not be opened for writing.
     */
    private static boolean writeUtf8WithBom(Context context, Uri uri, String text)
            throws java.io.IOException {
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
            if (outputStream == null) {
                return false;
            }
            // Add UTF-8 BOM for better compatibility
            outputStream.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            outputStream.write(text.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            return true;
        }
    }

    /**
     * Saves PDF content to the selected URI. Runs on a background thread and shows nothing; returns
     * whether the file was written.
     */
    @WorkerThread
    public static boolean savePdfToUri(
            Context context, Uri uri, String noteTitle, String noteContent) {
        PdfDocument pdfDocument = new PdfDocument();
        try {
            PdfDocument.PageInfo pageInfo =
                    new PdfDocument.PageInfo.Builder(PDF_PAGE_WIDTH, PDF_PAGE_HEIGHT, 1).create();
            PdfDocument.Page page = pdfDocument.startPage(pageInfo);

            Canvas canvas = page.getCanvas();
            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setAntiAlias(true);

            int y = PDF_TOP;

            String title =
                    (noteTitle == null || noteTitle.trim().isEmpty()) ? "***" : noteTitle.trim();

            paint.setTextSize(16);
            paint.setFakeBoldText(true);
            for (String titleLine : wrapLine(title, PDF_TEXT_WIDTH, paint::measureText)) {
                canvas.drawText(titleLine, PDF_LEFT, y, paint);
                y += PDF_LINE_HEIGHT;
            }
            y += 10; // Extra space after title

            paint.setTextSize(12);
            paint.setFakeBoldText(false);

            if (noteContent != null && !noteContent.trim().isEmpty()) {
                for (String line : noteContent.trim().split("\n")) {
                    // The page break is checked per drawn line, not per paragraph: a long
                    // paragraph wraps to many lines and ran off the bottom of the page.
                    for (String drawn : wrapLine(line, PDF_TEXT_WIDTH, paint::measureText)) {
                        if (y > PDF_BOTTOM) {
                            pdfDocument.finishPage(page);
                            pageInfo =
                                    new PdfDocument.PageInfo.Builder(
                                                    PDF_PAGE_WIDTH,
                                                    PDF_PAGE_HEIGHT,
                                                    pdfDocument.getPages().size() + 1)
                                            .create();
                            page = pdfDocument.startPage(pageInfo);
                            canvas = page.getCanvas();
                            y = PDF_TOP;
                        }
                        canvas.drawText(drawn, PDF_LEFT, y, paint);
                        y += PDF_LINE_HEIGHT;
                    }
                }
            } else {
                Log.d(TAG, "PDF Content is empty");
            }

            pdfDocument.finishPage(page);

            try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
                if (outputStream == null) {
                    Log.e(TAG, "PDF OutputStream is null");
                    return false;
                }
                pdfDocument.writeTo(outputStream);
                outputStream.flush();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error saving PDF file", e);
            return false;
        } finally {
            pdfDocument.close();
        }
    }

    /**
     * Splits one source line into the lines drawn for it, none wider than {@code maxWidth}.
     *
     * <p>Lines break between words; a single word wider than the whole line is broken between
     * characters, since a word that cannot fit anywhere was otherwise never drawn at all.
     *
     * <p>The line's leading whitespace is its indent (a nested list item, a code block), and every
     * line drawn for it starts with it. Splitting on spaces used to turn the indent into empty
     * words that the first line swallowed, so a wrapped line lost its indent while a short one kept
     * it. An indent wider than half the line is cut to half, so some text always fits beside it.
     */
    @VisibleForTesting
    static List<String> wrapLine(String line, float maxWidth, TextWidth width) {
        List<String> out = new ArrayList<>();
        if (width.of(line) <= maxWidth) {
            out.add(line);
            return out;
        }
        int bodyStart = 0;
        while (bodyStart < line.length()
                && (line.charAt(bodyStart) == ' ' || line.charAt(bodyStart) == '\t')) {
            bodyStart++;
        }
        String indent = line.substring(0, bodyStart);
        while (!indent.isEmpty() && width.of(indent) > maxWidth / 2) {
            indent = indent.substring(0, indent.length() - 1);
        }
        float bodyWidth = maxWidth - width.of(indent);
        for (String drawn : wrapWords(line.substring(bodyStart), bodyWidth, width)) {
            out.add(indent + drawn);
        }
        if (out.isEmpty()) {
            out.add(indent);
        }
        return out;
    }

    /** Breaks text that starts with no whitespace into lines no wider than {@code maxWidth}. */
    private static List<String> wrapWords(String text, float maxWidth, TextWidth width) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ", -1)) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (width.of(candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (current.length() > 0) {
                out.add(current.toString());
                current.setLength(0);
            }
            while (width.of(word) > maxWidth) {
                int cut = fittingPrefix(word, maxWidth, width);
                out.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            current.append(word);
        }
        if (current.length() > 0) {
            out.add(current.toString());
        }
        return out;
    }

    /**
     * The length of the longest prefix of {@code word} that fits, never splitting a surrogate pair
     * and never less than one character, so the caller always makes progress.
     */
    private static int fittingPrefix(String word, float maxWidth, TextWidth width) {
        int low = word.offsetByCodePoints(0, 1);
        int high = word.length();
        // Invariant: the prefix of length low is taken (it fits, or is the forced minimum).
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (Character.isHighSurrogate(word.charAt(mid - 1))) {
                mid--; // the prefix would end between the two halves of one character
            }
            if (mid <= low) {
                break;
            }
            if (width.of(word.substring(0, mid)) <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    /**
     * Saves HTML content to the selected URI. Runs on a background thread and shows nothing;
     * returns whether the file was written.
     */
    @WorkerThread
    public static boolean saveHtmlToUri(
            Context context, Uri uri, String noteTitle, String noteContent, List<Note> notes) {
        try {
            String htmlContent =
                    HtmlTemplateGenerator.generateHtmlContent(
                            context, noteTitle, noteContent, notes);

            return writeUtf8WithBom(context, uri, htmlContent);
        } catch (Exception e) {
            Log.e(TAG, "Error saving HTML file", e);
            return false;
        }
    }

    /** Generate file name with timestamp and handle empty titles */
    private static String generateFileName(String noteTitle, String extension) {
        // Handle empty or null title
        String title = (noteTitle == null || noteTitle.trim().isEmpty()) ? "***" : noteTitle.trim();

        String sanitizedTitle = title.replaceAll("[^a-zA-Z0-9\\u0400-\\u04FF.-]", "_");
        if (sanitizedTitle.length() > 50) {
            sanitizedTitle = sanitizedTitle.substring(0, 50);
        }

        String timestamp =
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return sanitizedTitle + "_" + timestamp + "." + extension;
    }

    /** Format note content with title and text properly */
    public static String formatNoteContent(String noteTitle, String noteContent) {
        StringBuilder formattedContent = new StringBuilder();

        // Handle title
        String title = (noteTitle == null || noteTitle.trim().isEmpty()) ? "***" : noteTitle.trim();
        formattedContent.append(title).append("\n\n");

        // Add content if exists
        if (noteContent != null && !noteContent.trim().isEmpty()) {
            formattedContent.append(noteContent.trim());
        }

        return formattedContent.toString();
    }

    /** Check if app is installed */
    public static boolean isAppInstalled(Context context) {
        try {
            context.getPackageManager().getApplicationInfo(FileExportUtils.GOOGLE_DRIVE_PACKAGE, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns whether the device has an activity that can handle the exact intent. */
    public static boolean canHandleIntent(PackageManager packageManager, Intent intent) {
        return intent.resolveActivity(packageManager) != null;
    }

    /** Generates a timestamped backup file name with .json extension. */
    public static String generateBackupFileName() {
        // Поточна дата
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("dd_MM_yy_HH_mm", Locale.getDefault());
        String dateStr = sdf.format(now);

        return "MyNotes_Backup_" + dateStr + ".json";
    }
}
