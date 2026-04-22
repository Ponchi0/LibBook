package com.example.samsungproject.domain;

import androidx.annotation.Nullable;

import java.util.Arrays;

public class User {
    private final long id;
    @Nullable private String email;
    @Nullable private String password;
    @Nullable private String name;
    @Nullable private byte[] icon;

    public User(long id, @Nullable String email, @Nullable String password, @Nullable String name, @Nullable byte[] icon) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.name = name;
        this.icon = (icon != null) ? Arrays.copyOf(icon, icon.length) : null;
    }

    public long getId() {
        return id;
    }

    @Nullable
    public String getEmail() {
        return email;
    }

    @Nullable
    public String getPassword() {
        return password;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public byte[] getIcon() {
        return (icon != null) ? Arrays.copyOf(icon, icon.length) : null;
    }

    public void changeEmail(@Nullable String newEmail) {
        this.email = newEmail;
    }

    public void changePassword(@Nullable String newPassword) {
        this.password = newPassword;
    }

    public void changeName(@Nullable String newName) {
        this.name = newName;
    }

    public void changeIcon(@Nullable byte[] newIcon) {
        this.icon = (newIcon != null) ? Arrays.copyOf(newIcon, newIcon.length) : null;
    }
}
