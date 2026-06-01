package com.example.samsungproject.util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDResources;
import com.tom_roush.pdfbox.pdmodel.graphics.PDXObject;
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;


public final class BookTextExtractor {

    private static final int MAX_BYTES = 24 * 1024 * 1024;
    private static volatile boolean pdfBoxInitialized;

    public enum Status {
        OK,
        EMPTY,
        HAS_IMAGES,
        UNSUPPORTED,
        IO_ERROR
    }

    public static final class Result {
        public final Status status;
        @Nullable public final String text;

        /**
         * Создаёт результат извлечения текста с указанным статусом и содержимым.
         *
         * @param status статус операции
         * @param text   извлечённый текст или {@code null}
         */
        private Result(Status status, @Nullable String text) {
            this.status = status;
            this.text = text;
        }

        /**
         * Создаёт успешный результат с непустым текстом.
         *
         * @param text извлечённый текст
         * @return результат со статусом {@link Status#OK}
         */
        public static Result ok(@NonNull String text) {
            return new Result(Status.OK, text);
        }

        /**
         * Создаёт результат без текста с указанным статусом.
         *
         * @param status статус операции
         * @return результат без текста
         */
        public static Result of(Status status) {
            return new Result(status, null);
        }
    }


    public static final String[] PICK_TEXT_MIME_TYPES = {
            "text/plain",
            "application/pdf",
            "application/x-fictionbook+xml",
            "application/fictionbook+xml",
            "application/xml",
            "application/zip",
    };

    /**
     * Приватный конструктор, запрещающий создание экземпляров утилитного класса.
     */
    private BookTextExtractor() {
    }

