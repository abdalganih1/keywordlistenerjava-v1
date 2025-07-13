package com.example.keywordlistenerjava.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.keywordlistenerjava.R;
import com.example.keywordlistenerjava.db.entity.AlertLog;

import java.util.List;
import java.util.Locale;

public class AlertLogAdapter extends RecyclerView.Adapter<AlertLogAdapter.AlertLogViewHolder> {

    private List<AlertLog> alertLogs;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onMapLinkClick(String mapLink);
    }

    public AlertLogAdapter(List<AlertLog> alertLogs, OnItemClickListener listener) {
        this.alertLogs = alertLogs;
        this.listener = listener;
    }

    @NonNull
    @Override
    public AlertLogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_alert, parent, false);
        return new AlertLogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlertLogViewHolder holder, int position) {
        AlertLog alert = alertLogs.get(position);

        holder.tvLogId.setText("رقم الحالة: " + alert.getLogId());
        holder.tvKeywordUsed.setText("الكلمة المفتاحية: " + alert.getKeywordUsed());
        holder.tvDateTime.setText("التاريخ والوقت: " + alert.getAlertDate() + " " + alert.getAlertTime());
        holder.tvLocation.setText("الموقع: " + String.format(Locale.US, "%.6f, %.6f", alert.getLatitude(), alert.getLongitude()));

        // *** إخفاء TextView الخاص بالحالة ***
        holder.tvStatus.setVisibility(View.GONE); // اجعله مخفياً بالكامل
        // أو يمكنك فقط إزالة تعيين النص:
        // holder.tvStatus.setText(""); // إذا أردت إبقاءه مرئياً ولكن فارغاً
        // String statusText;
        // if (alert.getIsFalseAlarm() == null) {
        //     statusText = "الحالة: قيد التقييم";
        // } else if (alert.getIsFalseAlarm()) {
        //     statusText = "الحالة: بلاغ كاذب";
        //     holder.tvStatus.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.holo_red_dark));
        // } else {
        //     statusText = "الحالة: بلاغ حقيقي";
        //     holder.tvStatus.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.holo_green_dark));
        // }
        // holder.tvStatus.setText(statusText);


        holder.tvLocation.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMapLinkClick(alert.getMapLink());
            }
        });
    }

    @Override
    public int getItemCount() {
        return alertLogs.size();
    }

    static class AlertLogViewHolder extends RecyclerView.ViewHolder {
        TextView tvLogId, tvKeywordUsed, tvDateTime, tvLocation, tvStatus;

        public AlertLogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLogId = itemView.findViewById(R.id.tv_alert_log_id);
            tvKeywordUsed = itemView.findViewById(R.id.tv_alert_keyword_used);
            tvDateTime = itemView.findViewById(R.id.tv_alert_date_time);
            tvLocation = itemView.findViewById(R.id.tv_alert_location);
            tvStatus = itemView.findViewById(R.id.tv_alert_status);
        }
    }
}