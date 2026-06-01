package com.example.samsungproject.Fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
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
import com.example.samsungproject.net.ApiConfig;
import com.example.samsungproject.net.LibBookApiClient;
import com.example.samsungproject.util.BookTextExtractor;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AddBookFragment extends Fragment {

    private static final String TAG = "AddBookFragment";
    private static final int ICON_MAX_SIDE_PX = 1280;
    private static final int ICON_JPEG_QUALITY = 88;

    private static final String PREFS_NAME = "user_prefs";
    private static final String KEY_SERVER_USER_ID = "server_user_id";

    private final Executor executor = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<String[]> pickTextLauncher;

    @Nullable private String iconDataUrl;
    @Nullable private String bookText;

    @Nullable private String[] allTags;
    @Nullable private boolean[] checkedTags;
    @NonNull private final ArrayList<String> selectedTags = new ArrayList<>();

    /** Создаёт экземпляр фрагмента добавления книги. */
    public AddBookFragment() {
        super(R.layout.add_book);
    }

    /** Регистрирует обработчики выбора обложки и текстового файла. */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), this::onImagePicked);
        pickTextLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onTextPicked);
    }

    /** Настраивает форму добавления книги и обработчики кнопок. */
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
        TextInputLayout tilPassword = view.findViewById(R.id.tilBookPassword);
        TextInputEditText etName = view.findViewById(R.id.etBookName);
        TextInputEditText etDescription = view.findViewById(R.id.etBookDescription);
        TextInputEditText etPassword = view.findViewById(R.id.etBookPassword);
        TextInputEditText actTags = view.findViewById(R.id.actBookTags);

        if (allTags == null) {
            allTags = requireContext().getResources().getStringArray(R.array.book_tags);
        }
        if (checkedTags == null) {
            checkedTags = new boolean[allTags.length];
        }
        actTags.setOnClickListener(v -> showTagsDialog(actTags));

        btnPickIcon.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        btnPickText.setOnClickListener(v ->
                pickTextLauncher.launch(BookTextExtractor.PICK_TEXT_MIME_TYPES));

        btnConfirm.setOnClickListener(v -> {
            tilName.setError(null);
            tilDescription.setError(null);
            tilPassword.setError(null);

            String name = text(etName);
            String description = text(etDescription);
            String passwordbook = text(etPassword);

            if (name.isEmpty()) {
                tilName.setError(getString(R.string.field_required));
                return;
            }
            if (description.isEmpty()) {
                tilDescription.setError(getString(R.string.field_required));
                return;
            }
            if (passwordbook.isEmpty()) {
                tilPassword.setError(getString(R.string.field_required));
                return;
            }
            if (iconDataUrl == null || iconDataUrl.trim().isEmpty()) {
                Toast.makeText(requireContext(), R.string.add_book_err_pick_icon, Toast.LENGTH_SHORT).show();
                return;
            }
            if (bookText == null || bookText.trim().isEmpty()) {
                Toast.makeText(requireContext(), R.string.add_book_err_pick_text, Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedTags.isEmpty()) {
                Toast.makeText(requireContext(), R.string.add_book_err_tags, Toast.LENGTH_SHORT).show();
                return;
            }

            SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long userId = prefs.getLong(KEY_SERVER_USER_ID, -1L);
            if (userId < 0L) {
                Toast.makeText(requireContext(), R.string.add_book_err_login, Toast.LENGTH_SHORT).show();
                return;
            }

            btnConfirm.setEnabled(false);
            Context appCtx = requireContext().getApplicationContext();
            String baseUrl = ApiConfig.baseUrl(requireContext().getApplicationContext());
            String finalName = name;
            String finalDescription = description;
            String finalIcon = iconDataUrl;
            long finalUserId = userId;
            String finalText = (bookText != null && !bookText.trim().isEmpty()) ? bookText : null;
            String finalPassword = passwordbook;

            executor.execute(() -> {
                try {
                    JSONArray tags = new JSONArray();
                    for (String t : selectedTags) {
                        tags.put(t);
                    }
                    LibBookApiClient.HttpResult r = LibBookApiClient.postBook(
                            baseUrl,
                            finalName,
                            finalDescription,
                            finalText,
                            finalIcon,
                            tags,
                            finalUserId,
                            finalPassword
                    );
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        btnConfirm.setEnabled(true);
                        if (r.statusCode == 200 || r.statusCode == 201) {
                            Toast.makeText(requireContext(), R.string.add_book_created, Toast.LENGTH_SHORT).show();
                            NavHostFragment.findNavController(AddBookFragment.this)
                                    .navigate(R.id.action_addBookFragment_to_generalFragment);
                        } else {
                            String detail = r.body != null && !r.body.isEmpty() ? r.body : "";
                            Toast.makeText(requireContext(),
                                    getString(R.string.add_book_err_server) + detail,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "postBook failed, baseUrl=" + baseUrl, e);
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        btnConfirm.setEnabled(true);
                        String msg = e.getMessage() != null ? e.getMessage() : "";
                        if (msg.contains("timed out")) {
                            Toast.makeText(appCtx, R.string.add_book_err_timeout, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(appCtx, R.string.register_network_error, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        });
    }

    /** Открывает диалог множественного выбора тегов книги. */
    private void showTagsDialog(@NonNull TextInputEditText actTags) {
        if (allTags == null || checkedTags == null) {
            return;
        }
        boolean[] tmpChecked = Arrays.copyOf(checkedTags, checkedTags.length);
        List<String> tmpSelected = new ArrayList<>(selectedTags);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.add_book_hint_tags)
                .setMultiChoiceItems(allTags, tmpChecked, (dialog, which, isChecked) -> {
                    String tag = allTags[which];
                    if (isChecked) {
                        if (!tmpSelected.contains(tag)) tmpSelected.add(tag);
                    } else {
                        tmpSelected.remove(tag);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    checkedTags = tmpChecked;
                    selectedTags.clear();
                    selectedTags.addAll(tmpSelected);
                    actTags.setText(String.join(", ", selectedTags));
                })
                .show();
    }

    /** Обрабатывает выбранное изображение обложки и сохраняет его в формате data URL. */
    private void onImagePicked(@Nullable Uri uri) {
        if (uri == null) return;
        Context ctx = requireContext();
        executor.execute(() -> {
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    toastMain(ctx, R.string.upload_image_read_error);
                    return;
                }
                byte[] bytes = readScaledIconBytes(readAllBytes(in));
                if (bytes.length == 0) {
                    toastMain(ctx, R.string.upload_image_empty);
                    return;
                }
                String mime = "image/jpeg";
                String b64 = java.util.Base64.getEncoder().encodeToString(bytes);
                iconDataUrl = "data:" + mime + ";base64," + b64;
                toastMain(ctx, R.string.upload_image_success);
            } catch (Exception e) {
                toastMain(ctx, R.string.upload_image_failed);
            }
        });
    }

    /** Обрабатывает выбранный текстовый файл и извлекает из него текст книги. */
    private void onTextPicked(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        Context ctx = requireContext();
        try {
            ctx.getContentResolver().takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (Exception ignored) {

        }
        executor.execute(() -> {
            BookTextExtractor.Result result = BookTextExtractor.extract(ctx, uri);
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                switch (result.status) {
                    case OK:
                        bookText = result.text;
                        Toast.makeText(ctx, R.string.add_book_add_text, Toast.LENGTH_SHORT).show();
                        break;
                    case HAS_IMAGES:
                        bookText = null;
                        Toast.makeText(ctx, R.string.add_book_err_images_in_file, Toast.LENGTH_LONG).show();
                        break;
                    case EMPTY:
                        bookText = null;
                        Toast.makeText(ctx, R.string.upload_image_empty, Toast.LENGTH_SHORT).show();
                        break;
                    case UNSUPPORTED:
                        bookText = null;
                        Toast.makeText(ctx, R.string.add_book_err_bad_file, Toast.LENGTH_SHORT).show();
                        break;
                    default:
                        bookText = null;
                        Toast.makeText(ctx, R.string.add_book_err_bad_file, Toast.LENGTH_SHORT).show();
                        break;
                }
            });
        });
    }

    /** Показывает короткое уведомление в главном потоке. */
    private void toastMain(@NonNull Context ctx, int resId) {
        requireActivity().runOnUiThread(() -> Toast.makeText(ctx, resId, Toast.LENGTH_SHORT).show());
    }

    /** Возвращает обрезанный текст из поля ввода или пустую строку. */
    private static String text(@Nullable TextInputEditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }


    /** Масштабирует изображение обложки и сжимает его в JPEG. */
    @NonNull
    private static byte[] readScaledIconBytes(@NonNull byte[] raw) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, 0, raw.length, bounds);
        int sample = 1;
        while (bounds.outWidth / sample > ICON_MAX_SIDE_PX
                || bounds.outHeight / sample > ICON_MAX_SIDE_PX) {
            sample *= 2;
        }
        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeByteArray(raw, 0, raw.length, decode);
        if (bmp == null) {
            return raw;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, ICON_JPEG_QUALITY, out);
        bmp.recycle();
        return out.toByteArray();
    }

    /** Читает все байты из входного потока. */
    private static byte[] readAllBytes(@NonNull InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }

}
