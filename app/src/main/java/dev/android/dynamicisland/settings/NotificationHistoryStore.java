package dev.android.dynamicisland.settings;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;

import org.json.JSONObject;

import java.util.ArrayList;

/** Bounded local store for ordinary notification cards actually displayed by coobvo. */
final class NotificationHistoryStore extends SQLiteOpenHelper {
    private static final String DATABASE = "coobvo_notification_history.db";
    private static final int VERSION = 1;
    private static final String TABLE = "notification_history";
    private static final String ID = "_id";
    private static final String NOTIFICATION_KEY = "notification_key";
    private static final String PACKAGE_NAME = "package_name";
    private static final String APP_NAME = "app_name";
    private static final String TITLE = "title";
    private static final String TEXT = "text";
    private static final String SUB_TEXT = "sub_text";
    private static final String POST_TIME = "post_time";
    private static final String CATEGORY = "category";
    private static final String CHANNEL_ID = "channel_id";
    private static final int MAX_ENTRIES = 500;
    private static final long RETENTION_MS = 7L * 24L * 60L * 60L * 1000L;

    NotificationHistoryStore(Context context) {
        super(context.getApplicationContext(), DATABASE, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + NOTIFICATION_KEY + " TEXT NOT NULL,"
                + PACKAGE_NAME + " TEXT NOT NULL,"
                + APP_NAME + " TEXT NOT NULL,"
                + TITLE + " TEXT NOT NULL,"
                + TEXT + " TEXT NOT NULL,"
                + SUB_TEXT + " TEXT,"
                + POST_TIME + " INTEGER NOT NULL,"
                + CATEGORY + " TEXT,"
                + CHANNEL_ID + " TEXT,"
                + "UNIQUE(" + PACKAGE_NAME + "," + NOTIFICATION_KEY + "," + POST_TIME + "))");
        db.execSQL("CREATE INDEX idx_coobvo_history_time ON " + TABLE + "(" + POST_TIME + " DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Future schema upgrades must preserve user history.
    }

    static boolean record(Context context, Bundle extras) {
        if (context == null || extras == null) return false;
        NotificationHistoryStore helper = new NotificationHistoryStore(context);
        long id = -1L;
        try {
            String key = safe(extras.getString("notificationKey"), 512);
            String packageName = safe(extras.getString("packageName"), 200);
            String appName = safe(extras.getString("appName"), 200);
            String title = safe(extras.getString("title"), 1000);
            String text = safe(extras.getString("text"), 2000);
            String subText = safe(extras.getString("subText"), 500);
            String category = safe(extras.getString("category"), 80);
            String channelId = safe(extras.getString("channelId"), 200);
            long postTime = extras.getLong("postTime", 0L);
            if (key.isEmpty() || packageName.isEmpty() || appName.isEmpty()
                    || (title.isEmpty() && text.isEmpty()) || postTime <= 0L) return false;

            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                db.delete(TABLE, POST_TIME + " < ?",
                        new String[]{Long.toString(System.currentTimeMillis() - RETENTION_MS)});
                ContentValues values = new ContentValues();
                values.put(NOTIFICATION_KEY, key);
                values.put(PACKAGE_NAME, packageName);
                values.put(APP_NAME, appName);
                values.put(TITLE, title);
                values.put(TEXT, text);
                values.put(SUB_TEXT, subText.isEmpty() ? null : subText);
                values.put(POST_TIME, postTime);
                values.put(CATEGORY, category.isEmpty() ? null : category);
                values.put(CHANNEL_ID, channelId.isEmpty() ? null : channelId);
                id = db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE);
                db.execSQL("DELETE FROM " + TABLE + " WHERE " + ID + " NOT IN (SELECT " + ID
                        + " FROM " + TABLE + " ORDER BY " + POST_TIME + " DESC, " + ID + " DESC LIMIT " + MAX_ENTRIES + ")");
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Throwable ignored) {
            // History is optional; storage failures must not affect System UI.
        } finally {
            helper.close();
        }
        return id >= 0L;
    }

    static ArrayList<String> read(Context context, int requestedLimit) {
        int limit = Math.max(1, Math.min(100, requestedLimit));
        ArrayList<String> result = new ArrayList<>();
        NotificationHistoryStore helper = new NotificationHistoryStore(context);
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.delete(TABLE, POST_TIME + " < ?",
                    new String[]{Long.toString(System.currentTimeMillis() - RETENTION_MS)});
            try (Cursor cursor = db.query(TABLE,
                    new String[]{NOTIFICATION_KEY, PACKAGE_NAME, APP_NAME, TITLE, TEXT, SUB_TEXT, POST_TIME, CATEGORY, CHANNEL_ID},
                    null, null, null, null, POST_TIME + " DESC, " + ID + " DESC", Integer.toString(limit))) {
                while (cursor.moveToNext()) {
                    JSONObject item = new JSONObject();
                    item.put("notificationKey", cursor.getString(0));
                    item.put("packageName", cursor.getString(1));
                    item.put("appName", cursor.getString(2));
                    item.put("title", cursor.getString(3));
                    item.put("text", cursor.getString(4));
                    item.put("subText", cursor.isNull(5) ? "" : cursor.getString(5));
                    item.put("postTime", cursor.getLong(6));
                    item.put("category", cursor.isNull(7) ? "" : cursor.getString(7));
                    item.put("channelId", cursor.isNull(8) ? "" : cursor.getString(8));
                    result.add(item.toString());
                }
            }
        } catch (Throwable ignored) {
            result.clear();
        } finally {
            helper.close();
        }
        return result;
    }

    static void clear(Context context) {
        NotificationHistoryStore helper = new NotificationHistoryStore(context);
        try {
            helper.getWritableDatabase().delete(TABLE, null, null);
        } finally {
            helper.close();
        }
    }

    private static String safe(String value, int maxLength) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder out = new StringBuilder(Math.min(value.length(), maxLength));
        for (int i = 0; i < value.length() && out.length() < maxLength; i++) {
            char ch = value.charAt(i);
            if (ch == '\n' || ch == '\t' || !Character.isISOControl(ch)) out.append(ch);
        }
        return out.toString().trim();
    }
}
