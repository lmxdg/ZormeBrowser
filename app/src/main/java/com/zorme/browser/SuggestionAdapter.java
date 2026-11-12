package com.zorme.browser;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class SuggestionAdapter extends RecyclerView.Adapter<SuggestionAdapter.VH> {

    public interface Listener {
        void onSuggestion(Suggestion s);
    }

    private final List<Suggestion> data = new ArrayList<>();
    private final Listener listener;

    public SuggestionAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Suggestion> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_suggestion, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Suggestion s = data.get(position);
        h.title.setText(s.title);
        if (s.subtitle == null || s.subtitle.trim().isEmpty()) {
            h.subtitle.setVisibility(View.GONE);
        } else {
            h.subtitle.setVisibility(View.VISIBLE);
            h.subtitle.setText(s.subtitle);
        }
        h.tag.setText(s.tag);
        if (s.type == Suggestion.TYPE_BOOKMARK) {
            h.icon.setImageResource(R.drawable.ic_star_filled);
        } else if (s.type == Suggestion.TYPE_HISTORY) {
            h.icon.setImageResource(R.drawable.ic_history);
        } else if (s.type == Suggestion.TYPE_URL) {
            h.icon.setImageResource(R.drawable.ic_globe);
        } else {
            h.icon.setImageResource(R.drawable.ic_search);
        }
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSuggestion(s);
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title, subtitle, tag;

        VH(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.suggestion_icon);
            title = itemView.findViewById(R.id.suggestion_title);
            subtitle = itemView.findViewById(R.id.suggestion_subtitle);
            tag = itemView.findViewById(R.id.suggestion_tag);
        }
    }
}
