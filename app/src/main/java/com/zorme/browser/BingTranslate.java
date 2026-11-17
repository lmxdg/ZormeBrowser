package com.zorme.browser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 必应翻译客户端。
 *
 * <p>使用必应翻译网页版（cn.bing.com / www.bing.com）公开的翻译接口：
 * 先取页面里的 {@code params_AbusePreventionHelper}（请求 key + token）与 {@code IG}，
 * 再提交到 {@code /ttranslatev3}。无需用户配置 API Key，也不经过任何第三方中转服务。</p>
 *
 * <p>多段文本会以换行拼接后一次请求，返回结果按行还原为逐段译文，减少请求次数；
 * 若服务端合并或拆分了行，则自动退化为逐段请求，保证段落与译文一一对应。</p>
 *
 * <p>所有方法均为静态方法，不持有 Context，便于单元测试。</p>
 */
public class BingTranslate {

    /** 翻译页（同时用于换取请求凭据） */
    public static final String PAGE_URL = "https://www.bing.com/translator";
    /** 接口路径，主机名取实际重定向后的域名（国内为 cn.bing.com） */
    public static final String API_PATH = "/ttranslatev3";
    public static final String IID = "translator.5028.1";

    /** 单次请求最多携带的段落数 */
    public static final int MAX_BATCH = 40;
    /** 单次请求的最大字符数（接口对单段文本有长度限制，留出余量） */
    public static final int MAX_CHARS = 4500;

    /** 凭据有效期约 1 小时，本地缓存 20 分钟 */
    private static final long CRED_TTL = 20 * 60 * 1000L;

    private static final String UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 "
            + "Edg/124.0.0.0";

    private static final Pattern P_KEY = Pattern.compile(
            "params_AbusePreventionHelper\\s*=\\s*\\[\\s*(\\d{6,})\\s*,\\s*\"([^\"]+)\"");
    private static final Pattern P_IG = Pattern.compile("IG\\s*:\\s*\"([0-9A-Fa-f]{8,})\"");

    private static Creds cachedCreds;
    private static long cachedAt;

    /** 目标语言：显示名 + 接口代码 */
    public static final class Lang {
        public final String name;
        public final String code;

        Lang(String name, String code) {
            this.name = name;
            this.code = code;
        }
    }

    /** 支持的目标语言，第 0 项为默认值 */
    public static final Lang[] LANGS = new Lang[]{
            new Lang("简体中文", "zh-Hans"),
            new Lang("繁体中文", "zh-Hant"),
            new Lang("英语", "en"),
            new Lang("日语", "ja"),
            new Lang("韩语", "ko"),
            new Lang("法语", "fr"),
            new Lang("德语", "de"),
            new Lang("西班牙语", "es"),
            new Lang("葡萄牙语", "pt"),
            new Lang("俄语", "ru"),
            new Lang("意大利语", "it"),
            new Lang("阿拉伯语", "ar"),
            new Lang("泰语", "th"),
            new Lang("越南语", "vi"),
            new Lang("土耳其语", "tr"),
    };

    /** 请求凭据：key / token / IG + 实际可用的主机 */
    public static final class Creds {
        public final String host;
        public final String key;
        public final String token;
        public final String ig;

        Creds(String host, String key, String token, String ig) {
            this.host = host;
            this.key = key;
            this.token = token;
            this.ig = ig;
        }

        public boolean valid() {
            return key != null && !key.isEmpty()
                    && token != null && !token.isEmpty()
                    && ig != null && !ig.isEmpty()
                    && host != null && !host.isEmpty();
        }
    }

    /** 翻译结果：成功时 texts 与入参一一对应 */
    public static final class Result {
        public final boolean ok;
        public final List<String> texts;
        public final String error;

        Result(boolean ok, List<String> texts, String error) {
            this.ok = ok;
            this.texts = texts == null ? new ArrayList<>() : texts;
            this.error = error;
        }
    }

    private BingTranslate() {
    }

    /* ------------------------------------------------------------------
     * 语言
     * ------------------------------------------------------------------ */

    public static Lang byCode(String code) {
        if (code != null) {
            for (Lang l : LANGS) {
                if (l.code.equalsIgnoreCase(code.trim())) return l;
            }
        }
        return LANGS[0];
    }

    /** 语言代码对应的中文显示名 */
    public static String targetName(String code) {
        return byCode(code).name;
    }

    /** 取语言主标签：zh-Hans → zh，en-US → en */
    public static String primary(String code) {
        if (code == null) return "";
        String s = code.trim().toLowerCase();
        int dash = s.indexOf('-');
        if (dash > 0) s = s.substring(0, dash);
        int underscore = s.indexOf('_');
        if (underscore > 0) s = s.substring(0, underscore);
        return s;
    }

