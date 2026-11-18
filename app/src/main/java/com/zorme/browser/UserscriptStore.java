package com.zorme.browser;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 油猴脚本存储：脚本列表存 userscripts.json，GM_* 数据存 userscript_storage.json。
 * 全部保存在应用私有目录，无外部存储依赖。
 */
public class UserscriptStore {

    private static volatile UserscriptStore instance;

    public static UserscriptStore get(Context ctx) {
        if (instance == null) {
            synchronized (UserscriptStore.class) {
                if (instance == null) instance = new UserscriptStore(ctx.getApplicationContext());
            }
        }
        return instance;
    }

    private final Context app;
    private final List<Userscript> scripts = new ArrayList<>();
    /** GM 存储：脚本 gmKey -> (变量名 -> 值) */
    private final Map<String, Map<String, String>> gmData = new HashMap<>();

    private UserscriptStore(Context app) {
        this.app = app;
        loadScripts();
        loadStorage();
    }

    // ------------------------------------------------------------------
    // 脚本管理
    // ------------------------------------------------------------------

    public List<Userscript> all() {
        synchronized (scripts) {
            return new ArrayList<>(scripts);
        }
    }

    public int enabledCount() {
        int n = 0;
        for (Userscript s : all()) if (s.enabled) n++;
        return n;
    }

    public Userscript add(String code, String sourceUrl) {
        Userscript s = Userscript.parse(code);
        s.id = UUID.randomUUID().toString();
        s.sourceUrl = sourceUrl == null ? "" : sourceUrl;
        s.enabled = true;
        s.created = System.currentTimeMillis();
        synchronized (scripts) {
            scripts.add(s);
            saveScripts();
        }
        return s;
    }

    public void remove(String id) {
        synchronized (scripts) {
            Userscript target = null;
            for (Userscript s : scripts) {
                if (s.id.equals(id)) {
                    target = s;
                    break;
                }
            }
            if (target != null) {
                scripts.remove(target);
                saveScripts();
                gmData.remove(target.gmKey());
                saveStorage();
            }
        }
    }

    public void setEnabled(String id, boolean enabled) {
        synchronized (scripts) {
            for (Userscript s : scripts) {
                if (s.id.equals(id)) {
                    s.enabled = enabled;
                    saveScripts();
                    return;
                }
            }
        }
    }

    /** 命中 URL 且启用的脚本，按安装顺序返回 */
    public List<Userscript> enabledFor(String url) {
        List<Userscript> out = new ArrayList<>();
        if (url == null || url.isEmpty() || !url.startsWith("http")) return out;
        for (Userscript s : all()) {
            if (s.enabled && s.matchesUrl(url)) out.add(s);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // GM_* 数据
    // ------------------------------------------------------------------

    public Map<String, String> gmDataFor(String scriptKey) {
        synchronized (gmData) {
            Map<String, String> d = gmData.get(scriptKey);
            return d == null ? Collections.emptyMap() : d;
        }
    }

    public void gmSet(String scriptKey, String key, String value) {
        synchronized (gmData) {
            Map<String, String> d = gmData.get(scriptKey);
            if (d == null) {
                d = new HashMap<>();
                gmData.put(scriptKey, d);
            }
            d.put(key, value);
            saveStorage();
        }
    }

    public void gmDelete(String scriptKey, String key) {
        synchronized (gmData) {
            Map<String, String> d = gmData.get(scriptKey);
            if (d != null) {
                d.remove(key);
                saveStorage();
            }
        }
    }

    // ------------------------------------------------------------------
    // 持久化
    // ------------------------------------------------------------------

    private File scriptsFile() {
        return new File(app.getFilesDir(), "userscripts.json");
    }

    private File storageFile() {
        return new File(app.getFilesDir(), "userscript_storage.json");
    }

    private void loadScripts() {
        try {
            if (!scriptsFile().exists()) return;
            JSONArray arr = new JSONArray(readFile(scriptsFile()));
            synchronized (scripts) {
                scripts.clear();
                for (int i = 0; i < arr.length(); i++) {
                    scripts.add(Userscript.fromJson(arr.optJSONObject(i)));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void saveScripts() {
        try {
            JSONArray arr = new JSONArray();
            synchronized (scripts) {
                for (Userscript s : scripts) arr.put(s.toJson());
            }
            writeFile(scriptsFile(), arr.toString());
        } catch (Exception ignored) {
        }
    }

    private void loadStorage() {
        try {
            if (!storageFile().exists()) return;
            JSONObject root = new JSONObject(readFile(storageFile()));
            synchronized (gmData) {
                gmData.clear();
                Iterator<String> keys = root.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    JSONObject inner = root.optJSONObject(k);
                    if (inner == null) continue;
                    Map<String, String> d = new HashMap<>();
                    Iterator<String> ik = inner.keys();
                    while (ik.hasNext()) {
                        String ikk = ik.next();
                        d.put(ikk, inner.optString(ikk, ""));
                    }
                    gmData.put(k, d);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void saveStorage() {
        try {
            JSONObject root = new JSONObject();
            synchronized (gmData) {
                for (Map.Entry<String, Map<String, String>> e : gmData.entrySet()) {
                    JSONObject inner = new JSONObject();
                    for (Map.Entry<String, String> kv : e.getValue().entrySet()) {
                        inner.put(kv.getKey(), kv.getValue());
                    }
                    root.put(e.getKey(), inner);
                }
            }
            writeFile(storageFile(), root.toString());
        } catch (Exception ignored) {
        }
    }

    private String readFile(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[(int) f.length()];
            int read = in.read(buf);
            return new String(buf, 0, Math.max(read, 0), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    private void writeFile(File f, String content) throws Exception {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }
    }
}
