package com.example.samsungproject.Fragment;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.samsungproject.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class ChangeEmailConfNewDialogFragment extends DialogFragment {

    private static final String KEY_STEP = "enterchange_step";

    /**
     * Создаёт диалог подтверждения (экран с вводом кода) и привязывает разметку.
     */
    public ChangeEmailConfNewDialogFragment() {
        super(R.layout.changeemailconfnew);
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
     * Настраивает затемнение фона, тексты/поле ввода и обработчик подтверждения кода (смена email).
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View dim = view.findViewById(R.id.cecnDimBackground);
        dim.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        final android.widget.TextView tvTop = view.findViewById(R.id.cecntvTopLeft);
        final TextInputLayout til = view.findViewById(R.id.tilInput);
        final TextInputEditText et = view.findViewById(R.id.cecnetInput);
        final MaterialButton btnConfirm = view.findViewById(R.id.cecbtnConfirm);

        tvTop.setText("Confirm the email by specifying the code from the message");
        til.setHint("Code");
        if (et != null) et.setInputType(android.text.InputType.TYPE_CLASS_TEXT);

        btnConfirm.setOnClickListener(v -> {
            til.setError(null);
            String code = (et != null && et.getText() != null) ? et.getText().toString().trim() : "";
            if (TextUtils.isEmpty(code)) {
                til.setError("Required");
                return;
            }

            NavController nav = NavHostFragment.findNavController(this);
            NavBackStackEntry previous = nav.getPreviousBackStackEntry();
            if (previous != null) {
                previous.getSavedStateHandle().set(KEY_STEP, "new");
            }
            nav.popBackStack();
        });
    }
}
