package com.example.keywordlistenerjava.db.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.example.keywordlistenerjava.db.DatabaseHelper;
import com.example.keywordlistenerjava.db.entity.EmergencyNumber;
import com.example.keywordlistenerjava.db.entity.Keyword;
import com.example.keywordlistenerjava.db.entity.KeywordNumberLink;
import com.example.keywordlistenerjava.db.entity.User;
import com.example.keywordlistenerjava.util.PasswordHasher; // لاستخدام تجزئة كلمة المرور

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class UserDao {
    private static final String TAG = "UserDao";
    private SQLiteDatabase db;
    private DatabaseHelper dbHelper;
    private Context context; // Keep context to pass to other DAOs for default linking

    public UserDao(Context context) {
        this.context = context;
        dbHelper = new DatabaseHelper(context);
    }

    public void open() {
        db = dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
    }

    /**
     * Inserts a new user into the database and links them to default keywords and numbers.
     * @param user The user object to insert (password should be plain text here, will be hashed).
     * @return The user_id of the newly inserted row, or -1 if an error occurred.
     */
    public long registerUser(User user) {
        long userId = -1;
        db.beginTransaction(); // Start a transaction for atomicity
        try {
            // Hash the password
            String hashedPassword = PasswordHasher.hashPassword(user.getPasswordHash());

            ContentValues values = new ContentValues();
            values.put(DatabaseHelper.COLUMN_USER_FIRST_NAME, user.getFirstName());
            values.put(DatabaseHelper.COLUMN_USER_LAST_NAME, user.getLastName());
            values.put(DatabaseHelper.COLUMN_USER_PHONE, user.getPhoneNumber());
            values.put(DatabaseHelper.COLUMN_USER_RESIDENCE, user.getResidenceArea());
            values.put(DatabaseHelper.COLUMN_USER_PASSWORD_HASH, hashedPassword);
            values.put(DatabaseHelper.COLUMN_USER_REG_DATE, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
            values.put(DatabaseHelper.COLUMN_USER_IS_ACTIVE, 1); // Default to active

            userId = db.insert(DatabaseHelper.TABLE_USERS, null, values);

            if (userId != -1) {
                // Link default keywords and numbers for the new user
                linkDefaultKeywordsAndNumbersToUser((int) userId);
                db.setTransactionSuccessful(); // Mark transaction as successful
                Log.i(TAG, "User registered and default links created successfully. User ID: " + userId);
            } else {
                Log.e(TAG, "Failed to insert user into database.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error registering user: " + e.getMessage(), e);
        } finally {
            db.endTransaction(); // End transaction (commit or rollback)
        }
        return userId;
    }

    /**
     * Authenticates a user by phone number and password.
     * @param phoneNumber The user's phone number.
     * @param password The plain text password.
     * @return The User object if authenticated, null otherwise.
     */
    public User authenticateUser(String phoneNumber, String password) {
        Cursor cursor = null;
        User user = null;
        try {
            cursor = db.query(
                    DatabaseHelper.TABLE_USERS,
                    null, // all columns
                    DatabaseHelper.COLUMN_USER_PHONE + " = ?",
                    new String[]{phoneNumber},
                    null, null, null
            );

            if (cursor != null && cursor.moveToFirst()) {
                String storedHashedPassword = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_USER_PASSWORD_HASH));
                if (PasswordHasher.verifyPassword(password, storedHashedPassword)) {
                    user = cursorToUser(cursor);
                    Log.i(TAG, "User authenticated: " + phoneNumber);
                } else {
                    Log.w(TAG, "Authentication failed: Incorrect password for " + phoneNumber);
                }
            } else {
                Log.w(TAG, "Authentication failed: User not found with phone number " + phoneNumber);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error authenticating user: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return user;
    }

    /**
     * Retrieves a user by their user ID.
     * @param userId The ID of the user.
     * @return A User object if found, otherwise null.
     */
    public User getUserById(int userId) {
        Cursor cursor = null;
        User user = null;
        try {
            cursor = db.query(
                    DatabaseHelper.TABLE_USERS,
                    null, // all columns
                    DatabaseHelper.COLUMN_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)},
                    null, null, null
            );

            if (cursor != null && cursor.moveToFirst()) {
                user = cursorToUser(cursor);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting user by ID: " + userId, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return user;
    }

    // Helper method to convert Cursor to User object
    private User cursorToUser(Cursor cursor) {
        User user = new User();
        user.setUserId(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_USER_ID)));
        user.setFirstName(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_USER_FIRST_NAME)));
        user.setLastName(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_USER_LAST_NAME)));
        user.setPhoneNumber(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_USER_PHONE)));
        user.setResidenceArea(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_USER_RESIDENCE)));
        user.setPasswordHash(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_USER_PASSWORD_HASH)));
        user.setRegistrationDate(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_USER_REG_DATE)));
        user.setActive(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_USER_IS_ACTIVE)) == 1);
        return user;
    }

    // This method creates default links for a newly registered user
    private void linkDefaultKeywordsAndNumbersToUser(int userId) {
        KeywordDao keywordDao = new KeywordDao(context);
        EmergencyNumberDao numberDao = new EmergencyNumberDao(context);
        KeywordNumberLinkDao linkDao = new KeywordNumberLinkDao(context);

        try {
            keywordDao.open();
            numberDao.open();
            linkDao.open();

            // Get all default keywords and numbers
            List<Keyword> defaultKeywords = keywordDao.getAllDefaultKeywords();
            List<EmergencyNumber> defaultNumbers = numberDao.getAllDefaultNumbers();

            // Create links between default keywords and default numbers for the new user
            // This example creates a link between each default keyword and each default number.
            // You might want to customize this logic (e.g., link only specific keywords to specific numbers).
            for (Keyword keyword : defaultKeywords) {
                for (EmergencyNumber number : defaultNumbers) {
                    // Create link record
                    KeywordNumberLink link = new KeywordNumberLink();
                    link.setKeywordId(keyword.getKeywordId());
                    link.setNumberId(number.getNumberId());
                    link.setUserId(userId);
                    link.setActive(true); // Default links are active

                    long linkId = linkDao.addLink(link);
                    if (linkId != -1) {
                        Log.d(TAG, "Default link created: Keyword '" + keyword.getKeywordText() + "' to Number '" + number.getPhoneNumber() + "' for User ID " + userId);
                    } else {
                        Log.e(TAG, "Failed to create default link: Keyword '" + keyword.getKeywordText() + "' to Number '" + number.getPhoneNumber() + "' for User ID " + userId);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error linking default keywords/numbers to new user: " + e.getMessage(), e);
        } finally {
            linkDao.close();
            numberDao.close();
            keywordDao.close();
        }
    }
}