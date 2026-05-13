package com.example.samsungproject.net;

import org.json.JSONObject;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class SamsungApiClient {

    public static final String PURPOSE_PASSWORD_RESET = "password_reset";

    private SamsungApiClient() {
    }

    public static final class HttpResult {
        public final int statusCode;
        public final String body;

        public HttpResult(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body != null ? body : "";
        }
    }

    public static HttpResult getUserByEmail(String baseUrl, String email) throws Exception {
        String q = "/users/by-email?email=" + java.net.URLEncoder.encode(email, StandardCharsets.UTF_8.name());
        return http("GET", baseUrl, q, null, false);
    }

    /**
     * Creates a new user on the server. Expects JSON body {@code {email,name,password}} and response
     * {@code {id,email,name,...}} with HTTP 200 or 201.
     */
    public static HttpResult postRegisterUser(String baseUrl, String email, String name, String password) throws Exception {
        JSONObject json = new JSONObject();
        json.put("email", email);
        json.put("name", name);
        json.put("password", password);
        return http("POST", baseUrl, "/users", json.toString(), true);
    }

    public static HttpResult requestVerification(String baseUrl, String email, String purpose) throws Exception {
        JSONObject json = new JSONObject();
        json.put("email", email);
        json.put("purpose", purpose);
        return http("POST", baseUrl, "/auth/verification/request", json.toString(), true);
    }

    public static HttpResult verifyCode(String baseUrl, String email, String purpose, String code) throws Exception {
        JSONObject json = new JSONObject();
        json.put("email", email);
        json.put("purpose", purpose);
        json.put("code", code);
        return http("POST", baseUrl, "/auth/verification/verify", json.toString(), true);
    }

    public static HttpResult putUserPassword(String baseUrl, long userId, String newPassword) throws Exception {
        JSONObject json = new JSONObject();
        json.put("password", newPassword);
        return http("PUT", baseUrl, "/users/" + userId, json.toString(), true);
    }

    public static HttpResult putUserName(String baseUrl, long userId, String name) throws Exception {
        JSONObject json = new JSONObject();
        json.put("name", name);
        return http("PUT", baseUrl, "/users/" + userId, json.toString(), true);
    }

    public static HttpResult putUserIcon(String baseUrl, long userId, String mimeType, byte[] imageBytes) throws Exception {
        String mime = (mimeType != null && !mimeType.isEmpty()) ? mimeType : "image/jpeg";
        String b64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
        String dataUrl = "data:" + mime + ";base64," + b64;
        JSONObject json = new JSONObject();
        json.put("iconBase64", dataUrl);
        return http("PUT", baseUrl, "/users/" + userId, json.toString(), true);
    }

    public static HttpResult verifyUserPassword(String baseUrl, long userId, String password) throws Exception {
        JSONObject json = new JSONObject();
        json.put("password", password);
        return http("POST", baseUrl, "/users/" + userId + "/verify-password", json.toString(), true);
    }

    public static HttpResult getBooks(String baseUrl) throws Exception {
        return http("GET", baseUrl, "/books", null, false);
    }

    public static HttpResult getBook(String baseUrl, long bookId) throws Exception {
        return http("GET", baseUrl, "/books/" + bookId, null, false);
    }

    public static HttpResult postBook(
            String baseUrl,
            String name,
            String description,
            String iconDataUrl,
            org.json.JSONArray tags,
            @Nullable String text,
            @Nullable String textFileBase64,
            @Nullable String textFileMime
    ) throws Exception {
        JSONObject json = new JSONObject();
        json.put("name", name);
        json.put("description", description);
        json.put("icon", iconDataUrl);
        json.put("tags", tags);
        if (text != null) {
            json.put("text", text);
        }
        if (textFileBase64 != null) {
            json.put("textFileBase64", textFileBase64);
        }
        if (textFileMime != null) {
            json.put("textFileMime", textFileMime);
        }
        return http("POST", baseUrl, "/books", json.toString(), true);
    }

    private static HttpResult http(String method, String baseUrl, String path, String jsonBody, boolean jsonContentType) throws Exception {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        URL url = new URL(base + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(20000);
        c.setReadTimeout(20000);
        if (jsonContentType) {
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        }
        if (jsonBody != null && (method.equals("POST") || method.equals("PUT"))) {
            c.setDoOutput(true);
            byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream os = c.getOutputStream()) {
                os.write(bytes);
            }
        }
        int code = c.getResponseCode();
        InputStream stream = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = readStream(stream);
        c.disconnect();
        return new HttpResult(code, body);
    }

    private static String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
