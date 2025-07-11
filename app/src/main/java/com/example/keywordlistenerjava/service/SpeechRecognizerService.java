package com.example.keywordlistenerjava.service;

import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.location.FusedLocationProviderClient; // لم يعد يستخدم مباشرة هنا، ولكن مفيد في LocationHelper
import com.google.android.gms.location.LocationServices; // لم يعد يستخدم مباشرة هنا
import com.google.android.gms.location.Priority; // لم يعد يستخدم مباشرة هنا
import com.google.android.gms.tasks.CancellationTokenSource; // لم يعد يستخدم مباشرة هنا
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import com.example.keywordlistenerjava.R;
import com.example.keywordlistenerjava.activity.MainActivity;
import com.example.keywordlistenerjava.db.dao.AlertLogDao;
import com.example.keywordlistenerjava.db.dao.KeywordDao;
import com.example.keywordlistenerjava.db.dao.KeywordNumberLinkDao;
import com.example.keywordlistenerjava.db.dao.UserDao;
import com.example.keywordlistenerjava.db.entity.AlertLog;
import com.example.keywordlistenerjava.db.entity.EmergencyNumber;
import com.example.keywordlistenerjava.db.entity.Keyword;
import com.example.keywordlistenerjava.db.entity.User;
import com.example.keywordlistenerjava.util.LocationHelper;
import com.example.keywordlistenerjava.util.SharedPreferencesHelper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SpeechRecognizerService extends Service {

    private static final String TAG = "SpeechRecognizerService";
    private static final String NOTIFICATION_CHANNEL_ID = "SpeechRecognizerServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    private static final String TARGET_PHONE_NUMBER_DEFAULT = "0000000000";

    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private boolean isListening = false;

    private Handler mainThreadHandler;
    private ExecutorService executorService;

    private AlertLogDao alertLogDao;
    private KeywordNumberLinkDao keywordNumberLinkDao;
    private UserDao userDao;
    private KeywordDao keywordDao;

    private SharedPreferencesHelper prefsHelper;
    private LocationHelper locationHelper;

    private List<Keyword> activeKeywords;


    // --- 1. دورة حياة الخدمة ---

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate: SpeechRecognizer Service creating...");

        mainThreadHandler = new Handler(Looper.getMainLooper());
        executorService = Executors.newSingleThreadExecutor();

        alertLogDao = new AlertLogDao(this);
        keywordNumberLinkDao = new KeywordNumberLinkDao(this);
        userDao = new UserDao(this);
        keywordDao = new KeywordDao(this);
        prefsHelper = new SharedPreferencesHelper(this);
        locationHelper = new LocationHelper(this);

        createNotificationChannel();

        setupSpeechRecognizer();

        Log.i(TAG, "onCreate: SpeechRecognizer Service created.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand: SpeechRecognizer Service starting...");

        Notification notification = createNotification("الخدمة تستمع في الخلفية...");
        startForeground(NOTIFICATION_ID, notification);
        Log.d(TAG, "onStartCommand: Service started in foreground.");

        loadActiveKeywordsForUser(); // جلب الكلمات المفتاحية

        if (speechRecognizer != null) {
            startListeningLoop();
        } else {
            Log.e(TAG, "onStartCommand: SpeechRecognizer not initialized. Cannot start listening.");
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy: SpeechRecognizer Service destroying...");
        stopListening();
        if (speechRecognizer != null) {
            mainThreadHandler.post(() -> {
                if (speechRecognizer != null) {
                    speechRecognizer.destroy();
                    speechRecognizer = null;
                    Log.i(TAG, "onDestroy: SpeechRecognizer destroyed.");
                }
            });
        }

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
            Log.i(TAG, "onDestroy: ExecutorService shutdown.");
        }

        stopForeground(true);
        Log.i(TAG, "onDestroy: SpeechRecognizer Service destroyed.");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void loadActiveKeywordsForUser() {
        int currentUserId = prefsHelper.getLoggedInUserId();
        if (currentUserId == -1) {
            Log.e(TAG, "loadActiveKeywordsForUser: No user logged in, cannot load active keywords.");
            activeKeywords = new ArrayList<>();
            return;
        }

        executorService.execute(() -> {
            keywordDao.open();
            activeKeywords = keywordDao.getAllKeywordsForUser(currentUserId);
            keywordDao.close();
            Log.d(TAG, "Loaded " + activeKeywords.size() + " active keywords for user " + currentUserId);
            for (Keyword kw : activeKeywords) {
                Log.d(TAG, "Active Keyword: " + kw.getKeywordText());
            }
        });
    }

    // --- 2. إعداد SpeechRecognizer ---

    private void setupSpeechRecognizer() {
        Log.d(TAG, "setupSpeechRecognizer: Setting up...");
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new KeywordRecognitionListener());
            setupSpeechIntent();
            Log.i(TAG, "setupSpeechRecognizer: Setup complete.");
        } else {
            Log.e(TAG, "setupSpeechRecognizer: Speech Recognition not available on this device. Stopping service.");
            Toast.makeText(getApplicationContext(), "التعرف على الصوت غير متاح على هذا الجهاز.", Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void setupSpeechIntent() {
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        String languagePref = "ar";
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languagePref);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languagePref);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true); // للحصول على نتائج جزئية
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        // *** تحسينات لأوقات الصمت، لمنع التوقف السريع جداً ***
        // هذه القيم بالمللي ثانية
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L); // انتظر 1.5 ثانية على الأقل من الكلام
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L); // بعد الكلام، انتظر 2 ثانية صمت قبل التوقف
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L); // أثناء الكلام، انتظر 3 ثواني صمت قبل التوقف

        // محاولة إضافية لضبط سلوك التعرف (قد لا تعمل على كل الأجهزة)
        // speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        // speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true); // تفضيل الوضع بدون انترنت

        Log.d(TAG, "setupSpeechIntent: Intent configured for language: " + languagePref);
    }

    // --- 3. التحكم في عملية الاستماع ---

    private void startListeningLoop() {
        if (!isListening && speechRecognizer != null) {
            mainThreadHandler.post(() -> {
                try {
                    isListening = true;
                    speechRecognizer.startListening(speechRecognizerIntent);
                    Log.i(TAG, "startListeningLoop: Listener started.");
                } catch (SecurityException se) {
                    Log.e(TAG, "startListeningLoop: SecurityException - Check RECORD_AUDIO permission", se);
                    isListening = false;
                    updateNotification("خطأ: إذن الميكروفون مفقود.");
                    stopSelf();
                } catch (Exception e) {
                    isListening = false;
                    Log.e(TAG, "startListeningLoop: Error starting listener: " + e.getMessage(), e);
                    restartListeningAfterDelay(1000);
                }
            });
        } else {
            Log.w(TAG, "startListeningLoop: Already listening or recognizer is null.");
        }
    }

    private void stopListening() {
        if (isListening && speechRecognizer != null) {
            mainThreadHandler.post(() -> {
                if (speechRecognizer != null) {
                    try {
                        speechRecognizer.stopListening();
                        Log.i(TAG, "stopListening: Listener stopped.");
                    } catch (Exception e) {
                        Log.e(TAG, "stopListening: Error stopping listener: " + e.getMessage(), e);
                    } finally {
                        isListening = false;
                    }
                } else {
                    isListening = false;
                }
            });
        }
    }

    private void restartListeningAfterDelay(long delayMillis) {
        mainThreadHandler.removeCallbacks(this::startListeningLoop);
        mainThreadHandler.postDelayed(this::startListeningLoop, delayMillis);
        Log.d(TAG, "Scheduled listener restart after " + delayMillis + "ms.");
    }

    // --- 4. المستمع لأحداث التعرف على الصوت (RecognitionListener) ---
    private class KeywordRecognitionListener implements RecognitionListener {

        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "Listener: onReadyForSpeech");
            isListening = true;
        }

        @Override
        public void onBeginningOfSpeech() {
            Log.d(TAG, "Listener: onBeginningOfSpeech");
        }

        @Override
        public void onRmsChanged(float rmsdB) { /* تجاهل */ }

        @Override
        public void onBufferReceived(byte[] buffer) { /* تجاهل */ }

        @Override
        public void onEndOfSpeech() {
            Log.d(TAG, "Listener: onEndOfSpeech");
            isListening = false;
            // أعد التشغيل بعد فترة صمت
            restartListeningAfterDelay(500); // زيادة التأخير قليلاً
        }

        @Override
        public void onError(int error) {
            isListening = false;
            String errorMessage = getErrorText(error);
            Log.w(TAG, "Listener: onError - " + errorMessage + " (code: " + error + ")");
            handleRecognitionError(error);
        }

        @Override
        public void onResults(Bundle results) {
            isListening = false;
            Log.d(TAG, "Listener: onResults");
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String recognizedText = matches.get(0);
                Log.i(TAG, "Listener: Recognized text = '" + recognizedText + "'");

                // *** إرسال النص المكتشف إلى MainActivity ***
                Intent intent = new Intent("com.example.keywordlistenerjava.RECOGNIZED_TEXT");
                intent.putExtra("recognized_text", recognizedText);
                LocalBroadcastManager.getInstance(SpeechRecognizerService.this).sendBroadcast(intent);
                // ********************************************

                // *** فحص النص المكتشف مقابل جميع الكلمات المفتاحية النشطة ***
                String lowerCaseRecognizedText = recognizedText.trim().toLowerCase(Locale.ROOT);
                String detectedKeyword = null;

                if (activeKeywords != null && !activeKeywords.isEmpty()) {
                    for (Keyword kw : activeKeywords) {
                        String lowerCaseKeyword = kw.getKeywordText().toLowerCase(Locale.ROOT);
                        if (lowerCaseRecognizedText.contains(lowerCaseKeyword)) {
                            detectedKeyword = kw.getKeywordText(); // نجد الكلمة التي تم التعرف عليها
                            break; // نكتفي بأول كلمة مطابقة
                        }
                    }
                }

                if (detectedKeyword != null) {
                    Log.i(TAG, "Listener: >>> DETECTED KEYWORD: '" + detectedKeyword + "' <<<");
                    onKeywordDetectedAction(detectedKeyword);
                    // لا تعيد التشغيل فوراً هنا، onKeywordDetectedAction ستنتهي وسيعاود الاستماع
                } else {
                    Log.d(TAG, "Listener: No configured keyword found in recognized text.");
                    restartListeningAfterDelay(100); // إذا لم يتم العثور على الكلمة، أعد التشغيل
                }
            } else {
                Log.d(TAG, "Listener: No recognition results found.");
                restartListeningAfterDelay(100); // إذا لم يكن هناك نتائج، أعد التشغيل
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> partialMatches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (partialMatches != null && !partialMatches.isEmpty()) {
                String partialText = partialMatches.get(0);
                Log.d(TAG, "Listener: Partial Results = '" + partialText + "'");
                // *** إرسال النص المكتشف جزئياً إلى MainActivity (يمكن تفعيله إذا لزم الأمر) ***
                Intent intent = new Intent("com.example.keywordlistenerjava.RECOGNIZED_TEXT_PARTIAL");
                intent.putExtra("recognized_text", "(جزئي) " + partialText);
                LocalBroadcastManager.getInstance(SpeechRecognizerService.this).sendBroadcast(intent);
            }
        }

        @Override
        public void onEvent(int eventType, Bundle params) { /* تجاهل */ }

        private String getErrorText(int errorCode) {
            switch (errorCode) {
                case SpeechRecognizer.ERROR_AUDIO: return "Audio recording error";
                case SpeechRecognizer.ERROR_CLIENT: return "Client side error";
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Insufficient permissions";
                case SpeechRecognizer.ERROR_NETWORK: return "Network error";
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Network timeout";
                case SpeechRecognizer.ERROR_NO_MATCH: return "No match";
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "RecognitionService busy";
                case SpeechRecognizer.ERROR_SERVER: return "Error from server";
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "No speech input / Timeout";
                default: return "Unknown error code: " + errorCode;
            }
        }
    }

    private void handleRecognitionError(int error) {
        boolean shouldRestart = false;
        switch (error) {
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                Log.w(TAG, "Network error, retrying after longer delay...");
                restartListeningAfterDelay(5000);
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                // No Match: لم يتم التعرف على الكلام
                shouldRestart = true; // أعد التشغيل
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                // Speech Timeout: لم يتم اكتشاف كلام في الوقت المحدد (مهم للتوقف عند عدم وجود صوت)
                shouldRestart = true; // أعد التشغيل
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                Log.w(TAG, "Recognizer is busy, retrying after a short delay.");
                restartListeningAfterDelay(1000); // تأخير أكبر
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                Log.e(TAG, "Insufficient permissions detected. Stopping service.");
                updateNotification("خطأ: إذن الميكروفون مفقود.");
                stopSelf();
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                Log.e(TAG, "Client error. Attempting restart after delay.");
                restartListeningAfterDelay(2000);
                break;
            default:
                shouldRestart = true;
                break;
        }
        if (shouldRestart) {
            restartListeningAfterDelay(500);
        }
    }

    // --- 5. إعداد الإشعارات ---

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Speech Recognizer Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Notifications for background speech recognition service.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            } else {
                Log.e(TAG, "createNotificationChannel: NotificationManager is null.");
            }
        }
    }

    private Notification createNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Keyword Listener (STT)")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_mic_on)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String contentText) {
        if (speechRecognizer != null) {
            Notification notification = createNotification(contentText);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, notification);
                Log.d(TAG, "Notification updated: " + contentText);
            }
        } else {
            Log.w(TAG, "Skipped notification update as SpeechRecognizer is null (service stopping or failed to initialize).");
        }
    }

    // --- 6. الإجراء عند اكتشاف الكلمة المفتاحية ---
    private void onKeywordDetectedAction(String keyword_used) {
        Log.i(TAG, "onKeywordDetectedAction: Action triggered for keyword: " + keyword_used);

        mainThreadHandler.post(() ->
                Toast.makeText(getApplicationContext(), "تم اكتشاف الكلمة: " + keyword_used, Toast.LENGTH_SHORT).show());
        updateNotification("تم رصد الكلمة! جارٍ الحصول على الموقع...");

        final int currentUserId = prefsHelper.getLoggedInUserId();
        if (currentUserId == -1) {
            Log.e(TAG, "onKeywordDetectedAction: No user logged in. Cannot process alert.");
            mainThreadHandler.post(() -> updateNotification("خطأ: لا يوجد مستخدم مسجل الدخول."));
            return;
        }

        executorService.execute(() -> {
            final User[] userHolder = {null};
            final List<EmergencyNumber> recipientNumbersHolder = new ArrayList<>();
            final String finalDetectedKeyword = keyword_used;

            userDao.open();
            userHolder[0] = userDao.getUserById(currentUserId);
            userDao.close();

            if (userHolder[0] == null) {
                Log.e(TAG, "onKeywordDetectedAction: Current user not found in DB! ID: " + currentUserId);
                mainThreadHandler.post(() -> updateNotification("خطأ: بيانات المستخدم غير موجودة."));
                return;
            }

            keywordNumberLinkDao.open();
            recipientNumbersHolder.addAll(keywordNumberLinkDao.getEmergencyNumbersForKeyword(currentUserId, finalDetectedKeyword));
            keywordNumberLinkDao.close();

            if (recipientNumbersHolder.isEmpty()) {
                Log.w(TAG, "onKeywordDetectedAction: No emergency numbers linked for user " + currentUserId + " with keyword " + finalDetectedKeyword);
                mainThreadHandler.post(() -> updateNotification("لا توجد أرقام طوارئ مرتبطة بالكلمة المفتاحية."));
                return;
            }

            locationHelper.getCurrentLocation()
                    .addOnSuccessListener(new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            String mapLink = "غير متاح";
                            double lat = 0.0;
                            double lon = 0.0;
                            if (location != null) {
                                lat = location.getLatitude();
                                lon = location.getLongitude();
                                // *** إنشاء الرابط غير المرمز أولاً ***
                                String rawMapLink = LocationHelper.generateGoogleMapsLink(lat, lon);
                                Log.d(TAG, "Raw map link: " + rawMapLink);

                                // *** ترميز الرابط قبل إرساله (هذا هو الحل) ***
                                try {
                                    // نستخدم URLEncoder.encode لترميز الرابط
                                    // من المهم تحديد ترميز UTF-8
                                    mapLink = URLEncoder.encode(rawMapLink, StandardCharsets.UTF_8.toString());
                                    // بعض تطبيقات الرسائل قد تحتاج إلى ترميز الجزء الذي يحتوي على الفاصلة فقط
                                    // الطريقة الأكثر موثوقية هي ترميز الرابط كاملاً أو الجزء الحساس منه
                                    // سنستخدم طريقة ترميز الرابط كاملاً، ولكن سنستبدل بعض الأحرف التي لا يجب ترميزها
                                    mapLink = "http://maps.google.com/maps?q=" + lat + "%2C" + lon; // طريقة أكثر أمانًا
                                    Log.d(TAG, "Encoded map link for SMS: " + mapLink);
                                } catch (Exception e) {
                                    Log.e(TAG, "Failed to encode map link, using raw link.", e);
                                    mapLink = rawMapLink; // استخدام الرابط غير المرمز في حالة فشل الترميز
                                }

                            } else {
                                Log.w(TAG, "Location is null for the alert.");
                            }

                            // *** استخدام mapLink المرمز في الرسالة النهائية ***
                            final String finalMapLink = mapLink;
                            final String messageText = String.format(Locale.getDefault(),
                                    "لقد استعمل %s %s الكلمة المفتاحية (%s) في الموقع الجغرافي على الخريطة (%s)",
                                    userHolder[0].getFirstName(), userHolder[0].getLastName(), finalDetectedKeyword, finalMapLink);

                            // ... (باقي الكود لإرسال الرسالة وتسجيل البلاغ) ...
                            for (EmergencyNumber number : recipientNumbersHolder) {
                                sendActionMessage(number.getPhoneNumber(), messageText);
                            }

                            AlertLog newAlert = new AlertLog();
                            newAlert.setUserId(currentUserId);
                            newAlert.setKeywordUsed(finalDetectedKeyword);
                            newAlert.setLatitude(lat);
                            newAlert.setLongitude(lon);
                            newAlert.setMapLink(finalMapLink); // تخزين الرابط المرمز

                            alertLogDao.open();
                            long logId = alertLogDao.addAlertLog(newAlert);
                            alertLogDao.close();

                            if (logId != -1) {
                                Log.i(TAG, "Alert successfully logged with ID: " + logId);
                                mainThreadHandler.post(() -> updateNotification("تم إرسال التنبيه وتسجيل البلاغ."));
                            } else {
                                Log.e(TAG, "Failed to log alert to database.");
                                mainThreadHandler.post(() -> updateNotification("تم إرسال التنبيه، لكن فشل تسجيل البلاغ."));
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e(TAG, "Failed to get location for alert: " + e.getMessage(), e);
                            mainThreadHandler.post(() -> updateNotification("خطأ في تحديد الموقع. لم يتم إرسال التنبيه."));
                        }
                    });
        });
    }

    // --- 7. إرسال رسالة SMS ---
    private void sendActionMessage(String recipientPhoneNumber, String messageContent) {
        Log.d(TAG, "sendActionMessage: Preparing SMS to " + recipientPhoneNumber);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "sendActionMessage: SEND_SMS permission missing!");
            mainThreadHandler.post(() -> Toast.makeText(getApplicationContext(), "لا يوجد إذن لإرسال الرسائل القصيرة", Toast.LENGTH_LONG).show());
            return;
        }

        try {
            SmsManager smsManager = SmsManager.getDefault();
            ArrayList<String> parts = smsManager.divideMessage(messageContent);
            smsManager.sendMultipartTextMessage(recipientPhoneNumber, null, parts, null, null);
            Log.i(TAG, "sendActionMessage: SMS sent successfully to " + recipientPhoneNumber);
        } catch (Exception e) {
            Log.e(TAG, "sendActionMessage: Failed to send SMS to " + recipientPhoneNumber + ": " + e.getMessage(), e);
            mainThreadHandler.post(() -> Toast.makeText(getApplicationContext(), "فشل إرسال الرسالة إلى " + recipientPhoneNumber + ": " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }
}