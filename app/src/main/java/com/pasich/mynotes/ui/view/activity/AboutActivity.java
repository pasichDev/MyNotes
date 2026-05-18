package com.pasich.mynotes.ui.view.activity;

import static com.pasich.mynotes.utils.constants.ContactLink.LINK_APP_SITE;
import static com.pasich.mynotes.utils.constants.ContactLink.LINK_GITHUB_REPO;
import static com.pasich.mynotes.utils.constants.ContactLink.LINK_PRIVACY_POLICY;
import static com.pasich.mynotes.utils.constants.ContactLink.SEND_FEEDBACK;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import androidx.activity.OnBackPressedCallback;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.pasich.mynotes.BuildConfig;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.databinding.ActivityAboutBinding;
import com.pasich.mynotes.utils.recycler.SpacesItemDecoration;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Named;

@AndroidEntryPoint
public class AboutActivity extends BaseActivity {

    protected ActivityAboutBinding binding;

    @Inject public LinearLayoutManager mLinearLayoutManager;

    @Named("NotesItemSpaceDecoration")
    @Inject
    public SpacesItemDecoration itemDecorationNotes;

    @Inject boolean isPlayMarketInstall;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selectTheme();
        binding = ActivityAboutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupEdgeToEdgeInsets(binding.getRoot());
        binding.setActivity(this);
        initActivity();
        getOnBackPressedDispatcher()
                .addCallback(
                        new OnBackPressedCallback(true) {
                            @Override
                            public void handleOnBackPressed() {
                                setEnabled(finishActivity());
                            }
                        });
    }

    private void startLoadingProducts() {
        initListeners();
    }

    @Override
    public void initListeners() {}

    @SuppressLint("SetTextI18n")
    private void initActivity() {
        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        binding.versionApp.setText(BuildConfig.VERSION_NAME + " ⦁ " + BuildConfig.GIT_COMMIT_HASH);
        startLoadingProducts();
    }

    public void shareApp() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(LINK_APP_SITE)));
    }

    public void policyOpen() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(LINK_PRIVACY_POLICY)));
    }

    public void listLibraryOpen() {
        startActivity(new Intent(this, LibsActivity.class));
    }

    public void sendFeedback() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SEND_FEEDBACK)));
    }

    public void githubRepo() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(LINK_GITHUB_REPO)));
    }

    public void openChangelog() {
        startActivity(new Intent(this, ChangelogActivity.class));
    }

    public void openSupportDev() {
        startActivity(new Intent(this, SupportActivity.class));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_activity_toolbar, menu);
        return true;
    }

    public void openHelp() {
        startActivity(new Intent(this, HelpActivity.class));
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
}
