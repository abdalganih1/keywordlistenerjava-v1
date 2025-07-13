package com.example.keywordlistenerjava.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.keywordlistenerjava.R;
import com.example.keywordlistenerjava.db.entity.EmergencyNumber;
import com.example.keywordlistenerjava.db.entity.Keyword;

import java.util.List;

public class ManageItemsAdapter extends RecyclerView.Adapter<ManageItemsAdapter.ManageItemViewHolder> {

    public enum ItemType { KEYWORD, NUMBER }

    private List<?> items; // Can be a list of Keywords or EmergencyNumbers
    private OnManageItemListener listener;
    private ItemType itemType;

    public interface OnManageItemListener {
        void onItemEdit(Object item);
        void onItemDelete(Object item);
    }

    public ManageItemsAdapter(List<?> items, OnManageItemListener listener, ItemType itemType) {
        this.items = items;
        this.listener = listener;
        this.itemType = itemType;
    }

    @NonNull
    @Override
    public ManageItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_manageable, parent, false);
        return new ManageItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ManageItemViewHolder holder, int position) {
        Object item = items.get(position);

        if (itemType == ItemType.KEYWORD) {
            Keyword keyword = (Keyword) item;
            holder.tvItemText.setText(keyword.getKeywordText());
        } else if (itemType == ItemType.NUMBER) {
            EmergencyNumber number = (EmergencyNumber) item;
            holder.tvItemText.setText(number.getNumberDescription() + " (" + number.getPhoneNumber() + ")");
        }

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemEdit(item);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemDelete(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ManageItemViewHolder extends RecyclerView.ViewHolder {
        TextView tvItemText;
        Button btnEdit, btnDelete;

        public ManageItemViewHolder(@NonNull View itemView) {
            super(itemView);
            tvItemText = itemView.findViewById(R.id.tv_manage_item_text);
            btnEdit = itemView.findViewById(R.id.btn_manage_item_edit);
            btnDelete = itemView.findViewById(R.id.btn_manage_item_delete);
        }
    }
}
