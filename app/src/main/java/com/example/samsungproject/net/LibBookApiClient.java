package com.example.samsungproject.net;

import org.json.JSONObject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class LibBookApiClient {

    public static final String PURPOSE_PASSWORD_RESET = "password_reset";
    private static final int DEFAULT_CONNECT_MS = 20_000;
    private static final int DEFAULT_READ_MS = 20_000;

    private static final int BOOK_UPLOAD_CONNECT_MS = 30_000;
    private static final int BOOK_UPLOAD_READ_MS = 30_000;
    private static final int ICON_UPLOAD_CONNECT_MS = 45_000;
    private static final int ICON_UPLOAD_READ_MS = 45_000;

    /**
     * Приватный конструктор, запрещающий создание экземпляров утилитного класса.
     */
    private LibBookApiClient() {
    }

    public static final class HttpResult {
        public final int statusCode;
        public final String body;

        /**
         * Создаёт результат HTTP-запроса с кодом статуса и телом ответа.
         *
         * @param statusCode HTTP-код ответа
         * @param body       тело ответа (пустая строка, если {@code null})
         */
        public HttpResult(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body != null ? body : "";
        }
    }

    /**
     * Запрашивает пользователя по адресу электронной почты.
     *
     * @param baseUrl базовый URL API
     * @param email   email для поиска
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult getUserByEmail(String baseUrl, String email) throws Exception {
        String q = "/users/by-email?email=" + java.net.URLEncoder.encode(email, StandardCharsets.UTF_8.name());
        return http("GET", baseUrl, q, null, false);
    }

    /**
     * Запрашивает пользователя по отображаемому имени.
     *
     * @param baseUrl базовый URL API
     * @param name    имя для поиска
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult getUserByName(String baseUrl, String name) throws Exception {
        String q = "/users/by-name?name=" + java.net.URLEncoder.encode(name, StandardCharsets.UTF_8.name());
        return http("GET", baseUrl, q, null, false);
    }

    /**
     * Проверяет, занято ли имя другим пользователем (не {@code excludeUserId}).
     *
     * @param lookup        результат поиска пользователя по имени
     * @param excludeUserId id текущего пользователя, которого нужно исключить
     * @return {@code true}, если имя принадлежит другому пользователю
     */
    public static boolean isUserNameTakenByOther(@NonNull HttpResult lookup, long excludeUserId) {
        if (lookup.statusCode != 200) {
            return false;
        }
        try {
            org.json.JSONObject user = new org.json.JSONObject(lookup.body);
            long id = user.optLong("id", -1L);
            return id > 0 && id != excludeUserId;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Определяет, вернул ли сервер ошибку «имя уже занято» (HTTP 409).
     *
     * @param result результат HTTP-запроса
     * @return {@code true} для конфликта имени
     */
    public static boolean isNameAlreadyExistsError(@NonNull HttpResult result) {
        if (result.statusCode != 409) {
            return false;
        }
        String body = result.body.toLowerCase(java.util.Locale.ROOT);
        return body.contains("name already exists") || body.contains("name_already_exists");
    }

    /**
     * Запрашивает пользователя по идентификатору на сервере.
     *
     * @param baseUrl базовый URL API
     * @param userId  идентификатор пользователя
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult getUserById(String baseUrl, long userId) throws Exception {
        return http("GET", baseUrl, "/users/" + userId, null, false);
    }

    /**
     * Возвращает id пользователя на сервере: из кэша, проверки или повторного lookup по email.
     *
     * @param context контекст для SharedPreferences
     * @param baseUrl базовый URL API
     * @return server user id или {@code -1}
     */
    public static long resolveServerUserId(@NonNull android.content.Context context, @NonNull String baseUrl) {
        android.content.SharedPreferences prefs =
                context.getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE);
        long cached = prefs.getLong("server_user_id", -1L);
        if (cached > 0) {
            try {
                HttpResult check = getUserById(baseUrl, cached);
                if (check.statusCode == 200) {
                    return cached;
                }
            } catch (Exception ignored) {
            }
            prefs.edit().remove("server_user_id").apply();
        }
        String email = prefs.getString("email", "");
        if (email == null || email.trim().isEmpty()) {
            return -1L;
        }
        try {
            HttpResult r = getUserByEmail(baseUrl, email.trim());
            if (r.statusCode != 200) {
                return -1L;
            }
            JSONObject user = new JSONObject(r.body);
            long userId = user.optLong("id", -1L);
            if (userId > 0) {
                prefs.edit().putLong("server_user_id", userId).apply();
            }
            return userId;
        } catch (Exception e) {
            return -1L;
        }
    }

    /**
     * Регистрирует нового пользователя на сервере.
     *
     * @param baseUrl  базовый URL API
     * @param email    email
     * @param name     имя
     * @param password пароль
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult postRegisterUser(String baseUrl, String email, String name, String password) throws Exception {
        JSONObject json = new JSONObject();
        json.put("email", email);
        json.put("name", name);
        json.put("password", password);
        return http("POST", baseUrl, "/users", json.toString(), true);
    }

    /**
     * Запрашивает отправку кода верификации на email.
     *
     * @param baseUrl базовый URL API
     * @param email   адрес получателя
     * @param purpose цель верификации (например, сброс пароля)
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult requestVerification(String baseUrl, String email, String purpose) throws Exception {
        JSONObject json = new JSONObject();
        json.put("email", email);
        json.put("purpose", purpose);
        return http("POST", baseUrl, "/auth/verification/request", json.toString(), true);
    }

    /**
     * Проверяет код верификации, введённый пользователем.
     *
     * @param baseUrl базовый URL API
     * @param email   email
     * @param purpose цель верификации
     * @param code    код из письма
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult verifyCode(String baseUrl, String email, String purpose, String code) throws Exception {
        JSONObject json = new JSONObject();
        json.put("email", email);
        json.put("purpose", purpose);
        json.put("code", code);
        return http("POST", baseUrl, "/auth/verification/verify", json.toString(), true);
    }

    /**
     * Обновляет пароль пользователя (POST с fallback на PUT).
     *
     * @param baseUrl     базовый URL API
     * @param userId      id пользователя
     * @param newPassword новый пароль
     * @return результат успешного запроса или последней попытки
     * @throws Exception при ошибке сети
     */
    public static HttpResult putUserPassword(String baseUrl, long userId, String newPassword) throws Exception {
        JSONObject json = new JSONObject();
        json.put("password", newPassword);
        String body = json.toString();
        HttpResult post = http("POST", baseUrl, "/users/" + userId + "/password", body, true);
        if (post.statusCode == 200) {
            return post;
        }
        HttpResult put = http("PUT", baseUrl, "/users/" + userId, body, true);
        return put.statusCode == 200 ? put : post;
    }

    /**
     * Обновляет отображаемое имя пользователя (POST с fallback на PUT).
     *
     * @param baseUrl базовый URL API
     * @param userId  id пользователя
     * @param name    новое имя
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult putUserName(String baseUrl, long userId, String name) throws Exception {
        JSONObject json = new JSONObject();
        json.put("name", name);
        String body = json.toString();
        HttpResult post = http("POST", baseUrl, "/users/" + userId + "/name", body, true);
        if (post.statusCode == 200) {
            return post;
        }
        HttpResult put = http("PUT", baseUrl, "/users/" + userId, body, true);
        return put.statusCode == 200 ? put : post;
    }

    /**
     * Обновляет email пользователя (POST с fallback на PUT).
     *
     * @param baseUrl базовый URL API
     * @param userId  id пользователя
     * @param email   новый email
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult putUserEmail(String baseUrl, long userId, String email) throws Exception {
        JSONObject json = new JSONObject();
        json.put("email", email);
        String body = json.toString();
        HttpResult post = http("POST", baseUrl, "/users/" + userId + "/email", body, true);
        if (post.statusCode == 200) {
            return post;
        }
        HttpResult put = http("PUT", baseUrl, "/users/" + userId, body, true);
        return put.statusCode == 200 ? put : post;
    }

    /**
     * Проверяет успешность обновления email по телу ответа сервера.
     *
     * @param result        результат HTTP-запроса
     * @param expectedEmail ожидаемый новый email
     * @return {@code true}, если обновление подтверждено
     */
    public static boolean isEmailUpdateSuccess(@NonNull HttpResult result, @NonNull String expectedEmail) {
        if (result.statusCode != 200) {
            return false;
        }
        try {
            JSONObject user = new JSONObject(result.body != null ? result.body : "{}");
            if (user.optBoolean("ok", false)) {
                return true;
            }
            String email = user.optString("email", "").trim();
            return !email.isEmpty()
                    && email.equalsIgnoreCase(expectedEmail.trim());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Загружает аватар пользователя в формате data URL Base64.
     *
     * @param baseUrl    базовый URL API
     * @param userId     id пользователя
     * @param mimeType   MIME изображения
     * @param imageBytes байты изображения
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult putUserIcon(String baseUrl, long userId, String mimeType, byte[] imageBytes) throws Exception {
        String mime = (mimeType != null && !mimeType.isEmpty()) ? mimeType : "image/jpeg";
        String b64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
        String dataUrl = "data:" + mime + ";base64," + b64;
        JSONObject json = new JSONObject();
        json.put("iconBase64", dataUrl);
        String body = json.toString();
        HttpResult post = http("POST", baseUrl, "/users/" + userId + "/icon", body, true,
                ICON_UPLOAD_CONNECT_MS, ICON_UPLOAD_READ_MS);
        if (post.statusCode == 200) {
            return post;
        }
        HttpResult put = http("PUT", baseUrl, "/users/" + userId, body, true,
                ICON_UPLOAD_CONNECT_MS, ICON_UPLOAD_READ_MS);
        return put.statusCode == 200 ? put : post;
    }

    /**
     * Проверяет пароль пользователя на сервере.
     *
     * @param baseUrl  базовый URL API
     * @param userId   id пользователя
     * @param password пароль для проверки
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult verifyUserPassword(String baseUrl, long userId, String password) throws Exception {
        JSONObject json = new JSONObject();
        json.put("password", password);
        return http("POST", baseUrl, "/users/" + userId + "/verify-password", json.toString(), true);
    }

    /**
     * Удаляет аккаунт пользователя после проверки пароля.
     *
     * @param baseUrl  базовый URL API
     * @param userId   id пользователя
     * @param password пароль для подтверждения
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult deleteUserAccount(String baseUrl, long userId, String password) throws Exception {
        JSONObject json = new JSONObject();
        json.put("password", password);
        return http("POST", baseUrl, "/users/" + userId + "/delete", json.toString(), true);
    }

    /**
     * Возвращает список всех книг каталога.
     *
     * @param baseUrl базовый URL API
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult getBooks(String baseUrl) throws Exception {
        return http("GET", baseUrl, "/books", null, false);
    }

    /**
     * Возвращает популярные книги с ограничением по количеству (1–50).
     *
     * @param baseUrl базовый URL API
     * @param limit   максимальное число книг
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult getPopularBooks(String baseUrl, int limit) throws Exception {
        int lim = Math.max(1, Math.min(limit, 50));
        return http("GET", baseUrl, "/books/popular?limit=" + lim, null, false);
    }

    /**
     * Запрашивает одну книгу по id без контекста пользователя.
     *
     * @param baseUrl базовый URL API
     * @param bookId  id книги
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult getBook(String baseUrl, long bookId) throws Exception {
        return http("GET", baseUrl, "/books/" + bookId, null, false);
    }

    /**
     * Запрашивает книгу с учётом id пользователя (закладки, рейтинг и т.д.).
     *
     * @param baseUrl базовый URL API
     * @param bookId  id книги
     * @param userId  id пользователя
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult getBook(String baseUrl, long bookId, long userId) throws Exception {
        return http("GET", baseUrl, "/books/" + bookId + "?userId=" + userId, null, false);
    }

    /**
     * Возвращает количество книг, загруженных пользователем.
     *
     * @param baseUrl базовый URL API
     * @param userId  id пользователя
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult getUserUploadedBooksCount(String baseUrl, long userId) throws Exception {
        return http("GET", baseUrl, "/users/" + userId + "/uploaded-books-count", null, false);
    }

    /**
     * Возвращает список книг, загруженных пользователем.
     *
     * @param baseUrl базовый URL API
     * @param userId  id пользователя
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult getUserUploadedBooks(String baseUrl, long userId) throws Exception {
        return http("GET", baseUrl, "/users/" + userId + "/uploaded-books", null, false);
    }

    /**
     * Пакетно удаляет книги с подтверждением паролем пользователя.
     *
     * @param baseUrl  базовый URL API
     * @param userId   id пользователя
     * @param password пароль
     * @param bookIds  массив id книг
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult deleteBooksBatch(
            String baseUrl,
            long userId,
            @NonNull String password,
            @NonNull long[] bookIds
    ) throws Exception {
        JSONObject json = new JSONObject();
        json.put("userId", userId);
        json.put("password", password);
        org.json.JSONArray arr = new org.json.JSONArray();
        for (long id : bookIds) {
            arr.put(id);
        }
        json.put("bookIds", arr);
        return http("POST", baseUrl, "/books/delete-batch", json.toString(), true);
    }

    /**
     * Удаляет одну загруженную пользователем книгу по имени.
     *
     * @param baseUrl  базовый URL API
     * @param userId   id пользователя
     * @param name     название книги
     * @param password пароль для подтверждения
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult deleteUploadedBook(String baseUrl, long userId, String name, String password) throws Exception {
        JSONObject json = new JSONObject();
        json.put("userId", userId);
        json.put("name", name);
        json.put("password", password);
        return http("POST", baseUrl, "/books/delete-uploaded", json.toString(), true);
    }

    /**
     * Отправляет или обновляет оценку книги пользователем.
     *
     * @param baseUrl базовый URL API
     * @param bookId  id книги
     * @param userId  id пользователя
     * @param rating  оценка
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult postBookRating(String baseUrl, long bookId, long userId, int rating) throws Exception {
        JSONObject json = new JSONObject();
        json.put("userId", userId);
        json.put("rating", rating);
        return http("POST", baseUrl, "/books/" + bookId + "/rating", json.toString(), true);
    }

    /**
     * Возвращает закладки пользователя с сервера.
     *
     * @param baseUrl базовый URL API
     * @param userId  id пользователя
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult getUserBookmarks(String baseUrl, long userId) throws Exception {
        return http("GET", baseUrl, "/users/" + userId + "/bookmarks", null, false);
    }

    /**
     * Полностью синхронизирует закладки пользователя телом JSON (PUT).
     *
     * @param baseUrl  базовый URL API
     * @param userId   id пользователя
     * @param jsonBody JSON с закладками
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult syncUserBookmarks(String baseUrl, long userId, @NonNull String jsonBody) throws Exception {
        return http("PUT", baseUrl, "/users/" + userId + "/bookmarks/sync", jsonBody, true);
    }

    /**
     * Добавляет или обновляет одну закладку пользователя.
     *
     * @param baseUrl      базовый URL API
     * @param userId       id пользователя
     * @param bookId       id книги
     * @param lastOpenedAt время последнего открытия или {@code null}
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult upsertUserBookmark(String baseUrl, long userId, long bookId, @Nullable Long lastOpenedAt)
            throws Exception {
        JSONObject json = new JSONObject();
        json.put("bookId", bookId);
        if (lastOpenedAt != null && lastOpenedAt > 0) {
            json.put("lastOpenedAt", lastOpenedAt);
        }
        return http("POST", baseUrl, "/users/" + userId + "/bookmarks", json.toString(), true);
    }

    /**
     * Пакетно удаляет закладки пользователя на сервере.
     *
     * @param baseUrl базовый URL API
     * @param userId  id пользователя
     * @param bookIds массив id книг
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult deleteUserBookmarksBatch(String baseUrl, long userId, @NonNull long[] bookIds)
            throws Exception {
        JSONObject json = new JSONObject();
        org.json.JSONArray arr = new org.json.JSONArray();
        for (long id : bookIds) {
            arr.put(id);
        }
        json.put("bookIds", arr);
        return http("POST", baseUrl, "/users/" + userId + "/bookmarks/delete-batch", json.toString(), true);
    }

    /**
     * Создаёт новую книгу на сервере с метаданными, текстом и обложкой.
     *
     * @param baseUrl          базовый URL API
     * @param name             название
     * @param description      описание
     * @param text             текст книги
     * @param iconDataUrl      обложка в формате data URL
     * @param tags             массив тегов
     * @param uploadedByUserId id загрузившего пользователя
     * @param passwordbook     пароль доступа к книге
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult postBook(
            String baseUrl,
            String name,
            String description,
            @Nullable String text,
            String iconDataUrl,
            org.json.JSONArray tags,
            long uploadedByUserId,
            @NonNull String passwordbook
    ) throws Exception {
        JSONObject json = new JSONObject();
        json.put("name", name);
        json.put("description", description);
        if (text != null) {
            json.put("text", text);
        }
        json.put("iconBase64", iconDataUrl);
        json.put("tags", tags);
        json.put("uploadedByUserId", uploadedByUserId);
        json.put("passwordbook", passwordbook);
        return http("POST", baseUrl, "/books", json.toString(), true,
                BOOK_UPLOAD_CONNECT_MS, BOOK_UPLOAD_READ_MS);
    }

    /**
     * Проверяет пароль доступа к книге на сервере.
     *
     * @param baseUrl      базовый URL API
     * @param bookId       id книги
     * @param passwordbook пароль книги
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult verifyBookPassword(String baseUrl, long bookId, @NonNull String passwordbook)
            throws Exception {
        JSONObject json = new JSONObject();
        json.put("passwordbook", passwordbook);
        return http("POST", baseUrl, "/books/" + bookId + "/verify-password", json.toString(), true);
    }

    /**
     * Обновляет существующую книгу на сервере (PUT).
     *
     * @param baseUrl      базовый URL API
     * @param bookId       id книги
     * @param userId       id пользователя-владельца
     * @param passwordbook пароль книги
     * @param name         новое название
     * @param description  новое описание
     * @param text         новый текст
     * @param iconDataUrl  новая обложка
     * @param tags         новые теги
     * @return результат HTTP-запроса
     * @throws Exception при ошибке сети
     */
    public static HttpResult updateBook(
            String baseUrl,
            long bookId,
            long userId,
            @NonNull String passwordbook,
            String name,
            String description,
            @Nullable String text,
            String iconDataUrl,
            org.json.JSONArray tags
    ) throws Exception {
        JSONObject json = new JSONObject();
        json.put("userId", userId);
        json.put("passwordbook", passwordbook);
        json.put("name", name);
        json.put("description", description);
        if (text != null) {
            json.put("text", text);
        }
        json.put("iconBase64", iconDataUrl);
        json.put("tags", tags);
        HttpResult put = http("PUT", baseUrl, "/books/" + bookId, json.toString(), true,
                BOOK_UPLOAD_CONNECT_MS, BOOK_UPLOAD_READ_MS);
        if (put.statusCode == 200) {
            return put;
        }
        return put;
    }

    /**
     * Выполняет HTTP-запрос с таймаутами по умолчанию.
     *
     * @param method          HTTP-метод
     * @param baseUrl         базовый URL
     * @param path            путь и query
     * @param jsonBody        тело JSON или {@code null}
     * @param jsonContentType устанавливать ли Content-Type application/json
     * @return результат запроса
     * @throws Exception при ошибке сети
     */
    private static HttpResult http(String method, String baseUrl, String path, String jsonBody, boolean jsonContentType)
            throws Exception {
        return http(method, baseUrl, path, jsonBody, jsonContentType, DEFAULT_CONNECT_MS, DEFAULT_READ_MS);
    }

    /**
     * Выполняет HTTP-запрос с заданными таймаутами подключения и чтения.
     *
     * @param method           HTTP-метод
     * @param baseUrl          базовый URL
     * @param path             путь и query
     * @param jsonBody         тело JSON или {@code null}
     * @param jsonContentType  устанавливать ли Content-Type application/json
     * @param connectTimeoutMs таймаут подключения, мс
     * @param readTimeoutMs    таймаут чтения, мс
     * @return результат запроса
     * @throws Exception при ошибке сети
     */
    private static HttpResult http(
            String method,
            String baseUrl,
            String path,
            String jsonBody,
            boolean jsonContentType,
            int connectTimeoutMs,
            int readTimeoutMs
    ) throws Exception {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        URL url = new URL(base + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(connectTimeoutMs);
        c.setReadTimeout(readTimeoutMs);
        if (jsonContentType) {
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        }
        if (jsonBody != null && (method.equals("POST") || method.equals("PUT"))) {
            c.setDoOutput(true);
            c.setDoInput(true);
            byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 512 * 1024) {
                c.setChunkedStreamingMode(256 * 1024);
            } else {
                c.setFixedLengthStreamingMode(bytes.length);
            }
            try (OutputStream os = c.getOutputStream()) {
                os.write(bytes);
            }
        }
        int code = c.getResponseCode();
        InputStream stream = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = readStream(stream);
        c.disconnect();
        return new HttpResult(code, body);
    }

    /**
     * Читает тело HTTP-ответа построчно в одну строку.
     *
     * @param stream поток ответа
     * @return содержимое тела или пустая строка
     * @throws Exception при ошибке чтения
     */
    private static String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
