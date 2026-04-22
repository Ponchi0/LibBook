package com.example.samsungproject.Fragment;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.samsungproject.R;
import com.google.android.material.button.MaterialButton;

public class CatalogFragment extends Fragment {
    public CatalogFragment() {
        super(R.layout.activity_catalog);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton btnMarkbooks = view.findViewById(R.id.btn_cgMarkbooks);
        btnMarkbooks.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_catalogFragment_to_markbooksFragment)
        );

        MaterialButton btnMainMenu = view.findViewById(R.id.btn_cgMainMenu);
        btnMainMenu.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_catalogFragment_to_mainMenuFragment)
        );

        MaterialButton btnGeneral = view.findViewById(R.id.btn_cgGeneral);
        btnGeneral.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_catalogFragment_to_generalFragment)
        );
    }
}

