package com.example.samsungproject.Fragment;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.samsungproject.LocalDB.BookDatabaseHelper;
import com.example.samsungproject.R;
import com.example.samsungproject.util.BookmarkSyncHelper;
import com.example.samsungproject.util.SessionHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MarkbooksFragment extends Fragment {

    private final List<BookDatabaseHelper.BookRow> allBooks = new ArrayList<>();
    private final List<BookDatabaseHelper.BookRow> visibleBooks = new ArrayList<>();
    private MarkbooksAdapter adapter;
    private AppCompatEditText searchBar;
    private RecyclerView recycler;
    private TextView tvEmptyState;

    /**
     * Создаёт фрагмент экрана закладок пользователя.
     */
    public MarkbooksFragment() {
        super(R.layout.activity_markbooks);
    }

    /**
     * Настраивает навигацию, список закладок, поиск и удаление книг.
     *
     * @param view               корневое представление фрагмента
     * @param savedInstanceState сохранённое состояние или {@code null}
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton btnCatalog = view.findViewById(R.id.btn_mbCatalog);
        btnCatalog.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_markbooksFragment_to_catalogFragment)
        );

        MaterialButton btnMainMenu = view.findViewById(R.id.btn_mbMainMenu);
        btnMainMenu.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_markbooksFragment_to_mainMenuFragment)
        );

        MaterialButton btnGeneral = view.findViewById(R.id.btn_mbGeneral);
        btnGeneral.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_markbooksFragment_to_generalFragment)
        );

        recycler = view.findViewById(R.id.recyclerCenter);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);
        recycler.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LinearLayoutManager lm = new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false);
        lm.setReverseLayout(true);
        lm.setStackFromEnd(true);
        recycler.setLayoutManager(lm);
        adapter = new MarkbooksAdapter();
        recycler.setAdapter(adapter);

        searchBar = view.findViewById(R.id.searchBar);
        searchBar.addTextChangedListener(new TextWatcher() {
            /**
             * Не используется; требуется интерфейсом {@link TextWatcher}.
             */
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            /**
             * Не используется; требуется интерфейсом {@link TextWatcher}.
             */
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            /**
             * Применяет фильтр поиска после изменения текста в строке поиска.
             *
             * @param s текущий текст поиска
             */
            @Override
            public void afterTextChanged(Editable s) {
                applySearchFilter(s != null ? s.toString() : "");
            }
        });

        MaterialButton btnDelete = view.findViewById(R.id.btnDeleteBookmarks);
        btnDelete.setOnClickListener(v -> showDeleteDialog());
    }

    /**
     * Синхронизирует закладки с сервером или загружает их из локальной базы при возврате на экран.
     */
    @Override
    public void onResume() {
        super.onResume();
        if (!SessionHelper.isLoggedIn(requireContext())) {
            loadBookmarksFromLocalDb();
            return;
        }
        BookmarkSyncHelper.syncAsync(requireContext(), this::loadBookmarksFromLocalDb);
    }

    /**
     * Загружает закладки из локальной базы и применяет текущий фильтр поиска.
     */
    private void loadBookmarksFromLocalDb() {
        if (!SessionHelper.isLoggedIn(requireContext())) {
            try (BookDatabaseHelper db = new BookDatabaseHelper(requireContext().getApplicationContext())) {
                db.clearAllBookmarks();
            }
            allBooks.clear();
            applySearchFilter(searchBar != null && searchBar.getText() != null
                    ? searchBar.getText().toString() : "");
            return;
        }
        List<BookDatabaseHelper.BookRow> rows;
        try (BookDatabaseHelper db = new BookDatabaseHelper(requireContext().getApplicationContext())) {
            rows = db.listBookmarksByLastOpenedAsc();
        }
        allBooks.clear();
        allBooks.addAll(rows);
        applySearchFilter(searchBar != null && searchBar.getText() != null
                ? searchBar.getText().toString() : "");
    }

    /**
     * Фильтрует список закладок по названию книги.
     *
     * @param query строка поиска
     */
    private void applySearchFilter(@NonNull String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        visibleBooks.clear();
        if (q.isEmpty()) {
            visibleBooks.addAll(allBooks);
        } else {
            for (BookDatabaseHelper.BookRow book : allBooks) {
                String title = book.name != null ? book.name.toLowerCase(Locale.ROOT) : "";
                if (title.contains(q)) {
                    visibleBooks.add(book);
                }
            }
        }
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    /**
     * Показывает или скрывает сообщение о пустом списке закладок.
     */
    private void updateEmptyState() {
        if (tvEmptyState == null) {
            return;
        }
        boolean showEmptyMessage = allBooks.isEmpty();
        tvEmptyState.setVisibility(showEmptyMessage ? View.VISIBLE : View.GONE);
        if (recycler != null) {
            recycler.setVisibility(showEmptyMessage ? View.GONE : View.VISIBLE);
        }
    }

    private final class MarkbooksAdapter extends RecyclerView.Adapter<MarkbooksAdapter.VH> {

        /**
         * Создаёт элемент списка закладок.
         *
         * @param parent   контейнер RecyclerView
         * @param viewType тип элемента
         * @return держатель представления строки
         */
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_book_row, parent, false);
            return new VH(row);
        }

        /**
         * Привязывает данные закладки к элементу списка.
         *
         * @param holder   держатель представления
         * @param position позиция в отфильтрованном списке
         */
        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(visibleBooks.get(position));
        }

        /**
         * Возвращает количество видимых закладок после фильтрации.
         *
         * @return размер списка {@link #visibleBooks}
         */
        @Override
        public int getItemCount() {
            return visibleBooks.size();
        }

        final class VH extends RecyclerView.ViewHolder {
            private final ImageView cover;
            private final TextView title;

            /**
             * Создаёт держатель строки закладки.
             *
             * @param itemView корневое представление строки
             */
            VH(@NonNull View itemView) {
                super(itemView);
                cover = itemView.findViewById(R.id.ivRowCover);
                title = itemView.findViewById(R.id.tvRowTitle);
            }

            /**
             * Отображает обложку и название книги и открывает её страницу по нажатию.
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
                    NavHostFragment.findNavController(MarkbooksFragment.this)
                            .navigate(R.id.action_markbooksFragment_to_mainBookPageFragment, args);
                });
            }
        }
    }

    /**
     * Показывает диалог множественного выбора закладок для удаления.
     */
    private void showDeleteDialog() {
        if (!isAdded()) return;
        if (!SessionHelper.isLoggedIn(requireContext())) {
            SessionHelper.showLoginRequiredToast(requireContext());
            return;
        }
        if (allBooks.isEmpty()) {
            return;
        }


        List<BookDatabaseHelper.BookRow> rows = new ArrayList<>(allBooks);
        Set<Long> selectedIds = new HashSet<>();

        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
        RecyclerView.Adapter<?> delAdapter = new RecyclerView.Adapter<DeleteVH>() {
            /**
             * Создаёт строку выбора книги для удаления.
             *
             * @param parent   контейнер RecyclerView
             * @param viewType тип элемента
             * @return держатель строки удаления
             */
            @NonNull
            @Override
            public DeleteVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View row = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_delete_book, parent, false);
                return new DeleteVH(row);
            }

            /**
             * Привязывает книгу к строке диалога удаления.
             *
             * @param holder   держатель представления
             * @param position позиция в списке
             */
            @Override
            public void onBindViewHolder(@NonNull DeleteVH holder, int position) {
                holder.bind(rows.get(position), selectedIds);
            }

            /**
             * Возвращает количество закладок в диалоге удаления.
             *
             * @return размер списка для удаления
             */
            @Override
            public int getItemCount() {
                return rows.size();
            }
        };
        rv.setAdapter(delAdapter);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Удалить книги")
                .setView(rv)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Применить", (d, which) -> {
                    if (selectedIds.isEmpty()) return;
                    long[] ids = new long[selectedIds.size()];
                    int idx = 0;
                    for (Long id : selectedIds) {
                        ids[idx++] = id;
                    }
                    try (BookDatabaseHelper db = new BookDatabaseHelper(requireContext().getApplicationContext())) {
                        for (Long id : selectedIds) {
                            db.deleteBook(id);
                        }
                    }
                    BookmarkSyncHelper.deleteBookmarksAsync(requireContext(), ids);
                    loadBookmarksFromLocalDb();
                })
                .show();
    }

    private static final class DeleteVH extends RecyclerView.ViewHolder {
        private final ImageView cover;
        private final View checked;
        private final TextView title;

        /**
         * Создаёт держатель строки выбора книги для удаления.
         *
         * @param itemView корневое представление строки
         */
        DeleteVH(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.ivDelCover);
            checked = itemView.findViewById(R.id.ivDelChecked);
            title = itemView.findViewById(R.id.tvDelTitle);
        }

        /**
         * Отображает книгу и переключает её выбор для удаления.
         *
         * @param book        строка закладки
         * @param selectedIds множество выбранных идентификаторов книг
         */
        void bind(@NonNull BookDatabaseHelper.BookRow book, @NonNull Set<Long> selectedIds) {
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

            applySelection(selectedIds.contains(book.id));
            itemView.setOnClickListener(v -> {
                if (selectedIds.contains(book.id)) {
                    selectedIds.remove(book.id);
                } else {
                    selectedIds.add(book.id);
                }
                applySelection(selectedIds.contains(book.id));
            });
        }

        /**
         * Обновляет визуальное состояние выбранной строки.
         *
         * @param selected {@code true}, если книга отмечена для удаления
         */
        private void applySelection(boolean selected) {
            itemView.setSelected(selected);
            checked.setVisibility(selected ? View.VISIBLE : View.GONE);
        }
    }
}
