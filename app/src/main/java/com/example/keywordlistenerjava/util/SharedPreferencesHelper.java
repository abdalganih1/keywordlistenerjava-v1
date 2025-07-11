package com.example.keywordlistenerjava.util;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPreferencesHelper {
    private static final String PREF_NAME = "SecurityAppPrefs";
    private static final String KEY_LOGGED_IN_USER_ID = "loggedInUserId";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";

    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;

    public SharedPreferencesHelper(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    public void setLoggedInUser(int userId) {
        editor.putInt(KEY_LOGGED_IN_USER_ID, userId);
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.apply(); // Apply changes asynchronously
    }

    public int getLoggedInUserId() {
        return sharedPreferences.getInt(KEY_LOGGED_IN_USER_ID, -1); // -1 if no user logged in
    }

    public boolean isLoggedIn() {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public void logoutUser() {
        editor.remove(KEY_LOGGED_IN_USER_ID);
        editor.remove(KEY_IS_LOGGED_IN);
        editor.apply();
    }
}