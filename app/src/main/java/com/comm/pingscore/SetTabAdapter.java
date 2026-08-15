package com.comm.pingscore;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public final class SetTabAdapter extends RecyclerView.Adapter<SetTabAdapter.TabViewHolder> {
    public static final int COMPLETED = 0;
    public static final int CURRENT = 1;
    public static final int PENDING = 2;

    public static final class Item {
        public final int number;
        public final int status;
        public final String score;
        public final boolean selected;

        public Item(int number, int status, String score, boolean selected) {
            this.number = number;
            this.status = status;
            this.score = score;
            this.selected = selected;
        }
    }

    public interface OnTabClickListener {
        void onTabClick(int position);
    }

    private final List<Item> items;
    private final OnTabClickListener listener;

    public SetTabAdapter(List<Item> items, OnTabClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TabViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new TabViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_set_tab, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull TabViewHolder holder, int position) {
        Item item = items.get(position);
        holder.label.setText("第" + item.number + "局\n" + item.score);
        int background;
        int textColor;
        if (item.status == COMPLETED) {
            background = item.selected ? R.drawable.bg_set_completed_selected : R.drawable.bg_set_tab;
            textColor = R.color.score_ink;
        } else if (item.status == CURRENT) {
            background = R.drawable.bg_set_current;
            textColor = R.color.score_blue;
        } else {
            background = R.drawable.bg_set_tab;
            textColor = R.color.score_muted;
        }
        holder.label.setBackgroundResource(background);
        holder.label.setTextColor(holder.label.getContext().getColor(textColor));
        holder.label.setOnClickListener(v -> listener.onTabClick(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class TabViewHolder extends RecyclerView.ViewHolder {
        final TextView label;

        TabViewHolder(@NonNull View itemView) {
            super(itemView);
            label = (TextView) itemView;
        }
    }
}
