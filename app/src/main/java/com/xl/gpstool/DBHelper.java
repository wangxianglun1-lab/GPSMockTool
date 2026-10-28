package com.xl.gpstool;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;

public class DBHelper extends SQLiteOpenHelper {

    // 数据库名字和版本
    private static final String DATABASE_NAME = "幻迹GPS.db";
    private static final int DATABASE_VERSION = 2;

    // 表名和列名
    private static final String TABLE_FAVORITES = "favorites";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_NAME = "name";       // 地名备注
    private static final String COLUMN_LAT = "latitude";    // 纬度
    private static final String COLUMN_LON = "longitude";   // 经度

    public DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // 第一次建库时会自动运行这块代码
    @Override
    public void onCreate(android.database.sqlite.SQLiteDatabase db) {
        // 原来的收藏夹表 (把 KEY_ 全换成了你定义的 COLUMN_ )
        String CREATE_FAVORITES_TABLE = "CREATE TABLE " + TABLE_FAVORITES + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_NAME + " TEXT,"
                + COLUMN_LAT + " TEXT,"
                + COLUMN_LON + " TEXT" + ")";
        db.execSQL(CREATE_FAVORITES_TABLE);

        // 🌟 新增的：历史记录表
        String CREATE_HISTORY_TABLE = "CREATE TABLE history ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "time TEXT,"
                + "lat TEXT,"
                + "lon TEXT" + ")";
        db.execSQL(CREATE_HISTORY_TABLE);
    }
    // 🗑️ 抹除时间线 (清理历史记录)
    public void deleteHistory(String time) {
        android.database.sqlite.SQLiteDatabase db = this.getWritableDatabase();
        try {
            // 注意："history" 是你的表名，"time" 是时间列名。
            // 如果你当初建表时用的名字不一样，请自己对齐一下哦！
            db.delete("history", "time = ?", new String[]{time});
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            db.close();
        }
    }
    @Override
    public void onUpgrade(android.database.sqlite.SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FAVORITES);
        db.execSQL("DROP TABLE IF EXISTS history");
        onCreate(db);
    }
    public void addFavorite(String name, double lat, double lon) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, name);
        values.put(COLUMN_LAT, lat);
        values.put(COLUMN_LON, lon);
        db.insert(TABLE_FAVORITES, null, values);
        db.close();
    }
    public ArrayList<HashMap<String, String>> getAllFavorites() {
        ArrayList<HashMap<String, String>> favList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_FAVORITES, null);

        if (cursor.moveToFirst()) {
            do {
                HashMap<String, String> point = new HashMap<>();
                point.put("name", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)));
                point.put("lat", String.valueOf(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LAT))));
                point.put("lon", String.valueOf(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LON))));
                favList.add(point);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return favList;
    }
    // 🌟 往时光机里塞入一条新历史
    public void addHistory(String time, String lat, String lon) {
        android.database.sqlite.SQLiteDatabase db = this.getWritableDatabase();
        android.content.ContentValues values = new android.content.ContentValues();
        values.put("time", time);
        values.put("lat", lat);
        values.put("lon", lon);
        db.insert("history", null, values);
        db.close();
    }
    // 🗑️ 彻底抹除收藏点
    public void deleteFavorite(String name) {
        android.database.sqlite.SQLiteDatabase db = this.getWritableDatabase();
        try {
            // 注意："favorites" 是表名，如果你建表时是大写或者别的名字，请对齐一下
            db.delete("favorites", "name = ?", new String[]{name});
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            db.close();
        }
    }
    // 🌟 翻看时光机里的所有记录（按时间倒序，最新的在最上面）
    public java.util.ArrayList<java.util.HashMap<String, String>> getAllHistory() {
        java.util.ArrayList<java.util.HashMap<String, String>> historyList = new java.util.ArrayList<>();
        // ORDER BY id DESC 确保最新去过的地方排在最前面
        String selectQuery = "SELECT * FROM history ORDER BY id DESC LIMIT 20";

        android.database.sqlite.SQLiteDatabase db = this.getWritableDatabase();
        android.database.Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                java.util.HashMap<String, String> point = new java.util.HashMap<>();
                point.put("time", cursor.getString(cursor.getColumnIndexOrThrow("time")));
                point.put("lat", cursor.getString(cursor.getColumnIndexOrThrow("lat")));
                point.put("lon", cursor.getString(cursor.getColumnIndexOrThrow("lon")));
                historyList.add(point);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return historyList;
    }
}