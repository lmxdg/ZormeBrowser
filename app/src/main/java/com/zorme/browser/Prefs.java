package com.zorme.browser;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 全局偏好设置。所有开关都通过这里读写，便于各页面保持一致。
 */
public class Prefs {

    private static final String NAME = "zorme_prefs";

    public static final String KEY_SEARCH_ENGINE = "search_engine";
    public static final String KEY_HOMEPAGE = "homepage";
    public static final String KEY_ADBLOCK = "adblock";
    public static final String KEY_NIGHT = "night";
    public static final String KEY_NO_IMAGE = "no_image";
    public static final String KEY_DESKTOP = "desktop";
    public static final String KEY_JS = "javascript";
    public static final String KEY_GESTURE = "gesture";
    public static final String KEY_RESTORE = "restore_tabs";
    public static final String KEY_THEME = "theme";
    public static final String KEY_RECORD_HISTORY = "record_history";
    public static final String KEY_TEXT_ZOOM = "text_zoom";
    public static final String KEY_OPEN_HOME = "open_home";
    public static final String KEY_AGREED = "agreement_agreed";
    public static final String KEY_UPDATE_MIRROR = "update_mirror";
    public static final String KEY_NET_LOG = "net_log";

    public static final int THEME_DARK = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_SYSTEM = 2;

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String getSearchEngine(Context c) {
        return sp(c).getString(KEY_SEARCH_ENGINE, "baidu");
    }

    public static void setSearchEngine(Context c, String id) {
        sp(c).edit().putString(KEY_SEARCH_ENGINE, id).apply();
    }

    /** 空字符串表示使用内置起始页 */
    public static String getHomepage(Context c) {
        return sp(c).getString(KEY_HOMEPAGE, "");
    }

    public static void setHomepage(Context c, String url) {
        sp(c).edit().putString(KEY_HOMEPAGE, url == null ? "" : url.trim()).apply();
    }

    public static boolean adblock(Context c) {
        return sp(c).getBoolean(KEY_ADBLOCK, true);
    }

    public static void setAdblock(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_ADBLOCK, v).apply();
    }

    public static boolean night(Context c) {
        return sp(c).getBoolean(KEY_NIGHT, false);
    }

    public static void setNight(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_NIGHT, v).apply();
    }

    public static boolean noImage(Context c) {
        return sp(c).getBoolean(KEY_NO_IMAGE, false);
    }

    public static void setNoImage(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_NO_IMAGE, v).apply();
    }

    public static boolean desktop(Context c) {
        // 统一以 UA 模式为准，避免桌面开关与 UA 设置不同步
        return uaMode(c) == UA_DESKTOP;
    }

    public static void setDesktop(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_DESKTOP, v).apply();
        // 同步到统一的 UA 模式
        if (v && uaMode(c) != UA_DESKTOP) setUaMode(c, UA_DESKTOP);
        if (!v && uaMode(c) == UA_DESKTOP) setUaMode(c, UA_DEFAULT);
    }

    /* ---------- 浏览器标识（User-Agent） ---------- */

    public static final String KEY_UA_MODE = "ua_mode";
    public static final String KEY_UA_CUSTOM = "ua_custom";

    public static final int UA_DEFAULT = 0;   // 系统默认（安卓）
    public static final int UA_DESKTOP = 1;   // 桌面版 Chrome
    public static final int UA_IPHONE = 2;    // iPhone Safari
    public static final int UA_IPAD = 3;      // iPad Safari
    public static final int UA_CUSTOM = 4;    // 自定义

    public static int uaMode(Context c) {
        // 兼容旧版"桌面版网站"开关：从未选过 UA 模式但开了桌面版时自动迁移
        if (!sp(c).contains(KEY_UA_MODE) && sp(c).getBoolean(KEY_DESKTOP, false)) {
            return UA_DESKTOP;
        }
        return sp(c).getInt(KEY_UA_MODE, UA_DEFAULT);
    }

    public static void setUaMode(Context c, int mode) {
        sp(c).edit().putInt(KEY_UA_MODE, mode).apply();
    }

    public static String uaCustom(Context c) {
        return sp(c).getString(KEY_UA_CUSTOM, "");
    }

    public static void setUaCustom(Context c, String ua) {
        sp(c).edit().putString(KEY_UA_CUSTOM, ua == null ? "" : ua.trim()).apply();
    }

    public static boolean javaScript(Context c) {
        return sp(c).getBoolean(KEY_JS, true);
    }

    public static void setJavaScript(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_JS, v).apply();
    }

    public static boolean gesture(Context c) {
        return sp(c).getBoolean(KEY_GESTURE, true);
    }

    public static void setGesture(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_GESTURE, v).apply();
    }

    public static boolean restoreTabs(Context c) {
        return sp(c).getBoolean(KEY_RESTORE, true);
    }

    public static void setRestoreTabs(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_RESTORE, v).apply();
    }

    /** 每次启动浏览器都直接进入主页（默认开启） */
    public static boolean openHome(Context c) {
        return sp(c).getBoolean(KEY_OPEN_HOME, true);
    }

    public static void setOpenHome(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_OPEN_HOME, v).apply();
    }

    public static boolean recordHistory(Context c) {
        return sp(c).getBoolean(KEY_RECORD_HISTORY, true);
    }

    public static void setRecordHistory(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_RECORD_HISTORY, v).apply();
    }

    public static int theme(Context c) {
        return sp(c).getInt(KEY_THEME, THEME_DARK);
    }

    public static void setTheme(Context c, int mode) {
        sp(c).edit().putInt(KEY_THEME, mode).apply();
    }

    public static int textZoom(Context c) {
        return sp(c).getInt(KEY_TEXT_ZOOM, 100);
    }

    public static void setTextZoom(Context c, int v) {
        sp(c).edit().putInt(KEY_TEXT_ZOOM, v).apply();
    }

    /** 用户协议 / 隐私政策是否已同意 */
    public static boolean agreed(Context c) {
        return sp(c).getBoolean(KEY_AGREED, false);
    }

    public static void setAgreed(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_AGREED, v).apply();
    }

    /** 检测更新下载 APK 时使用的 GitHub 加速站下标（UpdateChecker.MIRROR_*） */
    public static int updateMirror(Context c) {
        return sp(c).getInt(KEY_UPDATE_MIRROR, 0);
    }

    public static void setUpdateMirror(Context c, int v) {
        sp(c).edit().putInt(KEY_UPDATE_MIRROR, v).apply();
    }

    /** 是否记录网络请求日志（仅内存，默认开启） */
    public static boolean netLog(Context c) {
        return sp(c).getBoolean(KEY_NET_LOG, true);
    }

    public static void setNetLog(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_NET_LOG, v).apply();
    }
}
