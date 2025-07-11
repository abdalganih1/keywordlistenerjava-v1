//package com.example.keywordlistenerjava.service;
//
//// *** استيرادات Picovoice Porcupine ***
//import ai.picovoice.porcupine.PorcupineActivationException;
//import ai.picovoice.porcupine.PorcupineActivationLimitException;
//import ai.picovoice.porcupine.PorcupineActivationRefusedException;
//import ai.picovoice.porcupine.PorcupineActivationThrottledException;
//import ai.picovoice.porcupine.PorcupineException;
//import ai.picovoice.porcupine.PorcupineIOException;
//import ai.picovoice.porcupine.PorcupineInvalidArgumentException;
//import ai.picovoice.porcupine.PorcupineKeyException;
//import ai.picovoice.porcupine.PorcupineManager;
//import ai.picovoice.porcupine.PorcupineManagerCallback;
//import ai.picovoice.porcupine.PorcupineMemoryException;
//import ai.picovoice.porcupine.PorcupineRuntimeException;
//import ai.picovoice.porcupine.PorcupineStopIterationException;
//
//// استيرادات Android Framework
//import android.Manifest;
//import android.annotation.SuppressLint;
//import android.app.Notification;
//import android.app.NotificationChannel;
//import android.app.NotificationManager;
//import android.app.PendingIntent;
//import android.app.Service;
//import android.content.Context;
//import android.content.Intent;
//import android.content.pm.PackageManager;
//import android.content.res.AssetManager;
//import android.location.Location; // استيراد كلاس Location الصحيح
//import android.os.Build;
//import android.os.Handler;
//import android.os.IBinder;
//import android.os.Looper;
//import android.telephony.SmsManager;
//import android.util.Log;
//import android.widget.Toast;
//
//// استيرادات AndroidX
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//import androidx.core.app.NotificationCompat;
//import androidx.core.content.ContextCompat;
//
//// استيرادات Google Play Services (للموقع)
//import com.google.android.gms.location.FusedLocationProviderClient; // لم يعد يستخدم مباشرة، ولكن جيد للتأكد
//import com.google.android.gms.location.LocationServices;
//import com.google.android.gms.location.Priority;
//import com.google.android.gms.tasks.CancellationTokenSource;
//import com.google.android.gms.tasks.OnFailureListener; // استيراد OnFailureListener
//import com.google.android.gms.tasks.OnSuccessListener; // استيراد OnSuccessListener
//import com.google.android.gms.tasks.Task; // استيراد Task
//
//// استيرادات من كود المشروع
//import com.example.keywordlistenerjava.R;
//import com.example.keywordlistenerjava.activity.MainActivity;
//import com.example.keywordlistenerjava.db.dao.AlertLogDao;
//import com.example.keywordlistenerjava.db.dao.KeywordNumberLinkDao;
//import com.example.keywordlistenerjava.db.dao.UserDao;
//import com.example.keywordlistenerjava.db.entity.AlertLog;
//import com.example.keywordlistenerjava.db.entity.EmergencyNumber;
//import com.example.keywordlistenerjava.db.entity.User;
//import com.example.keywordlistenerjava.util.LocationHelper; // تأكد من وجود هذا الملف
//import com.example.keywordlistenerjava.util.SharedPreferencesHelper;
//
//// استيرادات Java قياسية
//import java.io.BufferedInputStream;
//import java.io.BufferedOutputStream;
//import java.io.File;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//import java.text.SimpleDateFormat;
//import java.util.ArrayList;
//import java.util.Date;
//import java.util.List;
//import java.util.Locale;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
//public class PorcupineService extends Service {
//
//    // --- ثوابت الخدمة ---
//    private static final String TAG = "PorcupineService";
//    private static final String NOTIFICATION_CHANNEL_ID = "PorcupineServiceChannel";
//    private static final int NOTIFICATION_ID = 2;
//    private static final String PICOVOICE_ACCESS_KEY = "YOUR_ACCESS_KEY_HERE"; // <--- ضع مفتاحك هنا
//    private static final String KEYWORD_ASSET_NAME = "marhaban_android.ppn";
//    private static final String MODEL_ASSET_NAME = "porcupine_params_ar.pv";
//    private static final String TARGET_PHONE_NUMBER_DEFAULT = "0000000000";
//
//    // --- متغيرات الخدمة ---
//    private PorcupineManager porcupineManager;
//    private Handler mainThreadHandler;
//    private ExecutorService executorService;
//
//    // DAOs (Data Access Objects)
//    private AlertLogDao alertLogDao;
//    private KeywordNumberLinkDao keywordNumberLinkDao;
//    private UserDao userDao;
//
//    // Helpers
//    private SharedPreferencesHelper prefsHelper;
//    private LocationHelper locationHelper;
//
//    // --- 1. دورة حياة الخدمة ---
//
//    @Override
//    public void onCreate() {
//        super.onCreate();
//        Log.i(TAG, "onCreate: Porcupine Service creating...");
//
//        mainThreadHandler = new Handler(Looper.getMainLooper());
//        executorService = Executors.newSingleThreadExecutor();
//
//        alertLogDao = new AlertLogDao(this);
//        keywordNumberLinkDao = new KeywordNumberLinkDao(this);
//        userDao = new UserDao(this);
//        prefsHelper = new SharedPreferencesHelper(this);
//        locationHelper = new LocationHelper(this); // تهيئة LocationHelper
//
//        createNotificationChannel();
//
//        executorService.execute(this::initPorcupine);
//
//        Log.i(TAG, "onCreate: Porcupine Service initialization submitted.");
//    }
//
//    @Override
//    public int onStartCommand(Intent intent, int flags, int startId) {
//        Log.i(TAG, "onStartCommand: Porcupine Service starting...");
//
//        Notification notification = createNotification("Porcupine يستمع لكلمة مفتاحية...");
//        startForeground(NOTIFICATION_ID, notification);
//        Log.d(TAG, "onStartCommand: Service started in foreground.");
//
//        startPorcupineListening();
//
//        return START_STICKY;
//    }
//
//    @Override
//    public void onDestroy() {
//        Log.i(TAG, "onDestroy: Porcupine Service destroying...");
//        stopPorcupineListening();
//
//        if (executorService != null && !executorService.isShutdown()) {
//            executorService.shutdownNow();
//            Log.i(TAG, "onDestroy: ExecutorService shutdown.");
//        }
//
//        stopForeground(true);
//        Log.i(TAG, "onDestroy: Porcupine Service destroyed.");
//        super.onDestroy();
//    }
//
//    @Nullable
//    @Override
//    public IBinder onBind(Intent intent) {
//        return null;
//    }
//
//    // --- 2. تهيئة وبدء/إيقاف Porcupine ---
//
//    private void initPorcupine() {
//        Log.d(TAG, "initPorcupine: Attempting to initialize Porcupine...");
//        try {
//            String keywordPath = extractResource(this, KEYWORD_ASSET_NAME);
//            String modelPath = extractResource(this, MODEL_ASSET_NAME);
//
//            if (keywordPath == null || modelPath == null) {
//                Log.e(TAG, "initPorcupine: Failed to extract necessary Porcupine resources from assets.");
//                handlePorcupineError(new PorcupineIOException("Failed to extract resources."));
//                return;
//            }
//            Log.d(TAG, "initPorcupine: Keyword Path: " + keywordPath);
//            Log.d(TAG, "initPorcupine: Model Path: " + modelPath);
//
//            porcupineManager = new PorcupineManager.Builder()
//                    .setAccessKey(PICOVOICE_ACCESS_KEY)
//                    .setModelPath(modelPath)
//                    .setKeywordPaths(new String[]{keywordPath})
//                    .setSensitivity(0.7f)
//                    .build(getApplicationContext(), new PorcupineManagerCallback() {
//                        @Override
//                        public void invoke(int keywordIndex) {
//                            Log.i(TAG, ">>> Porcupine Keyword Detected! Index: " + keywordIndex + " <<<");
//                            mainThreadHandler.post(() -> onKeywordDetected());
//                        }
//                    });
//            Log.i(TAG, "initPorcupine: PorcupineManager initialized successfully.");
//            startPorcupineListening();
//
//        } catch (PorcupineException e) {
//            Log.e(TAG, "initPorcupine: Failed to initialize Porcupine. Error: " + e.getMessage(), e);
//            handlePorcupineError(e);
//        } catch (IOException e) {
//            Log.e(TAG, "initPorcupine: IOException during resource extraction: " + e.getMessage(), e);
//            handlePorcupineError(new PorcupineIOException("IOException during resource extraction: " + e.getMessage()));
//        }
//    }
//
//    private String extractResource(Context context, String resourceName) throws IOException {
//        AssetManager assetManager = context.getAssets();
//        InputStream is = null;
//        OutputStream os = null;
//        File outputFile = null;
//        File cacheDir = context.getCacheDir();
//        if (!cacheDir.exists()) {
//            cacheDir.mkdirs();
//        }
//        try {
//            is = new BufferedInputStream(assetManager.open(resourceName), 256);
//            outputFile = new File(cacheDir, resourceName);
//            os = new BufferedOutputStream(new java.io.FileOutputStream(outputFile), 256);
//
//            int r;
//            byte[] buffer = new byte[256];
//            while ((r = is.read(buffer, 0, 256)) != -1) {
//                os.write(buffer, 0, r);
//            }
//            os.flush();
//            Log.d(TAG, "Extracted resource '" + resourceName + "' to: " + outputFile.getAbsolutePath());
//            return outputFile.getAbsolutePath();
//        } catch (IOException e) {
//            Log.e(TAG, "Failed to extract resource: " + resourceName, e);
//            throw e;
//        } finally {
//            if (os != null) {
//                try {
//                    os.close();
//                } catch (IOException e) {
//                    Log.e(TAG, "Error closing output stream", e);
//                }
//            }
//            if (is != null) {
//                try {
//                    is.close();
//                } catch (IOException e) {
//                    Log.e(TAG, "Error closing input stream", e);
//                }
//            }
//        }
//    }
//
//    private void startPorcupineListening() {
//        if (porcupineManager != null) {
//            try {
//                porcupineManager.start();
//                Log.i(TAG, "startPorcupineListening: Porcupine processing started.");
//            } catch (PorcupineException e) {
//                Log.e(TAG, "startPorcupineListening: Failed to start Porcupine processing: " + e.getMessage(), e);
//                handlePorcupineError(e);
//            }
//        } else {
//            Log.w(TAG, "startPorcupineListening: PorcupineManager is not initialized yet. Will retry after init.");
//        }
//    }
//
//    private void stopPorcupineListening() {
//        if (porcupineManager != null) {
//            try {
//                porcupineManager.stop();
//                Log.i(TAG, "stopPorcupineListening: Porcupine processing stopped.");
//                porcupineManager.delete();
//                porcupineManager = null;
//                Log.i(TAG, "stopPorcupineListening: Porcupine resources released.");
//            } catch (PorcupineException e) {
//                Log.e(TAG, "stopPorcupineListening: Failed to stop/delete Porcupine: " + e.getMessage(), e);
//                if (porcupineManager != null) {
//                    porcupineManager.delete();
//                    porcupineManager = null;
//                }
//            }
//        }
//    }
//
//    // --- 3. إعداد الإشعارات ---
//
//    private void createNotificationChannel() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            NotificationChannel serviceChannel = new NotificationChannel(
//                    NOTIFICATION_CHANNEL_ID,
//                    "Porcupine Service Channel",
//                    NotificationManager.IMPORTANCE_LOW
//            );
//            serviceChannel.setDescription("Notifications for Porcupine keyword listener background service.");
//            NotificationManager manager = getSystemService(NotificationManager.class);
//            if (manager != null) {
//                manager.createNotificationChannel(serviceChannel);
//            } else {
//                Log.e(TAG, "createNotificationChannel: NotificationManager is null.");
//            }
//        }
//    }
//
//    private Notification createNotification(String contentText) {
//        Intent notificationIntent = new Intent(this, MainActivity.class);
//        PendingIntent pendingIntent = PendingIntent.getActivity(this,
//                0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
//
//        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
//                .setContentTitle("Keyword Listener (Porcupine)")
//                .setContentText(contentText)
//                // تأكد من وجود ic_mic_on في مجلد res/drawable
//                .setSmallIcon(R.drawable.ic_mic_on)
//                .setContentIntent(pendingIntent)
//                .setOngoing(true)
//                .setOnlyAlertOnce(true)
//                .setPriority(NotificationCompat.PRIORITY_LOW)
//                .build();
//    }
//
//    private void updateNotification(String contentText) {
//        if (porcupineManager != null) {
//            Notification notification = createNotification(contentText);
//            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
//            if (manager != null) {
//                manager.notify(NOTIFICATION_ID, notification);
//                Log.d(TAG, "Porcupine Notification updated: " + contentText);
//            }
//        } else {
//            Log.w(TAG, "Skipped notification update as PorcupineManager is null (service stopping or failed to initialize).");
//        }
//    }
//
//    // --- 4. الإجراء عند اكتشاف الكلمة المفتاحية ---
//    private void onKeywordDetected() {
//        Log.i(TAG, "onKeywordDetected: Action triggered!");
//
//        mainThreadHandler.post(() ->
//                Toast.makeText(getApplicationContext(), "Porcupine اكتشف الكلمة!", Toast.LENGTH_SHORT).show());
//        updateNotification("Porcupine رصد الكلمة! جارٍ الحصول على الموقع...");
//
//        final int currentUserId = prefsHelper.getLoggedInUserId(); // جعلها final
//        if (currentUserId == -1) {
//            Log.e(TAG, "onKeywordDetected: No user logged in. Cannot process alert.");
//            mainThreadHandler.post(() -> updateNotification("خطأ: لا يوجد مستخدم مسجل الدخول."));
//            return;
//        }
//
//        executorService.execute(() -> {
//            // المتغيرات التي سيتم استخدامها داخل الـ OnSuccessListener يجب أن تكون Effectively Final
//            final User[] userHolder = {null}; // استخدام مصفوفة لتجاوز قيود final
//            final List<EmergencyNumber> recipientNumbersHolder = new ArrayList<>();
//            final String detectedKeyword = KEYWORD_ASSET_NAME.replace(".ppn", ""); // الكلمة المكتشفة النهائية
//
//            userDao.open();
//            userHolder[0] = userDao.getUserById(currentUserId);
//            userDao.close();
//
//            if (userHolder[0] == null) {
//                Log.e(TAG, "onKeywordDetected: Current user not found in DB! ID: " + currentUserId);
//                mainThreadHandler.post(() -> updateNotification("خطأ: بيانات المستخدم غير موجودة."));
//                return;
//            }
//
//            keywordNumberLinkDao.open();
//            recipientNumbersHolder.addAll(keywordNumberLinkDao.getEmergencyNumbersForKeyword(currentUserId, detectedKeyword));
//            keywordNumberLinkDao.close();
//
//            if (recipientNumbersHolder.isEmpty()) {
//                Log.w(TAG, "onKeywordDetected: No emergency numbers linked for user " + currentUserId + " with keyword " + detectedKeyword);
//                mainThreadHandler.post(() -> updateNotification("لا توجد أرقام طوارئ مرتبطة بالكلمة المفتاحية."));
//                return;
//            }
//
//            // --- الحصول على الموقع وإرسال الرسالة ---
//            locationHelper.getCurrentLocation() // تم حذف executorService كوسيط
//                    .addOnSuccessListener(new OnSuccessListener<Location>() { // استخدام كلاس داخلي لتجنب مشاكل final
//                        @Override
//                        public void onSuccess(Location location) {
//                            String mapLink = "غير متاح";
//                            double lat = 0.0;
//                            double lon = 0.0;
//                            if (location != null) {
//                                lat = location.getLatitude();
//                                lon = location.getLongitude();
//                                mapLink = LocationHelper.generateGoogleMapsLink(lat, lon);
//                                Log.d(TAG, "Location obtained: " + lat + ", " + lon + " Map Link: " + mapLink);
//                            } else {
//                                Log.w(TAG, "Location is null for the alert.");
//                            }
//
//                            // بناء الرسالة المطلوبة
//                            final String messageText = String.format(Locale.getDefault(),
//                                    "لقد استعمل %s %s الكلمة المفتاحية (%s) في الموقع الجغرافي على الخريطة (%s)",
//                                    userHolder[0].getFirstName(), userHolder[0].getLastName(), detectedKeyword, mapLink);
//
//                            // إرسال الرسالة لجميع المستلمين
//                            for (EmergencyNumber number : recipientNumbersHolder) {
//                                sendActionMessage(number.getPhoneNumber(), messageText);
//                            }
//
//                            // تسجيل البلاغ في قاعدة البيانات
//                            AlertLog newAlert = new AlertLog();
//                            newAlert.setUserId(currentUserId); // currentUserId هو final، لا مشكلة
//                            newAlert.setKeywordUsed(detectedKeyword);
//                            newAlert.setLatitude(lat);
//                            newAlert.setLongitude(lon);
//                            newAlert.setMapLink(mapLink);
//
//                            alertLogDao.open();
//                            long logId = alertLogDao.addAlertLog(newAlert);
//                            alertLogDao.close();
//
//                            if (logId != -1) {
//                                Log.i(TAG, "Alert successfully logged with ID: " + logId);
//                                mainThreadHandler.post(() -> updateNotification("تم إرسال التنبيه وتسجيل البلاغ."));
//                            } else {
//                                Log.e(TAG, "Failed to log alert to database.");
//                                mainThreadHandler.post(() -> updateNotification("تم إرسال التنبيه، لكن فشل تسجيل البلاغ."));
//                            }
//                        }
//                    })
//                    .addOnFailureListener(new OnFailureListener() { // استخدام كلاس داخلي
//                        @Override
//                        public void onFailure(@NonNull Exception e) { // تصحيح: e هو Exception، و addOnFailureListener يتوقع NonNull
//                            Log.e(TAG, "Failed to get location for alert: " + e.getMessage(), e);
//                            mainThreadHandler.post(() -> updateNotification("خطأ في تحديد الموقع. لم يتم إرسال التنبيه."));
//                        }
//                    });
//        });
//    }
//
//    // --- 5. إرسال رسالة SMS ---
//    private void sendActionMessage(String recipientPhoneNumber, String messageContent) {
//        Log.d(TAG, "sendActionMessage: Preparing SMS to " + recipientPhoneNumber);
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
//            Log.e(TAG, "sendActionMessage: SEND_SMS permission missing!");
//            mainThreadHandler.post(() -> Toast.makeText(getApplicationContext(), "لا يوجد إذن لإرسال الرسائل القصيرة", Toast.LENGTH_LONG).show());
//            return;
//        }
//
//        try {
//            SmsManager smsManager = SmsManager.getDefault();
//            ArrayList<String> parts = smsManager.divideMessage(messageContent);
//            smsManager.sendMultipartTextMessage(recipientPhoneNumber, null, parts, null, null);
//            Log.i(TAG, "sendActionMessage: SMS sent successfully to " + recipientPhoneNumber);
//        } catch (Exception e) {
//            Log.e(TAG, "sendActionMessage: Failed to send SMS to " + recipientPhoneNumber + ": " + e.getMessage(), e);
//            mainThreadHandler.post(() -> Toast.makeText(getApplicationContext(), "فشل إرسال الرسالة إلى " + recipientPhoneNumber + ": " + e.getMessage(), Toast.LENGTH_LONG).show());
//        }
//    }
//
//    // --- 6. معالجة أخطاء Porcupine ---
//    private void handlePorcupineError(PorcupineException e) {
//        Log.e(TAG, "Porcupine Error: " + e.getMessage(), e);
//        String userMessage = "حدث خطأ في خدمة Porcupine: ";
//        if (e instanceof PorcupineActivationException) { userMessage += "خطأ في التفعيل."; }
//        else if (e instanceof PorcupineActivationLimitException) { userMessage += "تم تجاوز حد التفعيل الشهري للجهاز."; }
//        else if (e instanceof PorcupineActivationRefusedException) { userMessage += "تم رفض التفعيل (تحقق من AccessKey)."; }
//        else if (e instanceof PorcupineActivationThrottledException) { userMessage += "تم تجاوز حد طلبات التفعيل (حاول لاحقًا)."; }
//        else if (e instanceof PorcupineInvalidArgumentException) { userMessage += "وسيط غير صالح (تحقق من AccessKey أو ملفات النموذج/الكلمة)."; }
//        else if (e instanceof PorcupineIOException) { userMessage += "خطأ في قراءة ملف النموذج/الكلمة."; }
//        else if (e instanceof PorcupineKeyException) { userMessage += "مفتاح الوصول غير صالح أو منتهي الصلاحية."; }
//        else if (e instanceof PorcupineMemoryException) { userMessage += "خطأ في تخصيص الذاكرة."; }
//        else if (e instanceof PorcupineRuntimeException) { userMessage += "خطأ في وقت التشغيل."; }
//        else if (e instanceof PorcupineStopIterationException) { userMessage += "خطأ في معالجة الصوت."; }
//        else { userMessage += e.getClass().getSimpleName(); }
//
//        final String finalUserMessage = userMessage;
//        mainThreadHandler.post(() -> {
//            Toast.makeText(getApplicationContext(), finalUserMessage, Toast.LENGTH_LONG).show();
//            updateNotification(finalUserMessage);
//        });
//
//        if (!(e instanceof PorcupineActivationThrottledException)) {
//            Log.w(TAG, "Stopping service due to critical Porcupine error.");
//            stopSelf();
//        }
//    }
//}