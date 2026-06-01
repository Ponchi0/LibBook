package com.example.samsungproject.LocalDB;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

public final class UserDatabaseHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "app.db";
    public static final int DATABASE_VERSION = 4;

    public static final String TABLE_USERS = "users";
    public static final String COL_NAME = "name";
    public static final String COL_ICON = "icon";

    /**
     * Создаёт помощник для работы с локальной таблицей пользователей.
     *
     * @param context контекст приложения
     */
    public UserDatabaseHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Создаёт таблицу пользователей при первом создании БД.
     *
     * @param db экземпляр SQLiteDatabase
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        ensureUsersTable(db);
    }

    /**
     * Создаёт таблицу пользователей, если она ещё не существует.
     *
     * @param db экземпляр SQLiteDatabase или {@code null}
     */
    public static void ensureUsersTable(@Nullable SQLiteDatabase db) {
        if (db == null) {
            return;
        }
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_USERS + " (" +
                        COL_NAME + " TEXT, " +
                        COL_ICON + " BLOB" +
                        ")"
        );
    }

    /**
     * Гарантирует наличие таблицы пользователей при открытии БД.
     *
     * @param db экземпляр SQLiteDatabase
     */
    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        ensureUsersTable(db);
    }

    /**
     * Обновляет схему БД при повышении версии: таблица пользователей и миграция книг.
     *
     * @param db         экземпляр SQLiteDatabase
     * @param oldVersion предыдущая версия схемы
     * @param newVersion новая версия схемы
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        ensureUsersTable(db);
        BookDatabaseHelper.migrateBooksSchema(db, oldVersion);
    }

    /**
     * Сохраняет или заменяет единственную запись текущего пользователя (имя и иконка).
     *
     * @param serverUserId идентификатор пользователя на сервере
     * @param name         отображаемое имя
     * @param icon         байты аватара
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
     * Возвращает локально сохранённые данные пользователя.
     *
     * @param serverUserId идентификатор пользователя на сервере
     * @return строка с именем и иконкой или {@code null}, если записи нет
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
     * Удаляет все записи пользователей из локальной таблицы.
     *
     * @param id идентификатор (не используется, таблица содержит одну запись)
     * @return количество удалённых строк
     */
    @SuppressWarnings("unused")
    public int deleteUser(long id) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(TABLE_USERS, null, null);
    }

    public static final class UserRow {

        public final long id;
        @Nullable public final String name;
        @Nullable public final byte[] icon;

        /**
         * Создаёт неизменяемую строку данных пользователя из локальной БД.
         *
         * @param id   идентификатор пользователя
         * @param name имя
         * @param icon байты аватара
         */
        public UserRow(long id, @Nullable String name, @Nullable byte[] icon) {
            this.id = id;
            this.name = name;
            this.icon = icon;
        }
    }
}
