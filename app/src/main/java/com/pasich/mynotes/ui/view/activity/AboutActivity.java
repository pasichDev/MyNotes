package com.pasich.mynotes.ui.view.activity;

import static com.pasich.mynotes.utils.constants.ContactLink.LINK_APP_SITE;
import static com.pasich.mynotes.utils.constants.ContactLink.LINK_PRIVACY_POLICY;
import static com.pasich.mynotes.utils.constants.ContactLink.LINK_TELEGRAM_DEVELOP;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.transition.platform.MaterialFade;
import com.pasich.mynotes.BuildConfig;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.databinding.ActivityAboutBinding;
import com.pasich.mynotes.utils.recycler.SpacesItemDecoration;

import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class AboutActivity extends BaseActivity {

    public ActivityAboutBinding binding;

    @Inject
    public LinearLayoutManager mLinearLayoutManager;
    @Named("NotesItemSpaceDecoration")
    @Inject
    public SpacesItemDecoration itemDecorationNotes;
    @Inject
    boolean isPlayMarketInstall;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        selectTheme();
        binding = ActivityAboutBinding.inflate(getLayoutInflater());
        getWindow().setEnterTransition(new MaterialFade().addTarget(binding.activityAbout));
        getWindow().setAllowEnterTransitionOverlap(true);
        super.onCreate(savedInstanceState);
        setContentView(binding.getRoot());
        binding.setActivity(this);
        initActivity();
        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
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
    public void initListeners() {

    }

    private void initActivity() {
        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        binding.versionApp.setText(getString(R.string.versionAndCodeApp, BuildConfig.VERSION_NAME));
        startLoadingProducts();
    }

    public void sendEmail() {
        Intent intent = new Intent(Intent.ACTION_SENDTO).setData(Uri.parse("mailto:pasichDev@outlook.com"));
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }

    }

    public void sendTelegram() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(LINK_TELEGRAM_DEVELOP)));
    }
    public void reddit() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.reddit.com/r/MyNotes_/")));
    }

    public void sendKoFi() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/pasichdev")));
    }

    public void shareApp() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(LINK_APP_SITE)));
    }

    public void policyOpen() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(LINK_PRIVACY_POLICY)));
    }

    public void openRatingGooglePlay() {
        final Intent rateAppIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName()));
        if (!getPackageManager().queryIntentActivities(rateAppIntent, 0).isEmpty()) {
            startActivity(rateAppIntent);
        } else {
            Toast.makeText(this, getString(R.string.notFoundPlayMarket), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_activity_toolbar, menu);
        return true;
    }

    @Override
    public void onBackPressed() {
        finishActivity();
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