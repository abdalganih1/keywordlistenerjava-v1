package com.example.keywordlistenerjava.db.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.example.keywordlistenerjava.db.DatabaseHelper;
import com.example.keywordlistenerjava.db.entity.KeywordNumberLink;
import com.example.keywordlistenerjava.db.entity.EmergencyNumber;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class KeywordNumberLinkDao {
    private static final String TAG = "KeywordNumberLinkDao";
    private SQLiteDatabase db;
    private DatabaseHelper dbHelper;

    private String[] allColumns = {
            DatabaseHelper.COLUMN_LINK_ID,
            DatabaseHelper.COLUMN_LINK_KEYWORD_ID,
            DatabaseHelper.COLUMN_LINK_NUMBER_ID,
            DatabaseHelper.COLUMN_LINK_USER_ID,
            DatabaseHelper.COLUMN_LINK_IS_ACTIVE,
            DatabaseHelper.COLUMN_LINK_CREATED_AT
    };

    public KeywordNumberLinkDao(Context context) {
        dbHelper = new DatabaseHelper(context);
    }

    public void open() {
        db = dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
    }

    public long addLink(KeywordNumberLink link) {
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_LINK_KEYWORD_ID, link.getKeywordId());
        values.put(DatabaseHelper.COLUMN_LINK_NUMBER_ID, link.getNumberId());
        values.put(DatabaseHelper.COLUMN_LINK_USER_ID, link.getUserId());
        values.put(DatabaseHelper.COLUMN_LINK_IS_ACTIVE, link.isActive() ? 1 : 0);
        values.put(DatabaseHelper.COLUMN_LINK_CREATED_AT, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));

        return db.insert(DatabaseHelper.TABLE_KEYWORD_NUMBER_LINKS, null, values);
    }

    /**
     * Retrieves ONLY active links for a specific user.
     * @param userId The ID of the user.
     * @return A list of KeywordNumberLink objects.
     */
    public List<KeywordNumberLink> getAllActiveLinksForUser(int userId) {
        List<KeywordNumberLink> links = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    DatabaseHelper.TABLE_KEYWORD_NUMBER_LINKS,
                    allColumns,
                    DatabaseHelper.COLUMN_LINK_USER_ID + " = ? AND " + DatabaseHelper.COLUMN_LINK_IS_ACTIVE + " = 1",
                    new String[]{String.valueOf(userId)},
                    null, null, null
            );
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                links.add(cursorToLink(cursor));
                cursor.moveToNext();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting all active links for user: " + userId, e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return links;
    }

    /**
     * *** جديد: Retrieves ALL links (active and inactive) for a specific user. ***
     * @param userId The ID of the user.
     * @return A list of KeywordNumberLink objects.
     */
    public List<KeywordNumberLink> getAllLinksForUser(int userId) {
        List<KeywordNumberLink> links = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    DatabaseHelper.TABLE_KEYWORD_NUMBER_LINKS,
                    allColumns,
                    DatabaseHelper.COLUMN_LINK_USER_ID + " = ?", // *** الشرط الجديد: جلب كل روابط المستخدم ***
                    new String[]{String.valueOf(userId)},
                    null, null,
                    DatabaseHelper.COLUMN_LINK_ID + " DESC" // فرز حسب الأحدث
            );
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                links.add(cursorToLink(cursor));
                cursor.moveToNext();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting all links for user: " + userId, e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return links;
    }

    public List<EmergencyNumber> getEmergencyNumbersForKeyword(int userId, String keywordText) {
        List<EmergencyNumber> numbers = new ArrayList<>();
        Cursor cursor = null;
        try {
            String query = "SELECT T2." + DatabaseHelper.COLUMN_NUMBER_ID + ", " +
                    "T2." + DatabaseHelper.COLUMN_NUMBER_PHONE + ", " +
                    "T2." + DatabaseHelper.COLUMN_NUMBER_DESC + ", " +
                    "T2." + DatabaseHelper.COLUMN_COMMON_USER_ID + ", " +
                    "T2." + DatabaseHelper.COLUMN_NUMBER_IS_DEFAULT + ", " +
                    "T2." + DatabaseHelper.COLUMN_NUMBER_ADDED_DATE +
                    " FROM " + DatabaseHelper.TABLE_KEYWORD_NUMBER_LINKS + " AS T1" +
                    " JOIN " + DatabaseHelper.TABLE_EMERGENCY_NUMBERS + " AS T2 ON T1." + DatabaseHelper.COLUMN_LINK_NUMBER_ID + " = T2." + DatabaseHelper.COLUMN_NUMBER_ID +
                    " JOIN " + DatabaseHelper.TABLE_KEYWORDS + " AS T3 ON T1." + DatabaseHelper.COLUMN_LINK_KEYWORD_ID + " = T3." + DatabaseHelper.COLUMN_KEYWORD_ID +
                    " WHERE T1." + DatabaseHelper.COLUMN_LINK_USER_ID + " = ? AND T3." + DatabaseHelper.COLUMN_KEYWORD_TEXT + " = ? AND T1." + DatabaseHelper.COLUMN_LINK_IS_ACTIVE + " = 1;";

            cursor = db.rawQuery(query, new String[]{String.valueOf(userId), keywordText});

            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                EmergencyNumber number = new EmergencyNumber();
                number.setNumberId(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_NUMBER_ID)));
                number.setPhoneNumber(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_NUMBER_PHONE)));
                number.setNumberDescription(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_NUMBER_DESC)));

                int numUserIdIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_COMMON_USER_ID);
                if (!cursor.isNull(numUserIdIndex)) {
                    number.setUserId(cursor.getInt(numUserIdIndex));
                } else {
                    number.setUserId(null);
                }

                number.setDefault(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_NUMBER_IS_DEFAULT)) == 1);
                number.setAddedDate(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_NUMBER_ADDED_DATE)));
                numbers.add(number);
                cursor.moveToNext();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting emergency numbers for keyword '" + keywordText + "' for user " + userId, e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return numbers;
    }

    public int updateLinkStatus(int linkId, boolean isActive) {
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_LINK_IS_ACTIVE, isActive ? 1 : 0);

        return db.update(
                DatabaseHelper.TABLE_KEYWORD_NUMBER_LINKS,
                values,
                DatabaseHelper.COLUMN_LINK_ID + " = ?",
                new String[]{String.valueOf(linkId)}
        );
    }

    public int deleteLink(int linkId) {
        return db.delete(
                DatabaseHelper.TABLE_KEYWORD_NUMBER_LINKS,
                DatabaseHelper.COLUMN_LINK_ID + " = ?",
                new String[]{String.valueOf(linkId)}
        );
    }

    public boolean linkExists(int userId, int keywordId, int numberId) {
        Cursor cursor = null;
        try {
            cursor = db.query(
                    DatabaseHelper.TABLE_KEYWORD_NUMBER_LINKS,
                    new String[]{DatabaseHelper.COLUMN_LINK_ID},
                    DatabaseHelper.COLUMN_LINK_USER_ID + " = ? AND " +
                            DatabaseHelper.COLUMN_LINK_KEYWORD_ID + " = ? AND " +
                            DatabaseHelper.COLUMN_LINK_NUMBER_ID + " = ?",
                    new String[]{String.valueOf(userId), String.valueOf(keywordId), String.valueOf(numberId)},
                    null, null, null
            );
            return cursor != null && cursor.getCount() > 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private KeywordNumberLink cursorToLink(Cursor cursor) {
        KeywordNumberLink link = new KeywordNumberLink();
        link.setLinkId(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LINK_ID)));
        link.setKeywordId(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LINK_KEYWORD_ID)));
        link.setNumberId(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LINK_NUMBER_ID)));
        link.setUserId(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LINK_USER_ID)));
        link.setActive(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LINK_IS_ACTIVE)) == 1);
        link.setCreatedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LINK_CREATED_AT)));
        return link;
    }
}