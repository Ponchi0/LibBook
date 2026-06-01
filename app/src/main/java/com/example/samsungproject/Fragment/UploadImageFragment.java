package com.example.samsungproject.Fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;
import com.example.samsungproject.LocalDB.UserDatabaseHelper;
import com.example.samsungproject.R;
import com.example.samsungproject.domain.User;
import com.example.samsungproject.net.ApiConfig;
import com.example.samsungproject.net.LibBookApiClient;
import com.google.android.material.card.MaterialCardView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class UploadImageFragment extends DialogFragment {

    private static final String TAG = "UploadImageFragment";
    private static final int ICON_MAX_SIDE_PX = 512;
    private static final int ICON_JPEG_QUALITY = 82;

    private static final String PREFS_NAME = "user_prefs";
    private static final String KEY_SERVER_USER_ID = "server_user_id";
    private static final String KEY_NAME = "name";

    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<CropImageContractOptions> cropImageLauncher;
    private final Executor executor = Executors.newSingleThreadExecutor();

    /**
     * Создаёт диалог загрузки и обрезки аватара пользователя.
     */
    public UploadImageFragment() {
        super(R.layout.upload_image_area);
    }

    /**
     * Регистрирует обработчики выбора и обрезки изображения.
     *
     * @param savedInstanceState сохранённое состояние или {@code null}
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, 0);
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), this::onImagePicked);
        cropImageLauncher = registerForActivityResult(new CropImageContract(), this::onImageCropped);
    }

    /**
     * Растягивает диалог на весь экран с прозрачным фоном.
     */
    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            Window window = getDialog().getWindow();
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    /**
     * Обрабатывает выбор изображения из галереи и запускает обрезку.
     *
     * @param uri URI выбранного файла или {@code null}
     */
    private void onImagePicked(@Nullable Uri uri) {
        if (uri != null) {
            startCrop(uri);
        }
    }

    /**
     * Обрабатывает результат обрезки и отправляет изображение на сервер.
     *
     * @param result результат работы экрана обрезки
     */
    private void onImageCropped(@NonNull CropImageView.CropResult result) {
        if (!result.isSuccessful()) {
            return;
        }
        Uri uri = result.getUriContent();
        if (uri != null) {
            uploadUri(uri);
        }
    }

    /**
     * Открывает экран квадратной обрезки для выбранного изображения.
     *
     * @param sourceUri URI исходного изображения
     */
    private void startCrop(@NonNull Uri sourceUri) {
        Context ctx = requireContext();
        int sizePx = cropOutputSizePx(ctx);

        CropImageOptions options = new CropImageOptions();
        options.fixAspectRatio = true;
        options.aspectRatioX = 1;
        options.aspectRatioY = 1;
        options.outputRequestWidth = sizePx;
        options.outputRequestHeight = sizePx;
        options.outputRequestSizeOptions = CropImageView.RequestSizeOptions.RESIZE_EXACT;
        options.maxCropResultWidth = sizePx;
        options.maxCropResultHeight = sizePx;
        options.guidelines = CropImageView.Guidelines.ON;
        options.imageSourceIncludeGallery = false;
        options.imageSourceIncludeCamera = false;
        options.outputCompressFormat = Bitmap.CompressFormat.JPEG;
        options.outputCompressQuality = ICON_JPEG_QUALITY;
        options.activityTitle = getString(R.string.upload_image_crop_title);

        cropImageLauncher.launch(new CropImageContractOptions(sourceUri, options));
    }

    /**
     * Возвращает целевой размер обрезанного изображения по ширине экрана.
     *
     * @param ctx контекст приложения
     * @return размер стороны квадрата в пикселях
     */
    private static int cropOutputSizePx(@NonNull Context ctx) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        return dm.widthPixels;
    }

    /**
     * Настраивает области нажатия для выбора изображения и закрытия диалога.
     *
     * @param view               корневое представление диалога
     * @param savedInstanceState сохранённое состояние или {@code null}
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View.OnClickListener openPicker = v -> pickImageLauncher.launch("image/*");

        view.findViewById(R.id.uploadDimBackground).setOnClickListener(v ->
                EnterChangeEmailDialogFragment.dismissAllChangeDialogs(this));

        view.findViewById(R.id.tvUploadImageHint1).setOnClickListener(openPicker);
        view.findViewById(R.id.tvUploadImageHint2).setOnClickListener(openPicker);
        MaterialCardView surface = view.findViewById(R.id.uploadImageSurface);
        surface.setOnClickListener(openPicker);
    }

    /**
     * Читает, масштабирует и загружает аватар пользователя на сервер и в локальную БД.
     *
     * @param uri URI обрезанного изображения
     */
    private void uploadUri(@NonNull Uri uri) {
        Context appCtx = requireContext().getApplicationContext();
        String baseUrl = ApiConfig.baseUrl(appCtx);
        executor.execute(() -> {
            try {
                byte[] bytes;
                try (InputStream in = appCtx.getContentResolver().openInputStream(uri)) {
                    if (in == null) {
                        toastMain(appCtx, R.string.upload_image_read_error);
                        return;
                    }
                    bytes = readAllBytes(in);
                }
                if (bytes.length == 0) {
                    toastMain(appCtx, R.string.upload_image_empty);
                    return;
                }
                byte[] scaled = readScaledJpeg(bytes);

                long userId = LibBookApiClient.resolveServerUserId(appCtx, baseUrl);
                if (userId < 0) {
                    toastMain(appCtx, R.string.upload_image_no_user_id);
                    return;
                }

                LibBookApiClient.HttpResult r = LibBookApiClient.putUserIcon(baseUrl, userId, "image/jpeg", scaled);
                if (r.statusCode == 404) {
                    appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .remove(KEY_SERVER_USER_ID)
                            .apply();
                    userId = LibBookApiClient.resolveServerUserId(appCtx, baseUrl);
                    if (userId >= 0) {
                        r = LibBookApiClient.putUserIcon(baseUrl, userId, "image/jpeg", scaled);
                    }
                }
                if (userId < 0) {
                    toastMain(appCtx, R.string.upload_image_no_user_id);
                    return;
                }
                if (r.statusCode != 200) {
                    Log.e(TAG, "putUserIcon failed: " + r.statusCode + " body=" + r.body);
                    toastMain(appCtx, R.string.upload_image_server_error);
                    return;
                }

                SharedPreferences prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit().putLong(KEY_SERVER_USER_ID, userId).apply();

                UserDatabaseHelper db = new UserDatabaseHelper(appCtx);
                UserDatabaseHelper.UserRow row = db.getUser(userId);
                String name = row != null && row.name != null ? row.name : prefs.getString(KEY_NAME, null);
                if (name == null || name.trim().isEmpty()) {
                    name = User.getName_null(appCtx);
                }
                db.upsertUser(userId, name, scaled);
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(appCtx, R.string.upload_image_success, Toast.LENGTH_SHORT).show();
                    EnterChangeEmailDialogFragment.dismissAllChangeDialogs(UploadImageFragment.this);
                });
            } catch (Exception e) {
                Log.e(TAG, "uploadUri failed", e);
                toastMain(appCtx, R.string.upload_image_failed);
            }
        });
    }

    /**
     * Показывает короткое сообщение в UI-потоке активности.
     *
     * @param ctx   контекст приложения
     * @param resId идентификатор строкового ресурса
     */
    private void toastMain(@NonNull Context ctx, int resId) {
        requireActivity().runOnUiThread(() -> Toast.makeText(ctx, resId, Toast.LENGTH_SHORT).show());
    }

    /**
     * Читает все байты из потока в память.
     *
     * @param in входной поток
     * @return содержимое потока
     * @throws Exception при ошибке чтения
     */
    private static byte[] readAllBytes(@NonNull InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }

    /**
     * Уменьшает изображение до допустимого размера и перекодирует его в JPEG.
     *
     * @param raw исходные байты изображения
     * @return масштабированные байты JPEG или исходные байты при ошибке декодирования
     * @throws Exception при ошибке обработки bitmap
     */
    @NonNull
    private static byte[] readScaledJpeg(@NonNull byte[] raw) throws Exception {
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
}
