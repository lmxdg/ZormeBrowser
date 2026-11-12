package com.zorme.browser;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

/**
 * 轻量广告拦截：按域名后缀匹配。规则来自 assets/ad_hosts.txt，
 * 读取失败时退回到内置核心列表。
 */
public class AdBlocker {

    private static AdBlocker instance;

    private final Set<String> hosts = new HashSet<>();
    private final Set<String> whitelist = new HashSet<>();

    public static synchronized AdBlocker get(Context c) {
        if (instance == null) instance = new AdBlocker(c.getApplicationContext());
        return instance;
    }

    /** 重新加载规则（切换拦截开关后调用） */
    public static synchronized void reload(Context c) {
        instance = null;
    }

    private AdBlocker(Context c) {
        load(c);
    }

    private void load(Context c) {
        try {
            AssetManager am = c.getAssets();
            BufferedReader br = new BufferedReader(new InputStreamReader(am.open("ad_hosts.txt"), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("@@")) whitelist.add(line.substring(2).toLowerCase());
                else hosts.add(line.toLowerCase());
            }
            br.close();
        } catch (Exception ignored) {
        }
        if (hosts.isEmpty()) {
            String[] core = {
                    "doubleclick.net", "googlesyndication.com", "googleadservices.com",
                    "google-analytics.com", "googletagmanager.com", "adservice.google.com",
                    "umeng.com", "cnzz.com", "hm.baidu.com", "pos.baidu.com", "cpro.baidu.com"
            };
            for (String s : core) hosts.add(s);
        }
    }

    public boolean isBlocked(String url) {
        if (url == null || hosts.isEmpty()) return false;
        String host = null;
        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                host = new URI(url).getHost();
            }
        } catch (Exception ignored) {
        }
        if (host == null) return false;
        host = host.toLowerCase();
        for (String w : whitelist) {
            if (host.equals(w) || host.endsWith("." + w)) return false;
        }
        String[] parts = host.split("\\.");
        for (int i = 0; i < parts.length - 1; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < parts.length; j++) {
                if (j > i) sb.append('.');
                sb.append(parts[j]);
            }
            if (hosts.contains(sb.toString())) return true;
        }
        return false;
    }
}
