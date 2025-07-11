package com.example.keywordlistenerjava.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.keywordlistenerjava.R;

import java.util.List;

public class KeywordLinkAdapter extends RecyclerView.Adapter<KeywordLinkAdapter.KeywordLinkViewHolder> {

    private List<LinkDisplayItem> linkDisplayItems;
    private OnItemActionListener listener;

    // Interface for actions (toggle/delete)
    public interface OnItemActionListener {
        void onLinkToggle(int linkId, boolean isChecked);
        void onLinkDelete(int linkId);
    }

    public KeywordLinkAdapter(List<LinkDisplayItem> linkDisplayItems, OnItemActionListener listener) {
        this.linkDisplayItems = linkDisplayItems;
        this.listener = listener;
    }

    @NonNull
    @Override
    public KeywordLinkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_keyword_link, parent, false);
        return new KeywordLinkViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull KeywordLinkViewHolder holder, int position) {
        LinkDisplayItem item = linkDisplayItems.get(position);

        holder.tvKeywordText.setText("الكلمة: " + item.keywordText);
        holder.tvPhoneNumber.setText("الرقم: " + item.phoneNumber + " (" + item.numberDescription + ")");
        holder.toggleActive.setChecked(item.isActive);

        holder.toggleActive.setOnCheckedChangeListener(null); // Clear previous listener to prevent infinite loops
        holder.toggleActive.setChecked(item.isActive);
        holder.toggleActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) {
                listener.onLinkToggle(item.linkId, isChecked);
            }
        });

        holder.btnDeleteLink.setOnClickListener(v -> {
            if (listener != null) {
                listener.onLinkDelete(item.linkId);
            }
        });
    }

    @Override
    public int getItemCount() {
        return linkDisplayItems.size();
    }

    static class KeywordLinkViewHolder extends RecyclerView.ViewHolder {
        TextView tvKeywordText, tvPhoneNumber;
        ToggleButton toggleActive;
        Button btnDeleteLink;

        public KeywordLinkViewHolder(@NonNull View itemView) {
            super(itemView);
            tvKeywordText = itemView.findViewById(R.id.tv_link_keyword);
            tvPhoneNumber = itemView.findViewById(R.id.tv_link_number);
            toggleActive = itemView.findViewById(R.id.toggle_link_active);
            btnDeleteLink = itemView.findViewById(R.id.btn_delete_link);
        }
    }

    // Helper class to combine data for display
    public static class LinkDisplayItem {
        public int linkId;
        public String keywordText;
        public String phoneNumber;
        public String numberDescription;
        public boolean isActive;

        public LinkDisplayItem(int linkId, String keywordText, String phoneNumber, String numberDescription, boolean isActive) {
            this.linkId = linkId;
            this.keywordText = keywordText;
            this.phoneNumber = phoneNumber;
            this.numberDescription = numberDescription;
            this.isActive = isActive;
        }
    }
}