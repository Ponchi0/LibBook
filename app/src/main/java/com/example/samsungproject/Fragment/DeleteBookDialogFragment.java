package com.example.samsungproject.Fragment;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.samsungproject.LocalDB.BookDatabaseHelper;
import com.example.samsungproject.R;
import com.example.samsungproject.net.ApiConfig;
import com.example.samsungproject.net.LibBookApiClient;
import com.example.samsungproject.util.UserResponseParser;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class DeleteBookDialogFragment extends DialogFragment {

    public static final String ARG_USER_ID = "userId";

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private final List<UploadedBookItem> books = new ArrayList<>();
    private final Set<Long> selectedIds = new HashSet<>();

    private RecyclerView recycler;
    private ProgressBar progress;
    private TextView emptyView;
    private MaterialButton btnDelete;
    private TextInputLayout tilPassword;
    private TextInputEditText etPassword;
    private BooksAdapter adapter;

    /** Создаёт экземпляр диалога удаления загруженных книг. */
    public DeleteBookDialogFragment() {
        super(R.layout.enterdeletebook);
    }

    /** Инициализирует стиль диалога без заголовка. */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, 0);
    }

    /** Растягивает окно диалога на весь экран с прозрачным фоном. */
    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            Window window = getDialog().getWindow();
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    /** Настраивает список книг для удаления и обработчики кнопок. */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.ecDimBackground).setOnClickListener(v -> dismissToGeneral());

        recycler = view.findViewById(R.id.rvDeleteBooks);
        progress = view.findViewById(R.id.pbDeleteBooks);
        emptyView = view.findViewById(R.id.tvDeleteBooksEmpty);
        btnDelete = view.findViewById(R.id.btnDeleteBooksConfirm);
        tilPassword = view.findViewById(R.id.tilDeleteBooksPassword);
        etPassword = view.findViewById(R.id.etDeleteBooksPassword);
        MaterialButton btnCancel = view.findViewById(R.id.btnDeleteBooksCancel);

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new BooksAdapter();
        recycler.setAdapter(adapter);

        btnCancel.setOnClickListener(v -> dismissToGeneral());
        btnDelete.setOnClickListener(v -> deleteSelectedOnServer());

        Bundle args = getArguments();
        long userId = args != null ? args.getLong(ARG_USER_ID, -1L) : -1L;
        if (userId < 0) {
            Toast.makeText(requireContext(), R.string.delete_book_not_logged_in, Toast.LENGTH_SHORT).show();
            dismissToGeneral();
            return;
        }

        loadUploadedBooks(userId);
    }

    /** Загружает список книг, загруженных пользователем, с сервера. */
    private void loadUploadedBooks(long userId) {
        showLoading(true);
        String baseUrl = ApiConfig.baseUrl(requireContext().getApplicationContext());
        executor.execute(() -> {
            try {
                LibBookApiClient.HttpResult r = LibBookApiClient.getUserUploadedBooks(baseUrl, userId);
                List<UploadedBookItem> loaded = new ArrayList<>();
                if (r.statusCode == 200) {
                    JSONArray arr = new JSONArray(r.body);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject book = arr.getJSONObject(i);
                        long id = book.optLong("id", -1L);
                        if (id < 0) {
                            continue;
                        }
                        String name = book.optString("name", "").trim();
                        byte[] icon = UserResponseParser.decodeUserIcon(book);
                        loaded.add(new UploadedBookItem(id, name.isEmpty() ? "—" : name, icon));
                    }
                }
                mainHandler.post(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    showLoading(false);
                    books.clear();
                    selectedIds.clear();
                    books.addAll(loaded);
                    adapter.notifyDataSetChanged();
                    if (books.isEmpty()) {
                        emptyView.setVisibility(View.VISIBLE);
                        recycler.setVisibility(View.GONE);
                        btnDelete.setEnabled(false);
                    } else {
                        emptyView.setVisibility(View.GONE);
                        recycler.setVisibility(View.VISIBLE);
                        btnDelete.setEnabled(true);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    showLoading(false);
                    Toast.makeText(requireContext(), R.string.register_network_error, Toast.LENGTH_SHORT).show();
                    dismissToGeneral();
                });
            }
        });
    }

    /** Удаляет выбранные книги на сервере после проверки пароля. */
    private void deleteSelectedOnServer() {
        if (selectedIds.isEmpty()) {
            Toast.makeText(requireContext(), R.string.delete_book_none_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
        if (password.isEmpty()) {
            tilPassword.setError(getString(R.string.field_required));
            return;
        }
        tilPassword.setError(null);

        Bundle args = getArguments();
        long userId = args != null ? args.getLong(ARG_USER_ID, -1L) : -1L;
        if (userId < 0) {
            return;
        }

        long[] ids = new long[selectedIds.size()];
        int i = 0;
        for (Long id : selectedIds) {
            ids[i++] = id;
        }

        btnDelete.setEnabled(false);
        String baseUrl = ApiConfig.baseUrl(requireContext().getApplicationContext());
        executor.execute(() -> {
            try {
                LibBookApiClient.HttpResult r =
                        LibBookApiClient.deleteBooksBatch(baseUrl, userId, password, ids);
                mainHandler.post(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    btnDelete.setEnabled(true);
                    if (r.statusCode == 200) {
                        try (BookDatabaseHelper db = new BookDatabaseHelper(
                                requireContext().getApplicationContext())) {
                            for (long bookId : ids) {
                                db.deleteBook(bookId);
                            }
                        }
                        Toast.makeText(requireContext(), R.string.delete_book_success, Toast.LENGTH_SHORT).show();
                        dismissToGeneral();
                    } else if (r.statusCode == 401) {
                        tilPassword.setError(getString(R.string.login_wrong_password));
                        Toast.makeText(requireContext(), R.string.login_wrong_password, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), R.string.register_network_error, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    btnDelete.setEnabled(true);
                    Toast.makeText(requireContext(), R.string.register_network_error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /** Показывает или скрывает индикатор загрузки списка книг. */
    private void showLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            recycler.setVisibility(View.GONE);
            emptyView.setVisibility(View.GONE);
        }
    }

    /** Закрывает диалог и возвращается на экран «Общее». */
    private void dismissToGeneral() {
        NavHostFragment.findNavController(this).popBackStack(R.id.generalFragment, false);
    }

    private static final class UploadedBookItem {
        final long id;
        @NonNull final String name;
        @Nullable final byte[] icon;

        /** Создаёт элемент списка загруженной книги. */
        UploadedBookItem(long id, @NonNull String name, @Nullable byte[] icon) {
            this.id = id;
            this.name = name;
            this.icon = icon;
        }
    }

    private final class BooksAdapter extends RecyclerView.Adapter<BookVH> {

        /** Создаёт элемент списка для выбора книги на удаление. */
        @NonNull
        @Override
        public BookVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_delete_book, parent, false);
            return new BookVH(row);
        }

        /** Привязывает данные книги к элементу списка. */
        @Override
        public void onBindViewHolder(@NonNull BookVH holder, int position) {
            holder.bind(books.get(position));
        }

        /** Возвращает количество книг в списке. */
        @Override
        public int getItemCount() {
            return books.size();
        }
    }

    private final class BookVH extends RecyclerView.ViewHolder {
        private final ImageView cover;
        private final View checked;
        private final TextView title;

        /** Инициализирует представления элемента списка удаления. */
        BookVH(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.ivDelCover);
            checked = itemView.findViewById(R.id.ivDelChecked);
            title = itemView.findViewById(R.id.tvDelTitle);
        }

        /** Заполняет элемент данными книги и настраивает выбор для удаления. */
        void bind(@NonNull UploadedBookItem book) {
            title.setText(book.name);
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

        /** Обновляет визуальное состояние выбранного элемента. */
        private void applySelection(boolean selected) {
            itemView.setSelected(selected);
            checked.setVisibility(selected ? View.VISIBLE : View.GONE);
        }
    }
}
