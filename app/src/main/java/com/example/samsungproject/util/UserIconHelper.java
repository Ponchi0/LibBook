package com.example.samsungproject.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.samsungproject.LocalDB.UserDatabaseHelper;
import com.example.samsungproject.domain.User;

public final class UserIconHelper {

    private static final String PREFS_NAME = "user_prefs";
    private static final String KEY_SERVER_USER_ID = "server_user_id";

    /**
     * Приватный конструктор, запрещающий создание экземпляров утилитного класса.
     */
    private UserIconHelper() {
    }

    /**
     * Загружает иконку текущего авторизованного пользователя из локальной БД и отображает её.
     *
     * @param imageView ImageView для отображения иконки
     */
    public static void bindLoggedInUserIcon(@NonNull ImageView imageView) {
        Context ctx = imageView.getContext();
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long uid = prefs.getLong(KEY_SERVER_USER_ID, -1L);
        byte[] icon = null;
        if (uid >= 0) {
            try {
                UserDatabaseHelper.UserRow row = new UserDatabaseHelper(ctx).getUser(uid);
                if (row != null) {
                    icon = row.icon;
                }
            } catch (Exception ignored) {

            }
        }
        bindIcon(imageView, icon);
    }

    /**
     * Отображает иконку пользователя из байтов или заглушку по умолчанию.
     *
     * @param imageView  ImageView для отображения
     * @param iconBytes  байты изображения или {@code null}
     */
    public static void bindIcon(@NonNull ImageView imageView, @Nullable byte[] iconBytes) {
        if (iconBytes != null && iconBytes.length > 0) {
            Bitmap bm = BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.length);
            if (bm != null) {
                imageView.setImageBitmap(bm);
                return;
            }
        }
        imageView.setImageResource(User.getIcon_null());
    }
}
