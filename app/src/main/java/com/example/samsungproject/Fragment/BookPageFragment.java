package com.example.samsungproject.Fragment;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.samsungproject.R;
import com.google.android.material.button.MaterialButton;

public class BookPageFragment extends Fragment {
    public BookPageFragment() {
        super(R.layout.bookpage);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton btnPageTitle = view.findViewById(R.id.bpbtnPageTitle);
        btnPageTitle.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_bookPageFragment_to_mainBookPageFragment)
        );
    }
}

