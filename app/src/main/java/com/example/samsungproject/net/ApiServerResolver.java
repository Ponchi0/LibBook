package com.example.samsungproject.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.samsungproject.R;

import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;


final class ApiServerResolver {

    private static final String PREFS = "api_config";
    private static final String KEY_RESOLVED_URL = "resolved_base_url";
    private static final String KEY_LAN_HOST = "last_lan_host";

    private static final int HEALTH_CONNECT_MS = 1500;
    private static final int HEALTH_READ_MS = 1500;

    private static volatile String memoryCache;
    private static final ExecutorService RESOLVE_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RESOLVE_IN_PROGRESS = new AtomicBoolean(false);
    private static volatile long lastResolveAtMs;

    /**
     * Приватный конструктор, запрещающий создание экземпляров утилитного класса.
     */
    private ApiServerResolver() {
    }

    /**
     * Возвращает кэшированный или облачный базовый URL API без сетевого запроса.
     *
     * @param context контекст приложения
     * @return базовый URL сервера
     */
    @NonNull
    static String cachedOrDefault(@NonNull Context context) {
        String cached = memoryCache;
        if (!TextUtils.isEmpty(cached)) {
            return cached;
        }
        SharedPreferences prefs = prefs(context);
        cached = prefs.getString(KEY_RESOLVED_URL, null);
        if (!TextUtils.isEmpty(cached)) {
            memoryCache = cached;
            return cached;
        }
        String cloud = cloudUrl(context);
        memoryCache = cloud;
        return cloud;
    }

    /**
     * Асинхронно перепроверяет доступность серверов и обновляет кэш URL (не чаще раза в 15 с).
     *
     * @param context контекст приложения
     */
    static void refreshAsync(@NonNull Context context) {
        long now = System.currentTimeMillis();
        if (now - lastResolveAtMs < 15_000L) {
            return;
        }
        if (!RESOLVE_IN_PROGRESS.compareAndSet(false, true)) {
            return;
        }
        Context app = context.getApplicationContext();
        RESOLVE_EXECUTOR.execute(() -> {
            try {
                String resolved = resolveSafely(app);
                memoryCache = resolved;
                prefs(app).edit().putString(KEY_RESOLVED_URL, resolved).apply();
                lastResolveAtMs = System.currentTimeMillis();
            } finally {
                RESOLVE_IN_PROGRESS.set(false);
            }
        });
    }

    /**
     * Безопасно определяет URL сервера, при ошибке возвращает облачный адрес.
     *
     * @param context контекст приложения
     * @return рабочий базовый URL
     */
    @NonNull
    private static String resolveSafely(@NonNull Context context) {
        try {
            return resolve(context);
        } catch (Exception ignored) {
            return cloudUrl(context);
        }
    }

    /**
     * Выбирает лучший доступный URL: облако, кэш, последний LAN или быстрый поиск в локальной сети.
     *
     * @param context контекст приложения
     * @return базовый URL доступного сервера
     */
    @NonNull
    private static String resolve(@NonNull Context context) {
        String cloud = cloudUrl(context);
        if (healthOk(cloud)) {
            return cloud;
        }

        String cached = prefs(context).getString(KEY_RESOLVED_URL, null);
        if (!TextUtils.isEmpty(cached) && healthOk(cached)) {
            return cached;
        }

        String lastLan = prefs(context).getString(KEY_LAN_HOST, null);
        if (!TextUtils.isEmpty(lastLan) && healthOk(lastLan)) {
            return lastLan;
        }

        if (isOnWifi(context)) {
            String lan = discoverLanServerQuick(context);
            if (!TextUtils.isEmpty(lan)) {
                prefs(context).edit().putString(KEY_LAN_HOST, lan).apply();
                return lan;
            }
        }

        return cloud;
    }

    /**
     * Возвращает нормализованный облачный URL из строковых ресурсов.
     *
     * @param context контекст приложения
     * @return URL облачного сервера
     */
    @NonNull
    private static String cloudUrl(@NonNull Context context) {
        return normalize(context.getString(R.string.api_base_url_cloud));
    }

