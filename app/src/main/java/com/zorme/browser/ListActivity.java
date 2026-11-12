package com.zorme.browser;

import android.app.DownloadManager;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 书签 / 历史 / 下载 三个列表页共用。
 */
public class ListActivity extends AppCompatActivity {

    public static final int MODE_BOOKMARKS = 1;
    public static final int MODE_HISTORY = 2;
    public static final int MODE_DOWNLOADS = 3;

    private int mode;
    private final List<Record> data = new ArrayList<>();
    private RecordAdapter adapter;
    private LinearLayout empty;
    private TextView emptyText, title;
    private EditText search;
    private LinearLayout searchBox;
    private ImageView emptyIcon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);

        mode = getIntent().getIntExtra("mode", MODE_HISTORY);
        title = findViewById(R.id.list_title);
        empty = findViewById(R.id.list_empty);
        emptyText = findViewById(R.id.list_empty_text);
        emptyIcon = findViewById(R.id.list_empty_icon);
        search = findViewById(R.id.list_search);
        searchBox = findViewById(R.id.list_search_box);
        ImageView action = findViewById(R.id.list_action);

        findViewById(R.id.list_back).setOnClickListener(v -> finish());

        RecyclerView recycler = findViewById(R.id.list_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecordAdapter();
        recycler.setAdapter(adapter);

        if (mode == MODE_BOOKMARKS) {
            title.setText(R.string.bookmarks);
            emptyText.setText(R.string.empty_bookmarks);
            emptyIcon.setImageResource(R.drawable.ic_star);
            action.setVisibility(View.GONE);
            searchBox.setVisibility(View.VISIBLE);
            search.setHint("搜索书签");
        } else if (mode == MODE_HISTORY) {
            title.setText(R.string.history);
            emptyText.setText(R.string.empty_history);
            emptyIcon.setImageResource(R.drawable.ic_history);
            action.setVisibility(View.VISIBLE);
            action.setImageResource(R.drawable.ic_delete);
            searchBox.setVisibility(View.VISIBLE);
            search.setHint("搜索历史");
            action.setOnClickListener(v -> confirmClear());
        } else {
            title.setText(R.string.downloads);
            emptyText.setText(R.string.empty_downloads);
            emptyIcon.setImageResource(R.drawable.ic_download);
            action.setVisibility(View.VISIBLE);
            action.setImageResource(R.drawable.ic_delete);
            action.setOnClickListener(v -> confirmClear());
        }

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                load();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        String q = search.getText() == null ? "" : search.getText().toString().trim();
        data.clear();
        DbHelper db = DbHelper.get(this);
        if (mode == MODE_BOOKMARKS) data.addAll(db.bookmarks(q));
        else if (mode == MODE_HISTORY) data.addAll(db.history(q, 500));
        else data.addAll(db.downloads());
        adapter.notifyDataSetChanged();
        empty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openUrl(String url) {
        Intent i = new Intent(this, MainActivity.class);
        i.setAction(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private void confirmClear() {
        String msg = mode == MODE_HISTORY ? "确定清除全部历史记录？" : "确定清除全部下载记录？\n（不会删除已下载的文件）";
        ZDialog.confirmDanger(this, getString(R.string.clear_data), msg, "清除", () -> {
            if (mode == MODE_HISTORY) {
                DbHelper.get(this).clearHistory();
                Utils.toast(this, getString(R.string.history_cleared));
            } else {
                DbHelper.get(this).clearDownloads();
                Utils.toast(this, "下载记录已清空");
            }
            load();
        });
    }

    private void showItemMenu(final Record r) {
        List<String> items = new ArrayList<>();
        if (mode == MODE_BOOKMARKS) {
            items.add("编辑标题");
            items.add("复制链接");
            items.add("删除书签");
        } else if (mode == MODE_HISTORY) {
            items.add("复制链接");
            items.add("删除记录");
        } else {
            items.add("打开下载目录");
            items.add("删除记录");
        }
        int danger = items.size() - 1;
        ZDialog.options(this, mode == MODE_BOOKMARKS ? "书签" : (mode == MODE_HISTORY ? "历史记录" : "下载"),
                items.toArray(new String[0]), danger, which -> {
                    if (mode == MODE_BOOKMARKS) {
                        if (which == 0) editBookmark(r);
                        else if (which == 1) Utils.copy(this, r.url);
                        else {
                            DbHelper.get(this).deleteBookmark(r.id);
                            load();
                            Utils.toast(this, "书签已删除");
                        }
                    } else if (mode == MODE_HISTORY) {
                        if (which == 0) Utils.copy(this, r.url);
                        else {
                            DbHelper.get(this).deleteHistory(r.id);
                            load();
                            Utils.toast(this, "记录已删除");
                        }
                    } else {
                        if (which == 0) openDownloads();
                        else {
                            DbHelper.get(this).deleteDownload(r.id);
                            load();
                            Utils.toast(this, "记录已删除");
                        }
                    }
                });
    }

    private void editBookmark(final Record r) {
        ZDialog.input(this, getString(R.string.edit_bookmark), "书签名称", r.title,
                getString(R.string.save), text -> {
                    String name = text == null ? "" : text.trim();
                    if (name.isEmpty()) name = r.title;
                    DbHelper.get(this).updateBookmark(r.id, name, r.url);
                    load();
                    Utils.toast(this, "书签已更新");
                });
    }

    private void openDownloads() {
        try {
            startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
        } catch (Exception e) {
            Utils.toast(this, "无法打开下载管理器");
        }
    }

    private class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_record, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Record r = data.get(position);
            String label = r.title == null || r.title.trim().isEmpty() ? Utils.titleOf(r.url) : r.title;
            h.title.setText(label);
            h.letter.setText(Utils.letterOf(label));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Utils.colorFor(r.url));
            bg.setCornerRadius(Utils.dp(ListActivity.this, 12));
            h.letter.setBackground(bg);

            if (mode == MODE_DOWNLOADS) {
                h.url.setText(Utils.formatSize(r.size) + (r.size > 0 ? " · " : "") + Utils.displayUrl(r.url));
                h.time.setText(Utils.formatTime(r.time));
            } else {
                h.url.setText(Utils.displayUrl(r.url));
                h.time.setText(Utils.formatTime(r.time));
            }

            h.itemView.setOnClickListener(v -> {
                if (mode == MODE_DOWNLOADS) openDownloads();
                else openUrl(r.url);
            });
            h.itemView.setOnLongClickListener(v -> {
                showItemMenu(r);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView letter, title, url, time;

            VH(@NonNull View itemView) {
                super(itemView);
                letter = itemView.findViewById(R.id.record_letter);
                title = itemView.findViewById(R.id.record_title);
                url = itemView.findViewById(R.id.record_url);
                time = itemView.findViewById(R.id.record_time);
            }
        }
    }
}
