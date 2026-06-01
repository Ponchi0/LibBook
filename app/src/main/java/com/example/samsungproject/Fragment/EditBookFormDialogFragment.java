package com.example.samsungproject.Fragment;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
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
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class EditBookFormDialogFragment extends DialogFragment {

    private static final String TAG = "EditBookFormDialog";
    private static final int ICON_MAX_SIDE_PX = 1280;
    private static final int ICON_JPEG_QUALITY = 88;

    public static final String ARG_USER_ID = "userId";
    public static final String ARG_BOOK_ID = "bookId";
    public static final String ARG_BOOK_NAME = "bookName";

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<String[]> pickTextLauncher;

    @Nullable private String iconDataUrl;
    @Nullable private String bookText;
    @Nullable private String verifiedBookPassword;

    @Nullable private String[] allTags;
    @Nullable private boolean[] checkedTags;
    @NonNull private final ArrayList<String> selectedTags = new ArrayList<>();

    private boolean editStepUnlocked;
    private long bookId = -1L;
    private long userId = -1L;

    /** Создаёт экземпляр диалога редактирования книги. */
    public EditBookFormDialogFragment() {
        super(R.layout.entereditbook);
    }

    /** Инициализирует стиль диалога и регистрирует обработчики выбора файлов. */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, 0);
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), this::onImagePicked);
        pickTextLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onTextPicked);
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

    /** Настраивает форму редактирования книги и обработчики кнопок. */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        bookId = args != null ? args.getLong(ARG_BOOK_ID, -1L) : -1L;
        userId = args != null ? args.getLong(ARG_USER_ID, -1L) : -1L;
        String bookName = args != null ? args.getString(ARG_BOOK_NAME, "") : "";

        if (bookId < 0 || userId < 0) {
            Toast.makeText(requireContext(), R.string.edit_book_not_logged_in, Toast.LENGTH_SHORT).show();
            dismissToGeneral();
            return;
        }

        view.findViewById(R.id.ecDimBackground).setOnClickListener(v -> dismissToGeneral());

        android.widget.TextView tvTitle = view.findViewById(R.id.tvEditBookTitle);
        tvTitle.setText(getString(R.string.edit_book_title, bookName));

        TextInputLayout tilPassword = view.findViewById(R.id.tilEditBookPassword);
        TextInputEditText etPassword = view.findViewById(R.id.etEditBookPassword);
        View formScroll = view.findViewById(R.id.svEditBookForm);
        MaterialButton btnCancel = view.findViewById(R.id.btnEditBookCancel);
        MaterialButton btnConfirm = view.findViewById(R.id.btnEditBookConfirm);

        View formRoot = view.findViewById(R.id.editBookForm);
        TextInputLayout tilName = formRoot.findViewById(R.id.tilBookName);
        TextInputLayout tilDescription = formRoot.findViewById(R.id.tilBookDescription);
        TextInputLayout tilBookPassword = formRoot.findViewById(R.id.tilBookPassword);
        TextInputEditText etName = formRoot.findViewById(R.id.etBookName);
        TextInputEditText etDescription = formRoot.findViewById(R.id.etBookDescription);
        TextInputEditText actTags = formRoot.findViewById(R.id.actBookTags);
        MaterialButton btnPickIcon = formRoot.findViewById(R.id.btnAddBookIcon);
        MaterialButton btnPickText = formRoot.findViewById(R.id.btnAddBookText);

        tilBookPassword.setVisibility(View.GONE);

        if (allTags == null) {
            allTags = requireContext().getResources().getStringArray(R.array.book_tags);
        }
        if (checkedTags == null) {
            checkedTags = new boolean[allTags.length];
        }
        actTags.setOnClickListener(v -> showTagsDialog(actTags));
        btnPickIcon.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        btnPickText.setOnClickListener(v -> pickTextLauncher.launch(BookTextExtractor.PICK_TEXT_MIME_TYPES));

        btnCancel.setOnClickListener(v -> dismissToGeneral());
        btnConfirm.setOnClickListener(v -> {
            if (!editStepUnlocked) {
                verifyPasswordAndLoadBook(
                        tilPassword, etPassword, formScroll, tilPassword,
                        tilName, tilDescription, etName, etDescription, actTags, btnConfirm
                );
            } else {
                saveChanges(tilName, tilDescription, etName, etDescription, btnConfirm);
            }
        });
    }

    /** Проверяет пароль книги и загружает её данные для редактирования. */
    private void verifyPasswordAndLoadBook(
            @NonNull TextInputLayout tilPassword,
            @NonNull TextInputEditText etPassword,
            @NonNull View formScroll,
            @NonNull TextInputLayout tilPasswordRef,
            @NonNull TextInputLayout tilName,
            @NonNull TextInputLayout tilDescription,
            @NonNull TextInputEditText etName,
            @NonNull TextInputEditText etDescription,
            @NonNull TextInputEditText actTags,
            @NonNull MaterialButton btnConfirm
    ) {
        String password = text(etPassword);
        if (password.isEmpty()) {
            tilPassword.setError(getString(R.string.field_required));
            return;
        }
        tilPassword.setError(null);
        btnConfirm.setEnabled(false);

        String baseUrl = ApiConfig.baseUrl(requireContext().getApplicationContext());
        executor.execute(() -> {
            try {
                LibBookApiClient.HttpResult verify =
                        LibBookApiClient.verifyBookPassword(baseUrl, bookId, password);
                JSONObject v = new JSONObject(verify.body != null ? verify.body : "{}");
                boolean ok = verify.statusCode == 200 && v.optBoolean("valid", false);
                if (!ok) {
                    mainHandler.post(() -> {
                        if (!isAdded()) return;
                        btnConfirm.setEnabled(true);
                        tilPassword.setError(getString(R.string.edit_book_wrong_password));
                    });
                    return;
                }
                LibBookApiClient.HttpResult bookR = LibBookApiClient.getBook(baseUrl, bookId);
                if (bookR.statusCode != 200) {
                    mainHandler.post(() -> {
                        if (!isAdded()) return;
                        btnConfirm.setEnabled(true);
                        Toast.makeText(requireContext(), R.string.register_network_error, Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                JSONObject book = new JSONObject(bookR.body);
                verifiedBookPassword = password;
                String name = book.optString("name", "");
                String description = book.optString("description", "");
                bookText = book.isNull("text") ? null : book.optString("text", null);
                iconDataUrl = resolveIconDataUrl(book);
                selectedTags.clear();
                JSONArray tags = book.optJSONArray("tags");
                if (tags != null) {
                    for (int i = 0; i < tags.length(); i++) {
                        selectedTags.add(tags.getString(i));
                    }
                }
                if (allTags != null && checkedTags != null) {
                    Arrays.fill(checkedTags, false);
                    for (int i = 0; i < allTags.length; i++) {
                        checkedTags[i] = selectedTags.contains(allTags[i]);
                    }
                }
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    btnConfirm.setEnabled(true);
                    editStepUnlocked = true;
                    tilPasswordRef.setVisibility(View.GONE);
                    formScroll.setVisibility(View.VISIBLE);
                    etName.setText(name);
                    etDescription.setText(description);
                    actTags.setText(String.join(", ", selectedTags));
                    btnConfirm.setText(R.string.edit_book_confirm);
                });
            } catch (Exception e) {
                Log.e(TAG, "verify/load book failed", e);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    btnConfirm.setEnabled(true);
                    Toast.makeText(requireContext(), R.string.register_network_error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /** Извлекает URL обложки книги из JSON-ответа сервера. */
    @Nullable
    private static String resolveIconDataUrl(@NonNull JSONObject book) {
        String icon = book.optString("iconBase64", null);
        if (icon == null || icon.isBlank()) {
            icon = book.optString("icon", null);
        }
        if (icon == null || icon.isBlank()) {
            return null;
        }
        return icon;
    }

    /** Отправляет изменённые данные книги на сервер. */
    private void saveChanges(
            @NonNull TextInputLayout tilName,
            @NonNull TextInputLayout tilDescription,
            @NonNull TextInputEditText etName,
            @NonNull TextInputEditText etDescription,
            @NonNull MaterialButton btnConfirm
    ) {
        tilName.setError(null);
        tilDescription.setError(null);

        String name = text(etName);
        String description = text(etDescription);
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
            Toast.makeText(requireContext(), R.string.add_book_err_pick_text, Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedTags.isEmpty()) {
            Toast.makeText(requireContext(), R.string.add_book_err_tags, Toast.LENGTH_SHORT).show();
            return;
        }
        if (verifiedBookPassword == null || verifiedBookPassword.isEmpty()) {
            Toast.makeText(requireContext(), R.string.edit_book_wrong_password, Toast.LENGTH_SHORT).show();
            return;
        }

        btnConfirm.setEnabled(false);
        String baseUrl = ApiConfig.baseUrl(requireContext().getApplicationContext());
        String finalName = name;
        String finalDescription = description;
        String finalIcon = iconDataUrl;
        String finalText = bookText;
        String bookPassword = verifiedBookPassword;

        executor.execute(() -> {
            try {
                JSONArray tags = new JSONArray();
                for (String t : selectedTags) {
                    tags.put(t);
                }
                LibBookApiClient.HttpResult r = LibBookApiClient.updateBook(
                        baseUrl,
                        bookId,
                        userId,
                        bookPassword,
                        finalName,
                        finalDescription,
                        finalText,
                        finalIcon,
                        tags
                );
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    btnConfirm.setEnabled(true);
                    if (r.statusCode == 200) {
                        Toast.makeText(requireContext(), R.string.edit_book_success, Toast.LENGTH_SHORT).show();
                        dismissToGeneral();
                    } else if (r.statusCode == 401) {
                        Toast.makeText(requireContext(), R.string.edit_book_wrong_password, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), R.string.edit_book_failed, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "updateBook failed", e);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    btnConfirm.setEnabled(true);
                    Toast.makeText(requireContext(), R.string.register_network_error, Toast.LENGTH_SHORT).show();
                });
            }
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
                    toastMain(R.string.upload_image_read_error);
                    return;
                }
                byte[] bytes = readScaledIconBytes(readAllBytes(in));
                String b64 = java.util.Base64.getEncoder().encodeToString(bytes);
                iconDataUrl = "data:image/jpeg;base64," + b64;
                toastMain(R.string.upload_image_success);
            } catch (Exception e) {
                toastMain(R.string.upload_image_failed);
            }
        });
    }

    /** Обрабатывает выбранный текстовый файл и извлекает из него текст книги. */
    private void onTextPicked(@Nullable Uri uri) {
        if (uri == null) return;
        Context ctx = requireContext();
        executor.execute(() -> {
            BookTextExtractor.Result result = BookTextExtractor.extract(ctx, uri);
            mainHandler.post(() -> {
                if (!isAdded()) return;
                switch (result.status) {
                    case OK:
                        bookText = result.text;
                        Toast.makeText(ctx, R.string.add_book_add_text, Toast.LENGTH_SHORT).show();
                        break;
                    case HAS_IMAGES:
                        bookText = null;
                        Toast.makeText(ctx, R.string.add_book_err_images_in_file, Toast.LENGTH_LONG).show();
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
    private void toastMain(int resId) {
        mainHandler.post(() -> {
            if (isAdded()) {
                Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Закрывает диалог и возвращается на экран «Общее». */
    private void dismissToGeneral() {
        NavHostFragment.findNavController(this).popBackStack(R.id.generalFragment, false);
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
