package com.pasich.mynotes.base.activity;


import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.os.Build;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.view.BaseView;
import com.pasich.mynotes.utils.constants.SnackBarInfo;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;
import com.pasich.mynotes.utils.themes.ThemesArray;
import com.preference.PowerPreference;

public abstract class BaseActivity extends AppCompatActivity implements BaseView {


    @Override
    public void selectTheme() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        setTheme(PowerPreference.getDefaultFile().getBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_DYNAMIC_COLOR, PreferencesConfig.ARGUMENT_DEFAULT_DYNAMIC_COLOR_VALUE) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? R.style.AppThemeDynamic : getSelectedTheme());

    }

    private int getSelectedTheme() {
        return new ThemesArray().getThemeStyle(PowerPreference.getDefaultFile().getInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME, PreferencesConfig.ARGUMENT_DEFAULT_THEME_VALUE));
    }

    @Override
    public void onInfoSnack(int resID, View view, int typeInfo, int time) {
        Snackbar snackbar = Snackbar.make(view == null ? findViewById(android.R.id.content) : view, getString(resID), time);
        if (typeInfo != SnackBarInfo.Info) {
            TextView snackbarTextView = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
            snackbarTextView.setTypeface(snackbarTextView.getTypeface(), Typeface.BOLD);
        }
        switch (typeInfo) {
            case SnackBarInfo.Info:
                break;
            case SnackBarInfo.Success:
                snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.successColorBackground));
                snackbar.setActionTextColor(ContextCompat.getColor(this, R.color.successTextOnColor));
                break;
            case SnackBarInfo.Error:
                snackbar.setBackgroundTint(MaterialColors.getColor(this, R.attr.colorError, Color.DKGRAY));
                snackbar.setActionTextColor(MaterialColors.getColor(this, R.attr.colorOnError, Color.GRAY));
                break;
            default:
        }


        snackbar.show();
    }

    @Override
    public boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
    }

}