package com.zorme.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Method;

/** 自定义 UA、intent 链接回退、版本比较 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h740dp-xxhdpi")
public class NewFeaturesTest {

    private Context c;

    @Before
    public void setUp() {
        c = ApplicationProvider.getApplicationContext();
        Prefs.setUaMode(c, Prefs.UA_DEFAULT);
        Prefs.setUaCustom(c, "");
        try {
            TabManager.get().destroyAll();
        } catch (Exception ignored) {
        }
    }

    /* ---------- 版本比较 ---------- */

    @Test
    public void testCompareVersion() {
        assertTrue(UpdateChecker.compareVersion("1.0.1", "1.0.0") > 0);
        assertTrue(UpdateChecker.compareVersion("1.0.0", "1.0.0") == 0);
        assertTrue(UpdateChecker.compareVersion("1.0.0", "1.0.1") < 0);
        assertTrue(UpdateChecker.compareVersion("2.0", "1.9.9") > 0);
        assertTrue(UpdateChecker.compareVersion("1.10.0", "1.9.0") > 0);
        assertTrue(UpdateChecker.compareVersion("1.0", "1.0.0") == 0);
        assertTrue(UpdateChecker.compareVersion("", "0.0.1") < 0);
    }

    @Test
    public void testNormalizeTag() {
        assertEquals("1.0.0", UpdateChecker.normalize("v1.0.0"));
        assertEquals("1.0.0", UpdateChecker.normalize("V1.0.0"));
        assertEquals("1.0.0", UpdateChecker.normalize(" 1.0.0 "));
    }

    /* ---------- 自定义 UA ---------- */

    @Test
    public void testUaDefault() {
        Prefs.setUaMode(c, Prefs.UA_DEFAULT);
        // Robolectric 不模拟系统 UA，默认模式下 uaFor 应返回 null（即交给系统默认值）
        assertNull(WebSettingsHelper.uaFor(c));
        WebView wv = new WebView(c);
        WebSettingsHelper.configure(c, wv, false);
        String ua = wv.getSettings().getUserAgentString();
        assertTrue("默认模式不应注入桌面 UA", ua == null || !ua.contains("Windows NT"));
    }

    @Test
    public void testUaDesktop() {
        Prefs.setUaMode(c, Prefs.UA_DESKTOP);
        WebView wv = new WebView(c);
        WebSettingsHelper.configure(c, wv, false);
        assertTrue(wv.getSettings().getUserAgentString().contains("Windows NT"));
        assertTrue("桌面模式应与 desktop 开关同步", Prefs.desktop(c));
    }

    @Test
    public void testUaIphoneIpad() {
        Prefs.setUaMode(c, Prefs.UA_IPHONE);
        WebView wv = new WebView(c);
        WebSettingsHelper.configure(c, wv, false);
        assertTrue(wv.getSettings().getUserAgentString().contains("iPhone"));

        Prefs.setUaMode(c, Prefs.UA_IPAD);
        WebSettingsHelper.configure(c, wv, false);
        assertTrue(wv.getSettings().getUserAgentString().contains("iPad"));
    }

    @Test
    public void testUaCustom() {
        Prefs.setUaCustom(c, "MyZormeUA/9.9");
        Prefs.setUaMode(c, Prefs.UA_CUSTOM);
        WebView wv = new WebView(c);
        WebSettingsHelper.configure(c, wv, false);
        assertEquals("MyZormeUA/9.9", wv.getSettings().getUserAgentString());
    }

    @Test
    public void testDesktopSwitchMigratesUaMode() {
        Prefs.setUaMode(c, Prefs.UA_DEFAULT);
        Prefs.setDesktop(c, true);
        assertEquals(Prefs.UA_DESKTOP, Prefs.uaMode(c));
        Prefs.setDesktop(c, false);
        assertEquals(Prefs.UA_DEFAULT, Prefs.uaMode(c));
    }

    @Test
    public void testUaLegacyDesktopMigration() throws Exception {
        // 旧版本只写过 desktop=true、从未写过 ua_mode 时，应识别为桌面模式
        Context sp = c;
        sp.getSharedPreferences("zorme_prefs", Context.MODE_PRIVATE)
                .edit().clear().putBoolean("desktop", true).commit();
        assertEquals(Prefs.UA_DESKTOP, Prefs.uaMode(c));
        sp.getSharedPreferences("zorme_prefs", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    /* ---------- intent 链接处理与回退 ---------- */

    @Test
    public void testExtractFallbackUrl() throws Exception {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        Method m = MainActivity.class.getDeclaredMethod("extractFallbackUrl", String.class);
        m.setAccessible(true);
        String intent = "intent://detail/#Intent;scheme=taobao;package=com.taobao.taobao;"
                + "S.browser_fallback_url=https%3A%2F%2Fh5.m.taobao.com%2Fdetail.html;end";
        assertEquals("https://h5.m.taobao.com/detail.html", m.invoke(a, intent));
        assertNull(m.invoke(a, "intent://x/#Intent;end"));

        // handleSpecialUrl 应拦截 intent:// 且不抛异常
        Method h = MainActivity.class.getDeclaredMethod("handleSpecialUrl", String.class);
        h.setAccessible(true);
        assertTrue((Boolean) h.invoke(a, intent));
        assertFalse((Boolean) h.invoke(a, "https://www.example.com"));
        assertTrue((Boolean) h.invoke(a, "weixin://dl/chat"));
    }

    @Test
    public void testMainActivityLaunchWithUaPrefs() {
        Prefs.setUaMode(c, Prefs.UA_IPHONE);
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertTrue(TabManager.get().size() >= 1);
        WebView wv = TabManager.get().current().webView;
        assertEquals(WebSettingsHelper.IPHONE_UA, wv.getSettings().getUserAgentString());
    }
}
