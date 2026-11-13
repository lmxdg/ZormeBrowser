package com.zorme.browser;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenAI 兼容 Chat Completions 客户端（POST {base}/chat/completions）。
 * 支持流式 SSE 与一次性返回；请求仅在内存中，无任何云端依赖。
 */
public final class AiClient {

    /** 一条消息（system / user / assistant） */
    public static class Msg {
        public final String role;
        public final String content;

        public Msg(String role, String content) {
            this.role = role;
            this.content = content == null ? "" : content;
        }
    }

    /** 回调：onDelta 收到增量（参数为当前累计全文）；onDone 结束（error 非空表示失败） */
    public interface Callback {
        void onDelta(String fullSoFar);

        void onDone(String fullText, String error);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    private static volatile HttpURLConnection current;
    private static volatile boolean cancelled;

    private AiClient() {
    }

    public static boolean busy() {
        return BUSY.get();
    }

    /** 取消进行中的请求（保留已生成部分） */
    public static void cancel() {
        cancelled = true;
        HttpURLConnection c = current;
        if (c != null) {
            try {
                c.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 规范化 API 地址为完整 chat/completions 端点。
     * 允许填写：根地址、带 /v1 的地址或完整端点；容错尾随斜杠。
     */
    public static String endpoint(String base) {
        if (base == null) return "";
        String b = base.trim();
        if (b.isEmpty()) return "";
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (b.endsWith("/chat/completions")) return b;
        return b + "/chat/completions";
    }

    /** 组装 OpenAI 协议请求体 */
    public static String requestBody(String model, List<Msg> messages, boolean stream) throws Exception {
        JSONObject root = new JSONObject();
        root.put("model", model == null ? "" : model.trim());
        root.put("stream", stream);
        JSONArray arr = new JSONArray();
        for (Msg m : messages) {
            JSONObject o = new JSONObject();
            o.put("role", m.role);
            o.put("content", m.content);
            arr.put(o);
        }
        root.put("messages", arr);
        return root.toString();
    }

    /**
     * 解析 SSE 一行。返回增量文本；[DONE] 返回标记 "[DONE]"；无关行返回 null。
     * 同时兼容非标准返回（如 choices[0].message.content）。
     */
    public static String parseSseLine(String line) {
        if (line == null) return null;
        String t = line.trim();
        if (t.isEmpty() || !t.startsWith("data:")) return null;
        String payload = t.substring(5).trim();
        if (payload.isEmpty()) return null;
        if ("[DONE]".equals(payload)) return "[DONE]";
        try {
            JSONObject o = new JSONObject(payload);
            JSONArray choices = o.optJSONArray("choices");
            if (choices == null || choices.length() == 0) return null;
            JSONObject first = choices.getJSONObject(0);
            JSONObject delta = first.optJSONObject("delta");
            if (delta == null) delta = first.optJSONObject("message");
            if (delta == null) return null;
            return delta.has("content") && !delta.isNull("content")
                    ? delta.getString("content") : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 发起对话请求（异步，回调回到主线程） */
    public static void chat(String base, String key, String model, List<Msg> messages,
                            boolean stream, Callback cb) {
        String url = endpoint(base);
        if (url.isEmpty()) {
            MAIN.post(() -> cb.onDone("", "请先在设置中配置 API 地址"));
            return;
        }
        if (key == null || key.trim().isEmpty()) {
            MAIN.post(() -> cb.onDone("", "请先在设置中填写 API Key"));
            return;
        }
        if (model == null || model.trim().isEmpty()) {
            MAIN.post(() -> cb.onDone("", "请先在设置中填写模型名称"));
            return;
        }
        if (!BUSY.compareAndSet(false, true)) {
            MAIN.post(() -> cb.onDone("", "正在回复中，请先停止或等待完成"));
            return;
        }
        cancelled = false;
        Thread t = new Thread(() -> {
            StringBuilder full = new StringBuilder();
            String error = null;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                current = conn;
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(120000);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + key.trim());
                conn.setDoOutput(true);
                byte[] body = requestBody(model, messages, stream).getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                }
                int code = conn.getResponseCode();
                InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                if (in == null) throw new Exception("HTTP " + code);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                if (code >= 400) {
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = reader.readLine()) != null) sb.append(l);
                    throw new Exception(friendlyError(code, sb.toString()));
                }
                if (!stream) {
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = reader.readLine()) != null) sb.append(l);
                    JSONObject resp = new JSONObject(sb.toString());
                    JSONArray choices = resp.optJSONArray("choices");
                    if (choices != null && choices.length() > 0) {
                        JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
                        if (msg != null) full.append(msg.optString("content"));
                    }
                } else {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (cancelled) break;
                        String delta = parseSseLine(line);
                        if ("[DONE]".equals(delta)) break;
                        if (delta != null && !delta.isEmpty()) {
                            full.append(delta);
                            String snap = full.toString();
                            MAIN.post(() -> cb.onDelta(snap));
                        }
                    }
                }
            } catch (Exception e) {
                if (cancelled) {
                    // 用户主动停止：已生成的部分照常返回，不算错误
                } else {
                    error = e.getMessage() == null ? e.toString() : e.getMessage();
                }
            } finally {
                current = null;
                if (conn != null) {
                    try {
                        conn.disconnect();
                    } catch (Exception ignored) {
                    }
                }
                BUSY.set(false);
            }
            String text = full.toString();
            String err = error;
            MAIN.post(() -> cb.onDone(text, err));
        }, "zorme-ai");
        t.setDaemon(true);
        t.start();
    }

    /** 把 HTTP 错误转换为可读信息（优先提取协议中的 error.message） */
    public static String friendlyError(int code, String body) {
        String detail = "";
        try {
            JSONObject o = new JSONObject(body);
            JSONObject err = o.optJSONObject("error");
            if (err != null) {
                detail = err.optString("message");
            } else if (o.has("message")) {
                detail = o.optString("message");
            }
        } catch (Exception ignored) {
        }
        if (detail.isEmpty()) detail = body;
        if (detail.length() > 300) detail = detail.substring(0, 300) + "…";
        String hint;
        if (code == 401) hint = "API Key 无效或未授权";
        else if (code == 429) hint = "请求过于频繁或额度不足";
        else if (code == 404) hint = "接口地址或模型不存在";
        else hint = "HTTP " + code;
        return hint + (detail.isEmpty() ? "" : ("：" + detail));
    }
}
