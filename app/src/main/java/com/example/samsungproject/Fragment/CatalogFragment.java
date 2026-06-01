package com.example.samsungproject.Fragment;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.samsungproject.R;
import com.example.samsungproject.net.ApiConfig;
import com.example.samsungproject.net.LibBookApiClient;
import com.example.samsungproject.util.BookDisplayHelper;
import com.example.samsungproject.util.UserResponseParser;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CatalogFragment extends Fragment {

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<CatalogBook> allBooks = new ArrayList<>();
    private final List<CatalogBook> visibleBooks = new ArrayList<>();
    private CatalogBooksAdapter adapter;
    private AppCompatEditText searchBar;
    private TextView tvEmptyState;
    private RecyclerView recycler;

    @Nullable private String[] allTags;
    @Nullable private boolean[] checkedTags;
    @NonNull private final ArrayList<String> selectedTags = new ArrayList<>();

    /** Создаёт экземпляр фрагмента каталога книг. */
    public CatalogFragment() {
        super(R.layout.activity_catalog);
    }

    /** Настраивает список книг, поиск и навигацию по разделам. */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton btnMarkbooks = view.findViewById(R.id.btn_cgMarkbooks);
        btnMarkbooks.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_catalogFragment_to_markbooksFragment)
        );

        MaterialButton btnMainMenu = view.findViewById(R.id.btn_cgMainMenu);
        btnMainMenu.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_catalogFragment_to_mainMenuFragment)
        );

        MaterialButton btnGeneral = view.findViewById(R.id.btn_cgGeneral);
        btnGeneral.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_catalogFragment_to_generalFragment)
        );

        MaterialButton btnFilter = view.findViewById(R.id.btn_Filter);
        btnFilter.setOnClickListener(v -> showTagsFilterDialog());

        RecyclerView recycler = view.findViewById(R.id.recyclerCenter);
        this.recycler = recycler;
        tvEmptyState = view.findViewById(R.id.tvEmptyState);
        recycler.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        recycler.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        adapter = new CatalogBooksAdapter();
        recycler.setAdapter(adapter);

        searchBar = view.findViewById(R.id.searchBar);
        searchBar.addTextChangedListener(new TextWatcher() {
            /** Вызывается перед изменением текста в поле поиска. */
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            /** Вызывается при изменении текста в поле поиска. */
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            /** Применяет фильтр после изменения текста поиска. */
            @Override
            public void afterTextChanged(Editable s) {
                applySearchFilter(s != null ? s.toString() : "");
            }
        });
    }

    /** Обновляет список книг при возвращении фрагмента на экран. */
    @Override
    public void onResume() {
        super.onResume();
        loadBooksFromServer();
    }

    /** Загружает каталог книг с сервера в фоновом потоке. */
    private void loadBooksFromServer() {
        String baseUrl = ApiConfig.baseUrl(requireContext().getApplicationContext());
        executor.execute(() -> {
            try {
                LibBookApiClient.HttpResult r = LibBookApiClient.getBooks(baseUrl);
                if (r.statusCode != 200) {
                    mainHandler.post(() -> Toast.makeText(requireContext(), R.string.catalog_load_failed, Toast.LENGTH_SHORT).show());
                    return;
                }
                List<CatalogBook> parsed = parseBooksJson(r.body);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    allBooks.clear();
                    allBooks.addAll(parsed);
                    applySearchFilter(searchBar.getText() != null ? searchBar.getText().toString() : "");
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.register_network_error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /** Разбирает JSON-ответ сервера в список книг каталога. */
    @NonNull
    private static List<CatalogBook> parseBooksJson(@Nullable String body) throws Exception {
        if (body == null || body.trim().isEmpty()) {
            return Collections.emptyList();
        }
        JSONArray arr = new JSONArray(body);
        List<CatalogBook> out = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject b = arr.getJSONObject(i);
            long id = b.optLong("id", -1L);
            String name = b.optString("name", "").trim();
            if (name.isEmpty()) {
                name = "—";
            }
            byte[] iconBytes = decodeIconForList(b);
            ArrayList<String> tags = new ArrayList<>();
            JSONArray tagsArr = b.optJSONArray("tags");
            if (tagsArr != null) {
                for (int t = 0; t < tagsArr.length(); t++) {
                    String tag = tagsArr.optString(t, "").trim();
                    if (!tag.isEmpty()) tags.add(tag);
                }
            }
            double avg = BookDisplayHelper.parseAvgRating(b);
            out.add(new CatalogBook(id, name, iconBytes, tags, avg));
        }
        Collections.sort(out, (a, b) -> Long.compare(a.id, b.id));
        return out;
    }

    /** Декодирует обложку книги из JSON для отображения в списке. */
    @Nullable
    private static byte[] decodeIconForList(@NonNull JSONObject bookJson) {
        return UserResponseParser.decodeUserIcon(bookJson);
    }

    /** Фильтрует книги по строке поиска и выбранным тегам. */
    private void applySearchFilter(@NonNull String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        visibleBooks.clear();
        boolean hasTagFilter = !selectedTags.isEmpty();
        Set<String> selected = hasTagFilter ? new HashSet<>(selectedTags) : null;
        for (CatalogBook book : allBooks) {
            boolean matchesQuery = q.isEmpty() || book.title.toLowerCase(Locale.ROOT).contains(q);
            if (!matchesQuery) continue;

            if (hasTagFilter) {
                boolean matchesAny = false;
                for (String t : book.tags) {
                    if (selected.contains(t)) {
                        matchesAny = true;
                        break;
                    }
                }
                if (!matchesAny) continue;
            }
            visibleBooks.add(book);
        }
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    /** Обновляет видимость пустого состояния и списка книг. */
    private void updateEmptyState() {
        if (tvEmptyState == null) {
            return;
        }
        boolean empty = allBooks.isEmpty();
        tvEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (recycler != null) {
            recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
    }

    private static final class CatalogBook {
        final long id;
        @NonNull final String title;
        @Nullable final byte[] iconBytes;
        @NonNull final List<String> tags;
        final double avgRating;

        /** Создаёт элемент каталога с данными книги. */
        CatalogBook(long id, @NonNull String title, @Nullable byte[] iconBytes, @NonNull List<String> tags, double avgRating) {
            this.id = id;
            this.title = title;
            this.iconBytes = iconBytes;
            this.tags = tags;
            this.avgRating = avgRating;
        }
    }

    private final class CatalogBooksAdapter extends RecyclerView.Adapter<CatalogBooksAdapter.VH> {

        /** Создаёт элемент списка каталога. */
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_book, parent, false);
            return new VH(row);
        }

        /** Привязывает данные книги к элементу списка. */
        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(visibleBooks.get(position));
        }

        /** Возвращает количество видимых книг в каталоге. */
        @Override
        public int getItemCount() {
            return visibleBooks.size();
        }

        final class VH extends RecyclerView.ViewHolder {
            private final ImageView cover;
            private final TextView title;
            private final TextView ratingBadge;

            /** Инициализирует представления элемента каталога. */
            VH(@NonNull View itemView) {
                super(itemView);
                cover = itemView.findViewById(R.id.ivCatalogCover);
                title = itemView.findViewById(R.id.tvCatalogTitle);
                ratingBadge = itemView.findViewById(R.id.tvCatalogRating);
            }

            /** Заполняет элемент данными книги и настраивает переход на страницу книги. */
            void bind(@NonNull CatalogBook book) {
                title.setText(book.title);
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
                    NavHostFragment.findNavController(CatalogFragment.this)
                            .navigate(R.id.action_catalogFragment_to_mainBookPageFragment, args);
                });
            }
        }
    }

    /** Открывает диалог фильтрации по тегам с текущими настройками. */
    private void showTagsFilterDialog() {
        showTagsFilterDialog(null, null);
    }

    /** Открывает диалог фильтрации по тегам с заданным начальным состоянием. */
    private void showTagsFilterDialog(@Nullable boolean[] initialChecked, @Nullable List<String> initialSelected) {
        if (allTags == null) {
            allTags = requireContext().getResources().getStringArray(R.array.book_tags);
        }
        if (checkedTags == null) {
            checkedTags = new boolean[allTags.length];
        }

        boolean[] tmpChecked = initialChecked != null
                ? Arrays.copyOf(initialChecked, initialChecked.length)
                : Arrays.copyOf(checkedTags, checkedTags.length);
        List<String> tmpSelected = initialSelected != null
                ? new ArrayList<>(initialSelected)
                : new ArrayList<>(selectedTags);


        LinearLayout titleRow = new LinearLayout(requireContext());
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        int pad = (int) (requireContext().getResources().getDisplayMetrics().density * 8);
        titleRow.setPadding(pad, pad, pad, 0);

        TextView tv = new TextView(requireContext());
        tv.setText(R.string.add_book_hint_tags);
        tv.setTextSize(20);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(tv);

        MaterialButton btnClear = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnClear.setText("");
        btnClear.setIconResource(android.R.drawable.ic_menu_delete);
        btnClear.setIconTintResource(android.R.color.holo_red_dark);
        btnClear.setStrokeColorResource(android.R.color.holo_red_dark);
        btnClear.setStrokeWidth((int) (requireContext().getResources().getDisplayMetrics().density * 1));
        btnClear.setCornerRadius((int) (requireContext().getResources().getDisplayMetrics().density * 12));
        btnClear.setMinWidth(0);
        btnClear.setMinimumWidth(0);
        btnClear.setPadding(pad, pad, pad, pad);
        titleRow.addView(btnClear);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setCustomTitle(titleRow)
                .setMultiChoiceItems(allTags, tmpChecked, (dialog, which, isChecked) -> {
                    String tag = allTags[which];
                    if (isChecked) {
                        if (!tmpSelected.contains(tag)) tmpSelected.add(tag);
                    } else {
                        tmpSelected.remove(tag);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.add_book_confirm, (dialog, which) -> {
                    checkedTags = tmpChecked;
                    selectedTags.clear();
                    selectedTags.addAll(tmpSelected);
                    applySearchFilter(searchBar != null && searchBar.getText() != null ? searchBar.getText().toString() : "");
                });

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        btnClear.setOnClickListener(v -> {
            dialog.dismiss();
            boolean[] cleared = new boolean[allTags.length];
            showTagsFilterDialog(cleared, new ArrayList<>());
        });
        dialog.show();
    }
}
