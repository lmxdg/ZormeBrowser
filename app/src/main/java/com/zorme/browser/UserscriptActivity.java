package com.zorme.browser;

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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 油猴脚本管理：列表（启用开关 / 删除）、从 URL 或粘贴代码安装脚本。
 */
public class UserscriptActivity extends AppCompatActivity {

    private final List<Userscript> data = new ArrayList<>();
    private ScriptAdapter adapter;
    private LinearLayout empty;
    private TextView emptyText;
    private EditText search;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_userscript);

        TextView title = findViewById(R.id.list_title);
        empty = findViewById(R.id.list_empty);
        emptyText = findViewById(R.id.list_empty_text);
        search = findViewById(R.id.list_search);
        LinearLayout searchBox = findViewById(R.id.list_search_box);
        ImageView action = findViewById(R.id.list_action);

        title.setText(getString(R.string.userscript_title));
        emptyText.setText(R.string.userscript_empty);
        ((ImageView) findViewById(R.id.list_empty_icon)).setImageResource(R.drawable.ic_code);
        action.setVisibility(View.VISIBLE);
        action.setImageResource(R.drawable.ic_add);
        action.setContentDescription(getString(R.string.userscript_add));
        action.setOnClickListener(v -> showAddDialog());

        findViewById(R.id.list_back).setOnClickListener(v -> finish());
        searchBox.setVisibility(View.VISIBLE);
        search.setHint("搜索脚本");
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

        RecyclerView recycler = findViewById(R.id.list_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ScriptAdapter();
        recycler.setAdapter(adapter);
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        String q = search.getText() == null ? "" : search.getText().toString().trim().toLowerCase();
        data.clear();
        for (Userscript s : UserscriptStore.get(this).all()) {
            if (q.isEmpty() || s.name.toLowerCase().contains(q)
                    || s.description.toLowerCase().contains(q)) {
                data.add(s);
            }
        }
        adapter.notifyDataSetChanged();
        empty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showAddDialog() {
        ZDialog.input(this, getString(R.string.userscript_add),
                "输入 .user.js 脚本地址或直接粘贴脚本代码", "",
                getString(R.string.save), text -> {
                    String t = text == null ? "" : text.trim();
                    if (t.isEmpty()) return;
                    if (t.startsWith("http://") || t.startsWith("https://")) {
                        installFromUrl(t);
                    } else if (t.contains("==UserScript==") || t.contains("function")) {
                        installCode(t, "");
                    } else {
                        Utils.toast(this, "内容不是有效的脚本或链接");
                    }
                });
    }

    private void installFromUrl(String url) {
        Utils.toast(this, "正在下载脚本…");
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (ZormeBrowser)");
                int code = conn.getResponseCode();
                if (code != 200) throw new IllegalStateException("HTTP " + code);
                InputStream in = conn.getInputStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                in.close();
                conn.disconnect();
                String body = new String(bos.toByteArray(), StandardCharsets.UTF_8);
                runOnUiThread(() -> installCode(body, url));
            } catch (Exception e) {
                runOnUiThread(() -> Utils.toast(this, "下载失败：" + e.getMessage()));
            }
        }, "zorme-us-install").start();
    }

    private void installCode(String code, String sourceUrl) {
        if (code == null || code.trim().length() < 10) {
            Utils.toast(this, "脚本内容为空");
            return;
        }
        Userscript s = UserscriptStore.get(this).add(code, sourceUrl);
        load();
        Utils.toast(this, "已安装：" + s.name);
    }

    private class ScriptAdapter extends RecyclerView.Adapter<ScriptAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_userscript, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            final Userscript s = data.get(position);
            h.title.setText(s.name);
            StringBuilder meta = new StringBuilder();
            if (!s.version.isEmpty()) meta.append('v').append(s.version).append(" · ");
            meta.append(s.enabled ? "已启用" : "已停用");
            if (!s.matches.isEmpty()) meta.append(" · ").append(s.matches.get(0));
            else if (!s.includes.isEmpty()) meta.append(" · ").append(s.includes.get(0));
            h.meta.setText(meta.toString());
            if (s.description != null && !s.description.isEmpty()) {
                h.desc.setVisibility(View.VISIBLE);
                h.desc.setText(s.description);
            } else {
                h.desc.setVisibility(View.GONE);
            }
            h.switchBtn.setCheckedSilently(s.enabled);
            h.switchBtn.setOnCheckedChangeListener((view, checked) -> {
                UserscriptStore.get(UserscriptActivity.this).setEnabled(s.id, checked);
                load();
            });
            h.itemView.setOnClickListener(v -> showScriptMenu(s));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView title, meta, desc;
            final ZSwitch switchBtn;

            VH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.us_title);
                meta = itemView.findViewById(R.id.us_meta);
                desc = itemView.findViewById(R.id.us_desc);
                switchBtn = itemView.findViewById(R.id.us_switch);
            }
        }
    }

    private void showScriptMenu(Userscript s) {
        ZDialog.options(this, s.name,
                new String[]{"查看代码", "复制代码", "删除脚本"}, 2, which -> {
                    if (which == 0) {
                        ZDialog.alert(this, s.name, s.code);
                    } else if (which == 1) {
                        Utils.copy(this, s.code);
                    } else {
                        ZDialog.confirmDanger(this, "删除脚本",
                                "确定删除「" + s.name + "」及其保存的数据？", "删除", () -> {
                                    UserscriptStore.get(this).remove(s.id);
                                    load();
                                    Utils.toast(this, "脚本已删除");
                                });
                    }
                });
    }
}
