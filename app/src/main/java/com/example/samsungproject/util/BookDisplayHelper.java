package com.example.samsungproject.util;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.samsungproject.R;

import org.json.JSONObject;

import java.util.Locale;

public final class BookDisplayHelper {

    /**
     * Приватный конструктор, запрещающий создание экземпляров утилитного класса.
     */
    private BookDisplayHelper() {
    }

    /**
     * Извлекает среднюю оценку книги из JSON-объекта.
     *
     * @param book JSON-объект книги
     * @return средняя оценка или {@code NaN}, если поле отсутствует
     */
    public static double parseAvgRating(@NonNull JSONObject book) {
        if (!book.isNull("avgRating")) {
            return book.optDouble("avgRating", Double.NaN);
        }
        return Double.NaN;
    }

    /**
     * Форматирует среднюю оценку с одним знаком после запятой для отображения.
     *
     * @param avg средняя оценка
     * @return строка с оценкой или пустая строка, если оценка не задана
     */
    @NonNull
    public static String formatAvgTenths(double avg) {
        if (Double.isNaN(avg) || avg <= 0) {
            return "";
        }
        return String.format(Locale.ROOT, "%.1f", avg);
    }

    /**
     * Привязывает бейдж рейтинга в каталоге: текст, цвет фона и видимость.
     *
     * @param badge TextView для отображения рейтинга
     * @param avg   средняя оценка книги
     */
    public static void bindCatalogRatingBadge(@Nullable TextView badge, double avg) {
        if (badge == null) {
            return;
        }
        if (Double.isNaN(avg) || avg < 0) {
            badge.setVisibility(View.GONE);
            return;
        }

        badge.setTextColor(ContextCompat.getColor(badge.getContext(), R.color.black));

        if (avg == 0) {
            badge.setText("0");
            badge.setBackgroundResource(R.drawable.bg_catalog_rating_zero);
            badge.setVisibility(View.VISIBLE);
            return;
        }

        badge.setText(String.format(Locale.ROOT, "%.1f", avg));
        int bgRes;
        if (avg >= 7) {
            bgRes = R.drawable.bg_catalog_rating_high;
        } else if (avg >= 4) {
            bgRes = R.drawable.bg_catalog_rating_mid;
        } else if (avg >= 1) {
            bgRes = R.drawable.bg_catalog_rating_low;
        } else {
            bgRes = R.drawable.bg_catalog_rating_zero;
        }
        badge.setBackgroundResource(bgRes);
        badge.setVisibility(View.VISIBLE);
    }
}
