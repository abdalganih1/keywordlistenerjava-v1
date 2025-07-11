package com.example.keywordlistenerjava.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location; // تأكد من هذا الاستيراد

import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Task;
// لا حاجة لـ ExecutorService هنا في هذه الدالة، ولكن الاستيراد لا يضر
// import java.util.concurrent.ExecutorService;


public class LocationHelper {
    private static final String TAG = "LocationHelper";
    private final FusedLocationProviderClient fusedLocationClient;
    private final Context context;

    public LocationHelper(Context context) {
        this.context = context;
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    /**
     * Gets the current location with high accuracy.
     * This method is asynchronous and returns a Task.
     * Make sure you have ACCESS_FINE_LOCATION permission granted.
     *
     * @return A Task that will yield a Location object or an Exception.
     */
    @SuppressLint("MissingPermission") // Suppressed because permission check is done externally
    public Task<Location> getCurrentLocation() {
        // التحقق من الإذن قبل محاولة طلب الموقع
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted. Cannot get location.");
            // إرجاع Task يفشل فورًا إذا لم يكن الإذن ممنوحًا
            // يتطلب 'com.google.android.gms:play-services-tasks:latest_version'
            return com.google.android.gms.tasks.Tasks.forException(new SecurityException("Location permission not granted."));
        }

        Log.d(TAG, "Requesting current location...");
        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
        // هنا التصحيح: استخدام getToken()
        return fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.getToken() // *** التصحيح هنا: استخدام .getToken() ***
        );
        // .addOnCompleteListener / .addOnSuccessListener are chained outside this method
    }

    /**
     * Generates a Google Maps link for the given latitude and longitude.
     * @param latitude Latitude.
     * @param longitude Longitude.
     * @return A Google Maps URL.
     */
    public static String generateGoogleMapsLink(double latitude, double longitude) {
        return "http://maps.google.com/maps?q=" + latitude + "," + longitude;
    }
}