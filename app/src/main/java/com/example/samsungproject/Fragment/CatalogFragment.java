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
import com.example.samsungproject.net.SamsungApiClient;
import com.example.samsungproject.util.UserResponseParser;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CatalogFragment extends Fragment {

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<CatalogBook> allBooks = new ArrayList<>();
    private final List<CatalogBook> visibleBooks = new ArrayList<>();
    private CatalogBooksAdapter adapter;
    private AppCompatEditText searchBar;

    public CatalogFragment() {
        super(R.layout.activity_catalog);
    }

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

        RecyclerView recycler = view.findViewById(R.id.recyclerCenter);
        recycler.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        recycler.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        adapter = new CatalogBooksAdapter();
        recycler.setAdapter(adapter);

        searchBar = view.findViewById(R.id.searchBar);
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applySearchFilter(s != null ? s.toString() : "");
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadBooksFromServer();
    }

    private void loadBooksFromServer() {
        String baseUrl = getString(R.string.api_base_url);
        executor.execute(() -> {
            try {
                SamsungApiClient.HttpResult r = SamsungApiClient.getBooks(baseUrl);
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
            byte[] iconBytes = UserResponseParser.decodeUserIcon(b);
            out.add(new CatalogBook(id, name, iconBytes));
        }
        Collections.sort(out, (a, b) -> Long.compare(a.id, b.id));
        return out;
    }

    private void applySearchFilter(@NonNull String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        visibleBooks.clear();
        if (q.isEmpty()) {
            visibleBooks.addAll(allBooks);
        } else {
            for (CatalogBook book : allBooks) {
                if (book.title.toLowerCase(Locale.ROOT).contains(q)) {
                    visibleBooks.add(book);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private static final class CatalogBook {
        final long id;
        @NonNull final String title;
        @Nullable final byte[] iconBytes;

        CatalogBook(long id, @NonNull String title, @Nullable byte[] iconBytes) {
            this.id = id;
            this.title = title;
            this.iconBytes = iconBytes;
        }
    }

    private final class CatalogBooksAdapter extends RecyclerView.Adapter<CatalogBooksAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_catalog_book, parent, false);
            return new VH(row);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(visibleBooks.get(position));
        }

        @Override
        public int getItemCount() {
            return visibleBooks.size();
        }

        final class VH extends RecyclerView.ViewHolder {
            private final ImageView cover;
            private final TextView title;

            VH(@NonNull View itemView) {
                super(itemView);
                cover = itemView.findViewById(R.id.ivCatalogCover);
                title = itemView.findViewById(R.id.tvCatalogTitle);
            }

            void bind(@NonNull CatalogBook book) {
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
                    NavHostFragment.findNavController(CatalogFragment.this)
                            .navigate(R.id.action_catalogFragment_to_mainBookPageFragment, args);
                });
            }
        }
    }
}
