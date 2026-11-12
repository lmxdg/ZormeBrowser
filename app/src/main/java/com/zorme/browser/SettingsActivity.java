package com.zorme.browser;

import android.app.DownloadManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

/** 设置页：外观、浏览、隐私、关于 */
public class SettingsActivity extends AppCompatActivity {

    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        container = findViewById(R.id.settings_container);
        findViewById(R.id.settings_back).setOnClickListener(v -> finish());
        build();
    }

    private void build() {
        container.removeAllViews();

        addSection("外观");
        addText(getString(R.string.setting_theme), "深色 / 浅色 / 跟随系统", themeLabel(),
                this::chooseTheme);
        addText(getString(R.string.setting_text_size), "调整网页文字大小", Prefs.textZoom(this) + "%",
                this::chooseTextZoom);

        addSection("浏览");
        addText(getString(R.string.setting_search_engine), "地址栏搜索时使用", SearchEngine.get(this).name,
                this::chooseEngine);
        addText(getString(R.string.setting_homepage), "主页按钮和启动页指向的地址",
                homepageLabel(), this::editHomepage);
        addSwitch(getString(R.string.setting_open_home), "每次启动浏览器直接进入主页",
                Prefs.openHome(this), v -> {
                    Prefs.setOpenHome(this, v);
                    Utils.toast(this, v ? "启动时将打开主页" : "启动时将恢复上次的标签页");
                });
        addSwitch(getString(R.string.setting_night), getString(R.string.setting_night_desc),
                Prefs.night(this), v -> {
                    Prefs.setNight(this, v);
                    applyWeb();
                });
        addSwitch(getString(R.string.setting_adblock), getString(R.string.setting_adblock_desc),
                Prefs.adblock(this), v -> {
                    Prefs.setAdblock(this, v);
                    applyWeb();
                    AdBlocker.reload(this);
                });
        addSwitch(getString(R.string.setting_no_image), getString(R.string.setting_no_image_desc),
                Prefs.noImage(this), v -> {
                    Prefs.setNoImage(this, v);
                    applyWeb();
                });
        addSwitch(getString(R.string.setting_desktop), getString(R.string.setting_desktop_desc),
                Prefs.desktop(this), v -> {
                    Prefs.setDesktop(this, v);
                    applyWeb();
                });
        addText(getString(R.string.setting_ua), "网站识别到的浏览器标识", uaLabel(), this::chooseUa);
        addSwitch(getString(R.string.setting_js), getString(R.string.setting_js_desc),
                Prefs.javaScript(this), v -> {
                    Prefs.setJavaScript(this, v);
                    applyWeb();
                });
        addSwitch(getString(R.string.setting_gesture), getString(R.string.setting_gesture_desc),
                Prefs.gesture(this), v -> Prefs.setGesture(this, v));
        addSwitch("记录浏览历史", "关闭后不再保存新的历史记录",
                Prefs.recordHistory(this), v -> Prefs.setRecordHistory(this, v));

        addSection(getString(R.string.setting_privacy));
        addText(getString(R.string.setting_clear_cache), "清除网页缓存文件（不影响书签）", "",
                () -> confirmClear(0));
        addText(getString(R.string.setting_clear_cookies), "退出所有网站的登录状态", "",
                () -> confirmClear(1));
        addText(getString(R.string.setting_clear_history), "删除全部浏览记录", "",
                () -> confirmClear(2));
        addText(getString(R.string.setting_clear_all), "缓存、Cookie、本地存储与历史记录", "",
                () -> confirmClear(3));

        addSection(getString(R.string.setting_about));
        addText(getString(R.string.check_update), "拉取 GitHub 最新发布版本对比", versionName(),
                this::checkUpdate);
        addText(getString(R.string.about), getString(R.string.setting_about_desc), "",
                this::showAbout);

        TextView footer = new TextView(this);
        footer.setText("Zorme 浏览器 · 基于 Android WebView\n轻量、干净、不打扰");
        footer.setTextSize(10);
        footer.setTextColor(getColor(R.color.text_tertiary));
        footer.setGravity(android.view.Gravity.CENTER);
        footer.setLineSpacing(Utils.dp(this, 2), 1f);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = Utils.dp(this, 36);
        flp.bottomMargin = Utils.dp(this, 10);
        container.addView(footer, flp);
    }

    private String themeLabel() {
        switch (Prefs.theme(this)) {
            case Prefs.THEME_LIGHT:
                return getString(R.string.setting_theme_light);
            case Prefs.THEME_SYSTEM:
                return getString(R.string.setting_theme_system);
            case Prefs.THEME_DARK:
            default:
                return getString(R.string.setting_theme_dark);
        }
    }

    private String homepageLabel() {
        String h = Prefs.getHomepage(this);
        return h.isEmpty() ? getString(R.string.homepage_default) : Utils.displayUrl(h);
    }

    private void addSection(String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(11);
        tv.setTextColor(getColor(R.color.text_tertiary));
        tv.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Utils.dp(this, 20);
        lp.rightMargin = Utils.dp(this, 20);
        lp.topMargin = Utils.dp(this, 24);
        lp.bottomMargin = Utils.dp(this, 6);
        container.addView(tv, lp);
    }

    private void addDivider() {
        View v = new View(this);
        v.setBackgroundColor(getColor(R.color.divider));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = Utils.dp(this, 20);
        container.addView(v, lp);
    }

    private void addSwitch(String title, String desc, boolean checked, ValueListener listener) {
        View v = LayoutInflater.from(this).inflate(R.layout.item_setting_switch, container, false);
        ((TextView) v.findViewById(R.id.setting_title)).setText(title);
        ((TextView) v.findViewById(R.id.setting_desc)).setText(desc);
        ZSwitch sw = v.findViewById(R.id.setting_switch);
        sw.setCheckedSilently(checked);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> listener.onChange(isChecked));
        v.setOnClickListener(x -> sw.toggle());
        container.addView(v);
        addDivider();
    }

    private void addText(String title, String desc, String value, Runnable onClick) {
        View v = LayoutInflater.from(this).inflate(R.layout.item_setting_text, container, false);
        ((TextView) v.findViewById(R.id.setting_title)).setText(title);
        TextView d = v.findViewById(R.id.setting_desc);
        if (desc == null || desc.isEmpty()) {
            d.setVisibility(View.GONE);
        } else {
            d.setText(desc);
        }
        TextView val = v.findViewById(R.id.setting_value);
        if (value == null || value.isEmpty()) {
            val.setVisibility(View.GONE);
        } else {
            val.setText(value);
        }
        v.setOnClickListener(x -> onClick.run());
        container.addView(v);
        addDivider();
    }

    private void chooseTheme() {
        final String[] items = {getString(R.string.setting_theme_dark),
                getString(R.string.setting_theme_light),
                getString(R.string.setting_theme_system)};
        ZDialog.choice(this, getString(R.string.setting_theme), items, Prefs.theme(this), which -> {
            if (which == Prefs.theme(this)) return;
            Prefs.setTheme(this, which);
            int mode;
            switch (which) {
                case Prefs.THEME_LIGHT:
                    mode = AppCompatDelegate.MODE_NIGHT_NO;
                    break;
                case Prefs.THEME_SYSTEM:
                    mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                    break;
                case Prefs.THEME_DARK:
                default:
                    mode = AppCompatDelegate.MODE_NIGHT_YES;
                    break;
            }
            // 立即在整个应用生效
            AppCompatDelegate.setDefaultNightMode(mode);
            build();
            Utils.toast(this, "主题已切换为" + items[which]);
        });
    }

    private void chooseTextZoom() {
        final int[] values = {80, 90, 100, 110, 120, 130, 150};
        final String[] labels = {"小 · 80%", "较小 · 90%", "标准 · 100%", "较大 · 110%",
                "大 · 120%", "很大 · 130%", "特大 · 150%"};
        int checked = 2;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == Prefs.textZoom(this)) checked = i;
        }
        ZDialog.choice(this, "网页字体大小", labels, checked, which -> {
            Prefs.setTextZoom(this, values[which]);
            for (BrowserTab t : TabManager.get().tabs) {
                t.webView.getSettings().setTextZoom(values[which]);
            }
            build();
            Utils.toast(this, "字体大小已设为 " + values[which] + "%");
        });
    }

    private void chooseEngine() {
        final SearchEngine[] all = SearchEngine.ALL;
        String[] names = new String[all.length];
        for (int i = 0; i < all.length; i++) names[i] = all[i].name;
        ZDialog.choice(this, getString(R.string.search_engine_title), names,
                SearchEngine.indexOf(Prefs.getSearchEngine(this)), which -> {
                    Prefs.setSearchEngine(this, all[which].id);
                    build();
                    Utils.toast(this, "搜索引擎已切换为 " + all[which].name);
                });
    }

    private void editHomepage() {
        ZDialog.input(this, getString(R.string.setting_homepage), "留空则使用 Zorme 起始页",
                Prefs.getHomepage(this), getString(R.string.save), text -> {
                    Prefs.setHomepage(this, text == null ? "" : text.trim());
                    build();
                    Utils.toast(this, homepageLabel().isEmpty() ? "已恢复默认主页" : "主页已保存");
                });
    }

    private void applyWeb() {
        for (BrowserTab t : TabManager.get().tabs) {
            WebSettingsHelper.configure(this, t.webView, t.incognito);
        }
        BrowserTab cur = TabManager.get().current();
        if (cur != null && !cur.showingHome) cur.webView.reload();
    }

    private void confirmClear(int what) {
        String[] titles = {"清除缓存", "清除 Cookie", "清除历史记录", "清除全部浏览数据"};
        String[] messages = {
                "将删除网页缓存，不影响书签和登录状态。",
                "将退出所有网站的登录状态。",
                "将删除全部浏览历史，无法恢复。",
                "将清除缓存、Cookie、本地存储和历史记录，书签会保留。"
        };
        ZDialog.confirmDanger(this, titles[what], messages[what], "清除", () -> {
            doClear(what);
            if (what == 0) Utils.toast(this, getString(R.string.setting_done_cache));
            else if (what == 1) Utils.toast(this, getString(R.string.setting_done_cookies));
            else if (what == 2) Utils.toast(this, getString(R.string.setting_done_history));
            else Utils.toast(this, getString(R.string.setting_done_all));
        });
    }

    private void doClear(int what) {
        if (what == 0 || what == 3) {
            for (BrowserTab t : TabManager.get().tabs) {
                t.webView.clearCache(true);
                t.webView.clearFormData();
            }
        }
        if (what == 1 || what == 3) {
            CookieManager cm = CookieManager.getInstance();
            cm.removeAllCookies(null);
            cm.flush();
        }
        if (what == 2 || what == 3) {
            DbHelper.get(this).clearHistory();
        }
        if (what == 3) {
            WebStorage.getInstance().deleteAllData();
        }
    }

    private void showAbout() {
        ZDialog.alert(this, "Zorme 浏览器",
                "版本 " + versionName() + "\n\n"
                        + "基于 Android 系统 WebView 构建的轻量浏览器。\n\n"
                        + "· 多标签浏览与标签恢复\n"
                        + "· 广告与追踪域名拦截\n"
                        + "· 夜间模式 / 无图模式 / 浏览器标识(UA)\n"
                        + "· 阅读模式与页内查找\n"
                        + "· 无痕浏览、书签、历史、下载管理\n"
                        + "· 底部地址栏与边缘滑动导航\n"
                        + "· 应用内检测更新\n"
                        + "· 全部界面元素内置自绘\n\n"
                        + "界面与图标均为 Zorme 原创设计。");
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return BuildConfig.VERSION_NAME;
        }
    }

    /* ---------- 浏览器标识（UA） ---------- */

    private String uaLabel() {
        switch (Prefs.uaMode(this)) {
            case Prefs.UA_DESKTOP:
                return "桌面版 Chrome";
            case Prefs.UA_IPHONE:
                return "iPhone";
            case Prefs.UA_IPAD:
                return "iPad";
            case Prefs.UA_CUSTOM:
                return "自定义";
            case Prefs.UA_DEFAULT:
            default:
                return "默认（安卓）";
        }
    }

    private void chooseUa() {
        final String[] items = {"默认（安卓）", "桌面版 Chrome", "iPhone", "iPad", "自定义…"};
        ZDialog.choice(this, getString(R.string.setting_ua), items, Prefs.uaMode(this), which -> {
            if (which == Prefs.UA_CUSTOM) {
                ZDialog.input(this, "自定义 User-Agent", "输入完整的 UA 字符串，留空恢复默认",
                        Prefs.uaCustom(this), getString(R.string.save), text -> {
                            Prefs.setUaMode(this, Prefs.UA_CUSTOM);
                            Prefs.setUaCustom(this, text == null ? "" : text.trim());
                            if ((text == null || text.trim().isEmpty())) {
                                Prefs.setUaMode(this, Prefs.UA_DEFAULT);
                            }
                            applyWeb();
                            build();
                            Utils.toast(this, "浏览器标识已更新");
                        });
            } else if (which != Prefs.uaMode(this)) {
                Prefs.setUaMode(this, which);
                applyWeb();
                build();
                Utils.toast(this, "浏览器标识已切换为 " + items[which]);
            }
        });
    }

    /* ---------- 检测更新 ---------- */

    private void checkUpdate() {
        ZDialog.toast(this, "正在检查更新…");
        UpdateChecker.check((ok, hasUpdate, latest, notes, apkUrl, releaseUrl) -> {
            if (isFinishing()) return;
            if (!ok) {
                ZDialog.alert(this, getString(R.string.check_update),
                        "检查失败，请确认网络可以访问 github.com 后重试。");
                return;
            }
            if (!hasUpdate) {
                ZDialog.alert(this, getString(R.string.check_update),
                        "当前已是最新版本 " + versionName() + "。");
                return;
            }
            String brief = notes == null ? "" : notes.trim();
            if (brief.length() > 240) brief = brief.substring(0, 240) + "…";
            if (brief.isEmpty()) brief = "已发布新版本，建议更新以获得更好的体验。";
            ZDialog.confirm(this, "发现新版本 " + latest,
                    brief + "\n\n当前版本：" + versionName(),
                    "获取更新", () -> downloadUpdate(latest, apkUrl, releaseUrl));
        });
    }

    private void downloadUpdate(String version, String apkUrl, String releaseUrl) {
        String[] options;
        if (apkUrl != null && !apkUrl.isEmpty()) {
            options = new String[]{"下载安装包", "打开发布页"};
        } else {
            options = new String[]{"打开发布页"};
        }
        ZDialog.options(this, "获取更新 " + version, options, which -> {
            if (options[which].equals("打开发布页")) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)));
                } catch (Exception e) {
                    Utils.toast(this, "无法打开发布页");
                }
            } else {
                startApkDownload(apkUrl, version);
            }
        });
    }

    private void startApkDownload(String apkUrl, String version) {
        try {
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(apkUrl));
            req.setTitle("ZormeBrowser-" + version + ".apk");
            req.setDescription("Zorme 浏览器新版本");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                    "ZormeBrowser-" + version + ".apk");
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(req);
            Utils.toast(this, "已开始下载，完成后在通知栏点击安装");
        } catch (Exception e) {
            Utils.toast(this, "下载失败，请尝试打开发布页手动下载");
        }
    }

    interface ValueListener {
        void onChange(boolean value);
    }
}
