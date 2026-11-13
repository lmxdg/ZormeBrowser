package com.zorme.browser;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 网页源码查看页：展示 evaluateJavascript 获取的 HTML 源码，支持复制。
 * 通过静态字段传递内容（避免 Intent 超出 Binder 限制）。
 */
public class SourceActivity extends AppCompatActivity {

    /** 跳转前设置源码内容，进入后立即置空引用帮助 GC */
    private static String pendingSource;
    private static String pendingUrl;

    private static final int MAX_SHOW = 400_000;

    public static void start(AppCompatActivity from, String url, String html) {
        pendingSource = html;
        pendingUrl = url;
        from.startActivity(new android.content.Intent(from, SourceActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_source);

        String html = pendingSource;
        String url = pendingUrl;
        pendingSource = null;
        pendingUrl = null;

        findViewById(R.id.source_back).setOnClickListener(v -> finish());
        findViewById(R.id.source_copy).setOnClickListener(v -> {
            Utils.copy(this, html == null ? "" : html);
            ZDialog.toast(this, "源码已复制");
        });

        TextView title = findViewById(R.id.source_title);
        TextView subtitle = findViewById(R.id.source_subtitle);
        TextView text = findViewById(R.id.source_text);

        if (html == null || html.isEmpty()) {
            title.setText("网页源码");
            subtitle.setText("");
            text.setText("未能获取到网页源码。\n\n可能原因：页面尚未加载完成、页面由脚本动态生成或页面已离开。请等页面加载完成后重试。");
            return;
        }
        title.setText("网页源码");
        subtitle.setText(url == null ? "" : Utils.displayUrl(url)
                + " · " + Utils.formatSize(html.length()));
        if (html.length() > MAX_SHOW) {
            text.setText(html.substring(0, MAX_SHOW)
                    + "\n\n…（源码过长，仅显示前 40 万字符；可点击右上角复制完整源码）");
        } else {
            text.setText(html);
        }
    }
}
