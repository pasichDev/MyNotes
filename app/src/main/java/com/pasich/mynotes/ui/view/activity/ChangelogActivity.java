package com.pasich.mynotes.ui.view.activity;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.Spanned;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;

import com.google.android.material.transition.platform.MaterialFade;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.databinding.ActivityChangelogBinding;
import com.pasich.mynotes.utils.UpdateChecker;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ChangelogActivity extends BaseActivity {

    public ActivityChangelogBinding binding;
    private static final String CHANGELOG_URL = "https://raw.githubusercontent.com/pasichDev/MyNotes/refs/heads/master/CHANGELOG.md";
    private ExecutorService executor;
    private Markwon markwon;
    
    @Inject
    UpdateChecker updateChecker;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        selectTheme();
        binding = ActivityChangelogBinding.inflate(getLayoutInflater());
        getWindow().setEnterTransition(new MaterialFade().addTarget(binding.activityChangelog));
        getWindow().setAllowEnterTransitionOverlap(true);
        super.onCreate(savedInstanceState);
        setupEdgeToEdgeInsets(binding.getRoot());
        setContentView(binding.getRoot());
        initActivity();
        initListeners();
        loadChangelog();
        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                setEnabled(true);
                finish();
            }
        });
    }

    @Override
    public void initListeners() {
        binding.retryButton.setOnClickListener(v -> retryLoad());

        binding.acknowledgeButton.setOnClickListener(v -> {
            updateChecker.markVersionAsRead();
            binding.acknowledgeButton.setVisibility(View.GONE);
            setResult(RESULT_OK);
            finish();
        });
    }

    private void initActivity() {
        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        executor = Executors.newSingleThreadExecutor();
        markwon = Markwon.builder(this)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(LinkifyPlugin.create())
                .build();
                
        // Перевіряємо, чи потрібно показувати кнопку "Ознайомився"
        updateAcknowledgeButtonVisibility();
    }
    
    /**
     * Оновлює видимість кнопки "Ознайомився" в залежності від того,
     * чи користувач вже ознайомився з поточною версією
     */
    private void updateAcknowledgeButtonVisibility() {
        boolean hasNewVersion = updateChecker.hasNewVersion();
        binding.acknowledgeButton.setVisibility(hasNewVersion ? View.VISIBLE : View.GONE);
    }

    private void loadChangelog() {
        if (!isNetworkAvailable()) {
            showError();
            return;
        }

        if (isFinishing()) {
            return;
        }

        // Защита от повторного создания executor
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor();
        }

        showLoading();

        executor.execute(() -> {
            if (isFinishing()) return;

            try {
                String changelogContent = downloadChangelog();
                String parsedContent = parseChangelogContent(changelogContent);

                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        showContent(parsedContent);
                    }
                });
            } catch (Exception e) {
                Log.e("ChangelogActivity", "Error loading changelog", e);
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        showError();
                    }
                });
            }
        });
    }

    private void showError() {
        binding.progressBar.setVisibility(View.GONE);
        binding.scrollView.setVisibility(View.GONE);
        binding.errorLayout.setVisibility(View.VISIBLE);
    }

    private void showLoading() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.scrollView.setVisibility(View.GONE);
        binding.errorLayout.setVisibility(View.GONE);
    }

    private void showContent(String content) {
        binding.progressBar.setVisibility(View.GONE);
        binding.scrollView.setVisibility(View.VISIBLE);

        String currentVersion = updateChecker.getCurrentAppVersion();
        binding.versionText.setText(getString(R.string.current_version, currentVersion));

        updateAcknowledgeButtonVisibility();

        // Рендеримо Markdown у фоновому потоці
        executor.execute(() -> {
            // Рендеримо Markdown у фоновому потоці
            Spanned markdown = markwon.toMarkdown(content);

            runOnUiThread(() -> {
                if (!isFinishing()) {
                    // Каст до Spanned
                    markwon.setParsedMarkdown(binding.changelogText, markdown);
                }
            });
        });

    }


    private String downloadChangelog() throws IOException {
        URL url = new URL(CHANGELOG_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "MyNotes-Android-App");

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode);
        }

        try (InputStream inputStream = connection.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            
            String result = content.toString().trim();
            if (result.isEmpty()) {
                throw new IOException("Empty response received");
            }
            
            return result;
        } finally {
            connection.disconnect();
        }
    }
    
    /**
     * Парсить контент changelog, залишаючи тільки те, що після заголовку "# CHANGELOG"
     */
    private String parseChangelogContent(String fullContent) {
        if (fullContent == null || fullContent.isEmpty()) {
            return "";
        }
        
        String[] lines = fullContent.split("\n");
        StringBuilder parsedContent = new StringBuilder();
        boolean foundChangelogHeader = false;

        int maxLines = Math.min(lines.length, 1000);
        for (int i = 0; i < maxLines; i++) {
            String line = lines[i];
            if (!foundChangelogHeader) {
                if (line.trim().equalsIgnoreCase("# CHANGELOG") || 
                    line.trim().equalsIgnoreCase("## CHANGELOG") ||
                    line.contains("CHANGELOG")) {
                    foundChangelogHeader = true;
                }
            } else {
                parsedContent.append(line).append("\n");
                
                // Обмежуємо розмір результату
                if (parsedContent.length() > 50000) {
                    parsedContent.append("\n... (content truncated)");
                    break;
                }
            }
        }
        
        return parsedContent.toString().trim();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }

    public void retryLoad() {
        loadChangelog();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return true;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        shutdownExecutorService();
    }

    private void shutdownExecutorService() {
        if (executor != null && !executor.isShutdown()) {
            try {
                // Отменяем все текущие задачи
                executor.shutdownNow();

                // Ждем завершения максимум 1 секунду
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    Log.w("ChangelogActivity", "ExecutorService did not terminate gracefully");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e("ChangelogActivity", "ExecutorService shutdown interrupted", e);
            } finally {
                executor = null;
            }
        }
    }
}
