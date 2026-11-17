package com.zorme.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** v1.0.7：必应翻译（整页翻译 / 划词翻译 / 目标语言）相关测试 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h740dp-xxhdpi")
public class Features107Test {

    @Before
    public void setUp() {
        Context c = ApplicationProvider.getApplicationContext();
        Prefs.setAgreed(c, true);
        Prefs.setTranslateTarget(c, BingTranslate.LANGS[0].code);
        Prefs.setAutoTranslate(c, false);
        try {
            TabManager.get().destroyAll();
        } catch (Exception ignored) {
        }
    }

    @After
    public void tearDown() {
        while (ZDialog.handleBack()) {
            // 清空遗留弹窗
        }
        try {
            TabManager.get().destroyAll();
        } catch (Exception ignored) {
        }
    }

    // ---------------- 语言表 ----------------

    @Test
    public void languageTableCoversCommonTargets() {
        assertTrue(BingTranslate.LANGS.length >= 10);
        assertEquals("zh-Hans", BingTranslate.LANGS[0].code);
        assertEquals("简体中文", BingTranslate.LANGS[0].name);
        List<String> codes = new ArrayList<>();
        for (BingTranslate.Lang l : BingTranslate.LANGS) codes.add(l.code);
        assertTrue(codes.contains("en"));
        assertTrue(codes.contains("ja"));
        assertTrue(codes.contains("zh-Hant"));
        // 未知代码回落默认语言
        assertEquals("zh-Hans", BingTranslate.byCode("xx-YY").code);
        assertEquals("英语", BingTranslate.targetName("en"));
        assertTrue(BingTranslate.isChinese("zh-Hant"));
        assertFalse(BingTranslate.isChinese("ja"));
    }

    @Test
    public void sameLanguageComparesPrimarySubtag() {
        assertTrue(BingTranslate.sameLanguage("zh-CN", "zh-Hans"));
        assertTrue(BingTranslate.sameLanguage("en-US", "en"));
        assertFalse(BingTranslate.sameLanguage("en", "zh-Hans"));
        assertFalse(BingTranslate.sameLanguage("", "zh-Hans"));
        assertFalse(BingTranslate.sameLanguage(null, "zh-Hans"));
    }

    @Test
    public void selectionTargetAvoidsMeaninglessDirection() {
        // 中文页面 + 中文目标 → 反向译成英语
        assertEquals("en", BingTranslate.selectionTarget("这是一段中文说明", "zh-Hans"));
        // 英文文本 + 中文目标 → 正常译成中文
        assertEquals("zh-Hans", BingTranslate.selectionTarget("Hello world", "zh-Hans"));
        // 英文文本 + 英语目标 → 反向译成中文
        assertEquals("zh-Hans", BingTranslate.selectionTarget("Hello world", "en"));
        // 其它语言保持用户选择
        assertEquals("ja", BingTranslate.selectionTarget("Hello", "ja"));
        // autoTarget：中文文本译为英语，其它文本译为默认目标语言
        assertEquals("en", BingTranslate.autoTarget("这是一段中文"));
        assertEquals("zh-Hans", BingTranslate.autoTarget("plain english text"));
    }

    // ---------------- 请求构造与解析 ----------------

    @Test
    public void endpointFollowsRedirectedHost() {
        assertEquals("https://cn.bing.com/ttranslatev3", BingTranslate.endpoint("cn.bing.com"));
        assertEquals("https://cn.bing.com/ttranslatev3", BingTranslate.endpoint(""));
        String full = BingTranslate.endpoint("cn.bing.com", "5F17D4E85D714EE28E2470E633D4E4D3");
        assertTrue(full.contains("IG=5F17D4E85D714EE28E2470E633D4E4D3"));
        assertTrue(full.contains("IID=translator.5028.1"));
    }

    @Test
    public void credentialsParsedFromTranslatorPage() {
        String html = "<script>var params_AbusePreventionHelper = "
                + "[1794913871315,\"apbv1j15fXjlKZ4Ub02ctiy0LFiA2E4W\",3600000];"
                + "IG:\"5F17D4E85D714EE28E2470E633D4E4D3\",</script>";
        assertEquals("1794913871315", BingTranslate.parseKey(html));
        assertEquals("apbv1j15fXjlKZ4Ub02ctiy0LFiA2E4W", BingTranslate.parseToken(html));
        assertEquals("5F17D4E85D714EE28E2470E633D4E4D3", BingTranslate.parseIg(html));
        BingTranslate.Creds c = BingTranslate.parseCredentials(html, "cn.bing.com");
        assertNotNull(c);
        assertTrue(c.valid());
        assertEquals("cn.bing.com", c.host);
        // 缺任意一项都视为无效凭据
        assertEquals(null, BingTranslate.parseCredentials("<html></html>", "cn.bing.com"));
        assertEquals("", BingTranslate.parseKey(null));
        assertFalse(new BingTranslate.Creds("", "1", "2", "3").valid());
    }

