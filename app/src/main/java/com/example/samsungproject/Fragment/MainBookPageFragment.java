package com.example.samsungproject.Fragment;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.samsungproject.R;
import com.example.samsungproject.net.SamsungApiClient;
import com.example.samsungproject.util.BookJsonHelper;
import com.example.samsungproject.util.UserResponseParser;
import com.google.android.material.button.MaterialButton;

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
    private TextView tvDescription;
    private TextView tvTags;
    private MaterialButton btnDescriptionMore;
    private MaterialButton btnTagsMore;
    private ImageView imgCover;

    public MainBookPageFragment() {
        super(R.layout.mainbookpage);
    }

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

        MaterialButton btnRead = view.findViewById(R.id.mbpbtnRead);
        btnRead.setOnClickListener(v -> {
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

        if (bookId >= 0L) {
            loadBookFromServer(bookId);
        }
    }

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

    private void loadBookFromServer(long id) {
        String baseUrl = getString(R.string.api_base_url);
        executor.execute(() -> {
            try {
                SamsungApiClient.HttpResult r = SamsungApiClient.getBook(baseUrl, id);
                if (r.statusCode != 200) {
                    mainHandler.post(() -> {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), R.string.catalog_load_failed, Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                JSONObject book = new JSONObject(r.body);
                byte[] iconBytes = UserResponseParser.decodeUserIcon(book);
                String description = book.isNull("description") ? "" : book.optString("description", "");
                String tagsLine = BookJsonHelper.formatTagsLine(book);

                mainHandler.post(() -> {
                    if (!isAdded()) return;
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
