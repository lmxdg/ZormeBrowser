package com.zorme.browser;

import android.webkit.WebView;

/** 一个标签页 = 一个 WebView 实例 + 展示状态 */
public class BrowserTab {

    public final WebView webView;
    public final boolean incognito;

    public String title = "";
    public String url = "";
    public boolean showingHome = true;
    public boolean errorPage = false;

    public BrowserTab(WebView webView, boolean incognito) {
        this.webView = webView;
        this.incognito = incognito;
    }

    public String displayTitle() {
        if (showingHome) return "新标签页";
        if (title != null && !title.trim().isEmpty()) return title;
        if (url != null && !url.isEmpty()) return Utils.titleOf(url);
        return "新标签页";
    }
}
