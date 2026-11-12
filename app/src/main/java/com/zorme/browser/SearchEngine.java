package com.zorme.browser;

import android.content.Context;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * 内置搜索引擎。搜索词统一编码后拼接。
 */
public class SearchEngine {

    public final String id;
    public final String name;
    public final String searchUrl;
    public final String homeUrl;

    public SearchEngine(String id, String name, String searchUrl, String homeUrl) {
        this.id = id;
        this.name = name;
        this.searchUrl = searchUrl;
        this.homeUrl = homeUrl;
    }

    public static final SearchEngine[] ALL = new SearchEngine[]{
            new SearchEngine("baidu", "百度", "https://www.baidu.com/s?wd=%s", "https://m.baidu.com"),
            new SearchEngine("bing", "必应", "https://cn.bing.com/search?q=%s", "https://cn.bing.com"),
            new SearchEngine("google", "Google", "https://www.google.com/search?q=%s", "https://www.google.com"),
            new SearchEngine("sogou", "搜狗", "https://www.sogou.com/web?query=%s", "https://m.sogou.com"),
            new SearchEngine("so360", "360 搜索", "https://www.so.com/s?q=%s", "https://m.so.com"),
            new SearchEngine("sm", "神马", "https://m.sm.cn/s?q=%s", "https://m.sm.cn"),
            new SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com"),
    };

    public static SearchEngine get(Context c) {
        return byId(Prefs.getSearchEngine(c));
    }

    public static SearchEngine byId(String id) {
        for (SearchEngine e : ALL) {
            if (e.id.equals(id)) return e;
        }
        return ALL[0];
    }

    public String searchUrlFor(String query) {
        try {
            return String.format(searchUrl, URLEncoder.encode(query, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            return homeUrl;
        }
    }

    public static int indexOf(String id) {
        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i].id.equals(id)) return i;
        }
        return 0;
    }
}
