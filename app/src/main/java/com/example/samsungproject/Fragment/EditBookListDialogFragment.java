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

import com.example.samsungproject.R;
import com.example.samsungproject.net.ApiConfig;
import com.example.samsungproject.net.LibBookApiClient;
import com.example.samsungproject.util.UserResponseParser;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class EditBookListDialogFragment extends DialogFragment {

    public static final String ARG_USER_ID = "userId";

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private final List<UploadedBookItem> books = new ArrayList<>();

    /** Создаёт экземпляр диалога выбора книги для редактирования. */
    public EditBookListDialogFragment() {
        super(R.layout.entereditbooklist);
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
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    /** Настраивает список книг для редактирования и обработчики кнопок. */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.ecDimBackground).setOnClickListener(v -> dismissToGeneral());

        RecyclerView recycler = view.findViewById(R.id.rvEditBooks);
        ProgressBar progress = view.findViewById(R.id.pbEditBooks);
        TextView emptyView = view.findViewById(R.id.tvEditBooksEmpty);
        MaterialButton btnCancel = view.findViewById(R.id.btnEditBookListCancel);

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        BooksAdapter adapter = new BooksAdapter();
        recycler.setAdapter(adapter);

        btnCancel.setOnClickListener(v -> dismissToGeneral());

        Bundle args = getArguments();
        long userId = args != null ? args.getLong(ARG_USER_ID, -1L) : -1L;
        if (userId < 0) {
            Toast.makeText(requireContext(), R.string.edit_book_not_logged_in, Toast.LENGTH_SHORT).show();
            dismissToGeneral();
            return;
        }

        loadUploadedBooks(userId, progress, emptyView, recycler, adapter);
    }

    /** Загружает список книг, загруженных пользователем, с сервера. */
    private void loadUploadedBooks(
            long userId,
            ProgressBar progress,
            TextView emptyView,
            RecyclerView recycler,
            BooksAdapter adapter
    ) {
        progress.setVisibility(View.VISIBLE);
        recycler.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);

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
                    progress.setVisibility(View.GONE);
                    books.clear();
                    books.addAll(loaded);
                    adapter.notifyDataSetChanged();
                    if (books.isEmpty()) {
                        emptyView.setVisibility(View.VISIBLE);
                        recycler.setVisibility(View.GONE);
                    } else {
                        emptyView.setVisibility(View.GONE);
                        recycler.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    progress.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), R.string.register_network_error, Toast.LENGTH_SHORT).show();
                    dismissToGeneral();
                });
            }
        });
    }

    /** Открывает форму редактирования выбранной книги. */
    private void openEditForm(@NonNull UploadedBookItem book) {
        Bundle args = new Bundle();
        args.putLong(EditBookFormDialogFragment.ARG_BOOK_ID, book.id);
        args.putString(EditBookFormDialogFragment.ARG_BOOK_NAME, book.name);
        Bundle current = getArguments();
        if (current != null) {
            args.putLong(EditBookFormDialogFragment.ARG_USER_ID, current.getLong(ARG_USER_ID, -1L));
        }
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_editBookListDialogFragment_to_editBookFormDialogFragment, args);
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

        /** Создаёт элемент списка для выбора книги на редактирование. */
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

        /** Инициализирует представления элемента списка редактирования. */
        BookVH(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.ivDelCover);
            checked = itemView.findViewById(R.id.ivDelChecked);
            title = itemView.findViewById(R.id.tvDelTitle);
        }

        /** Заполняет элемент данными книги и настраивает переход к форме редактирования. */
        void bind(@NonNull UploadedBookItem book) {
            title.setText(book.name);
            checked.setVisibility(View.GONE);
            itemView.setSelected(false);
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
            itemView.setOnClickListener(v -> openEditForm(book));
        }
    }
}
