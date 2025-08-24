package com.pasich.mynotes.ui.view.activity;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;

import com.google.android.material.transition.platform.MaterialFade;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.databinding.ActivityChangelogBinding;

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

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ChangelogActivity extends BaseActivity {

    public ActivityChangelogBinding binding;
    private static final String CHANGELOG_URL = "https://raw.githubusercontent.com/pasichDev/MyNotes/refs/heads/v30/CHANGELOG.md";
    private ExecutorService executor;
    private Markwon markwon;

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
                setEnabled(finishActivity());
            }
        });
    }

    @Override
    public void initListeners() {
        binding.retryButton.setOnClickListener(v -> {
            Log.d("ChangelogActivity", "Retry button clicked programmatically");
            retryLoad();
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
    }

    private void loadChangelog() {
        Log.d("ChangelogActivity", "loadChangelog() called");
        
        // Check network connectivity first
        if (!isNetworkAvailable()) {
            Log.d("ChangelogActivity", "No network available, showing error layout");
            binding.progressBar.setVisibility(View.GONE);
            binding.scrollView.setVisibility(View.GONE);
            binding.errorLayout.setVisibility(View.VISIBLE);
            return;
        }

        Log.d("ChangelogActivity", "Network available, starting download");
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.scrollView.setVisibility(View.GONE);
        binding.errorLayout.setVisibility(View.GONE);

        // Recreate executor if it's shutdown
        if (executor == null || executor.isShutdown()) {
            Log.d("ChangelogActivity", "Recreating ExecutorService");
            executor = Executors.newSingleThreadExecutor();
        }

        executor.execute(() -> {
            try {
                 String changelogContent = downloadChangelog();
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.scrollView.setVisibility(View.VISIBLE);
                    markwon.setMarkdown(binding.changelogText, changelogContent);
                    Log.d("ChangelogActivity", "UI updated with changelog content");
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

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            boolean isConnected = activeNetworkInfo != null && activeNetworkInfo.isConnected();
            Log.d("ChangelogActivity", "Network available: " + isConnected);
            return isConnected;
        }
        Log.d("ChangelogActivity", "ConnectivityManager is null");
        return false;
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
