package com.example.samsungproject.Fragment;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.samsungproject.R;
import com.example.samsungproject.util.SessionHelper;
import com.google.android.material.button.MaterialButton;

public class SettingsFragment extends Fragment {
    private static final String ARG_MODE = "mode";
    private static final String MODE_EMAIL = "email";
    private static final String MODE_PASSWORD = "password";
    private static final String MODE_NAME = "name";

    /**
     * Создаёт фрагмент экрана настроек.
     */
    public SettingsFragment() {
        super(R.layout.settings);
    }

    /**
     * Настраивает кнопки возврата и перехода к смене email, пароля, имени и аватара.
     *
     * @param view               корневое представление фрагмента
     * @param savedInstanceState сохранённое состояние или {@code null}
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton btnBack = view.findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());

        MaterialButton btnChangeEmail = view.findViewById(R.id.btnChangeEmail);
        btnChangeEmail.setOnClickListener(v -> {
            if (!SessionHelper.isLoggedIn(requireContext())) {
                SessionHelper.showLoginRequiredToast(requireContext());
                return;
            }
            Bundle args = new Bundle();
            args.putString(ARG_MODE, MODE_EMAIL);
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_settingsFragment_to_enterChangeEmailDialogFragment, args);
        });

        MaterialButton btnChangePassword = view.findViewById(R.id.btnChangePassword);
        btnChangePassword.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString(ARG_MODE, MODE_PASSWORD);
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_settingsFragment_to_enterChangeEmailDialogFragment, args);
        });

        MaterialButton btnChangeName = view.findViewById(R.id.btnChangeName);
        btnChangeName.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString(ARG_MODE, MODE_NAME);
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_settingsFragment_to_enterChangeEmailDialogFragment, args);
        });

        MaterialButton btnChangePicture = view.findViewById(R.id.btnChangePicture);
        btnChangePicture.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_settingsFragment_to_uploadImageFragment));
    }
}
