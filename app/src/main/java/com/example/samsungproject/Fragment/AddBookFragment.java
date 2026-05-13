package com.example.samsungproject.Fragment;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.samsungproject.R;
import com.example.samsungproject.net.SamsungApiClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AddBookFragment extends Fragment {

    private final Executor executor = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<String[]> pickTextLauncher;

    @Nullable private String iconDataUrl;
    @Nullable private String bookText;
    @Nullable private String textFileBase64;
    @Nullable private String textFileMime;

    public AddBookFragment() {
        super(R.layout.add_book);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), this::onImagePicked);
        pickTextLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onTextPicked);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton btnBack = view.findViewById(R.id.btnAddBookBack);
        btnBack.setOnClickListener(v ->
                NavHostFragment.findNavController(AddBookFragment.this)
                        .navigate(R.id.action_addBookFragment_to_generalFragment));

        MaterialButton btnPickIcon = view.findViewById(R.id.btnAddBookIcon);
        MaterialButton btnPickText = view.findViewById(R.id.btnAddBookText);
        MaterialButton btnConfirm = view.findViewById(R.id.btnAddBookConfirm);

        TextInputLayout tilName = view.findViewById(R.id.tilBookName);
        TextInputLayout tilDescription = view.findViewById(R.id.tilBookDescription);
        TextInputEditText etName = view.findViewById(R.id.etBookName);
        TextInputEditText etDescription = view.findViewById(R.id.etBookDescription);
        MaterialAutoCompleteTextView actTags = view.findViewById(R.id.actBookTags);

        ArrayAdapter<CharSequence> tagsAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.book_tags,
                android.R.layout.simple_list_item_1
        );
        actTags.setAdapter(tagsAdapter);

        btnPickIcon.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        btnPickText.setOnClickListener(v -> pickTextLauncher.launch(new String[]{
                "text/plain",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/msword"
        }));

        btnConfirm.setOnClickListener(v -> {
            tilName.setError(null);
            tilDescription.setError(null);

            String name = text(etName);
            String description = text(etDescription);
            String tag = actTags.getText() != null ? actTags.getText().toString().trim() : "";

            if (name.isEmpty()) {
                tilName.setError(getString(R.string.field_required));
                return;
            }
            if (description.isEmpty()) {
                tilDescription.setError(getString(R.string.field_required));
                return;
            }
            if (iconDataUrl == null || iconDataUrl.trim().isEmpty()) {
                Toast.makeText(requireContext(), R.string.add_book_err_pick_icon, Toast.LENGTH_SHORT).show();
                return;
            }
            if (bookText == null || bookText.trim().isEmpty()) {
                if (textFileBase64 == null || textFileBase64.trim().isEmpty()) {
                    Toast.makeText(requireContext(), R.string.add_book_err_pick_text, Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            if (tag.isEmpty()) {
                Toast.makeText(requireContext(), R.string.add_book_err_tags, Toast.LENGTH_SHORT).show();
                return;
            }

            btnConfirm.setEnabled(false);
            Context appCtx = requireContext().getApplicationContext();
            String baseUrl = getString(R.string.api_base_url);
            String finalName = name;
            String finalDescription = description;
            String finalIcon = iconDataUrl;
            String finalText = bookText;
            String finalTextFileBase64 = textFileBase64;
            String finalTextFileMime = textFileMime;

            executor.execute(() -> {
                try {
                    JSONArray tags = new JSONArray();
                    tags.put(tag);
                    SamsungApiClient.HttpResult r = SamsungApiClient.postBook(
                            baseUrl,
                            finalName,
                            finalDescription,
                            finalIcon,
                            tags,
                            finalText,
                            finalTextFileBase64,
                            finalTextFileMime
                    );
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        btnConfirm.setEnabled(true);
                        if (r.statusCode == 200 || r.statusCode == 201) {
                            Toast.makeText(requireContext(), R.string.add_book_created, Toast.LENGTH_SHORT).show();
                            NavHostFragment.findNavController(AddBookFragment.this)
                                    .navigate(R.id.action_addBookFragment_to_generalFragment);
                        } else {
                            Toast.makeText(requireContext(), R.string.add_book_err_server, Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        btnConfirm.setEnabled(true);
                        Toast.makeText(appCtx, R.string.register_network_error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
    }

    private void onImagePicked(@Nullable Uri uri) {
        if (uri == null) return;
        Context ctx = requireContext();
        executor.execute(() -> {
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    toastMain(ctx, R.string.upload_image_read_error);
                    return;
                }
                byte[] bytes = readAllBytes(in);
                if (bytes.length == 0) {
                    toastMain(ctx, R.string.upload_image_empty);
                    return;
                }
                String mime = ctx.getContentResolver().getType(uri);
                if (mime == null || mime.trim().isEmpty()) {
                    mime = "image/jpeg";
                }
                String b64 = java.util.Base64.getEncoder().encodeToString(bytes);
                iconDataUrl = "data:" + mime + ";base64," + b64;
                toastMain(ctx, R.string.upload_image_success);
            } catch (Exception e) {
                toastMain(ctx, R.string.upload_image_failed);
            }
        });
    }

    private void onTextPicked(@Nullable Uri uri) {
        if (uri == null) return;
        Context ctx = requireContext();
        executor.execute(() -> {
            try {
                String mime = ctx.getContentResolver().getType(uri);
                String nameGuess = (uri.getLastPathSegment() != null) ? uri.getLastPathSegment() : "";
                String lower = nameGuess.toLowerCase(Locale.ROOT);

                String extracted;
                if ("text/plain".equals(mime) || lower.endsWith(".txt")) {
                    extracted = readTextUtf8(ctx, uri);
                    if (extracted == null || extracted.trim().isEmpty()) {
                        toastMain(ctx, R.string.upload_image_empty);
                        return;
                    }
                    bookText = extracted;
                    textFileBase64 = null;
                    textFileMime = null;
                    toastMain(ctx, R.string.add_book_add_text);
                    return;
                } else if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mime) || lower.endsWith(".docx")) {
                    byte[] bytes = readBytesFromUri(ctx, uri);
                    if (bytes.length == 0) {
                        toastMain(ctx, R.string.upload_image_empty);
                        return;
                    }
                    textFileBase64 = java.util.Base64.getEncoder().encodeToString(bytes);
                    textFileMime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                    bookText = null;
                    toastMain(ctx, R.string.add_book_add_text);
                    return;
                } else if ("application/msword".equals(mime) || lower.endsWith(".doc")) {
                    byte[] bytes = readBytesFromUri(ctx, uri);
                    if (bytes.length == 0) {
                        toastMain(ctx, R.string.upload_image_empty);
                        return;
                    }
                    textFileBase64 = java.util.Base64.getEncoder().encodeToString(bytes);
                    textFileMime = "application/msword";
                    bookText = null;
                    toastMain(ctx, R.string.add_book_add_text);
                    return;
                } else {
                    toastMain(ctx, R.string.add_book_err_bad_file);
                    return;
                }
            } catch (Exception e) {
                toastMain(ctx, R.string.add_book_err_bad_file);
            }
        });
    }

    private void toastMain(@NonNull Context ctx, int resId) {
        requireActivity().runOnUiThread(() -> Toast.makeText(ctx, resId, Toast.LENGTH_SHORT).show());
    }

    private static String text(@Nullable TextInputEditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }

    private static byte[] readAllBytes(@NonNull InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }

    private static String readTextUtf8(@NonNull Context ctx, @NonNull Uri uri) throws Exception {
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            byte[] bytes = readAllBytes(in);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static byte[] readBytesFromUri(@NonNull Context ctx, @NonNull Uri uri) throws Exception {
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return new byte[0];
            return readAllBytes(in);
        }
    }
}

