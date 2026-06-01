package com.libbook.server;

import fi.iki.elonen.NanoHTTPD;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LibBookHttpServer extends NanoHTTPD {

    private static final Pattern USER_ID = Pattern.compile("^/users/(\\d+)$");
    private static final Pattern USER_SUB = Pattern.compile("^/users/(\\d+)/(\\w[\\w-]*)$");
    private static final Pattern USER_BOOKMARKS_SYNC =
            Pattern.compile("^/users/(\\d+)/bookmarks/sync$");
    private static final Pattern USER_BOOKMARKS_DELETE =
            Pattern.compile("^/users/(\\d+)/bookmarks/delete-batch$");
    private static final Pattern BOOK_ID = Pattern.compile("^/books/(\\d+)$");
    private static final Pattern BOOK_SUB = Pattern.compile("^/books/(\\d+)/(\\w[\\w-]*)$");
    private static final Pattern BOOK_RATING = Pattern.compile("^/books/(\\d+)/rating$");

    private final Database db;
    private final Map<String, String> verificationCodes = new ConcurrentHashMap<>();

    /** Создаёт HTTP-сервер на указанном порту с доступом к базе данных. */
    LibBookHttpServer(int port, Database db) {
        super(port);
        this.db = db;
    }

    /**
     * Обрабатывает входящий HTTP-запрос: отвечает на preflight CORS,
     * маршрутизирует запрос и перехватывает ошибки БД и сервера.
     */
    @Override
    public Response serve(IHTTPSession session) {
        if (Method.OPTIONS.equals(session.getMethod())) {
            return HttpUtil.corsPreflight();
        }
        try {
            return route(session);
        } catch (SQLException e) {
            e.printStackTrace();
            return HttpUtil.error(500, "db_error");
        } catch (Exception e) {
            e.printStackTrace();
            return HttpUtil.error(500, "internal_error");
        }
    }

    /** Направляет запрос на обработчик по пути и HTTP-методу. */
    private Response route(IHTTPSession session) throws Exception {
        String path = HttpUtil.pathOnly(session.getUri());
        Method method = session.getMethod();
        Map<String, String> query = HttpUtil.queryParams(session);

        if ("/health".equals(path) && Method.GET.equals(method)) {
            return HttpUtil.okJson(new JSONObject().put("ok", true));
        }

        if ("/users".equals(path) && Method.POST.equals(method)) {
            return registerUser(session);
        }
        if ("/users/by-email".equals(path) && Method.GET.equals(method)) {
            return getUserByEmail(query);
        }
        if ("/users/by-name".equals(path) && Method.GET.equals(method)) {
            return getUserByName(query);
        }

        if ("/books".equals(path)) {
            if (Method.GET.equals(method)) {
                return HttpUtil.okJson(db.listBooksWithRatings());
            }
            if (Method.POST.equals(method)) {
                return createBook(session);
            }
        }
        if ("/books/popular".equals(path) && Method.GET.equals(method)) {
            int limit = HttpUtil.parseInt(query.getOrDefault("limit", "10"), 10);
            limit = Math.max(1, Math.min(limit, 50));
            return HttpUtil.okJson(db.listPopularBooks(limit));
        }
        if ("/books/delete-batch".equals(path) && Method.POST.equals(method)) {
            return deleteBooksBatch(session);
        }
        if ("/books/delete-uploaded".equals(path) && Method.POST.equals(method)) {
            return deleteUploadedBook(session);
        }

        if ("/auth/verification/request".equals(path) && Method.POST.equals(method)) {
            return requestVerification(session);
        }
        if ("/auth/verification/verify".equals(path) && Method.POST.equals(method)) {
            return verifyCode(session);
        }

        Matcher bookRating = BOOK_RATING.matcher(path);
        if (bookRating.matches() && Method.POST.equals(method)) {
            return postBookRating(bookRating.group(1), session);
        }

        Matcher bookSub = BOOK_SUB.matcher(path);
        if (bookSub.matches()) {
            long bookId = Long.parseLong(bookSub.group(1));
            String action = bookSub.group(2);
            if ("verify-password".equals(action) && Method.POST.equals(method)) {
                return verifyBookPassword(bookId, session);
            }
        }

        Matcher bookId = BOOK_ID.matcher(path);
        if (bookId.matches()) {
            long id = Long.parseLong(bookId.group(1));
            if (Method.GET.equals(method)) {
                Long userId = null;
                String uid = query.get("userId");
                if (uid != null && !uid.isBlank()) {
                    userId = HttpUtil.parseLong(uid, -1);
                }
                return getBook(id, userId);
            }
            if (Method.PUT.equals(method)) {
                return updateBook(id, session);
            }
        }

        Matcher bmSync = USER_BOOKMARKS_SYNC.matcher(path);
        if (bmSync.matches()) {
            long userId = Long.parseLong(bmSync.group(1));
            if (!Method.PUT.equals(method)) {
                return HttpUtil.error(405, "method_not_allowed");
            }
            return syncBookmarks(userId, session);
        }

        Matcher bmDelete = USER_BOOKMARKS_DELETE.matcher(path);
        if (bmDelete.matches()) {
            long userId = Long.parseLong(bmDelete.group(1));
            if (!Method.POST.equals(method)) {
                return HttpUtil.error(405, "method_not_allowed");
            }
            return deleteBookmarksBatch(userId, session);
        }

        Matcher userSub = USER_SUB.matcher(path);
        if (userSub.matches()) {
            long userId = Long.parseLong(userSub.group(1));
            String action = userSub.group(2);
            return handleUserAction(userId, action, method, session);
        }

        Matcher userIdMatch = USER_ID.matcher(path);
        if (userIdMatch.matches()) {
            long userId = Long.parseLong(userIdMatch.group(1));
            if (Method.GET.equals(method)) {
                return getUserById(userId);
            }
            if (Method.PUT.equals(method)) {
                return updateUser(userId, session);
            }
        }

        return HttpUtil.error(404, "not_found");
    }

    /** Регистрирует нового пользователя по email, паролю и имени. */
    private Response registerUser(IHTTPSession session) throws Exception {
        JSONObject body = HttpUtil.readJson(session);
        String email = body.optString("email", "").trim();
        String password = body.optString("password", "");
        String name = body.optString("name", "").trim();
        if (email.isEmpty() || password.isEmpty()) {
            return HttpUtil.error(400, "email_and_password_required");
        }
        if (!name.isEmpty() && db.isNameTakenByOther(name, -1)) {
            return HttpUtil.error(409, "name already exists");
        }
        try {
            long id = db.insertUser(email, name, PasswordUtil.hash(password));
            return HttpUtil.json(201, db.findUserById(id).orElse(new JSONObject()));
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                return HttpUtil.error(409, "email_taken");
            }
            throw e;
        }
    }

    /** Возвращает пользователя по email из query-параметров. */
    private Response getUserByEmail(Map<String, String> query) throws SQLException {
        String email = query.getOrDefault("email", "").trim();
        if (email.isEmpty()) {
            return HttpUtil.error(400, "email_required");
        }
        Optional<JSONObject> user = db.findUserByEmail(email);
        return user.map(HttpUtil::okJson).orElseGet(() -> HttpUtil.error(404, "user_not_found"));
    }

    /** Возвращает пользователя по имени из query-параметров. */
    private Response getUserByName(Map<String, String> query) throws SQLException {
        String name = query.getOrDefault("name", "").trim();
        if (name.isEmpty()) {
            return HttpUtil.error(400, "name_required");
        }
        Optional<JSONObject> user = db.findUserByName(name);
        return user.map(HttpUtil::okJson).orElseGet(() -> HttpUtil.error(404, "user_not_found"));
    }

    /** Возвращает пользователя по числовому идентификатору. */
    private Response getUserById(long userId) throws SQLException {
        Optional<JSONObject> user = db.findUserById(userId);
        return user.map(HttpUtil::okJson).orElseGet(() -> HttpUtil.error(404, "user_not_found"));
    }

    /** Обновляет профиль пользователя: пароль, имя, email и иконку. */
    private Response updateUser(long userId, IHTTPSession session) throws Exception {
        if (db.findUserById(userId).isEmpty()) {
            return HttpUtil.error(404, "user_not_found");
        }
        JSONObject body = HttpUtil.readJson(session);
        if (body.has("password")) {
            db.updateUserPassword(userId, PasswordUtil.hash(body.getString("password")));
        }
        if (body.has("name")) {
            String name = body.getString("name").trim();
            if (db.isNameTakenByOther(name, userId)) {
                return HttpUtil.error(409, "name already exists");
            }
            db.updateUserName(userId, name);
        }
        if (body.has("email")) {
            try {
                db.updateUserEmail(userId, body.getString("email").trim());
            } catch (SQLException e) {
                String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                if (msg.contains("unique") || msg.contains("constraint")) {
                    return HttpUtil.error(409, "email_taken");
                }
                throw e;
            }
        }
        if (body.has("iconBase64")) {
            db.updateUserIcon(userId, body.optString("iconBase64", null));
        }
        return HttpUtil.okJson(db.findUserById(userId).orElse(new JSONObject()));
    }

    /** Обрабатывает подресурсы пользователя: пароль, профиль, закладки и удаление. */
    private Response handleUserAction(long userId, String action, Method method, IHTTPSession session)
            throws Exception {
        if (db.findUserById(userId).isEmpty()) {
            return HttpUtil.error(404, "user_not_found");
        }
        return switch (action) {
            case "password" -> {
                if (!Method.POST.equals(method)) {
                    yield HttpUtil.error(405, "method_not_allowed");
                }
                yield updatePassword(userId, session);
            }
            case "name" -> {
                if (!Method.POST.equals(method)) {
                    yield HttpUtil.error(405, "method_not_allowed");
                }
                yield updateName(userId, session);
            }
            case "email" -> {
                if (!Method.POST.equals(method)) {
                    yield HttpUtil.error(405, "method_not_allowed");
                }
                yield updateEmail(userId, session);
            }
            case "icon" -> {
                if (!Method.POST.equals(method)) {
                    yield HttpUtil.error(405, "method_not_allowed");
                }
                yield updateIcon(userId, session);
            }
            case "verify-password" -> {
                if (!Method.POST.equals(method)) {
                    yield HttpUtil.error(405, "method_not_allowed");
                }
                yield verifyPassword(userId, session);
            }
            case "delete" -> {
                if (!Method.POST.equals(method)) {
                    yield HttpUtil.error(405, "method_not_allowed");
                }
                yield deleteUser(userId, session);
            }
            case "uploaded-books-count" -> {
                if (!Method.GET.equals(method)) {
                    yield HttpUtil.error(405, "method_not_allowed");
                }
                yield HttpUtil.okJson(new JSONObject().put("count", db.countUploadedBooks(userId)));
            }
            case "uploaded-books" -> {
                if (!Method.GET.equals(method)) {
                    yield HttpUtil.error(405, "method_not_allowed");
                }
                yield HttpUtil.okJson(db.listUploadedBooks(userId));
            }
            case "bookmarks" -> {
                if (Method.GET.equals(method)) {
                    yield HttpUtil.okJson(new JSONObject().put("bookmarks", db.listBookmarks(userId)));
                }
                if (Method.POST.equals(method)) {
                    yield upsertBookmark(userId, session);
                }
                yield HttpUtil.error(405, "method_not_allowed");
            }
            default -> HttpUtil.error(404, "not_found");
        };
    }

    /** Синхронизирует список закладок пользователя с данными из тела запроса. */
    private Response syncBookmarks(long userId, IHTTPSession session) throws Exception {
        if (db.findUserById(userId).isEmpty()) {
            return HttpUtil.error(404, "user_not_found");
        }
        JSONObject body = HttpUtil.readJson(session);
        JSONArray items = body.optJSONArray("bookmarks");
        if (items == null) {
            items = new JSONArray();
        }
        db.syncBookmarks(userId, items);
        return HttpUtil.okJson(new JSONObject().put("ok", true));
    }

    /** Удаляет указанные закладки пользователя пакетом. */
    private Response deleteBookmarksBatch(long userId, IHTTPSession session) throws Exception {
        if (db.findUserById(userId).isEmpty()) {
            return HttpUtil.error(404, "user_not_found");
        }
        JSONObject body = HttpUtil.readJson(session);
        List<Long> bookIds = HttpUtil.longArray(body.optJSONArray("bookIds"));
        db.deleteBookmarks(userId, bookIds);
        return HttpUtil.okJson(new JSONObject().put("ok", true));
    }

    /** Меняет пароль пользователя после проверки тела запроса. */
    private Response updatePassword(long userId, IHTTPSession session) throws Exception {
        JSONObject body = HttpUtil.readJson(session);
        String password = body.optString("password", "");
        if (password.isEmpty()) {
            return HttpUtil.error(400, "password_required");
        }
        db.updateUserPassword(userId, PasswordUtil.hash(password));
        return HttpUtil.okJson(new JSONObject().put("ok", true));
    }

    /** Меняет отображаемое имя пользователя с проверкой уникальности. */
    private Response updateName(long userId, IHTTPSession session) throws Exception {
        JSONObject body = HttpUtil.readJson(session);
        String name = body.optString("name", "").trim();
        if (name.isEmpty()) {
            return HttpUtil.error(400, "name_required");
        }
        if (db.isNameTakenByOther(name, userId)) {
            return HttpUtil.error(409, "name already exists");
        }
        db.updateUserName(userId, name);
        return HttpUtil.okJson(db.findUserById(userId).orElse(new JSONObject()));
    }

    /** Меняет email пользователя с проверкой уникальности. */
    private Response updateEmail(long userId, IHTTPSession session) throws Exception {
        JSONObject body = HttpUtil.readJson(session);
        String email = body.optString("email", "").trim();
        if (email.isEmpty()) {
            return HttpUtil.error(400, "email_required");
        }
        try {
            db.updateUserEmail(userId, email);
        } catch (SQLException e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("unique") || msg.contains("constraint")) {
                return HttpUtil.error(409, "email_taken");
            }
            throw e;
        }
        return HttpUtil.okJson(db.findUserById(userId).orElse(new JSONObject()));
    }

    /** Обновляет иконку профиля пользователя из Base64 в теле запроса. */
    private Response updateIcon(long userId, IHTTPSession session) throws Exception {
        JSONObject body = HttpUtil.readJson(session);
        String icon = body.optString("iconBase64", null);
        db.updateUserIcon(userId, icon);
        return HttpUtil.okJson(db.findUserById(userId).orElse(new JSONObject()));
    }

    /** Проверяет, совпадает ли переданный пароль с сохранённым хешем пользователя. */
    private Response verifyPassword(long userId, IHTTPSession session) throws Exception {
        JSONObject body = HttpUtil.readJson(session);
        String password = body.optString("password", "");
        Optional<String> stored = db.findUserPassword(userId);
        boolean valid = stored.isPresent() && PasswordUtil.matches(stored.get(), password);
        return HttpUtil.okJson(new JSONObject().put("valid", valid));
    }

    /** Удаляет аккаунт пользователя после проверки пароля. */
    private Response deleteUser(long userId, IHTTPSession session) throws Exception {
        JSONObject body = HttpUtil.readJson(session);
        String password = body.optString("password", "");
        Optional<String> stored = db.findUserPassword(userId);
        if (stored.isEmpty() || !PasswordUtil.matches(stored.get(), password)) {
            return HttpUtil.error(401, "wrong_password");
        }
        db.deleteUser(userId);
        return HttpUtil.okJson(new JSONObject().put("ok", true));
    }

    /** Возвращает книгу по id с опциональной оценкой запрашивающего пользователя. */
    private Response getBook(long bookId, Long userId) throws SQLException {
        Optional<JSONObject> book = db.findBook(bookId, userId);
        return book.map(HttpUtil::okJson).orElseGet(() -> HttpUtil.error(404, "Book not found"));
    }

    /** Создаёт новую книгу от имени загрузившего пользователя. */
    private Response createBook(IHTTPSession session) throws Exception {
        JSONObject body = HttpUtil.readJson(session);
        String name = body.optString("name", null);
        String description = body.optString("description", null);
        String icon = body.optString("iconBase64", body.optString("icon", null));
        String text = body.isNull("text") ? null : body.optString("text", null);
        String tagsJson = HttpUtil.tagsFromJson(body.optJSONArray("tags"));
        long uploadedBy = body.optLong("uploadedByUserId", -1);
        if (uploadedBy <= 0) {
            return HttpUtil.error(400, "uploaded_by_required");
        }
        String passwordbook = body.optString("passwordbook", body.optString("bookPassword", "")).trim();
        if (passwordbook.isEmpty()) {
            return HttpUtil.error(400, "passwordbook_required");
        }
        long id = db.insertBook(
                name,
                description,
                icon,
                tagsJson,
                text,
                uploadedBy,
                PasswordUtil.hash(passwordbook)
        );
        Optional<JSONObject> book = db.findBook(id, null);
        return HttpUtil.json(201, book.orElse(new JSONObject()));
    }

    /** Проверяет пароль доступа к книге. */
    private Response verifyBookPassword(long bookId, IHTTPSession session) throws Exception {
        if (db.findBook(bookId, null).isEmpty()) {
            return HttpUtil.error(404, "book_not_found");
        }
        JSONObject body = HttpUtil.readJson(session);
        String passwordbook = body.optString("passwordbook", body.optString("bookPassword", "")).trim();
        if (passwordbook.isEmpty()) {
            return HttpUtil.error(400, "passwordbook_required");
        }
        Optional<String> stored = db.findBookPassword(bookId);
        boolean valid = stored.isPresent() && PasswordUtil.matches(stored.get(), passwordbook);
        return HttpUtil.okJson(new JSONObject().put("valid", valid));
    }

    /** Обновляет содержимое книги после проверки владельца и пароля книги. */
    private Response updateBook(long bookId, IHTTPSession session) throws Exception {
        if (db.findBook(bookId, null).isEmpty()) {
            return HttpUtil.error(404, "book_not_found");
        }
        JSONObject body = HttpUtil.readJson(session);
        long userId = body.optLong("userId", -1);
        String passwordbook = body.optString("passwordbook", body.optString("bookPassword", "")).trim();
        if (userId <= 0 || passwordbook.isEmpty()) {
            return HttpUtil.error(400, "invalid_request");
        }
        if (!db.isBookOwnedBy(bookId, userId)) {
            return HttpUtil.error(403, "forbidden");
        }
        Optional<String> stored = db.findBookPassword(bookId);
        if (stored.isEmpty() || !PasswordUtil.matches(stored.get(), passwordbook)) {
            return HttpUtil.error(401, "wrong_book_password");
        }
        String name = body.optString("name", "").trim();
        String description = body.optString("description", "").trim();
        String icon = body.optString("iconBase64", body.optString("icon", null));
        String text = body.isNull("text") ? null : body.optString("text", null);
        String tagsJson = HttpUtil.tagsFromJson(body.optJSONArray("tags"));
        if (name.isEmpty() || description.isEmpty()) {
            return HttpUtil.error(400, "name_and_description_required");
        }
        db.updateBookContent(bookId, name, description, icon, tagsJson, text);
        return HttpUtil.okJson(db.findBook(bookId, userId).orElse(new JSONObject()));
    }

    /** Принимает или обновляет оценку книги от пользователя. */
    private Response postBookRating(String bookIdStr, IHTTPSession session) throws Exception {
        long bookId = Long.parseLong(bookIdStr);
        JSONObject body = HttpUtil.readJson(session);
        long userId = body.optLong("userId", -1);
        int rating = body.optInt("rating", -1);
        if (userId <= 0 || rating < 1) {
            return HttpUtil.error(400, "invalid_rating");
        }
        if (db.findBook(bookId, null).isEmpty()) {
            return HttpUtil.error(404, "Book not found");
        }
        db.upsertRating(userId, bookId, rating);
        return HttpUtil.okJson(db.findBook(bookId, userId).orElse(new JSONObject()));
    }

    /** Удаляет несколько книг пользователя после проверки пароля аккаунта. */
    private Response deleteBooksBatch(IHTTPSession session) throws Exception {
        JSONObject body = HttpUtil.readJson(session);
        long userId = body.optLong("userId", -1);
        String password = body.optString("password", "");
        JSONArray ids = body.optJSONArray("bookIds");
        if (userId <= 0 || password.isEmpty() || ids == null) {
            return HttpUtil.error(400, "invalid_request");
        }
        Optional<String> stored = db.findUserPassword(userId);
        if (stored.isEmpty() || !PasswordUtil.matches(stored.get(), password)) {
            return HttpUtil.error(401, "wrong_password");
        }
        List<Long> bookIds = HttpUtil.longArray(ids);
        db.deleteBooksBatch(userId, bookIds);
        return HttpUtil.okJson(new JSONObject().put("ok", true));
    }

    /** Удаляет загруженную книгу пользователя по имени после проверки пароля. */
    private Response deleteUploadedBook(IHTTPSession session) throws Exception {
        JSONObject body = HttpUtil.readJson(session);
        long userId = body.optLong("userId", -1);
        String password = body.optString("password", "");
        String name = body.optString("name", "").trim();
        if (userId <= 0 || password.isEmpty() || name.isEmpty()) {
            return HttpUtil.error(400, "invalid_request");
        }
        Optional<String> stored = db.findUserPassword(userId);
        if (stored.isEmpty() || !PasswordUtil.matches(stored.get(), password)) {
            return HttpUtil.error(401, "wrong_password");
        }
        db.deleteUploadedBookByName(userId, name);
        return HttpUtil.okJson(new JSONObject().put("ok", true));
    }

    /** Добавляет или обновляет одну закладку пользователя. */
    private Response upsertBookmark(long userId, IHTTPSession session) throws Exception {
        JSONObject body = HttpUtil.readJson(session);
        long bookId = body.optLong("bookId", -1);
        if (bookId <= 0) {
            return HttpUtil.error(400, "book_id_required");
        }
        Long lastOpened = null;
        if (body.has("lastOpenedAt") && !body.isNull("lastOpenedAt")) {
            lastOpened = body.optLong("lastOpenedAt");
        }
        db.upsertBookmark(userId, bookId, lastOpened);
        return HttpUtil.okJson(new JSONObject().put("ok", true));
    }

    /** Генерирует и сохраняет код подтверждения для указанного email и цели. */
    private Response requestVerification(IHTTPSession session) throws Exception {
        JSONObject body = HttpUtil.readJson(session);
        String email = body.optString("email", "").trim().toLowerCase();
        String purpose = body.optString("purpose", "default");
        if (email.isEmpty()) {
            return HttpUtil.error(400, "email_required");
        }
        String code = String.format("%06d", (int) (Math.random() * 1_000_000));
        verificationCodes.put(email + "|" + purpose, code);
        System.out.println("[verification] " + email + " purpose=" + purpose + " code=" + code);
        return HttpUtil.okJson(new JSONObject().put("ok", true));
    }

    /** Проверяет код подтверждения и удаляет его при успешной проверке. */
    private Response verifyCode(IHTTPSession session) throws Exception {
        JSONObject body = HttpUtil.readJson(session);
        String email = body.optString("email", "").trim().toLowerCase();
        String purpose = body.optString("purpose", "default");
        String code = body.optString("code", "").trim();
        String expected = verificationCodes.get(email + "|" + purpose);
        boolean ok = expected != null && expected.equals(code);
        if (ok) {
            verificationCodes.remove(email + "|" + purpose);
        }
        return HttpUtil.okJson(new JSONObject().put("valid", ok));
    }
}

