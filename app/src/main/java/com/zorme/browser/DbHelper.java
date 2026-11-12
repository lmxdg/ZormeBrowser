package com.zorme.browser;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地 SQLite 存储：书签、历史、下载记录。
 */
public class DbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "zorme.db";
    private static final int DB_VERSION = 1;

    private static DbHelper instance;

    public static synchronized DbHelper get(Context c) {
        if (instance == null) instance = new DbHelper(c.getApplicationContext());
        return instance;
    }

    private DbHelper(Context c) {
        super(c, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE bookmarks(" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT," +
                "url TEXT NOT NULL UNIQUE," +
                "created INTEGER)");
        db.execSQL("CREATE TABLE history(" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT," +
                "url TEXT NOT NULL," +
                "visited INTEGER," +
                "visits INTEGER DEFAULT 1)");
        db.execSQL("CREATE INDEX idx_history_visited ON history(visited DESC)");
        db.execSQL("CREATE TABLE downloads(" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT," +
                "url TEXT," +
                "path TEXT," +
                "size INTEGER," +
                "time INTEGER," +
                "download_id INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    // ---------------- bookmarks ----------------

    public boolean addBookmark(String title, String url) {
        if (url == null || url.isEmpty()) return false;
        ContentValues cv = new ContentValues();
        cv.put("title", title == null ? "" : title);
        cv.put("url", url);
        cv.put("created", System.currentTimeMillis());
        long r = getWritableDatabase().insertWithOnConflict("bookmarks", null, cv,
                SQLiteDatabase.CONFLICT_IGNORE);
        return r != -1;
    }

    public void updateBookmark(long id, String title, String url) {
        ContentValues cv = new ContentValues();
        cv.put("title", title == null ? "" : title);
        cv.put("url", url);
        getWritableDatabase().update("bookmarks", cv, "_id=?", new String[]{String.valueOf(id)});
    }

    public boolean isBookmarked(String url) {
        if (url == null) return false;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT _id FROM bookmarks WHERE url=? LIMIT 1", new String[]{url});
        boolean r = c.moveToFirst();
        c.close();
        return r;
    }

    public Record findBookmark(String url) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT _id,title,url,created FROM bookmarks WHERE url=? LIMIT 1", new String[]{url});
        Record r = null;
        if (c.moveToFirst()) {
            r = new Record();
            r.id = c.getLong(0);
            r.title = c.getString(1);
            r.url = c.getString(2);
            r.time = c.getLong(3);
        }
        c.close();
        return r;
    }

    public void deleteBookmark(long id) {
        getWritableDatabase().delete("bookmarks", "_id=?", new String[]{String.valueOf(id)});
    }

    public void deleteBookmarkByUrl(String url) {
        getWritableDatabase().delete("bookmarks", "url=?", new String[]{url});
    }

    public List<Record> bookmarks(String keyword) {
        List<Record> list = new ArrayList<>();
        Cursor c;
        if (keyword == null || keyword.trim().isEmpty()) {
            c = getReadableDatabase().rawQuery(
                    "SELECT _id,title,url,created FROM bookmarks ORDER BY created DESC", null);
        } else {
            String k = "%" + keyword.trim() + "%";
            c = getReadableDatabase().rawQuery(
                    "SELECT _id,title,url,created FROM bookmarks WHERE title LIKE ? OR url LIKE ? ORDER BY created DESC",
                    new String[]{k, k});
        }
        while (c.moveToNext()) {
            Record r = new Record();
            r.id = c.getLong(0);
            r.title = c.getString(1);
            r.url = c.getString(2);
            r.time = c.getLong(3);
            list.add(r);
        }
        c.close();
        return list;
    }

    // ---------------- history ----------------

    public void addHistory(String title, String url) {
        if (url == null || url.isEmpty()) return;
        if (url.startsWith("zorme://") || url.startsWith("data:") || url.startsWith("about:")) return;
        long now = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery(
                "SELECT _id,visited FROM history WHERE url=? ORDER BY visited DESC LIMIT 1",
                new String[]{url});
        if (c.moveToFirst() && now - c.getLong(1) < 30 * 60 * 1000L) {
            long id = c.getLong(0);
            c.close();
            db.execSQL("UPDATE history SET visited=?, visits=visits+1, title=? WHERE _id=?",
                    new Object[]{now, title == null ? "" : title, id});
            return;
        }
        c.close();
        ContentValues cv = new ContentValues();
        cv.put("title", title == null ? "" : title);
        cv.put("url", url);
        cv.put("visited", now);
        cv.put("visits", 1);
        db.insert("history", null, cv);
    }

    public List<Record> history(String keyword, int limit) {
        List<Record> list = new ArrayList<>();
        Cursor c;
        String tail = " ORDER BY visited DESC LIMIT " + limit;
        if (keyword == null || keyword.trim().isEmpty()) {
            c = getReadableDatabase().rawQuery(
                    "SELECT _id,title,url,visited FROM history" + tail, null);
        } else {
            String k = "%" + keyword.trim() + "%";
            c = getReadableDatabase().rawQuery(
                    "SELECT _id,title,url,visited FROM history WHERE title LIKE ? OR url LIKE ?" + tail,
                    new String[]{k, k});
        }
        while (c.moveToNext()) {
            Record r = new Record();
            r.id = c.getLong(0);
            r.title = c.getString(1);
            r.url = c.getString(2);
            r.time = c.getLong(3);
            list.add(r);
        }
        c.close();
        return list;
    }

    public void deleteHistory(long id) {
        getWritableDatabase().delete("history", "_id=?", new String[]{String.valueOf(id)});
    }

    public void clearHistory() {
        getWritableDatabase().delete("history", null, null);
    }

    // ---------------- downloads ----------------

    public void addDownload(String title, String url, String path, long size, long downloadId) {
        ContentValues cv = new ContentValues();
        cv.put("title", title == null ? "" : title);
        cv.put("url", url);
        cv.put("path", path);
        cv.put("size", size);
        cv.put("time", System.currentTimeMillis());
        cv.put("download_id", downloadId);
        getWritableDatabase().insert("downloads", null, cv);
    }

    public List<Record> downloads() {
        List<Record> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT _id,title,url,path,size,time,download_id FROM downloads ORDER BY time DESC", null);
        while (c.moveToNext()) {
            Record r = new Record();
            r.id = c.getLong(0);
            r.title = c.getString(1);
            r.url = c.getString(2);
            r.path = c.getString(3);
            r.size = c.getLong(4);
            r.time = c.getLong(5);
            r.downloadId = c.getLong(6);
            list.add(r);
        }
        c.close();
        return list;
    }

    public void deleteDownload(long id) {
        getWritableDatabase().delete("downloads", "_id=?", new String[]{String.valueOf(id)});
    }

    public void clearDownloads() {
        getWritableDatabase().delete("downloads", null, null);
    }
}
