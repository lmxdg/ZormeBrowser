package com.zorme.browser;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TabAdapter extends RecyclerView.Adapter<TabAdapter.VH> {

    public interface Listener {
        void onSelect(int index);

        void onClose(int index);
    }

    private final List<BrowserTab> tabs;
    private final Listener listener;

    public TabAdapter(List<BrowserTab> tabs, Listener listener) {
        this.tabs = tabs;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tab, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        BrowserTab t = tabs.get(position);
        String title = t.displayTitle();
        h.title.setText(title);
        String url = t.showingHome ? "zorme://home" : t.url;
        h.url.setText(t.showingHome ? "新标签页" : Utils.displayUrl(url));
        h.badge.setVisibility(t.incognito ? View.VISIBLE : View.GONE);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        if (t.showingHome) {
            h.letter.setText("Z");
            bg.setColor(0xFF1E1E26);
        } else {
            h.letter.setText(Utils.letterOf(title));
            bg.setColor(Utils.colorFor(url));
        }
        bg.setCornerRadius(Utils.dp(h.itemView.getContext(), 12));
        h.preview.setBackground(bg);
        // 有实时截图时显示截图，否则显示色块 + 字母
        if (t.preview != null && !t.preview.isRecycled()) {
            h.shot.setImageBitmap(t.preview);
            h.shot.setVisibility(View.VISIBLE);
            h.letter.setVisibility(View.GONE);
        } else {
            h.shot.setVisibility(View.GONE);
            h.letter.setVisibility(View.VISIBLE);
        }
        h.itemView.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) listener.onSelect(pos);
        });
        h.close.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) listener.onClose(pos);
        });
        if (TabManager.get().current == position) {
            h.itemView.setBackgroundResource(R.drawable.bg_tab_card_active);
        } else {
            h.itemView.setBackgroundResource(R.drawable.bg_tab_card);
        }
    }

    @Override
    public int getItemCount() {
        return tabs.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, url, badge, letter;
        final View preview;
        final ImageView close, shot;

        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tab_item_title);
            url = itemView.findViewById(R.id.tab_item_url);
            badge = itemView.findViewById(R.id.tab_item_badge);
            letter = itemView.findViewById(R.id.tab_item_letter);
            preview = itemView.findViewById(R.id.tab_item_preview);
            shot = itemView.findViewById(R.id.tab_item_shot);
            close = itemView.findViewById(R.id.tab_item_close);
        }
    }
}
