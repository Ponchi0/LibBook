package com.example.samsungproject.Fragment;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.samsungproject.R;
import com.google.android.material.button.MaterialButton;

public class InfoFragment extends Fragment {
    public InfoFragment() {
        super(R.layout.info);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton btnBackInfo = view.findViewById(R.id.btnBackInfo);
        btnBackInfo.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_infoFragment_to_generalFragment)
        );
    }
}

