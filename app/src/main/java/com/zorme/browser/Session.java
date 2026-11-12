package com.zorme.browser;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 浏览会话的保存与恢复（不包含无痕标签） */
public class Session {

    private static final String NAME = "zorme_session";
    private static final String KEY_TABS = "tabs";
    private static final String KEY_CURRENT = "current";

    public static class Item {
        public String url = "";
        public String title = "";
    }

    public static void save(Context c) {
        JSONArray arr = new JSONArray();
        TabManager tm = TabManager.get();
        int current = 0;
        int realIndex = 0;
        for (int i = 0; i < tm.tabs.size(); i++) {
            BrowserTab t = tm.tabs.get(i);
            if (t.incognito) continue;
            if (i == tm.current) current = realIndex;
            JSONObject o = new JSONObject();
            try {
                o.put("url", t.showingHome ? "" : t.url);
                o.put("title", t.title);
            } catch (Exception ignored) {
            }
            arr.put(o);
            realIndex++;
        }
        SharedPreferences sp = c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_TABS, arr.toString()).putInt(KEY_CURRENT, current).apply();
    }

    public static List<Item> restore(Context c) {
        List<Item> list = new ArrayList<>();
        SharedPreferences sp = c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_TABS, null);
        if (json == null) return list;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Item item = new Item();
                item.url = o.optString("url", "");
                item.title = o.optString("title", "");
                list.add(item);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static int savedCurrent(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt(KEY_CURRENT, 0);
    }

    public static void clear(Context c) {
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().clear().apply();
    }
}
