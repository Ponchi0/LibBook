package com.libbook.server;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class Database implements AutoCloseable {

    private final Connection conn;

    /** Открывает SQLite-базу по пути, включает WAL и инициализирует схему таблиц. */
    Database(Path dbPath) throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
        }
        initSchema();
    }

    /** Создаёт таблицы и выполняет миграции схемы при необходимости. */
    private void initSchema() throws SQLException {
        exec("""
                CREATE TABLE IF NOT EXISTS users (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  email TEXT UNIQUE,
                  name TEXT,
                  password TEXT,
                  icon TEXT
                );
                """);
        exec("""
                CREATE TABLE IF NOT EXISTS books (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT,
                  description TEXT,
                  icon TEXT,
                  tags TEXT,
                  text TEXT,
                  uploaded_by_user_id INTEGER,
                  passwordbook TEXT
                );
                """);
        exec("""
                CREATE TABLE IF NOT EXISTS book_ratings (
                  user_id INTEGER NOT NULL,
                  book_id INTEGER NOT NULL,
                  rating INTEGER NOT NULL,
                  PRIMARY KEY (user_id, book_id)
                );
                """);
        exec("""
                CREATE TABLE IF NOT EXISTS user_bookmarks (
                  user_id INTEGER NOT NULL,
                  book_id INTEGER NOT NULL,
                  last_opened_at INTEGER,
                  PRIMARY KEY (user_id, book_id)
                );
                """);
        ensureColumn("books", "tags", "TEXT");
        ensureColumn("books", "text", "TEXT");
        ensureColumn("books", "uploaded_by_user_id", "INTEGER");
        ensureColumn("books", "passwordbook", "TEXT");
        ensureColumn("books", "book_password", "TEXT");
        migrateLegacyBookPasswordColumn();
    }

    /** Копирует пароли из устаревшей колонки book_password в passwordbook. */
    private void migrateLegacyBookPasswordColumn() throws SQLException {
        exec("""
                UPDATE books SET passwordbook = book_password
                WHERE (passwordbook IS NULL OR trim(passwordbook) = '')
                  AND book_password IS NOT NULL AND trim(book_password) <> ''
                """);
    }

    /** Добавляет колонку в таблицу, если её ещё нет в схеме. */
    private void ensureColumn(String table, String column, String type) throws SQLException {
        boolean found = false;
        try (PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            exec("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        }
    }

    /** Выполняет один SQL-запрос без параметров. */
    private void exec(String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    /** Ищет пользователя по числовому идентификатору. */
    Optional<JSONObject> findUserById(long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, email, name, icon FROM users WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(rowUser(rs));
            }
        }
    }

    /** Ищет пользователя по email без учёта регистра и пробелов. */
    Optional<JSONObject> findUserByEmail(String email) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, email, name, icon FROM users WHERE lower(trim(email)) = lower(trim(?))")) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(rowUser(rs));
            }
        }
    }

    /** Ищет пользователя по отображаемому имени без учёта регистра. */
    Optional<JSONObject> findUserByName(String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, email, name, icon FROM users WHERE name IS NOT NULL AND lower(trim(name)) = lower(trim(?))")) {
            ps.setString(1, name.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(rowUser(rs));
            }
        }
    }

    /** Возвращает сохранённый хеш пароля пользователя. */
    Optional<String> findUserPassword(long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT password FROM users WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(rs.getString("password"));
            }
        }
    }

    /** Создаёт нового пользователя и возвращает сгенерированный id. */
    long insertUser(String email, String name, String passwordHash) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (email, name, password) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email.trim());
            ps.setString(2, name != null && !name.isBlank() ? name.trim() : null);
            ps.setString(3, passwordHash);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("no user id");
    }

    /** Проверяет, занято ли имя другим пользователем. */
    boolean isNameTakenByOther(String name, long excludeUserId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM users WHERE name IS NOT NULL AND lower(trim(name)) = lower(trim(?)) AND id <> ?")) {
            ps.setString(1, name.trim());
            ps.setLong(2, excludeUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Обновляет хеш пароля пользователя. */
    void updateUserPassword(long id, String passwordHash) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET password = ? WHERE id = ?")) {
            ps.setString(1, passwordHash);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    /** Обновляет отображаемое имя пользователя. */
    void updateUserName(long id, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET name = ? WHERE id = ?")) {
            ps.setString(1, name.trim());
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    /** Обновляет email пользователя с проверкой уникальности. */
    void updateUserEmail(long id, String email) throws SQLException {
        String normalized = email.trim().toLowerCase();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM users WHERE lower(trim(email)) = ? AND id <> ?")) {
            ps.setString(1, normalized);
            ps.setLong(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new SQLException("UNIQUE constraint failed: users.email");
                }
            }
        }
        try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET email = ? WHERE id = ?")) {
            ps.setString(1, normalized);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    /** Обновляет иконку профиля пользователя. */
    void updateUserIcon(long id, String icon) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET icon = ? WHERE id = ?")) {
            ps.setString(1, icon);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    /** Удаляет пользователя и связанные с ним рейтинги, закладки и загруженные книги. */
    void deleteUser(long id) throws SQLException {
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM book_ratings WHERE user_id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM user_bookmarks WHERE user_id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM books WHERE uploaded_by_user_id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    /** Возвращает краткий список книг каталога (без text и description). */
    JSONArray listBooksWithRatings() throws SQLException {
        String sql = """
                SELECT b.id, b.name, b.icon, b.tags,
                       AVG(r.rating) AS avg_rating,
                       COUNT(r.rating) AS ratings_count
                FROM books b
                LEFT JOIN book_ratings r ON r.book_id = b.id
                GROUP BY b.id
                ORDER BY b.id
                """;
        JSONArray out = new JSONArray();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rowBookListFromStats(rs));
            }
        }
        return out;
    }

    /** Возвращает краткий список популярных книг (без text и description). */
    JSONArray listPopularBooks(int limit) throws SQLException {
        String sql = """
                SELECT b.id, b.name, b.icon, b.tags,
                       AVG(r.rating) AS avg_rating,
                       COUNT(r.rating) AS ratings_count
                FROM books b
                LEFT JOIN book_ratings r ON r.book_id = b.id
                GROUP BY b.id
                ORDER BY avg_rating DESC, ratings_count DESC, b.id DESC
                LIMIT ?
                """;
        JSONArray out = new JSONArray();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rowBookListFromStats(rs));
                }
            }
        }
        return out;
    }

    /** Ищет книгу по id и при необходимости добавляет оценку указанного пользователя. */
    Optional<JSONObject> findBook(long bookId, Long userId) throws SQLException {
        String sql = """
                SELECT b.id, b.name, b.description, b.icon, b.tags, b.text,
                       AVG(r.rating) AS avg_rating,
                       COUNT(r.rating) AS ratings_count
                FROM books b
                LEFT JOIN book_ratings r ON r.book_id = b.id
                WHERE b.id = ?
                GROUP BY b.id
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Integer myRating = null;
                if (userId != null && userId > 0) {
                    myRating = findUserBookRating(userId, bookId).orElse(null);
                }
                return Optional.of(rowBookWithStats(rs, myRating));
            }
        }
    }

    /** Возвращает оценку конкретной книги от указанного пользователя. */
    private Optional<Integer> findUserBookRating(long userId, long bookId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT rating FROM book_ratings WHERE user_id = ? AND book_id = ?")) {
            ps.setLong(1, userId);
            ps.setLong(2, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(rs.getInt("rating"));
            }
        }
    }

    /** Добавляет новую книгу в каталог и возвращает её id. */
    long insertBook(
            String name,
            String description,
            String icon,
            String tagsJson,
            String text,
            long uploadedBy,
            String passwordbook
    ) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO books (name, description, icon, tags, text, uploaded_by_user_id, passwordbook) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setString(3, icon);
            ps.setString(4, tagsJson);
            ps.setString(5, text);
            ps.setLong(6, uploadedBy);
            ps.setString(7, passwordbook);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("no book id");
    }

    /** Создаёт или обновляет оценку книги пользователем в диапазоне 1–10. */
    void upsertRating(long userId, long bookId, int rating) throws SQLException {
        int r = Math.max(1, Math.min(10, rating));
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO book_ratings (user_id, book_id, rating) VALUES (?, ?, ?) "
                        + "ON CONFLICT(user_id, book_id) DO UPDATE SET rating = excluded.rating")) {
            ps.setLong(1, userId);
            ps.setLong(2, bookId);
            ps.setInt(3, r);
            ps.executeUpdate();
        }
    }

    /** Считает количество книг, загруженных указанным пользователем. */
    int countUploadedBooks(long userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) AS c FROM books WHERE uploaded_by_user_id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("c");
            }
        }
    }

    /** Возвращает краткий список книг пользователя (без text и description). */
    JSONArray listUploadedBooks(long userId) throws SQLException {
        JSONArray out = new JSONArray();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, name, icon, tags FROM books WHERE uploaded_by_user_id = ? ORDER BY id")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(HttpUtil.bookListJson(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("icon"),
                            rs.getString("tags"),
                            null,
                            null
                    ));
                }
            }
        }
        return out;
    }

    /** Возвращает сохранённый пароль доступа к книге. */
    Optional<String> findBookPassword(long bookId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT passwordbook FROM books WHERE id = ?")) {
            ps.setLong(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(rs.getString("passwordbook"));
            }
        }
    }

    /** Проверяет, принадлежит ли книга указанному пользователю как загрузившему. */
    boolean isBookOwnedBy(long bookId, long userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM books WHERE id = ? AND uploaded_by_user_id = ?")) {
            ps.setLong(1, bookId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Обновляет текстовые поля и метаданные существующей книги. */
    void updateBookContent(
            long bookId,
            String name,
            String description,
            String icon,
            String tagsJson,
            String text
    ) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE books SET name = ?, description = ?, icon = ?, tags = ?, text = ? WHERE id = ?")) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setString(3, icon);
            ps.setString(4, tagsJson);
            ps.setString(5, text);
            ps.setLong(6, bookId);
            ps.executeUpdate();
        }
    }

    /** Удаляет несколько книг пользователя и связанные рейтинги с закладками. */
    int deleteBooksBatch(long userId, List<Long> bookIds) throws SQLException {
        if (bookIds.isEmpty()) {
            return 0;
        }
        deleteRatingsAndBookmarksForBooks(bookIds);
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < bookIds.size(); i++) {
            if (i > 0) {
                placeholders.append(",");
            }
            placeholders.append("?");
        }
        String sql = "DELETE FROM books WHERE uploaded_by_user_id = ? AND id IN (" + placeholders + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            int idx = 2;
            for (Long id : bookIds) {
                ps.setLong(idx++, id);
            }
            return ps.executeUpdate();
        }
    }

    /** Удаляет загруженную книгу пользователя по точному совпадению имени. */
    int deleteUploadedBookByName(long userId, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM books WHERE uploaded_by_user_id = ? AND name = ?")) {
            ps.setLong(1, userId);
            ps.setString(2, name);
            List<Long> ids = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong("id"));
                }
            }
            if (ids.isEmpty()) {
                return 0;
            }
            deleteRatingsAndBookmarksForBooks(ids);
            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM books WHERE uploaded_by_user_id = ? AND name = ?")) {
                del.setLong(1, userId);
                del.setString(2, name);
                return del.executeUpdate();
            }
        }
    }

    /** Удаляет рейтинги и закладки для набора книг перед их удалением. */
    private void deleteRatingsAndBookmarksForBooks(List<Long> bookIds) throws SQLException {
        if (bookIds.isEmpty()) {
            return;
        }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < bookIds.size(); i++) {
            if (i > 0) {
                placeholders.append(",");
            }
            placeholders.append("?");
        }
        String in = placeholders.toString();
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM book_ratings WHERE book_id IN (" + in + ")")) {
            int idx = 1;
            for (Long id : bookIds) {
                ps.setLong(idx++, id);
            }
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM user_bookmarks WHERE book_id IN (" + in + ")")) {
            int idx = 1;
            for (Long id : bookIds) {
                ps.setLong(idx++, id);
            }
            ps.executeUpdate();
        }
    }

    /** Возвращает закладки пользователя без текста книги (только метаданные и обложка). */
    JSONArray listBookmarks(long userId) throws SQLException {
        String sql = """
                SELECT b.id AS book_id, b.name, b.icon, ub.last_opened_at
                FROM user_bookmarks ub
                JOIN books b ON b.id = ub.book_id
                WHERE ub.user_id = ?
                ORDER BY ub.last_opened_at DESC, b.id DESC
                """;
        JSONArray bookmarks = new JSONArray();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JSONObject item = new JSONObject();
                    item.put("bookId", rs.getLong("book_id"));
                    item.put("name", rs.getString("name"));
                    HttpUtil.putListIcon(item, rs.getString("icon"));
                    long opened = rs.getLong("last_opened_at");
                    if (!rs.wasNull() && opened > 0) {
                        item.put("lastOpenedAt", opened);
                    }
                    bookmarks.put(item);
                }
            }
        }
        return bookmarks;
    }

    /** Добавляет или обновляет закладку с опциональной меткой времени последнего открытия. */
    void upsertBookmark(long userId, long bookId, Long lastOpenedAt) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO user_bookmarks (user_id, book_id, last_opened_at) VALUES (?, ?, ?) "
                        + "ON CONFLICT(user_id, book_id) DO UPDATE SET last_opened_at = COALESCE(excluded.last_opened_at, user_bookmarks.last_opened_at)")) {
            ps.setLong(1, userId);
            ps.setLong(2, bookId);
            if (lastOpenedAt != null && lastOpenedAt > 0) {
                ps.setLong(3, lastOpenedAt);
            } else {
                ps.setNull(3, java.sql.Types.INTEGER);
            }
            ps.executeUpdate();
        }
    }

    /** Удаляет указанные закладки пользователя. */
    void deleteBookmarks(long userId, List<Long> bookIds) throws SQLException {
        if (bookIds.isEmpty()) {
            return;
        }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < bookIds.size(); i++) {
            if (i > 0) {
                placeholders.append(",");
            }
            placeholders.append("?");
        }
        String sql = "DELETE FROM user_bookmarks WHERE user_id = ? AND book_id IN (" + placeholders + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            int idx = 2;
            for (Long id : bookIds) {
                ps.setLong(idx++, id);
            }
            ps.executeUpdate();
        }
    }

    /** Пакетно синхронизирует закладки пользователя из JSON-массива в одной транзакции. */
    void syncBookmarks(long userId, JSONArray items) throws SQLException {
        conn.setAutoCommit(false);
        try {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                long bookId = item.optLong("bookId", -1);
                if (bookId <= 0) {
                    continue;
                }
                Long lastOpened = null;
                if (item.has("lastOpenedAt") && !item.isNull("lastOpenedAt")) {
                    lastOpened = item.optLong("lastOpenedAt");
                }
                upsertBookmark(userId, bookId, lastOpened);
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    /** Преобразует строку результата SQL в JSON-объект пользователя. */
    private JSONObject rowUser(ResultSet rs) throws SQLException {
        return HttpUtil.userJson(
                rs.getLong("id"),
                rs.getString("email"),
                rs.getString("name"),
                rs.getString("icon")
        );
    }

    /** Преобразует строку SQL в краткий JSON книги для списков (с рейтингами). */
    private JSONObject rowBookListFromStats(ResultSet rs) throws SQLException {
        double avg = rs.getDouble("avg_rating");
        if (rs.wasNull()) {
            avg = Double.NaN;
        }
        int count = rs.getInt("ratings_count");
        Double avgBox = Double.isNaN(avg) ? null : avg;
        return HttpUtil.bookListJson(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("icon"),
                rs.getString("tags"),
                avgBox,
                count
        );
    }

    /** Преобразует строку результата SQL в полный JSON книги со статистикой рейтингов. */
    private JSONObject rowBookWithStats(ResultSet rs, Integer myRating) throws SQLException {
        double avg = rs.getDouble("avg_rating");
        if (rs.wasNull()) {
            avg = Double.NaN;
        }
        int count = rs.getInt("ratings_count");
        Double avgBox = Double.isNaN(avg) ? null : avg;
        return HttpUtil.bookJson(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("icon"),
                rs.getString("tags"),
                rs.getString("text"),
                avgBox,
                count,
                myRating
        );
    }

    /** Закрывает соединение с базой данных. */
    @Override
    public void close() throws SQLException {
        conn.close();
    }
}

