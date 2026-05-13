package com.example.samsungproject.Fragment;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.samsungproject.R;
import com.example.samsungproject.net.SamsungApiClient;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class BookPageFragment extends Fragment {

    public static final String ARG_BOOK_ID = "bookId";

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    public BookPageFragment() {
        super(R.layout.bookpage);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        long bookIdArg = -1L;
        Bundle args = getArguments();
        if (args != null) {
            bookIdArg = args.getLong(ARG_BOOK_ID, -1L);
        }

        MaterialButton btnPageTitle = view.findViewById(R.id.bpbtnPageTitle);
        btnPageTitle.setOnClickListener(v -> NavHostFragment.findNavController(BookPageFragment.this).popBackStack());

        TextView tvPageText = view.findViewById(R.id.tvPageText);

        if (bookIdArg < 0L) {
            Toast.makeText(requireContext(), R.string.catalog_load_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        final long bookId = bookIdArg;
        String baseUrl = getString(R.string.api_base_url);
        executor.execute(() -> {
            try {
                SamsungApiClient.HttpResult r = SamsungApiClient.getBook(baseUrl, bookId);
                if (r.statusCode != 200) {
                    mainHandler.post(() -> {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), R.string.catalog_load_failed, Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                JSONObject book = new JSONObject(r.body);
                String name = book.optString("name", "").trim();
                if (name.isEmpty()) {
                    name = "—";
                }
                String text = book.isNull("text") ? "" : book.optString("text", "");
                String finalName = name;
                String finalText = text;
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    btnPageTitle.setText(finalName);
                    tvPageText.setText(finalText);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.register_network_error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}
