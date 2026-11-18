package com.zorme.browser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 油猴（userscript）脚本模型。
 * 解析 ==UserScript== 元数据块，支持 @name/@namespace/@version/@description/@author/
 * @match/@include/@exclude/@grant/@run-at，并提供 URL 匹配能力。
 */
public class Userscript {

    public String id;
    public String name = "";
    public String namespace = "";
    public String version = "";
    public String description = "";
    public String author = "";
    public String runAt = "document-end"; // document-start / document-end / document-idle
    public String sourceUrl = "";
    public String code = "";
    public boolean enabled = true;
    public long created;

    public final List<String> matches = new ArrayList<>();
    public final List<String> includes = new ArrayList<>();
    public final List<String> excludes = new ArrayList<>();

    public Userscript() {
    }

    /** 脚本存储命名空间：GM_* API 的数据按脚本隔离 */
    public String gmKey() {
        String ns = namespace == null || namespace.isEmpty() ? "zorme" : namespace;
        return ns + "|" + (name == null ? "" : name);
    }

    /** 从脚本源码解析元数据块，返回解析后的模型 */
    public static Userscript parse(String code) {
        Userscript s = new Userscript();
        s.code = code == null ? "" : code;
        String head = s.metaBlock(s.code);
        if (head != null) {
            for (String raw : head.split("\n")) {
                String line = raw.trim();
                if (!line.startsWith("@")) continue;
                String[] parts = line.split("\\s+", 3);
                if (parts.length < 2) continue;
                String key = parts[0].substring(1);
                // @name/@description 等值可能含空格，直接拼回完整值
                String value = parts.length == 3
                        ? (parts[1] + " " + parts[2]).trim() : parts[1];
                switch (key) {
                    case "name":
                        s.name = value;
                        break;
                    case "namespace":
                        s.namespace = value;
                        break;
                    case "version":
                        s.version = value;
                        break;
                    case "description":
                        s.description = value;
                        break;
                    case "author":
                        s.author = value;
                        break;
                    case "run-at":
                        s.runAt = value;
                        break;
                    case "match":
                        s.matches.add(value);
                        break;
                    case "include":
                        s.includes.add(value);
                        break;
                    case "exclude":
                        s.excludes.add(value);
                        break;
                    default:
                        break;
                }
            }
        }
        if (s.name == null || s.name.isEmpty()) s.name = guessName(s.code, s.sourceUrl);
        if (s.runAt == null || s.runAt.isEmpty()) s.runAt = "document-end";
        if (!"document-start".equals(s.runAt) && !"document-end".equals(s.runAt)
                && !"document-idle".equals(s.runAt)) {
            s.runAt = "document-end";
        }
        return s;
    }

    private String metaBlock(String code) {
        int start = code.indexOf("==UserScript==");
        if (start < 0) return null;
        int end = code.indexOf("==/UserScript==", start);
        if (end < 0) end = Math.min(code.length(), start + 4000);
        String block = code.substring(start, end);
        // 去掉行首注释符
        return block.replaceAll("(?m)^\\s*(//|/\\*|\\*)\\s?", "");
    }

