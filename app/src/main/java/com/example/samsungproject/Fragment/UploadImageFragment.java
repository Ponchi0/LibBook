package com.example.samsungproject.Fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.samsungproject.LocalDB.UserDatabaseHelper;
import com.example.samsungproject.R;
import com.example.samsungproject.domain.User;
import com.example.samsungproject.net.SamsungApiClient;
import com.google.android.material.card.MaterialCardView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class UploadImageFragment extends Fragment {

    private static final String PREFS_NAME = "user_prefs";
    private static final String KEY_SERVER_USER_ID = "server_user_id";
    private static final String KEY_NAME = "name";

    private ActivityResultLauncher<String> pickImageLauncher;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public UploadImageFragment() {
        super(R.layout.upload_image_area);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), this::onImagePicked);
    }

    private void onImagePicked(@Nullable Uri uri) {
        if (uri != null) {
            uploadUri(uri);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View.OnClickListener openPicker = v -> pickImageLauncher.launch("image/*");

        view.findViewById(R.id.uploadDimBackground).setOnClickListener(v ->
                NavHostFragment.findNavController(UploadImageFragment.this).navigateUp());

        view.findViewById(R.id.tvUploadImageHint1).setOnClickListener(openPicker);
        view.findViewById(R.id.tvUploadImageHint2).setOnClickListener(openPicker);
        MaterialCardView surface = view.findViewById(R.id.uploadImageSurface);
        surface.setOnClickListener(openPicker);
    }

    private void uploadUri(@NonNull Uri uri) {
        Context ctx = requireContext();
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long userId = prefs.getLong(KEY_SERVER_USER_ID, -1L);
        if (userId < 0L) {
            Toast.makeText(ctx, R.string.upload_image_no_user_id, Toast.LENGTH_SHORT).show();
            return;
        }
        String baseUrl = getString(R.string.api_base_url);
        String rawMime = ctx.getContentResolver().getType(uri);
        final String imageMime =
                (rawMime == null || rawMime.isEmpty()) ? "image/jpeg" : rawMime;

        executor.execute(() -> {
            try {
                byte[] bytes;
                try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                    if (in == null) {
                        toastMain(ctx, R.string.upload_image_read_error);
                        return;
                    }
                    bytes = readAllBytes(in);
                }
                if (bytes.length == 0) {
                    toastMain(ctx, R.string.upload_image_empty);
                    return;
                }
                SamsungApiClient.HttpResult r = SamsungApiClient.putUserIcon(baseUrl, userId, imageMime, bytes);
                if (r.statusCode != 200) {
                    toastMain(ctx, R.string.upload_image_server_error);
                    return;
                }
                UserDatabaseHelper db = new UserDatabaseHelper(ctx);
                UserDatabaseHelper.UserRow row = db.getUser(userId);
                String name = row != null && row.name != null ? row.name : prefs.getString(KEY_NAME, null);
                if (name == null || name.trim().isEmpty()) {
                    name = User.getName_null(ctx);
                }
                db.upsertUser(userId, name, bytes);
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(ctx, R.string.upload_image_success, Toast.LENGTH_SHORT).show();
                    NavHostFragment.findNavController(UploadImageFragment.this).navigateUp();
                });
            } catch (Exception e) {
                toastMain(ctx, R.string.upload_image_failed);
            }
        });
    }

    private void toastMain(@NonNull Context ctx, int resId) {
        requireActivity().runOnUiThread(() -> Toast.makeText(ctx, resId, Toast.LENGTH_SHORT).show());
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
}
