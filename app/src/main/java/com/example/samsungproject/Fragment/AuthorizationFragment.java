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
import com.example.samsungproject.net.SamsungApiClient;
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

    public AuthorizationFragment() {
        super(R.layout.authorization);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextInputLayout tilEmail = view.findViewById(R.id.tilAuthorizationEmail);
        TextInputLayout tilPassword = view.findViewById(R.id.tilAuthorizationPassword);
        TextInputEditText etEmail = view.findViewById(R.id.etAuthorizationEmail);
        TextInputEditText etPassword = view.findViewById(R.id.etAuthorizationPassword);
        MaterialButton btnSubmit = view.findViewById(R.id.btnAuthorizationSubmit);
        MaterialButton btnCancel = view.findViewById(R.id.btnAuthorizationCancel);

        btnCancel.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());

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
            String baseUrl = getString(R.string.api_base_url);
            Context ctx = requireContext().getApplicationContext();

            executor.execute(() -> {
                try {
                    SamsungApiClient.HttpResult lookup = SamsungApiClient.getUserByEmail(baseUrl, email);
                    if (lookup.statusCode != 200) {
                        mainHandler.post(() -> {
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
                        mainHandler.post(() -> {
                            btnSubmit.setEnabled(true);
                            Toast.makeText(requireContext(), R.string.login_unknown_email, Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }

                    SamsungApiClient.HttpResult verify = SamsungApiClient.verifyUserPassword(baseUrl, userId, password);
                    JSONObject vBody = new JSONObject(
                            verify.body != null && verify.body.length() > 0 ? verify.body : "{}");
                    boolean ok = verify.statusCode == 200 && vBody.optBoolean("valid", false);
                    if (!ok) {
                        mainHandler.post(() -> {
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

                    mainHandler.post(() -> {
                        if (!isAdded()) return;
                        btnSubmit.setEnabled(true);
                        Toast.makeText(requireContext(), R.string.login_success, Toast.LENGTH_SHORT).show();
                        NavHostFragment.findNavController(AuthorizationFragment.this).popBackStack();
                    });
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        if (!isAdded()) return;
                        btnSubmit.setEnabled(true);
                        Toast.makeText(requireContext(), R.string.register_network_error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
    }

    private static String text(@Nullable TextInputEditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }
}
