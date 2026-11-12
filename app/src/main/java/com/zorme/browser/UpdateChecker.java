package com.zorme.browser;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 检测更新：从 GitHub Releases 拉取最新版本号，与本地 BuildConfig.VERSION_NAME 对比。
 * 回调统一回主线程。
 */
public class UpdateChecker {

    public static final String REPO_OWNER = "lmxdg";
    public static final String REPO_NAME = "ZormeBrowser";
    public static final String RELEASES_URL =
            "https://github.com/" + REPO_OWNER + "/" + REPO_NAME + "/releases/latest";
    public static final String API_URL =
            "https://api.github.com/repos/" + REPO_OWNER + "/" + REPO_NAME + "/releases/latest";

    /** ok：请求是否成功；hasUpdate：远端是否比本地新 */
    public interface Callback {
        void onResult(boolean ok, boolean hasUpdate, String latestVersion,
                      String notes, String apkUrl, String releaseUrl);
    }

    public static void check(final Callback cb) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            boolean ok = false;
            String latest = null, notes = null, apkUrl = null, releaseUrl = null;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(API_URL).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "ZormeBrowser/" + BuildConfig.VERSION_NAME);
                if (conn.getResponseCode() == 200) {
                    String body = readAll(conn.getInputStream());
                    JSONObject obj = new JSONObject(body);
                    latest = normalize(obj.optString("tag_name", ""));
                    notes = obj.optString("body", "");
                    releaseUrl = obj.optString("html_url", RELEASES_URL);
                    JSONArray assets = obj.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject a = assets.optJSONObject(i);
                            if (a == null) continue;
                            String name = a.optString("name", "");
                            if (name.toLowerCase().endsWith(".apk")) {
                                apkUrl = a.optString("browser_download_url", null);
                                break;
                            }
                        }
                    }
                    ok = latest != null && !latest.isEmpty();
                }
                conn.disconnect();
            } catch (Exception ignored) {
            }

            final boolean fOk = ok;
            final boolean hasUpdate = fOk && compareVersion(latest, BuildConfig.VERSION_NAME) > 0;
            final String fLatest = latest, fNotes = notes, fApk = apkUrl, fRel = releaseUrl;
            main.post(() -> cb.onResult(fOk, hasUpdate, fLatest, fNotes, fApk, fRel));
        }, "zorme-update-check").start();
    }

    /** 去掉 v 前缀与首尾空白 */
    public static String normalize(String tag) {
        if (tag == null) return "";
        tag = tag.trim();
        if (tag.startsWith("v") || tag.startsWith("V")) tag = tag.substring(1);
        return tag.trim();
    }

    /** 语义化版本比较：>0 表示 a 更新，<0 表示 b 更新，0 相同 */
    public static int compareVersion(String a, String b) {
        int[] pa = parse(a);
        int[] pb = parse(b);
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int x = i < pa.length ? pa[i] : 0;
            int y = i < pb.length ? pb[i] : 0;
            if (x != y) return x - y;
        }
        return 0;
    }

    private static int[] parse(String v) {
        if (v == null || v.isEmpty()) return new int[]{0};
        String[] parts = v.split("[._-]");
        int[] r = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            StringBuilder digits = new StringBuilder();
            for (char ch : parts[i].toCharArray()) {
                if (Character.isDigit(ch)) digits.append(ch);
                else break;
            }
            try {
                r[i] = Integer.parseInt(digits.length() == 0 ? "0" : digits.toString());
            } catch (Exception e) {
                r[i] = 0;
            }
        }
        return r;
    }

    private static String readAll(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }
}
