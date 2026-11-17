package com.zorme.browser;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 网页翻译：把页面正文按文本节点切分，交给 {@link BingTranslate} 批量翻译后写回原位。
 *
 * <p>翻译不刷新页面、不改变布局：只替换文本节点的内容，原始文本保存在页面内的
 * {@code window.__zormeTr} 中，可随时一键还原。</p>
 */
public class PageTranslator {

    /** 单页最多翻译的文本节点数，避免超长页面产生过多请求 */
    public static final int MAX_NODES = 600;

    /** 每批发送的文本条数 */
    public static final int PAGE_SIZE = 40;

    public interface Listener {
        void onProgress(int done, int total);

        void onFinished(int translated, int total);

        void onError(String message);
    }

    public interface TextCallback {
        void onText(String text);
    }

    private final WebView webView;
    private final String toLang;
    private final Handler main = new Handler(Looper.getMainLooper());

    private Listener listener;
    private boolean running;
    private boolean cancelled;
    private int cursor;
    private int total;
    private int translated;

    public PageTranslator(WebView webView, String toLang) {
        this.webView = webView;
        this.toLang = toLang == null || toLang.trim().isEmpty()
                ? BingTranslate.LANGS[0].code : toLang.trim();
    }

    public boolean running() {
        return running;
    }

    public String targetLanguage() {
        return toLang;
    }

    /** 开始翻译；重复调用会被忽略，需先 stop() */
    public void start(Listener l) {
        if (webView == null || running) return;
        listener = l;
        running = true;
        cancelled = false;
        cursor = 0;
        total = 0;
        translated = 0;
        step();
    }

    /** 停止翻译（已写入的译文保留） */
    public void stop() {
        cancelled = true;
        running = false;
    }

