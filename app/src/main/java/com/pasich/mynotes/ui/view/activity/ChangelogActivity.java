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
import com.pasich.mynotes.utils.changelog.ChangelogManager;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.noties.markwon.Markwon;

@AndroidEntryPoint
public class ChangelogActivity extends BaseActivity {

    public ActivityChangelogBinding binding;
    @Inject
    Markwon markwon;
    @Inject
    ChangelogManager changelogManager;
    @Inject
    UpdateChecker updateChecker;
    private ExecutorService executor;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        getWindow().setAllowEnterTransitionOverlap(true);
        super.onCreate(savedInstanceState);
        selectTheme();
        binding = ActivityChangelogBinding.inflate(getLayoutInflater());
        getWindow().setEnterTransition(new MaterialFade().addTarget(binding.activityChangelog));
        setupEdgeToEdgeInsets(binding.getRoot());
        setContentView(binding.getRoot());
        initActivity();
        initListeners();
        loadLocalChangelog();

    }

    @Override
    public void initListeners() {
        binding.acknowledgeButton.setOnClickListener(v -> {
            changelogManager.markChangelogRead();
            finish();
        });

    }

    private void initActivity() {
        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        executor = Executors.newSingleThreadExecutor();
        // Перевіряємо, чи потрібно показувати кнопку "Ознайомився"
        updateAcknowledgeButtonVisibility();
    }

    private void updateAcknowledgeButtonVisibility() {
        boolean show = updateChecker.hasNewVersion();
        binding.acknowledgeButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }


    private void loadLocalChangelog() {
        String content = changelogManager.readRawChangelog();

        if (content.isEmpty()) {
            content = changelogManager.readRawChangelog();
        }

        showContent(content);
    }


    private void showContent(String content) {
        binding.scrollView.setVisibility(View.VISIBLE);

        String currentVersion = updateChecker.getCurrentAppVersion();
        binding.versionText.setText(getString(R.string.current_version, currentVersion));

        updateAcknowledgeButtonVisibility();

        executor.execute(() -> {
            Spanned markdown = markwon.toMarkdown(content);

            runOnUiThread(() -> {
                if (!isFinishing()) {
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
                executor.shutdownNow();
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    Log.w("ChangelogActivity", "ExecutorService did not terminate gracefully");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                executor = null;
            }
        }
    }
}
