package com.example.samsungproject.Fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.samsungproject.LocalDB.UserDatabaseHelper;
import com.example.samsungproject.R;
import com.example.samsungproject.net.ApiConfig;
import com.example.samsungproject.net.LibBookApiClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class EnterChangeEmailDialogFragment extends DialogFragment {

    private static final String PREFS_NAME = "user_prefs";
    private static final String KEY_SERVER_USER_ID = "server_user_id";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_NAME = "name";
    private static final String ARG_MODE = "mode";
    private static final String MODE_EMAIL = "email";
    private static final String MODE_PASSWORD = "password";
    private static final String MODE_NAME = "name";
    private static final int MIN_NEW_PASSWORD_LENGTH = 8;

    private boolean isNewStep = false;
    private boolean passwordAwaitingNew = false;
    @NonNull private String mode = MODE_EMAIL;

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());


    /**
     * Создаёт диалог смены email, пароля или имени пользователя.
     */
    public EnterChangeEmailDialogFragment() {
        super(R.layout.enterchange);
    }

    /**
     * Настраивает стиль диалога без заголовка окна.
     *
     * @param savedInstanceState сохранённое состояние или {@code null}
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, 0);
    }


    /**
     * Растягивает диалог на весь экран и настраивает поведение клавиатуры.
     */
    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            Window window = getDialog().getWindow();
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }


    /**
     * Настраивает поля ввода и обработку подтверждения смены email, пароля или имени.
     *
     * @param view               корневое представление диалога
     * @param savedInstanceState сохранённое состояние или {@code null}
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View dim = view.findViewById(R.id.ecDimBackground);
        dim.setOnClickListener(v -> dismissAllChangeDialogs(this));

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

        btnConfirm.setOnClickListener(v -> {
            til.setError(null);
            String input = (et != null && et.getText() != null) ? et.getText().toString().trim() : "";
            if (TextUtils.isEmpty(input)) {
                til.setError(getString(R.string.field_required));
                return;
            }

            if (MODE_PASSWORD.equals(mode)) {
                if (!passwordAwaitingNew) {
                    btnConfirm.setEnabled(false);
                    String baseUrl = ApiConfig.baseUrl(requireContext().getApplicationContext());
                    executor.execute(() -> {
                        try {
                            long userId = LibBookApiClient.resolveServerUserId(requireContext().getApplicationContext(), baseUrl);
                            if (userId < 0) {
                                mainHandler.post(() -> {
                                    btnConfirm.setEnabled(true);
                                    til.setError(getString(R.string.change_password_no_user));
                                });
                                return;
                            }
                            LibBookApiClient.HttpResult r = LibBookApiClient.verifyUserPassword(baseUrl, userId, input);
                            JSONObject j = new JSONObject(r.body);
                            boolean ok = r.statusCode == 200 && j.optBoolean("valid", false);
                            mainHandler.post(() -> {
                                btnConfirm.setEnabled(true);
                                if (!ok) {
                                    til.setError(getString(R.string.change_password_wrong));
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
                                til.setError(getString(R.string.register_network_error));
                            });
                        }
                    });
                    return;
                }
                if (input.length() < MIN_NEW_PASSWORD_LENGTH) {
                    til.setError(getString(R.string.change_password_too_short));
                    return;
                }
                btnConfirm.setEnabled(false);
                Context appCtx = requireContext().getApplicationContext();
                String baseUrl = ApiConfig.baseUrl(appCtx);
                executor.execute(() -> {
                    try {
                        long userId = LibBookApiClient.resolveServerUserId(appCtx, baseUrl);
                        if (userId < 0) {
                            mainHandler.post(() -> {
                                btnConfirm.setEnabled(true);
                                til.setError(getString(R.string.change_password_no_user));
                            });
                            return;
                        }
                        LibBookApiClient.HttpResult r = LibBookApiClient.putUserPassword(baseUrl, userId, input);
                        if (r.statusCode == 404) {
                            appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                    .edit()
                                    .remove(KEY_SERVER_USER_ID)
                                    .apply();
                            userId = LibBookApiClient.resolveServerUserId(appCtx, baseUrl);
                            if (userId >= 0) {
                                r = LibBookApiClient.putUserPassword(baseUrl, userId, input);
                            }
                        }
                        final long finalUserId = userId;
                        final LibBookApiClient.HttpResult finalResult = r;
                        mainHandler.post(() -> {
                            btnConfirm.setEnabled(true);
                            if (finalUserId < 0) {
                                til.setError(getString(R.string.change_password_no_user));
                                return;
                            }
                            if (finalResult.statusCode != 200) {
                                til.setError(getString(R.string.change_password_failed));
                                return;
                            }
                            appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                    .edit()
                                    .putLong(KEY_SERVER_USER_ID, finalUserId)
                                    .apply();
                            writePassword(requireContext(), input);
                            Toast.makeText(requireContext(), R.string.change_password_success, Toast.LENGTH_SHORT).show();
                            dismissAllChangeDialogs(EnterChangeEmailDialogFragment.this);
                        });
                    } catch (Exception e) {
                        mainHandler.post(() -> {
                            btnConfirm.setEnabled(true);
                            til.setError(getString(R.string.register_network_error));
                        });
                    }
                });
                return;
            }

            if (MODE_NAME.equals(mode)) {
                btnConfirm.setEnabled(false);
                Context appCtx = requireContext().getApplicationContext();
                String baseUrl = ApiConfig.baseUrl(appCtx);
                executor.execute(() -> {
                    try {
                        long userId = LibBookApiClient.resolveServerUserId(appCtx, baseUrl);
                        if (userId < 0) {
                            mainHandler.post(() -> {
                                btnConfirm.setEnabled(true);
                                til.setError(getString(R.string.change_password_no_user));
                            });
                            return;
                        }
                        String currentName = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                .getString(KEY_NAME, "");
                        if (!input.equalsIgnoreCase(currentName != null ? currentName.trim() : "")) {
                            LibBookApiClient.HttpResult nameLookup =
                                    LibBookApiClient.getUserByName(baseUrl, input);
                            if (LibBookApiClient.isUserNameTakenByOther(nameLookup, userId)) {
                                mainHandler.post(() -> {
                                    btnConfirm.setEnabled(true);
                                    til.setError(getString(R.string.register_name_taken));
                                    Toast.makeText(requireContext(), R.string.register_name_taken, Toast.LENGTH_SHORT).show();
                                });
                                return;
                            }
                            if (nameLookup.statusCode != 404) {
                                mainHandler.post(() -> {
                                    btnConfirm.setEnabled(true);
                                    til.setError(getString(R.string.register_network_error));
                                });
                                return;
                            }
                        }
                        LibBookApiClient.HttpResult r = LibBookApiClient.putUserName(baseUrl, userId, input);
                        if (r.statusCode == 404) {
                            appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                    .edit()
                                    .remove(KEY_SERVER_USER_ID)
                                    .apply();
                            userId = LibBookApiClient.resolveServerUserId(appCtx, baseUrl);
                            if (userId >= 0) {
                                r = LibBookApiClient.putUserName(baseUrl, userId, input);
                            }
                        }
                        final long finalUserId = userId;
                        final LibBookApiClient.HttpResult finalResult = r;
                        mainHandler.post(() -> {
                            btnConfirm.setEnabled(true);
                            if (finalUserId < 0) {
                                til.setError(getString(R.string.change_password_no_user));
                                return;
                            }
                            if (LibBookApiClient.isNameAlreadyExistsError(finalResult)) {
                                til.setError(getString(R.string.register_name_taken));
                                Toast.makeText(requireContext(), R.string.register_name_taken, Toast.LENGTH_SHORT).show();
                                return;
                            }
                            if (!isNameUpdateSuccess(finalResult)) {
                                android.util.Log.e("EnterChangeEmail", "putUserName: "
                                        + finalResult.statusCode + " " + finalResult.body);
                                til.setError(getString(R.string.change_name_failed));
                                return;
                            }
                            appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                    .edit()
                                    .putLong(KEY_SERVER_USER_ID, finalUserId)
                                    .apply();
                            writeName(requireContext(), finalUserId, input);
                            Toast.makeText(requireContext(), R.string.change_name_success, Toast.LENGTH_SHORT).show();
                            dismissAllChangeDialogs(EnterChangeEmailDialogFragment.this);
                        });
                    } catch (Exception e) {
                        mainHandler.post(() -> {
                            btnConfirm.setEnabled(true);
                            til.setError(getString(R.string.register_network_error));
                        });
                    }
                });
                return;
            }

            if (MODE_EMAIL.equals(mode)) {
                if (!isNewStep) {
                    btnConfirm.setEnabled(false);
                    String baseUrl = ApiConfig.baseUrl(requireContext().getApplicationContext());
                    executor.execute(() -> {
                        try {
                            long userId = LibBookApiClient.resolveServerUserId(
                                    requireContext().getApplicationContext(), baseUrl);
                            if (userId < 0) {
                                mainHandler.post(() -> {
                                    btnConfirm.setEnabled(true);
                                    til.setError(getString(R.string.change_email_no_account));
                                });
                                return;
                            }
                            LibBookApiClient.HttpResult r = LibBookApiClient.verifyUserPassword(baseUrl, userId, input);
                            JSONObject j = new JSONObject(r.body != null ? r.body : "{}");
                            boolean ok = r.statusCode == 200 && j.optBoolean("valid", false);
                            mainHandler.post(() -> {
                                btnConfirm.setEnabled(true);
                                if (!ok) {
                                    til.setError(getString(R.string.change_password_wrong));
                                    return;
                                }
                                requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                        .edit()
                                        .putLong(KEY_SERVER_USER_ID, userId)
                                        .apply();
                                isNewStep = true;
                                til.setError(null);
                                if (et != null) {
                                    et.setText("");
                                }
                                applyStepUi(tvTop, et, til);
                            });
                        } catch (Exception e) {
                            mainHandler.post(() -> {
                                btnConfirm.setEnabled(true);
                                til.setError(getString(R.string.register_network_error));
                            });
                        }
                    });
                    return;
                }

                if (!isCompleteEmail(input)) {
                    til.setError(getString(R.string.invalid_email));
                    return;
                }
                String currentEmail = readEmail(requireContext());
                if (input.equalsIgnoreCase(currentEmail != null ? currentEmail.trim() : "")) {
                    til.setError(getString(R.string.change_email_same_as_current));
                    return;
                }

                btnConfirm.setEnabled(false);
                String baseUrl = ApiConfig.baseUrl(requireContext().getApplicationContext());
                String newEmail = input.trim().toLowerCase(Locale.ROOT);
                executor.execute(() -> {
                    try {
                        Context appCtx = requireContext().getApplicationContext();
                        long currentUserId = LibBookApiClient.resolveServerUserId(appCtx, baseUrl);
                        LibBookApiClient.HttpResult taken = LibBookApiClient.getUserByEmail(baseUrl, newEmail);
                        if (taken.statusCode == 200) {
                            try {
                                long takenId = new JSONObject(taken.body).optLong("id", -1L);
                                if (takenId > 0 && takenId != currentUserId) {
                                    mainHandler.post(() -> {
                                        btnConfirm.setEnabled(true);
                                        til.setError(getString(R.string.register_email_taken));
                                    });
                                    return;
                                }
                            } catch (Exception ignored) {
                                mainHandler.post(() -> {
                                    btnConfirm.setEnabled(true);
                                    til.setError(getString(R.string.register_email_taken));
                                });
                                return;
                            }
                        } else if (taken.statusCode != 404) {
                            mainHandler.post(() -> {
                                btnConfirm.setEnabled(true);
                                til.setError(getString(R.string.register_network_error));
                            });
                            return;
                        }
                        if (currentUserId < 0) {
                            mainHandler.post(() -> {
                                btnConfirm.setEnabled(true);
                                til.setError(getString(R.string.change_email_no_account));
                            });
                            return;
                        }
                        LibBookApiClient.HttpResult r =
                                putUserEmailWithRetry(appCtx, baseUrl, newEmail);
                        mainHandler.post(() -> {
                            btnConfirm.setEnabled(true);
                            if (LibBookApiClient.isEmailUpdateSuccess(r, newEmail)) {
                                appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                        .edit()
                                        .putLong(KEY_SERVER_USER_ID, currentUserId)
                                        .apply();
                                writeEmail(requireContext(), newEmail);
                                Toast.makeText(requireContext(), R.string.change_email_success, Toast.LENGTH_SHORT).show();
                                dismissAllChangeDialogs(EnterChangeEmailDialogFragment.this);
                                return;
                            }
                            android.util.Log.e("EnterChangeEmail", "putUserEmail: "
                                    + r.statusCode + " " + r.body);
                            if (r.statusCode == 409) {
                                til.setError(getString(R.string.register_email_taken));
                            } else if (r.statusCode == 404) {
                                til.setError(getString(R.string.change_email_no_account));
                            } else {
                                til.setError(getString(R.string.change_email_failed));
                            }
                        });
                    } catch (Exception e) {
                        mainHandler.post(() -> {
                            btnConfirm.setEnabled(true);
                            til.setError(getString(R.string.register_network_error));
                        });
                    }
                });
                return;
            }

            til.setError(getString(R.string.change_email_no_account));
        });
    }


    /**
     * Обновляет подсказки, тип ввода и иконки поля в зависимости от режима и шага.
     *
     * @param tvTop заголовок текущего шага
     * @param et    поле ввода или {@code null}
     * @param til   контейнер поля ввода
     */
    private void applyStepUi(@NonNull android.widget.TextView tvTop, @Nullable TextInputEditText et, @NonNull TextInputLayout til) {
        til.setHintEnabled(false);
        til.setHint(null);
        til.setEndIconMode(TextInputLayout.END_ICON_NONE);

        if (MODE_PASSWORD.equals(mode)) {
            int inputType = android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;
            til.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
            if (!passwordAwaitingNew) {
                tvTop.setText(R.string.change_password_confirm_hint);
            } else {
                tvTop.setText(R.string.change_password_new_hint);
            }
            if (et != null) {
                et.setHint(null);
                et.setInputType(inputType);
            }
            return;
        }

        if (!isNewStep) {
            if (MODE_NAME.equals(mode)) {
                tvTop.setText(R.string.change_name_title);
                if (et != null) {
                    et.setHint(null);
                    et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
                }
                return;
            }
            if (MODE_EMAIL.equals(mode)) {
                tvTop.setText(R.string.change_email_password_hint);
                til.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
                if (et != null) {
                    et.setHint(null);
                    et.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                            | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                }
                return;
            }
            return;
        }

        if (MODE_NAME.equals(mode)) {
            tvTop.setText(R.string.change_name_title);
            if (et != null) {
                et.setHint(null);
                et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            }
            return;
        }

        tvTop.setText(R.string.change_email_new_title);
        if (et != null) {
            et.setHint(null);
            et.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        }
    }

    /**
     * Проверяет, успешно ли сервер принял обновление имени пользователя.
     *
     * @param r ответ HTTP от сервера
     * @return {@code true}, если имя обновлено
     */
    private static boolean isNameUpdateSuccess(@NonNull LibBookApiClient.HttpResult r) {
        if (r.statusCode != 200) {
            return false;
        }
        try {
            JSONObject j = new JSONObject(r.body != null ? r.body : "{}");
            if (j.optBoolean("ok", false)) {
                return true;
            }
            return j.has("id") && j.has("name");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Проверяет, что строка является полным корректным адресом электронной почты.
     *
     * @param email проверяемый адрес
     * @return {@code true}, если адрес выглядит полным и валидным
     */
    private static boolean isCompleteEmail(@NonNull String email) {
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return false;
        }
        int at = email.indexOf('@');
        if (at <= 0 || at >= email.length() - 1) {
            return false;
        }
        String domain = email.substring(at + 1);
        int dot = domain.lastIndexOf('.');
        return dot > 0 && dot < domain.length() - 1;
    }

    /**
     * Отправляет новый email на сервер с повторной попыткой после сброса кэша идентификатора пользователя.
     *
     * @param context контекст приложения
     * @param baseUrl базовый URL API
     * @param email   новый адрес электронной почты
     * @return результат последнего HTTP-запроса
     * @throws Exception при сетевой или разборной ошибке
     */
    @NonNull
    private static LibBookApiClient.HttpResult putUserEmailWithRetry(
            @NonNull Context context,
            @NonNull String baseUrl,
            @NonNull String email
    ) throws Exception {
        long userId = LibBookApiClient.resolveServerUserId(context, baseUrl);
        if (userId < 0) {
            return new LibBookApiClient.HttpResult(404, "");
        }
        LibBookApiClient.HttpResult r = LibBookApiClient.putUserEmail(baseUrl, userId, email);
        if (LibBookApiClient.isEmailUpdateSuccess(r, email)) {
            return r;
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_SERVER_USER_ID)
                .apply();
        userId = LibBookApiClient.resolveServerUserId(context, baseUrl);
        if (userId >= 0) {
            r = LibBookApiClient.putUserEmail(baseUrl, userId, email);
        }
        return r;
    }


    /**
     * Читает сохранённый email пользователя из настроек.
     *
     * @param context контекст приложения
     * @return email или пустая строка
     */
    private static String readEmail(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_EMAIL, "");
    }


    /**
     * Сохраняет новый email пользователя в локальные настройки.
     *
     * @param context контекст приложения
     * @param email   новый адрес электронной почты
     */
    private static void writeEmail(@NonNull Context context, @NonNull String email) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_EMAIL, email).apply();
    }


    /**
     * Сохраняет новый пароль пользователя в локальные настройки.
     *
     * @param context  контекст приложения
     * @param password новый пароль
     */
    private static void writePassword(@NonNull Context context, @NonNull String password) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_PASSWORD, password).apply();
    }


    /**
     * Сохраняет новое имя пользователя в настройках и локальной базе данных.
     *
     * @param context контекст приложения
     * @param userId  идентификатор пользователя на сервере
     * @param name    новое отображаемое имя
     */
    private static void writeName(@NonNull Context context, long userId, @NonNull String name) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_NAME, name).apply();
        if (userId >= 0) {
            UserDatabaseHelper db = new UserDatabaseHelper(context);
            UserDatabaseHelper.UserRow row = db.getUser(userId);
            byte[] icon = row != null ? row.icon : null;
            db.upsertUser(userId, name, icon);
        }
    }


    /**
     * Закрывает все диалоги изменения профиля и возвращает к экрану настроек.
     *
     * @param fragment фрагмент, из которого выполняется навигация
     */
    static void dismissAllChangeDialogs(@NonNull Fragment fragment) {
        NavHostFragment.findNavController(fragment).popBackStack(R.id.settingsFragment, false);
    }
}
