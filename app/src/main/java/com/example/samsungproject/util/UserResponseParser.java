package com.example.samsungproject.util;

import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

public final class UserResponseParser {

    private UserResponseParser() {
    }

    /**
     * Декодирует аватар из JSON пользователя ({@code iconBase64} как data URL или {@code icon} в base64).
     */
    @Nullable
    public static byte[] decodeUserIcon(@Nullable JSONObject user) {
        if (user == null) {
            return null;
        }
        String raw = user.optString("iconBase64", "");
        if (TextUtils.isEmpty(raw)) {
            raw = user.optString("icon", "");
        }
        if (TextUtils.isEmpty(raw)) {
            return null;
        }
        String b64 = raw.trim();
        int comma = b64.indexOf(',');
        if (b64.startsWith("data:") && comma > 0) {
            b64 = b64.substring(comma + 1).trim();
        }
        try {
            return Base64.decode(b64, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
