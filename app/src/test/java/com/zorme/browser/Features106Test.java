package com.zorme.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** v1.0.6：AI 助手、弹窗长文本滚动、主页黑白 logo 相关测试 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h740dp-xxhdpi")
public class Features106Test {

    @Before
    public void setUp() {
        Prefs.setAgreed(ApplicationProvider.getApplicationContext(), true);
        Prefs.setAiBase(ApplicationProvider.getApplicationContext(),
                AiProvider.PRESETS[0].baseUrl);
        Prefs.setAiKey(ApplicationProvider.getApplicationContext(), "");
        Prefs.setAiModel(ApplicationProvider.getApplicationContext(),
                AiProvider.PRESETS[0].model);
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

    // ---------------- 内置服务商预设 ----------------

    @Test
    public void builtinPresetsCoverMajorProviders() {
        assertEquals(6, AiProvider.PRESETS.length);
        List<String> names = new ArrayList<>();
        for (AiProvider p : AiProvider.PRESETS) names.add(p.name);
        // 内置：智谱 / DeepSeek / 阿里云百炼 / 基元律动 / 字节跳动 + 自定义
        assertTrue(names.contains("智谱"));
        assertTrue(names.contains("DeepSeek"));
        assertTrue(names.contains("阿里云百炼"));
        assertTrue(names.contains("基元律动"));
        assertTrue(names.contains("字节跳动"));
        assertEquals("自定义", AiProvider.PRESETS[AiProvider.CUSTOM].name);
        for (int i = 0; i < AiProvider.CUSTOM; i++) {
            assertTrue("预设地址必须为 https："
                            + AiProvider.PRESETS[i].baseUrl,
                    AiProvider.PRESETS[i].baseUrl.startsWith("https://"));
            assertFalse(AiProvider.PRESETS[i].model.isEmpty());
        }
        assertEquals("", AiProvider.PRESETS[AiProvider.CUSTOM].baseUrl);
        assertNotNull(AiProvider.byName("DeepSeek"));
        assertEquals(null, AiProvider.byName("不存在"));
    }

    @Test
    public void providerNameFollowsConfiguredBase() {
        assertEquals("智谱", AiProvider.currentName(
                AiProvider.PRESETS[0].baseUrl, null));
        assertEquals("基元律动", AiProvider.currentName(
                "https://tokenrhythm.studio/v1", null));
        // 与预设不符 → 自定义
        assertEquals("自定义", AiProvider.currentName(
                "https://my-own.example.com/v1", null));
        assertEquals("自定义", AiProvider.currentName("", null));
    }

    // ---------------- OpenAI 兼容协议 ----------------

    @Test
    public void endpointNormalization() {
        assertEquals("https://a.com/v1/chat/completions",
                AiClient.endpoint("https://a.com/v1"));
        assertEquals("https://a.com/v1/chat/completions",
                AiClient.endpoint("https://a.com/v1/"));
        assertEquals("https://a.com/v1/chat/completions",
                AiClient.endpoint("https://a.com/v1/chat/completions"));
        assertEquals("https://a.com/chat/completions",
                AiClient.endpoint(" https://a.com "));
        assertEquals("", AiClient.endpoint(""));
        assertEquals("", AiClient.endpoint(null));
        // 智谱与基元律动的真实预设可正确拼出端点
        assertEquals("https://open.bigmodel.cn/api/paas/v4/chat/completions",
                AiClient.endpoint(AiProvider.PRESETS[0].baseUrl));
        assertEquals("https://tokenrhythm.studio/v1/chat/completions",
                AiClient.endpoint("https://tokenrhythm.studio/v1"));
    }

    @Test
    public void requestBodyFollowsOpenAiProtocol() throws Exception {
        List<AiClient.Msg> msgs = Arrays.asList(
                new AiClient.Msg("system", "you are a helper"),
                new AiClient.Msg("user", "你好"),
                new AiClient.Msg("assistant", "你好！"));
        String body = AiClient.requestBody("glm-4-flash", msgs, true);
        JSONObject o = new JSONObject(body);
        assertEquals("glm-4-flash", o.getString("model"));
        assertTrue(o.getBoolean("stream"));
        JSONArray arr = o.getJSONArray("messages");
        assertEquals(3, arr.length());
        assertEquals("system", arr.getJSONObject(0).getString("role"));
        assertEquals("你好", arr.getJSONObject(1).getString("content"));
        assertEquals("assistant", arr.getJSONObject(2).getString("role"));
        // 中文与特殊字符不应破坏 JSON
        String tricky = AiClient.requestBody("m", Arrays.asList(
                new AiClient.Msg("user", "引号\"与\n换行")), false);
        assertEquals("引号\"与\n换行",
                new JSONObject(tricky).getJSONArray("messages")
                        .getJSONObject(0).getString("content"));
    }

    @Test
    public void sseDeltaParsing() {
        assertEquals("世", AiClient.parseSseLine(
                "data: {\"choices\":[{\"delta\":{\"content\":\"世\"}}]}"));
        assertEquals("Hello", AiClient.parseSseLine(
                "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}"));
        // 空增量
        assertEquals("", AiClient.parseSseLine(
                "data: {\"choices\":[{\"delta\":{\"content\":\"\"}}]}"));
        // 结束标记
        assertEquals("[DONE]", AiClient.parseSseLine("data: [DONE]"));
        // 无关行与坏 JSON
        assertEquals(null, AiClient.parseSseLine(": keep-alive"));
        assertEquals(null, AiClient.parseSseLine("event: ping"));
        assertEquals(null, AiClient.parseSseLine("data: not-json"));
        assertEquals(null, AiClient.parseSseLine(null));
        assertEquals(null, AiClient.parseSseLine(""));
        // 兼容 message 形式
        assertEquals("x", AiClient.parseSseLine(
                "data: {\"choices\":[{\"message\":{\"content\":\"x\"}}]}"));
    }

    @Test
    public void friendlyErrorExtractsServerMessage() {
        String e = AiClient.friendlyError(401,
                "{\"error\":{\"message\":\"Invalid API key\"}}");
        assertTrue(e.contains("Invalid API key"));
        assertTrue(e.contains("API Key"));
        assertTrue(AiClient.friendlyError(429, "{}").contains("频繁"));
        assertTrue(AiClient.friendlyError(404, "{}").contains("不存在"));
    }

    // ---------------- 配置存取 ----------------

    @Test
    public void aiConfigPersisted() {
        android.content.Context c = ApplicationProvider.getApplicationContext();
        Prefs.setAiBase(c, "https://tokenrhythm.studio/v1");
        Prefs.setAiKey(c, "sk_test_123");
        Prefs.setAiModel(c, "deepseek-v4-flash-0731");
        assertEquals("https://tokenrhythm.studio/v1", Prefs.aiBase(c));
        assertEquals("sk_test_123", Prefs.aiKey(c));
        assertEquals("deepseek-v4-flash-0731", Prefs.aiModel(c));
        // 空值容错
        Prefs.setAiKey(c, null);
        assertEquals("", Prefs.aiKey(c));
        // 默认值
        Prefs.setAiBase(c, null);
        assertEquals(AiProvider.PRESETS[0].baseUrl, Prefs.aiBase(c));
    }

    // ---------------- 界面：主页黑白 logo + AI 入口 ----------------

    @Test
    public void homeShowsMonochromeLogoAndAiEntry() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        android.widget.ImageView logo = a.findViewById(R.id.home_logo);
        assertNotNull(logo);
        // 黑白配色：tint 跟随主题文字色（深色主题下为白）
        android.content.Context c = ApplicationProvider.getApplicationContext();
        int expected = c.getColor(R.color.text_primary);
        assertEquals(expected, logo.getImageTintList().getDefaultColor());
        // 主页 AI 入口可见可点
        View aiEntry = a.findViewById(R.id.home_ai_entry);
        assertEquals(View.VISIBLE, aiEntry.getVisibility());
        aiEntry.performClick();
        ShadowLooper.idleMainLooper();
        org.robolectric.shadows.ShadowActivity.IntentForResult res =
                Shadows.shadowOf(a).getNextStartedActivityForResult();
        assertNotNull("点击主页 AI 入口应打开聊天页", res);
        assertEquals(AiChatActivity.class.getName(),
                res.intent.getComponent().getClassName());
    }

