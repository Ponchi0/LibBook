package com.example.samsungproject.Fragment;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.samsungproject.R;
import com.google.android.material.button.MaterialButton;

public class GeneralFragment extends Fragment {
    public GeneralFragment() {
        super(R.layout.activity_general);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton btnMarkbooks = view.findViewById(R.id.btn_glMarkbooks);
        btnMarkbooks.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_generalFragment_to_markbooksFragment)
        );

        MaterialButton btnCatalog = view.findViewById(R.id.btn_glCatalog);
        btnCatalog.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_generalFragment_to_catalogFragment)
        );

        MaterialButton btnMainMenu = view.findViewById(R.id.btn_glMainMenu);
        btnMainMenu.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_generalFragment_to_mainMenuFragment)
        );

        MaterialButton btnInfo = view.findViewById(R.id.btn_Info);
        btnInfo.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_generalFragment_to_infoFragment)
        );

        MaterialButton btnNotifications = view.findViewById(R.id.btn_Notifications);
        btnNotifications.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_generalFragment_to_notificationsFragment)
        );

        MaterialButton btnQa = view.findViewById(R.id.btn_QA);
        btnQa.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_generalFragment_to_qaFragment)
        );

        MaterialButton btnSettings = view.findViewById(R.id.btn_Settings);
        btnSettings.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_generalFragment_to_settingsFragment)
        );
    }
}