    private static String guessName(String code, String sourceUrl) {
        if (sourceUrl != null && !sourceUrl.isEmpty()) {
            String u = sourceUrl;
            int slash = u.lastIndexOf('/');
            if (slash >= 0) u = u.substring(slash + 1);
            if (u.toLowerCase().endsWith(".user.js")) u = u.substring(0, u.length() - 8);
            if (!u.isEmpty()) return u;
        }
        return "未命名脚本";
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name);
            o.put("namespace", namespace);
            o.put("version", version);
            o.put("description", description);
            o.put("author", author);
            o.put("runAt", runAt);
            o.put("sourceUrl", sourceUrl);
            o.put("code", code);
            o.put("enabled", enabled);
            o.put("created", created);
            JSONArray m = new JSONArray(), i = new JSONArray(), e = new JSONArray();
            for (String x : matches) m.put(x);
            for (String x : includes) i.put(x);
            for (String x : excludes) e.put(x);
            o.put("matches", m);
            o.put("includes", i);
            o.put("excludes", e);
            return o;
        } catch (Exception ex) {
            return new JSONObject();
        }
    }

    public static Userscript fromJson(JSONObject o) {
        Userscript s = new Userscript();
        s.id = o.optString("id", "");
        s.name = o.optString("name", "");
        s.namespace = o.optString("namespace", "");
        s.version = o.optString("version", "");
        s.description = o.optString("description", "");
        s.author = o.optString("author", "");
        s.runAt = o.optString("runAt", "document-end");
        s.sourceUrl = o.optString("sourceUrl", "");
        s.code = o.optString("code", "");
        s.enabled = o.optBoolean("enabled", true);
        s.created = o.optLong("created", 0);
        JSONArray m = o.optJSONArray("matches");
        if (m != null) for (int i = 0; i < m.length(); i++) s.matches.add(m.optString(i));
        JSONArray inc = o.optJSONArray("includes");
        if (inc != null) for (int i = 0; i < inc.length(); i++) s.includes.add(inc.optString(i));
        JSONArray exc = o.optJSONArray("excludes");
        if (exc != null) for (int i = 0; i < exc.length(); i++) s.excludes.add(exc.optString(i));
        return s;
    }

    /** 是否对该 URL 生效（@match + @include 命中且未被 @exclude 排除） */
    public boolean matchesUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        for (String ex : excludes) {
            if (includeMatch(url, ex)) return false;
        }
        boolean hit = false;
        for (String m : matches) {
            if (matchPattern(url, m)) {
                hit = true;
                break;
            }
        }
        if (!hit) {
            for (String inc : includes) {
                if (includeMatch(url, inc)) {
                    hit = true;
                    break;
                }
            }
        }
        return hit;
    }

    /**
     * Chrome 扩展风格的 @match 规则：
     * <scheme>://<host><path>，scheme 支持 http/https/file/ftp/*，
     * host 支持 *（任意）与 *.example.com，path 支持 * 通配。
     */
    public static boolean matchPattern(String url, String pattern) {
        if (pattern == null || pattern.isEmpty()) return false;
        if ("<all_urls>".equals(pattern.trim())) {
            return url.startsWith("http://") || url.startsWith("https://")
                    || url.startsWith("file://") || url.startsWith("ftp://");
        }
        try {
            java.net.URI uri = java.net.URI.create(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            String path = uri.getRawPath() == null || uri.getRawPath().isEmpty()
                    ? "/" : uri.getRawPath();
            String query = uri.getRawQuery();
            String full = path + (query != null ? "?" + query : "");

            int schemeEnd = pattern.indexOf("://");
            if (schemeEnd < 0) return false;
            String pScheme = pattern.substring(0, schemeEnd).toLowerCase();
            String rest = pattern.substring(schemeEnd + 3);

            if (!schemeMatches(pScheme, scheme)) return false;

            int pathStart = rest.indexOf('/');
            String pHost = pathStart < 0 ? rest : rest.substring(0, pathStart);
            String pPath = pathStart < 0 ? "/" : rest.substring(pathStart);

            return hostMatches(pHost.toLowerCase(), host) && globMatches(pPath, full);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean schemeMatches(String pattern, String scheme) {
        if ("*".equals(pattern)) {
            return "http".equals(scheme) || "https".equals(scheme)
                    || "ws".equals(scheme) || "wss".equals(scheme) || "ftp".equals(scheme)
                    || "file".equals(scheme);
        }
        return pattern.equals(scheme);
    }

    private static boolean hostMatches(String pattern, String host) {
        if ("*".equals(pattern)) return true;
        if (pattern.startsWith("*.")) {
            String suffix = pattern.substring(2);
            return host.equals(suffix) || host.endsWith("." + suffix);
        }
        return pattern.equals(host);
    }

    /** @include / @exclude 规则：支持 /regex/ 与 glob（* ? 通配） */
    public static boolean includeMatch(String url, String rule) {
        if (rule == null || rule.isEmpty()) return false;
        rule = rule.trim();
        if (rule.length() > 2 && rule.startsWith("/") && rule.endsWith("/")) {
            try {
                return Pattern.compile(rule.substring(1, rule.length() - 1))
                        .matcher(url).find();
            } catch (Exception e) {
                return false;
            }
        }
        String glob = rule;
        if (glob.startsWith("*://")) glob = "*" + glob.substring(4);
        return globMatches(glob, url);
    }

    /** glob 匹配：* 任意字符（含空），? 单个字符，其余按字面量 */
    public static boolean globMatches(String pattern, String text) {
        if (pattern == null) return false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            switch (c) {
                case '*':
                    sb.append(".*");
                    break;
                case '?':
                    sb.append('.');
                    break;
                case '.':
                case '(':
                case ')':
                case '[':
                case ']':
                case '{':
                case '}':
                case '+':
                case '^':
                case '$':
                case '|':
                case '\\':
                    sb.append('\\').append(c);
                    break;
                default:
                    sb.append(c);
            }
        }
        try {
            return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE).matcher(text).matches();
        } catch (Exception e) {
            return false;
        }
    }
}
