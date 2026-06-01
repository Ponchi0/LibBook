package com.example.samsungproject.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.samsungproject.R;

public final class SessionHelper {

    public static final String PREFS_NAME = "user_prefs";
    public static final String KEY_SERVER_USER_ID = "server_user_id";

    /**
     * Приватный конструктор, запрещающий создание экземпляров утилитного класса.
     */
    private SessionHelper() {
    }

    /**
     * Проверяет, выполнен ли вход пользователя (сохранён идентификатор на сервере).
     *
     * @param context контекст приложения
     * @return {@code true}, если пользователь авторизован
     */
    public static boolean isLoggedIn(@NonNull Context context) {
        return getServerUserId(context) >= 0L;
    }

    /**
     * Возвращает идентификатор пользователя на сервере из локальных настроек.
     *
     * @param context контекст приложения
     * @return идентификатор пользователя или {@code -1}, если не авторизован
     */
    public static long getServerUserId(@NonNull Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_SERVER_USER_ID, -1L);
    }

    /**
     * Показывает короткое уведомление о необходимости войти в аккаунт.
     *
     * @param context контекст для отображения Toast
     */
    public static void showLoginRequiredToast(@NonNull Context context) {
        Toast.makeText(context, R.string.login_required, Toast.LENGTH_SHORT).show();
    }

    /**
     * Показывает уведомление о необходимости войти для выставления оценки.
     *
     * @param context контекст для отображения Toast
     */
    public static void showRatingLoginRequiredToast(@NonNull Context context) {
        Toast.makeText(context, R.string.login_required_rating, Toast.LENGTH_SHORT).show();
    }
}
