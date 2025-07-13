package com.example.keywordlistenerjava.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.keywordlistenerjava.R;
import com.example.keywordlistenerjava.adapter.AlertLogAdapter;
import com.example.keywordlistenerjava.db.dao.AlertLogDao;
import com.example.keywordlistenerjava.db.entity.AlertLog;
import com.example.keywordlistenerjava.util.SharedPreferencesHelper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AlertLogActivity extends AppCompatActivity implements AlertLogAdapter.OnItemClickListener {

    private static final String TAG = "AlertLogActivity";

    private RecyclerView recyclerViewAlerts;
    private AlertLogAdapter adapter;
    private ProgressBar progressBar;

    private AlertLogDao alertLogDao;
    private SharedPreferencesHelper prefsHelper;
    private ExecutorService dbExecutor;

    private int currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert_log);

        prefsHelper = new SharedPreferencesHelper(this);
        currentUserId = prefsHelper.getLoggedInUserId();
        if (currentUserId == -1) {
            Toast.makeText(this, "خطأ: المستخدم غير مسجل الدخول.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        alertLogDao = new AlertLogDao(this);
        dbExecutor = Executors.newSingleThreadExecutor();

        recyclerViewAlerts = findViewById(R.id.recycler_view_alerts);
        progressBar = findViewById(R.id.progress_bar_alert_log);

        recyclerViewAlerts.setLayoutManager(new LinearLayoutManager(this));

        loadAlertLogs();
    }

    private void loadAlertLogs() {
        progressBar.setVisibility(View.VISIBLE);
        dbExecutor.execute(() -> {
            alertLogDao.open();
            List<AlertLog> alerts = alertLogDao.getAllAlertsForUser(currentUserId);
            alertLogDao.close();

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                if (alerts.isEmpty()) {
                    Toast.makeText(this, "لا توجد بلاغات مسجلة بعد.", Toast.LENGTH_SHORT).show();
                }
                adapter = new AlertLogAdapter(alerts, this);
                recyclerViewAlerts.setAdapter(adapter);
            });
        });
    }

    @Override
    public void onMapLinkClick(String mapLink) {
        if (mapLink != null && !mapLink.isEmpty()) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(mapLink));
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Error opening map link: " + mapLink, e);
                Toast.makeText(this, "لا يمكن فتح رابط الخريطة.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "لا يوجد رابط خريطة لهذا البلاغ.", Toast.LENGTH_SHORT).show();
        }
    }

    // هذه الدالة ستظل موجودة في DAO لكي يتمكن المسؤول (أو نظام خارجي) من تحديث الحالة،
    // لكنها لن تكون متاحة مباشرة من واجهة المستخدم للمستخدم العادي.
    public void updateAlertStatus(int logId, Boolean isFalseAlarm) {
        dbExecutor.execute(() -> {
            alertLogDao.open();
            int rowsAffected = alertLogDao.updateFalseAlarmStatus(logId, isFalseAlarm);
            alertLogDao.close();

            runOnUiThread(() -> {
                if (rowsAffected > 0) {
                    Toast.makeText(this, "تم تحديث حالة البلاغ (للمسؤول).", Toast.LENGTH_SHORT).show();
                    loadAlertLogs();
                } else {
                    Toast.makeText(this, "فشل تحديث حالة البلاغ (للمسؤول).", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbExecutor.shutdownNow();
    }
}