package com.example.samsungproject.domain;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.samsungproject.R;

import java.util.Arrays;

public class User {
    /** Ресурс {@code R.drawable.icon_null} — стандартная иконка пользователя. */
    private static final int icon_null = R.drawable.icon_null;

    /**
     * Запасное имя для отображения (ресурс {@code R.string.name_null}).
     */
    @NonNull
    public static String getName_null(@NonNull Context context) {
        return context.getApplicationContext().getString(R.string.name_null);
    }

    /**
     * Ресурс стандартной иконки пользователя (значение {@link #icon_null}).
     */
    public static int getIcon_null() {
        return icon_null;
    }

    private final long id;
    @Nullable private String email;
    @Nullable private String password;
    @Nullable private String name;
    @Nullable private byte[] icon;

    /**
     * Создаёт объект пользователя с переданными полями.
     */
    public User(long id, @Nullable String email, @Nullable String password, @Nullable String name, @Nullable byte[] icon) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.name = name;
        this.icon = (icon != null) ? Arrays.copyOf(icon, icon.length) : null;
    }

    /**
     * Возвращает идентификатор пользователя.
     */
    public long getId() {
        return id;
    }

    /**
     * Возвращает email пользователя (может быть null).
     */
    @Nullable
    public String getEmail() {
        return email;
    }

    /**
     * Возвращает пароль пользователя (может быть null).
     */
    @Nullable
    public String getPassword() {
        return password;
    }

    /**
     * Возвращает имя пользователя (может быть null).
     */
    @Nullable
    public String getName() {
        return name;
    }

    /**
     * Имя для отображения: своё или значение {@link #getName_null(Context)}.
     */
    @NonNull
    public String getDisplayName(@NonNull Context context) {
        return (name != null && !name.trim().isEmpty()) ? name : getName_null(context);
    }

    /**
     * Возвращает копию  иконки пользователя (может быть null).
     */
    @Nullable
    public byte[] getIcon() {
        return (icon != null) ? Arrays.copyOf(icon, icon.length) : null;
    }

    /**
     * Изменяет email пользователя.
     */
    public void changeEmail(@Nullable String newEmail) {
        this.email = newEmail;
    }

    /**
     * Изменяет пароль пользователя.
     */
    public void changePassword(@Nullable String newPassword) {
        this.password = newPassword;
    }

    /**
     * Изменяет имя пользователя.
     */
    public void changeName(@Nullable String newName) {
        this.name = newName;
    }

    /**
     * Изменяет иконку пользователя (сохраняет защитную копию байтов).
     */
    public void changeIcon(@Nullable byte[] newIcon) {
        this.icon = (newIcon != null) ? Arrays.copyOf(newIcon, newIcon.length) : null;
    }
}
