package com.example.samsungproject.net;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import com.example.samsungproject.R;


public final class ApiConfig {

    /**
     * Приватный конструктор, запрещающий создание экземпляров утилитного класса.
     */
    private ApiConfig() {
    }

    /**
     * Возвращает базовый URL API с учётом типа устройства и настроек подключения.
     *
     * @param context контекст приложения
     * @return нормализованный базовый URL без завершающего слэша
     */
    @NonNull
    public static String baseUrl(@NonNull Context context) {
        if (isGenymotion()) {
            return normalize(context.getString(R.string.api_base_url_genymotion));
        }
        if (isEmulator()) {
            return normalize(context.getString(R.string.api_base_url_emulator));
        }
        if (context.getResources().getBoolean(R.bool.api_use_usb_reverse)) {
            return normalize(context.getString(R.string.api_base_url_usb));
        }
        return ApiServerResolver.cachedOrDefault(context);
    }

    /**
     * Асинхронно обновляет кэшированный адрес сервера (для физических устройств в LAN).
     *
     * @param context контекст приложения
     */
    public static void refreshAsync(@NonNull Context context) {
        if (isGenymotion() || isEmulator()) {
            return;
        }
        if (context.getResources().getBoolean(R.bool.api_use_usb_reverse)) {
            return;
        }
        ApiServerResolver.refreshAsync(context);
    }

    /**
     * Убирает пробелы и завершающие слэши из URL.
     *
     * @param url исходный URL
     * @return нормализованный URL
     */
    @NonNull
    private static String normalize(String url) {
        if (url == null) {
            return "";
        }
        String u = url.trim();
        if (u.isEmpty()) {
            return "";
        }
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    /**
     * Проверяет, запущено ли приложение на эмуляторе Genymotion.
     *
     * @return {@code true}, если устройство — Genymotion
     */
    private static boolean isGenymotion() {
        String manufacturer = Build.MANUFACTURER;
        return manufacturer != null && manufacturer.contains("Genymotion");
    }

    /**
     * Определяет, работает ли приложение на Android-эмуляторе.
     *
     * @return {@code true}, если обнаружены признаки эмулятора
     */
    public static boolean isEmulator() {
        if ("1".equals(System.getProperty("ro.kernel.qemu"))) {
            return true;
        }
        String fingerprint = Build.FINGERPRINT;
        String model = Build.MODEL;
        String product = Build.PRODUCT;
        String hardware = Build.HARDWARE;
        String brand = Build.BRAND;
        String device = Build.DEVICE;

        if (fingerprint != null) {
            String fp = fingerprint.toLowerCase();
            if (fp.startsWith("generic") || fp.startsWith("unknown")) {
                return true;
            }
        }
        if (hardware != null) {
            String hw = hardware.toLowerCase();
            if (hw.contains("goldfish") || hw.contains("ranchu") || hw.contains("vbox")
                    || hw.contains("generic") || hw.contains("qemu")) {
                return true;
            }
            if ("android_x86".equals(hw)) {
                return true;
            }
        }
        if (product != null) {
            String p = product.toLowerCase();
            if (p.startsWith("sdk_") || p.contains("emulator") || p.contains("simulator")) {
                return true;
            }
        }
        if (model != null) {
            String m = model.toLowerCase();
            if (m.contains("emulator") || m.contains("google_sdk") || m.contains("sdk_gphone")
                    || m.contains("android sdk built for")) {
                return true;
            }
        }
        return brand != null && brand.startsWith("generic")
                && device != null && device.startsWith("generic");
    }
}
