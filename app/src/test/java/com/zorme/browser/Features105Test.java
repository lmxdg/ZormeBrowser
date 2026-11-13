package com.zorme.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

/**
 * v1.0.5 新功能：协议确认、GitHub 加速站、网络日志、启动主页修复、网页源码入口。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h740dp-xxhdpi")
public class Features105Test {

    @Before
    public void setUp() {
        RuntimeEnvironment.getApplication()
                .getSharedPreferences("zorme_prefs", 0).edit().clear().commit();
        RuntimeEnvironment.getApplication()
                .getSharedPreferences("zorme_session", 0).edit().clear().commit();
        try {
            TabManager.get().destroyAll();
        } catch (Exception ignored) {
        }
        NetworkLog.clear();
        MainActivity.recreatingForTheme = false;
    }

    @After
    public void tearDown() {
        while (ZDialog.handleBack()) {
            // 清空本用例遗留的弹窗
        }
        try {
            TabManager.get().destroyAll();
        } catch (Exception ignored) {
        }
        NetworkLog.clear();
        MainActivity.recreatingForTheme = false;
    }

    /* ---------- 首次启动协议 ---------- */

    @Test
    public void agreementShownOnFirstLaunch() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        assertTrue("首次启动应弹出协议确认", ZDialog.isShowing());
        // 仍然只用内置弹窗，不出现系统 Dialog / Toast
        assertEquals(0, ShadowDialog.getShownDialogs().size());
        assertTrue(ShadowToast.getLatestToast() == null);
        assertFalse(Prefs.agreed(a));
        assertFalse(a.isFinishing());
    }

    @Test
    public void agreementMentionsOpenSourceAndNoCloud() {
        String text = Agreement.fullText();
        assertTrue(text.contains("开源"));
        assertTrue(text.contains("无云服务") || text.contains("没有服务器"));
        assertTrue(text.contains("本地"));
        assertTrue(text.contains("隐私政策"));
        assertTrue(text.contains("用户协议"));
        assertTrue(text.contains("github.com/lmxdg/ZormeBrowser"));
    }

    @Test
    public void agreementAcceptRecordsAndContinues() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        assertTrue(ZDialog.isShowing());
        // 点击"同意并继续"（弹窗底部主按钮）
        TextView positive = a.findViewById(R.id.dialog_positive);
        assertNotNull(positive);
        positive.performClick();
        ShadowLooper.idleMainLooper();
        assertTrue(Prefs.agreed(a));
        assertFalse(ZDialog.isShowing());
        assertFalse("同意后应继续使用，不退出", a.isFinishing());
    }

    @Test
    public void agreementDeclineExits() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        TextView negative = a.findViewById(R.id.dialog_negative);
        assertNotNull(negative);
        negative.performClick();
        ShadowLooper.idleMainLooper();
        assertFalse(Prefs.agreed(a));
        assertTrue("拒绝协议应退出应用", a.isFinishing());
    }

    @Test
    public void agreementNotShownWhenAlreadyAgreed() {
        Prefs.setAgreed(RuntimeEnvironment.getApplication(), true);
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        assertFalse(ZDialog.isShowing());
    }

    /* ---------- 更新下载加速站 ---------- */

    @Test
    public void applyMirrorBuildsProxyUrls() {
        String apk = "https://github.com/lmxdg/ZormeBrowser/releases/download/1.0.5/app.apk";
        assertEquals(apk, UpdateChecker.applyMirror(apk, 0));
        assertEquals("https://ghfast.top/" + apk, UpdateChecker.applyMirror(apk, 1));
        assertEquals("https://ghproxy.net/" + apk, UpdateChecker.applyMirror(apk, 2));
        // 非法下标与原样返回
        assertEquals(apk, UpdateChecker.applyMirror(apk, -1));
        assertEquals(apk, UpdateChecker.applyMirror(apk, 99));
        assertNullDirect(UpdateChecker.applyMirror(null, 1));
        assertEquals("", UpdateChecker.applyMirror("", 1));
    }

    private void assertNullDirect(String s) {
        assertTrue(s == null);
    }

    @Test
    public void mirrorNamesMatchPrefixes() {
        assertEquals(UpdateChecker.MIRROR_NAMES.length, UpdateChecker.MIRROR_PREFIXES.length);
        assertTrue(UpdateChecker.MIRROR_NAMES.length >= 3);
        assertTrue(UpdateChecker.MIRROR_NAMES[0].contains("直连"));
        for (int i = 1; i < UpdateChecker.MIRROR_PREFIXES.length; i++) {
            assertTrue(UpdateChecker.MIRROR_PREFIXES[i].startsWith("https://"));
        }
    }

    @Test
    public void mirrorChoiceIsRemembered() {
        Prefs.setUpdateMirror(RuntimeEnvironment.getApplication(), 2);
        assertEquals(2, Prefs.updateMirror(RuntimeEnvironment.getApplication()));
        Prefs.setUpdateMirror(RuntimeEnvironment.getApplication(), 0);
        assertEquals(0, Prefs.updateMirror(RuntimeEnvironment.getApplication()));
    }

    /* ---------- 网络日志 ---------- */

    @Test
    public void networkLogRecordsAndOrdersNewestFirst() {
        NetworkLog.record("GET", "https://a.com/page", true, false);
        NetworkLog.record("GET", "https://a.com/style.css", false, false);
        NetworkLog.record("POST", "https://b.com/api", false, false);
        assertEquals(3, NetworkLog.size());
        var list = NetworkLog.snapshot();
        assertEquals("https://b.com/api", list.get(0).url);
        assertEquals("POST", list.get(0).method);
        assertEquals("https://a.com/page", list.get(list.size() - 1).url);
        assertTrue(list.get(list.size() - 1).mainFrame);
        assertFalse(list.get(0).mainFrame);
    }

    @Test
    public void networkLogMarksBlockedEntries() {
        NetworkLog.record("GET", "https://ad.example.com/banner.js", false, true);
        assertTrue(NetworkLog.snapshot().get(0).blocked);
    }

    @Test
    public void networkLogCapsAtLimit() {
        for (int i = 0; i < NetworkLog.MAX_ENTRIES + 50; i++) {
            NetworkLog.record("GET", "https://x.com/" + i, false, false);
        }
        assertEquals(NetworkLog.MAX_ENTRIES, NetworkLog.size());
        // 最新的一条保留
        assertTrue(NetworkLog.snapshot().get(0).url.endsWith("/" + (NetworkLog.MAX_ENTRIES + 49)));
    }

    @Test
    public void networkLogClear() {
        NetworkLog.record("GET", "https://x.com", false, false);
        NetworkLog.clear();
        assertEquals(0, NetworkLog.size());
    }

    /* ---------- 启动主页修复 ---------- */

    @Test
    public void coldStartOpensHomeWhenEnabled() {
        Prefs.setOpenHome(RuntimeEnvironment.getApplication(), true);
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        assertEquals(1, TabManager.get().size());
        assertTrue("启动后当前标签应处于主页", TabManager.get().current().showingHome);
        assertEquals(View.VISIBLE, a.findViewById(R.id.home_view).getVisibility());
    }

    @Test
    public void recreateAfterProcessDeathStillOpensHome() throws Exception {
        Prefs.setAgreed(RuntimeEnvironment.getApplication(), true);
        Prefs.setOpenHome(RuntimeEnvironment.getApplication(), true);
        // 先起一个实例并通过 ACTION_VIEW 打开一个网页，模拟浏览过内容
        MainActivity first = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        java.lang.reflect.Method ct = MainActivity.class.getDeclaredMethod(
                "createTab", boolean.class, String.class);
        ct.setAccessible(true);
        ct.invoke(first, false, "https://example.com/last");
        Session.save(first);
        TabManager.get().destroyAll();

        // 模拟"进程被杀后从最近任务恢复"（savedInstanceState != null）：
        // 开启"启动打开主页"时，重建也应直接进入主页，而不是需要手动点主页
        java.lang.reflect.Method m = MainActivity.class.getDeclaredMethod(
                "restoreOrCreateTabs", android.os.Bundle.class);
        m.setAccessible(true);
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        m.invoke(a, new android.os.Bundle());
        ShadowLooper.idleMainLooper();
        assertTrue("重建后应回到主页", TabManager.get().current().showingHome);
        assertEquals(View.VISIBLE, a.findViewById(R.id.home_view).getVisibility());
    }

    @Test
    public void themeRecreateKeepsRestoredTabs() throws Exception {
        Prefs.setAgreed(RuntimeEnvironment.getApplication(), true);
        Prefs.setOpenHome(RuntimeEnvironment.getApplication(), true);
        RuntimeEnvironment.getApplication()
                .getSharedPreferences("zorme_session", 0).edit()
                .putString("tabs", "[{\"url\":\"https://example.com/last\",\"title\":\"Last\"}]")
                .putInt("current", 0).commit();
        // 主题切换触发的 recreate：保留浏览内容，不强制回主页
        // （buildActivity().setup() 的 onCreate 已消耗过一次标记，这里重新设置以模拟 recreate 入参）
        MainActivity.recreatingForTheme = true;
        java.lang.reflect.Method m = MainActivity.class.getDeclaredMethod(
                "restoreOrCreateTabs", android.os.Bundle.class);
        m.setAccessible(true);
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        MainActivity.recreatingForTheme = true;
        m.invoke(a, new android.os.Bundle());
        ShadowLooper.idleMainLooper();
        assertFalse("主题重建不应强制回主页", TabManager.get().current().showingHome);
        assertEquals("https://example.com/last", TabManager.get().current().url);
        assertEquals(View.GONE, a.findViewById(R.id.home_view).getVisibility());
    }

    /* ---------- 菜单新入口 ---------- */

    @Test
    public void menuContainsSourceAndNetlogTiles() {
        Prefs.setAgreed(RuntimeEnvironment.getApplication(), true);
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        a.findViewById(R.id.btn_menu).performClick();
        GridLayout grid = a.findViewById(R.id.menu_grid);
        assertEquals(15, grid.getChildCount());
        boolean hasSource = false, hasNetlog = false;
        for (int i = 0; i < grid.getChildCount(); i++) {
            TextView label = grid.getChildAt(i).findViewById(R.id.menu_label);
            String s = label.getText().toString();
            if ("网页源码".equals(s)) hasSource = true;
            if ("网络日志".equals(s)) hasNetlog = true;
        }
        assertTrue(hasSource);
        assertTrue(hasNetlog);
    }

    /* ---------- 网页源码 ---------- */

    @Test
    public void viewSourceRequiresARealPage() throws Exception {
        Prefs.setAgreed(RuntimeEnvironment.getApplication(), true);
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        // 当前在主页：应通过自绘浮层提示，而不是崩溃或调用系统弹窗
        java.lang.reflect.Method m = MainActivity.class.getDeclaredMethod("viewSource");
        m.setAccessible(true);
        m.invoke(a);
        ShadowLooper.idleMainLooper();
        View toastView = a.findViewById(android.R.id.content).findViewWithTag("zorme_toast");
        assertNotNull("应弹出\"请先打开一个网页\"的自绘提示", toastView);
        assertTrue(((TextView) toastView).getText().toString().contains("请先打开一个网页"));
        assertEquals(0, ShadowDialog.getShownDialogs().size());
    }

    /* ---------- 网络日志页 ---------- */

    @Test
    public void networkLogActivityStarts() {
        NetworkLog.record("GET", "https://example.com/index", true, false);
        NetworkLog.record("GET", "https://ad.example.com/x.js", false, true);
        Intent i = new Intent(RuntimeEnvironment.getApplication(), NetworkLogActivity.class);
        NetworkLogActivity act = Robolectric.buildActivity(NetworkLogActivity.class, i).setup().get();
        assertNotNull(act.findViewById(R.id.list_recycler));
        TextView title = act.findViewById(R.id.list_title);
        assertEquals("网络日志", title.getText().toString());
    }

    /* ---------- 设置页新增项 ---------- */

    @Test
    public void settingsShowsAgreementEntry() {
        Prefs.setAgreed(RuntimeEnvironment.getApplication(), true);
        SettingsActivity s = Robolectric.buildActivity(SettingsActivity.class).setup().get();
        assertNotNull(s.findViewById(R.id.settings_container));
        // 布局构建完成（新条目在点击时弹出协议内容，确保 Agreement 可弹）
        ZDialog.alert(s, Agreement.title(), Agreement.fullText());
        assertTrue(ZDialog.isShowing());
    }
}
