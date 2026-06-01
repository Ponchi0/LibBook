package com.libbook.server;

import java.nio.file.Path;

public final class Main {

    /**
     * Точка входа сервера: разбирает аргументы и переменные окружения, открывает БД и запускает HTTP-сервер.
     */
    public static void main(String[] args) throws Exception {
        int port = 3000;
        Path dbPath = Path.of("data.db");
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if ("--db".equals(args[i]) && i + 1 < args.length) {
                dbPath = Path.of(args[++i]);
            }
        }
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            port = Integer.parseInt(envPort.trim());
        }
        String envDb = System.getenv("DB_PATH");
        if (envDb != null && !envDb.isBlank()) {
            dbPath = Path.of(envDb.trim());
        }

        Database db = new Database(dbPath);
        LibBookHttpServer server = new LibBookHttpServer(port, db);
        server.start(0, false);
        System.out.println("LibBookServer (Java) listening on http://0.0.0.0:" + port);
        System.out.println("Database: " + dbPath.toAbsolutePath());
        Thread.currentThread().join();
    }
}
