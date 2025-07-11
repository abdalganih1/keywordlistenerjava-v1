package com.example.keywordlistenerjava.db.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.example.keywordlistenerjava.db.DatabaseHelper;
import com.example.keywordlistenerjava.db.entity.Keyword;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class KeywordDao {
    private static final String TAG = "KeywordDao";
    private SQLiteDatabase db;
    private DatabaseHelper dbHelper;

    private String[] allColumns = {
            DatabaseHelper.COLUMN_KEYWORD_ID,
            DatabaseHelper.COLUMN_KEYWORD_TEXT,
            DatabaseHelper.COLUMN_COMMON_USER_ID, // تصحيح: استخدام هذا الثابت لعمود user_id
            DatabaseHelper.COLUMN_KEYWORD_PPN_FILE,
            DatabaseHelper.COLUMN_KEYWORD_IS_DEFAULT,
            DatabaseHelper.COLUMN_KEYWORD_ADDED_DATE
    };

    public KeywordDao(Context context) {
        dbHelper = new DatabaseHelper(context);
    }

    public void open() {
        db = dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
    }

    /**
     * Adds a new keyword to the database.
     * @param keyword The Keyword object to insert.
     * @return The row ID of the newly inserted row, or -1 if an error occurred.
     */
    public long addKeyword(Keyword keyword) {
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_KEYWORD_TEXT, keyword.getKeywordText());
        if (keyword.getUserId() != null) { // If userId is provided (for custom keywords)
            values.put(DatabaseHelper.COLUMN_COMMON_USER_ID, keyword.getUserId()); // تصحيح: استخدام هذا الثابت
        } else {
            values.putNull(DatabaseHelper.COLUMN_COMMON_USER_ID); // For default keywords (null userId)
        }
        values.put(DatabaseHelper.COLUMN_KEYWORD_PPN_FILE, keyword.getPpnFileName());
        values.put(DatabaseHelper.COLUMN_KEYWORD_IS_DEFAULT, keyword.isDefault() ? 1 : 0);
        values.put(DatabaseHelper.COLUMN_KEYWORD_ADDED_DATE, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));

        return db.insert(DatabaseHelper.TABLE_KEYWORDS, null, values);
    }

    /**
     * Retrieves a keyword by its ID.
     * @param keywordId The ID of the keyword.
     * @return A Keyword object if found, otherwise null.
     */
    public Keyword getKeywordById(int keywordId) {
        Cursor cursor = null;
        Keyword keyword = null;
        try {
            cursor = db.query(
                    DatabaseHelper.TABLE_KEYWORDS,
                    allColumns,
                    DatabaseHelper.COLUMN_KEYWORD_ID + " = ?",
                    new String[]{String.valueOf(keywordId)},
                    null, null, null
            );
            if (cursor != null && cursor.moveToFirst()) {
                keyword = cursorToKeyword(cursor);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting keyword by ID: " + keywordId, e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return keyword;
    }

    /**
     * Retrieves a keyword by its text (case-insensitive for comparison).
     * This will get the first matching keyword.
     * @param keywordText The text of the keyword.
     * @return A Keyword object if found, otherwise null.
     */
    public Keyword getKeywordByText(String keywordText) {
        Cursor cursor = null;
        Keyword keyword = null;
        try {
            cursor = db.query(
                    DatabaseHelper.TABLE_KEYWORDS,
                    allColumns,
                    DatabaseHelper.COLUMN_KEYWORD_TEXT + " = ?",
                    new String[]{keywordText}, // Exact match, consider using COLLATE NOCASE for case-insensitivity
                    null, null, null
            );
            if (cursor != null && cursor.moveToFirst()) {
                keyword = cursorToKeyword(cursor);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting keyword by text: " + keywordText, e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return keyword;
    }

    /**
     * Retrieves all keywords associated with a specific user (including default ones if linked).
     * For now, this only retrieves keywords that have the user_id assigned.
     * To get all keywords a user *can* use (user-specific + default system-wide),
     * you'd need a more complex query involving UNION or separate calls.
     * @param userId The ID of the user.
     * @return A list of Keyword objects.
     */
    public List<Keyword> getAllKeywordsForUser(int userId) {
        List<Keyword> keywords = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    DatabaseHelper.TABLE_KEYWORDS,
                    allColumns,
                    DatabaseHelper.COLUMN_COMMON_USER_ID + " = ? OR " + DatabaseHelper.COLUMN_KEYWORD_IS_DEFAULT + " = 1", // تصحيح: استخدام هذا الثابت
                    new String[]{String.valueOf(userId)},
                    null, null, null
            );
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                keywords.add(cursorToKeyword(cursor));
                cursor.moveToNext();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting all keywords for user: " + userId, e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return keywords;
    }

    /**
     * Retrieves all default (system-wide) keywords.
     * @return A list of Keyword objects.
     */
    public List<Keyword> getAllDefaultKeywords() {
        List<Keyword> keywords = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    DatabaseHelper.TABLE_KEYWORDS,
                    allColumns,
                    DatabaseHelper.COLUMN_KEYWORD_IS_DEFAULT + " = 1",
                    null, null, null, null
            );
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                keywords.add(cursorToKeyword(cursor));
                cursor.moveToNext();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting all default keywords.", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return keywords;
    }


    /**
     * Deletes a keyword by its ID.
     * @param keywordId The ID of the keyword to delete.
     * @return The number of rows affected (should be 1 if successful).
     */
    public int deleteKeyword(int keywordId) {
        return db.delete(
                DatabaseHelper.TABLE_KEYWORDS,
                DatabaseHelper.COLUMN_KEYWORD_ID + " = ?",
                new String[]{String.valueOf(keywordId)}
        );
    }

    // Helper method to convert Cursor to Keyword object
    private Keyword cursorToKeyword(Cursor cursor) {
        Keyword keyword = new Keyword();
        keyword.setKeywordId(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_KEYWORD_ID)));
        keyword.setKeywordText(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_KEYWORD_TEXT)));

        int userIdIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_COMMON_USER_ID); // تصحيح: استخدام هذا الثابت
        if (!cursor.isNull(userIdIndex)) {
            keyword.setUserId(cursor.getInt(userIdIndex));
        } else {
            keyword.setUserId(null); // For default keywords
        }

        keyword.setPpnFileName(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_KEYWORD_PPN_FILE)));
        keyword.setDefault(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_KEYWORD_IS_DEFAULT)) == 1);
        keyword.setAddedDate(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_KEYWORD_ADDED_DATE)));
        return keyword;
    }
}