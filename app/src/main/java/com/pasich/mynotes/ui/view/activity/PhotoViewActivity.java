package com.pasich.mynotes.ui.view.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.databinding.ActivityImageViewerBinding;
import com.pasich.mynotes.extendedEditor.attach.AttachmentStorage;
import com.pasich.mynotes.utils.SafeImageLoader;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fullscreen viewer for image attachments stored in internal storage.
 * Accepts the same URL string as stored in Editor.js/attachments JSON
 */
public class PhotoViewActivity extends BaseActivity {

    public static final String EXTRA_URI = "extra_image_uri";
    private static final String TAG = "PhotoViewActivity";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ActivityImageViewerBinding binding;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityImageViewerBinding.inflate(getLayoutInflater());
        setupEdgeToEdgeInsets(binding.getRoot());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        String rawUrl = getIntent().getStringExtra(EXTRA_URI);
        File file = AttachmentStorage.resolve(this, rawUrl);

        if (file == null || !file.exists() || rawUrl == null || rawUrl.trim().isEmpty()) {
            errorViewImage();
            finish();
            return;
        }

        loadImage(file);

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_photo_view, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.shareMenu) {
            String rawUrl = getIntent().getStringExtra(EXTRA_URI);
            File file = AttachmentStorage.resolve(this, rawUrl);
            if (file != null && file.exists()) {
                shareImage(file);
            } else {
                Toast.makeText(this, getString(R.string.openImageError), Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    private void shareImage(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    file
            );

            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("image/*");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(share, getString(R.string.share)));

        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.shareError), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadImage(File file) {
        executor.execute(() -> {
            try {
                Bitmap bmp = SafeImageLoader.load(
                        this,
                        file,
                        1080, 1920
                );

                if (bmp == null) {
                    runOnUiThread(this::errorViewImage
                    );
                    return;
                }

                runOnUiThread(() -> binding.photoView.setImageBitmap(bmp));

            } catch (Exception ex) {
                Log.e(TAG, "Error loading bitmap", ex);
                runOnUiThread(this::errorViewImage);
            }
        });
    }

    private void errorViewImage() {
        Toast.makeText(this, getString(R.string.openImageError), Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    @Override
    public void initListeners() {
    }
}
