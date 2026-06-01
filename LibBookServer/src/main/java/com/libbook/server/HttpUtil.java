package com.libbook.server;

import fi.iki.elonen.NanoHTTPD;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class HttpUtil {

    /** Запрещает создание экземпляров утилитного класса. */
    private HttpUtil() {
    }

    /** Формирует успешный HTTP-ответ со статусом 200 и JSON-телом. */
    static NanoHTTPD.Response okJson(Object json) {
        return json(200, json);
    }

    /** Формирует HTTP-ответ с указанным статусом и JSON-телом, добавляя заголовки CORS. */
    static NanoHTTPD.Response json(int status, Object json) {
        String body;
        if (json instanceof JSONObject obj) {
            body = obj.toString();
        } else if (json instanceof JSONArray arr) {
            body = arr.toString();
        } else {
            body = String.valueOf(json);
        }
        NanoHTTPD.Response response = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.lookup(status),
                "application/json; charset=utf-8",
                body
        );
        addCors(response);
        return response;
    }

    /** Возвращает JSON-ответ об ошибке с кодом в поле {@code error}. */
    static NanoHTTPD.Response error(int status, String code) {
        return json(status, new JSONObject().put("error", code));
    }

    /** Возвращает пустой ответ со статусом 204 No Content. */
    static NanoHTTPD.Response noContent() {
        NanoHTTPD.Response response = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.NO_CONTENT, "text/plain", ""
        );
        addCors(response);
        return response;
    }

    /** Обрабатывает preflight-запрос CORS (OPTIONS) пустым ответом 204. */
    static NanoHTTPD.Response corsPreflight() {
        NanoHTTPD.Response response = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.NO_CONTENT, "text/plain", ""
        );
        addCors(response);
        return response;
    }

    /** Добавляет к ответу заголовки для поддержки кросс-доменных запросов. */
    static void addCors(NanoHTTPD.Response response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type");
    }

    /** Читает тело HTTP-запроса из потока сессии в строку UTF-8. */
    static String readBody(NanoHTTPD.IHTTPSession session) throws Exception {
        int contentLength = contentLength(session);
        if (contentLength > 0) {
            InputStream in = session.getInputStream();
            if (in != null) {
                byte[] buf = new byte[contentLength];
                int offset = 0;
                while (offset < contentLength) {
                    int read = in.read(buf, offset, contentLength - offset);
                    if (read < 0) {
                        break;
                    }
                    offset += read;
                }
                if (offset > 0) {
                    return new String(buf, 0, offset, StandardCharsets.UTF_8);
                }
            }
        }
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        String raw = files.get("postData");
        if (raw != null && !raw.isEmpty()) {
            return raw;
        }
        InputStream in = session.getInputStream();
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) > 0) {
            buf.write(chunk, 0, n);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    /** Извлекает значение заголовка Content-Length из сессии запроса. */
    private static int contentLength(NanoHTTPD.IHTTPSession session) {
        String cl = session.getHeaders().get("content-length");
        if (cl == null) {
            cl = session.getHeaders().get("Content-Length");
        }
        if (cl == null || cl.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(cl.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Читает тело запроса и разбирает его как JSON-объект; пустое тело даёт пустой объект. */
    static JSONObject readJson(NanoHTTPD.IHTTPSession session) throws Exception {
        String body = readBody(session);
        if (body == null || body.isBlank()) {
            return new JSONObject();
        }
        return new JSONObject(body);
    }

    /** Разбирает query-параметры из строки URI после символа «?». */
    static Map<String, String> queryParams(String uri) {
        Map<String, String> out = new HashMap<>();
        if (uri == null || uri.isBlank()) {
            return out;
        }
        int q = uri.indexOf('?');
        if (q < 0) {
            return out;
        }
        parseQueryString(uri.substring(q + 1), out);
        return out;
    }

    /**
     * Собирает query-параметры из URI сессии и из {@code getParameters()} NanoHTTPD.
     * NanoHTTPD кладёт query в getParameters(), а getUri() часто без «?email=…».
     */
    static Map<String, String> queryParams(NanoHTTPD.IHTTPSession session) {
        Map<String, String> out = new HashMap<>(queryParams(session.getUri()));
        Map<String, List<String>> parms = session.getParameters();
        if (parms != null) {
            for (Map.Entry<String, List<String>> e : parms.entrySet()) {
                if (e.getValue() != null && !e.getValue().isEmpty()) {
                    String v = e.getValue().get(0);
                    if (v != null) {
                        out.put(e.getKey(), v);
                    }
                }
            }
        }
        return out;
    }

    /** Разбирает строку query-параметров формата {@code key=value&…} в карту. */
    private static void parseQueryString(String qs, Map<String, String> out) {
        for (String part : qs.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                out.put(urlDecode(part.substring(0, eq)), urlDecode(part.substring(eq + 1)));
            } else if (!part.isEmpty()) {
                out.put(urlDecode(part), "");
            }
        }
    }

    /** Возвращает путь URI без query-строки и завершающего слэша. */
    static String pathOnly(String uri) {
        int q = uri.indexOf('?');
        String p = q >= 0 ? uri.substring(0, q) : uri;
        if (p.endsWith("/") && p.length() > 1) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    /** Декодирует URL-кодированную строку в UTF-8. */
    private static String urlDecode(String s) {
        return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    /** Преобразует JSON-строку тегов в массив; при ошибке возвращает пустой массив. */
    static JSONArray tagsToJson(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return new JSONArray();
        }
        try {
            return new JSONArray(tagsJson);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /** Преобразует JSON-массив тегов в строку для хранения в БД. */
    static String tagsFromJson(org.json.JSONArray tags) {
        if (tags == null) {
            return "[]";
        }
        return tags.toString();
    }

    /** Собирает JSON-объект книги с рейтингами и опциональной оценкой текущего пользователя. */
    static JSONObject bookJson(
            long id,
            String name,
            String description,
            String icon,
            String tagsJson,
            String text,
            Double avgRating,
            Integer ratingsCount,
            Integer myRating
    ) {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name != null ? name : JSONObject.NULL);
        o.put("description", description != null ? description : JSONObject.NULL);
        putIcon(o, icon);
        o.put("tags", tagsToJson(tagsJson));
        o.put("text", text != null ? text : JSONObject.NULL);
        if (avgRating != null && !avgRating.isNaN()) {
            o.put("avgRating", avgRating);
        }
        if (ratingsCount != null) {
            o.put("ratingsCount", ratingsCount);
        }
        if (myRating != null && myRating >= 1) {
            o.put("myRating", myRating);
        }
        return o;
    }

    /** Собирает JSON-объект пользователя без пароля. */
    static JSONObject userJson(long id, String email, String name, String icon) {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("email", email != null ? email : JSONObject.NULL);
        o.put("name", name != null ? name : JSONObject.NULL);
        putIcon(o, icon);
        return o;
    }

    /** Записывает в JSON поля {@code icon} и {@code iconBase64} или null, если иконка пуста. */
    static void putIcon(JSONObject o, String icon) {
        if (icon == null || icon.isBlank()) {
            o.put("icon", JSONObject.NULL);
            o.put("iconBase64", JSONObject.NULL);
            return;
        }
        o.put("icon", icon);
        o.put("iconBase64", icon);
    }

    /** Разбирает строку как long; при ошибке возвращает значение по умолчанию. */
    static long parseLong(String s, long def) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Разбирает строку как int; при ошибке возвращает значение по умолчанию. */
    static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Извлекает из JSON-массива список положительных идентификаторов. */
    static List<Long> longArray(org.json.JSONArray arr) {
        java.util.ArrayList<Long> out = new java.util.ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (int i = 0; i < arr.length(); i++) {
            long v = arr.optLong(i, -1);
            if (v > 0) {
                out.add(v);
            }
        }
        return out;
    }
}

