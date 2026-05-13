package com.example.samsungproject.LocalDB;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

public final class UserDatabaseHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "app.db";
    public static final int DATABASE_VERSION = 2;

    public static final String TABLE_USERS = "users";
    public static final String COL_NAME = "name";
    public static final String COL_ICON = "icon";

    /**
     * Создаёт helper для работы с локальной SQLite-базой пользователей.
     */
    public UserDatabaseHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Таблица хранит одну строку профиля текущего пользователя: имя и иконка (без колонки {@code id},
     * идентификатор аккаунта хранится в {@code SharedPreferences}).
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE " + TABLE_USERS + " (" +
                        COL_NAME + " TEXT, " +
                        COL_ICON + " BLOB" +
                        ")"
        );
    }

    /**
     * Обновляет схему базы данных при увеличении версии.
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }

    /**
     * Заменяет локальный профиль: одна строка с именем и иконкой.
     * Параметр {@code serverUserId} не сохраняется в таблице (используйте prefs для id сервера).
     */
    public void upsertUser(long serverUserId, @Nullable String name, @Nullable byte[] icon) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_USERS, null, null);
            ContentValues values = new ContentValues();
            values.put(COL_NAME, name);
            values.put(COL_ICON, icon);
            db.insert(TABLE_USERS, null, values);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Возвращает локальный профиль для отображения; {@code serverUserId} должен совпадать с текущей сессией.
     */
    @Nullable
    public UserRow getUser(long serverUserId) {
        if (serverUserId < 0) {
            return null;
        }
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(
                TABLE_USERS,
                new String[]{COL_NAME, COL_ICON},
                null,
                null,
                null,
                null,
                null
        )) {
            if (!c.moveToFirst()) {
                return null;
            }
            String name = c.isNull(c.getColumnIndexOrThrow(COL_NAME)) ? null : c.getString(c.getColumnIndexOrThrow(COL_NAME));
            byte[] icon = c.isNull(c.getColumnIndexOrThrow(COL_ICON)) ? null : c.getBlob(c.getColumnIndexOrThrow(COL_ICON));
            return new UserRow(serverUserId, name, icon);
        }
    }

    /**
     * Удаляет все строки локального профиля пользователя.
     */
    @SuppressWarnings("unused")
    public int deleteUser(long id) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(TABLE_USERS, null, null);
    }

    public static final class UserRow {
        /** Идентификатор пользователя на сервере (из prefs), не из таблицы SQLite. */
        public final long id;
        @Nullable public final String name;
        @Nullable public final byte[] icon;

        /**
         * Создаёт объект-строку пользователя, полученную из базы.
         */
        public UserRow(long id, @Nullable String name, @Nullable byte[] icon) {
            this.id = id;
            this.name = name;
            this.icon = icon;
        }
    }
}
