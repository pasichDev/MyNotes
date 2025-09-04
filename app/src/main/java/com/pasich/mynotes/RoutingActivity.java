package com.pasich.mynotes;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.core.splashscreen.SplashScreen;

import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.ui.view.activity.MainActivity;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class RoutingActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        selectTheme();
        
        EdgeToEdge.enable(this);
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        splashScreen.setKeepOnScreenCondition(() -> true);
        startNextActivity();
    }

    @Override
    public void initListeners() {
    }

    private void startNextActivity() {
        startActivity(new Intent(RoutingActivity.this, MainActivity.class));
        finish();
    }

}