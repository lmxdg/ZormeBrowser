package com.zorme.browser;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.TypedValue;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class Utils {

    public static final String HOME_URL = "zorme://home";

    private static final int[] PALETTE = new int[]{
            0xFF2E7CF6, 0xFFE5484D, 0xFF12A594, 0xFFF5A524,
            0xFF8E4EC6, 0xFFE93D82, 0xFF3E63DD, 0xFF30A46C,
            0xFFE8730C, 0xFF5B5BD6, 0xFF0E7490, 0xFFB45309
    };

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    /** 使用 Zorme 内置浮层提示，不调用系统 Toast */
    public static void toast(Context c, String msg) {
        if (c == null || msg == null || msg.isEmpty()) return;
        Activity a = activityOf(c);
        if (a != null) ZDialog.toast(a, msg);
    }

    private static Activity activityOf(Context c) {
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            Context base = ((ContextWrapper) c).getBaseContext();
            if (base == c) break;
            c = base;
        }
        return c instanceof Activity ? (Activity) c : null;
    }

    public static void copy(Context c, String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("zorme", text));
            toast(c, c.getString(R.string.copied));
        }
    }

    public static boolean isHome(String url) {
        return url == null || url.isEmpty() || url.startsWith(HOME_URL);
    }

    /** 判断输入更像网址还是搜索词 */
    public static boolean looksLikeUrl(String input) {
        if (input == null) return false;
        String s = input.trim();
        if (s.isEmpty() || s.contains(" ")) return false;
        if (s.startsWith("http://") || s.startsWith("https://") || s.startsWith("file:")) return true;
        if (s.startsWith("about:") || s.startsWith("javascript:")) return false;
        int dot = s.lastIndexOf('.');
        if (dot < 0 || dot == s.length() - 1) return false;
        String host = s.contains("/") ? s.substring(0, s.indexOf('/')) : s;
        if (host.contains(":")) host = host.substring(0, host.indexOf(':'));
        dot = host.lastIndexOf('.');
        if (dot <= 0) return false;
        String tld = host.substring(dot + 1);
        return tld.length() >= 2 && tld.matches("[a-zA-Z]{2,10}");
    }

    public static String normalizeUrl(String input) {
        String s = input.trim();
        if (s.startsWith("http://") || s.startsWith("https://") || s.startsWith("file:")
                || s.startsWith("about:") || s.startsWith("data:") || s.startsWith("javascript:")) {
            return s;
        }
        return "https://" + s;
    }

    public static String domainOf(String url) {
        if (url == null) return "";
        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                URI uri = new URI(url);
                String host = uri.getHost();
                if (host == null) return url;
                return host.startsWith("www.") ? host.substring(4) : host;
            }
        } catch (Exception ignored) {
        }
        return url;
    }

    public static String displayUrl(String url) {
        if (url == null) return "";
        String s = url;
        if (s.startsWith("https://")) s = s.substring(8);
        else if (s.startsWith("http://")) s = s.substring(7);
        if (s.startsWith("www.")) s = s.substring(4);
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static String titleOf(String url) {
        String d = domainOf(url);
        if (d.isEmpty()) return "新标签页";
        return d;
    }

    public static int colorFor(String key) {
        if (key == null) key = "";
        int h = 0;
        for (int i = 0; i < key.length(); i++) h = h * 31 + key.charAt(i);
        return PALETTE[Math.abs(h) % PALETTE.length];
    }

    public static String letterOf(String text) {
        if (text == null || text.trim().isEmpty()) return "?";
        String t = text.trim();
        char c = t.charAt(0);
        if (Character.isLetterOrDigit(c)) return String.valueOf(c).toUpperCase(Locale.ROOT);
        return "·";
    }

    public static String formatTime(long time) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(time);
        boolean sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR);
        if (sameDay) {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(time));
        }
        if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) {
            return new SimpleDateFormat("M月d日", Locale.getDefault()).format(new Date(time));
        }
        return new SimpleDateFormat("yyyy/M/d", Locale.getDefault()).format(new Date(time));
    }

    public static String formatSize(long bytes) {
        if (bytes <= 0) return "";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024);
        return String.format(Locale.getDefault(), "%.2f GB", bytes / 1024.0 / 1024 / 1024);
    }
}
