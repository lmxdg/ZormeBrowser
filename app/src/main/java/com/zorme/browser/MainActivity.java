package com.zorme.browser;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Zorme 浏览器主界面：底部地址栏 + 多标签 WebView。
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_FILE = 1001;
    private static final int REQ_STORAGE = 1002;

    private static final String[][] SHORTCUTS = new String[][]{
            {"百度", "https://m.baidu.com"},
            {"知乎", "https://www.zhihu.com"},
            {"哔哩哔哩", "https://m.bilibili.com"},
            {"微博", "https://m.weibo.cn"},
            {"GitHub", "https://github.com"},
            {"淘宝", "https://m.taobao.com"},
            {"豆瓣", "https://m.douban.com"},
            {"维基百科", "https://zh.m.wikipedia.org"},
    };

    private FrameLayout root, content, webContainer;
    private View homeView;
    private LinearLayout errorView;
    private View progressBar;
    private LinearLayout findBar, bottomBar;
    private ImageView btnBack, btnForward, addressIcon, addressAction, btnMenu;
    private View btnTabs;
    private TextView addressTitle, tabCount, findCount, menuTitle, menuSubtitle, homePrivacy, errorDesc;
    private RecyclerView suggestionList, tabsRecycler;
    private EditText addressInput, findInput;
    private GridLayout menuGrid, shortcutGrid;
    private FrameLayout addressOverlay, tabsOverlay, menuOverlay;
    private LinearLayout menuCard;
    private TextView tabsTitle, menuNewTab, menuNewIncognito;

    private SuggestionAdapter suggestionAdapter;
    private TabAdapter tabAdapter;

    private boolean addressEditing;
    private boolean menuShown;
    private boolean tabsShown;
    private boolean pageLoading;
    private boolean isFullscreen;
    private int appliedTheme;

    private long lastBackPress;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable suggestRunnable;

    private ValueCallback<Uri[]> filePathCallback;
    private String pendingUrl, pendingUa, pendingMime, pendingDisposition;
    private long pendingLength;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private FrameLayout fullscreenContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.bg));

        bindViews();
        setupBottomBar();
        setupHome();
        setupAddressOverlay();
        setupTabsOverlay();
        setupMenu();
        setupFindBar();

        restoreOrCreateTabs(savedInstanceState);
        handleIntent(getIntent());
        updateAllUi();
        appliedTheme = Prefs.theme(this);
    }

    private void bindViews() {
        root = findViewById(R.id.root);
        content = findViewById(R.id.content);
        webContainer = findViewById(R.id.web_container);
        homeView = findViewById(R.id.home_view);
        errorView = findViewById(R.id.error_view);
        errorDesc = findViewById(R.id.error_desc);
        progressBar = findViewById(R.id.progress_bar);
        findBar = findViewById(R.id.find_bar);
        bottomBar = findViewById(R.id.bottom_bar);
        btnBack = findViewById(R.id.btn_back);
        btnForward = findViewById(R.id.btn_forward);
        addressIcon = findViewById(R.id.address_icon);
        addressAction = findViewById(R.id.address_action);
        btnTabs = findViewById(R.id.btn_tabs);
        btnMenu = findViewById(R.id.btn_menu);
        addressTitle = findViewById(R.id.address_title);
        tabCount = findViewById(R.id.tab_count);
        findCount = findViewById(R.id.find_count);
        menuTitle = findViewById(R.id.menu_title);
        menuSubtitle = findViewById(R.id.menu_subtitle);
        homePrivacy = findViewById(R.id.home_privacy);
        suggestionList = findViewById(R.id.suggestion_list);
        tabsRecycler = findViewById(R.id.tabs_recycler);
        addressInput = findViewById(R.id.address_input);
        findInput = findViewById(R.id.find_input);
        menuGrid = findViewById(R.id.menu_grid);
        shortcutGrid = findViewById(R.id.shortcut_grid);
        addressOverlay = findViewById(R.id.address_overlay);
        tabsOverlay = findViewById(R.id.tabs_overlay);
        menuOverlay = findViewById(R.id.menu_overlay);
        menuCard = findViewById(R.id.menu_card);
        tabsTitle = findViewById(R.id.tabs_title);
        menuNewTab = findViewById(R.id.menu_new_tab);
        menuNewIncognito = findViewById(R.id.menu_new_incognito);
    }

    // ------------------------------------------------------------------
    // 初始化
    // ------------------------------------------------------------------

    private void setupBottomBar() {
        btnBack.setOnClickListener(v -> {
            BrowserTab t = current();
            if (t == null || t.showingHome) return;
            if (t.webView.canGoBack()) t.webView.goBack();
        });
        btnForward.setOnClickListener(v -> {
            BrowserTab t = current();
            if (t == null || t.showingHome) return;
            if (t.webView.canGoForward()) t.webView.goForward();
        });
        findViewById(R.id.address_pill).setOnClickListener(v -> openAddressEditor());
        findViewById(R.id.address_pill).setOnLongClickListener(v -> {
            showPageActions();
            return true;
        });
        addressAction.setOnClickListener(v -> {
            BrowserTab t = current();
            if (t == null) return;
            if (pageLoading) t.webView.stopLoading();
            else t.webView.reload();
        });
        btnTabs.setOnClickListener(v -> showTabs());
        btnMenu.setOnClickListener(v -> showMenu());
    }

    private void setupHome() {
        findViewById(R.id.home_search).setOnClickListener(v -> openAddressEditor());
        findViewById(R.id.error_retry).setOnClickListener(v -> {
            BrowserTab t = current();
            if (t != null && t.url != null) {
                errorView.setVisibility(View.GONE);
                t.webView.loadUrl(t.url);
            }
        });
        buildShortcuts();
    }

    private void buildShortcuts() {
        shortcutGrid.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String[] site : SHORTCUTS) {
            View item = inflater.inflate(R.layout.item_shortcut, shortcutGrid, false);
            TextView letter = item.findViewById(R.id.shortcut_letter);
            TextView name = item.findViewById(R.id.shortcut_name);
            letter.setText(Utils.letterOf(site[0]));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Utils.colorFor(site[0]));
            bg.setCornerRadius(Utils.dp(this, 16));
            letter.setBackground(bg);
            name.setText(site[0]);
            item.setOnClickListener(v -> {
                BrowserTab t = current();
                if (t == null) t = createTab(false, site[1]);
                else loadInTab(t, site[1]);
            });
            shortcutGrid.addView(item);
        }
    }

    private void setupAddressOverlay() {
        suggestionList.setLayoutManager(new LinearLayoutManager(this));
        suggestionAdapter = new SuggestionAdapter(s -> {
            hideAddressEditor();
            loadUrlOrSearchInput(s.url, true);
        });
        suggestionList.setAdapter(suggestionAdapter);

        findViewById(R.id.address_cancel).setOnClickListener(v -> hideAddressEditor());
        addressInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_SEARCH) {
                commitAddress();
                return true;
            }
            return false;
        });
        addressInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (suggestRunnable != null) handler.removeCallbacks(suggestRunnable);
                String q = s.toString();
                suggestRunnable = () -> refreshSuggestions(q);
                handler.postDelayed(suggestRunnable, 120);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
    }

    private void setupTabsOverlay() {
        tabAdapter = new TabAdapter(TabManager.get().tabs, new TabAdapter.Listener() {
            @Override
            public void onSelect(int index) {
                selectTab(index);
            }

            @Override
            public void onClose(int index) {
                closeTab(index);
            }
        });
        tabsRecycler.setLayoutManager(new GridLayoutManager(this, 2));
        tabsRecycler.setAdapter(tabAdapter);
        findViewById(R.id.tabs_close).setOnClickListener(v -> hideTabs());
        findViewById(R.id.tabs_new).setOnClickListener(v -> {
            createTab(false, null);
            hideTabs();
        });
        findViewById(R.id.tabs_new_incognito).setOnClickListener(v -> {
            createTab(true, null);
            hideTabs();
        });
    }

    private void setupMenu() {
        menuOverlay.setOnClickListener(v -> hideMenu());
        menuCard.setClickable(true);
        findViewById(R.id.menu_close).setOnClickListener(v -> hideMenu());
        menuNewTab.setOnClickListener(v -> {
            createTab(false, null);
            hideMenu();
        });
        menuNewIncognito.setOnClickListener(v -> {
            createTab(true, null);
            hideMenu();
        });
    }

    private void setupFindBar() {
        findInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                BrowserTab t = current();
                if (t == null) return;
                String q = s.toString();
                if (q.isEmpty()) {
                    t.webView.clearMatches();
                    findCount.setText("");
                } else {
                    t.webView.findAllAsync(q);
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
        findInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO) {
                BrowserTab t = current();
                if (t != null) t.webView.findNext(true);
                return true;
            }
            return false;
        });
        findViewById(R.id.find_prev).setOnClickListener(v -> {
            BrowserTab t = current();
            if (t != null) t.webView.findNext(false);
        });
        findViewById(R.id.find_next).setOnClickListener(v -> {
            BrowserTab t = current();
            if (t != null) t.webView.findNext(true);
        });
        findViewById(R.id.find_close).setOnClickListener(v -> hideFind());
    }

    // ------------------------------------------------------------------
    // 标签页
    // ------------------------------------------------------------------

    private BrowserTab current() {
        return TabManager.get().current();
    }

    private BrowserTab createTab(boolean incognito, String url) {
        BrowserTab tab = createTabInternal(incognito, url);
        attachCurrent();
        if (url == null) showHome(tab);
        return tab;
    }

    private BrowserTab createTabInternal(boolean incognito, String url) {
        ZWebView wv = new ZWebView(this);
        wv.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        wv.setBackgroundColor(ContextCompat.getColor(this, R.color.bg));
        configureWebView(wv, incognito);
        wv.setWebViewClient(new ZClient());
        wv.setWebChromeClient(new ZChrome());
        wv.addJavascriptInterface(new Bridge(), "Zorme");
        wv.setFindListener((total, active, done) -> {
            if (total == 0) findCount.setText(R.string.find_no_result);
            else findCount.setText(getString(R.string.find_count_fmt, active + 1, total));
        });
        wv.setOnLongClickListener(v -> handleLongPress((ZWebView) v));
        wv.setDownloadListener((downloadUrl, userAgent, contentDisposition, mimeType, contentLength) ->
                requestDownload(downloadUrl, userAgent, contentDisposition, mimeType, contentLength));
        wv.setEdgeSwipeListener(forward -> {
            if (!Prefs.gesture(this)) return;
            BrowserTab t = current();
            if (t == null || t.showingHome) return;
            if (forward) {
                if (t.webView.canGoForward()) t.webView.goForward();
            } else {
                if (t.webView.canGoBack()) t.webView.goBack();
            }
        });

        BrowserTab tab = new BrowserTab(wv, incognito);
        TabManager.get().add(tab);
        if (url != null && !url.isEmpty()) {
            tab.showingHome = false;
            tab.url = url;
            wv.loadUrl(url);
        } else {
            wv.loadUrl("about:blank");
        }
        return tab;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(WebView wv, boolean incognito) {
        WebSettingsHelper.configure(this, wv, incognito);
        wv.setVerticalScrollBarEnabled(false);
        wv.setHorizontalScrollBarEnabled(false);
    }

    private void applyNightMode(WebView wv) {
        WebSettingsHelper.applyNight(this, wv);
    }    private boolean supportsNativeDark() {
        return WebSettingsHelper.supportsNativeDark();
    }

    private void attachCurrent() {
        BrowserTab t = current();
        webContainer.removeAllViews();
        if (t != null) {
            if (t.webView.getParent() instanceof ViewGroup) {
                ((ViewGroup) t.webView.getParent()).removeView(t.webView);
            }
            webContainer.addView(t.webView);
        }
        pageLoading = false;
        if (errorView != null) errorView.setVisibility(View.GONE);
        updateAllUi();
    }

    private void selectTab(int index) {
        TabManager tm = TabManager.get();
        if (index < 0 || index >= tm.tabs.size()) return;
        tm.current = index;
        attachCurrent();
        hideTabs();
    }

    private void closeTab(int index) {
        TabManager tm = TabManager.get();
        if (index < 0 || index >= tm.tabs.size()) return;
        BrowserTab t = tm.tabs.get(index);
        webContainer.removeView(t.webView);
        if (t.incognito) {
            try {
                t.webView.clearCache(true);
                t.webView.clearHistory();
                WebStorage.getInstance().deleteAllData();
            } catch (Exception ignored) {
            }
        }
        tm.remove(index);
        if (tm.size() == 0) {
            createTab(false, null);
        } else {
            attachCurrent();
        }
        tabAdapter.notifyDataSetChanged();
        updateTabsTitle();
    }

    private void showHome(BrowserTab tab) {
        if (tab == null) return;
        tab.showingHome = true;
        updateAllUi();
    }

    /** 设置里填写的自定义主页，空串表示使用内置起始页 */
    private String customHomeUrl() {
        String h = Prefs.getHomepage(this);
        return h == null ? "" : h.trim();
    }

    /** 主页按钮：有自定义主页就加载它，否则显示内置起始页 */
    private void goHome(BrowserTab tab) {
        if (tab == null) return;
        String h = customHomeUrl();
        if (h.isEmpty()) {
            showHome(tab);
            return;
        }
        tab.showingHome = false;
        tab.url = Utils.normalizeUrl(h);
        tab.webView.loadUrl(tab.url);
        updateAllUi();
    }

    private void showTabs() {
        hideKeyboard();
        updateTabsTitle();
        tabAdapter.notifyDataSetChanged();
        tabsOverlay.setVisibility(View.VISIBLE);
        tabsShown = true;
        tabsOverlay.setAlpha(0f);
        tabsOverlay.animate().alpha(1f).setDuration(150).start();
    }

    private void hideTabs() {
        tabsShown = false;
        tabsOverlay.setVisibility(View.GONE);
    }

    private void updateTabsTitle() {
        tabsTitle.setText(getString(R.string.tab_title_fmt, TabManager.get().size()));
    }

    // ------------------------------------------------------------------
    // 地址栏
    // ------------------------------------------------------------------

    private void openAddressEditor() {
        hideMenu();
        BrowserTab t = current();
        String text = (t == null || t.showingHome) ? "" : t.url;
        addressOverlay.setVisibility(View.VISIBLE);
        addressEditing = true;
        addressInput.setText(text);
        addressInput.setSelection(addressInput.getText().length());
        addressInput.requestFocus();
        addressOverlay.setAlpha(0f);
        addressOverlay.animate().alpha(1f).setDuration(120).start();
        refreshSuggestions(text);
        addressInput.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(addressInput, InputMethodManager.SHOW_IMPLICIT);
        }, 100);
    }

    private void hideAddressEditor() {
        addressEditing = false;
        addressOverlay.setVisibility(View.GONE);
        hideKeyboard();
        addressInput.clearFocus();
    }

    private void commitAddress() {
        String input = addressInput.getText().toString().trim();
        if (input.isEmpty()) {
            hideAddressEditor();
            return;
        }
        hideAddressEditor();
        BrowserTab t = current();
        if (t == null) createTab(false, null);
        loadUrlOrSearchInput(input, false);
    }

    private void loadUrlOrSearchInput(String input, boolean alreadyUrl) {
        String url;
        if (alreadyUrl || Utils.looksLikeUrl(input)) {
            url = Utils.normalizeUrl(input);
        } else {
            url = SearchEngine.get(this).searchUrlFor(input);
        }
        BrowserTab t = current();
        if (t == null) t = createTab(false, url);
        else loadInTab(t, url);
    }

    private void loadInTab(BrowserTab tab, String url) {
        if (url == null || url.isEmpty()) return;
        if (url.startsWith("javascript:")) return;
        if (handleSpecialUrl(url)) return;
        tab.showingHome = false;
        tab.url = url;
        errorView.setVisibility(View.GONE);
        tab.webView.loadUrl(url);
        if (current() == tab) updateAllUi();
    }

    private boolean handleSpecialUrl(String url) {
        if (url == null) return false;
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file:")
                || url.startsWith("about:") || url.startsWith("data:") || url.startsWith("javascript:")
                || url.startsWith("blob:")) {
            return false;
        }
        // intent:// 或带 #Intent; 尾部的安卓应用链接：解析目标应用，失败时回退到网页自带的 fallback 地址
        if (url.startsWith("intent:") || url.contains("#Intent;")) {
            try {
                Intent i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                // 安全限制：不允许网页通过 intent 指定任意组件
                i.setComponent(null);
                i.setSelector(null);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception e) {
                String fallback = extractFallbackUrl(url);
                if (fallback != null) {
                    loadInTab(current(), fallback);
                }
                // 没有 fallback 时静默忽略：不打断当前浏览，网页自身的延时跳转会接管
            }
            return true;
        }
        // 其他外部 scheme（weixin://、tbopen://、tel:、mailto: 等）：尝试唤起对应应用
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            // 未安装对应应用：静默忽略，避免弹错误打断浏览（网页通常会自己跳转到可访问的地址）
        }
        return true;
    }

    /** 从 intent:// 链接中提取 S.browser_fallback_url 指定的网页回退地址 */
    private String extractFallbackUrl(String intentUrl) {
        try {
            int idx = intentUrl.indexOf("S.browser_fallback_url=");
            if (idx < 0) return null;
            String rest = intentUrl.substring(idx + "S.browser_fallback_url=".length());
            int end = rest.indexOf(';');
            if (end < 0) end = rest.length();
            return android.net.Uri.decode(rest.substring(0, end));
        } catch (Exception e) {
            return null;
        }
    }

    private void refreshSuggestions(String query) {
        List<Suggestion> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        SearchEngine engine = SearchEngine.get(this);
        String q = query == null ? "" : query.trim();
        DbHelper db = DbHelper.get(this);

        if (!q.isEmpty()) {
            if (Utils.looksLikeUrl(q)) {
                list.add(new Suggestion(Suggestion.TYPE_URL, q, "访问网站", Utils.normalizeUrl(q), "打开"));
            } else {
                list.add(new Suggestion(Suggestion.TYPE_SEARCH, "搜索 “" + q + "”", engine.name,
                        engine.searchUrlFor(q), "搜索"));
            }
        }
        for (Record r : db.history(q, q.isEmpty() ? 8 : 6)) {
            if (Utils.isHome(r.url) || !seen.add(r.url)) continue;
            String title = r.title == null || r.title.trim().isEmpty()
                    ? Utils.displayUrl(r.url) : r.title;
            list.add(new Suggestion(Suggestion.TYPE_HISTORY, title, Utils.displayUrl(r.url), r.url, "历史"));
        }
        for (Record r : db.bookmarks(q)) {
            if (!seen.add(r.url)) continue;
            if (list.size() > 12) break;
            String title = r.title == null || r.title.trim().isEmpty()
                    ? Utils.displayUrl(r.url) : r.title;
            list.add(new Suggestion(Suggestion.TYPE_BOOKMARK, title, Utils.displayUrl(r.url), r.url, "书签"));
        }
        suggestionAdapter.submit(list);
    }

    private void updateAddressBar() {
        BrowserTab t = current();
        boolean home = t == null || t.showingHome;
        if (home) {
            addressTitle.setText(R.string.search_hint);
            addressTitle.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary));
            addressIcon.setImageResource(R.drawable.ic_search);
            addressAction.setVisibility(View.GONE);
        } else {
            String url = t.url == null ? "" : t.url;
            addressTitle.setText(Utils.displayUrl(url));
            addressTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            if (url.startsWith("https://")) {
                addressIcon.setImageResource(R.drawable.ic_lock);
            } else {
                addressIcon.setImageResource(R.drawable.ic_globe);
            }
            addressAction.setVisibility(View.VISIBLE);
            addressAction.setImageResource(pageLoading ? R.drawable.ic_close : R.drawable.ic_refresh);
        }
        tabCount.setText(String.valueOf(TabManager.get().size()));
    }

    private void updateAllUi() {
        BrowserTab t = current();
        boolean home = t == null || t.showingHome;
        homeView.setVisibility(home ? View.VISIBLE : View.GONE);
        homePrivacy.setText(t != null && t.incognito
                ? getString(R.string.incognito_on)
                : "广告拦截已开启 · 不追踪你的浏览");
        updateAddressBar();
        btnBack.setAlpha(t != null && !t.showingHome && t.webView.canGoBack() ? 1f : 0.3f);
        btnForward.setAlpha(t != null && !t.showingHome && t.webView.canGoForward() ? 1f : 0.3f);
        tabCount.setText(String.valueOf(TabManager.get().size()));
    }

    private void setPageProgress(int p) {
        if (p >= 100) {
            progressBar.setVisibility(View.GONE);
            return;
        }
        int width = content.getWidth();
        if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;
        ViewGroup.LayoutParams lp = progressBar.getLayoutParams();
        lp.width = Math.max(1, width * p / 100);
        progressBar.setLayoutParams(lp);
        progressBar.setVisibility(View.VISIBLE);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            View focus = getCurrentFocus();
            if (focus != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }

    // ------------------------------------------------------------------
    // 菜单
    // ------------------------------------------------------------------

    private void showMenu() {
        hideKeyboard();
        BrowserTab t = current();
        menuGrid.removeAllViews();
        if (t != null && !t.showingHome) {
            menuTitle.setText(t.displayTitle());
            menuSubtitle.setText((t.url.startsWith("https://") ? "安全连接 · " : "")
                    + Utils.displayUrl(t.url));
        } else {
            menuTitle.setText(R.string.brand);
            menuSubtitle.setText("新标签页");
        }

        boolean bookmarked = t != null && !t.showingHome && DbHelper.get(this).isBookmarked(t.url);
        addMenuTile(R.drawable.ic_refresh, "刷新", () -> {
            hideMenu();
            if (t != null && !t.showingHome) t.webView.reload();
        });
        addMenuTile(R.drawable.ic_home, "主页", () -> {
            hideMenu();
            goHome(current());
        });
        addMenuTile(bookmarked ? R.drawable.ic_star_filled : R.drawable.ic_star,
                bookmarked ? "已收藏" : "书签", () -> {
                    hideMenu();
                    toggleBookmark();
                });
        addMenuTile(R.drawable.ic_history, "历史", () -> {
            hideMenu();
            startActivity(new Intent(this, ListActivity.class).putExtra("mode", ListActivity.MODE_HISTORY));
        });
        addMenuTile(R.drawable.ic_download, "下载", () -> {
            hideMenu();
            startActivity(new Intent(this, ListActivity.class).putExtra("mode", ListActivity.MODE_DOWNLOADS));
        });
        addMenuTile(R.drawable.ic_moon, Prefs.night(this) ? "日间" : "夜间", () -> {
            hideMenu();
            Prefs.setNight(this, !Prefs.night(this));
            applyToAllTabs(true);
            updateAllUi();
            Utils.toast(this, Prefs.night(this) ? "夜间模式已开启" : "夜间模式已关闭");
        });
        addMenuTile(R.drawable.ic_no_image, Prefs.noImage(this) ? "显示图片" : "无图", () -> {
            hideMenu();
            Prefs.setNoImage(this, !Prefs.noImage(this));
            applyToAllTabs(true);
            Utils.toast(this, Prefs.noImage(this) ? "无图模式已开启" : "已恢复图片");
        });
        addMenuTile(R.drawable.ic_desktop, Prefs.desktop(this) ? "手机版" : "桌面版", () -> {
            hideMenu();
            Prefs.setDesktop(this, !Prefs.desktop(this));
            applyToAllTabs(true);
            Utils.toast(this, Prefs.desktop(this) ? "已切换为桌面版网站" : "已切换为手机版网站");
        });
        addMenuTile(R.drawable.ic_search, "页内查找", () -> {
            hideMenu();
            showFind();
        });
        addMenuTile(R.drawable.ic_reader, "阅读模式", () -> {
            hideMenu();
            readerMode();
        });
        addMenuTile(R.drawable.ic_share, "分享", () -> {
            hideMenu();
            sharePage();
        });
        addMenuTile(R.drawable.ic_settings, "设置", () -> {
            hideMenu();
            startActivity(new Intent(this, SettingsActivity.class));
        });

        menuOverlay.setVisibility(View.VISIBLE);
        menuShown = true;
        menuCard.setTranslationY(Utils.dp(this, 40));
        menuCard.setAlpha(0f);
        menuCard.animate().translationY(0).alpha(1f).setDuration(180).start();
    }

    private void hideMenu() {
        menuShown = false;
        menuOverlay.setVisibility(View.GONE);
    }

    private void addMenuTile(int icon, String label, Runnable action) {
        View v = LayoutInflater.from(this).inflate(R.layout.item_menu_tile, menuGrid, false);
        ((ImageView) v.findViewById(R.id.menu_icon)).setImageResource(icon);
        ((TextView) v.findViewById(R.id.menu_label)).setText(label);
        GridLayout.LayoutParams lp = (GridLayout.LayoutParams) v.getLayoutParams();
        lp.width = 0;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
        v.setLayoutParams(lp);
        v.setOnClickListener(x -> action.run());
        menuGrid.addView(v);
    }

    private void applyToAllTabs(boolean reload) {
        for (BrowserTab t : TabManager.get().tabs) {
            configureWebView(t.webView, t.incognito);
            if (reload && !t.showingHome) t.webView.reload();
        }
    }

    // ------------------------------------------------------------------
    // 页面操作
    // ------------------------------------------------------------------

    private void toggleBookmark() {
        BrowserTab t = current();
        if (t == null || t.showingHome || t.url == null || t.url.isEmpty()) {
            Utils.toast(this, "当前没有可收藏的页面");
            return;
        }
        Record r = DbHelper.get(this).findBookmark(t.url);
        if (r != null) {
            DbHelper.get(this).deleteBookmark(r.id);
            Utils.toast(this, getString(R.string.bookmark_removed));
        } else {
            showBookmarkDialog(t.displayTitle(), t.url);
        }
    }

    private void showBookmarkDialog(String title, String url) {
        ZDialog.input(this, getString(R.string.add_bookmark), "书签名称", title,
                getString(R.string.save), name -> {
                    String n = name == null ? "" : name.trim();
                    if (n.isEmpty()) n = Utils.titleOf(url);
                    DbHelper.get(this).addBookmark(n, url);
                    Utils.toast(this, getString(R.string.bookmark_added));
                });
    }

    private void sharePage() {
        BrowserTab t = current();
        if (t == null || t.showingHome) {
            Utils.toast(this, "当前没有可分享的页面");
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, t.displayTitle());
        send.putExtra(Intent.EXTRA_TEXT, t.displayTitle() + "\n" + t.url);
        startActivity(Intent.createChooser(send, getString(R.string.share)));
    }

    private void showPageActions() {
        BrowserTab t = current();
        if (t == null || t.showingHome) {
            Utils.toast(this, "当前没有页面链接");
            return;
        }
        String[] items = new String[]{"复制链接", "添加到桌面", "用其他浏览器打开", "保存离线网页"};
        ZDialog.options(this, "页面操作", items, which -> {
            if (which == 0) {
                Utils.copy(this, t.url);
            } else if (which == 1) {
                addToHome(t.displayTitle(), t.url);
            } else if (which == 2) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(t.url)));
                } catch (Exception e) {
                    Utils.toast(this, "没有找到可用的浏览器");
                }
            } else {
                requestDownload(t.url, t.webView.getSettings().getUserAgentString(),
                        null, "text/html", -1);
            }
        });
    }

    private void addToHome(String title, String url) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Utils.toast(this, "当前系统版本不支持");
            return;
        }
        try {
            ShortcutManager sm = getSystemService(ShortcutManager.class);
            if (sm == null || !sm.isRequestPinShortcutSupported()) {
                Utils.toast(this, "当前桌面不支持添加快捷方式");
                return;
            }
            String shortTitle = title == null ? Utils.titleOf(url) : title;
            if (shortTitle.length() > 10) shortTitle = shortTitle.substring(0, 10);
            ShortcutInfo info = new ShortcutInfo.Builder(this, "zorme_" + Math.abs(url.hashCode()))
                    .setShortLabel(shortTitle)
                    .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
                    .setIntent(new Intent(this, MainActivity.class)
                            .setAction(Intent.ACTION_VIEW)
                            .setData(Uri.parse(url)))
                    .build();
            sm.requestPinShortcut(info, null);
            Utils.toast(this, "已添加到桌面");
        } catch (Exception e) {
            Utils.toast(this, "添加失败");
        }
    }

    private void showFind() {
        BrowserTab t = current();
        if (t == null || t.showingHome) return;
        findBar.setVisibility(View.VISIBLE);
        findInput.setText("");
        findCount.setText("");
        findInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(findInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideFind() {
        findBar.setVisibility(View.GONE);
        BrowserTab t = current();
        if (t != null) t.webView.clearMatches();
        hideKeyboard();
    }

    private void readerMode() {
        BrowserTab t = current();
        if (t == null || t.showingHome) {
            Utils.toast(this, "请先打开一个网页");
            return;
        }
        boolean dark = Prefs.night(this);
        String textColor = dark ? "#EDEDF0" : "#141418";
        String bgColor = dark ? "#0B0B0E" : "#FAFAFC";
        String js = "(function(){try{"
                + "var src=document.querySelector('article')||document.querySelector('main')||"
                + "document.querySelector('[role=\"main\"]')||document.querySelector('.post-content')||"
                + "document.querySelector('#content')||document.body;"
                + "var c=src.cloneNode(true);"
                + "var junk=c.querySelectorAll('script,style,nav,aside,footer,header,form,iframe,noscript,button,input,select,textarea,svg,video,audio');"
                + "for(var i=junk.length-1;i>=0;i--){if(junk[i].parentNode)junk[i].parentNode.removeChild(junk[i]);}"
                + "var title=(document.title||'').replace(/[<>&]/g,'');"
                + "var html='<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>body{margin:0 auto;padding:26px 22px 70px;max-width:720px;background:" + bgColor + ";color:" + textColor + ";"
                + "font:17px/1.9 -apple-system,system-ui,'PingFang SC','Noto Sans SC',sans-serif;word-wrap:break-word}"
                + "h1{font-size:25px;line-height:1.45;margin:0 0 10px;font-weight:700}"
                + "h2,h3{line-height:1.5;margin:28px 0 10px}p{margin:0 0 19px}"
                + "img{max-width:100%;height:auto;border-radius:10px;display:block;margin:16px 0}"
                + "a{color:#4C8DFF;text-decoration:none}blockquote{margin:0 0 20px;padding:4px 0 4px 16px;border-left:3px solid #7A7A88;opacity:.85}"
                + "pre{overflow:auto;background:" + (dark ? "#17171C" : "#F0F0F4") + ";padding:14px;border-radius:10px;font-size:14px}"
                + "figure{margin:16px 0}figcaption{font-size:12px;opacity:.6}hr{border:0;border-top:1px solid " + (dark ? "#2A2A32" : "#E3E3EA") + ";margin:28px 0}"
                + "</style></head><body><h1>'+title+'</h1>'+c.innerHTML+'</body></html>';"
                + "return html;}catch(e){return null;}})()";
        t.webView.evaluateJavascript(js, value -> {
            if (value == null || value.equals("null") || value.length() < 10) {
                Utils.toast(this, "未能提取到正文");
                return;
            }
            try {
                String html = new JSONArray("[" + value + "]").getString(0);
                t.webView.loadDataWithBaseURL(t.url, html, "text/html", "utf-8", t.url);
            } catch (Exception e) {
                Utils.toast(this, "未能提取到正文");
            }
        });
    }

    private boolean handleLongPress(ZWebView wv) {
        WebView.HitTestResult r = wv.getHitTestResult();
        if (r == null) return false;
        int type = r.getType();
        final String url = r.getExtra();
        if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE && url != null) {
            showLinkSheet(url, false);
            return true;
        }
        if (type == WebView.HitTestResult.IMAGE_TYPE && url != null) {
            showLinkSheet(url, true);
            return true;
        }
        if (type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE && url != null) {
            showLinkSheet(url, true);
            return true;
        }
        return false;
    }

    private void showLinkSheet(String url, boolean image) {
        List<String> items = new ArrayList<>();
        items.add("在新标签页打开");
        items.add("在后台打开");
        items.add("复制链接");
        items.add("下载链接");
        if (image) items.add("保存图片");
        ZDialog.options(this, image ? "图片" : "链接", items.toArray(new String[0]), which -> {
            String u = url;
            switch (which) {
                case 0:
                    createTab(false, u);
                    break;
                case 1:
                    openInBackground(u);
                    break;
                case 2:
                    Utils.copy(this, u);
                    break;
                case 3:
                    requestDownload(u, current() == null ? null
                                    : current().webView.getSettings().getUserAgentString(),
                            null, null, -1);
                    break;
                case 4:
                    requestDownload(u, current() == null ? null
                                    : current().webView.getSettings().getUserAgentString(),
                            null, "image/*", -1);
                    break;
                default:
                    break;
            }
        });
    }

    private String jsTitle(String url) {
        BrowserTab t = current();
        String raw = (t != null && !t.showingHome) ? t.url : url;
        String d = Utils.domainOf(raw);
        return d == null || d.isEmpty() ? "网页提示" : d;
    }

    private void openInBackground(String url) {
        int cur = TabManager.get().current;
        BrowserTab t = createTabInternal(false, url);
        TabManager.get().current = cur;
        tabAdapter.notifyDataSetChanged();
        updateAddressBar();
        Utils.toast(this, "已在后台打开");
    }

    // ------------------------------------------------------------------
    // 下载
    // ------------------------------------------------------------------

    private void requestDownload(String url, String ua, String disposition, String mime, long length) {
        if (url == null || url.isEmpty()) return;
        if (url.startsWith("data:")) {
            Utils.toast(this, "不支持下载 data: 链接");
            return;
        }
        if (url.startsWith("blob:")) {
            Utils.toast(this, "暂不支持 blob: 下载，请长按图片另存");
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingUrl = url;
            pendingUa = ua;
            pendingMime = mime;
            pendingDisposition = disposition;
            pendingLength = length;
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        doDownload(url, ua, disposition, mime, length);
    }

    private void doDownload(String url, String ua, String disposition, String mime, long length) {
        try {
            String fileName = URLUtil.guessFileName(url, disposition, mime);
            if (fileName == null || fileName.trim().isEmpty()) {
                fileName = "zorme_" + System.currentTimeMillis();
            }
            java.io.File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            java.io.File f = new java.io.File(dir, fileName);
            if (f.exists()) {
                fileName = System.currentTimeMillis() + "_" + fileName;
            }
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm == null) {
                Utils.toast(this, getString(R.string.download_failed));
                return;
            }
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setMimeType(mime == null ? "application/octet-stream" : mime);
            if (ua != null) req.addRequestHeader("User-Agent", ua);
            req.setTitle(fileName);
            req.setDescription(Utils.domainOf(url));
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            long id = dm.enqueue(req);
            DbHelper.get(this).addDownload(fileName, url, fileName, length, id);
            Utils.toast(this, getString(R.string.download_started) + " · " + fileName);
        } catch (Exception e) {
            Utils.toast(this, getString(R.string.download_failed));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
                    && pendingUrl != null) {
                doDownload(pendingUrl, pendingUa, pendingDisposition, pendingMime, pendingLength);
            } else {
                Utils.toast(this, "没有存储权限，无法保存文件");
            }
            pendingUrl = null;
        }
    }

    // ------------------------------------------------------------------
    // 文件选择
    // ------------------------------------------------------------------

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            if (filePathCallback == null) {
                super.onActivityResult(requestCode, resultCode, data);
                return;
            }
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // ------------------------------------------------------------------
    // 生命周期与返回
    // ------------------------------------------------------------------

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        updateAllUi();
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        // 从桌面图标再次进入：回到主页（原标签保留在标签页中，可随时切回）
        if (Intent.ACTION_MAIN.equals(action) && Prefs.openHome(this)) {
            BrowserTab t = current();
            if (t != null && !t.showingHome) {
                goHome(t);
            }
            return;
        }
        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            String url = intent.getData().toString();
            if (url.startsWith("http")) {
                BrowserTab t = current();
                if (t != null && t.showingHome && TabManager.get().size() == 1) {
                    loadInTab(t, url);
                } else {
                    createTab(false, url);
                }
            }
        }
    }

    private void restoreOrCreateTabs(Bundle state) {
        // 冷启动：始终打开主页（可在设置中关闭）
        if (state == null && Prefs.openHome(this)) {
            String h = customHomeUrl();
            createTab(false, h.isEmpty() ? null : Utils.normalizeUrl(h));
            return;
        }
        // 界面重建（如切换主题）：清掉上一实例遗留的 WebView，再按会话恢复
        if (state != null) {
            TabManager.get().destroyAll();
        }
        boolean restored = false;
        if (Prefs.restoreTabs(this)) {
            List<Session.Item> items = Session.restore(this);
            int count = 0;
            for (Session.Item item : items) {
                BrowserTab t = createTabInternal(false, item.url.isEmpty() ? null : item.url);
                t.title = item.title;
                count++;
            }
            if (count > 0) {
                int cur = Session.savedCurrent(this);
                if (cur >= TabManager.get().size()) cur = TabManager.get().size() - 1;
                if (cur < 0) cur = 0;
                TabManager.get().current = cur;
                attachCurrent();
                restored = true;
            }
        }
        if (!restored) {
            createTab(false, null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Prefs.theme(this) != appliedTheme) {
            appliedTheme = Prefs.theme(this);
            Session.save(this);
            recreate();
            return;
        }
        updateAllUi();
        tabAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Session.save(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!isChangingConfigurations()) {
            Session.save(this);
            TabManager.get().destroyAll();
        }
    }

    @Override
    public void onBackPressed() {
        if (ZDialog.handleBack()) {
            return;
        }
        if (addressEditing) {
            hideAddressEditor();
            return;
        }
        if (menuShown) {
            hideMenu();
            return;
        }
        if (tabsShown) {
            hideTabs();
            return;
        }
        if (isFullscreen) {
            exitFullscreen();
            return;
        }
        if (findBar.getVisibility() == View.VISIBLE) {
            hideFind();
            return;
        }
        BrowserTab t = current();
        if (t != null) {
            if (t.showingHome) {
                String last = t.webView.getUrl();
                if (last != null && !last.equals("about:blank")) {
                    t.showingHome = false;
                    t.url = last;
                    updateAllUi();
                    return;
                }
            } else {
                if (t.webView.canGoBack()) {
                    t.webView.goBack();
                    return;
                }
                showHome(t);
                return;
            }
        }
        long now = System.currentTimeMillis();
        if (now - lastBackPress < 2000) {
            super.onBackPressed();
        } else {
            lastBackPress = now;
            Utils.toast(this, getString(R.string.exit_hint));
        }
    }

    // ------------------------------------------------------------------
    // 全屏视频
    // ------------------------------------------------------------------

    private void enterFullscreen(View view, WebChromeClient.CustomViewCallback callback) {
        if (customView != null) {
            callback.onCustomViewHidden();
            return;
        }
        customView = view;
        customViewCallback = callback;
        FrameLayout decor = (FrameLayout) getWindow().getDecorView();
        fullscreenContainer = new FrameLayout(this);
        fullscreenContainer.setBackgroundColor(Color.BLACK);
        fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        decor.addView(fullscreenContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemBars();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        isFullscreen = true;
    }

    private void exitFullscreen() {
        if (customView == null) return;
        FrameLayout decor = (FrameLayout) getWindow().getDecorView();
        if (fullscreenContainer != null) {
            decor.removeView(fullscreenContainer);
            fullscreenContainer = null;
        }
        customView = null;
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        isFullscreen = false;
    }

    private void hideSystemBars() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isFullscreen) hideSystemBars();
    }

    // ------------------------------------------------------------------
    // JS 桥
    // ------------------------------------------------------------------

    public class Bridge {
        @JavascriptInterface
        public void retry() {
            runOnUiThread(() -> {
                BrowserTab t = current();
                if (t != null && t.url != null) t.webView.loadUrl(t.url);
            });
        }

        @JavascriptInterface
        public void openUrl(String url) {
            runOnUiThread(() -> {
                if (url != null) createTab(false, url);
            });
        }

        @JavascriptInterface
        public void toast(String msg) {
            runOnUiThread(() -> Utils.toast(MainActivity.this, msg));
        }
    }

    // ------------------------------------------------------------------
    // WebView 回调
    // ------------------------------------------------------------------

    private BrowserTab tabOf(WebView view) {
        for (BrowserTab t : TabManager.get().tabs) {
            if (t.webView == view) return t;
        }
        return null;
    }

    private class ZClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            return handleSpecialUrl(url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleSpecialUrl(url);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            try {
                if (Prefs.adblock(MainActivity.this)
                        && AdBlocker.get(MainActivity.this).isBlocked(request.getUrl().toString())) {
                    return new WebResourceResponse("text/plain", "utf-8",
                            new ByteArrayInputStream(new byte[0]));
                }
            } catch (Exception ignored) {
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            BrowserTab tab = tabOf(view);
            if (tab == null) return;
            tab.errorPage = false;
            if (url != null && !url.startsWith("about:")) {
                tab.url = url;
                tab.showingHome = false;
            }
            if (view == (current() == null ? null : current().webView)) {
                errorView.setVisibility(View.GONE);
                pageLoading = true;
                setPageProgress(8);
                updateAddressBar();
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            BrowserTab tab = tabOf(view);
            if (tab == null) return;
            String title = view.getTitle();
            if (title != null) tab.title = title;
            if (url != null && !url.startsWith("about:") && !url.startsWith("data:")) {
                tab.url = url;
            }
            if (!tab.errorPage && !tab.incognito && Prefs.recordHistory(MainActivity.this)
                    && tab.url != null && tab.url.startsWith("http")) {
                DbHelper.get(MainActivity.this).addHistory(tab.title, tab.url);
            }
            if (Prefs.night(MainActivity.this) && !supportsNativeDark()) {
                injectDark(view);
            }
            if (view == (current() == null ? null : current().webView)) {
                pageLoading = false;
                setPageProgress(100);
                updateAllUi();
                tabAdapter.notifyDataSetChanged();
            }
            Session.save(MainActivity.this);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request == null || !request.isForMainFrame()) return;
            String url = request.getUrl() == null ? "" : request.getUrl().toString();
            // 空白页/数据页的加载错误不提示
            if (url.startsWith("about:") || url.startsWith("data:") || url.isEmpty()) return;
            int code = error == null ? android.webkit.WebViewClient.ERROR_UNKNOWN : error.getErrorCode();
            String desc = error == null || error.getDescription() == null
                    ? "" : String.valueOf(error.getDescription());
            // ERR_ABORTED(-3)：导航被新的跳转或页面自身取消，属于正常流程，随后会加载真实页面，
            // 提前报错就会出现"先报错、过几秒又打开"的现象
            if (code == android.webkit.WebViewClient.ERROR_UNSUPPORTED_SCHEME /* -10 */) return;
            if (code == -3 /* net::ERR_ABORTED */) return;
            if (desc.contains("ERR_ABORTED") || desc.contains("ERR_UNKNOWN_URL_SCHEME")) return;
            showErrorPage(view, code <= 0 ? "" : String.valueOf(code), desc);
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
            handler.cancel();
            Utils.toast(MainActivity.this, "此网站的安全证书有问题，已阻止访问");
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            super.doUpdateVisitedHistory(view, url, isReload);
            BrowserTab tab = tabOf(view);
            if (tab != null && tab.showingHome) {
                tab.showingHome = false;
            }
            if (view == (current() == null ? null : current().webView)) updateAllUi();
        }
    }

    private void injectDark(WebView wv) {
        String js = "(function(){if(document.getElementById('zorme-dark'))return;"
                + "var s=document.createElement('style');s.id='zorme-dark';"
                + "s.textContent='html{background:#101014!important;filter:invert(1) hue-rotate(180deg)}"
                + "img,video,picture,canvas,svg,iframe{filter:invert(1) hue-rotate(180deg)!important}';"
                + "document.documentElement.appendChild(s);})();";
        wv.evaluateJavascript(js, null);
    }

    private void showErrorPage(WebView view, String code, String desc) {
        BrowserTab tab = tabOf(view);
        if (tab == null) return;
        tab.errorPage = true;
        if (view != (current() == null ? null : current().webView)) return;
        String url = tab.url == null ? "" : tab.url;
        errorView.setVisibility(View.VISIBLE);
        StringBuilder sb = new StringBuilder(getString(R.string.error_hint));
        sb.append("\n").append(Utils.displayUrl(url));
        if (desc != null && !desc.isEmpty() && code != null && !code.isEmpty()
                && !desc.contains("ERR_" + code)) {
            sb.append("\n").append(desc);
        } else if (code != null && !code.isEmpty()) {
            sb.append("\nERR ").append(code);
        }
        errorDesc.setText(sb.toString());
        pageLoading = false;
        setPageProgress(100);
        updateAddressBar();
    }

    private class ZChrome extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            if (view == (current() == null ? null : current().webView)) {
                if (newProgress >= 100) {
                    pageLoading = false;
                    setPageProgress(100);
                } else {
                    pageLoading = true;
                    setPageProgress(newProgress);
                }
            }
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            super.onReceivedTitle(view, title);
            BrowserTab tab = tabOf(view);
            if (tab != null && title != null) {
                tab.title = title;
                if (view == (current() == null ? null : current().webView)) {
                    tabAdapter.notifyDataSetChanged();
                }
            }
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                         FileChooserParams fileChooserParams) {
            if (MainActivity.this.filePathCallback != null) {
                MainActivity.this.filePathCallback.onReceiveValue(null);
            }
            MainActivity.this.filePathCallback = filePathCallback;
            try {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                if (fileChooserParams != null && fileChooserParams.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                startActivityForResult(Intent.createChooser(intent, "选择文件"), REQ_FILE);
                return true;
            } catch (Exception e) {
                MainActivity.this.filePathCallback = null;
                return false;
            }
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
            ZDialog.alert(MainActivity.this, jsTitle(url), message, "知道了", result::confirm);
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
            ZDialog.confirm(MainActivity.this, jsTitle(url), message, "确定",
                    result::confirm, result::cancel);
            return true;
        }

        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String defaultValue,
                                  final JsPromptResult result) {
            ZDialog.input(MainActivity.this, jsTitle(url), message, defaultValue, "确定",
                    result::confirm, result::cancel);
            return true;
        }

        @Override
        public boolean onJsBeforeUnload(WebView view, String url, String message, final JsResult result) {
            ZDialog.confirm(MainActivity.this, jsTitle(url),
                    message == null || message.isEmpty() ? "确定要离开此页面吗？" : message,
                    "离开", result::confirm, result::cancel);
            return true;
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            enterFullscreen(view, callback);
        }

        @Override
        public void onHideCustomView() {
            exitFullscreen();
        }

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
            BrowserTab tab = createTabInternal(false, null);
            if (resultMsg != null && resultMsg.obj instanceof WebView.WebViewTransport) {
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(tab.webView);
                resultMsg.sendToTarget();
            }
            attachCurrent();
            return true;
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            request.deny();
        }
    }
}
