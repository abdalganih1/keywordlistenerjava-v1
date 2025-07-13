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
    public EmergencyNumber getEmergencyNumberByPhoneNumber(String phoneNumber) {
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
        } finally {
            if (cursor != null) cursor.close();
        }
        return number;
    }

    /**
     * Retrieves a specific emergency number for a specific user.
     * @param phoneNumber The phone number to search for.
     * @param userId The ID of the user.
     * @return An EmergencyNumber object if found for the user, otherwise null.
     */
    public EmergencyNumber getEmergencyNumberByPhoneNumberAndUser(String phoneNumber, int userId) {
        Cursor cursor = null;
        EmergencyNumber number = null;
        try {
            cursor = db.query(
                    DatabaseHelper.TABLE_EMERGENCY_NUMBERS,
                    allColumns,
                    DatabaseHelper.COLUMN_NUMBER_PHONE + " = ? AND " + DatabaseHelper.COLUMN_COMMON_USER_ID + " = ?",
                    new String[]{phoneNumber, String.valueOf(userId)},
                    null, null, null
            );
            if (cursor != null && cursor.moveToFirst()) {
                number = cursorToEmergencyNumber(cursor);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting emergency number by phone number for user: " + phoneNumber, e);
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

    /**
     * Updates an existing custom emergency number for a specific user.
     * @param numberId The ID of the number to update.
     * @param newPhoneNumber The new phone number.
     * @param newDescription The new description.
     * @param userId The ID of the user who owns this number.
     * @return The number of rows affected (should be 1 if successful).
     */
    public int updateCustomEmergencyNumber(int numberId, String newPhoneNumber, String newDescription, int userId) {
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_NUMBER_PHONE, newPhoneNumber);
        values.put(DatabaseHelper.COLUMN_NUMBER_DESC, newDescription);

        return db.update(
                DatabaseHelper.TABLE_EMERGENCY_NUMBERS,
                values,
                DatabaseHelper.COLUMN_NUMBER_ID + " = ? AND " + DatabaseHelper.COLUMN_COMMON_USER_ID + " = ?",
                new String[]{String.valueOf(numberId), String.valueOf(userId)}
        );
    }

    /**
     * Deletes a custom emergency number by its ID, ensuring it belongs to the user.
     * @param numberId The ID of the number to delete.
     * @param userId The ID of the user who owns this number.
     * @return The number of rows affected (should be 1 if successful).
     */
    public int deleteCustomEmergencyNumber(int numberId, int userId) {
        // ON DELETE CASCADE in the database will automatically delete related links in KeywordNumberLinks table.
        return db.delete(
                DatabaseHelper.TABLE_EMERGENCY_NUMBERS,
                DatabaseHelper.COLUMN_NUMBER_ID + " = ? AND " + DatabaseHelper.COLUMN_COMMON_USER_ID + " = ?",
                new String[]{String.valueOf(numberId), String.valueOf(userId)}
        );
    }

    /**
     * Retrieves only the custom numbers created by a specific user.
     * @param userId The ID of the user.
     * @return A list of custom EmergencyNumber objects.
     */
    public List<EmergencyNumber> getCustomNumbersForUser(int userId) {
        List<EmergencyNumber> numbers = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    DatabaseHelper.TABLE_EMERGENCY_NUMBERS,
                    allColumns,
                    DatabaseHelper.COLUMN_COMMON_USER_ID + " = ? AND " + DatabaseHelper.COLUMN_NUMBER_IS_DEFAULT + " = 0",
                    new String[]{String.valueOf(userId)},
                    null, null, null
            );
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                numbers.add(cursorToEmergencyNumber(cursor));
                cursor.moveToNext();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting custom numbers for user: " + userId, e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return numbers;
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