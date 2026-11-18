package com.zorme.browser;

import android.content.Context;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 油猴脚本注入器：把启用且匹配当前页面的脚本包装上 GM_* API 后注入 WebView。
 * document-start 的脚本在 onPageStarted 注入，其余在 onPageFinished 注入。
 */
public final class UserscriptInjector {

    private UserscriptInjector() {
    }

    /** 按时机注入匹配的脚本 */
    public static void inject(Context ctx, WebView wv, String url, boolean atStart) {
        if (wv == null || url == null) return;
        List<Userscript> list = UserscriptStore.get(ctx).enabledFor(url);
        if (list.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (Userscript s : list) {
            boolean isStart = "document-start".equals(s.runAt);
            if (atStart != isStart) continue;
            sb.append(build(ctx, s));
        }
        String js = sb.toString().trim();
        if (js.isEmpty()) return;
        wv.evaluateJavascript(js, null);
    }

    /** 为单个脚本生成完整注入代码（GM 垫片 + 用户代码） */
    public static String build(Context ctx, Userscript s) {
        StringBuilder sb = new StringBuilder();
        sb.append("(function(){\n");
        sb.append("try{\n");
        appendShim(sb, ctx, s);
        sb.append(s.code == null ? "" : s.code);
        sb.append("\n}catch(e){console.error('[Zorme油猴] " + escapeJs(s.name) + ":',e)}\n");
        sb.append("})();\n");
        return sb.toString();
    }

    private static void appendShim(StringBuilder sb, Context ctx, Userscript s) {
        // 预读 GM 数据快照（页面级），写入走原生持久化
        Map<String, String> data = UserscriptStore.get(ctx).gmDataFor(s.gmKey());
        StringBuilder dataJson = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : data.entrySet()) {
            if (!first) dataJson.append(',');
            first = false;
            dataJson.append('"').append(escapeJs(e.getKey())).append("\":\"")
                    .append(escapeJs(e.getValue())).append('"');
        }
        dataJson.append('}');

        sb.append("var __gmData=").append(dataJson).append(";\n");
        sb.append("var __gmNs=").append('"').append(escapeJs(s.gmKey())).append("\";\n");
        sb.append("var GM_info={script:{name:").append(quote(s.name))
                .append(",version:").append(quote(s.version))
                .append(",namespace:").append(quote(s.namespace))
                .append(",description:").append(quote(s.description))
                .append(",author:").append(quote(s.author))
                .append(",matches:").append(jsonArr(s.matches))
                .append("},version:'1.0',scriptHandler:'Zorme Browser'};\n");
        sb.append("function GM_getValue(k,d){var v=__gmData[String(k)];return v===undefined?d:v;}\n");
        sb.append("function GM_setValue(k,v){__gmData[String(k)]=String(v);try{Zorme.gmSet(__gmNs,String(k),String(v))}catch(e){}}\n");
        sb.append("function GM_deleteValue(k){delete __gmData[String(k)];try{Zorme.gmDelete(__gmNs,String(k))}catch(e){}}\n");
        sb.append("function GM_listValues(){return Object.keys(__gmData);}\n");
        sb.append("function GM_addStyle(css){try{var st=document.createElement('style');st.textContent=css;(document.head||document.documentElement).appendChild(st)}catch(e){}}\n");
        sb.append("function GM_setClipboard(t){try{Zorme.setClipboard(String(t))}catch(e){}}\n");
        sb.append("function GM_notification(t){try{Zorme.toast(typeof t==='string'?t:(t&&t.text)||'油猴通知')}catch(e){}}\n");
        sb.append("function GM_openInTab(u){try{Zorme.openUrl(String(u))}catch(e){}}\n");
        sb.append("function GM_download(u){try{Zorme.download(String(u))}catch(e){}}\n");
        sb.append("function GM_xmlhttpRequest(d){var cb='c'+Date.now()+Math.floor(Math.random()*1e6);"
                + "if(!window.__zormeGmCbs)window.__zormeGmCbs={};"
                + "window.__zormeGmCbs[cb]=d||{};"
                + "var p={method:(d&&d.method)||'GET',url:(d&&d.url)||'',headers:(d&&d.headers)||{},"
                + "data:(d&&d.data)?String(d.data):'',timeout:(d&&d.timeout)||30000};"
                + "try{Zorme.gmXhr(cb,JSON.stringify(p))}catch(e){if(d&&d.onerror)setTimeout(function(){d.onerror({error:String(e),details:{}})},0)}"
                + "return{abort:function(){}};}\n");
        sb.append("window.__zormeGmXhrDone=function(cb,ok,payload){var d=window.__zormeGmCbs&&window.__zormeGmCbs[cb];"
                + "if(!d)return;delete window.__zormeGmCbs[cb];"
                + "try{var r=JSON.parse(payload);if(ok&&d.onload)d.onload(r);else if(!ok&&d.onerror)d.onerror(r);}catch(e){}};\n");
        sb.append("var unsafeWindow=window;\n");
        sb.append("var GM={info:GM_info,getValue:GM_getValue,setValue:GM_setValue,deleteValue:GM_deleteValue,"
                + "listValues:GM_listValues,addStyle:GM_addStyle,setClipboard:GM_setClipboard,"
                + "notification:GM_notification,openInTab:GM_openInTab,download:GM_download,"
                + "xmlHttpRequest:GM_xmlhttpRequest};\n");
        sb.append("window.GM_info=GM_info;window.GM_getValue=GM_getValue;window.GM_setValue=GM_setValue;"
                + "window.GM_deleteValue=GM_deleteValue;window.GM_listValues=GM_listValues;"
                + "window.GM_addStyle=GM_addStyle;window.GM_setClipboard=GM_setClipboard;"
                + "window.GM_notification=GM_notification;window.GM_openInTab=GM_openInTab;"
                + "window.GM_download=GM_download;window.GM_xmlhttpRequest=GM_xmlhttpRequest;"
                + "window.unsafeWindow=window;window.GM=GM;\n");
    }

