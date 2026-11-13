package com.zorme.browser;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 助手聊天页：OpenAI 兼容接口，配置在设置中完成。
 * 界面全部自绘，不使用系统弹窗；对话仅保存在内存中。
 */
public class AiChatActivity extends AppCompatActivity {

    private static final String SYSTEM_PROMPT =
            "你是 Zorme 浏览器内置的 AI 助手。请使用与用户提问一致的语言，"
                    + "回答简洁、结构清晰，输出纯文本（避免 Markdown 表格与图片语法）。";

    private final List<AiClient.Msg> msgs = new ArrayList<>();
    private final List<AiClient.Msg> history = new ArrayList<>();
    private ChatAdapter adapter;
    private EditText input;
    private TextView send;
    private LinearLayoutManager layoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);

        input = findViewById(R.id.ai_input);
        send = findViewById(R.id.ai_send);
        TextView subtitle = findViewById(R.id.ai_subtitle);
        RecyclerView list = findViewById(R.id.ai_list);
        findViewById(R.id.ai_back).setOnClickListener(v -> finish());
        findViewById(R.id.ai_clear).setOnClickListener(v -> confirmClear());

        subtitle.setText(Prefs.aiModel(this) + " · " + AiProvider.currentName(
                Prefs.aiBase(this), null));
        send.setOnClickListener(v -> {
            if (AiClient.busy()) {
                AiClient.cancel();
                return;
            }
            send();
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send();
                return true;
            }
            return false;
        });

        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        list.setLayoutManager(layoutManager);
        adapter = new ChatAdapter();
        list.setAdapter(adapter);

        if (msgs.isEmpty()) {
            msgs.add(new AiClient.Msg("assistant",
                    "你好，我是 Zorme 内置的 AI 助手，基于 OpenAI 兼容接口连接你配置的服务商。\n\n"
                            + "可在「设置 → AI 助手」中选择服务商（智谱 / DeepSeek / 阿里云百炼 / 基元律动 / "
                            + "字节跳动）或自定义接口，并填写 API Key。"));
            adapter.notifyItemInserted(0);
        }

        if (Prefs.aiKey(this).trim().isEmpty()) {
            ZDialog.confirm(this, "配置 AI 服务商",
                    "还没有配置 API Key。先去设置选择服务商（内置智谱、DeepSeek、阿里云百炼、基元律动、"
                            + "字节跳动）并填写 Key，也可以随时在对话后再配置。",
                    "去设置", () -> startActivity(new Intent(this, SettingsActivity.class)));
        }
    }

    @Override
    protected void onDestroy() {
        if (AiClient.busy()) AiClient.cancel();
        super.onDestroy();
    }

    private void confirmClear() {
        ZDialog.confirmDanger(this, "清空对话",
                "将删除当前全部聊天记录（仅保存在内存中）。", "清空", () -> {
                    history.clear();
                    int count = msgs.size();
                    msgs.clear();
                    adapter.notifyItemRangeRemoved(0, count);
                });
    }

    private List<AiClient.Msg> buildMessages() {
        List<AiClient.Msg> out = new ArrayList<>();
        out.add(new AiClient.Msg("system", SYSTEM_PROMPT));
        out.addAll(history);
        return out;
    }

    private void send() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        input.setText("");

        history.add(new AiClient.Msg("user", text));
        msgs.add(new AiClient.Msg("user", text));
        adapter.notifyItemInserted(msgs.size() - 1);
        msgs.add(new AiClient.Msg("assistant", ""));
        adapter.notifyItemInserted(msgs.size() - 1);
        scrollToBottom();
        setBusyUi(true);

        AiClient.chat(Prefs.aiBase(this), Prefs.aiKey(this), Prefs.aiModel(this),
                buildMessages(), true, new AiClient.Callback() {
                    @Override
                    public void onDelta(String fullSoFar) {
                        updateLastAssistant(fullSoFar);
                    }

                    @Override
                    public void onDone(String fullText, String error) {
                        setBusyUi(false);
                        if (error != null) {
                            // 失败：从上下文移除该提问，气泡显示错误说明
                            if (!history.isEmpty()
                                    && "user".equals(history.get(history.size() - 1).role)) {
                                history.remove(history.size() - 1);
                            }
                            updateLastAssistant("⚠ 请求失败：" + error);
                        } else if (fullText.isEmpty()) {
                            updateLastAssistant("⚠ 服务返回了空内容");
                        } else {
                            history.add(new AiClient.Msg("assistant", fullText));
                            updateLastAssistant(fullText);
                        }
                    }
                });
    }

    private void updateLastAssistant(String text) {
        int index = -1;
        for (int i = msgs.size() - 1; i >= 0; i--) {
            if ("assistant".equals(msgs.get(i).role)) {
                index = i;
                break;
            }
        }
        if (index < 0) return;
        msgs.set(index, new AiClient.Msg("assistant", text));
        adapter.notifyItemChanged(index);
        scrollToBottom();
    }

    private void setBusyUi(boolean busy) {
        send.setText(busy ? "停止" : getString(R.string.ai_send));
    }

    private void scrollToBottom() {
        if (layoutManager != null && msgs.size() > 0) {
            layoutManager.scrollToPositionWithOffset(msgs.size() - 1, 0);
        }
    }

    private void copyText(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("zorme", text));
            Utils.toast(this, "已复制");
        }
    }

    /** 聊天气泡列表适配器 */
    private class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_ai_msg, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            AiClient.Msg m = msgs.get(position);
            boolean user = "user".equals(m.role);
            h.userBubble.setVisibility(user ? View.VISIBLE : View.GONE);
            h.aiWrap.setVisibility(user ? View.GONE : View.VISIBLE);
            if (user) {
                h.userText.setText(m.content);
            } else {
                h.aiText.setText(m.content.isEmpty() ? "…" : m.content);
                boolean error = m.content.startsWith("⚠");
                h.aiText.setTextColor(error
                        ? getColor(R.color.danger) : getColor(R.color.text_primary));
            }
            View longPressView = user ? h.userText : h.aiText;
            longPressView.setOnLongClickListener(v -> {
                copyText(m.content);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return msgs.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView userText, aiText;
            final View userBubble;
            final LinearLayout aiWrap;

            VH(View v) {
                super(v);
                userText = v.findViewById(R.id.msg_user);
                aiText = v.findViewById(R.id.msg_ai);
                userBubble = v.findViewById(R.id.msg_user);
                aiWrap = v.findViewById(R.id.msg_ai_wrap);
            }
        }
    }
}
