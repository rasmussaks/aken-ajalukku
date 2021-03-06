package com.github.rasmussaks.akenajalukku.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;

import java.util.Locale;

public class LocalizedActivity extends AppCompatActivity {
    private static String language = "en";
    private Locale myLocale = null;

    public static void fixLocale(Context context) {
        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);
        language = prefs.getString("pref_language", "en");
        Locale locale = new Locale(language);

        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.locale = locale;
        Resources resources = context.getResources();
        resources.updateConfiguration(config, resources.getDisplayMetrics());

        if (context instanceof LocalizedActivity) {
            ((LocalizedActivity) context).myLocale = locale;
        }
    }

    public String getLocale() {
        return language;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        fixLocale(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (myLocale != null && !myLocale.getLanguage().equals(Locale.getDefault().getLanguage())) {
            myLocale = null;
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    recreate();
                }
            }, 1);
        }
    }
}
