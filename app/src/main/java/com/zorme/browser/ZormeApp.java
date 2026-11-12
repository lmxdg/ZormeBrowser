package com.zorme.browser;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

public class ZormeApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        int theme = Prefs.theme(this);
        int mode;
        switch (theme) {
            case Prefs.THEME_LIGHT:
                mode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case Prefs.THEME_SYSTEM:
                mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                break;
            case Prefs.THEME_DARK:
            default:
                mode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
        }
        AppCompatDelegate.setDefaultNightMode(mode);

        // 后台预热广告拦截规则，避免首次加载网页时阻塞
        new Thread(() -> AdBlocker.get(ZormeApp.this)).start();
    }
}