    /**
     * Извлекает текст из файла по URI: поддерживаются TXT, PDF и FB2.
     *
     * @param context контекст для доступа к ContentResolver
     * @param uri     URI выбранного файла
     * @return результат извлечения со статусом и текстом
     */
    @NonNull
    public static Result extract(@NonNull Context context, @NonNull Uri uri) {
        Context app = context.getApplicationContext();
        try {
            String mime = app.getContentResolver().getType(uri);
            String displayName = resolveDisplayName(app, uri);
            String lower = displayName.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".docx") || lower.endsWith(".doc")) {
                return Result.of(Status.UNSUPPORTED);
            }
            byte[] bytes = readBytes(app, uri);
            if (bytes.length == 0) {
                return Result.of(Status.EMPTY);
            }
            if (isPdf(mime, lower, bytes)) {
                return extractPdf(app, bytes);
            }
            if (isTxt(mime, lower)) {
                String text = new String(bytes, StandardCharsets.UTF_8).trim();
                return text.isEmpty() ? Result.of(Status.EMPTY) : Result.ok(text);
            }
            if (Fb2TextParser.isFb2(mime, lower, bytes)) {
                return extractFb2(bytes);
            }
            return Result.of(Status.UNSUPPORTED);
        } catch (Exception e) {
            return Result.of(Status.IO_ERROR);
        }
    }

    /**
     * Проверяет, является ли файл обычным текстовым (TXT) по MIME или расширению.
     *
     * @param mime      MIME-тип
     * @param lowerName имя файла в нижнем регистре
     * @return {@code true} для текстовых файлов
     */
    private static boolean isTxt(@Nullable String mime, @NonNull String lowerName) {
        return "text/plain".equals(mime) || lowerName.endsWith(".txt");
    }

    /**
     * Извлекает текст из FB2-файла через {@link Fb2TextParser}.
     *
     * @param bytes содержимое файла
     * @return результат извлечения
     */
    @NonNull
    private static Result extractFb2(@NonNull byte[] bytes) {
        try {
            String text = Fb2TextParser.extractText(bytes).trim();
            return text.isEmpty() ? Result.of(Status.EMPTY) : Result.ok(text);
        } catch (IllegalStateException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("too large")) {
                return Result.of(Status.IO_ERROR);
            }
            return Result.of(Status.EMPTY);
        } catch (Exception e) {
            return Result.of(Status.UNSUPPORTED);
        }
    }

    /**
     * Проверяет, является ли файл PDF по MIME, расширению или сигнатуре.
     *
     * @param mime      MIME-тип
     * @param lowerName имя файла в нижнем регистре
     * @param bytes     содержимое файла
     * @return {@code true} для PDF-документов
     */
    private static boolean isPdf(@Nullable String mime, @NonNull String lowerName, @NonNull byte[] bytes) {
        if ("application/pdf".equals(mime) || lowerName.endsWith(".pdf")) {
            return true;
        }
        return bytes.length >= 4
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F';
    }

    /**
     * Определяет отображаемое имя файла по URI через ContentResolver или путь.
     *
     * @param context контекст приложения
     * @param uri     URI файла
     * @return имя файла или пустая строка
     */
    @NonNull
    private static String resolveDisplayName(@NonNull Context context, @NonNull Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = cursor.getString(idx);
                    if (name != null && !name.trim().isEmpty()) {
                        return name.trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        if (last != null && !last.isEmpty()) {
            return last;
        }
        String path = uri.getPath();
        if (path != null) {
            int slash = path.lastIndexOf('/');
            if (slash >= 0 && slash < path.length() - 1) {
                return path.substring(slash + 1);
            }
            return path;
        }
        return "";
    }

    /**
     * Извлекает текст из PDF; возвращает {@link Status#HAS_IMAGES}, если документ содержит изображения.
     *
     * @param app   контекст приложения для инициализации PDFBox
     * @param bytes содержимое PDF
     * @return результат извлечения
     * @throws Exception при ошибке чтения PDF
     */
    @NonNull
    private static Result extractPdf(@NonNull Context app, @NonNull byte[] bytes) throws Exception {
        ensurePdfBoxInit(app);
        try (PDDocument doc = PDDocument.load(bytes)) {
            if (pdfHasImages(doc)) {
                return Result.of(Status.HAS_IMAGES);
            }
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            if (text == null) {
                text = "";
            }
            text = text.trim();
            return text.isEmpty() ? Result.of(Status.EMPTY) : Result.ok(text);
        }
    }

    /**
     * Однократно инициализирует PDFBox для работы на Android.
     *
     * @param app контекст приложения
     */
    private static void ensurePdfBoxInit(@NonNull Context app) {
        if (!pdfBoxInitialized) {
            synchronized (BookTextExtractor.class) {
                if (!pdfBoxInitialized) {
                    PDFBoxResourceLoader.init(app.getApplicationContext());
                    pdfBoxInitialized = true;
                }
            }
        }
    }

    /**
     * Проверяет наличие изображений на любой странице PDF-документа.
     *
     * @param doc загруженный PDF-документ
     * @return {@code true}, если найдены растровые изображения
     * @throws Exception при ошибке обхода ресурсов страницы
     */
    private static boolean pdfHasImages(@NonNull PDDocument doc) throws Exception {
        for (PDPage page : doc.getPages()) {
            if (resourcesHaveImages(page.getResources())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Рекурсивно проверяет ресурсы страницы или формы на наличие изображений.
     *
     * @param resources ресурсы PDF-страницы или формы
     * @return {@code true}, если обнаружено изображение
     * @throws Exception при ошибке доступа к XObject
     */
    private static boolean resourcesHaveImages(@Nullable PDResources resources) throws Exception {
        if (resources == null) {
            return false;
        }
        for (COSName name : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(name);
            if (xObject instanceof PDImageXObject) {
                return true;
            }
            if (xObject instanceof PDFormXObject) {
                if (resourcesHaveImages(((PDFormXObject) xObject).getResources())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Читает байты файла по URI через ContentResolver.
     *
     * @param context контекст приложения
     * @param uri     URI файла
     * @return содержимое файла
     * @throws Exception при ошибке чтения
     */
    @NonNull
    private static byte[] readBytes(@NonNull Context context, @NonNull Uri uri) throws Exception {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return new byte[0];
            }
            return readStreamBytes(in);
        }
    }

    /**
     * Читает поток в массив байтов с ограничением максимального размера.
     *
     * @param in входной поток
     * @return прочитанные байты
     * @throws Exception если размер превышает {@link #MAX_BYTES}
     */
    @NonNull
    private static byte[] readStreamBytes(@NonNull InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(chunk)) != -1) {
            total += n;
            if (total > MAX_BYTES) {
                throw new IllegalStateException("file too large");
            }
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }
}
