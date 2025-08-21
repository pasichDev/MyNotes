package com.pasich.mynotes.ui.view.activity;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;

import com.google.android.material.transition.platform.MaterialFade;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.databinding.ActivityChangelogBinding;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ChangelogActivity extends BaseActivity {

    public ActivityChangelogBinding binding;
    private static final String CHANGELOG_URL = "https://raw.githubusercontent.com/pasichDev/MyNotes/refs/heads/v30/CHANGELOG.md";
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        selectTheme();
        binding = ActivityChangelogBinding.inflate(getLayoutInflater());
        getWindow().setEnterTransition(new MaterialFade().addTarget(binding.activityChangelog));
        getWindow().setAllowEnterTransitionOverlap(true);
        super.onCreate(savedInstanceState);
        setContentView(binding.getRoot());
        initActivity();
        loadChangelog();
        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                setEnabled(finishActivity());
            }
        });
    }

    @Override
    public void initListeners() {
        // No listeners needed
    }

    private void initActivity() {
        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
    }

    private void loadChangelog() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.scrollView.setVisibility(View.GONE);
        binding.errorLayout.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                String changelogContent = downloadChangelog();
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.scrollView.setVisibility(View.VISIBLE);
                    binding.changelogText.setText(formatChangelog(changelogContent));
                });
            } catch (Exception e) {
                Log.e("ChangelogActivity", "Error loading changelog", e);
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.errorLayout.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private String downloadChangelog() throws IOException {
        URL url = new URL(CHANGELOG_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        try (InputStream inputStream = connection.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        } finally {
            connection.disconnect();
        }
    }

    private String formatChangelog(String markdown) {
        // Basic markdown to text formatting
        return markdown
                .replaceAll("^# ", "")
                .replaceAll("^## ", "\n\n")
                .replaceAll("^### ", "\n")
                .replaceAll("\\*\\*(.*?)\\*\\*", "$1")
                .replaceAll("\\*(.*?)\\*", "$1")
                .replaceAll("^- ", "• ")
                .replaceAll("\\n- ", "\n• ")
                .trim();
    }

    public void retryLoad() {
        loadChangelog();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finishActivity();
        }
        return true;
    }

    private boolean finishActivity() {
        supportFinishAfterTransition();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
