package com.example.samsungproject.util;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

public final class BookJsonHelper {

    /**
     * Приватный конструктор, запрещающий создание экземпляров утилитного класса.
     */
    private BookJsonHelper() {
    }

    /**
     * Извлекает текст книги из JSON-объекта.
     *
     * @param book JSON-объект книги
     * @return текст книги или пустая строка, если поле отсутствует
     */
    @NonNull
    public static String extractBookText(@NonNull JSONObject book) {
        if (book.isNull("text")) {
            return "";
        }
        String t = book.optString("text", "");
        return t != null ? t.trim() : "";
    }

    /**
     * Форматирует массив тегов книги в строку вида «Теги: …».
     *
     * @param book JSON-объект книги
     * @return отформатированная строка с тегами или «Теги: —», если тегов нет
     */
    @NonNull
    public static String formatTagsLine(@NonNull JSONObject book) {
        JSONArray arr = book.optJSONArray("tags");
        if (arr == null || arr.length() == 0) {
            return "Теги: —";
        }
        StringBuilder sb = new StringBuilder("Теги: ");
        for (int i = 0; i < arr.length(); i++) {
            String t = arr.optString(i, "").trim();
            if (t.isEmpty()) continue;
            if (sb.length() > "Теги: ".length()) {
                sb.append(", ");
            }
            sb.append(t);
        }
        return sb.length() > "Теги: ".length() ? sb.toString() : "Теги: —";
    }
}
