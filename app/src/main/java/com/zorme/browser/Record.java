package com.zorme.browser;

/** 书签 / 历史 / 下载的通用数据模型 */
public class Record {
    public long id;
    public long time;
    public long size;
    public long downloadId;
    public String title = "";
    public String url = "";
    public String path = "";
}