    private void step() {
        if (cancelled || !running) return;
        webView.evaluateJavascript(jsCollect(cursor, PAGE_SIZE), value -> {
            if (cancelled || !running) return;
            JSONObject obj = parseObject(value);
            if (obj == null) {
                fail("未能读取网页内容");
                return;
            }
            total = obj.optInt("total", 0);
            int next = obj.optInt("next", cursor);
            List<String> batch = new ArrayList<>();
            JSONArray arr = obj.optJSONArray("texts");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String t = arr.optString(i, "");
                    if (!t.isEmpty()) batch.add(t);
                }
            }
            if (total <= 0 || batch.isEmpty() || next <= cursor) {
                finish();
                return;
            }
            final int start = cursor;
            new Thread(() -> {
                BingTranslate.Result result = BingTranslate.translate(batch, "", toLang);
                main.post(() -> {
                    if (cancelled || !running) return;
                    if (!result.ok) {
                        fail(result.error);
                        return;
                    }
                    apply(start, result.texts, next);
                });
            }, "zorme-page-translate").start();
        });
    }

    private void apply(int start, List<String> texts, int next) {
        webView.evaluateJavascript(jsApply(start, texts), value -> {
            if (cancelled || !running) return;
            translated += texts.size();
            cursor = next;
            if (listener != null) listener.onProgress(cursor, total);
            if (cursor >= total || cursor >= MAX_NODES) {
                finish();
            } else {
                step();
            }
        });
    }

    private void finish() {
        if (cancelled) return;
        running = false;
        if (listener != null) listener.onFinished(translated, total);
    }

    private void fail(String message) {
        running = false;
        if (listener != null) {
            listener.onError(message == null || message.isEmpty() ? "翻译失败，请稍后重试" : message);
        }
    }

    /* ------------------------------------------------------------------
     * 复用方法：还原原文、读取选中文字、识别页面语言
     * ------------------------------------------------------------------ */

    /** 还原为原文（若页面未翻译则无副作用） */
    public static void restore(WebView webView) {
        if (webView == null) return;
        webView.evaluateJavascript(JS_RESTORE, null);
    }

    /** 读取用户当前选中的文字 */
    public static void selectedText(WebView webView, TextCallback callback) {
        if (webView == null || callback == null) return;
        webView.evaluateJavascript(JS_SELECTION,
                value -> callback.onText(decodeJsString(value)));
    }

    /** 读取页面的 lang 声明，识别不到时回调空串 */
    public static void detectLanguage(WebView webView, TextCallback callback) {
        if (webView == null || callback == null) return;
        webView.evaluateJavascript(JS_LANGUAGE,
                value -> callback.onText(decodeJsString(value)));
    }

    /* ------------------------------------------------------------------
     * 注入脚本（静态方法，便于单元测试断言）
     * ------------------------------------------------------------------ */

    /** 收集第 from 条起的文本节点，返回 {total,start,next,texts} */
    public static String jsCollect(int from, int size) {
        int start = Math.max(0, from);
        int limit = Math.max(1, size);
        return "(function(){try{"
                + "var S=window.__zormeTr;"
                + "if(!S){S=window.__zormeTr={nodes:[],orig:[],ready:false};}"
                + "if(!S.ready){S.ready=true;"
                + "var skip={SCRIPT:1,STYLE:1,NOSCRIPT:1,TEXTAREA:1,CODE:1,PRE:1,SVG:1,CANVAS:1,IFRAME:1,HEAD:1,TITLE:1};"
                + "var root=document.body||document.documentElement;"
                + "if(root){var w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,null,false);var n;"
                + "while((n=w.nextNode())&&S.nodes.length<" + MAX_NODES + "){"
                + "var p=n.parentNode;if(!p)continue;"
                + "var tag=(p.nodeName||'').toUpperCase();if(skip[tag])continue;"
                + "if(p.isContentEditable)continue;"
                + "if(p.closest&&p.closest('[contenteditable]'))continue;"
                + "var t=n.nodeValue;if(!t)continue;var s=t.trim();if(s.length<2)continue;"
                + "if(!/[A-Za-z\\u00C0-\\u024F\\u0400-\\u04FF\\u3040-\\u30FF\\uAC00-\\uD7AF]/.test(s))continue;"
                + "S.nodes.push(n);S.orig.push(t);}}}"
                + "var out=[];var total=S.nodes.length;var start=" + start + ";"
                + "for(var i=start;i<total&&out.length<" + limit + ";i++){"
                + "out.push(S.orig[i].trim().replace(/\\s+/g,' '));}"
                + "return JSON.stringify({total:total,start:start,next:start+out.length,texts:out});"
                + "}catch(e){return JSON.stringify({total:0,start:0,next:0,texts:[],error:String(e)});}})()";
    }

    /** 把译文写回第 start 条起的文本节点 */
    public static String jsApply(int start, List<String> texts) {
        JSONArray arr = new JSONArray();
        if (texts != null) {
            for (String t : texts) arr.put(t == null ? "" : t);
        }
        // U+2028 / U+2029 在 JSON 中合法，但在旧版 JS 字符串字面量里非法，需转义
        String literal = arr.toString()
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029");
        return "(function(){var S=window.__zormeTr;if(!S)return 0;var a=" + literal
                + ";var start=" + Math.max(0, start) + ";var c=0;"
                + "for(var i=0;i<a.length;i++){var n=S.nodes[start+i];"
                + "if(n){try{n.nodeValue=a[i];c++;}catch(e){}}}"
                + "return c;})()";
    }

    /** 还原全部文本节点 */
    public static final String JS_RESTORE = "(function(){var S=window.__zormeTr;if(!S)return 0;"
            + "var c=0;for(var i=0;i<S.nodes.length;i++){var n=S.nodes[i];"
            + "if(n){try{n.nodeValue=S.orig[i];c++;}catch(e){}}}"
            + "S.nodes=[];S.orig=[];S.ready=false;return c;})()";

    /** 读取当前选中文字 */
    public static final String JS_SELECTION = "(function(){try{var s=window.getSelection();"
            + "if(!s)return '';return String(s).trim();}catch(e){return '';}})()";

    /** 读取页面语言声明 */
    public static final String JS_LANGUAGE = "(function(){try{var h=document.documentElement;"
            + "var l=(h.getAttribute&&h.getAttribute('lang'))||h.lang||'';"
            + "if(!l){var m=document.querySelector('meta[http-equiv=\"content-language\"]');"
            + "if(m)l=m.getAttribute('content')||'';}"
            + "return String(l).trim();}catch(e){return '';}})()";

    /** 解析 evaluateJavascript 返回的 JSON 字符串 */
    public static JSONObject parseObject(String value) {
        String raw = decodeJsString(value);
        if (raw.isEmpty()) return null;
        try {
            return new JSONObject(raw);
        } catch (Exception e) {
            return null;
        }
    }

    /** 还原 JS 字符串字面量；null / 非字符串返回空串 */
    public static String decodeJsString(String value) {
        if (value == null || "null".equals(value) || value.length() < 2) return "";
        try {
            return new JSONArray("[" + value + "]").getString(0);
        } catch (Exception e) {
            return "";
        }
    }
}
