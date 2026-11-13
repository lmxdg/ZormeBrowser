package com.zorme.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** SSL 证书获取与标签实时预览 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h740dp-xxhdpi")
public class SslAndPreviewTest {

    private MainActivity a;

    @Before
    public void setUp() {
        try {
            TabManager.get().destroyAll();
        } catch (Exception ignored) {
        }
        Prefs.setAgreed(org.robolectric.RuntimeEnvironment.getApplication(), true);
        a = Robolectric.buildActivity(MainActivity.class).setup().get();
    }

    @After
    public void tearDown() {
        for (BrowserTab t : TabManager.get().tabs) t.recyclePreview();
        TabManager.get().destroyAll();
    }

    /** 等待 SslInfo 回调：后台线程 post 回主线程，Robolectric 需要手动驱动 Looper */
    private void awaitLatch(CountDownLatch latch, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (latch.getCount() > 0 && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper(100);
            if (latch.await(50, TimeUnit.MILLISECONDS)) return;
        }
        ShadowLooper.idleMainLooper(100);
    }

    /* ---------- SSL 解析纯函数 ---------- */

    @Test
    public void testCnOf() {
        assertEquals("github.com", SslInfo.cnOf("CN=github.com,O=GitHub\\, Inc.,C=US"));
        assertEquals("DigiCert Inc", SslInfo.cnOf("C=US,O=DigiCert Inc,CN=DigiCert Inc"));
        assertEquals("weird-dn", SslInfo.cnOf("weird-dn"));
        assertEquals("", SslInfo.cnOf(null));
        assertEquals("", SslInfo.cnOf(""));
    }

    @Test
    public void testSslFetchRejectsHttp() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] err = new String[1];
        SslInfo.fetch("http://www.baidu.com", (ok, error, info) -> {
            assertFalse("http 网站不应走证书获取", ok);
            err[0] = error;
            latch.countDown();
        });
        awaitLatch(latch, 15000);
        assertEquals("回调应完成", 0, latch.getCount());
        assertNotNull(err[0]);
    }

    @Test
    public void testSslFetchRealSite() throws Exception {
        // 对 api.github.com 做真实 TLS 握手（沙箱可直连），验证完整证书链解析
        final CountDownLatch latch = new CountDownLatch(1);
        final SslInfo[] got = new SslInfo[1];
        final String[] err = new String[1];
        SslInfo.fetch("https://api.github.com", (ok, error, info) -> {
            if (ok) got[0] = info;
            else err[0] = error;
            latch.countDown();
        });
        awaitLatch(latch, 25000);
        if (got[0] == null) {
            // 网络不可用时跳过断言，但错误信息必须存在
            assertNotNull("失败时应有错误信息", err[0]);
            return;
        }
        SslInfo s = got[0];
        assertTrue("subject 不应为空", s.subject != null && !s.subject.isEmpty());
        assertTrue("issuer 不应为空", s.issuer != null && !s.issuer.isEmpty());
        assertNotNull(s.notBefore);
        assertNotNull(s.notAfter);
        assertTrue("有效期应覆盖当前时间", s.notAfter.getTime() > System.currentTimeMillis());
        assertTrue("SHA-256 指纹应为 59 组十六进制对", s.sha256 != null && s.sha256.length() == 95);
        assertTrue("证书链至少 1 张", s.chainSize >= 1);
        assertTrue("摘要文本应包含颁发者", s.summary().contains("颁发者"));
    }

    /* ---------- 标签实时预览 ---------- */

    @Test
    public void testPreviewLifecycle() throws Exception {
        assertEquals(1, TabManager.get().size());
        BrowserTab t = TabManager.get().current();

        // 主页标签不应生成预览
        a.findViewById(R.id.btn_tabs).performClick();
        ShadowLooper.idleMainLooper();
        assertNull("主页标签不应有截图", t.preview);

        // WebView 已布局时捕获不应抛异常（Robolectric 的 WebView.draw 无法渲染真实网页，
        // 该路径在真机验证；此处验证流程安全）
        t.showingHome = false;
        t.title = "百度一下";
        t.url = "https://www.baidu.com";
        t.webView.layout(0, 0, 1080, 2220);
        a.findViewById(R.id.tabs_new).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals(2, TabManager.get().size());
    }

    @Test
    public void testAdapterShowsPreviewAndRecycle() {
        BrowserTab t = TabManager.get().current();
        t.showingHome = false;
        t.title = "示例页";
        t.url = "https://example.com";
        // 模拟捕获成功后的预览位图
        t.preview = Bitmap.createBitmap(240, 500, Bitmap.Config.ARGB_8888);

        // 打开标签管理：适配器应正常绑定截图且不回收位图
        a.findViewById(R.id.btn_tabs).performClick();
        ShadowLooper.idleMainLooper();
        assertFalse("显示中的预览位图不应被回收", t.preview.isRecycled());
        a.findViewById(R.id.tabs_close).performClick();
        ShadowLooper.idleMainLooper();

        // 关闭标签：位图应被回收（closeTab 内部会置空引用，先留一份）
        Bitmap saved = t.preview;
        a.closeTabByIndexForTest(TabManager.get().current);
        assertTrue("关闭标签后位图应回收", saved.isRecycled());
        assertNull(t.preview);
    }
}
