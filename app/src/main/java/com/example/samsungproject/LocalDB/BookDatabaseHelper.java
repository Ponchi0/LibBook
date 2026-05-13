package com.example.samsungproject.LocalDB;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

public final class BookDatabaseHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "app.db";
    public static final int DATABASE_VERSION = 2;

    public static final String TABLE_BOOKS = "books";
    public static final String COL_ID = "id";
    public static final String COL_NAME = "name";
    public static final String COL_ICON = "icon";
    public static final String COL_TEXT = "text";

    /**
     * Создаёт helper для работы с локальной SQLite-базой книг.
     */
    public BookDatabaseHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Создаёт таблицы базы данных при первом запуске.
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE " + TABLE_BOOKS + " (" +
                        COL_ID + " INTEGER PRIMARY KEY, " +
                        COL_NAME + " TEXT, " +
                        COL_ICON + " BLOB, " +
                        COL_TEXT + " TEXT" +
                        ")"
        );
    }

    /**
     * Обновляет схему базы данных при увеличении версии.
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_BOOKS + " ADD COLUMN " + COL_TEXT + " TEXT");
        }
    }

    /**
     * Вставляет или обновляет книгу по ID (заменяет запись при конфликте).
     */
    public void upsertBook(long id, @Nullable String name, @Nullable byte[] icon) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_ID, id);
        values.put(COL_NAME, name);
        values.put(COL_ICON, icon);
        db.insertWithOnConflict(TABLE_BOOKS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Вставляет или обновляет книгу по ID (заменяет запись при конфликте).
     */
    public void upsertBook(long id, @Nullable String name, @Nullable byte[] icon, @Nullable String text) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_ID, id);
        values.put(COL_NAME, name);
        values.put(COL_ICON, icon);
        values.put(COL_TEXT, text);
        db.insertWithOnConflict(TABLE_BOOKS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Возвращает книгу по ID или null, если запись не найдена.
     */
    @Nullable
    public BookRow getBook(long id) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(
                TABLE_BOOKS,
                new String[]{COL_ID, COL_NAME, COL_ICON, COL_TEXT},
                COL_ID + "=?",
                new String[]{String.valueOf(id)},
                null,
                null,
                null
        )) {
            if (!c.moveToFirst()) return null;
            long bookId = c.getLong(c.getColumnIndexOrThrow(COL_ID));
            String name = c.isNull(c.getColumnIndexOrThrow(COL_NAME)) ? null : c.getString(c.getColumnIndexOrThrow(COL_NAME));
            byte[] icon = c.isNull(c.getColumnIndexOrThrow(COL_ICON)) ? null : c.getBlob(c.getColumnIndexOrThrow(COL_ICON));
            String text = c.isNull(c.getColumnIndexOrThrow(COL_TEXT)) ? null : c.getString(c.getColumnIndexOrThrow(COL_TEXT));
            return new BookRow(bookId, name, icon, text);
        }
    }

    /**
     * Удаляет книгу по ID и возвращает количество удалённых строк.
     */
    public int deleteBook(long id) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(TABLE_BOOKS, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public static final class BookRow {
        public final long id;
        @Nullable public final String name;
        @Nullable public final byte[] icon;
        @Nullable public final String text;

        /**
         * Создаёт объект-строку книги, полученную из базы.
         */
        public BookRow(long id, @Nullable String name, @Nullable byte[] icon, @Nullable String text) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.text = text;
        }
    }
}

