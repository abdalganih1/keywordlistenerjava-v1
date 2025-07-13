package com.example.keywordlistenerjava.activity;

import android.Manifest;
import android.content.BroadcastReceiver; // لاستقبال البث المحلي
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter; // لفلترة البث المحلي
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager; // لإدارة البث المحلي

import com.example.keywordlistenerjava.R;
import com.example.keywordlistenerjava.db.dao.AlertLogDao;
import com.example.keywordlistenerjava.db.dao.UserDao;
import com.example.keywordlistenerjava.db.entity.User;
import com.example.keywordlistenerjava.service.SpeechRecognizerService;
import com.example.keywordlistenerjava.util.SharedPreferencesHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSIONS_REQUEST_CODE = 101;

    private TextView tvWelcome, tvTotalAlerts, tvRealAlerts, tvFalseAlerts, tvRecognizedText; // إضافة tvRecognizedText
    private Button btnStartSTT, btnStopSTT, btnViewLog, btnSettings, btnLogout;

    private SharedPreferencesHelper prefsHelper;
    private UserDao userDao;
    private AlertLogDao alertLogDao;
    private ExecutorService dbExecutor;

    private int currentUserId;

    private String[] requiredPermissions;

    // مستقبل البث المحلي لاستقبال النصوص المكتشفة من الخدمة
    private BroadcastReceiver recognizedTextReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String recognizedText = intent.getStringExtra("recognized_text");
            if (tvRecognizedText != null) {
                tvRecognizedText.setText("نص مكتشف: " + recognizedText);
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefsHelper = new SharedPreferencesHelper(this);
        userDao = new UserDao(this);
        alertLogDao = new AlertLogDao(this);
        dbExecutor = Executors.newSingleThreadExecutor();

        // 1. تحقق من تسجيل الدخول فوراً
        if (!prefsHelper.isLoggedIn()) {
            Log.w(TAG, "No user logged in. Redirecting to LoginActivity.");
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish(); // إنهاء النشاط الحالي
            return; // توقف عن تنفيذ onCreate
        }

        currentUserId = prefsHelper.getLoggedInUserId();
        if (currentUserId == -1) {
            Log.e(TAG, "Logged in user ID is -1. Critical error, logging out.");
            prefsHelper.logoutUser();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish(); // إنهاء النشاط الحالي
            return; // توقف عن تنفيذ onCreate
        }

        // 2. تهيئة عناصر الواجهة (بعد setContentView مباشرة)
        tvWelcome = findViewById(R.id.tv_welcome);
        tvTotalAlerts = findViewById(R.id.tv_total_alerts);
        tvRealAlerts = findViewById(R.id.tv_real_alerts);
        tvFalseAlerts = findViewById(R.id.tv_false_alerts);
        tvRecognizedText = findViewById(R.id.tv_recognized_text); // ربط TextView الجديد
        btnStartSTT = findViewById(R.id.btn_start_porcupine);
        btnStopSTT = findViewById(R.id.btn_stop_porcupine);
        btnViewLog = findViewById(R.id.btn_view_log);
        btnSettings = findViewById(R.id.btn_settings);
        btnLogout = findViewById(R.id.btn_logout);

        // 3. إخفاء TextViews للإحصائيات الحقيقية/الكاذبة
        tvRealAlerts.setVisibility(View.GONE);
        tvFalseAlerts.setVisibility(View.GONE);

        // 4. تعيين المستمعين للأزرار
        btnStartSTT.setOnClickListener(v -> checkPermissionsAndStartService());
        btnStopSTT.setOnClickListener(v -> stopSTTService());
        btnViewLog.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, AlertLogActivity.class)));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));
        btnLogout.setOnClickListener(v -> logoutUser());

        // 5. بناء قائمة الأذونات
        buildRequiredPermissionsList();

        // 6. تحميل بيانات المستخدم والإحصائيات
        loadUserDataAndStats();

        // 7. تسجيل BroadcastReceiver
        LocalBroadcastManager.getInstance(this).registerReceiver(recognizedTextReceiver,
                new IntentFilter("com.example.keywordlistenerjava.RECOGNIZED_TEXT"));
        LocalBroadcastManager.getInstance(this).registerReceiver(recognizedTextReceiver,
                new IntentFilter("com.example.keywordlistenerjava.RECOGNIZED_TEXT_PARTIAL"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // تحديث الإحصائيات كلما عاد النشاط إلى الواجهة الأمامية
        loadUserDataAndStats();
    }

    private void loadUserDataAndStats() {
        dbExecutor.execute(() -> {
            userDao.open();
            User currentUser = userDao.getUserById(currentUserId);
            userDao.close();

            alertLogDao.open();
            int total = alertLogDao.getTotalAlertsCount(currentUserId);
            alertLogDao.close();

            runOnUiThread(() -> {
                if (currentUser != null) {
                    tvWelcome.setText("مرحبًا، " + currentUser.getFirstName() + " " + currentUser.getLastName());
                } else {
                    tvWelcome.setText("مرحبًا أيها المستخدم!");
                }
                tvTotalAlerts.setText("عدد البلاغات الإجمالي: " + total);
            });
        });
    }

    private void buildRequiredPermissionsList() {
        ArrayList<String> permissionsList = new ArrayList<>();
        permissionsList.add(Manifest.permission.RECORD_AUDIO);
        permissionsList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissionsList.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permissionsList.add(Manifest.permission.SEND_SMS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permissionsList.add(Manifest.permission.FOREGROUND_SERVICE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsList.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        requiredPermissions = permissionsList.toArray(new String[0]);
        Log.d(TAG,"Required permissions: " + Arrays.toString(requiredPermissions));
    }

    private void checkPermissionsAndStartService() {
        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                // Foreground service types (MICROPHONE, LOCATION) are declared in manifest, not requested here
                if (!permission.equals(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE) &&
                        !permission.equals(Manifest.permission.FOREGROUND_SERVICE_LOCATION)) {
                    permissionsToRequest.add(permission);
                }
            }
        }

        if (permissionsToRequest.isEmpty()) {
            startSTTService();
        } else {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), PERMISSIONS_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                startSTTService();
            } else {
                Toast.makeText(this, "الأذونات ضرورية لتشغيل خدمة الاستماع.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startSTTService() {
        Log.d(TAG, "Starting SpeechRecognizerService...");
        Intent serviceIntent = new Intent(this, SpeechRecognizerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "تم بدء خدمة الاستماع (STT)", Toast.LENGTH_SHORT).show();
    }

    private void stopSTTService() {
        Log.d(TAG, "Stopping SpeechRecognizerService...");
        Intent serviceIntent = new Intent(this, SpeechRecognizerService.class);
        stopService(serviceIntent);
        Toast.makeText(this, "تم إيقاف خدمة الاستماع (STT)", Toast.LENGTH_SHORT).show();
    }

    private void logoutUser() {
        stopSTTService(); // أوقف الخدمة قبل تسجيل الخروج
        prefsHelper.logoutUser();
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish(); // أغلق MainActivity
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbExecutor.shutdownNow(); // أغلق الـ Executor

        // إلغاء تسجيل BroadcastReceiver لمنع تسرب الذاكرة
        LocalBroadcastManager.getInstance(this).unregisterReceiver(recognizedTextReceiver);
    }
}