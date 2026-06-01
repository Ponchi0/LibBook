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

public class AuthorizationFragment extends Fragment {

    private static final String PREFS_NAME = "user_prefs";
    private static final String KEY_SERVER_USER_ID = "server_user_id";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_NAME = "name";

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean submitInProgress;

    /** Создаёт экземпляр фрагмента авторизации. */
    public AuthorizationFragment() {
        super(R.layout.authorization);
    }

    /** Настраивает форму входа и обработчики кнопок. */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextInputLayout tilEmail = view.findViewById(R.id.tilAuthorizationEmail);
        TextInputLayout tilPassword = view.findViewById(R.id.tilAuthorizationPassword);
        TextInputEditText etEmail = view.findViewById(R.id.etAuthorizationEmail);
        TextInputEditText etPassword = view.findViewById(R.id.etAuthorizationPassword);
        MaterialButton btnSubmit = view.findViewById(R.id.btnAuthorizationSubmit);
        MaterialButton btnCancel = view.findViewById(R.id.btnAuthorizationCancel);

        btnCancel.setOnClickListener(v -> navigateBackSafely());

        btnSubmit.setOnClickListener(v -> {
            tilEmail.setError(null);
            tilPassword.setError(null);

            String email = text(etEmail);
            String password = text(etPassword);

            if (TextUtils.isEmpty(email)) {
                tilEmail.setError(getString(R.string.field_required));
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.setError(getString(R.string.invalid_email));
                return;
            }
            if (TextUtils.isEmpty(password)) {
                tilPassword.setError(getString(R.string.field_required));
                return;
            }

            btnSubmit.setEnabled(false);
            submitInProgress = true;
            String baseUrl = ApiConfig.baseUrl(requireContext().getApplicationContext());
            Context ctx = requireContext().getApplicationContext();

            executor.execute(() -> {
                try {
                    if (!submitInProgress) {
                        return;
                    }
                    LibBookApiClient.HttpResult lookup = LibBookApiClient.getUserByEmail(baseUrl, email);
                    if (lookup.statusCode != 200) {
                        postUi(() -> {
                            btnSubmit.setEnabled(true);
                            tilEmail.setError(getString(R.string.login_unknown_email));
                            Toast.makeText(requireContext(), R.string.login_unknown_email, Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }

                    JSONObject user = new JSONObject(
                            lookup.body != null && !lookup.body.trim().isEmpty() ? lookup.body : "{}");
                    long userId = user.optLong("id", -1L);
                    if (userId < 0) {
                        postUi(() -> {
                            btnSubmit.setEnabled(true);
                            Toast.makeText(requireContext(), R.string.login_unknown_email, Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }

                    LibBookApiClient.HttpResult verify = LibBookApiClient.verifyUserPassword(baseUrl, userId, password);
                    JSONObject vBody = new JSONObject(
                            verify.body != null && verify.body.length() > 0 ? verify.body : "{}");
                    boolean ok = verify.statusCode == 200 && vBody.optBoolean("valid", false);
                    if (!ok) {
                        postUi(() -> {
                            btnSubmit.setEnabled(true);
                            tilPassword.setError(getString(R.string.login_wrong_password));
                            Toast.makeText(requireContext(), R.string.login_wrong_password, Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }

                    String name = user.optString("name", "");
                    if (name.trim().isEmpty()) {
                        name = User.getName_null(ctx);
                    }
                    byte[] iconBytes = UserResponseParser.decodeUserIcon(user);

                    SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    prefs.edit()
                            .putLong(KEY_SERVER_USER_ID, userId)
                            .putString(KEY_EMAIL, email)
                            .putString(KEY_NAME, name)
                            .putString(KEY_PASSWORD, password)
                            .apply();

                    UserDatabaseHelper db = new UserDatabaseHelper(ctx);
                    db.upsertUser(userId, name, iconBytes);

                    BookmarkSyncHelper.syncAsync(ctx, null);

                    postUi(() -> {
                        btnSubmit.setEnabled(true);
                        Toast.makeText(requireContext(), R.string.login_success, Toast.LENGTH_SHORT).show();
                        navigateBackSafely();
                    });
                } catch (Exception e) {
                    postUi(() -> {
                        btnSubmit.setEnabled(true);
                        Toast.makeText(requireContext(), R.string.register_network_error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
    }

    /** Отменяет незавершённый вход и очищает отложенные задачи UI. */
    @Override
    public void onDestroyView() {
        submitInProgress = false;
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }

    /** Выполняет действие в главном потоке, если фрагмент ещё активен. */
    private void postUi(@NonNull Runnable action) {
        mainHandler.post(() -> {
            if (!isAdded() || getView() == null) {
                return;
            }
            action.run();
        });
    }

    /** Безопасно возвращается на предыдущий экран навигации. */
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

    /** Возвращает обрезанный текст из поля ввода или пустую строку. */
    private static String text(@Nullable TextInputEditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }
}
