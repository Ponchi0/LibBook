package com.example.samsungproject.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


public final class Fb2TextParser {

    private static final int MAX_BYTES = 24 * 1024 * 1024;
    private static final Pattern ENCODING_IN_XML = Pattern.compile(
            "encoding\\s*=\\s*[\"']([^\"']+)\"",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Приватный конструктор, запрещающий создание экземпляров утилитного класса.
     */
    private Fb2TextParser() {
    }

    /**
     * Определяет, является ли файл форматом FictionBook (FB2) по имени, MIME и содержимому.
     *
     * @param mime      MIME-тип файла
     * @param lowerName имя файла в нижнем регистре
     * @param bytes     первые байты файла
     * @return {@code true}, если файл распознан как FB2
     */
    public static boolean isFb2(@Nullable String mime, @NonNull String lowerName, @NonNull byte[] bytes) {
        if (lowerName.endsWith(".fb2") || lowerName.endsWith(".fb2.zip")) {
            return true;
        }
        if (mime != null) {
            String m = mime.toLowerCase(Locale.ROOT);
            if (m.contains("fictionbook") || "application/xml".equals(m) && lowerName.endsWith(".fb2")) {
                return true;
            }
        }
        if (lowerName.endsWith(".zip") && isZipArchive(bytes)) {
            return lowerName.endsWith(".fb2.zip") || zipContainsFb2Entry(bytes);
        }
        return looksLikeFictionBookXml(bytes);
    }

    /**
     * Извлекает читаемый текст из FB2-файла или FB2 внутри ZIP-архива.
     *
     * @param raw байты FB2 или ZIP с FB2
     * @return извлечённый текст книги
     * @throws Exception при ошибке разбора или пустом теле документа
     */
    @NonNull
    public static String extractText(@NonNull byte[] raw) throws Exception {
        byte[] xmlBytes = unwrapIfZip(raw);
        Charset charset = detectCharset(xmlBytes);
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new InputStreamReader(new ByteArrayInputStream(xmlBytes), charset));

        StringBuilder sb = new StringBuilder();
        boolean inBody = false;
        boolean skipNotesBody = false;
        int skipSubtreeDepth = 0;
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String tag = localName(parser);
                if ("body".equals(tag)) {
                    String bodyName = parser.getAttributeValue(null, "name");
                    skipNotesBody = isSecondaryBody(bodyName);
                    inBody = !skipNotesBody;
                } else if (inBody && !skipNotesBody) {
                    if ("empty-line".equals(tag)) {
                        appendParagraphBreak(sb);
                    } else if (isSkippableTag(tag)) {
                        skipSubtreeDepth++;
                    }
                }
            } else if (event == XmlPullParser.TEXT && inBody && !skipNotesBody && skipSubtreeDepth == 0) {
                String chunk = parser.getText();
                if (chunk != null && !chunk.isEmpty()) {
                    sb.append(chunk);
                }
            } else if (event == XmlPullParser.END_TAG) {
                String tag = localName(parser);
                if ("body".equals(tag)) {
                    inBody = false;
                    skipNotesBody = false;
                } else if (inBody && !skipNotesBody) {
                    if (isSkippableTag(tag) && skipSubtreeDepth > 0) {
                        skipSubtreeDepth--;
                    } else if (skipSubtreeDepth == 0 && isBlockTag(tag)) {
                        appendParagraphBreak(sb);
                    }
                }
            }
            event = parser.next();
        }

        String text = normalizeWhitespace(sb.toString());
        if (text.isEmpty()) {
            throw new IllegalStateException("empty fb2 body");
        }
        return text;
    }

    /**
     * Распаковывает FB2 из ZIP-архива или возвращает исходные байты, если это не ZIP.
     *
     * @param raw байты файла
     * @return байты XML-документа FB2
     * @throws Exception если в ZIP нет файла .fb2
     */
    @NonNull
    private static byte[] unwrapIfZip(@NonNull byte[] raw) throws Exception {
        if (!isZipArchive(raw)) {
            return raw;
        }
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(raw))) {
            ZipEntry entry;
            byte[] firstFb2 = null;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".fb2")) {
                    continue;
                }
                byte[] content = readZipEntryBytes(zis);
                if (firstFb2 == null) {
                    firstFb2 = content;
                }
                if (looksLikeFictionBookXml(content)) {
                    return content;
                }
            }
            if (firstFb2 != null) {
                return firstFb2;
            }
        }
        throw new IllegalStateException("no .fb2 in zip");
    }

    /**
     * Проверяет сигнатуру ZIP-архива по первым байтам.
     *
     * @param bytes содержимое файла
     * @return {@code true}, если байты соответствуют ZIP
     */
    private static boolean isZipArchive(@NonNull byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == 'P' && bytes[1] == 'K'
                && (bytes[2] == 3 || bytes[2] == 5 || bytes[2] == 7)
                && (bytes[3] == 4 || bytes[3] == 6 || bytes[3] == 8);
    }

    /**
     * Проверяет наличие записи .fb2 внутри ZIP-архива.
     *
     * @param bytes байты ZIP-файла
     * @return {@code true}, если найден файл с расширением .fb2
     */
    private static boolean zipContainsFb2Entry(@NonNull byte[] bytes) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()
                        && entry.getName().toLowerCase(Locale.ROOT).endsWith(".fb2")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Проверяет, похожи ли первые байты на XML-документ FictionBook.
     *
     * @param bytes содержимое файла
     * @return {@code true}, если обнаружены маркеры FB2
     */
    private static boolean looksLikeFictionBookXml(@NonNull byte[] bytes) {
        int len = Math.min(bytes.length, 4096);
        String head = new String(bytes, 0, len, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
        return head.contains("fictionbook") || head.contains("<fb2");
    }

    /**
     * Определяет кодировку XML по объявлению или BOM.
     *
     * @param bytes байты XML-документа
     * @return charset для чтения текста
     */
    @NonNull
    private static Charset detectCharset(@NonNull byte[] bytes) {
        int len = Math.min(bytes.length, 512);
        String head = new String(bytes, 0, len, StandardCharsets.US_ASCII);
        Matcher m = ENCODING_IN_XML.matcher(head);
        if (m.find()) {
            try {
                return Charset.forName(m.group(1).trim());
            } catch (Exception ignored) {
            }
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        return StandardCharsets.UTF_8;
    }

    /**
     * Определяет, является ли секция body вторичной (примечания, комментарии, сноски).
     *
     * @param bodyName значение атрибута name у тега body
     * @return {@code true}, если тело следует пропустить при извлечении текста
     */
    private static boolean isSecondaryBody(@Nullable String bodyName) {
        if (bodyName == null || bodyName.isBlank()) {
            return false;
        }
        String n = bodyName.trim().toLowerCase(Locale.ROOT);
        return "notes".equals(n) || "comments".equals(n) || "footnotes".equals(n);
    }

    /**
     * Проверяет, является ли тег блочным элементом, после которого нужен разрыв абзаца.
     *
     * @param tag локальное имя XML-тега
     * @return {@code true} для блочных тегов FB2
     */
    private static boolean isBlockTag(@NonNull String tag) {
        switch (tag) {
            case "p":
            case "v":
            case "subtitle":
            case "text-author":
            case "epigraph":
            case "cite":
            case "poem":
            case "stanza":
            case "section":
            case "title":
                return true;
            default:
                return false;
        }
    }

    /**
     * Проверяет, следует ли пропустить содержимое тега (бинарные данные, изображения).
     *
     * @param tag локальное имя XML-тега
     * @return {@code true} для пропускаемых тегов
     */
    private static boolean isSkippableTag(@NonNull String tag) {
        return "binary".equals(tag) || "image".equals(tag) || "inline-image".equals(tag);
    }

    /**
     * Возвращает локальное имя XML-тега без префикса пространства имён.
     *
     * @param parser XML-парсер
     * @return локальное имя текущего тега
     */
    @NonNull
    private static String localName(@NonNull XmlPullParser parser) {
        String name = parser.getName();
        if (name == null) {
            return "";
        }
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    /**
     * Добавляет разрыв абзаца в буфер, избегая лишних пустых строк.
     *
     * @param sb накопитель текста
     */
    private static void appendParagraphBreak(@NonNull StringBuilder sb) {
        int len = sb.length();
        if (len == 0) {
            return;
        }
        if (len >= 2 && sb.charAt(len - 1) == '\n' && sb.charAt(len - 2) == '\n') {
            return;
        }
        if (len >= 1 && sb.charAt(len - 1) == '\n') {
            sb.append('\n');
        } else {
            sb.append("\n\n");
        }
    }

    /**
     * Нормализует пробелы и переводы строк в извлечённом тексте.
     *
     * @param text исходный текст
     * @return текст с единообразными пробелами и абзацами
     */
    @NonNull
    private static String normalizeWhitespace(@NonNull String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        normalized = normalized.replaceAll("[ \\t\\x0B\\f]+", " ");
        normalized = normalized.replaceAll(" ?\\n ?", "\n");
        normalized = normalized.replaceAll("\n{3,}", "\n\n");
        return normalized.trim();
    }

    /**
     * Читает содержимое текущей записи ZIP-потока с ограничением по размеру.
     *
     * @param in поток ZIP-записи
     * @return байты содержимого записи
     * @throws Exception если файл превышает допустимый размер
     */
    @NonNull
    private static byte[] readZipEntryBytes(@NonNull InputStream in) throws Exception {
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
