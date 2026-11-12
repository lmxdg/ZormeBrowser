package com.zorme.browser;

/** 地址栏输入建议 */
public class Suggestion {

    public static final int TYPE_SEARCH = 0;
    public static final int TYPE_URL = 1;
    public static final int TYPE_HISTORY = 2;
    public static final int TYPE_BOOKMARK = 3;

    public int type;
    public String title;
    public String subtitle;
    public String url;
    public String tag;

    public Suggestion(int type, String title, String subtitle, String url, String tag) {
        this.type = type;
        this.title = title;
        this.subtitle = subtitle;
        this.url = url;
        this.tag = tag;
    }
}
