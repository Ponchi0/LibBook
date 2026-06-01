package com.example.samsungproject.Fragment;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.samsungproject.R;
import com.example.samsungproject.net.ApiConfig;
import com.example.samsungproject.net.LibBookApiClient;
import com.example.samsungproject.util.SessionHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class DeleteAccountDialogFragment extends DialogFragment {

    public static final String REQUEST_KEY = "delete_account_result";
    public static final String BUNDLE_SUCCESS = "success";

    private static final long CONFIRM_DELAY_MS = 10_000L;
    private static final long TICK_MS = 1_000L;

    private final Executor executor = Executors.newSingleThreadExecutor();

    @Nullable
    private CountDownTimer confirmCountdown;

    /** Создаёт экземпляр диалога подтверждения удаления аккаунта. */
    public DeleteAccountDialogFragment() {
        super(R.layout.delete_account_confirm);
    }

    /** Инициализирует стиль диалога без заголовка. */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, 0);
    }

    /** Растягивает окно диалога на весь экран с прозрачным фоном. */
    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            Window window = getDialog().getWindow();
            window.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
            );
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    /** Настраивает форму подтверждения удаления аккаунта и обработчики кнопок. */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.daDimBackground).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btnDeleteAccountCancel).setOnClickListener(v -> dismiss());

        if (!SessionHelper.isLoggedIn(requireContext())) {
            dismiss();
            return;
        }

        TextInputLayout tilPass = view.findViewById(R.id.tilDeleteAccountPassword);
        TextInputEditText etPass = view.findViewById(R.id.etDeleteAccountPassword);
        MaterialButton btnConfirm = view.findViewById(R.id.btnDeleteAccountConfirm);

        startConfirmCountdown(btnConfirm);

        btnConfirm.setOnClickListener(v -> {
            if (!btnConfirm.isEnabled()) {
                return;
            }
            String pass = etPass.getText() != null ? etPass.getText().toString() : "";
            if (pass.isEmpty()) {
                tilPass.setError(getString(R.string.field_required));
                return;
            }
            tilPass.setError(null);
            btnConfirm.setEnabled(false);
            long uid = SessionHelper.getServerUserId(requireContext());
            String baseUrl = ApiConfig.baseUrl(requireContext().getApplicationContext());
            executor.execute(() -> {
                try {
                    LibBookApiClient.HttpResult r = LibBookApiClient.deleteUserAccount(baseUrl, uid, pass);
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) {
                            return;
                        }
                        if (r.statusCode == 200) {
                            Bundle result = new Bundle();
                            result.putBoolean(BUNDLE_SUCCESS, true);
                            getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
                            dismiss();
                        } else if (r.statusCode == 401) {
                            btnConfirm.setEnabled(true);
                            Toast.makeText(requireContext(), R.string.login_wrong_password, Toast.LENGTH_SHORT).show();
                        } else {
                            btnConfirm.setEnabled(true);
                            Toast.makeText(requireContext(), R.string.register_network_error, Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) {
                            return;
                        }
                        btnConfirm.setEnabled(true);
                        Toast.makeText(requireContext(), R.string.register_network_error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
    }

    /** Запускает обратный отсчёт перед активацией кнопки подтверждения. */
    private void startConfirmCountdown(@NonNull MaterialButton btnConfirm) {
        if (confirmCountdown != null) {
            confirmCountdown.cancel();
        }
        btnConfirm.setEnabled(false);
        confirmCountdown = new CountDownTimer(CONFIRM_DELAY_MS, TICK_MS) {
            /** Обновляет текст кнопки с оставшимся временем ожидания. */
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = (millisUntilFinished + TICK_MS - 1) / TICK_MS;
                btnConfirm.setText(getString(R.string.delete_account_confirm_wait, seconds));
            }

            /** Активирует кнопку подтверждения по окончании отсчёта. */
            @Override
            public void onFinish() {
                btnConfirm.setEnabled(true);
                btnConfirm.setText(R.string.delete_account_confirm);
            }
        };
        confirmCountdown.start();
    }

    /** Отменяет таймер обратного отсчёта при уничтожении представления. */
    @Override
    public void onDestroyView() {
        if (confirmCountdown != null) {
            confirmCountdown.cancel();
            confirmCountdown = null;
        }
        super.onDestroyView();
    }
}
