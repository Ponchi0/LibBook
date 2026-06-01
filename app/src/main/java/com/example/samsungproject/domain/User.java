package com.example.samsungproject.domain;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.samsungproject.R;

import java.util.Arrays;

public class User {

    private static final int icon_null = R.drawable.icon_null;

    /**
     * Возвращает строку-заглушку для имени неавторизованного пользователя.
     *
     * @param context контекст для доступа к строковым ресурсам
     * @return локализованное имя по умолчанию
     */
    @NonNull
    public static String getName_null(@NonNull Context context) {
        return context.getApplicationContext().getString(R.string.name_null);
    }

    /**
     * Возвращает идентификатор ресурса иконки-заглушки пользователя.
     *
     * @return id drawable-ресурса
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
     * Создаёт объект пользователя с указанными полями.
     *
     * @param id       идентификатор пользователя
     * @param email    адрес электронной почты
     * @param password пароль
     * @param name     отображаемое имя
     * @param icon     байты аватара
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
     *
     * @return идентификатор
     */
    public long getId() {
        return id;
    }

    /**
     * Возвращает адрес электронной почты пользователя.
     *
     * @return email или {@code null}
     */
    @Nullable
    public String getEmail() {
        return email;
    }

    /**
     * Возвращает пароль пользователя.
     *
     * @return пароль или {@code null}
     */
    @Nullable
    public String getPassword() {
        return password;
    }

    /**
     * Возвращает отображаемое имя пользователя.
     *
     * @return имя или {@code null}
     */
    @Nullable
    public String getName() {
        return name;
    }

    /**
     * Возвращает имя для отображения: заданное имя или заглушку по умолчанию.
     *
     * @param context контекст для строковых ресурсов
     * @return имя пользователя для UI
     */
    @NonNull
    public String getDisplayName(@NonNull Context context) {
        return (name != null && !name.trim().isEmpty()) ? name : getName_null(context);
    }

    /**
     * Возвращает копию байтов аватара пользователя.
     *
     * @return байты изображения или {@code null}
     */
    @Nullable
    public byte[] getIcon() {
        return (icon != null) ? Arrays.copyOf(icon, icon.length) : null;
    }

    /**
     * Изменяет адрес электронной почты пользователя.
     *
     * @param newEmail новый email
     */
    public void changeEmail(@Nullable String newEmail) {
        this.email = newEmail;
    }

    /**
     * Изменяет пароль пользователя.
     *
     * @param newPassword новый пароль
     */
    public void changePassword(@Nullable String newPassword) {
        this.password = newPassword;
    }

    /**
     * Изменяет отображаемое имя пользователя.
     *
     * @param newName новое имя
     */
    public void changeName(@Nullable String newName) {
        this.name = newName;
    }

    /**
     * Изменяет аватар пользователя.
     *
     * @param newIcon новые байты изображения
     */
    public void changeIcon(@Nullable byte[] newIcon) {
        this.icon = (newIcon != null) ? Arrays.copyOf(newIcon, newIcon.length) : null;
    }
}
