package com.pasich.mynotes.utils.backup.otherApp;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.pasich.mynotes.data.model.backup.googleKeep.GoogleKeepImportResult;
import com.pasich.mynotes.data.model.backup.googleKeep.GoogleKeepLabel;
import com.pasich.mynotes.data.model.backup.googleKeep.GoogleKeepNote;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public class GoogleKeepImportService {
    private static final String TAG = "GoogleKeepImportService";
    private static final String TAKEOUT_KEEP_PATH = "Takeout/Keep/";
    private static final String JSON_EXTENSION = ".json";

    private final Context context;
    private final Gson gson;

    @Inject
    public GoogleKeepImportService(@ApplicationContext Context context) {
        this.context = context;
        this.gson = new Gson();
    }

    public GoogleKeepImportResult importFromZip(Uri zipUri) {
        List<GoogleKeepNote> notes = new ArrayList<>();
        List<GoogleKeepNote> trashedNotes = new ArrayList<>();
        List<GoogleKeepLabel> labels = new ArrayList<>();

        try (InputStream inputStream = context.getContentResolver().openInputStream(zipUri); ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

            ZipEntry entry;
            boolean foundKeepFolder = false;

            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();

                // Проверяем, есть ли папка Takeout/Keep в архиве
                if (entryName.startsWith(TAKEOUT_KEEP_PATH)) {
                    foundKeepFolder = true;

                    // Обрабатываем только JSON файлы
                    if (entryName.endsWith(JSON_EXTENSION)) {
                        processJsonEntry(zipInputStream, notes, trashedNotes, labels);
                    }
                }
                zipInputStream.closeEntry();
            }

            if (!foundKeepFolder) {
                return GoogleKeepImportResult.error("The archive does not match the Google Takeout/Keep export format");
            }

            if (notes.isEmpty() && trashedNotes.isEmpty()) {
                return GoogleKeepImportResult.error("No notes were found in the archive");
            }

            return GoogleKeepImportResult.success(notes, trashedNotes, labels);

        } catch (IOException e) {
            Log.e(TAG, "Error while processing the ZIP archive", e);
            return GoogleKeepImportResult.error("Error while processing the ZIP archive: " + e.getMessage());
        }
    }

    private void processJsonEntry(ZipInputStream zipInputStream, List<GoogleKeepNote> notes, List<GoogleKeepNote> trashedNotes, List<GoogleKeepLabel> labels) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(zipInputStream));
        StringBuilder jsonContent = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            jsonContent.append(line);
        }

        try {
            GoogleKeepNote note = gson.fromJson(jsonContent.toString(), GoogleKeepNote.class);
            if (note != null) {
                if (note.isTrashed()) {
                    trashedNotes.add(note);
                } else {
                    if (!note.getFirstLabel().getName().isEmpty()) {
                        if (!labels.contains(note.getFirstLabel())) {
                            labels.add(note.getFirstLabel());
                        }
                    }
                    notes.add(note);
                }
            }
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "Failed to process JSON", e);
        }
    }
}