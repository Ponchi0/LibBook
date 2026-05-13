package com.example.samsungproject.util;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

public final class BookJsonHelper {

    private BookJsonHelper() {
    }

    /**
     * Строка для {@code mbptvTags}: префикс «Теги:» и список через запятую.
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
