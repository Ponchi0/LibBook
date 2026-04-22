package com.example.samsungproject.Fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class EnterChangeEmailDialogFragment extends DialogFragment {

    private static final String PREFS_NAME = "user_prefs";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_NAME = "name";
    private static final String KEY_STEP = "enterchange_step";
    private static final String ARG_MODE = "mode";
    private static final String MODE_EMAIL = "email";
    private static final String MODE_PASSWORD = "password";
    private static final String MODE_NAME = "name";

    private boolean isNewStep = false;
    @NonNull private String mode = MODE_EMAIL;

    public EnterChangeEmailDialogFragment() {
        super(R.layout.enterchange);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, 0);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            Window window = getDialog().getWindow();
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

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

            if (!isNewStep) {
                if (MODE_NAME.equals(mode)) {
                    writeName(requireContext(), input);
                    nav.popBackStack();
                    return;
                }
                String savedEmail = readEmail(requireContext());
                if (!TextUtils.equals(input, savedEmail)) {
                    til.setError("Email does not match");
                    return;
                }
                Bundle toCodeArgs = new Bundle();
                toCodeArgs.putString(ARG_MODE, mode);
                nav.navigate(R.id.action_enterChangeEmailDialogFragment_to_changeEmailConfNewDialogFragment, toCodeArgs);
                return;
            }

            if (MODE_PASSWORD.equals(mode)) {
                writePassword(requireContext(), input);
            } else if (MODE_NAME.equals(mode)) {
                writeName(requireContext(), input);
            } else {
                writeEmail(requireContext(), input);
            }
            nav.popBackStack();
        });
    }

    private void applyStepUi(@NonNull android.widget.TextView tvTop, @Nullable TextInputEditText et, @NonNull TextInputLayout til) {
        if (!isNewStep) {
            if (MODE_NAME.equals(mode)) {
                tvTop.setText("Enter a new name.");
                til.setHint("Enter a new name.");
                if (et != null) et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
                return;
            }
            if (MODE_PASSWORD.equals(mode)) {
                tvTop.setText("Enter email for confirmation.");
                til.setHint("Enter email for confirmation.");
                if (et != null) et.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            } else {
                tvTop.setText("Enter old mail");
                til.setHint("Enter old mail");
                if (et != null) et.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            }
            return;
        }

        if (MODE_NAME.equals(mode)) {
            tvTop.setText("Enter a new name.");
            til.setHint("Enter a new name.");
            if (et != null) et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            return;
        }

        if (MODE_PASSWORD.equals(mode)) {
            tvTop.setText("Enter a new password");
            til.setHint("Enter a new password");
            if (et != null) et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else {
            tvTop.setText("Enter a new email");
            til.setHint("Enter a new email");
            if (et != null) et.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        }
    }

    private static String readEmail(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_EMAIL, "");
    }

    private static void writeEmail(@NonNull Context context, @NonNull String email) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_EMAIL, email).apply();
    }

    private static void writePassword(@NonNull Context context, @NonNull String password) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_PASSWORD, password).apply();
    }

    private static void writeName(@NonNull Context context, @NonNull String name) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_NAME, name).apply();
    }
}

