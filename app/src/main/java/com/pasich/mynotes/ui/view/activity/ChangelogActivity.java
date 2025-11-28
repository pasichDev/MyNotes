package com.pasich.mynotes.ui.view.activity;

import android.os.Bundle;
import android.text.Spanned;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import com.google.android.material.transition.platform.MaterialFade;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.databinding.ActivityChangelogBinding;
import com.pasich.mynotes.utils.UpdateChecker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;

@AndroidEntryPoint
public class ChangelogActivity extends BaseActivity {

    public ActivityChangelogBinding binding;
    private ExecutorService executor;
    private Markwon markwon;

    @Inject
    UpdateChecker updateChecker;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        getWindow().setEnterTransition(new MaterialFade().addTarget(binding.activityChangelog));
        getWindow().setAllowEnterTransitionOverlap(true);
        super.onCreate(savedInstanceState);
        selectTheme();
        binding = ActivityChangelogBinding.inflate(getLayoutInflater());
        setupEdgeToEdgeInsets(binding.getRoot());
        setContentView(binding.getRoot());
        initActivity();
        initListeners();
        loadLocalChangelog();

    }

    @Override
    public void initListeners() {
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
        markwon = Markwon.builder(this).usePlugin(StrikethroughPlugin.create()).usePlugin(LinkifyPlugin.create()).build();

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

    private void loadLocalChangelog() {
        try (InputStream inputStream = getResources().openRawResource(R.raw.changelog); BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            showContent(content.toString());

        } catch (IOException e) {
            Log.e("ChangelogActivity", "Error reading local changelog", e);
        }
    }


    private void showContent(String content) {
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
