package com.zorme.browser;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 内置弹窗与启动主页行为验证。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h740dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DialogTest {

    private static final int W = 1080;
    private static final int H = 2220;

    @Before
    public void setUp() {
        while (ZDialog.handleBack()) {
            // 清空上一个用例遗留的弹窗
        }
        try {
            TabManager.get().destroyAll();
        } catch (Exception ignored) {
        }
        Prefs.setTheme(RuntimeEnvironment.getApplication(), Prefs.THEME_DARK);
        ShadowLooper.idleMainLooper();
        new File("/tmp/shots").mkdirs();
    }

    @After
    public void tearDown() {
        while (ZDialog.handleBack()) {
            // 释放弹窗
        }
        try {
            TabManager.get().destroyAll();
        } catch (Exception ignored) {
        }
    }

    @Test
    public void confirmDialogRendersAndCallsBack() throws Exception {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        layout(a);

        final AtomicBoolean ok = new AtomicBoolean(false);
        ZDialog.confirmDanger(a, "清除浏览数据",
                "将清除缓存、Cookie、本地存储和历史记录，书签会保留。", "清除", () -> ok.set(true));
        ShadowLooper.idleMainLooper();
        settle(a);
        layout(a);
        assertNotNull(a.findViewById(R.id.dialog_root));
        assertTrue("不应创建任何系统 Dialog", ShadowDialog.getShownDialogs().isEmpty());
        save(a, "/tmp/shots/09_dialog_confirm.png");

        a.findViewById(R.id.dialog_positive).performClick();
        ShadowLooper.idleMainLooper();
        assertTrue("确定按钮应触发回调", ok.get());

        // 取消不触发确定回调
        final AtomicBoolean ok2 = new AtomicBoolean(false);
        ZDialog.confirm(a, "退出 Zorme", "再按一次返回键退出", "退出", () -> ok2.set(true));
        ShadowLooper.idleMainLooper();
        settle(a);
        layout(a);
        assertFalse(ok2.get());
        ZDialog.handleBack();
        ShadowLooper.idleMainLooper();
        assertFalse(ok2.get());
    }

    @Test
    public void inputDialogRenders() throws Exception {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        layout(a);

        final String[] result = new String[1];
        ZDialog.input(a, "添加书签", "书签名称", "Android Developers 官网", "保存",
                text -> result[0] = text);
        ShadowLooper.idleMainLooper();
        settle(a);
        layout(a);
        assertNotNull(a.findViewById(R.id.dialog_input));
        save(a, "/tmp/shots/10_dialog_input.png");

        a.findViewById(R.id.dialog_positive).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals("Android Developers 官网", result[0]);
    }

    @Test
    public void optionDialogRenders() throws Exception {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        layout(a);

        final int[] picked = new int[]{-1};
        ZDialog.options(a, "链接", new String[]{"在新标签页打开", "在后台打开", "复制链接", "下载链接"},
                i -> picked[0] = i);
        ShadowLooper.idleMainLooper();
        settle(a);
        layout(a);
        save(a, "/tmp/shots/11_dialog_options.png");

        assertEquals(4, ((android.view.ViewGroup) a.findViewById(R.id.dialog_options)).getChildCount());
        ZDialog.handleBack();
        ShadowLooper.idleMainLooper();
        assertEquals(-1, picked[0]);
    }

    @Test
    public void choiceDialogRenders() throws Exception {
        SettingsActivity s = Robolectric.buildActivity(SettingsActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        layout(s);

        ZDialog.choice(s, "搜索引擎", new String[]{"百度", "必应", "Google", "搜狗", "360 搜索"},
                0, i -> {
                });
        ShadowLooper.idleMainLooper();
        settle(s);
        layout(s);
        save(s, "/tmp/shots/12_dialog_choice.png");
    }

    @Test
    public void toastIsBuiltIn() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        Utils.toast(a, "书签已添加");
        ShadowLooper.idleMainLooper();
        View v = a.findViewById(android.R.id.content).findViewWithTag("zorme_toast");
        assertNotNull("应使用内置浮层提示", v);
        assertEquals("不应调用系统 Toast", 0, ShadowToast.shownToastCount());
    }

    /** 冷启动必须落在主页，即使上一次停在网页 */
    @Test
    public void coldStartAlwaysShowsHome() throws Exception {
        MainActivity first = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        BrowserTab tab = TabManager.get().current();
        assertNotNull(tab);
        assertTrue("首次启动应显示主页", tab.showingHome);

        // 模拟打开过网页
        tab.showingHome = false;
        tab.url = "https://developer.android.com/";
        Session.save(first);
        ShadowLooper.idleMainLooper();

        // 新进程冷启动：savedInstanceState 为空
        MainActivity second = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        BrowserTab now = TabManager.get().current();
        assertNotNull(now);
        assertTrue("每次启动都应自动打开主页", now.showingHome);
    }

    /** 关闭「启动时打开主页」后才允许恢复上次页面 */
    @Test
    public void openHomeSettingIsRespected() {
        Prefs.setOpenHome(RuntimeEnvironment.getApplication(), false);
        boolean old = Prefs.restoreTabs(RuntimeEnvironment.getApplication());
        Prefs.setRestoreTabs(RuntimeEnvironment.getApplication(), true);

        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        BrowserTab tab = TabManager.get().current();
        assertNotNull(tab);
        // 无历史会话时仍然给出主页
        assertTrue(tab.showingHome);

        Prefs.setOpenHome(RuntimeEnvironment.getApplication(), true);
        Prefs.setRestoreTabs(RuntimeEnvironment.getApplication(), old);
    }

    /** 自定义主页应真正被使用 */
    @Test
    public void customHomepageIsUsed() {
        Prefs.setHomepage(RuntimeEnvironment.getApplication(), "https://example.com");
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        BrowserTab tab = TabManager.get().current();
        assertNotNull(tab);
        assertFalse("设置自定义主页后不应再显示内置起始页", tab.showingHome);
        assertEquals("https://example.com", tab.url);

        // 清空后回到内置起始页
        Prefs.setHomepage(RuntimeEnvironment.getApplication(), "");
        MainActivity b = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        assertTrue(TabManager.get().current().showingHome);
    }

    /** 从设置页返回时，开关值应持久化 */
    @Test
    public void settingsPersist() {
        SettingsActivity s = Robolectric.buildActivity(SettingsActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        layout(s);

        boolean before = Prefs.openHome(s);
        Prefs.setOpenHome(s, !before);
        assertEquals(!before, Prefs.openHome(s));
        Prefs.setOpenHome(s, before);
        assertEquals(before, Prefs.openHome(s));
    }

    private void settle(android.app.Activity a) {
        View root = a.findViewById(R.id.dialog_root);
        if (root == null) return;
        root.setAlpha(1f);
        View card = a.findViewById(R.id.dialog_card);
        if (card != null) card.setTranslationY(0);
        root.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, W, H);
    }

    private void layout(android.app.Activity a) {
        View decor = a.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, W, H);
    }

    private void save(android.app.Activity a, String path) throws Exception {
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        a.getWindow().getDecorView().draw(canvas);
        FileOutputStream out = new FileOutputStream(path);
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        out.close();
        bmp.recycle();
    }
}
