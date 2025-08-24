package com.pasich.mynotes;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.pasich.mynotes.ui.view.activity.MainActivity;


public class RoutingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
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