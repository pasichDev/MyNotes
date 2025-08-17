package com.pasich.mynotes;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.core.splashscreen.SplashScreen;

import com.pasich.mynotes.ui.view.activity.MainActivity;


public class RoutingActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        splashScreen.setKeepOnScreenCondition(() -> true);
        startNextActivity();
    }

    private void startNextActivity() {
        startActivity(new Intent(RoutingActivity.this, MainActivity.class));
        finish();
    }

}