package com.example.samsungproject.Fragment;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.samsungproject.LocalDB.BookDatabaseHelper;
import com.example.samsungproject.R;
import com.example.samsungproject.net.ApiConfig;
import com.example.samsungproject.net.LibBookApiClient;
import com.example.samsungproject.util.BookDisplayHelper;
import com.example.samsungproject.util.UserIconHelper;
import com.example.samsungproject.util.UserResponseParser;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainMenuFragment extends Fragment {

    private static final int NEW_BOOKS_LIMIT = 30;
    private static final int POPULAR_BOOKS_LIMIT = 10;
    private static final int SEARCH_SUGGESTIONS_LIMIT = 10;
    private static final long MAIN_MENU_CACHE_MS = 300_000L;

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<NewBook> popularBooks = new ArrayList<>();
    private final List<BookDatabaseHelper.BookRow> continueReadingBooks = new ArrayList<>();
    private final List<NewBook> newBooks = new ArrayList<>();
    private final List<SearchBook> searchSuggestions = new ArrayList<>();
    private PopularAdapter popularAdapter;
    private ContinueReadingAdapter continueReadingAdapter;
    private NewBooksAdapter newBooksAdapter;
    private SearchSuggestionsAdapter searchSuggestionsAdapter;
    private RecyclerView listSearchSuggestions;
    private EditText searchBar;
    @Nullable private ShapeableImageView btnUserIcon;
    @Nullable private List<SearchBook> searchAllBooksCache;
    @Nullable private Runnable searchDebounce;
    private long mainMenuServerLoadedAtMs;

    /**
     * Создаёт фрагмент главного меню с поиском и подборками книг.
     */
    public MainMenuFragment() {
        super(R.layout.activity_mainmenu);
    }

    /**
     * Настраивает поиск, навигацию и списки популярных, новых и продолжаемых книг.
     *
     * @param view               корневое представление фрагмента
     * @param savedInstanceState сохранённое состояние или {@code null}
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        searchBar = view.findViewById(R.id.searchBar);
        listSearchSuggestions = view.findViewById(R.id.listSearchSuggestions);
        listSearchSuggestions.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
        searchSuggestionsAdapter = new SearchSuggestionsAdapter();
        listSearchSuggestions.setAdapter(searchSuggestionsAdapter);

        searchBar.addTextChangedListener(new TextWatcher() {
            /** Не используется; требуется интерфейсом {@link TextWatcher}. */
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            /** Не используется; требуется интерфейсом {@link TextWatcher}. */
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            /**
             * Планирует обновление подсказок поиска после изменения текста.
             *
             * @param s текущий текст строки поиска
             */
            @Override public void afterTextChanged(Editable s) {
                scheduleSearchSuggestions(s != null ? s.toString() : "");
            }
        });

        btnUserIcon = view.findViewById(R.id.btn_UserIcon);
        btnUserIcon.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_mainMenuFragment_to_generalFragment)
        );
        refreshUserIcon();

        MaterialButton btnCatalog = view.findViewById(R.id.btn_mmCatalog);
        btnCatalog.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_mainMenuFragment_to_catalogFragment)
        );

        MaterialButton btnGeneral = view.findViewById(R.id.btn_mmGeneral);
        btnGeneral.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_mainMenuFragment_to_generalFragment)
        );

        MaterialButton btnMarkbooks = view.findViewById(R.id.btn_mmMarkbooks);
        btnMarkbooks.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_mainMenuFragment_to_markbooksFragment)
        );

        RecyclerView listPopular = view.findViewById(R.id.listPopular);
        listPopular.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        );
        popularAdapter = new PopularAdapter();
        listPopular.setAdapter(popularAdapter);

        RecyclerView listContinueReading = view.findViewById(R.id.listContinueReading);
        listContinueReading.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        );
        continueReadingAdapter = new ContinueReadingAdapter();
        listContinueReading.setAdapter(continueReadingAdapter);

        RecyclerView listNew = view.findViewById(R.id.listNew);
        listNew.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
        newBooksAdapter = new NewBooksAdapter();
        listNew.setAdapter(newBooksAdapter);
    }

    /**
     * Обновляет иконку пользователя в шапке главного меню.
     */
    private void refreshUserIcon() {
        if (btnUserIcon != null) {
            UserIconHelper.bindLoggedInUserIcon(btnUserIcon);
        }
    }

    /**
     * При возврате на экран обновляет аватар и перезагружает все подборки книг.
     */
    @Override
    public void onResume() {
        super.onResume();
        refreshUserIcon();
        loadContinueReading();
        loadMainMenuFromServerIfNeeded();
    }

    /**
     * Загружает подборки и поиск с сервера не чаще одного раза в {@link #MAIN_MENU_CACHE_MS}.
     */
    private void loadMainMenuFromServerIfNeeded() {
        long now = System.currentTimeMillis();
        if (mainMenuServerLoadedAtMs > 0
                && now - mainMenuServerLoadedAtMs < MAIN_MENU_CACHE_MS
                && !popularBooks.isEmpty()
                && !newBooks.isEmpty()
                && searchAllBooksCache != null) {
            return;
        }
        String baseUrl = ApiConfig.baseUrl(requireContext().getApplicationContext());
        executor.execute(() -> {
            try {
                LibBookApiClient.HttpResult popularR =
                        LibBookApiClient.getPopularBooks(baseUrl, POPULAR_BOOKS_LIMIT);
                List<NewBook> popularParsed = popularR.statusCode == 200
                        ? parseNewBooks(popularR.body) : Collections.emptyList();

                List<NewBook> newParsed = Collections.emptyList();
                List<SearchBook> searchParsed = Collections.emptyList();
                LibBookApiClient.HttpResult booksR = LibBookApiClient.getBooks(baseUrl);
                if (booksR.statusCode == 200) {
                    newParsed = parseNewBooks(booksR.body);
                    searchParsed = parseSearchBooks(booksR.body);
                }

                final List<NewBook> popularFinal = popularParsed;
                final List<NewBook> newFinal = newParsed;
                final List<SearchBook> searchFinal = searchParsed;
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    popularBooks.clear();
                    popularBooks.addAll(popularFinal);
                    popularAdapter.notifyDataSetChanged();
                    newBooks.clear();
                    newBooks.addAll(newFinal);
                    newBooksAdapter.notifyDataSetChanged();
                    searchAllBooksCache = searchFinal;
                    mainMenuServerLoadedAtMs = System.currentTimeMillis();
                    updateSearchSuggestions(
                            searchBar.getText() != null ? searchBar.getText().toString().trim() : "");
                });
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Откладывает обновление подсказок поиска, чтобы не дергать UI на каждый символ.
     *
     * @param queryRaw необработанный текст запроса
     */
    private void scheduleSearchSuggestions(@NonNull String queryRaw) {
        String query = queryRaw.trim();
        if (searchDebounce != null) {
            mainHandler.removeCallbacks(searchDebounce);
        }
        searchDebounce = () -> updateSearchSuggestions(query);
        mainHandler.postDelayed(searchDebounce, 250);
    }

    /**
     * Фильтрует кэш книг и показывает подсказки по введённому запросу.
     *
     * @param queryRaw текст поискового запроса
     */
    private void updateSearchSuggestions(@NonNull String queryRaw) {
        if (!isAdded()) return;
        String q = queryRaw.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            searchSuggestions.clear();
            searchSuggestionsAdapter.notifyDataSetChanged();
            listSearchSuggestions.setVisibility(View.GONE);
            return;
        }
        List<SearchBook> source = searchAllBooksCache;
        if (source == null) {

            listSearchSuggestions.setVisibility(View.GONE);
            return;
        }
        searchSuggestions.clear();
        for (SearchBook b : source) {
            if (b.titleLower.contains(q)) {
                searchSuggestions.add(b);
                if (searchSuggestions.size() >= SEARCH_SUGGESTIONS_LIMIT) break;
            }
        }
        searchSuggestionsAdapter.notifyDataSetChanged();
        listSearchSuggestions.setVisibility(searchSuggestions.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * Разбирает JSON-каталог книг для поисковых подсказок.
     *
     * @param body тело HTTP-ответа с массивом книг
     * @return отсортированный список книг для поиска
     * @throws Exception при ошибке разбора JSON
     */
    @NonNull
    private static List<SearchBook> parseSearchBooks(@Nullable String body) throws Exception {
        if (body == null || body.trim().isEmpty()) return Collections.emptyList();
        JSONArray arr = new JSONArray(body);
        List<SearchBook> out = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject b = arr.getJSONObject(i);
            long id = b.optLong("id", -1L);
            String name = b.optString("name", "").trim();
            if (name.isEmpty()) name = "—";
            double avg = b.isNull("avgRating") ? Double.NaN : b.optDouble("avgRating", Double.NaN);
            int cnt = b.optInt("ratingsCount", 0);
            byte[] icon = decodeIconForList(b);
            out.add(new SearchBook(id, name, icon, avg, cnt));
        }

        out.sort((a, b) -> {
            int byAvg = Double.compare(
                    Double.isNaN(b.avgRating) ? -1 : b.avgRating,
                    Double.isNaN(a.avgRating) ? -1 : a.avgRating
            );
            if (byAvg != 0) return byAvg;
            int byCnt = Integer.compare(b.ratingsCount, a.ratingsCount);
            if (byCnt != 0) return byCnt;
            return Long.compare(b.id, a.id);
        });
        return out;
    }

    /**
     * Загружает из локальной базы книги, которые пользователь недавно открывал.
     */
    private void loadContinueReading() {
        try (BookDatabaseHelper db = new BookDatabaseHelper(requireContext().getApplicationContext())) {
            continueReadingBooks.clear();
            continueReadingBooks.addAll(db.listOpenedBooksByLastOpenedDesc());
        }
        continueReadingAdapter.notifyDataSetChanged();
    }

    /**
     * Разбирает JSON-каталог книг для списков «Новое» и «Популярное».
     *
     * @param body тело HTTP-ответа с массивом книг
     * @return список книг, ограниченный {@link #NEW_BOOKS_LIMIT}
     * @throws Exception при ошибке разбора JSON
     */
    @NonNull
    private static List<NewBook> parseNewBooks(@Nullable String body) throws Exception {
        if (body == null || body.trim().isEmpty()) return Collections.emptyList();
        JSONArray arr = new JSONArray(body);
        List<NewBook> out = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject b = arr.getJSONObject(i);
            long id = b.optLong("id", -1L);
            String name = b.optString("name", "").trim();
            if (name.isEmpty()) name = "—";
            byte[] icon = decodeIconForList(b);
            double avg = BookDisplayHelper.parseAvgRating(b);
            out.add(new NewBook(id, name, icon, avg));
        }
        out.sort((a, b) -> Long.compare(b.id, a.id));
        if (out.size() > NEW_BOOKS_LIMIT) {
            return new ArrayList<>(out.subList(0, NEW_BOOKS_LIMIT));
        }
        return out;
    }

    /**
     * Извлекает байты обложки книги из JSON-объекта.
     *
     * @param bookJson JSON-объект книги
     * @return байты иконки или {@code null}
     */
    @Nullable
    private static byte[] decodeIconForList(@NonNull JSONObject bookJson) {
        return UserResponseParser.decodeUserIcon(bookJson);
    }

    private static final class NewBook {
        final long id;
        @NonNull final String title;
        @Nullable final byte[] iconBytes;
        final double avgRating;

        /**
         * Создаёт модель книги для отображения в подборках.
         *
         * @param id         идентификатор книги
         * @param title      название книги
         * @param iconBytes  байты обложки
         * @param avgRating  средняя оценка
         */
        NewBook(long id, @NonNull String title, @Nullable byte[] iconBytes, double avgRating) {
            this.id = id;
            this.title = title;
            this.iconBytes = iconBytes;
            this.avgRating = avgRating;
        }
    }

    private static final class SearchBook {
        final long id;
        @NonNull final String title;
        @NonNull final String titleLower;
        @Nullable final byte[] iconBytes;
        final double avgRating;
        final int ratingsCount;

        /**
         * Создаёт модель книги для поисковых подсказок.
         *
         * @param id           идентификатор книги
         * @param title        название книги
         * @param iconBytes    байты обложки
         * @param avgRating    средняя оценка
         * @param ratingsCount количество оценок
         */
        SearchBook(long id, @NonNull String title, @Nullable byte[] iconBytes, double avgRating, int ratingsCount) {
            this.id = id;
            this.title = title;
            this.titleLower = title.toLowerCase(Locale.ROOT);
            this.iconBytes = iconBytes;
            this.avgRating = avgRating;
            this.ratingsCount = ratingsCount;
        }
    }

    private final class SearchSuggestionsAdapter extends RecyclerView.Adapter<SearchSuggestionsAdapter.VH> {
        /**
         * Создаёт строку подсказки поиска.
         *
         * @param parent   контейнер RecyclerView
         * @param viewType тип элемента
         * @return держатель строки подсказки
         */
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_book_row, parent, false);
            return new VH(row);
        }

        /**
         * Привязывает книгу к строке подсказки поиска.
         *
         * @param holder   держатель представления
         * @param position позиция в списке подсказок
         */
        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(searchSuggestions.get(position));
        }

        /**
         * Возвращает количество текущих поисковых подсказок.
         *
         * @return размер списка {@link #searchSuggestions}
         */
        @Override
        public int getItemCount() {
            return searchSuggestions.size();
        }

        final class VH extends RecyclerView.ViewHolder {
            private final ImageView cover;
            private final TextView title;

            /**
             * Создаёт держатель строки подсказки поиска.
             *
             * @param itemView корневое представление строки
             */
            VH(@NonNull View itemView) {
                super(itemView);
                cover = itemView.findViewById(R.id.ivRowCover);
                title = itemView.findViewById(R.id.tvRowTitle);
            }

            /**
             * Отображает книгу в подсказке и открывает её страницу по нажатию.
             *
             * @param book модель книги для поиска
             */
            void bind(@NonNull SearchBook book) {
                title.setText(book.title);
                if (book.iconBytes != null && book.iconBytes.length > 0) {
                    Bitmap bm = BitmapFactory.decodeByteArray(book.iconBytes, 0, book.iconBytes.length);
                    if (bm != null) {
                        cover.setImageBitmap(bm);
                    } else {
                        cover.setImageResource(R.drawable.icon_null);
                    }
                } else {
                    cover.setImageResource(R.drawable.icon_null);
                }
                itemView.setOnClickListener(v -> {

                    listSearchSuggestions.setVisibility(View.GONE);
                    searchBar.clearFocus();
                    Bundle args = new Bundle();
                    args.putLong(MainBookPageFragment.ARG_BOOK_ID, book.id);
                    NavHostFragment.findNavController(MainMenuFragment.this)
                            .navigate(R.id.action_mainMenuFragment_to_mainBookPageFragment, args);
                });
            }
        }
    }

    private final class NewBooksAdapter extends RecyclerView.Adapter<NewBooksAdapter.VH> {

        /**
         * Создаёт строку списка новых книг.
         *
         * @param parent   контейнер RecyclerView
         * @param viewType тип элемента
         * @return держатель строки новой книги
         */
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_book_row, parent, false);
            return new VH(row);
        }

        /**
         * Привязывает новую книгу к строке списка.
         *
         * @param holder   держатель представления
         * @param position позиция в списке новых книг
         */
        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(newBooks.get(position));
        }

        /**
         * Возвращает количество новых книг в списке.
         *
         * @return размер списка {@link #newBooks}
         */
        @Override
        public int getItemCount() {
            return newBooks.size();
        }

        final class VH extends RecyclerView.ViewHolder {
            private final ImageView cover;
            private final TextView title;

            /**
             * Создаёт держатель строки новой книги.
             *
             * @param itemView корневое представление строки
             */
            VH(@NonNull View itemView) {
                super(itemView);
                cover = itemView.findViewById(R.id.ivRowCover);
                title = itemView.findViewById(R.id.tvRowTitle);
            }

            /**
             * Отображает новую книгу и открывает её страницу по нажатию.
             *
             * @param book модель новой книги
             */
            void bind(@NonNull NewBook book) {
                title.setText(book.title);
                if (book.iconBytes != null && book.iconBytes.length > 0) {
                    Bitmap bm = BitmapFactory.decodeByteArray(book.iconBytes, 0, book.iconBytes.length);
                    if (bm != null) {
                        cover.setImageBitmap(bm);
                    } else {
                        cover.setImageResource(R.drawable.icon_null);
                    }
                } else {
                    cover.setImageResource(R.drawable.icon_null);
                }
                itemView.setOnClickListener(v -> {
                    Bundle args = new Bundle();
                    args.putLong(MainBookPageFragment.ARG_BOOK_ID, book.id);
                    NavHostFragment.findNavController(MainMenuFragment.this)
                            .navigate(R.id.action_mainMenuFragment_to_mainBookPageFragment, args);
                });
            }
        }
    }

    private final class PopularAdapter extends RecyclerView.Adapter<PopularAdapter.VH> {

        private final int itemWidthPx;

        /**
         * Вычисляет ширину карточки популярной книги в пикселях.
         */
        PopularAdapter() {
            itemWidthPx = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    112f,
                    requireContext().getResources().getDisplayMetrics()
            );
        }

        /**
         * Создаёт карточку популярной книги фиксированной ширины.
         *
         * @param parent   контейнер RecyclerView
         * @param viewType тип элемента
         * @return держатель карточки популярной книги
         */
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_book, parent, false);
            ViewGroup.LayoutParams lp = row.getLayoutParams();
            if (lp == null) {
                lp = new RecyclerView.LayoutParams(itemWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
            } else {
                lp.width = itemWidthPx;
            }
            row.setLayoutParams(lp);
            return new VH(row);
        }

        /**
         * Привязывает популярную книгу к карточке списка.
         *
         * @param holder   держатель представления
         * @param position позиция в списке популярных книг
         */
        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(popularBooks.get(position));
        }

        /**
         * Возвращает количество популярных книг в списке.
         *
         * @return размер списка {@link #popularBooks}
         */
        @Override
        public int getItemCount() {
            return popularBooks.size();
        }

        final class VH extends RecyclerView.ViewHolder {
            private final ImageView cover;
            private final TextView title;

            /**
             * Создаёт держатель карточки популярной книги.
             *
             * @param itemView корневое представление карточки
             */
            VH(@NonNull View itemView) {
                super(itemView);
                cover = itemView.findViewById(R.id.ivCatalogCover);
                title = itemView.findViewById(R.id.tvCatalogTitle);
            }

            /**
             * Отображает популярную книгу с рейтингом и открывает её страницу по нажатию.
             *
             * @param book модель популярной книги
             */
            void bind(@NonNull NewBook book) {
                title.setText(book.title);
                TextView ratingBadge = itemView.findViewById(R.id.tvCatalogRating);
                BookDisplayHelper.bindCatalogRatingBadge(ratingBadge, book.avgRating);
                if (book.iconBytes != null && book.iconBytes.length > 0) {
                    Bitmap bm = BitmapFactory.decodeByteArray(book.iconBytes, 0, book.iconBytes.length);
                    if (bm != null) {
                        cover.setImageBitmap(bm);
                    } else {
                        cover.setImageResource(R.drawable.icon_null);
                    }
                } else {
                    cover.setImageResource(R.drawable.icon_null);
                }
                itemView.setOnClickListener(v -> {
                    Bundle args = new Bundle();
                    args.putLong(MainBookPageFragment.ARG_BOOK_ID, book.id);
                    NavHostFragment.findNavController(MainMenuFragment.this)
                            .navigate(R.id.action_mainMenuFragment_to_mainBookPageFragment, args);
                });
            }
        }
    }

    private final class ContinueReadingAdapter extends RecyclerView.Adapter<ContinueReadingAdapter.VH> {

        private final int itemWidthPx;

        /**
         * Вычисляет ширину карточки книги «Продолжить чтение» в пикселях.
         */
        ContinueReadingAdapter() {
            itemWidthPx = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    112f,
                    requireContext().getResources().getDisplayMetrics()
            );
        }

        /**
         * Создаёт карточку книги для блока «Продолжить чтение».
         *
         * @param parent   контейнер RecyclerView
         * @param viewType тип элемента
         * @return держатель карточки продолжаемой книги
         */
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_book, parent, false);
            ViewGroup.LayoutParams lp = row.getLayoutParams();
            if (lp == null) {
                lp = new RecyclerView.LayoutParams(itemWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
            } else {
                lp.width = itemWidthPx;
            }
            row.setLayoutParams(lp);
            return new VH(row);
        }

        /**
         * Привязывает локальную закладку к карточке «Продолжить чтение».
         *
         * @param holder   держатель представления
         * @param position позиция в списке продолжаемых книг
         */
        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(continueReadingBooks.get(position));
        }

        /**
         * Возвращает количество книг в блоке «Продолжить чтение».
         *
         * @return размер списка {@link #continueReadingBooks}
         */
        @Override
        public int getItemCount() {
            return continueReadingBooks.size();
        }

        final class VH extends RecyclerView.ViewHolder {
            private final ImageView cover;
            private final TextView title;

            /**
             * Создаёт держатель карточки продолжаемой книги.
             *
             * @param itemView корневое представление карточки
             */
            VH(@NonNull View itemView) {
                super(itemView);
                cover = itemView.findViewById(R.id.ivCatalogCover);
                title = itemView.findViewById(R.id.tvCatalogTitle);
            }

            /**
             * Отображает ранее открытую книгу и переходит на её страницу по нажатию.
             *
             * @param book строка закладки из локальной базы
             */
            void bind(@NonNull BookDatabaseHelper.BookRow book) {
                String name = book.name != null && !book.name.isEmpty() ? book.name : "—";
                title.setText(name);
                if (book.icon != null && book.icon.length > 0) {
                    Bitmap bm = BitmapFactory.decodeByteArray(book.icon, 0, book.icon.length);
                    if (bm != null) {
                        cover.setImageBitmap(bm);
                    } else {
                        cover.setImageResource(R.drawable.icon_null);
                    }
                } else {
                    cover.setImageResource(R.drawable.icon_null);
                }
                itemView.setOnClickListener(v -> {
                    Bundle args = new Bundle();
                    args.putLong(MainBookPageFragment.ARG_BOOK_ID, book.id);
                    NavHostFragment.findNavController(MainMenuFragment.this)
                            .navigate(R.id.action_mainMenuFragment_to_mainBookPageFragment, args);
                });
            }
        }
    }
}
