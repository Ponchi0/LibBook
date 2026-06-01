package com.libbook.server;

import org.mindrot.jbcrypt.BCrypt;

final class PasswordUtil {

    /** Запрещает создание экземпляров утилитного класса. */
    private PasswordUtil() {
    }

    /** Возвращает BCrypt-хеш для переданного пароля в открытом виде. */
    static String hash(String plain) {
        return BCrypt.hashpw(plain, BCrypt.gensalt());
    }

    /**
     * Проверяет, совпадает ли открытый пароль с сохранённым значением.
     * Поддерживает BCrypt-хеши и устаревшее хранение пароля в открытом виде.
     */
    static boolean matches(String stored, String plain) {
        if (stored == null || plain == null) {
            return false;
        }
        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            try {
                return BCrypt.checkpw(plain, stored);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return stored.equals(plain);
    }
}