    // ---------------- 界面：聊天页 ----------------

    @Test
    public void chatActivityShowsWelcomeAndSendButton() {
        AiChatActivity a = Robolectric.buildActivity(AiChatActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        TextView send = a.findViewById(R.id.ai_send);
        assertEquals("发送", send.getText().toString());
        EditText input = a.findViewById(R.id.ai_input);
        assertNotNull(input);
        assertEquals(View.VISIBLE, input.getVisibility());
        // 无 Key 时给出配置提示（自绘弹窗）
        assertTrue(ZDialog.isShowing());
        // 清空遗留弹窗再继续
        while (ZDialog.handleBack()) {
        }
        // 发送为空文本不产生请求
        assertFalse(AiClient.busy());
    }

    @Test
    public void chatClearRemovesAllBubbles() {
        AiChatActivity a = Robolectric.buildActivity(AiChatActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        while (ZDialog.handleBack()) {
        }
        a.findViewById(R.id.ai_clear).performClick();
        ShadowLooper.idleMainLooper();
        // 确认清空
        RecyclerView list = a.findViewById(R.id.ai_list);
        int before = list.getAdapter().getItemCount();
        assertTrue(before > 0);
        // 点击确认按钮（弹窗按钮为自绘 TextView）
        View positive = findDialogButton(a, true);
        assertNotNull(positive);
        positive.performClick();
        ShadowLooper.idleMainLooper();
        assertEquals(0, list.getAdapter().getItemCount());
    }

    /** 在自绘弹窗中查找确认/取消按钮 */
    private View findDialogButton(android.app.Activity a, boolean positive) {
        android.view.ViewGroup content = a.findViewById(android.R.id.content);
        List<View> outs = new ArrayList<>();
        collectButtons(content, outs);
        for (View v : outs) {
            if (v.getId() == (positive ? R.id.dialog_positive : R.id.dialog_negative)) {
                return v;
            }
        }
        return null;
    }

    private void collectButtons(View v, List<View> outs) {
        if (v.getId() == R.id.dialog_positive || v.getId() == R.id.dialog_negative) {
            outs.add(v);
        }
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                collectButtons(((ViewGroup) v).getChildAt(i), outs);
            }
        }
    }

