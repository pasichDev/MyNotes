package com.pasich.mynotes.ui.view.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.pasich.mynotes.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * An activity that is a gateway to save a note via the save button
 */
public class ShareActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();

        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            try {
                startNoteActivityIntent(readFile(getIntent().getData()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (intent.getType().equals("text/plain")) {

            startNoteActivityIntent(handleSendText());

        } else {
            Toast.makeText(this, getString(R.string.notSupportedShare), Toast.LENGTH_LONG).show();
        }

        finish();
    }


    /**
     * Method that implements the opening of an intent to create an annotation
     *
     * @param textShare - The text that we fumble in a note
     */
    private void startNoteActivityIntent(String textShare) {
        startActivity(new Intent(this, NoteActivity.class).putExtra("NewNote", true).putExtra("tagNote", "").putExtra("shareText", textShare));
    }


    /**
     * The method that opens the file from which we want to take the text for sharing
     *
     * @param uri - uri file
     * @return - text files share
     * @throws IOException - errors
     */
    private String readFile(Uri uri) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri)));
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            stringBuilder.append(line);
            stringBuilder.append('\n');
        }

        return stringBuilder.toString();
    }


    /**
     * Returns the received text from the heap
     *
     * @return - String (TextData share)
     */
    private String handleSendText() {
        String sharedText = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        return sharedText != null ? sharedText : "";
    }
}