    // ------------------------------------------------------------------
    // GM_xmlhttpRequest 原生实现（由 Bridge 调用）
    // ------------------------------------------------------------------

    public static void handleXhr(MainActivity activity, String cbId, String detailsJson) {
        new Thread(() -> {
            String method = "GET", url = "", data = "";
            JSONObject headers = new JSONObject();
            int timeout = 30000;
            boolean ok = false;
            int status = 0;
            String statusText = "";
            StringBuilder body = new StringBuilder();
            StringBuilder respHeaders = new StringBuilder();
            try {
                JSONObject d = new JSONObject(detailsJson);
                method = d.optString("method", "GET").toUpperCase();
                url = d.optString("url", "");
                data = d.optString("data", "");
                headers = d.optJSONObject("headers") == null ? new JSONObject() : d.optJSONObject("headers");
                timeout = d.optInt("timeout", 30000);
            } catch (Exception ignored) {
            }
            try {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    throw new IllegalArgumentException("仅支持 http/https 请求");
                }
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod(method);
                conn.setConnectTimeout(Math.max(timeout, 5000));
                conn.setReadTimeout(Math.max(timeout, 5000));
                conn.setInstanceFollowRedirects(true);
                Iterator<String> keys = headers.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    conn.setRequestProperty(k, headers.optString(k, ""));
                }
                if (!("GET".equals(method) || "HEAD".equals(method)) && !data.isEmpty()) {
                    conn.setDoOutput(true);
                    byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
                    conn.getOutputStream().write(bytes);
                }
                status = conn.getResponseCode();
                statusText = conn.getResponseMessage();
                ok = status >= 200 && status < 400;
                Map<String, List<String>> hs = conn.getHeaderFields();
                if (hs != null) {
                    for (Map.Entry<String, List<String>> e : hs.entrySet()) {
                        if (e.getKey() == null) continue;
                        respHeaders.append(e.getKey()).append(": ")
                                .append(String.join(", ", e.getValue())).append("\n");
                    }
                }
                java.io.InputStream is = ok ? conn.getInputStream() : conn.getErrorStream();
                if (is != null) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    char[] buf = new char[4096];
                    int n;
                    while ((n = r.read(buf)) > 0) body.append(buf, 0, n);
                    r.close();
                }
                conn.disconnect();
            } catch (Exception e) {
                ok = false;
                statusText = String.valueOf(e.getMessage());
            }

            final boolean fOk = ok;
            final JSONObject payload = new JSONObject();
            try {
                payload.put("status", status);
                payload.put("statusText", statusText);
                payload.put("responseText", body.toString());
                payload.put("response", body.toString());
                payload.put("responseHeaders", respHeaders.toString());
                payload.put("finalUrl", url);
                payload.put("readyState", 4);
            } catch (Exception ignored) {
            }
            final String js = "window.__zormeGmXhrDone&&window.__zormeGmXhrDone('" + escapeJs(cbId) + "',"
                    + fOk + ",'" + escapeJs(payload.toString()) + "');";
            activity.runOnUiThread(() -> {
                BrowserTab t = activity.current();
                if (t != null && t.webView != null) {
                    t.webView.evaluateJavascript(js, null);
                }
            });
        }, "zorme-gm-xhr").start();
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static String jsonArr(List<String> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(quote(list.get(i)));
        }
        return sb.append("]").toString();
    }

    private static String quote(String s) {
        return "'" + escapeJs(s == null ? "" : s) + "'";
    }

    /** JS 字符串转义（用于包进单引号字面量） */
    public static String escapeJs(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\'':
                    sb.append("\\'");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '<':
                    // 防止 </script> 提前闭合
                    sb.append("\\u003c");
                    break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }
}
