package com.zorme.browser;

import java.util.ArrayList;
import java.util.List;

/**
 * 标签页仓库。WebView 实例常驻，切换标签只是把 View 挂到容器里。
 */
public class TabManager {

    private static final TabManager INSTANCE = new TabManager();

    public static TabManager get() {
        return INSTANCE;
    }

    public final List<BrowserTab> tabs = new ArrayList<>();
    public int current = -1;

    private TabManager() {
    }

    public BrowserTab current() {
        if (current < 0 || current >= tabs.size()) return null;
        return tabs.get(current);
    }

    public void add(BrowserTab tab) {
        tabs.add(tab);
        current = tabs.size() - 1;
    }

    public void remove(int index) {
        if (index < 0 || index >= tabs.size()) return;
        BrowserTab t = tabs.remove(index);
        try {
            t.webView.stopLoading();
            t.webView.destroy();
        } catch (Exception ignored) {
        }
        if (tabs.isEmpty()) {
            current = -1;
        } else if (current >= tabs.size()) {
            current = tabs.size() - 1;
        } else if (index < current) {
            current--;
        }
    }

    public void destroyAll() {
        for (BrowserTab t : tabs) {
            try {
                t.webView.stopLoading();
                t.webView.destroy();
            } catch (Exception ignored) {
            }
        }
        tabs.clear();
        current = -1;
    }

    public int size() {
        return tabs.size();
    }
}
