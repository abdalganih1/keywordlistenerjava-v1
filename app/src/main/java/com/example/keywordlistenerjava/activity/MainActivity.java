package com.example.keywordlistenerjava.activity;

import android.Manifest;
import android.content.BroadcastReceiver; // جديد
import android.content.Context; // جديد
import android.content.Intent;
import android.content.IntentFilter; // جديد
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager; // جديد

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

    private TextView tvWelcome, tvTotalAlerts, tvRealAlerts, tvFalseAlerts;
    private TextView tvRecognizedText; // *** جديد: لعرض النص المكتشف ***
    private Button btnStartListening, btnStopListening, btnViewLog, btnSettings, btnLogout;

    private SharedPreferencesHelper prefsHelper;
    private UserDao userDao;
    private AlertLogDao alertLogDao;
    private ExecutorService dbExecutor;

    private int currentUserId;

    private String[] requiredPermissions;

    // *** جديد: BroadcastReceiver لاستقبال النص من الخدمة ***
    private BroadcastReceiver recognizedTextReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String recognizedText = intent.getStringExtra("recognized_text");
            if (recognizedText != null) {
                tvRecognizedText.setText("التعرف على الصوت: " + recognizedText);
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

        if (!prefsHelper.isLoggedIn()) {
            Log.w(TAG, "No user logged in. Redirecting to LoginActivity.");
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        currentUserId = prefsHelper.getLoggedInUserId();
        if (currentUserId == -1) {
            Log.e(TAG, "Logged in user ID is -1. Critical error, logging out.");
            prefsHelper.logoutUser();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        // Initialize UI elements
        tvWelcome = findViewById(R.id.tv_welcome);
        tvTotalAlerts = findViewById(R.id.tv_total_alerts);
        tvRealAlerts = findViewById(R.id.tv_real_alerts);
        tvFalseAlerts = findViewById(R.id.tv_false_alerts);
        tvRecognizedText = findViewById(R.id.tv_recognized_text); // *** ربط العنصر الجديد ***

        btnStartListening = findViewById(R.id.btn_start_porcupine);
        btnStopListening = findViewById(R.id.btn_stop_porcupine);
        btnViewLog = findViewById(R.id.btn_view_log);
        btnSettings = findViewById(R.id.btn_settings);
        btnLogout = findViewById(R.id.btn_logout);

        // Set Listeners
        btnStartListening.setOnClickListener(v -> checkPermissionsAndStartService());
        btnStopListening.setOnClickListener(v -> stopSTTService());
        btnViewLog.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, AlertLogActivity.class)));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));
        btnLogout.setOnClickListener(v -> logoutUser());

        buildRequiredPermissionsList();
        loadUserDataAndStats();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserDataAndStats();
        // *** جديد: تسجيل الـ BroadcastReceiver عند استئناف النشاط ***
        LocalBroadcastManager.getInstance(this).registerReceiver(
                recognizedTextReceiver,
                new IntentFilter("com.example.keywordlistenerjava.RECOGNIZED_TEXT")
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        // *** جديد: إلغاء تسجيل الـ BroadcastReceiver عند إيقاف النشاط مؤقتاً ***
        LocalBroadcastManager.getInstance(this).unregisterReceiver(recognizedTextReceiver);
    }

    private void loadUserDataAndStats() {
        dbExecutor.execute(() -> {
            userDao.open();
            User currentUser = userDao.getUserById(currentUserId);
            userDao.close();

            alertLogDao.open();
            int total = alertLogDao.getTotalAlertsCount(currentUserId);
            int real = alertLogDao.getRealAlertsCount(currentUserId);
            int aFalse = alertLogDao.getFalseAlertsCount(currentUserId);
            alertLogDao.close();

            runOnUiThread(() -> {
                if (currentUser != null) {
                    tvWelcome.setText("مرحبًا، " + currentUser.getFirstName() + " " + currentUser.getLastName());
                } else {
                    tvWelcome.setText("مرحبًا أيها المستخدم!");
                }
                tvTotalAlerts.setText("عدد البلاغات الإجمالي: " + total);
                tvRealAlerts.setText("عدد البلاغات الحقيقية: " + real);
                tvFalseAlerts.setText("عدد البلاغات الكاذبة: " + aFalse);
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
        stopSTTService();
        prefsHelper.logoutUser();
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbExecutor.shutdownNow();
    }
}