    /** 两种语言代码是否属于同一语言（不区分地区，如 zh-Hans 与 zh-TW 视为同一种） */
    public static boolean sameLanguage(String a, String b) {
        String pa = primary(a), pb = primary(b);
        return !pa.isEmpty() && pa.equals(pb);
    }

    public static boolean isChinese(String code) {
        return "zh".equals(primary(code));
    }

    private static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF)
                || (c >= 0xF900 && c <= 0xFAFF);
    }

    /** 文本是否以中日韩文字为主：用于译向判断 */
    public static boolean looksChinese(String text) {
        if (text == null) return false;
        int cjk = 0, letters = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (isCjk(c)) cjk++;
            }
        }
        return letters > 0 && cjk * 2 > letters;
    }

    /** 自动判断译文方向：中文 → 英语，其余 → 默认目标语言 */
    public static String autoTarget(String text) {
        return looksChinese(text) ? "en" : LANGS[0].code;
    }

    /**
     * 划词翻译的译向：若选中文字已经是目标语言，则反向翻译成英语 / 中文，
     * 避免出现"中译中"这种没有意义的请求。
     */
    public static String selectionTarget(String text, String preferred) {
        String target = (preferred == null || preferred.trim().isEmpty())
                ? LANGS[0].code : preferred.trim();
        String p = primary(target);
        boolean chinese = looksChinese(text);
        if (chinese && "zh".equals(p)) return "en";
        if (!chinese && "en".equals(p)) return LANGS[0].code;
        return target;
    }

    /* ------------------------------------------------------------------
     * 请求构造与响应解析（纯函数，便于测试）
     * ------------------------------------------------------------------ */

    /** 接口地址：主机跟随翻译页重定向结果，国内为 cn.bing.com */
    public static String endpoint(String host) {
        String h = host == null || host.trim().isEmpty() ? "cn.bing.com" : host.trim();
        return "https://" + h + API_PATH;
    }

    /** 带 IG / IID 的完整请求地址 */
    public static String endpoint(String host, String ig) {
        String base = endpoint(host);
        String id = ig == null || ig.trim().isEmpty() ? "" : ig.trim();
        return base + "?isVertical=1&IG=" + encode(id) + "&IID=" + encode(IID);
    }

    /** 从翻译页 HTML 中取请求 key（毫秒时间戳串） */
    public static String parseKey(String html) {
        Matcher m = P_KEY.matcher(html == null ? "" : html);
        return m.find() ? m.group(1) : "";
    }

    /** 从翻译页 HTML 中取请求 token */
    public static String parseToken(String html) {
        Matcher m = P_KEY.matcher(html == null ? "" : html);
        return m.find() ? m.group(2) : "";
    }

    /** 从翻译页 HTML 中取 IG */
    public static String parseIg(String html) {
        Matcher m = P_IG.matcher(html == null ? "" : html);
        return m.find() ? m.group(1) : "";
    }

    /** 解析凭据；缺少任意一项返回 null */
    public static Creds parseCredentials(String html, String host) {
        Creds c = new Creds(host, parseKey(html), parseToken(html), parseIg(html));
        return c.valid() ? c : null;
    }

    /** 按条数与字符数把段落分组，保证每组一次请求即可完成 */
    public static List<List<String>> chunk(List<String> texts) {
        List<List<String>> groups = new ArrayList<>();
        if (texts == null || texts.isEmpty()) return groups;
        List<String> current = new ArrayList<>();
        int chars = 0;
        for (String raw : texts) {
            String t = raw == null ? "" : raw;
            if (!current.isEmpty()
                    && (current.size() >= MAX_BATCH || chars + t.length() + 1 > MAX_CHARS)) {
                groups.add(current);
                current = new ArrayList<>();
                chars = 0;
            }
            current.add(t);
            chars += t.length() + 1;
        }
        if (!current.isEmpty()) groups.add(current);
        return groups;
    }

    /** 多段文本以换行拼接成一次请求的文本 */
    public static String joinBatch(List<String> texts) {
        StringBuilder sb = new StringBuilder();
        if (texts != null) {
            for (int i = 0; i < texts.size(); i++) {
                if (i > 0) sb.append('\n');
                String t = texts.get(i) == null ? "" : texts.get(i);
                sb.append(t.replace('\n', ' ').replace('\r', ' '));
            }
        }
        return sb.toString();
    }

    /** 把整段译文按行拆回；行数与预期不符返回 null（由调用方退化为逐段请求） */
    public static List<String> splitBatch(String translated, int expected) {
        if (translated == null || expected <= 0) return null;
        String[] lines = translated.split("\n", -1);
        if (lines.length != expected) return null;
        List<String> out = new ArrayList<>();
        for (String line : lines) out.add(line.trim());
        return out;
    }

    /** 解析译文数组，缺失项以空串占位 */
    public static List<String> parseTranslations(String json) {
        List<String> out = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                String text = "";
                if (item != null) {
                    JSONArray tr = item.optJSONArray("translations");
                    if (tr != null && tr.length() > 0) {
                        JSONObject first = tr.optJSONObject(0);
                        if (first != null) text = first.optString("text", "");
                    }
                }
                out.add(text);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** 服务端错误转为可读提示 */
    public static String friendlyError(int code, String body) {
        String server = null;
        if (body != null && !body.trim().isEmpty()) {
            try {
                JSONObject o = new JSONObject(body);
                server = o.optString("errorMessage", null);
                if (server == null || server.isEmpty()) {
                    JSONObject err = o.optJSONObject("error");
                    if (err != null) server = err.optString("message", null);
                }
                if (server == null || server.isEmpty()) server = o.optString("message", null);
                if (server == null || server.isEmpty()) {
                    int sc = o.optInt("statusCode", 0);
                    if (sc > 0) code = sc;
                }
            } catch (Exception ignored) {
            }
        }
        String prefix;
        switch (code) {
            case 400:
                prefix = "翻译请求有误";
                break;
            case 401:
            case 403:
            case 205:
                prefix = "翻译服务鉴权失败，请稍后重试";
                break;
            case 408:
            case 504:
                prefix = "翻译服务响应超时，请稍后重试";
                break;
            case 429:
                prefix = "翻译请求过于频繁，请稍后再试";
                break;
            case 500:
            case 502:
            case 503:
                prefix = "翻译服务暂时不可用，请稍后重试";
                break;
            default:
                prefix = "翻译失败（HTTP " + code + "）";
                break;
        }
        if (server != null && !server.trim().isEmpty()
                && !server.contains("API") && !server.contains("key")) {
            return prefix + "：" + server.trim();
        }
        return prefix;
    }

    static String networkError(Exception e) {
        if (e instanceof UnknownHostException) {
            return "网络不可用，无法连接必应翻译";
        }
        if (e instanceof SocketTimeoutException) {
            return "连接必应翻译超时，请检查网络后重试";
        }
        String msg = e.getMessage();
        return "翻译失败：" + (msg == null || msg.isEmpty() ? e.getClass().getSimpleName() : msg);
    }

    private static String encode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return s;
        }
    }

    /* ------------------------------------------------------------------
     * 网络请求
     * ------------------------------------------------------------------ */

    /** 取请求凭据，force 为 true 时强制刷新 */
    public static synchronized Creds credentials(boolean force) throws Exception {
        long now = System.currentTimeMillis();
        if (!force && cachedCreds != null && cachedCreds.valid()
                && now - cachedAt < CRED_TTL) {
            return cachedCreds;
        }
        HttpURLConnection conn = (HttpURLConnection) new URL(PAGE_URL).openConnection();
        try {
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            int code = conn.getResponseCode();
            String host = conn.getURL() == null ? "" : conn.getURL().getHost();
            if (code != 200) {
                throw new IllegalStateException("翻译服务不可用（HTTP " + code + "）");
            }
            Creds c = parseCredentials(readAll(conn.getInputStream()), host);
            if (c == null) {
                throw new IllegalStateException("翻译服务凭据获取失败，请稍后重试");
            }
            cachedCreds = c;
            cachedAt = System.currentTimeMillis();
            return c;
        } finally {
            conn.disconnect();
        }
    }

    /** 清空本地凭据缓存（调试与测试用） */
    public static synchronized void clearCredentials() {
        cachedCreds = null;
        cachedAt = 0L;
    }

    /** 批量翻译；返回结果与入参顺序一一对应 */
    public static Result translate(List<String> texts, String from, String to) {
        if (texts == null || texts.isEmpty()) {
            return new Result(true, new ArrayList<>(), null);
        }
        List<String> out = new ArrayList<>();
        List<List<String>> groups = chunk(texts);
        try {
            Creds creds = credentials(false);
            for (List<String> group : groups) {
                List<String> part = translateGroup(group, from, to, creds);
                if (part == null) {
                    // 凭据失效：刷新后重试一次
                    creds = credentials(true);
                    part = translateGroup(group, from, to, creds);
                }
                if (part == null) {
                    return new Result(false, new ArrayList<>(),
                            "翻译服务鉴权失败，请稍后重试");
                }
                out.addAll(part);
            }
            return new Result(true, out, null);
        } catch (IllegalStateException e) {
            return new Result(false, new ArrayList<>(),
                    e.getMessage() == null ? "翻译失败，请稍后重试" : e.getMessage());
        } catch (Exception e) {
            return new Result(false, new ArrayList<>(), networkError(e));
        }
    }

    /** 单段文本翻译（保留原有换行） */
    public static Result translateOne(String text, String from, String to) {
        if (text == null || text.trim().isEmpty()) {
            return new Result(true, Collections.singletonList(""), null);
        }
        try {
            Creds creds = credentials(false);
            Reply reply = post(creds, text, from, to);
            if (reply.code == 401 || reply.code == 403 || reply.authFailed()) {
                creds = credentials(true);
                reply = post(creds, text, from, to);
            }
            if (reply.code == 401 || reply.code == 403 || reply.authFailed()) {
                return new Result(false, new ArrayList<>(), "翻译服务鉴权失败，请稍后重试");
            }
            if (reply.code != 200) {
                return new Result(false, new ArrayList<>(),
                        friendlyError(reply.code, reply.body));
            }
            List<String> parsed = parseTranslations(reply.body);
            if (parsed.isEmpty()) {
                return new Result(false, new ArrayList<>(), "翻译结果解析失败，请稍后重试");
            }
            return new Result(true, Collections.singletonList(parsed.get(0)), null);
        } catch (Exception e) {
            return new Result(false, new ArrayList<>(), networkError(e));
        }
    }

    /**
     * 翻译一组段落：优先合并成一次请求，行数对不上时退化为逐段请求。
     * 返回 null 表示鉴权失败（调用方刷新凭据后重试）。
     */
    private static List<String> translateGroup(List<String> group, String from, String to,
                                               Creds creds) throws Exception {
        String joined = joinBatch(group);
        Reply reply = post(creds, joined, from, to);
        if (reply.code == 401 || reply.code == 403 || reply.authFailed()) return null;
        if (reply.code != 200) {
            throw new IllegalStateException(friendlyError(reply.code, reply.body));
        }
        List<String> parsed = parseTranslations(reply.body);
        if (parsed.isEmpty()) {
            throw new IllegalStateException("翻译结果解析失败，请稍后重试");
        }
        List<String> lines = group.size() == 1
                ? Collections.singletonList(parsed.get(0))
                : splitBatch(parsed.get(0), group.size());
        if (lines != null) return lines;
        // 行数不一致：逐段请求，保证与原文一一对应
        List<String> out = new ArrayList<>();
        for (String s : group) {
            Reply one = post(creds, s, from, to);
            if (one.code == 401 || one.code == 403 || one.authFailed()) return null;
            if (one.code != 200) {
                throw new IllegalStateException(friendlyError(one.code, one.body));
            }
            List<String> p = parseTranslations(one.body);
            out.add(p.isEmpty() ? s : p.get(0));
        }
        return out;
    }

    private static final class Reply {
        final int code;
        final String body;

        Reply(int code, String body) {
            this.code = code;
            this.body = body;
        }

        /** 接口偶尔以 200 返回 {"statusCode":205,...} 表示凭据失效 */
        boolean authFailed() {
            if (body == null) return false;
            String b = body.trim();
            if (!b.startsWith("{")) return false;
            try {
                int sc = new JSONObject(b).optInt("statusCode", 0);
                return sc == 205 || sc == 401 || sc == 403;
            } catch (Exception e) {
                return false;
            }
        }
    }

    private static Reply post(Creds creds, String text, String from, String to) throws Exception {
        String target = (to == null || to.trim().isEmpty()) ? LANGS[0].code : to.trim();
        String source = (from == null || from.trim().isEmpty()) ? "auto-detect" : from.trim();
        StringBuilder body = new StringBuilder();
        body.append("fromLang=").append(encode(source));
        body.append("&text=").append(encode(text == null ? "" : text));
        body.append("&to=").append(encode(target));
        body.append("&token=").append(encode(creds.token));
        body.append("&key=").append(encode(creds.key));

        HttpURLConnection conn = (HttpURLConnection)
                new URL(endpoint(creds.host, creds.ig)).openConnection();
        try {
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(25000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Referer", "https://" + creds.host + "/translator");
            conn.setRequestProperty("Origin", "https://" + creds.host);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
                os.flush();
            }
            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 300
                    ? conn.getInputStream() : conn.getErrorStream();
            return new Reply(code, in == null ? "" : readAll(in));
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
