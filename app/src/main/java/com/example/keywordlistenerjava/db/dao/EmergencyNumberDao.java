package com.example.keywordlistenerjava.db.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.example.keywordlistenerjava.db.DatabaseHelper;
import com.example.keywordlistenerjava.db.entity.EmergencyNumber;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EmergencyNumberDao {
    private static final String TAG = "EmergencyNumberDao";
    private SQLiteDatabase db;
    private DatabaseHelper dbHelper;

    private String[] allColumns = {
            DatabaseHelper.COLUMN_NUMBER_ID,
            DatabaseHelper.COLUMN_NUMBER_PHONE,
            DatabaseHelper.COLUMN_NUMBER_DESC,
            DatabaseHelper.COLUMN_COMMON_USER_ID,
            DatabaseHelper.COLUMN_NUMBER_IS_DEFAULT,
            DatabaseHelper.COLUMN_NUMBER_ADDED_DATE
    };

    public EmergencyNumberDao(Context context) {
        dbHelper = new DatabaseHelper(context);
    }

    public void open() {
        db = dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
    }

    public long addEmergencyNumber(EmergencyNumber number) {
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_NUMBER_PHONE, number.getPhoneNumber());
        values.put(DatabaseHelper.COLUMN_NUMBER_DESC, number.getNumberDescription());
        if (number.getUserId() != null) {
            values.put(DatabaseHelper.COLUMN_COMMON_USER_ID, number.getUserId());
        } else {
            values.putNull(DatabaseHelper.COLUMN_COMMON_USER_ID);
        }
        values.put(DatabaseHelper.COLUMN_NUMBER_IS_DEFAULT, number.isDefault() ? 1 : 0);
        values.put(DatabaseHelper.COLUMN_NUMBER_ADDED_DATE, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));

        return db.insert(DatabaseHelper.TABLE_EMERGENCY_NUMBERS, null, values);
    }

    public EmergencyNumber getEmergencyNumberById(int numberId) {
        Cursor cursor = null;
        EmergencyNumber number = null;
        try {
            cursor = db.query(
                    DatabaseHelper.TABLE_EMERGENCY_NUMBERS,
                    allColumns,
                    DatabaseHelper.COLUMN_NUMBER_ID + " = ?",
                    new String[]{String.valueOf(numberId)},
                    null, null, null
            );
            if (cursor != null && cursor.moveToFirst()) {
                number = cursorToEmergencyNumber(cursor);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting emergency number by ID: " + numberId, e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return number;
    }

    /**
     * Retrieves an emergency number by its phone number.
     * @param phoneNumber The phone number to search for.
     * @return An EmergencyNumber object if found, otherwise null.
     */
    public EmergencyNumber getEmergencyNumberByPhoneNumber(String phoneNumber) { // *** الدالة الجديدة ***
        Cursor cursor = null;
        EmergencyNumber number = null;
        try {
            cursor = db.query(
                    DatabaseHelper.TABLE_EMERGENCY_NUMBERS,
                    allColumns,
                    DatabaseHelper.COLUMN_NUMBER_PHONE + " = ?",
                    new String[]{phoneNumber},
                    null, null, null
            );
            if (cursor != null && cursor.moveToFirst()) {
                number = cursorToEmergencyNumber(cursor);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting emergency number by phone number: " + phoneNumber, e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return number;
    }

    public List<EmergencyNumber> getAllEmergencyNumbersForUser(int userId) {
        List<EmergencyNumber> numbers = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    DatabaseHelper.TABLE_EMERGENCY_NUMBERS,
                    allColumns,
                    DatabaseHelper.COLUMN_COMMON_USER_ID + " = ? OR " + DatabaseHelper.COLUMN_NUMBER_IS_DEFAULT + " = 1",
                    new String[]{String.valueOf(userId)},
                    null, null, null
            );
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                numbers.add(cursorToEmergencyNumber(cursor));
                cursor.moveToNext();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting all emergency numbers for user: " + userId, e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return numbers;
    }

    public List<EmergencyNumber> getAllDefaultNumbers() {
        List<EmergencyNumber> numbers = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    DatabaseHelper.TABLE_EMERGENCY_NUMBERS,
                    allColumns,
                    DatabaseHelper.COLUMN_NUMBER_IS_DEFAULT + " = 1",
                    null, null, null, null
            );
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                numbers.add(cursorToEmergencyNumber(cursor));
                cursor.moveToNext();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting all default numbers.", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return numbers;
    }

    public int deleteEmergencyNumber(int numberId) {
        return db.delete(
                DatabaseHelper.TABLE_EMERGENCY_NUMBERS,
                DatabaseHelper.COLUMN_NUMBER_ID + " = ?",
                new String[]{String.valueOf(numberId)}
        );
    }

    private EmergencyNumber cursorToEmergencyNumber(Cursor cursor) {
        EmergencyNumber number = new EmergencyNumber();
        number.setNumberId(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_NUMBER_ID)));
        number.setPhoneNumber(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_NUMBER_PHONE)));
        number.setNumberDescription(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_NUMBER_DESC)));

        int userIdIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_COMMON_USER_ID);
        if (!cursor.isNull(userIdIndex)) {
            number.setUserId(cursor.getInt(userIdIndex));
        } else {
            number.setUserId(null);
        }

        number.setDefault(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_NUMBER_IS_DEFAULT)) == 1);
        number.setAddedDate(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_NUMBER_ADDED_DATE)));
        return number;
    }
}