package com.example.samsungproject.Fragment;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.samsungproject.R;
import com.google.android.material.button.MaterialButton;

public class MainMenuFragment extends Fragment {
    public MainMenuFragment() {
        super(R.layout.activity_mainmenu);
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton btnUserIcon = view.findViewById(R.id.btn_UserIcon);
        btnUserIcon.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_mainMenuFragment_to_generalFragment)
        );

        MaterialButton btnCatalog = view.findViewById(R.id.btn_mmCatalog);
        btnCatalog.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_mainMenuFragment_to_catalogFragment)
        );

        MaterialButton btnGeneral = view.findViewById(R.id.btn_mmGeneral);
        btnGeneral.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_mainMenuFragment_to_generalFragment)
        );

        MaterialButton btnMarkbooks = view.findViewById(R.id.btn_mmMarkbooks);
        btnMarkbooks.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_mainMenuFragment_to_markbooksFragment)
        );
    }
}
