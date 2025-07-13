package com.example.keywordlistenerjava.service;

// *** استيرادات SpeechRecognizer المدمج ***
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

// *** استيرادات Android Framework ***
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

// *** استيرادات AndroidX ***
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

// *** استيرادات Google Play Services (للموقع) ***
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

// *** استيرادات من كود المشروع ***
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

// *** استيرادات Java قياسية لـ HTTP POST ***
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SpeechRecognizerService extends Service {

    // --- ثوابت الخدمة ---
    private static final String TAG = "SpeechRecognizerService";
    private static final String NOTIFICATION_CHANNEL_ID = "SpeechRecognizerServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    // URL لملف PHP الخاص بك على localhost
    private static final String PHP_RECEIVER_URL = "http://192.168.43.121/security_app/receive_alert.php";

    // --- متغيرات SpeechRecognizer ---
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private boolean isListening = false;
    private boolean isActionInProgress = false; // لمنع تفعيل بلاغ جديد أثناء معالجة بلاغ قائم

    // --- متغيرات الخدمة الأخرى ---
    private Handler mainThreadHandler;
    private ExecutorService executorService;

    // DAOs
    private AlertLogDao alertLogDao;
    private KeywordNumberLinkDao keywordNumberLinkDao;
    private UserDao userDao;
    private KeywordDao keywordDao;

    // Helpers
    private SharedPreferencesHelper prefsHelper;
    private LocationHelper locationHelper;

    // الكلمات المفتاحية النشطة للمستخدم
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

        loadActiveKeywordsForUser();

        if (speechRecognizer != null) {
            startListeningLoop();
        } else {
            Log.e(TAG, "onStartCommand: SpeechRecognizer not initialized. Cannot start listening.");
            Toast.makeText(getApplicationContext(), "خطأ: خدمة التعرف على الصوت غير متاحة.", Toast.LENGTH_LONG).show();
            stopSelf();
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

        // لا تقم بإغلاق executorService هنا لمنع RejectedExecutionException
        // النظام سيقوم بتحرير الموارد عند إنهاء العملية.

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
        final int currentUserId = prefsHelper.getLoggedInUserId();
        if (currentUserId == -1) {
            Log.e(TAG, "loadActiveKeywordsForUser: No user logged in, cannot load active keywords.");
            activeKeywords = new ArrayList<>();
            return;
        }

        executeTask(() -> {
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
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L);

        Log.d(TAG, "setupSpeechIntent: Intent configured for language: " + languagePref);
    }

    // --- 3. التحكم في عملية الاستماع ---

    private void startListeningLoop() {
        if (!isListening && !isActionInProgress && speechRecognizer != null) {
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
            Log.w(TAG, "startListeningLoop: Not starting. isListening=" + isListening + ", isActionInProgress=" + isActionInProgress);
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
            restartListeningAfterDelay(500);
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

                sendBroadcastToActivity("RECOGNIZED_TEXT", recognizedText);

                String lowerCaseRecognizedText = recognizedText.trim().toLowerCase(Locale.ROOT);
                String detectedKeyword = null;

                if (activeKeywords != null && !activeKeywords.isEmpty()) {
                    for (Keyword kw : activeKeywords) {
                        String lowerCaseKeyword = kw.getKeywordText().toLowerCase(Locale.ROOT);
                        if (lowerCaseRecognizedText.contains(lowerCaseKeyword)) {
                            detectedKeyword = kw.getKeywordText();
                            break;
                        }
                    }
                }

                if (detectedKeyword != null && !isActionInProgress) {
                    Log.i(TAG, "Listener: >>> DETECTED KEYWORD: '" + detectedKeyword + "' <<<");
                    onKeywordDetectedAction(detectedKeyword);
                } else {
                    Log.d(TAG, "Listener: No configured keyword found or action in progress.");
                    restartListeningAfterDelay(100);
                }
            } else {
                Log.d(TAG, "Listener: No recognition results found.");
                restartListeningAfterDelay(100);
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> partialMatches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (partialMatches != null && !partialMatches.isEmpty()) {
                String partialText = partialMatches.get(0);
                Log.d(TAG, "Listener: Partial Results = '" + partialText + "'");
                sendBroadcastToActivity("RECOGNIZED_TEXT_PARTIAL", "(جزئي) " + partialText);
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
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                shouldRestart = true;
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                Log.w(TAG, "Recognizer is busy, retrying after a short delay.");
                restartListeningAfterDelay(1000);
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
        isActionInProgress = true; // منع بلاغات جديدة
        stopListening(); // أوقف الاستماع مؤقتًا

        mainThreadHandler.post(() ->
                Toast.makeText(getApplicationContext(), "تم اكتشاف الكلمة: " + keyword_used, Toast.LENGTH_SHORT).show());
        updateNotification("تم رصد الكلمة! جارٍ الحصول على الموقع...");

        final int currentUserId = prefsHelper.getLoggedInUserId();
        if (currentUserId == -1) {
            Log.e(TAG, "onKeywordDetectedAction: No user logged in. Cannot process alert.");
            mainThreadHandler.post(() -> updateNotification("خطأ: لا يوجد مستخدم مسجل الدخول."));
            resumeListeningAfterAction(); // استئناف الاستماع
            return;
        }

        executeTask(() -> {
            final User[] userHolder = {null};
            final List<EmergencyNumber> recipientNumbersHolder = new ArrayList<>();
            final String finalDetectedKeyword = keyword_used;

            userDao.open();
            userHolder[0] = userDao.getUserById(currentUserId);
            userDao.close();

            if (userHolder[0] == null) {
                Log.e(TAG, "onKeywordDetectedAction: Current user not found in DB! ID: " + currentUserId);
                mainThreadHandler.post(() -> updateNotification("خطأ: بيانات المستخدم غير موجودة."));
                resumeListeningAfterAction(); // استئناف الاستماع
                return;
            }

            keywordNumberLinkDao.open();
            recipientNumbersHolder.addAll(keywordNumberLinkDao.getEmergencyNumbersForKeyword(currentUserId, finalDetectedKeyword));
            keywordNumberLinkDao.close();

            if (recipientNumbersHolder.isEmpty()) {
                Log.w(TAG, "onKeywordDetectedAction: No emergency numbers linked for user " + currentUserId + " with keyword " + finalDetectedKeyword);
                mainThreadHandler.post(() -> updateNotification("لا توجد أرقام طوارئ مرتبطة بالكلمة المفتاحية."));
                resumeListeningAfterAction(); // استئناف الاستماع
                return;
            }

            locationHelper.getCurrentLocation()
                    .addOnSuccessListener(location -> {
                        String mapLink = "غير متاح";
                        double lat = 0.0;
                        double lon = 0.0;
                        String locationStringForPhp = "غير متاح";

                        if (location != null) {
                            lat = location.getLatitude();
                            lon = location.getLongitude();
                            mapLink = "http://maps.google.com/maps?q=" + lat + "%2C" + lon;
                            locationStringForPhp = String.format(Locale.US, "%.6f,%.6f", lat, lon);
                        } else {
                            Log.w(TAG, "Location is null for the alert.");
                        }

                        final String smsMessageText = String.format(Locale.getDefault(),
                                "لقد استعمل %s %s الكلمة المفتاحية (%s) في الموقع الجغرافي على الخريطة (%s)",
                                userHolder[0].getFirstName(), userHolder[0].getLastName(), finalDetectedKeyword, mapLink);

                        for (EmergencyNumber number : recipientNumbersHolder) {
                            sendActionMessage(number.getPhoneNumber(), smsMessageText);
                        }

                        sendHttpPostRequest(userHolder[0], finalDetectedKeyword, lat, lon, userHolder[0].getResidenceArea(), locationStringForPhp, mapLink);

                        AlertLog newAlert = new AlertLog();
                        newAlert.setUserId(currentUserId);
                        newAlert.setKeywordUsed(finalDetectedKeyword);
                        newAlert.setLatitude(lat);
                        newAlert.setLongitude(lon);
                        newAlert.setMapLink(mapLink);

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

                        resumeListeningAfterAction(); // استئناف الاستماع بعد إكمال جميع المهام
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get location for alert: " + e.getMessage(), e);
                        mainThreadHandler.post(() -> updateNotification("خطأ في تحديد الموقع. لم يتم إرسال التنبيه."));
                        resumeListeningAfterAction(); // استئناف الاستماع حتى لو فشل تحديد الموقع
                    });
        });
    }

    /**
     * يستأنف حلقة الاستماع بعد انتهاء معالجة البلاغ.
     */
    private void resumeListeningAfterAction() {
        isActionInProgress = false;
        Log.d(TAG, "Action finished. Resuming listening loop.");
        restartListeningAfterDelay(2000); // تأخير بسيط قبل الاستماع مرة أخرى
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

    // --- 8. إرسال طلب HTTP POST ---
    private void sendHttpPostRequest(User user, String keyword, double latitude, double longitude, String residenceArea, String locationStringForPhp, String mapLink) {
        executeTask(() -> {
            HttpURLConnection urlConnection = null;
            try {
                URL url = new URL(PHP_RECEIVER_URL);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("POST");
                urlConnection.setDoOutput(true);
                urlConnection.setConnectTimeout(10000);
                urlConnection.setReadTimeout(10000);

                StringBuilder postData = new StringBuilder();
                postData.append(URLEncoder.encode("firstName", StandardCharsets.UTF_8.toString())).append("=").append(URLEncoder.encode(user.getFirstName(), StandardCharsets.UTF_8.toString()));
                postData.append("&").append(URLEncoder.encode("lastName", StandardCharsets.UTF_8.toString())).append("=").append(URLEncoder.encode(user.getLastName(), StandardCharsets.UTF_8.toString()));
                postData.append("&").append(URLEncoder.encode("phoneNumber", StandardCharsets.UTF_8.toString())).append("=").append(URLEncoder.encode(user.getPhoneNumber(), StandardCharsets.UTF_8.toString()));
                postData.append("&").append(URLEncoder.encode("residenceArea", StandardCharsets.UTF_8.toString())).append("=").append(URLEncoder.encode(residenceArea, StandardCharsets.UTF_8.toString()));
                postData.append("&").append(URLEncoder.encode("keywordUsed", StandardCharsets.UTF_8.toString())).append("=").append(URLEncoder.encode(keyword, StandardCharsets.UTF_8.toString()));
                postData.append("&").append(URLEncoder.encode("latitude", StandardCharsets.UTF_8.toString())).append("=").append(URLEncoder.encode(String.valueOf(latitude), StandardCharsets.UTF_8.toString()));
                postData.append("&").append(URLEncoder.encode("longitude", StandardCharsets.UTF_8.toString())).append("=").append(URLEncoder.encode(String.valueOf(longitude), StandardCharsets.UTF_8.toString()));
                postData.append("&").append(URLEncoder.encode("mapLink", StandardCharsets.UTF_8.toString())).append("=").append(URLEncoder.encode(mapLink, StandardCharsets.UTF_8.toString()));

                OutputStream os = urlConnection.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
                writer.write(postData.toString());
                writer.flush();
                writer.close();
                os.close();

                int responseCode = urlConnection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), StandardCharsets.UTF_8));
                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();
                    Log.i(TAG, "HTTP POST Success. Server Response: " + response.toString());
                    mainThreadHandler.post(() -> Toast.makeText(getApplicationContext(), "تم إرسال البلاغ للخادم بنجاح.", Toast.LENGTH_SHORT).show());
                } else {
                    Log.e(TAG, "HTTP POST Failed. Response Code: " + responseCode);
                    mainThreadHandler.post(() -> Toast.makeText(getApplicationContext(), "فشل إرسال البلاغ للخادم. كود: " + responseCode, Toast.LENGTH_LONG).show());
                }

            } catch (Exception e) {
                Log.e(TAG, "HTTP POST Error: " + e.getMessage(), e);
                mainThreadHandler.post(() -> Toast.makeText(getApplicationContext(), "خطأ في الاتصال بالخادم: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
        });
    }

    // دالة مساعدة لتنفيذ المهام على ExecutorService بأمان
    private void executeTask(Runnable task) {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.execute(task);
        } else {
            Log.e(TAG, "executeTask: ExecutorService is not available, task rejected.");
        }
    }

    // دالة مساعدة لإرسال بث محلي للواجهة الأمامية
    private void sendBroadcastToActivity(String action, String message) {
        Intent intent = new Intent(action);
        intent.putExtra("recognized_text", message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}