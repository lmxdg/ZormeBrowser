package com.zorme.browser;

import android.graphics.Bitmap;
import android.webkit.WebView;

/** 一个标签页 = 一个 WebView 实例 + 展示状态 */
public class BrowserTab {

    public final WebView webView;
    public final boolean incognito;

    public String title = "";
    public String url = "";
    public boolean showingHome = true;
    public boolean errorPage = false;
    /** 标签管理界面显示的页面截图（视口内容） */
    public Bitmap preview;

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

    /** 回收预览位图，防止内存泄漏 */
    public void recyclePreview() {
        if (preview != null && !preview.isRecycled()) {
            preview.recycle();
        }
        preview = null;
    }
}
