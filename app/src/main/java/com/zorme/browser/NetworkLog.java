package com.zorme.browser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 网络日志：内存环形缓冲，记录 WebView 发出的资源请求。
 * shouldInterceptRequest 在后台线程回调，所有方法线程安全；日志不落盘，应用退出即清空。
 */
public final class NetworkLog {

    public static final int MAX_ENTRIES = 500;

    public static class Entry {
        public final long time;
        public final String method;
        public final String url;
        public final boolean mainFrame;
        public final boolean blocked;

        Entry(long time, String method, String url, boolean mainFrame, boolean blocked) {
            this.time = time;
            this.method = method;
            this.url = url;
            this.mainFrame = mainFrame;
            this.blocked = blocked;
        }
    }

    private static final List<Entry> LOGS = Collections.synchronizedList(new ArrayList<Entry>());

    private NetworkLog() {
    }

    public static void record(String method, String url, boolean mainFrame, boolean blocked) {
        if (url == null || url.isEmpty()) return;
        Entry e = new Entry(System.currentTimeMillis(),
                method == null ? "GET" : method, url, mainFrame, blocked);
        synchronized (LOGS) {
            LOGS.add(e);
            while (LOGS.size() > MAX_ENTRIES) {
                LOGS.remove(0);
            }
        }
    }

    /** 最新记录在前 */
    public static List<Entry> snapshot() {
        synchronized (LOGS) {
            List<Entry> copy = new ArrayList<Entry>(LOGS);
            Collections.reverse(copy);
            return copy;
        }
    }

    public static int size() {
        synchronized (LOGS) {
            return LOGS.size();
        }
    }

    public static void clear() {
        synchronized (LOGS) {
            LOGS.clear();
        }
    }
}
