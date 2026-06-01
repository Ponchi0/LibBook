package com.example.samsungproject.Fragment;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.samsungproject.LocalDB.BookDatabaseHelper;
import com.example.samsungproject.R;
import com.example.samsungproject.net.ApiConfig;
import com.example.samsungproject.net.LibBookApiClient;
import com.example.samsungproject.util.BookJsonHelper;
import com.example.samsungproject.util.BookmarkSyncHelper;
import com.example.samsungproject.util.SessionHelper;
import com.example.samsungproject.util.UserResponseParser;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainBookPageFragment extends Fragment {

    public static final String ARG_BOOK_ID = "bookId";

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private boolean isDescriptionExpanded = false;
    private boolean isTagsExpanded = false;

    private long bookId = -1L;
    private boolean bookLoaded = false;
    @Nullable private String loadedBookName;
    @Nullable private byte[] loadedIconBytes;
    @Nullable private String loadedDescription;
    @Nullable private String loadedTagsLine;
    @Nullable private String loadedBookText;

    private TextView tvDescription;
    private TextView tvTags;
    private MaterialButton btnDescriptionMore;
    private MaterialButton btnTagsMore;
    private ImageView imgCover;
    private MaterialAutoCompleteTextView actRating;
    private TextInputLayout tilBookRating;
    private MaterialButton btnSubmitRating;
    private int pendingRating = -1;
    private long serverUserId = -1L;
    private final View.OnClickListener loginRequiredClickListener = v ->
            SessionHelper.showLoginRequiredToast(requireContext());

    /**
     * Создаёт фрагмент страницы книги с описанием, рейтингом и закладками.
     */
    public MainBookPageFragment() {
        super(R.layout.mainbookpage);
    }

    /**
     * Инициализирует UI книги, навигацию, рейтинг и загрузку данных с сервера.
     *
     * @param view               корневое представление фрагмента
     * @param savedInstanceState сохранённое состояние или {@code null}
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            bookId = args.getLong(ARG_BOOK_ID, -1L);
        }

        imgCover = view.findViewById(R.id.mbpimgCover);

        MaterialButton btnMarkbooks = view.findViewById(R.id.btn_mbpMarkbooks);
        btnMarkbooks.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_mainBookPageFragment_to_markbooksFragment)
        );

        MaterialButton btnCatalog = view.findViewById(R.id.btn_mbpCatalog);
        btnCatalog.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_mainBookPageFragment_to_catalogFragment)
        );

        MaterialButton btnMainMenu = view.findViewById(R.id.btn_mbpMainMenu);
        btnMainMenu.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_mainBookPageFragment_to_mainMenuFragment)
        );

        MaterialButton btnGeneral = view.findViewById(R.id.btn_mbpGeneral);
        btnGeneral.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_mainBookPageFragment_to_generalFragment)
        );

        MaterialButton btnAddBookmark = view.findViewById(R.id.mbpbtnAddBookmark);
        btnAddBookmark.setOnClickListener(v -> saveCurrentBookToLocalDb());

        MaterialButton btnRead = view.findViewById(R.id.mbpbtnRead);
        btnRead.setOnClickListener(v -> {
            if (!requireLogin()) {
                return;
            }
            if (bookId < 0L) {
                Toast.makeText(requireContext(), R.string.catalog_load_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            Bundle b = new Bundle();
            b.putLong(ARG_BOOK_ID, bookId);
            NavHostFragment.findNavController(MainBookPageFragment.this)
                    .navigate(R.id.action_mainBookPageFragment_to_bookPageFragment, b);
        });

        tvDescription = view.findViewById(R.id.tvDescription);
        btnDescriptionMore = view.findViewById(R.id.mbpbtnDescriptionMore);
        btnDescriptionMore.setOnClickListener(v -> {
            isDescriptionExpanded = !isDescriptionExpanded;
            if (isDescriptionExpanded) {
                tvDescription.setMaxLines(Integer.MAX_VALUE);
                tvDescription.setEllipsize(null);
                btnDescriptionMore.setText("Свернуть");
            } else {
                tvDescription.setMaxLines(3);
                tvDescription.setEllipsize(TextUtils.TruncateAt.END);
                btnDescriptionMore.setText("Ещё");
            }
        });

        tilBookRating = view.findViewById(R.id.tilBookRating);
        actRating = view.findViewById(R.id.actBookRating);
        ArrayAdapter<CharSequence> ratingAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.book_ratings,
                android.R.layout.simple_list_item_1
        );
        actRating.setAdapter(ratingAdapter);
        actRating.setEnabled(false);
        btnSubmitRating = view.findViewById(R.id.mbpbtnSubmitRating);
        btnSubmitRating.setEnabled(false);
        btnSubmitRating.setOnClickListener(v -> {
            if (!SessionHelper.isLoggedIn(requireContext())) {
                SessionHelper.showRatingLoginRequiredToast(requireContext());
                return;
            }
            submitRating();
        });

        actRating.setOnItemClickListener((parent, v, position, id) -> {
            if (!actRating.isEnabled()) return;
            String s = parent.getItemAtPosition(position) != null ? parent.getItemAtPosition(position).toString() : "";
            try {
                pendingRating = Integer.parseInt(s.trim());
            } catch (Exception ignored) {
                pendingRating = -1;
            }
            btnSubmitRating.setEnabled(pendingRating >= 1 && pendingRating <= 10 && SessionHelper.isLoggedIn(requireContext()));
        });

        tvTags = view.findViewById(R.id.mbptvTags);
        btnTagsMore = view.findViewById(R.id.mbpbtnTagsMore);
        btnTagsMore.setOnClickListener(v -> {
            isTagsExpanded = !isTagsExpanded;
            if (isTagsExpanded) {
                tvTags.setMaxLines(Integer.MAX_VALUE);
                tvTags.setEllipsize(null);
                btnTagsMore.setText("Свернуть");
            } else {
                tvTags.setMaxLines(2);
                tvTags.setEllipsize(TextUtils.TruncateAt.END);
                btnTagsMore.setText("Ещё");
            }
        });

        serverUserId = SessionHelper.getServerUserId(requireContext());
        updateRatingForLoginState();

        if (bookId >= 0L) {
            loadBookFromServer(bookId);
        }
    }

    /**
     * При возврате на экран обновляет состояние авторизации и блока оценки.
     */
    @Override
    public void onResume() {
        super.onResume();
        serverUserId = SessionHelper.getServerUserId(requireContext());
        updateRatingForLoginState();
    }

    /**
     * Проверяет, авторизован ли пользователь; при необходимости показывает подсказку о входе.
     *
     * @return {@code true}, если пользователь вошёл в аккаунт
     */
    private boolean requireLogin() {
        if (SessionHelper.isLoggedIn(requireContext())) {
            serverUserId = SessionHelper.getServerUserId(requireContext());
            return true;
        }
        SessionHelper.showLoginRequiredToast(requireContext());
        return false;
    }

    /**
     * Включает или блокирует выбор и отправку оценки в зависимости от авторизации и загрузки книги.
     */
    private void updateRatingForLoginState() {
        if (!isAdded() || actRating == null || btnSubmitRating == null) {
            return;
        }
        boolean loggedIn = SessionHelper.isLoggedIn(requireContext());
        if (loggedIn) {
            actRating.setOnClickListener(null);
            if (tilBookRating != null) {
                tilBookRating.setOnClickListener(null);
            }
            actRating.setEnabled(bookLoaded);
            boolean canSubmit = pendingRating >= 1 && pendingRating <= 10 && bookLoaded;
            btnSubmitRating.setEnabled(canSubmit);
        } else {
            actRating.setEnabled(false);
            btnSubmitRating.setEnabled(bookLoaded);
            actRating.setOnClickListener(loginRequiredClickListener);
            if (tilBookRating != null) {
                tilBookRating.setOnClickListener(loginRequiredClickListener);
            }
        }
    }

    /**
     * Сбрасывает развёрнутое состояние описания и тегов к свёрнутому виду.
     */
    private void resetExpandUi() {
        isDescriptionExpanded = false;
        isTagsExpanded = false;
        tvDescription.setMaxLines(3);
        tvDescription.setEllipsize(TextUtils.TruncateAt.END);
        btnDescriptionMore.setText("Ещё");
        tvTags.setMaxLines(2);
        tvTags.setEllipsize(TextUtils.TruncateAt.END);
        btnTagsMore.setText("Ещё");
    }

    /**
     * Загружает данные книги с сервера и обновляет UI на главном потоке.
     *
     * @param id идентификатор книги на сервере
     */
    private void loadBookFromServer(long id) {
        String baseUrl = ApiConfig.baseUrl(requireContext().getApplicationContext());
        final boolean loggedIn = SessionHelper.isLoggedIn(requireContext());
        final long userId = SessionHelper.getServerUserId(requireContext());
        executor.execute(() -> {
            try {
                LibBookApiClient.HttpResult r = loggedIn
                        ? LibBookApiClient.getBook(baseUrl, id, userId)
                        : LibBookApiClient.getBook(baseUrl, id);
                if (r.statusCode != 200) {
                    mainHandler.post(() -> {
                        if (!isAdded()) return;
                        actRating.setEnabled(false);
                        btnSubmitRating.setEnabled(false);
                        Toast.makeText(requireContext(), R.string.catalog_load_failed, Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                JSONObject book = new JSONObject(r.body);
                final byte[] iconBytes = UserResponseParser.decodeUserIcon(book);
                String rawName = book.optString("name", "").trim();
                final String name = rawName.isEmpty() ? "—" : rawName;
                final String description = book.isNull("description") ? "" : book.optString("description", "");
                final String tagsLine = BookJsonHelper.formatTagsLine(book);
                final String bookText = BookJsonHelper.extractBookText(book);
                final int my = book.isNull("myRating") ? -1 : book.optInt("myRating", -1);

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    loadedBookName = name;
                    loadedIconBytes = iconBytes;
                    loadedDescription = description;
                    loadedTagsLine = tagsLine;
                    loadedBookText = bookText;
                    bookLoaded = true;
                    updateRatingForLoginState();
                    recordLastOpenedIfBookmarked();
                    if (iconBytes != null && iconBytes.length > 0) {
                        Bitmap bm = BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.length);
                        if (bm != null) {
                            imgCover.setImageBitmap(bm);
                        } else {
                            imgCover.setImageResource(R.drawable.icon_null);
                        }
                    } else {
                        imgCover.setImageResource(R.drawable.icon_null);
                    }
                    tvDescription.setText(description);
                    tvTags.setText(tagsLine);
                    resetExpandUi();

                    if (my >= 1 && my <= 10) {
                        actRating.setText(String.valueOf(my), false);
                        pendingRating = my;
                        updateRatingForLoginState();
                    } else {
                        actRating.setText("", false);
                        pendingRating = -1;
                        updateRatingForLoginState();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    actRating.setEnabled(false);
                    btnSubmitRating.setEnabled(false);
                    Toast.makeText(requireContext(), R.string.register_network_error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * Отправляет выбранную пользователем оценку книги на сервер.
     */
    private void submitRating() {
        if (!requireLogin() || !bookLoaded || bookId < 0L) return;
        if (pendingRating < 1 || pendingRating > 10) return;
        String baseUrl = ApiConfig.baseUrl(requireContext().getApplicationContext());
        btnSubmitRating.setEnabled(false);
        executor.execute(() -> {
            try {
                LibBookApiClient.HttpResult r = LibBookApiClient.postBookRating(baseUrl, bookId, serverUserId, pendingRating);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    if (r.statusCode == 200) {
                        loadBookFromServer(bookId);
                    } else {
                        btnSubmitRating.setEnabled(true);
                        Toast.makeText(requireContext(), R.string.catalog_load_failed, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    btnSubmitRating.setEnabled(true);
                    Toast.makeText(requireContext(), R.string.register_network_error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * Сохраняет текущую книгу в локальные закладки и ставит синхронизацию с сервером.
     */
    private void saveCurrentBookToLocalDb() {
        if (!requireLogin()) {
            return;
        }
        if (bookId < 0L) {
            Toast.makeText(requireContext(), R.string.catalog_load_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!bookLoaded) {
            Toast.makeText(requireContext(), R.string.bookmark_wait_load, Toast.LENGTH_SHORT).show();
            return;
        }
        String bodyText = loadedBookText != null ? loadedBookText.trim() : "";
        try (BookDatabaseHelper db = new BookDatabaseHelper(requireContext().getApplicationContext())) {
            db.upsertBookmark(bookId, loadedBookName, loadedIconBytes,
                    bodyText.isEmpty() ? null : bodyText);
        }
        BookmarkSyncHelper.markPendingAndPush(requireContext(), bookId, null);
        Toast.makeText(requireContext(), R.string.bookmark_saved, Toast.LENGTH_SHORT).show();
    }

    /**
     * Обновляет время последнего открытия книги в закладках, если она уже сохранена локально.
     */
    private void recordLastOpenedIfBookmarked() {
        if (!SessionHelper.isLoggedIn(requireContext()) || bookId < 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        try (BookDatabaseHelper db = new BookDatabaseHelper(requireContext().getApplicationContext())) {
            if (db.touchLastOpenedAt(bookId, now)) {
                BookmarkSyncHelper.pushTouchAsync(requireContext(), bookId, now);
            }
        }
    }
}
