package com.example.samsungproject.LocalDB;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class BookDatabaseHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "app.db";
    public static final int DATABASE_VERSION = 4;

    public static final String TABLE_BOOKS = "books";
    public static final String COL_ID = "id";
    public static final String COL_NAME = "name";
    public static final String COL_ICON = "icon";
    public static final String COL_TEXT = "text";
    public static final String COL_LAST_OPENED_AT = "last_opened_at";
    public static final String COL_RATING = "rating";
    public static final String COL_RATED_AT = "rated_at";

    /**
     * Создаёт помощник для работы с локальной таблицей книг и закладок.
     *
     * @param context контекст приложения
     */
    public BookDatabaseHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Создаёт таблицы пользователей и книг при первом создании БД.
     *
     * @param db экземпляр SQLiteDatabase
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        UserDatabaseHelper.ensureUsersTable(db);
        createBooksTable(db);
    }

    /**
     * Гарантирует наличие таблиц и актуальную схему книг при открытии БД.
     *
     * @param db экземпляр SQLiteDatabase
     */
    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        UserDatabaseHelper.ensureUsersTable(db);
        createBooksTable(db);
        migrateBooksSchema(db, 0);
    }

    /**
     * Обновляет схему БД при повышении версии.
     *
     * @param db         экземпляр SQLiteDatabase
     * @param oldVersion предыдущая версия схемы
     * @param newVersion новая версия схемы
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        UserDatabaseHelper.ensureUsersTable(db);
        createBooksTable(db);
        migrateBooksSchema(db, oldVersion);
    }

    /**
     * Добавляет недостающие столбцы таблицы книг при миграции с более старых версий.
     *
     * @param db         экземпляр SQLiteDatabase
     * @param oldVersion версия схемы до обновления
     */
    public static void migrateBooksSchema(SQLiteDatabase db, int oldVersion) {
        if (oldVersion < 2) {
            if (!columnExists(db, COL_TEXT)) {
                db.execSQL("ALTER TABLE " + TABLE_BOOKS + " ADD COLUMN " + COL_TEXT + " TEXT");
            }
        }
        if (oldVersion < 3) {
            if (!columnExists(db, COL_LAST_OPENED_AT)) {
                db.execSQL("ALTER TABLE " + TABLE_BOOKS + " ADD COLUMN " + COL_LAST_OPENED_AT + " INTEGER");
            }
        }
        if (oldVersion < 4) {
            if (!columnExists(db, COL_RATING)) {
                db.execSQL("ALTER TABLE " + TABLE_BOOKS + " ADD COLUMN " + COL_RATING + " INTEGER");
            }
            if (!columnExists(db, COL_RATED_AT)) {
                db.execSQL("ALTER TABLE " + TABLE_BOOKS + " ADD COLUMN " + COL_RATED_AT + " INTEGER");
            }
        }
    }

    /**
     * Проверяет наличие столбца в таблице книг через PRAGMA table_info.
     *
     * @param db     экземпляр SQLiteDatabase
     * @param column имя столбца
     * @return {@code true}, если столбец существует
     */
    private static boolean columnExists(SQLiteDatabase db, String column) {
        try (Cursor c = db.rawQuery("PRAGMA table_info(" + TABLE_BOOKS + ")", null)) {
            while (c.moveToNext()) {
                if (column.equalsIgnoreCase(c.getString(1))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Создаёт таблицу книг, если она ещё не существует.
     *
     * @param db экземпляр SQLiteDatabase
     */
    private static void createBooksTable(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_BOOKS + " (" +
                        COL_ID + " INTEGER PRIMARY KEY, " +
                        COL_NAME + " TEXT, " +
                        COL_ICON + " BLOB, " +
                        COL_TEXT + " TEXT, " +
                        COL_LAST_OPENED_AT + " INTEGER, " +
                        COL_RATING + " INTEGER, " +
                        COL_RATED_AT + " INTEGER" +
                        ")"
        );
    }

    /**
     * Добавляет или обновляет закладку с полными метаданными (без времени открытия).
     *
     * @param id   идентификатор книги
     * @param name название
     * @param icon байты обложки
     * @param text текст книги
     */
    public void upsertBookmark(long id, @Nullable String name, @Nullable byte[] icon, @Nullable String text) {
        SQLiteDatabase db = getWritableDatabase();
        BookRow existing = getBook(id);
        if (existing != null) {
            ContentValues values = new ContentValues();
            values.put(COL_NAME, name);
            values.put(COL_ICON, icon);
            values.put(COL_TEXT, text);
            db.update(TABLE_BOOKS, values, COL_ID + "=?", new String[]{String.valueOf(id)});
            return;
        }
        ContentValues values = new ContentValues();
        values.put(COL_ID, id);
        values.put(COL_NAME, name);
        values.put(COL_ICON, icon);
        values.put(COL_TEXT, text);
        db.insert(TABLE_BOOKS, null, values);
    }

    /**
     * Обновляет или вставляет закладку при синхронизации с сервером, сохраняя более позднее время открытия.
     *
     * @param id           идентификатор книги
     * @param name         название
     * @param icon         байты обложки
     * @param text         текст
     * @param lastOpenedAt время последнего открытия
     */
    public void upsertBookmarkFromSync(long id, @Nullable String name, @Nullable byte[] icon,
                                       @Nullable String text, @Nullable Long lastOpenedAt) {
        SQLiteDatabase db = getWritableDatabase();
        BookRow existing = getBook(id);
        Long mergedLastOpened = lastOpenedAt;
        if (existing != null && existing.lastOpenedAt != null) {
            if (mergedLastOpened == null || existing.lastOpenedAt > mergedLastOpened) {
                mergedLastOpened = existing.lastOpenedAt;
            }
        }
        if (existing != null) {
            ContentValues values = new ContentValues();
            if (name != null) {
                values.put(COL_NAME, name);
            }
            if (icon != null) {
                values.put(COL_ICON, icon);
            }
            if (text != null) {
                values.put(COL_TEXT, text);
            }
            if (mergedLastOpened != null) {
                values.put(COL_LAST_OPENED_AT, mergedLastOpened);
            }
            if (values.size() > 0) {
                db.update(TABLE_BOOKS, values, COL_ID + "=?", new String[]{String.valueOf(id)});
            }
            return;
        }
        ContentValues values = new ContentValues();
        values.put(COL_ID, id);
        values.put(COL_NAME, name);
        values.put(COL_ICON, icon);
        values.put(COL_TEXT, text);
        if (mergedLastOpened != null) {
            values.put(COL_LAST_OPENED_AT, mergedLastOpened);
        }
        db.insert(TABLE_BOOKS, null, values);
    }

    /**
     * Удаляет все закладки, кроме указанных идентификаторов.
     *
     * @param keepIds множество id книг, которые нужно сохранить
     */
    public void removeBookmarksExcept(@NonNull java.util.Set<Long> keepIds) {
        SQLiteDatabase db = getWritableDatabase();
        if (keepIds.isEmpty()) {
            db.delete(TABLE_BOOKS, null, null);
            return;
        }
        StringBuilder sb = new StringBuilder(COL_ID + " NOT IN (");
        String[] args = new String[keepIds.size()];
        int i = 0;
        for (Long id : keepIds) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
            args[i++] = String.valueOf(id);
        }
        sb.append(')');
        db.delete(TABLE_BOOKS, sb.toString(), args);
    }

    /**
     * Сохраняет или обновляет только название и обложку книги.
     *
     * @param id   идентификатор книги
     * @param name название
     * @param icon байты обложки
     */
    public void upsertBookMeta(long id, @Nullable String name, @Nullable byte[] icon) {
        SQLiteDatabase db = getWritableDatabase();
        BookRow existing = getBook(id);
        if (existing != null) {
            ContentValues values = new ContentValues();
            values.put(COL_NAME, name);
            values.put(COL_ICON, icon);
            db.update(TABLE_BOOKS, values, COL_ID + "=?", new String[]{String.valueOf(id)});
            return;
        }
        ContentValues values = new ContentValues();
        values.put(COL_ID, id);
        values.put(COL_NAME, name);
        values.put(COL_ICON, icon);
        db.insert(TABLE_BOOKS, null, values);
    }

    /**
     * Сохраняет оценку книги (1–10) с метаданными и отметкой времени.
     *
     * @param id     идентификатор книги
     * @param name   название
     * @param icon   обложка
     * @param rating оценка пользователя
     */
    public void rateBook(long id, @Nullable String name, @Nullable byte[] icon, int rating) {
        if (rating < 1) rating = 1;
        if (rating > 10) rating = 10;
        upsertBookMeta(id, name, icon);
        ContentValues values = new ContentValues();
        values.put(COL_RATING, rating);
        values.put(COL_RATED_AT, System.currentTimeMillis());
        SQLiteDatabase db = getWritableDatabase();
        db.update(TABLE_BOOKS, values, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    /**
     * Возвращает книги с оценками за последние 24 часа, отсортированные по рейтингу.
     *
     * @param limit максимальное количество записей (0 — без ограничения)
     * @return список строк книг
     */
    public List<BookRow> listPopularBooksLast24h(int limit) {
        long since = System.currentTimeMillis() - 24L * 60L * 60L * 1000L;
        SQLiteDatabase db = getReadableDatabase();
        List<BookRow> out = new ArrayList<>();
        String order = COL_RATING + " DESC, " + COL_RATED_AT + " DESC";
        String lim = limit > 0 ? String.valueOf(limit) : null;
        try (Cursor c = db.query(
                TABLE_BOOKS,
                null,
                COL_RATED_AT + ">=? AND " + COL_RATING + " IS NOT NULL",
                new String[]{String.valueOf(since)},
                null,
                null,
                order,
                lim
        )) {
            while (c.moveToNext()) {
                out.add(rowFromCursor(c));
            }
        }
        return out;
    }

    /**
     * Обновляет время последнего открытия книги текущим моментом.
     *
     * @param id идентификатор книги
     * @return {@code true}, если запись обновлена
     */
    public boolean touchLastOpened(long id) {
        return touchLastOpenedAt(id, System.currentTimeMillis());
    }

    /**
     * Устанавливает указанное время последнего открытия книги.
     *
     * @param id       идентификатор книги
     * @param openedAt метка времени в миллисекундах
     * @return {@code true}, если запись существовала и была обновлена
     */
    public boolean touchLastOpenedAt(long id, long openedAt) {
        if (getBook(id) == null) {
            return false;
        }
        ContentValues values = new ContentValues();
        values.put(COL_LAST_OPENED_AT, openedAt);
        SQLiteDatabase db = getWritableDatabase();
        return db.update(TABLE_BOOKS, values, COL_ID + "=?", new String[]{String.valueOf(id)}) > 0;
    }

    /**
     * Проверяет, есть ли книга в локальных закладках.
     *
     * @param id идентификатор книги
     * @return {@code true}, если книга сохранена локально
     */
    public boolean isBookmarked(long id) {
        return getBook(id) != null;
    }

    /**
     * Возвращает все закладки, отсортированные по времени последнего открытия (сначала давно не открытые).
     *
     * @return список строк книг
     */
    public List<BookRow> listBookmarksByLastOpenedAsc() {
        SQLiteDatabase db = getReadableDatabase();
        List<BookRow> out = new ArrayList<>();
        String order = "CASE WHEN " + COL_LAST_OPENED_AT + " IS NULL THEN 1 ELSE 0 END, "
                + COL_LAST_OPENED_AT + " ASC";
        try (Cursor c = db.query(TABLE_BOOKS, null, null, null, null, null, order)) {
            while (c.moveToNext()) {
                out.add(rowFromCursor(c));
            }
        }
        return out;
    }

    /**
     * Возвращает книги с известным временем открытия, от новых к старым.
     *
     * @return список строк книг
     */
    public List<BookRow> listOpenedBooksByLastOpenedDesc() {
        SQLiteDatabase db = getReadableDatabase();
        List<BookRow> out = new ArrayList<>();
        try (Cursor c = db.query(
                TABLE_BOOKS,
                null,
                COL_LAST_OPENED_AT + " IS NOT NULL",
                null,
                null,
                null,
                COL_LAST_OPENED_AT + " DESC"
        )) {
            while (c.moveToNext()) {
                out.add(rowFromCursor(c));
            }
        }
        return out;
    }

    /**
     * Возвращает локальную запись книги по идентификатору.
     *
     * @param id идентификатор книги
     * @return строка книги или {@code null}
     */
    @Nullable
    public BookRow getBook(long id) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(
                TABLE_BOOKS,
                null,
                COL_ID + "=?",
                new String[]{String.valueOf(id)},
                null,
                null,
                null
        )) {
            if (!c.moveToFirst()) {
                return null;
            }
            return rowFromCursor(c);
        }
    }

    /**
     * Удаляет книгу из локальной таблицы по идентификатору.
     *
     * @param id идентификатор книги
     * @return количество удалённых строк
     */
    public int deleteBook(long id) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(TABLE_BOOKS, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    /**
     * Удаляет все локальные закладки.
     */
    public void clearAllBookmarks() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_BOOKS, null, null);
    }

    /**
     * Устаревший метод: делегирует {@link #upsertBookmark} без текста.
     *
     * @param id   идентификатор книги
     * @param name название
     * @param icon обложка
     */
    @Deprecated
    public void upsertBook(long id, @Nullable String name, @Nullable byte[] icon) {
        upsertBookmark(id, name, icon, null);
    }

    /**
     * Устаревший метод: делегирует {@link #upsertBookmark}.
     *
     * @param id   идентификатор книги
     * @param name название
     * @param icon обложка
     * @param text текст
     */
    @Deprecated
    public void upsertBook(long id, @Nullable String name, @Nullable byte[] icon, @Nullable String text) {
        upsertBookmark(id, name, icon, text);
    }

    /**
     * Собирает объект {@link BookRow} из текущей строки курсора.
     *
     * @param c курсор запроса к таблице books
     * @return заполненная строка книги
     */
    private static BookRow rowFromCursor(Cursor c) {
        long bookId = c.getLong(c.getColumnIndexOrThrow(COL_ID));
        String name = c.isNull(c.getColumnIndexOrThrow(COL_NAME))
                ? null : c.getString(c.getColumnIndexOrThrow(COL_NAME));
        byte[] icon = c.isNull(c.getColumnIndexOrThrow(COL_ICON))
                ? null : c.getBlob(c.getColumnIndexOrThrow(COL_ICON));
        String text = c.isNull(c.getColumnIndexOrThrow(COL_TEXT))
                ? null : c.getString(c.getColumnIndexOrThrow(COL_TEXT));
        Long lastOpened = c.isNull(c.getColumnIndexOrThrow(COL_LAST_OPENED_AT))
                ? null : c.getLong(c.getColumnIndexOrThrow(COL_LAST_OPENED_AT));
        Integer rating = c.getColumnIndex(COL_RATING) >= 0 && !c.isNull(c.getColumnIndexOrThrow(COL_RATING))
                ? c.getInt(c.getColumnIndexOrThrow(COL_RATING)) : null;
        Long ratedAt = c.getColumnIndex(COL_RATED_AT) >= 0 && !c.isNull(c.getColumnIndexOrThrow(COL_RATED_AT))
                ? c.getLong(c.getColumnIndexOrThrow(COL_RATED_AT)) : null;
        return new BookRow(bookId, name, icon, text, lastOpened, rating, ratedAt);
    }

    public static final class BookRow {
        public final long id;
        @Nullable public final String name;
        @Nullable public final byte[] icon;
        @Nullable public final String text;
        @Nullable public final Long lastOpenedAt;
        @Nullable public final Integer rating;
        @Nullable public final Long ratedAt;

        /**
         * Создаёт строку книги с базовыми полями (без времени открытия и оценки).
         *
         * @param id   идентификатор
         * @param name название
         * @param icon обложка
         * @param text текст
         */
        public BookRow(long id, @Nullable String name, @Nullable byte[] icon, @Nullable String text) {
            this(id, name, icon, text, null, null, null);
        }

        /**
         * Создаёт строку книги с временем последнего открытия.
         *
         * @param id           идентификатор
         * @param name         название
         * @param icon         обложка
         * @param text         текст
         * @param lastOpenedAt время открытия
         */
        public BookRow(long id, @Nullable String name, @Nullable byte[] icon,
                         @Nullable String text, @Nullable Long lastOpenedAt) {
            this(id, name, icon, text, lastOpenedAt, null, null);
        }

        /**
         * Создаёт полную строку книги со всеми полями локальной БД.
         *
         * @param id           идентификатор
         * @param name         название
         * @param icon         обложка
         * @param text         текст
         * @param lastOpenedAt время открытия
         * @param rating       оценка пользователя
         * @param ratedAt      время выставления оценки
         */
        public BookRow(long id, @Nullable String name, @Nullable byte[] icon,
                         @Nullable String text, @Nullable Long lastOpenedAt,
                         @Nullable Integer rating, @Nullable Long ratedAt) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.text = text;
            this.lastOpenedAt = lastOpenedAt;
            this.rating = rating;
            this.ratedAt = ratedAt;
        }
    }
}
