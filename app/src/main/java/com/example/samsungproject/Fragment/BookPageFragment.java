package com.example.samsungproject.Fragment;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.samsungproject.LocalDB.BookDatabaseHelper;
import com.example.samsungproject.R;
import com.example.samsungproject.net.ApiConfig;
import com.example.samsungproject.net.LibBookApiClient;
import com.example.samsungproject.util.BookJsonHelper;
import com.example.samsungproject.util.SessionHelper;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class BookPageFragment extends Fragment {

    public static final String ARG_BOOK_ID = "bookId";

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    /** Создаёт экземпляр фрагмента страницы чтения книги. */
    public BookPageFragment() {
        super(R.layout.bookpage);
    }

    /** Загружает и отображает текст книги по идентификатору из аргументов. */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!SessionHelper.isLoggedIn(requireContext())) {
            SessionHelper.showLoginRequiredToast(requireContext());
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }

        long bookIdArg = -1L;
        Bundle args = getArguments();
        if (args != null) {
            bookIdArg = args.getLong(ARG_BOOK_ID, -1L);
        }

        MaterialButton btnPageTitle = view.findViewById(R.id.bpbtnPageTitle);
        btnPageTitle.setOnClickListener(v -> NavHostFragment.findNavController(BookPageFragment.this).popBackStack());

        TextView tvPageText = view.findViewById(R.id.tvPageText);

        if (bookIdArg < 0L) {
            btnPageTitle.setText(getString(R.string.no_server_connection));
            tvPageText.setText(getString(R.string.no_server_connection));
            return;
        }

        final long bookId = bookIdArg;
        showCachedTextIfAny(bookId, btnPageTitle, tvPageText);
        loadBookTextFromServer(bookId, btnPageTitle, tvPageText);
    }

    /** Показывает кэшированный текст книги из локальной базы, если он доступен. */
    private void showCachedTextIfAny(long bookId, @NonNull MaterialButton btnPageTitle,
                                     @NonNull TextView tvPageText) {
        try (BookDatabaseHelper localDb = new BookDatabaseHelper(requireContext().getApplicationContext())) {
            BookDatabaseHelper.BookRow row = localDb.getBook(bookId);
            if (row == null) {
                return;
            }
            String title = row.name != null && !row.name.trim().isEmpty() ? row.name.trim() : "—";
            btnPageTitle.setText(title);
            String cached = row.text != null ? row.text.trim() : "";
            if (!cached.isEmpty() && !looksLikeBookmarkMetadata(cached)) {
                tvPageText.setText(cached);
            } else {
                tvPageText.setText(R.string.book_text_loading);
            }
        }
    }

    /** Загружает текст книги с сервера и обновляет интерфейс. */
    private void loadBookTextFromServer(long bookId, @NonNull MaterialButton btnPageTitle,
                                        @NonNull TextView tvPageText) {
        String baseUrl = ApiConfig.baseUrl(requireContext().getApplicationContext());
        executor.execute(() -> {
            try {
                LibBookApiClient.HttpResult r = LibBookApiClient.getBook(baseUrl, bookId);
                if (r.statusCode != 200) {
                    mainHandler.post(() -> applyLoadError(btnPageTitle, tvPageText));
                    return;
                }
                JSONObject book = new JSONObject(r.body);
                String name = book.optString("name", "").trim();
                if (name.isEmpty()) {
                    name = "—";
                }
                String bookText = BookJsonHelper.extractBookText(book);
                String finalName = name;
                String finalText = bookText;
                mainHandler.post(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    btnPageTitle.setText(finalName);
                    if (finalText.isEmpty()) {
                        tvPageText.setText(R.string.book_text_unavailable);
                    } else {
                        tvPageText.setText(finalText);
                    }
                    cacheBookText(bookId, finalName, finalText);
                });
            } catch (Exception e) {
                mainHandler.post(() -> applyLoadError(btnPageTitle, tvPageText));
            }
        });
    }

    /** Отображает сообщение об ошибке загрузки, если текст ещё не был показан. */
    private void applyLoadError(@NonNull MaterialButton btnPageTitle, @NonNull TextView tvPageText) {
        if (!isAdded()) {
            return;
        }
        CharSequence current = tvPageText.getText();
        if (current == null || current.toString().equals(getString(R.string.book_text_loading))) {
            btnPageTitle.setText(getString(R.string.no_server_connection));
            tvPageText.setText(getString(R.string.no_server_connection));
        }
    }


    /** Сохраняет загруженный текст в локальную базу для закладок. */
    private void cacheBookText(long bookId, @NonNull String name, @NonNull String bookText) {
        try (BookDatabaseHelper db = new BookDatabaseHelper(requireContext().getApplicationContext())) {
            if (!db.isBookmarked(bookId)) {
                return;
            }
            BookDatabaseHelper.BookRow row = db.getBook(bookId);
            byte[] icon = row != null ? row.icon : null;
            db.upsertBookmark(bookId, name, icon, bookText.isEmpty() ? null : bookText);
            db.touchLastOpened(bookId);
        }
    }


    /** Проверяет, похож ли текст на метаданные закладки, а не на содержимое книги. */
    private static boolean looksLikeBookmarkMetadata(@NonNull String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("Теги:") || trimmed.startsWith("Теги :")) {
            return true;
        }
        return trimmed.equals("—") || trimmed.equals("-");
    }
}
