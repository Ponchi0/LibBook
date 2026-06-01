package com.example.samsungproject.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.samsungproject.LocalDB.BookDatabaseHelper;
import com.example.samsungproject.net.ApiConfig;
import com.example.samsungproject.net.LibBookApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


public final class BookmarkSyncHelper {

    private static final String PREFS_NAME = "bookmark_sync_prefs";
    private static final String KEY_PENDING = "pending_push_ids";

    private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /**
     * Приватный конструктор, запрещающий создание экземпляров утилитного класса.
     */
    private BookmarkSyncHelper() {
    }

    /**
     * Асинхронно синхронизирует закладки с сервером, если пользователь авторизован.
     *
     * @param context контекст приложения
     * @param onDone  колбэк по завершении (на главном потоке) или {@code null}
     */
    public static void syncAsync(@NonNull Context context, @Nullable Runnable onDone) {
        if (!SessionHelper.isLoggedIn(context)) {
            if (onDone != null) {
                MAIN.post(onDone);
            }
            return;
        }
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                syncNow(app);
            } catch (Exception ignored) {
            } finally {
                if (onDone != null) {
                    MAIN.post(onDone);
                }
            }
        });
    }

    /**
     * Помечает закладку как ожидающую отправки и асинхронно отправляет её на сервер.
     *
     * @param context       контекст приложения
     * @param bookId        идентификатор книги
     * @param lastOpenedAt  время последнего открытия или {@code null}
     */
    public static void markPendingAndPush(@NonNull Context context, long bookId, @Nullable Long lastOpenedAt) {
        if (!SessionHelper.isLoggedIn(context) || bookId <= 0) {
            return;
        }
        markPending(context, bookId);
        pushBookmarkAsync(context, bookId, lastOpenedAt);
    }

    /**
     * Асинхронно отправляет или обновляет закладку на сервере.
     *
     * @param context       контекст приложения
     * @param bookId        идентификатор книги
     * @param lastOpenedAt  время последнего открытия или {@code null}
     */
    public static void pushBookmarkAsync(@NonNull Context context, long bookId, @Nullable Long lastOpenedAt) {
        if (!SessionHelper.isLoggedIn(context) || bookId <= 0) {
            return;
        }
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                String baseUrl = ApiConfig.baseUrl(app);
                long userId = LibBookApiClient.resolveServerUserId(app, baseUrl);
                if (userId <= 0) {
                    return;
                }
                LibBookApiClient.HttpResult result =
                        LibBookApiClient.upsertUserBookmark(baseUrl, userId, bookId, lastOpenedAt);
                if (result.statusCode == 200) {
                    clearPending(app, bookId);
                }
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Асинхронно обновляет время последнего открытия закладки на сервере.
     *
     * @param context       контекст приложения
     * @param bookId        идентификатор книги
     * @param lastOpenedAt  время последнего открытия
     */
    public static void pushTouchAsync(@NonNull Context context, long bookId, long lastOpenedAt) {
        pushBookmarkAsync(context, bookId, lastOpenedAt);
    }

    /**
     * Асинхронно удаляет указанные закладки на сервере и снимает пометку ожидания отправки.
     *
     * @param context контекст приложения
     * @param bookIds массив идентификаторов книг для удаления
     */
    public static void deleteBookmarksAsync(@NonNull Context context, @NonNull long[] bookIds) {
        if (!SessionHelper.isLoggedIn(context) || bookIds.length == 0) {
            return;
        }
        Context app = context.getApplicationContext();
        for (long id : bookIds) {
            clearPending(app, id);
        }
        EXECUTOR.execute(() -> {
            try {
                String baseUrl = ApiConfig.baseUrl(app);
                long userId = LibBookApiClient.resolveServerUserId(app, baseUrl);
                if (userId <= 0) {
                    return;
                }
                LibBookApiClient.deleteUserBookmarksBatch(baseUrl, userId, bookIds);
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Синхронизирует закладки с сервером: отправляет ожидающие и загружает актуальный список.
     *
     * @param context контекст приложения
     * @return {@code true}, если синхронизация прошла успешно
     * @throws Exception при ошибке сети или разбора ответа
     */
    public static boolean syncNow(@NonNull Context context) throws Exception {
        if (!SessionHelper.isLoggedIn(context)) {
            return false;
        }
        Context app = context.getApplicationContext();
        String baseUrl = ApiConfig.baseUrl(app);
        long userId = LibBookApiClient.resolveServerUserId(app, baseUrl);
        if (userId <= 0) {
            return false;
        }

        pushPendingBookmarks(app, baseUrl, userId);

        LibBookApiClient.HttpResult result = LibBookApiClient.getUserBookmarks(baseUrl, userId);
        if (result.statusCode != 200) {
            return false;
        }

        JSONObject response = new JSONObject(result.body != null ? result.body : "{}");
        JSONArray serverBookmarks = response.optJSONArray("bookmarks");
        if (serverBookmarks == null) {
            return false;
        }

        Set<Long> serverIds = new HashSet<>();
        Set<Long> keepIds = new HashSet<>(getPendingIds(app));
        for (int i = 0; i < serverBookmarks.length(); i++) {
            JSONObject item = serverBookmarks.optJSONObject(i);
            if (item == null) {
                continue;
            }
            long bookId = item.optLong("bookId", -1L);
            if (bookId > 0) {
                serverIds.add(bookId);
                keepIds.add(bookId);
            }
        }

        try (BookDatabaseHelper db = new BookDatabaseHelper(app)) {
            applyServerBookmarkItems(db, serverBookmarks);
            db.removeBookmarksExcept(keepIds);
        }
        return true;
    }

    /**
     * Отправляет на сервер все закладки, помеченные как ожидающие синхронизации.
     *
     * @param app     контекст приложения
     * @param baseUrl базовый URL API
     * @param userId  идентификатор пользователя на сервере
     * @throws Exception при ошибке сети или БД
     */
    private static void pushPendingBookmarks(@NonNull Context app, @NonNull String baseUrl, long userId)
            throws Exception {
        Set<Long> pending = new HashSet<>(getPendingIds(app));
        if (pending.isEmpty()) {
            return;
        }
        try (BookDatabaseHelper db = new BookDatabaseHelper(app)) {
            for (Long bookId : pending) {
                if (bookId == null || bookId <= 0) {
                    continue;
                }
                BookDatabaseHelper.BookRow local = db.getBook(bookId);
                Long lastOpened = local != null ? local.lastOpenedAt : null;
                LibBookApiClient.HttpResult push =
                        LibBookApiClient.upsertUserBookmark(baseUrl, userId, bookId, lastOpened);
                if (push.statusCode == 200) {
                    clearPending(app, bookId);
                }
            }
        }
    }

    /**
     * Применяет элементы закладок с сервера к локальной базе данных.
     *
     * @param db     помощник локальной БД книг
     * @param merged JSON-массив закладок с сервера
     * @throws Exception при ошибке записи в БД
     */
    private static void applyServerBookmarkItems(@NonNull BookDatabaseHelper db, @NonNull JSONArray merged)
            throws Exception {
        for (int i = 0; i < merged.length(); i++) {
            JSONObject item = merged.optJSONObject(i);
            if (item == null) {
                continue;
            }
            long bookId = item.optLong("bookId", -1L);
            if (bookId <= 0) {
                continue;
            }
            String name = item.isNull("name") ? null : item.optString("name", null);
            String text = item.isNull("text") ? null : item.optString("text", null);
            byte[] icon = decodeIcon(item);
            Long lastOpened = null;
            if (item.has("lastOpenedAt") && !item.isNull("lastOpenedAt")) {
                lastOpened = item.optLong("lastOpenedAt", 0L);
                if (lastOpened <= 0) {
                    lastOpened = null;
                }
            }
            db.upsertBookmarkFromSync(bookId, name, icon, text, lastOpened);
        }
    }

    /**
     * Помечает закладку как ожидающую отправки на сервер.
     *
     * @param context контекст приложения
     * @param bookId  идентификатор книги
     */
    private static void markPending(@NonNull Context context, long bookId) {
        Set<String> ids = new HashSet<>(getPendingPrefs(context).getStringSet(KEY_PENDING, new HashSet<>()));
        ids.add(String.valueOf(bookId));
        getPendingPrefs(context).edit().putStringSet(KEY_PENDING, ids).apply();
    }

    /**
     * Снимает пометку ожидания отправки с закладки после успешной синхронизации.
     *
     * @param context контекст приложения
     * @param bookId  идентификатор книги
     */
    private static void clearPending(@NonNull Context context, long bookId) {
        Set<String> ids = new HashSet<>(getPendingPrefs(context).getStringSet(KEY_PENDING, new HashSet<>()));
        if (ids.remove(String.valueOf(bookId))) {
            getPendingPrefs(context).edit().putStringSet(KEY_PENDING, ids).apply();
        }
    }

    /**
     * Возвращает множество идентификаторов книг, ожидающих отправки на сервер.
     *
     * @param context контекст приложения
     * @return множество идентификаторов закладок
     */
    @NonNull
    private static Set<Long> getPendingIds(@NonNull Context context) {
        Set<String> raw = getPendingPrefs(context).getStringSet(KEY_PENDING, new HashSet<>());
        Set<Long> out = new HashSet<>();
        for (String s : raw) {
            try {
                long id = Long.parseLong(s);
                if (id > 0) {
                    out.add(id);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    /**
     * Возвращает SharedPreferences для хранения списка ожидающих синхронизации закладок.
     *
     * @param context контекст приложения
     * @return объект настроек
     */
    @NonNull
    private static SharedPreferences getPendingPrefs(@NonNull Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Декодирует иконку книги из JSON-элемента закладки сервера.
     *
     * @param item JSON-объект закладки
     * @return байты иконки или {@code null}
     */
    @Nullable
    private static byte[] decodeIcon(@NonNull JSONObject item) {
        JSONObject wrapper = new JSONObject();
        try {
            wrapper.put("iconBase64", item.optString("iconBase64", ""));
            return UserResponseParser.decodeUserIcon(wrapper);
        } catch (Exception e) {
            return null;
        }
    }
}
