package com.example.samsungproject.Fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.samsungproject.LocalDB.UserDatabaseHelper;
import com.example.samsungproject.R;
import com.example.samsungproject.domain.User;
import com.example.samsungproject.net.ApiConfig;
import com.example.samsungproject.net.LibBookApiClient;
import com.example.samsungproject.util.BookmarkSyncHelper;
import com.example.samsungproject.util.UserResponseParser;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class RegistrationFragment extends Fragment {

    private static final String PREFS_NAME = "user_prefs";
    private static final String KEY_SERVER_USER_ID = "server_user_id";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_NAME = "name";

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean submitInProgress;

    /**
     * Создаёт фрагмент регистрации нового пользователя.
     */
    public RegistrationFragment() {
        super(R.layout.registration);
    }

    /**
     * Настраивает форму регистрации и отправку данных на сервер.
     *
     * @param view               корневое представление фрагмента
     * @param savedInstanceState сохранённое состояние или {@code null}
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextInputLayout tilEmail = view.findViewById(R.id.tilRegistrationEmail);
        TextInputLayout tilName = view.findViewById(R.id.tilRegistrationName);
        TextInputLayout tilPassword = view.findViewById(R.id.tilRegistrationPassword);
        TextInputEditText etEmail = view.findViewById(R.id.etRegistrationEmail);
        TextInputEditText etName = view.findViewById(R.id.etRegistrationName);
        TextInputEditText etPassword = view.findViewById(R.id.etRegistrationPassword);
        MaterialButton btnSubmit = view.findViewById(R.id.btnRegistrationSubmit);
        MaterialButton btnCancel = view.findViewById(R.id.btnRegistrationCancel);

        btnCancel.setOnClickListener(v -> navigateBackSafely());

        btnSubmit.setOnClickListener(v -> {
            tilEmail.setError(null);
            tilName.setError(null);
            tilPassword.setError(null);

            String email = text(etEmail);
            String name = text(etName);
            String password = text(etPassword);

            if (TextUtils.isEmpty(email)) {
                tilEmail.setError(getString(R.string.field_required));
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.setError(getString(R.string.invalid_email));
                return;
            }
            if (TextUtils.isEmpty(name)) {
                tilName.setError(getString(R.string.field_required));
                return;
            }
            if (TextUtils.isEmpty(password)) {
                tilPassword.setError(getString(R.string.field_required));
                return;
            }

            btnSubmit.setEnabled(false);
            submitInProgress = true;
            Context ctx = requireContext().getApplicationContext();
            String baseUrl = ApiConfig.baseUrl(ctx);

            executor.execute(() -> {
                try {
                    if (!submitInProgress) {
                        return;
                    }
                    LibBookApiClient.HttpResult lookup = LibBookApiClient.getUserByEmail(baseUrl, email);
                    if (lookup.statusCode == 200) {
                        postUi(() -> {
                            btnSubmit.setEnabled(true);
                            tilEmail.setError(getString(R.string.register_email_taken));
                            Toast.makeText(requireContext(), R.string.register_email_taken, Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }
                    if (lookup.statusCode != 404) {
                        postUi(() -> {
                            btnSubmit.setEnabled(true);
                            Toast.makeText(requireContext(), R.string.register_server_lookup_failed, Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }

                    LibBookApiClient.HttpResult nameLookup = LibBookApiClient.getUserByName(baseUrl, name);
                    if (LibBookApiClient.isUserNameTakenByOther(nameLookup, -1L)) {
                        postUi(() -> {
                            btnSubmit.setEnabled(true);
                            tilName.setError(getString(R.string.register_name_taken));
                            Toast.makeText(requireContext(), R.string.register_name_taken, Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }
                    if (nameLookup.statusCode != 404) {
                        postUi(() -> {
                            btnSubmit.setEnabled(true);
                            Toast.makeText(requireContext(), R.string.register_server_lookup_failed, Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }

                    LibBookApiClient.HttpResult create = LibBookApiClient.postRegisterUser(baseUrl, email, name, password);
                    if (LibBookApiClient.isNameAlreadyExistsError(create)) {
                        postUi(() -> {
                            btnSubmit.setEnabled(true);
                            tilName.setError(getString(R.string.register_name_taken));
                            Toast.makeText(requireContext(), R.string.register_name_taken, Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }
                    if (create.statusCode != 200 && create.statusCode != 201) {
                        postUi(() -> {
                            btnSubmit.setEnabled(true);
                            Toast.makeText(requireContext(), R.string.register_create_failed, Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }

                    JSONObject user = new JSONObject(create.body);
                    long userId = user.optLong("id", -1L);
                    if (userId < 0) {
                        postUi(() -> {
                            btnSubmit.setEnabled(true);
                            Toast.makeText(requireContext(), R.string.register_create_failed, Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }

                    String resolvedName = user.optString("name", name);
                    if (resolvedName == null || resolvedName.trim().isEmpty()) {
                        resolvedName = User.getName_null(ctx);
                    }
                    byte[] iconBytes = UserResponseParser.decodeUserIcon(user);

                    SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    prefs.edit()
                            .putLong(KEY_SERVER_USER_ID, userId)
                            .putString(KEY_EMAIL, email)
                            .putString(KEY_NAME, resolvedName)
                            .putString(KEY_PASSWORD, password)
                            .apply();

                    UserDatabaseHelper db = new UserDatabaseHelper(ctx);
                    db.upsertUser(userId, resolvedName, iconBytes);

                    BookmarkSyncHelper.invalidateFullSyncCache(ctx);
                    BookmarkSyncHelper.syncAsync(ctx, null);

                    postUi(() -> {
                        btnSubmit.setEnabled(true);
                        Toast.makeText(requireContext(), R.string.register_success, Toast.LENGTH_SHORT).show();
                        navigateBackSafely();
                    });
                } catch (Exception e) {
                    String url = baseUrl;
                    postUi(() -> {
                        btnSubmit.setEnabled(true);
                        String msg = getString(R.string.register_network_error)
                                + " (" + url + ")";
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }

    /**
     * Останавливает фоновую регистрацию и снимает отложенные колбэки UI при уничтожении представления.
     */
    @Override
    public void onDestroyView() {
        submitInProgress = false;
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }

    /**
     * Выполняет действие в главном потоке, если фрагмент ещё прикреплён к экрану.
     *
     * @param action действие для выполнения в UI-потоке
     */
    private void postUi(@NonNull Runnable action) {
        mainHandler.post(() -> {
            if (!isAdded() || getView() == null) {
                return;
            }
            action.run();
        });
    }

    /**
     * Возвращается на предыдущий экран навигации без падения при отсутствии back stack.
     */
    private void navigateBackSafely() {
        if (!isAdded()) {
            return;
        }
        try {
            if (!NavHostFragment.findNavController(this).navigateUp()) {
                NavHostFragment.findNavController(this).popBackStack();
            }
        } catch (IllegalStateException ignored) {
        }
    }

    /**
     * Возвращает обрезанный текст из поля ввода или пустую строку.
     *
     * @param et поле ввода или {@code null}
     * @return текст поля без пробелов по краям
     */
    private static String text(@Nullable TextInputEditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }
}
