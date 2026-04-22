package com.example.samsungproject.Fragment;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.samsungproject.R;
import com.google.android.material.button.MaterialButton;

public class NotificationsFragment extends Fragment {
    public NotificationsFragment() {
        super(R.layout.notifications);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton btnBackNts = view.findViewById(R.id.btnBackNTS);
        btnBackNts.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_notificationsFragment_to_generalFragment)
        );
    }
}