    /**
     * Быстро ищет сервер LibBook в локальной Wi‑Fi-сети по типичным адресам.
     *
     * @param context контекст приложения
     * @return URL найденного сервера или {@code null}
     */
    @Nullable
    private static String discoverLanServerQuick(@NonNull Context context) {
        String phoneIp = wifiIpv4(context);
        if (phoneIp == null || !isPrivateIpv4(phoneIp)) {
            return null;
        }
        int lastDot = phoneIp.lastIndexOf('.');
        if (lastDot <= 0) {
            return null;
        }
        String prefix = phoneIp.substring(0, lastDot + 1);
        int ownOctet = parseOctet(phoneIp.substring(lastDot + 1));

        int[] candidates = {1, ownOctet - 1, ownOctet + 1, 100, 254};
        for (int host : candidates) {
            if (host < 1 || host > 254 || host == ownOctet) {
                continue;
            }
            String url = "http://" + prefix + host + ":3000";
            if (healthOk(url)) {
                return url;
            }
        }
        return null;
    }

    /**
     * Разбирает строку октета IPv4-адреса в число.
     *
     * @param part строка октета
     * @return значение октета или 128 при ошибке
     */
    private static int parseOctet(@NonNull String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 128;
        }
    }

    /**
     * Проверяет, подключено ли устройство к Wi‑Fi.
     *
     * @param context контекст приложения
     * @return {@code true}, если активная сеть — Wi‑Fi
     */
    private static boolean isOnWifi(@NonNull Context context) {
        try {
            ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
            if (cm == null) {
                return false;
            }
            Network network = cm.getActiveNetwork();
            if (network == null) {
                return false;
            }
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Возвращает IPv4-адрес телефона в текущей Wi‑Fi-сети.
     *
     * @param context контекст приложения
     * @return IP-адрес или {@code null}
     */
    @Nullable
    private static String wifiIpv4(@NonNull Context context) {
        try {
            ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
            if (cm == null) {
                return null;
            }
            Network network = cm.getActiveNetwork();
            if (network == null) {
                return null;
            }
            LinkProperties props = cm.getLinkProperties(network);
            if (props == null) {
                return null;
            }
            for (LinkAddress linkAddress : props.getLinkAddresses()) {
                InetAddress address = linkAddress.getAddress();
                if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                    return address.getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Проверяет, принадлежит ли IPv4-адрес частному диапазону (LAN).
     *
     * @param ip строка IP-адреса
     * @return {@code true} для частных адресов 10.x, 192.168.x, 172.16–31.x
     */
    private static boolean isPrivateIpv4(@NonNull String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            if (a == 10) {
                return true;
            }
            if (a == 192 && b == 168) {
                return true;
            }
            return a == 172 && b >= 16 && b <= 31;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Проверяет доступность сервера запросом к эндпоинту {@code /health}.
     *
     * @param baseUrl базовый URL сервера
     * @return {@code true}, если сервер ответил 200 и {@code "ok"} в теле
     */
    private static boolean healthOk(@NonNull String baseUrl) {
        if (TextUtils.isEmpty(baseUrl)) {
            return false;
        }
        HttpURLConnection conn = null;
        try {
            URL url = new URL(normalize(baseUrl) + "/health");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(HEALTH_CONNECT_MS);
            conn.setReadTimeout(HEALTH_READ_MS);
            int code = conn.getResponseCode();
            if (code != 200) {
                return false;
            }
            return readBody(conn).contains("\"ok\"");
        } catch (Exception ignored) {
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Читает начало тела HTTP-ответа для проверки health.
     *
     * @param conn открытое HTTP-соединение
     * @return прочитанная строка (до 256 байт)
     * @throws java.io.IOException при ошибке чтения
     */
    @NonNull
    private static String readBody(@NonNull HttpURLConnection conn) throws java.io.IOException {
        try (java.io.InputStream in = conn.getInputStream()) {
            byte[] buf = new byte[256];
            int n = in.read(buf);
            if (n <= 0) {
                return "";
            }
            return new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /**
     * Возвращает SharedPreferences для хранения разрешённого URL сервера.
     *
     * @param context контекст приложения
     * @return объект настроек api_config
     */
    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Убирает пробелы и завершающие слэши из URL.
     *
     * @param url исходный URL
     * @return нормализованный URL
     */
    @NonNull
    private static String normalize(@Nullable String url) {
        if (url == null) {
            return "";
        }
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}