    @Test
    public void batchGroupingHonoursCountAndCharLimits() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 95; i++) many.add("line " + i);
        List<List<String>> groups = BingTranslate.chunk(many);
        assertEquals(3, groups.size());
        assertEquals(BingTranslate.MAX_BATCH, groups.get(0).size());
        assertEquals(15, groups.get(2).size());
        // 超长文本各自成组，避免超出接口单次字符上限
        List<String> longOnes = Arrays.asList(repeat("a", 3000), repeat("b", 3000));
        assertEquals(2, BingTranslate.chunk(longOnes).size());
        assertTrue(BingTranslate.chunk(null).isEmpty());
    }

    @Test
    public void batchJoinAndSplitKeepSegmentMapping() {
        String joined = BingTranslate.joinBatch(
                Arrays.asList("Hello", "Good morning\nsecond line"));
        assertEquals("Hello\nGood morning second line", joined);
        List<String> back = BingTranslate.splitBatch("你好\n早上好", 2);
        assertNotNull(back);
        assertEquals("你好", back.get(0));
        assertEquals("早上好", back.get(1));
        // 行数对不上时返回 null，由调用方退化为逐段请求
        assertEquals(null, BingTranslate.splitBatch("只有一行", 2));
        assertEquals(null, BingTranslate.splitBatch(null, 2));
    }

    private static String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) sb.append(s);
        return sb.toString();
    }

    @Test
    public void responseParsingKeepsOrderAndToleratesGaps() {
        String json = "[{\"translations\":[{\"text\":\"你好\",\"to\":\"zh-Hans\"}]},"
                + "{\"translations\":[{\"text\":\"世界\",\"to\":\"zh-Hans\"}]},"
                + "{\"translations\":[]}]";
        List<String> out = BingTranslate.parseTranslations(json);
        assertEquals(3, out.size());
        assertEquals("你好", out.get(0));
        assertEquals("世界", out.get(1));
        assertEquals("", out.get(2));
        // 非法 / 空响应不崩溃
        assertTrue(BingTranslate.parseTranslations("not json").isEmpty());
        assertTrue(BingTranslate.parseTranslations(null).isEmpty());
    }

    @Test
    public void friendlyErrorsAreReadable() {
        assertTrue(BingTranslate.friendlyError(429, "").contains("频繁"));
        assertTrue(BingTranslate.friendlyError(401, "").contains("鉴权"));
        assertTrue(BingTranslate.friendlyError(503, "").contains("不可用"));
        // 服务端具体原因会被带出
        assertTrue(BingTranslate.friendlyError(400,
                "{\"error\":{\"message\":\"Text too long\"}}").contains("Text too long"));
        // 接口以 200 返回 statusCode 时也能给出提示
        assertTrue(BingTranslate.friendlyError(200,
                "{\"statusCode\":205,\"errorMessage\":\"\"}").contains("鉴权"));
    }

    // ---------------- 注入脚本 ----------------

    @Test
    public void collectScriptCarriesCursorAndLimit() {
        String js = PageTranslator.jsCollect(80, 40);
        assertTrue(js.contains("__zormeTr"));
        assertTrue(js.contains("var start=80"));
        assertTrue(js.contains("out.length<40"));
        assertTrue("应跳过脚本与样式节点", js.contains("SCRIPT:1"));
        // 负数参数被兜底
        String safe = PageTranslator.jsCollect(-5, 0);
        assertTrue(safe.contains("var start=0"));
        assertTrue(safe.contains("out.length<1"));
    }

    @Test
    public void applyScriptEmbedsEscapedTranslations() throws Exception {
        String js = PageTranslator.jsApply(12, Arrays.asList("你好", "a\"b\nc"));
        assertTrue(js.contains("var start=12"));
        assertTrue(js.contains("__zormeTr"));
        int begin = js.indexOf("var a=") + "var a=".length();
        int end = js.indexOf(";var start=");
        JSONArray arr = new JSONArray(js.substring(begin, end));
        assertEquals("你好", arr.getString(0));
        assertEquals("a\"b\nc", arr.getString(1));
        // 空列表也要生成合法数组
        assertTrue(PageTranslator.jsApply(0, null).contains("var a=[]"));
    }

    @Test
    public void restoreScriptResetsPageState() {
        assertTrue(PageTranslator.JS_RESTORE.contains("S.orig[i]"));
        assertTrue(PageTranslator.JS_RESTORE.contains("S.ready=false"));
        assertTrue(PageTranslator.JS_SELECTION.contains("getSelection"));
        assertTrue(PageTranslator.JS_LANGUAGE.contains("getAttribute('lang')"));
    }

    @Test
    public void jsStringDecodingHandlesQuotes() {
        assertEquals("你好", PageTranslator.decodeJsString("\"你好\""));
        assertEquals("a\"b", PageTranslator.decodeJsString("\"a\\\"b\""));
        assertEquals("", PageTranslator.decodeJsString("null"));
        assertEquals("", PageTranslator.decodeJsString(null));
        assertEquals("", PageTranslator.decodeJsString("x"));
    }

    // ---------------- 偏好设置 ----------------

    @Test
    public void translatePrefsDefaultsAndPersist() {
        Context c = ApplicationProvider.getApplicationContext();
        Prefs.setTranslateTarget(c, null);
        assertEquals("zh-Hans", Prefs.translateTarget(c));
        Prefs.setTranslateTarget(c, "ja");
        assertEquals("ja", Prefs.translateTarget(c));
        Prefs.setTranslateTarget(c, "  ");
        assertEquals("zh-Hans", Prefs.translateTarget(c));
        assertFalse("自动翻译默认关闭", Prefs.autoTranslate(c));
        Prefs.setAutoTranslate(c, true);
        assertTrue(Prefs.autoTranslate(c));
    }

    // ---------------- 界面 ----------------

    @Test
    public void translateBarIsHiddenUntilUsed() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        View bar = a.findViewById(R.id.translate_bar);
        assertNotNull(bar);
        assertEquals(View.GONE, bar.getVisibility());
        assertEquals(View.GONE, a.findViewById(R.id.translate_restore).getVisibility());
        assertEquals("原文", ((TextView) a.findViewById(R.id.translate_restore)).getText().toString());
    }

    @Test
    public void menuOffersTranslationSheet() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        while (ZDialog.handleBack()) {
        }
        BrowserTab t = TabManager.get().current();
        assertNotNull(t);
        t.showingHome = false;
        t.url = "https://example.com";

        a.findViewById(R.id.btn_menu).performClick();
        ShadowLooper.idleMainLooper();

        GridLayout grid = a.findViewById(R.id.menu_grid);
        View tile = null;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View item = grid.getChildAt(i);
            TextView label = item.findViewById(R.id.menu_label);
            if (label != null && "翻译".contentEquals(label.getText())) {
                tile = item;
                break;
            }
        }
        assertNotNull("菜单应新增「翻译」入口", tile);
        tile.performClick();
        ShadowLooper.idleMainLooper();

        assertEquals("翻译 · 必应",
                ((TextView) a.findViewById(R.id.dialog_title)).getText().toString());
        assertNotNull("应提供整页翻译入口", findText(a.findViewById(android.R.id.content), "翻译为简体中文"));
        assertNotNull("应提供目标语言切换", findText(a.findViewById(android.R.id.content), "选择目标语言…"));
        assertNotNull("应提供划词翻译", findText(a.findViewById(android.R.id.content), "翻译选中文字"));
        // 尚未翻译时不显示「显示原文」
        assertEquals(null, findText(a.findViewById(android.R.id.content), "显示原文"));
        // 仍是内置自绘弹窗
        assertEquals(0, org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
    }

    @Test
    public void settingsShowsTranslationSection() {
        SettingsActivity s = Robolectric.buildActivity(SettingsActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        View container = s.findViewById(R.id.settings_container);
        assertNotNull(findText(container, "翻译服务"));
        assertNotNull(findText(container, "目标语言"));
        assertNotNull(findText(container, "自动翻译外语网页"));
        assertNotNull(findText(container, "简体中文"));
        while (ZDialog.handleBack()) {
        }
    }

    /** 深度查找第一个文本完全匹配的视图 */
    private View findText(View root, String text) {
        if (root == null) return null;
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) {
            return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                View found = findText(g.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
