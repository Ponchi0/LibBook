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
     * Создаёт объект книги с указанными полями.
     *
     * @param id          идентификатор книги
     * @param password    пароль доступа к книге
     * @param name        название
     * @param icon        байты обложки
     * @param description описание
     * @param tags        список тегов
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
     *
     * @return идентификатор
     */
    public long getId() {
        return id;
    }

    /**
     * Возвращает пароль доступа к книге.
     *
     * @return пароль или {@code null}
     */
    @Nullable
    public String getPassword() {
        return password;
    }

    /**
     * Возвращает название книги.
     *
     * @return название или {@code null}
     */
    @Nullable
    public String getName() {
        return name;
    }

    /**
     * Возвращает копию байтов обложки книги.
     *
     * @return байты изображения или {@code null}
     */
    @Nullable
    public byte[] getIcon() {
        return (icon != null) ? Arrays.copyOf(icon, icon.length) : null;
    }

    /**
     * Возвращает описание книги.
     *
     * @return описание или {@code null}
     */
    @Nullable
    public String getDescription() {
        return description;
    }

    /**
     * Возвращает копию массива тегов книги.
     *
     * @return массив тегов
     */
    public String[] getTags() {
        return Arrays.copyOf(tags, tags.length);
    }

    /**
     * Изменяет название книги.
     *
     * @param newName новое название
     */
    public void changeName(@Nullable String newName) {
        this.name = newName;
    }

    /**
     * Изменяет обложку книги.
     *
     * @param newIcon новые байты изображения
     */
    public void changeIcon(@Nullable byte[] newIcon) {
        this.icon = (newIcon != null) ? Arrays.copyOf(newIcon, newIcon.length) : null;
    }

    /**
     * Изменяет описание книги.
     *
     * @param newDescription новое описание
     */
    public void changeDescription(@Nullable String newDescription) {
        this.description = newDescription;
    }

    /**
     * Добавляет и удаляет теги из текущего списка.
     *
     * @param tagsToAdd    теги для добавления
     * @param tagsToRemove теги для удаления
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
     * Полностью заменяет список тегов книги.
     *
     * @param newTags новый список тегов
     */
    public void changeTags(@Nullable List<String> newTags) {
        this.tags = normalizeTags(newTags);
    }

    /**
     * Нормализует список тегов: обрезает пробелы, убирает пустые и дубликаты.
     *
     * @param tags исходный список тегов
     * @return массив нормализованных тегов
     */
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
