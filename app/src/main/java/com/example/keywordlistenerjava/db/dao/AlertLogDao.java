package com.example.keywordlistenerjava.db.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.example.keywordlistenerjava.db.DatabaseHelper;
import com.example.keywordlistenerjava.db.entity.AlertLog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AlertLogDao {
    private static final String TAG = "AlertLogDao";
    private SQLiteDatabase db;
    private DatabaseHelper dbHelper;
    private String[] allColumns = {
            DatabaseHelper.COLUMN_LOG_ID,
            DatabaseHelper.COLUMN_LOG_USER_ID,
            DatabaseHelper.COLUMN_LOG_KEYWORD_USED,
            DatabaseHelper.COLUMN_LOG_DATE,
            DatabaseHelper.COLUMN_LOG_TIME,
            DatabaseHelper.COLUMN_LOG_LATITUDE,
            DatabaseHelper.COLUMN_LOG_LONGITUDE,
            DatabaseHelper.COLUMN_LOG_MAP_LINK,
            DatabaseHelper.COLUMN_LOG_IS_FALSE_ALARM
    };

    public AlertLogDao(Context context) {
        dbHelper = new DatabaseHelper(context);
    }

    public void open() {
        db = dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
    }

    /**
     * Inserts a new alert log into the database.
     * @param alertLog The AlertLog object to insert.
     * @return The row ID of the newly inserted row, or -1 if an error occurred.
     */
    public long addAlertLog(AlertLog alertLog) {
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_LOG_USER_ID, alertLog.getUserId());
        values.put(DatabaseHelper.COLUMN_LOG_KEYWORD_USED, alertLog.getKeywordUsed());
        values.put(DatabaseHelper.COLUMN_LOG_DATE, new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
        values.put(DatabaseHelper.COLUMN_LOG_TIME, new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()));
        values.put(DatabaseHelper.COLUMN_LOG_LATITUDE, alertLog.getLatitude());
        values.put(DatabaseHelper.COLUMN_LOG_LONGITUDE, alertLog.getLongitude());
        values.put(DatabaseHelper.COLUMN_LOG_MAP_LINK, alertLog.getMapLink());
        // isFalseAlarm is initially null, so no need to put it here

        return db.insert(DatabaseHelper.TABLE_ALERT_LOG, null, values);
    }

    /**
     * Retrieves all alert logs for a specific user, ordered by most recent first.
     * @param userId The ID of the user.
     * @return A list of AlertLog objects.
     */
    public List<AlertLog> getAllAlertsForUser(int userId) {
        List<AlertLog> alertLogs = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    DatabaseHelper.TABLE_ALERT_LOG,
                    allColumns,
                    DatabaseHelper.COLUMN_LOG_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)},
                    null, null,
                    DatabaseHelper.COLUMN_LOG_DATE + " DESC, " + DatabaseHelper.COLUMN_LOG_TIME + " DESC"
            );

            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                alertLogs.add(cursorToAlertLog(cursor));
                cursor.moveToNext();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting all alerts for user: " + userId, e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return alertLogs;
    }

    /**
     * Updates the 'is_false_alarm' status of an alert log.
     * This is typically done by an admin or user correcting a log.
     * @param logId The ID of the alert log to update.
     * @param isFalseAlarm True for false alarm, false for real, null to unset.
     * @return The number of rows affected (should be 1 if successful).
     */
    public int updateFalseAlarmStatus(int logId, Boolean isFalseAlarm) {
        ContentValues values = new ContentValues();
        if (isFalseAlarm == null) {
            values.putNull(DatabaseHelper.COLUMN_LOG_IS_FALSE_ALARM);
        } else {
            values.put(DatabaseHelper.COLUMN_LOG_IS_FALSE_ALARM, isFalseAlarm ? 1 : 0);
        }

        return db.update(
                DatabaseHelper.TABLE_ALERT_LOG,
                values,
                DatabaseHelper.COLUMN_LOG_ID + " = ?",
                new String[]{String.valueOf(logId)}
        );
    }

    /**
     * Gets the total count of alerts for a user.
     * @param userId The user's ID.
     * @return Total alert count.
     */
    public int getTotalAlertsCount(int userId) {
        Cursor cursor = null;
        int count = 0;
        try {
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_ALERT_LOG + " WHERE " + DatabaseHelper.COLUMN_LOG_USER_ID + " = ?", new String[]{String.valueOf(userId)});
            if (cursor != null && cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return count;
    }

    /**
     * Gets the count of real alerts for a user.
     * @param userId The user's ID.
     * @return Real alert count.
     */
    public int getRealAlertsCount(int userId) {
        Cursor cursor = null;
        int count = 0;
        try {
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_ALERT_LOG + " WHERE " + DatabaseHelper.COLUMN_LOG_USER_ID + " = ? AND " + DatabaseHelper.COLUMN_LOG_IS_FALSE_ALARM + " = 0", new String[]{String.valueOf(userId)});
            if (cursor != null && cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return count;
    }

    /**
     * Gets the count of false alerts for a user.
     * @param userId The user's ID.
     * @return False alert count.
     */
    public int getFalseAlertsCount(int userId) {
        Cursor cursor = null;
        int count = 0;
        try {
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_ALERT_LOG + " WHERE " + DatabaseHelper.COLUMN_LOG_USER_ID + " = ? AND " + DatabaseHelper.COLUMN_LOG_IS_FALSE_ALARM + " = 1", new String[]{String.valueOf(userId)});
            if (cursor != null && cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return count;
    }

    // Helper method to convert Cursor to AlertLog object
    private AlertLog cursorToAlertLog(Cursor cursor) {
        AlertLog alertLog = new AlertLog();
        alertLog.setLogId(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LOG_ID)));
        alertLog.setUserId(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LOG_USER_ID)));
        alertLog.setKeywordUsed(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LOG_KEYWORD_USED)));
        alertLog.setAlertDate(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LOG_DATE)));
        alertLog.setAlertTime(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LOG_TIME)));
        alertLog.setLatitude(cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LOG_LATITUDE)));
        alertLog.setLongitude(cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LOG_LONGITUDE)));

        int mapLinkIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LOG_MAP_LINK);
        if (!cursor.isNull(mapLinkIndex)) {
            alertLog.setMapLink(cursor.getString(mapLinkIndex));
        } else {
            alertLog.setMapLink(null);
        }

        int isFalseAlarmIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LOG_IS_FALSE_ALARM);
        if (cursor.isNull(isFalseAlarmIndex)) {
            alertLog.setIsFalseAlarm(null);
        } else {
            alertLog.setIsFalseAlarm(cursor.getInt(isFalseAlarmIndex) == 1);
        }
        return alertLog;
    }
}