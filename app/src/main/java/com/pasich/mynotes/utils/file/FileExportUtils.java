package com.pasich.mynotes.utils.file;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.pasich.mynotes.R;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utility class for exporting notes to different file formats
 */
public class FileExportUtils {
    
    private static final String TAG = "FileExportUtils";
    private static final String GOOGLE_DRIVE_PACKAGE = "com.google.android.apps.docs";
    
    /**
     * Create intent for saving TXT file with system file picker
     */
    public static Intent createSaveTxtIntent(String noteTitle) {
        String fileName = generateFileName(noteTitle, "txt");
        
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        
        return intent;
    }
    
    /**
     * Create intent for saving PDF file with system file picker
     */
    public static Intent createSavePdfIntent(String noteTitle) {
        String fileName = generateFileName(noteTitle, "pdf");
        
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        
        return intent;
    }
    
    /**
     * Open Google Drive intent to save file
     */
    public static void saveToGoogleDrive(Context context, String noteTitle, String noteContent) {
        try {
            // Check if Google Drive is installed
            if (!isAppInstalled(context)) {
                Toast.makeText(context, context.getString(R.string.googleDriveNotInstalled), Toast.LENGTH_LONG).show();
                return;
            }
            
            // Create intent to share with Google Drive
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, noteContent);
            intent.putExtra(Intent.EXTRA_SUBJECT, noteTitle);
            intent.setPackage(GOOGLE_DRIVE_PACKAGE);
            
            context.startActivity(intent);
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving to Google Drive", e);
            Toast.makeText(context, context.getString(R.string.errorSavingFile), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Share note through other apps
     */
    public static void shareViaOtherApps(Context context, String noteTitle, String noteContent) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, noteContent);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, noteTitle);
        
        Intent chooser = Intent.createChooser(shareIntent, context.getString(R.string.shareViaApps));
        context.startActivity(chooser);
    }
    
    /**
     * Save TXT content to selected URI
     */
    public static void saveTxtToUri(Context context, Uri uri, String noteTitle, String noteContent) {
        try {
            String formattedContent = formatNoteContent(noteTitle, noteContent);

            OutputStream outputStream = context.getContentResolver().openOutputStream(uri);

            if (outputStream != null) {
                // Add UTF-8 BOM for better compatibility
                byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
                outputStream.write(bom);
                
                byte[] contentBytes = formattedContent.getBytes(StandardCharsets.UTF_8);
                
                outputStream.write(contentBytes);
                outputStream.flush();
                outputStream.close();

                Toast.makeText(context, context.getString(R.string.fileSavedSuccessfully), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, context.getString(R.string.errorSavingFile), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving TXT file", e);
            Toast.makeText(context, context.getString(R.string.errorSavingFile), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Save PDF content to selected URI
     */
    public static void savePdfToUri(Context context, Uri uri, String noteTitle, String noteContent) {
        try {
            // Create PDF document
            PdfDocument pdfDocument = new PdfDocument();
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create(); // A4 size
            PdfDocument.Page page = pdfDocument.startPage(pageInfo);
            
            Canvas canvas = page.getCanvas();
            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setTextSize(12);
            paint.setAntiAlias(true);
            
            int y = 50;
            int lineHeight = 20;
            int maxWidth = 500;
            
            // Title
            String title = (noteTitle == null || noteTitle.trim().isEmpty()) ? "***" : noteTitle.trim();
            
            paint.setTextSize(16);
            paint.setFakeBoldText(true);
            canvas.drawText(title, 50, y, paint);
            y += 30; // Extra space after title
            
            // Content
            paint.setTextSize(12);
            paint.setFakeBoldText(false);
            
            if (noteContent != null && !noteContent.trim().isEmpty()) {
                String[] lines = noteContent.trim().split("\n");
                
                for (String line : lines) {
                    if (y > 800) { // Start new page if needed
                        pdfDocument.finishPage(page);
                        pageInfo = new PdfDocument.PageInfo.Builder(595, 842, pdfDocument.getPages().size() + 1).create();
                        page = pdfDocument.startPage(pageInfo);
                        canvas = page.getCanvas();
                        y = 50;
                    }
                    
                    // Handle line wrapping
                    if (paint.measureText(line) > maxWidth) {
                        String[] words = line.split(" ");
                        StringBuilder currentLine = new StringBuilder();
                        
                        for (String word : words) {
                            if (paint.measureText(currentLine + word + " ") > maxWidth) {
                                if (currentLine.length() > 0) {
                                    canvas.drawText(currentLine.toString(), 50, y, paint);
                                    y += lineHeight;
                                    currentLine = new StringBuilder(word + " ");
                                }
                            } else {
                                currentLine.append(word).append(" ");
                            }
                        }
                        
                        if (currentLine.length() > 0) {
                            canvas.drawText(currentLine.toString(), 50, y, paint);
                            y += lineHeight;
                        }
                    } else {
                        canvas.drawText(line, 50, y, paint);
                        y += lineHeight;
                    }
                }
            } else {
                Log.d(TAG, "PDF Content is empty");
            }
            
            pdfDocument.finishPage(page);
            
            // Save PDF to selected URI
            OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
            Log.d(TAG, "PDF OutputStream opened: " + (outputStream != null));
            
            if (outputStream != null) {
                pdfDocument.writeTo(outputStream);
                outputStream.flush();
                outputStream.close();
                
                Log.d(TAG, "PDF written successfully");
                Toast.makeText(context, context.getString(R.string.fileSavedSuccessfully), Toast.LENGTH_SHORT).show();
            } else {
                Log.e(TAG, "PDF OutputStream is null");
                Toast.makeText(context, context.getString(R.string.errorSavingFile), Toast.LENGTH_SHORT).show();
            }
            
            pdfDocument.close();
            
        } catch (Exception e) {
            Toast.makeText(context, context.getString(R.string.errorSavingFile), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Generate file name with timestamp and handle empty titles
     */
    private static String generateFileName(String noteTitle, String extension) {
        // Handle empty or null title
        String title = (noteTitle == null || noteTitle.trim().isEmpty()) ? "***" : noteTitle.trim();
        
        String sanitizedTitle = title.replaceAll("[^a-zA-Z0-9\\u0400-\\u04FF.-]", "_");
        if (sanitizedTitle.length() > 50) {
            sanitizedTitle = sanitizedTitle.substring(0, 50);
        }
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return sanitizedTitle + "_" + timestamp + "." + extension;
    }
    
    /**
     * Format note content with title and text properly
     */
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
    
    /**
     * Check if app is installed
     */
    private static boolean isAppInstalled(Context context) {
        try {
            context.getPackageManager().getApplicationInfo(FileExportUtils.GOOGLE_DRIVE_PACKAGE, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
