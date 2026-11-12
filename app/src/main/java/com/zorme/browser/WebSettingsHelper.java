package com.zorme.browser;

import android.content.Context;
import android.os.Build;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

/** 统一的 WebView 参数配置，主界面和设置页共用 */
public class WebSettingsHelper {

    public static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    public static final String IPHONE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1";
    public static final String IPAD_UA =
            "Mozilla/5.0 (iPad; CPU OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1";

    /** 根据设置的 UA 模式返回 User-Agent；null 表示使用系统默认 */
    public static String uaFor(Context c) {
        switch (Prefs.uaMode(c)) {
            case Prefs.UA_DESKTOP:
                return DESKTOP_UA;
            case Prefs.UA_IPHONE:
                return IPHONE_UA;
            case Prefs.UA_IPAD:
                return IPAD_UA;
            case Prefs.UA_CUSTOM: {
                String s = Prefs.uaCustom(c);
                return s.isEmpty() ? null : s;
            }
            case Prefs.UA_DEFAULT:
            default:
                return null;
        }
    }

    @SuppressWarnings("deprecation")
    public static void configure(Context c, WebView wv, boolean incognito) {
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(Prefs.javaScript(c));
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setGeolocationEnabled(false);
        s.setSaveFormData(!incognito);
        s.setUserAgentString(uaFor(c));
        s.setTextZoom(Prefs.textZoom(c));
        s.setBlockNetworkImage(Prefs.noImage(c));
        s.setLoadsImagesAutomatically(!Prefs.noImage(c));
        s.setCacheMode(incognito ? WebSettings.LOAD_NO_CACHE : WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            s.setSafeBrowsingEnabled(true);
        }
        applyNight(c, wv);
    }

    @SuppressWarnings("deprecation")
    public static void applyNight(Context c, WebView wv) {
        boolean night = Prefs.night(c);
        WebSettings s = wv.getSettings();
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, night);
                return;
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                WebSettingsCompat.setForceDark(s,
                        night ? WebSettingsCompat.FORCE_DARK_ON : WebSettingsCompat.FORCE_DARK_OFF);
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean supportsNativeDark() {
        return WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)
                || WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK);
    }
}
