package com.example.samsungproject.domain;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class Book {
    private final long id;
    @Nullable private String password;
    @Nullable private String name;
    @Nullable private byte[] icon;
    @Nullable private String description;
    private String[] tags;

    /**
     * Создаёт объект книги с переданными полями.
     */
    public Book(
            long id,
            @Nullable String password,
            @Nullable String name,
            @Nullable byte[] icon,
            @Nullable String description,
            @Nullable List<String> tags
    ) {
        this.id = id;
        this.password = password;
        this.name = name;
        this.icon = (icon != null) ? Arrays.copyOf(icon, icon.length) : null;
        this.description = description;
        this.tags = normalizeTags(tags);
    }

    /**
     * Возвращает идентификатор книги.
     */
    public long getId() {
        return id;
    }

    /**
     * Возвращает пароль книги (может быть null).
     */
    @Nullable
    public String getPassword() {
        return password;
    }

    /**
     * Возвращает название книги (может быть null).
     */
    @Nullable
    public String getName() {
        return name;
    }

    /**
     * Возвращает копию байтов иконки книги (может быть null).
     */
    @Nullable
    public byte[] getIcon() {
        return (icon != null) ? Arrays.copyOf(icon, icon.length) : null;
    }

    /**
     * Возвращает описание книги (может быть null).
     */
    @Nullable
    public String getDescription() {
        return description;
    }

    /**
     * Возвращает копию массива названий тегов.
     */
    public String[] getTags() {
        return Arrays.copyOf(tags, tags.length);
    }

    /**
     * Изменяет название книги.
     */
    public void changeName(@Nullable String newName) {
        this.name = newName;
    }

    /**
     * Изменяет иконку книги (сохраняет защитную копию байтов).
     */
    public void changeIcon(@Nullable byte[] newIcon) {
        this.icon = (newIcon != null) ? Arrays.copyOf(newIcon, newIcon.length) : null;
    }

    /**
     * Изменяет описание книги.
     */
    public void changeDescription(@Nullable String newDescription) {
        this.description = newDescription;
    }

    /**
     * Обновляет теги: удаляет и добавляет элементы за один вызов.
     */
    public void changeTags(@Nullable List<String> tagsToAdd, @Nullable List<String> tagsToRemove) {
        ArrayList<String> cur = new ArrayList<>(Arrays.asList(this.tags));

        if (tagsToRemove != null) {
            for (String tag : tagsToRemove) {
                if (tag == null) continue;
                String t = tag.trim();
                if (t.isEmpty()) continue;
                while (cur.remove(t)) {
                }
            }
        }

        if (tagsToAdd != null) {
            for (String tag : tagsToAdd) {
                if (tag == null) continue;
                String t = tag.trim();
                if (t.isEmpty() || cur.contains(t)) continue;
                cur.add(t);
            }
        }

        this.tags = normalizeTags(cur);
    }

    /**
     * Полностью заменяет список тегов.
     */
    public void changeTags(@Nullable List<String> newTags) {
        this.tags = normalizeTags(newTags);
    }

    private static String[] normalizeTags(@Nullable List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new String[0];
        }
        ArrayList<String> out = new ArrayList<>();
        for (String t : tags) {
            if (t == null) continue;
            String s = t.trim();
            if (s.isEmpty() || out.contains(s)) continue;
            out.add(s);
        }
        return out.toArray(new String[0]);
    }
}