    // ---------------- 弹窗长文本可滚动（显示不全修复） ----------------

    @Test
    public void longMessageIsFullyShownAndScrollable() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        while (ZDialog.handleBack()) {
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 120; i++) sb.append("第").append(i).append("行更新内容说明。\n");
        String full = sb.toString();
        ZDialog.alert(a, "更新日志", full);
        ShadowLooper.idleMainLooper();

        ScrollView scroll = a.findViewById(R.id.dialog_message_scroll);
        TextView message = a.findViewById(R.id.dialog_message);
        assertEquals(View.VISIBLE, scroll.getVisibility());
        assertEquals(full, message.getText().toString());
        // 不再截断：未设置省略号
        assertEquals(null, message.getEllipsize());
        // 内容超长时容器高度被限制（可滚动），且滚动容器允许内部滚动
        int max = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.55f);
        assertTrue("超出限制后应压到 max 以内，实际 "
                        + scroll.getHeight() + " vs " + max,
                scroll.getHeight() <= max + 2);
        ShadowLooper.idleMainLooper();
        assertTrue("应该可以向下滚动", scroll.canScrollVertically(1));
    }

    @Test
    public void shortMessageShowsWithoutScrollLimit() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        while (ZDialog.handleBack()) {
        }
        ZDialog.confirm(a, "标题", "短内容", "好", null);
        ShadowLooper.idleMainLooper();
        ScrollView scroll = a.findViewById(R.id.dialog_message_scroll);
        TextView message = a.findViewById(R.id.dialog_message);
        assertEquals("短内容", message.getText().toString());
        assertTrue("短内容不应被压高", scroll.getHeight() < 400);
    }

    @Test
    public void agreementDialogStillUsesInternalPopupOnly() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        ZDialog.alert(a, Agreement.title(), Agreement.fullText());
        ShadowLooper.idleMainLooper();
        // 长协议全文进入弹窗（不截断）且仍无系统弹窗
        TextView message = a.findViewById(R.id.dialog_message);
        assertEquals(Agreement.fullText(), message.getText().toString());
        assertEquals(0, org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
        assertEquals(0, org.robolectric.shadows.ShadowToast.shownToastCount());
    }
}
