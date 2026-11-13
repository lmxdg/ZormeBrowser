package com.zorme.browser;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * 网络日志页：展示 WebView 发出的资源请求（仅内存，不落盘）。
 */
public class NetworkLogActivity extends AppCompatActivity {

    private final List<NetworkLog.Entry> data = NetworkLog.snapshot();
    private LogAdapter adapter;
    private View empty;
    private TextView emptyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);

        ((TextView) findViewById(R.id.list_title)).setText("网络日志");
        empty = findViewById(R.id.list_empty);
        emptyText = findViewById(R.id.list_empty_text);
        ((android.widget.ImageView) findViewById(R.id.list_empty_icon))
                .setImageResource(R.drawable.ic_network);
        emptyText.setText("暂无网络请求\n打开网页后这里会显示资源加载记录");
        findViewById(R.id.list_search_box).setVisibility(View.GONE);

        android.widget.ImageView action = findViewById(R.id.list_action);
        action.setVisibility(View.VISIBLE);
        action.setImageResource(R.drawable.ic_delete);
        action.setOnClickListener(v -> ZDialog.confirmDanger(this, "清空网络日志",
                "将清空本次会话记录的所有网络请求日志。", "清空", () -> {
                    NetworkLog.clear();
                    reload();
                    ZDialog.toast(this, "网络日志已清空");
                }));

        findViewById(R.id.list_back).setOnClickListener(v -> finish());

        RecyclerView recycler = findViewById(R.id.list_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LogAdapter();
        recycler.setAdapter(adapter);
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        data.clear();
        data.addAll(NetworkLog.snapshot());
        adapter.notifyDataSetChanged();
        empty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private class LogAdapter extends RecyclerView.Adapter<LogAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_netlog, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            NetworkLog.Entry e = data.get(position);
            h.method.setText(e.method);
            GradientDrawable bg = new GradientDrawable();
            int color = e.blocked ? 0xFFD93B3B
                    : "POST".equals(e.method) ? 0xFF8A6BE8
                    : e.mainFrame ? 0xFF1D9FE8 : 0xFF6B7A8A;
            bg.setColor(color);
            bg.setCornerRadius(Utils.dp(NetworkLogActivity.this, 6));
            h.method.setBackground(bg);
            h.url.setText(e.url);
            String type = e.blocked ? "已拦截（广告/追踪）"
                    : e.mainFrame ? "主文档" : "子资源";
            h.type.setText(type + " · " + Utils.domainOf(e.url));
            h.time.setText(Utils.formatTime(e.time));
            h.itemView.setOnClickListener(v -> Utils.copy(NetworkLogActivity.this, e.url));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView method, url, type, time;

            VH(@NonNull View itemView) {
                super(itemView);
                method = itemView.findViewById(R.id.netlog_method);
                url = itemView.findViewById(R.id.netlog_url);
                type = itemView.findViewById(R.id.netlog_type);
                time = itemView.findViewById(R.id.netlog_time);
            }
        }
    }
}
