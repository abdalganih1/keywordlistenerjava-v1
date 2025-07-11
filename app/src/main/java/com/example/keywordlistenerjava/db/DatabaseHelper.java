package com.example.keywordlistenerjava.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.keywordlistenerjava.db.entity.Keyword;
import com.example.keywordlistenerjava.db.entity.EmergencyNumber;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "DatabaseHelper";

    // Database Info
    private static final String DATABASE_NAME = "SecurityApp.db";
    private static final int DATABASE_VERSION = 1;

    // Table Names
    public static final String TABLE_USERS = "Users";
    public static final String TABLE_KEYWORDS = "Keywords";
    public static final String TABLE_EMERGENCY_NUMBERS = "EmergencyNumbers";
    public static final String TABLE_KEYWORD_NUMBER_LINKS = "KeywordNumberLinks";
    public static final String TABLE_ALERT_LOG = "AlertLog";

    // Common Columns
    // هذا هو الثابت الذي يمثل اسم عمود "user_id" في الجداول التي تستخدمه كمفتاح أجنبي
    public static final String COLUMN_COMMON_USER_ID = "user_id";

    // Users Table - Columns
    public static final String COLUMN_USER_ID = "user_id"; // Primary Key
    public static final String COLUMN_USER_FIRST_NAME = "first_name";
    public static final String COLUMN_USER_LAST_NAME = "last_name";
    public static final String COLUMN_USER_PHONE = "phone_number";
    public static final String COLUMN_USER_RESIDENCE = "residence_area";
    public static final String COLUMN_USER_PASSWORD_HASH = "password_hash";
    public static final String COLUMN_USER_REG_DATE = "registration_date"; // TEXT (ISO 8601)
    public static final String COLUMN_USER_IS_ACTIVE = "is_active"; // INTEGER (0=false, 1=true)

    // Keywords Table - Columns
    public static final String COLUMN_KEYWORD_ID = "keyword_id"; // Primary Key
    public static final String COLUMN_KEYWORD_TEXT = "keyword_text";
    // عمود user_id في جدول Keywords هو نفسه COLUMN_COMMON_USER_ID
    public static final String COLUMN_KEYWORD_PPN_FILE = "ppn_file_name";
    public static final String COLUMN_KEYWORD_IS_DEFAULT = "is_default"; // INTEGER
    public static final String COLUMN_KEYWORD_ADDED_DATE = "added_date"; // TEXT

    // EmergencyNumbers Table - Columns
    public static final String COLUMN_NUMBER_ID = "number_id"; // Primary Key
    public static final String COLUMN_NUMBER_PHONE = "phone_number";
    public static final String COLUMN_NUMBER_DESC = "number_description";
    // عمود user_id في جدول EmergencyNumbers هو نفسه COLUMN_COMMON_USER_ID
    public static final String COLUMN_NUMBER_IS_DEFAULT = "is_default"; // INTEGER
    public static final String COLUMN_NUMBER_ADDED_DATE = "added_date"; // TEXT

    // KeywordNumberLinks Table - Columns
    public static final String COLUMN_LINK_ID = "link_id"; // Primary Key
    public static final String COLUMN_LINK_KEYWORD_ID = "keyword_id"; // FK to Keywords
    public static final String COLUMN_LINK_NUMBER_ID = "number_id"; // FK to EmergencyNumbers
    public static final String COLUMN_LINK_USER_ID = "user_id"; // FK to Users (this is also "user_id")
    public static final String COLUMN_LINK_IS_ACTIVE = "is_active"; // INTEGER
    public static final String COLUMN_LINK_CREATED_AT = "created_at"; // TEXT

    // AlertLog Table - Columns
    public static final String COLUMN_LOG_ID = "log_id"; // Primary Key
    public static final String COLUMN_LOG_USER_ID = "user_id"; // FK to Users
    public static final String COLUMN_LOG_KEYWORD_USED = "keyword_used";
    public static final String COLUMN_LOG_DATE = "alert_date"; // TEXT (YYYY-MM-DD)
    public static final String COLUMN_LOG_TIME = "alert_time"; // TEXT (HH:MM:SS)
    public static final String COLUMN_LOG_LATITUDE = "latitude"; // REAL
    public static final String COLUMN_LOG_LONGITUDE = "longitude"; // REAL
    public static final String COLUMN_LOG_MAP_LINK = "map_link"; // TEXT
    public static final String COLUMN_LOG_IS_FALSE_ALARM = "is_false_alarm"; // INTEGER (NULL, 0, 1)


    // SQL CREATE Statements
    private static final String CREATE_TABLE_USERS = "CREATE TABLE " + TABLE_USERS + " (" +
            COLUMN_USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_USER_FIRST_NAME + " TEXT NOT NULL, " +
            COLUMN_USER_LAST_NAME + " TEXT NOT NULL, " +
            COLUMN_USER_PHONE + " TEXT UNIQUE NOT NULL, " +
            COLUMN_USER_RESIDENCE + " TEXT, " +
            COLUMN_USER_PASSWORD_HASH + " TEXT NOT NULL, " +
            COLUMN_USER_REG_DATE + " TEXT DEFAULT (strftime('%Y-%m-%d %H:%M:%S','now','localtime')), " +
            COLUMN_USER_IS_ACTIVE + " INTEGER DEFAULT 1 NOT NULL" +
            ");";

    private static final String CREATE_TABLE_KEYWORDS = "CREATE TABLE " + TABLE_KEYWORDS + " (" +
            COLUMN_KEYWORD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_KEYWORD_TEXT + " TEXT NOT NULL UNIQUE, " + // UNIQUE to prevent duplicate keywords system-wide
            COLUMN_COMMON_USER_ID + " INTEGER, " + // هذا هو عمود user_id في جدول Keywords
            COLUMN_KEYWORD_PPN_FILE + " TEXT, " +
            COLUMN_KEYWORD_IS_DEFAULT + " INTEGER DEFAULT 0 NOT NULL, " +
            COLUMN_KEYWORD_ADDED_DATE + " TEXT DEFAULT (strftime('%Y-%m-%d %H:%M:%S','now','localtime')), " +
            "FOREIGN KEY(" + COLUMN_COMMON_USER_ID + ") REFERENCES " + TABLE_USERS + "(" + COLUMN_USER_ID + ") ON DELETE CASCADE" +
            ");";

    private static final String CREATE_TABLE_EMERGENCY_NUMBERS = "CREATE TABLE " + TABLE_EMERGENCY_NUMBERS + " (" +
            COLUMN_NUMBER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_NUMBER_PHONE + " TEXT NOT NULL UNIQUE, " + // UNIQUE to prevent duplicate numbers system-wide
            COLUMN_NUMBER_DESC + " TEXT NOT NULL, " +
            COLUMN_COMMON_USER_ID + " INTEGER, " + // هذا هو عمود user_id في جدول EmergencyNumbers
            COLUMN_NUMBER_IS_DEFAULT + " INTEGER DEFAULT 0 NOT NULL, " +
            COLUMN_NUMBER_ADDED_DATE + " TEXT DEFAULT (strftime('%Y-%m-%d %H:%M:%S','now','localtime')), " +
            "FOREIGN KEY(" + COLUMN_COMMON_USER_ID + ") REFERENCES " + TABLE_USERS + "(" + COLUMN_USER_ID + ") ON DELETE CASCADE" +
            ");";

    private static final String CREATE_TABLE_KEYWORD_NUMBER_LINKS = "CREATE TABLE " + TABLE_KEYWORD_NUMBER_LINKS + " (" +
            COLUMN_LINK_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_LINK_KEYWORD_ID + " INTEGER NOT NULL, " +
            COLUMN_LINK_NUMBER_ID + " INTEGER NOT NULL, " +
            COLUMN_LINK_USER_ID + " INTEGER NOT NULL, " + // هذا هو عمود user_id في جدول KeywordNumberLinks
            COLUMN_LINK_IS_ACTIVE + " INTEGER DEFAULT 1 NOT NULL, " +
            COLUMN_LINK_CREATED_AT + " TEXT DEFAULT (strftime('%Y-%m-%d %H:%M:%S','now','localtime')), " +
            "FOREIGN KEY(" + COLUMN_LINK_KEYWORD_ID + ") REFERENCES " + TABLE_KEYWORDS + "(" + COLUMN_KEYWORD_ID + ") ON DELETE CASCADE, " +
            "FOREIGN KEY(" + COLUMN_LINK_NUMBER_ID + ") REFERENCES " + TABLE_EMERGENCY_NUMBERS + "(" + COLUMN_NUMBER_ID + ") ON DELETE CASCADE, " +
            "FOREIGN KEY(" + COLUMN_LINK_USER_ID + ") REFERENCES " + TABLE_USERS + "(" + COLUMN_USER_ID + ") ON DELETE CASCADE, " +
            "UNIQUE(" + COLUMN_LINK_KEYWORD_ID + ", " + COLUMN_LINK_NUMBER_ID + ", " + COLUMN_LINK_USER_ID + ")" + // Prevent duplicate links for same user
            ");";

    private static final String CREATE_TABLE_ALERT_LOG = "CREATE TABLE " + TABLE_ALERT_LOG + " (" +
            COLUMN_LOG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_LOG_USER_ID + " INTEGER NOT NULL, " + // هذا هو عمود user_id في جدول AlertLog
            COLUMN_LOG_KEYWORD_USED + " TEXT NOT NULL, " +
            COLUMN_LOG_DATE + " TEXT NOT NULL, " + // Stored as YYYY-MM-DD
            COLUMN_LOG_TIME + " TEXT NOT NULL, " + // Stored as HH:MM:SS
            COLUMN_LOG_LATITUDE + " REAL NOT NULL, " +
            COLUMN_LOG_LONGITUDE + " REAL NOT NULL, " +
            COLUMN_LOG_MAP_LINK + " TEXT, " +
            COLUMN_LOG_IS_FALSE_ALARM + " INTEGER, " + // NULL, 0=false, 1=true
            "FOREIGN KEY(" + COLUMN_LOG_USER_ID + ") REFERENCES " + TABLE_USERS + "(" + COLUMN_USER_ID + ") ON DELETE CASCADE" +
            ");";


    public DatabaseHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i(TAG, "onCreate: Creating database tables...");
        // Enable foreign key constraints
        db.execSQL("PRAGMA foreign_keys = ON;");

        db.execSQL(CREATE_TABLE_USERS);
        db.execSQL(CREATE_TABLE_KEYWORDS);
        db.execSQL(CREATE_TABLE_EMERGENCY_NUMBERS);
        db.execSQL(CREATE_TABLE_KEYWORD_NUMBER_LINKS);
        db.execSQL(CREATE_TABLE_ALERT_LOG);
        Log.i(TAG, "onCreate: Database tables created.");

        // Add default system-wide keywords and numbers
        addSystemDefaultData(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "onUpgrade: Upgrading database from version " + oldVersion + " to " + newVersion);
        // !!! IMPORTANT: This will delete all existing data. For a real app, implement data migration.
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ALERT_LOG);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_KEYWORD_NUMBER_LINKS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_EMERGENCY_NUMBERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_KEYWORDS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db); // Recreate tables
    }

    // This method adds default keywords and numbers that are part of the system, not specific to any user.
    private void addSystemDefaultData(SQLiteDatabase db) {
        Log.i(TAG, "addSystemDefaultData: Adding default system-wide keywords and numbers...");

        // Default Keywords (10 examples)
        // Ensure you have actual .ppn files for these in your assets folder!
        addKeyword(db, "طوارئ", null, "default_emergency.ppn", true);
        addKeyword(db, "اسعاف", null, "default_ambulance.ppn", true);
        addKeyword(db, "شرطة", null, "default_police.ppn", true);
        addKeyword(db, "عنف", null, "default_violence.ppn", true);
        addKeyword(db, "خطر", null, "default_danger.ppn", true);
        addKeyword(db, "نجده", null, "default_help.ppn", true);
        addKeyword(db, "اعتداء", null, "default_assault.ppn", true);
        addKeyword(db, "سرقه", null, "default_theft.ppn", true);
        addKeyword(db, "اختطاف", null, "default_kidnap.ppn", true);
        addKeyword(db, "حريق", null, "default_fire.ppn", true);


        // Default Emergency Numbers (10 examples)
        addEmergencyNumber(db, "112", "الشرطة/الطوارئ العامة", null, true);
        addEmergencyNumber(db, "110", "الإسعاف", null, true);
        addEmergencyNumber(db, "108", "الشرطة", null, true);
        addEmergencyNumber(db, "113", "الدفاع المدني", null, true);
        addEmergencyNumber(db, "0912345678", "مشفى دمشق", null, true); // Example
        addEmergencyNumber(db, "0923456789", "المحافظة", null, true); // Example
        addEmergencyNumber(db, "0934567890", "الهلال الأحمر", null, true); // Example
        addEmergencyNumber(db, "0945678901", "الدائرة الأمنية 1", null, true); // Example
        addEmergencyNumber(db, "0956789012", "الدائرة الأمنية 2", null, true); // Example
        addEmergencyNumber(db, "0967890123", "الدائرة الأمنية 3", null, true); // Example


        // --- Example of linking default keywords to default numbers for the system ---
        // This is if you want pre-defined system-wide links that apply to all users
        // Otherwise, each user will get their own default links upon registration.
        // For simplicity, we'll let the user registration handle default linking.
        Log.i(TAG, "addSystemDefaultData: Default data added.");
    }

    // Helper method to add a keyword
    // Returns the row ID of the inserted keyword
    public long addKeyword(SQLiteDatabase db, String text, Integer userId, String ppnFile, boolean isDefault) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_KEYWORD_TEXT, text);
        if (userId != null) { // userId can be null for default keywords, or a valid user ID
            values.put(COLUMN_COMMON_USER_ID, userId);
        } else {
            values.putNull(COLUMN_COMMON_USER_ID); // Set to NULL for system-wide default keywords
        }
        values.put(COLUMN_KEYWORD_PPN_FILE, ppnFile);
        values.put(COLUMN_KEYWORD_IS_DEFAULT, isDefault ? 1 : 0);
        values.put(COLUMN_KEYWORD_ADDED_DATE, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        return db.insert(TABLE_KEYWORDS, null, values);
    }

    // Helper method to add an emergency number
    // Returns the row ID of the inserted number
    public long addEmergencyNumber(SQLiteDatabase db, String phone, String description, Integer userId, boolean isDefault) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_NUMBER_PHONE, phone);
        values.put(COLUMN_NUMBER_DESC, description);
        if (userId != null) { // userId can be null for default numbers, or a valid user ID
            values.put(COLUMN_COMMON_USER_ID, userId);
        } else {
            values.putNull(COLUMN_COMMON_USER_ID); // Set to NULL for system-wide default numbers
        }
        values.put(COLUMN_NUMBER_IS_DEFAULT, isDefault ? 1 : 0);
        values.put(COLUMN_NUMBER_ADDED_DATE, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        return db.insert(TABLE_EMERGENCY_NUMBERS, null, values);
    }

    // Helper methods to get IDs of default keywords/numbers (useful for linking)
    public long getDefaultKeywordId(SQLiteDatabase db, String keywordText) {
        Cursor cursor = null;
        long id = -1;
        try {
            cursor = db.query(
                    TABLE_KEYWORDS,
                    new String[]{COLUMN_KEYWORD_ID},
                    COLUMN_KEYWORD_TEXT + " = ? AND " + COLUMN_KEYWORD_IS_DEFAULT + " = 1",
                    new String[]{keywordText},
                    null, null, null
            );
            if (cursor != null && cursor.moveToFirst()) {
                id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_KEYWORD_ID));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting default keyword ID for " + keywordText, e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return id;
    }

    public long getDefaultNumberId(SQLiteDatabase db, String phoneNumber) {
        Cursor cursor = null;
        long id = -1;
        try {
            cursor = db.query(
                    TABLE_EMERGENCY_NUMBERS,
                    new String[]{COLUMN_NUMBER_ID},
                    COLUMN_NUMBER_PHONE + " = ? AND " + COLUMN_NUMBER_IS_DEFAULT + " = 1",
                    new String[]{phoneNumber},
                    null, null, null
            );
            if (cursor != null && cursor.moveToFirst()) {
                id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_NUMBER_ID));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting default number ID for " + phoneNumber, e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return id;
    }
}