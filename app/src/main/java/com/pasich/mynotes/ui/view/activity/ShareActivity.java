package com.pasich.mynotes.ui.view.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.pasich.mynotes.R;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.utils.navigation.NoteNavigator;
import com.pasich.mynotes.utils.processors.ShareProcessor;
import com.pasich.mynotes.utils.processors.SharedNoteCreator;

import java.io.IOException;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;


/**
 * An activity that is a gateway to save a note via the save button
 * or from text selection context menu
 */

@AndroidEntryPoint
public class ShareActivity extends AppCompatActivity {

    @Inject
    SharedNoteCreator noteCreator;
    @Inject
    ThemePreferencesCache themePreferencesCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        Intent intent = getIntent();

        try {

            // Context menu “process text”
            if (Intent.ACTION_PROCESS_TEXT.equals(intent.getAction())) {
                String text = ShareProcessor.extractProcessText(intent);
                createNoteAndOpen(text);
                return;
            }

            // Opening a file
            if (Intent.ACTION_VIEW.equals(intent.getAction())) {

                Uri uri = intent.getData();

                ShareProcessor.readFileAsync(
                        getContentResolver(),
                        uri,
                        new ShareProcessor.FileReadCallback() {
                            @Override
                            public void onSuccess(String content) {
                                runOnUiThread(() -> createNoteAndOpen(content));
                            }

                            @Override
                            public void onError(IOException e) {
                                Toast.makeText(ShareActivity.this,
                                        "Error reading file",
                                        Toast.LENGTH_LONG).show();
                                finish();
                            }
                        }
                );
                return;
            }

            // 3) Стандартний share intent
            if (ShareProcessor.isShareIntent(intent)) {
                String text = ShareProcessor.extractSharedText(intent);
                createNoteAndOpen(text);
                return;
            }

            Toast.makeText(this,
                    getString(R.string.notSupportedShare),
                    Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this,
                    "Error processing shared content",
                    Toast.LENGTH_LONG).show();
        }

        finish();
    }


    /**
     * Creates a note in the database and opens the editor
     */
    private void createNoteAndOpen(String text) {

        if (text == null) text = "";

        if (ShareProcessor.isTooLarge(text)) {
            Toast.makeText(this,
                    "Text is too large to share",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Creating a note using SharedNoteCreator
        noteCreator.create(text, new SharedNoteCreator.Callback() {
            @Override
            public void onCreated(long id) {
                openEditor(id);
            }

            @Override
            public void onError(Throwable error) {
                Toast.makeText(ShareActivity.this,
                        "Failed to save note",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }


    /**
     * Opens NoteActivity with an already created note
     */
    private void openEditor(long noteId) {
        new NoteNavigator(this, themePreferencesCache)
                .openNote(noteId, true, "",
                        null, null, false);

        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        noteCreator.clear();
    }
}
