package com.example.keywordlistenerjava.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
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

public class AlertLogActivity extends AppCompatActivity implements AlertLogAdapter.OnItemActionListener {

    private static final String TAG = "AlertLogActivity";
    private static final String LOG_PASSWORD = "0000"; // كلمة المرور للدخول إلى السجل

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

        // *** طلب كلمة المرور عند بدء النشاط ***
        showPasswordDialog();
    }

    private void showPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("إدخال كلمة المرور");
        builder.setMessage("يرجى إدخال كلمة المرور لعرض سجل البلاغات.");

        // إضافة حقل إدخال إلى مربع الحوار
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        builder.setView(input);

        // تعيين أزرار مربع الحوار
        builder.setPositiveButton("دخول", (dialog, which) -> {
            String password = input.getText().toString();
            if (password.equals(LOG_PASSWORD)) {
                // كلمة المرور صحيحة، قم بتحميل السجلات
                loadAlertLogs();
            } else {
                // كلمة المرور خاطئة، اعرض رسالة وأغلق النشاط
                Toast.makeText(AlertLogActivity.this, "كلمة المرور غير صحيحة.", Toast.LENGTH_SHORT).show();
                finish(); // إغلاق النشاط
            }
        });
        builder.setNegativeButton("إلغاء", (dialog, which) -> {
            dialog.cancel();
            finish(); // إغلاق النشاط عند الإلغاء
        });

        // منع إغلاق مربع الحوار عند النقر خارجه
        builder.setCancelable(false);

        builder.show();
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
                adapter = new AlertLogAdapter(alerts, this); // تمرير 'this' كـ listener
                recyclerViewAlerts.setAdapter(adapter);
            });
        });
    }

    // --- معالجة الأحداث من الـ Adapter ---

    @Override
    public void onMapLinkClick(String mapLink) {
        if (mapLink != null && !mapLink.isEmpty() && !mapLink.equals("غير متاح")) {
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

    @Override
    public void onMarkAsRealClick(int logId) {
        updateAlertStatus(logId, false); // false يعني بلاغ حقيقي
    }

    @Override
    public void onMarkAsFalseClick(int logId) {
        updateAlertStatus(logId, true); // true يعني بلاغ كاذب
    }

    /**
     * Updates the 'is_false_alarm' status of an alert log.
     * @param logId The ID of the alert log to update.
     * @param isFalseAlarm True for false alarm, false for real.
     */
    public void updateAlertStatus(int logId, Boolean isFalseAlarm) {
        dbExecutor.execute(() -> {
            alertLogDao.open();
            int rowsAffected = alertLogDao.updateFalseAlarmStatus(logId, isFalseAlarm);
            alertLogDao.close();

            runOnUiThread(() -> {
                if (rowsAffected > 0) {
                    Toast.makeText(this, "تم تحديث حالة البلاغ.", Toast.LENGTH_SHORT).show();
                    loadAlertLogs(); // تحديث القائمة لإظهار التغيير
                } else {
                    Toast.makeText(this, "فشل تحديث حالة البلاغ.", Toast.LENGTH_SHORT).show();
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