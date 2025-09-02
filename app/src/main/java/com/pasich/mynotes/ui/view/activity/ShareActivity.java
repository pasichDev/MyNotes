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

        try {
            if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                readFileAsync(getIntent().getData(), new FileReadCallback() {
                    @Override
                    public void onSuccess(String content) {
                        startNoteActivityIntent(content);
                    }

                    @Override
                    public void onError(IOException error) {
                        Toast.makeText(ShareActivity.this, "Error reading file", Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
                return;
            } else if (intent.getType() != null && intent.getType().equals("text/plain")) {
                startNoteActivityIntent(handleSendText());
            } else {
                Toast.makeText(this, getString(R.string.notSupportedShare), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error processing shared content", Toast.LENGTH_LONG).show();
        }

        finish();
    }


    /**
     * Method that implements the opening of an intent to create an annotation
     *
     * @param textShare - The text that we fumble in a note
     */
    private void startNoteActivityIntent(String textShare) {
        try {
            if (textShare == null) {
                textShare = "";
            }
            
            // Перевіряємо розмір тексту (500KB для безпеки)
            if (textShare.getBytes().length > 500000) {
                Toast.makeText(this, "Text is too large to share", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            
            startActivity(new Intent(this, NoteActivity.class)
                .putExtra("NewNote", true)
                .putExtra("tagNote", "")
                .putExtra("shareText", textShare));
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Error sharing data", Toast.LENGTH_LONG).show();
            finish();
        }
    }


    /**
     * The method that opens the file from which we want to take the text for sharing
     *
     * @param uri - uri file
     * @param callback - callback for async result
     */
    private void readFileAsync(Uri uri, FileReadCallback callback) {
        // Виконуємо читання файлу в фоновому потоці
        new Thread(() -> {
            try {
                StringBuilder stringBuilder = new StringBuilder();
                try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri)))) {
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line);
                        stringBuilder.append('\n');

                        // Обмежуємо розмір файлу при читанні
                        if (stringBuilder.length() > 500000) {
                            break;
                        }
                    }
                }
                
                // Повертаємо результат в UI потік
                runOnUiThread(() -> callback.onSuccess(stringBuilder.toString()));
                
            } catch (IOException e) {
                runOnUiThread(() -> callback.onError(e));
            }
        }).start();
    }

    /**
     * Callback interface for async file reading
     */
    private interface FileReadCallback {
        void onSuccess(String content);
        void onError(IOException error);
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
