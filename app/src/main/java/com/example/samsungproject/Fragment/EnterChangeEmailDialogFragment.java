package com.example.samsungproject.Fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.MutableLiveData;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.samsungproject.R;
import com.example.samsungproject.net.SamsungApiClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class EnterChangeEmailDialogFragment extends DialogFragment {

    private static final String PREFS_NAME = "user_prefs";
    private static final String KEY_SERVER_USER_ID = "server_user_id";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_NAME = "name";
    private static final String KEY_STEP = "enterchange_step";
    private static final String ARG_MODE = "mode";
    private static final String MODE_EMAIL = "email";
    private static final String MODE_PASSWORD = "password";
    private static final String MODE_NAME = "name";

    private boolean isNewStep = false;
    private boolean passwordAwaitingNew = false;
    @NonNull private String mode = MODE_EMAIL;

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Создаёт диалог ввода (используется для смены email/пароля/имени) и привязывает разметку.
     */
    public EnterChangeEmailDialogFragment() {
        super(R.layout.enterchange);
    }

    /**
     * Настраивает стиль диалога (без заголовка).
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, 0);
    }

    /**
     * Делает диалог полноэкранным и задаёт прозрачный фон окна.
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
     * Настраивает затемнение фона, поля ввода, тексты и обработчик подтверждения действия.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View dim = view.findViewById(R.id.ecDimBackground);
        dim.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        final android.widget.TextView tvTop = view.findViewById(R.id.ectvTopLeft);
        final TextInputLayout til = view.findViewById(R.id.tilInput);
        final TextInputEditText et = view.findViewById(R.id.ecetInput);
        final MaterialButton btnConfirm = view.findViewById(R.id.ecbtnConfirm);

        Bundle args = getArguments();
        if (args != null) {
            String argMode = args.getString(ARG_MODE, MODE_EMAIL);
            if (MODE_PASSWORD.equals(argMode)) {
                mode = MODE_PASSWORD;
            } else if (MODE_NAME.equals(argMode)) {
                mode = MODE_NAME;
            } else {
                mode = MODE_EMAIL;
            }
        }

        applyStepUi(tvTop, et, til);

        NavController nav = NavHostFragment.findNavController(this);
        NavBackStackEntry entry = nav.getCurrentBackStackEntry();
        if (entry != null) {
            MutableLiveData<String> liveData = entry.getSavedStateHandle().getLiveData(KEY_STEP);
            liveData.observe(getViewLifecycleOwner(), step -> {
                if (!MODE_EMAIL.equals(mode)) {
                    return;
                }
                if ("new".equals(step)) {
                    isNewStep = true;
                    entry.getSavedStateHandle().remove(KEY_STEP);
                    til.setError(null);
                    if (et != null) et.setText("");
                    applyStepUi(tvTop, et, til);
                }
            });
        }

        btnConfirm.setOnClickListener(v -> {
            til.setError(null);
            String input = (et != null && et.getText() != null) ? et.getText().toString().trim() : "";
            if (TextUtils.isEmpty(input)) {
                til.setError("Required");
                return;
            }

            if (MODE_PASSWORD.equals(mode)) {
                if (!passwordAwaitingNew) {
                    btnConfirm.setEnabled(false);
                    String baseUrl = getString(R.string.api_base_url);
                    executor.execute(() -> {
                        try {
                            long userId = resolveServerUserId(requireContext(), baseUrl);
                            if (userId < 0) {
                                mainHandler.post(() -> {
                                    btnConfirm.setEnabled(true);
                                    til.setError("No user on server; set email in app or register");
                                });
                                return;
                            }
                            SamsungApiClient.HttpResult r = SamsungApiClient.verifyUserPassword(baseUrl, userId, input);
                            JSONObject j = new JSONObject(r.body);
                            boolean ok = r.statusCode == 200 && j.optBoolean("valid", false);
                            mainHandler.post(() -> {
                                btnConfirm.setEnabled(true);
                                if (!ok) {
                                    til.setError("Password does not match");
                                    return;
                                }
                                requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                        .edit()
                                        .putLong(KEY_SERVER_USER_ID, userId)
                                        .apply();
                                passwordAwaitingNew = true;
                                til.setError(null);
                                if (et != null) et.setText("");
                                applyStepUi(tvTop, et, til);
                            });
                        } catch (Exception e) {
                            mainHandler.post(() -> {
                                btnConfirm.setEnabled(true);
                                til.setError("Network error");
                            });
                        }
                    });
                    return;
                }
                long userId = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getLong(KEY_SERVER_USER_ID, -1L);
                if (userId < 0) {
                    til.setError("User not loaded");
                    return;
                }
                btnConfirm.setEnabled(false);
                String baseUrl = getString(R.string.api_base_url);
                executor.execute(() -> {
                    try {
                        SamsungApiClient.HttpResult r = SamsungApiClient.putUserPassword(baseUrl, userId, input);
                        mainHandler.post(() -> {
                            btnConfirm.setEnabled(true);
                            if (r.statusCode != 200) {
                                til.setError("Could not update password");
                                return;
                            }
                            writePassword(requireContext(), input);
                            nav.popBackStack();
                        });
                    } catch (Exception e) {
                        mainHandler.post(() -> {
                            btnConfirm.setEnabled(true);
                            til.setError("Network error");
                        });
                    }
                });
                return;
            }

            if (MODE_NAME.equals(mode)) {
                btnConfirm.setEnabled(false);
                String baseUrl = getString(R.string.api_base_url);
                executor.execute(() -> {
                    try {
                        long userId = resolveServerUserId(requireContext(), baseUrl);
                        if (userId < 0) {
                            mainHandler.post(() -> {
                                btnConfirm.setEnabled(true);
                                til.setError("No user on server; set email in app or register");
                            });
                            return;
                        }
                        SamsungApiClient.HttpResult r = SamsungApiClient.putUserName(baseUrl, userId, input);
                        mainHandler.post(() -> {
                            btnConfirm.setEnabled(true);
                            if (r.statusCode != 200) {
                                til.setError("Could not update name");
                                return;
                            }
                            writeName(requireContext(), input);
                            nav.popBackStack();
                        });
                    } catch (Exception e) {
                        mainHandler.post(() -> {
                            btnConfirm.setEnabled(true);
                            til.setError("Network error");
                        });
                    }
                });
                return;
            }

            if (!isNewStep) {
                String savedEmail = readEmail(requireContext());
                if (!TextUtils.equals(input, savedEmail)) {
                    til.setError("Email does not match");
                    return;
                }
                Bundle toCodeArgs = new Bundle();
                toCodeArgs.putString(ARG_MODE, mode);
                toCodeArgs.putString("resetEmail", input);
                nav.navigate(R.id.action_enterChangeEmailDialogFragment_to_changeEmailConfNewDialogFragment, toCodeArgs);
                return;
            }

            writeEmail(requireContext(), input);
            nav.popBackStack();
        });
    }

    /**
     * Обновляет тексты и тип ввода в зависимости от текущего шага и режима.
     */
    private void applyStepUi(@NonNull android.widget.TextView tvTop, @Nullable TextInputEditText et, @NonNull TextInputLayout til) {
        if (MODE_PASSWORD.equals(mode)) {
            if (!passwordAwaitingNew) {
                tvTop.setText("Enter the password to confirm");
                til.setHint("Enter the password to confirm");
                if (et != null) {
                    et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                }
            } else {
                tvTop.setText("Enter a new password");
                til.setHint("Enter a new password");
                if (et != null) {
                    et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                }
            }
            return;
        }

        if (!isNewStep) {
            if (MODE_NAME.equals(mode)) {
                tvTop.setText("Enter a new name.");
                til.setHint("Enter a new name.");
                if (et != null) et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
                return;
            }
            tvTop.setText("Enter old mail");
            til.setHint("Enter old mail");
            if (et != null) et.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            return;
        }

        if (MODE_NAME.equals(mode)) {
            tvTop.setText("Enter a new name.");
            til.setHint("Enter a new name.");
            if (et != null) et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            return;
        }

        tvTop.setText("Enter a new email");
        til.setHint("Enter a new email");
        if (et != null) et.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    }

    private static long resolveServerUserId(@NonNull Context context, @NonNull String baseUrl) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long id = prefs.getLong(KEY_SERVER_USER_ID, -1L);
        if (id >= 0) {
            return id;
        }
        String email = readEmail(context);
        if (TextUtils.isEmpty(email)) {
            return -1L;
        }
        try {
            SamsungApiClient.HttpResult r = SamsungApiClient.getUserByEmail(baseUrl, email);
            if (r.statusCode != 200) {
                return -1L;
            }
            JSONObject user = new JSONObject(r.body);
            long userId = user.optLong("id", -1L);
            if (userId >= 0) {
                prefs.edit().putLong(KEY_SERVER_USER_ID, userId).apply();
            }
            return userId;
        } catch (Exception e) {
            return -1L;
        }
    }

    /**
     * Читает сохранённый email из SharedPreferences.
     */
    private static String readEmail(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_EMAIL, "");
    }

    /**
     * Сохраняет email в SharedPreferences.
     */
    private static void writeEmail(@NonNull Context context, @NonNull String email) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_EMAIL, email).apply();
    }

    /**
     * Сохраняет пароль в SharedPreferences.
     */
    private static void writePassword(@NonNull Context context, @NonNull String password) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_PASSWORD, password).apply();
    }

    /**
     * Сохраняет имя пользователя в SharedPreferences.
     */
    private static void writeName(@NonNull Context context, @NonNull String name) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_NAME, name).apply();
    }
}

