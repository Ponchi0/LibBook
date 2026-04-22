package com.example.samsungproject.Fragment;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.samsungproject.R;
import com.google.android.material.button.MaterialButton;

public class MainBookPageFragment extends Fragment {

    private boolean isDescriptionExpanded = false;
    private boolean isTagsExpanded = false;

    public MainBookPageFragment() {
        super(R.layout.mainbookpage);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton btnMarkbooks = view.findViewById(R.id.btn_mbpMarkbooks);
        btnMarkbooks.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_mainBookPageFragment_to_markbooksFragment)
        );

        MaterialButton btnCatalog = view.findViewById(R.id.btn_mbpCatalog);
        btnCatalog.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_mainBookPageFragment_to_catalogFragment)
        );

        MaterialButton btnMainMenu = view.findViewById(R.id.btn_mbpMainMenu);
        btnMainMenu.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_mainBookPageFragment_to_mainMenuFragment)
        );

        MaterialButton btnGeneral = view.findViewById(R.id.btn_mbpGeneral);
        btnGeneral.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_mainBookPageFragment_to_generalFragment)
        );

        TextView tvDescription = view.findViewById(R.id.tvDescription);
        MaterialButton btnDescriptionMore = view.findViewById(R.id.mbpbtnDescriptionMore);
        btnDescriptionMore.setOnClickListener(v -> {
            isDescriptionExpanded = !isDescriptionExpanded;
            if (isDescriptionExpanded) {
                tvDescription.setMaxLines(Integer.MAX_VALUE);
                tvDescription.setEllipsize(null);
                btnDescriptionMore.setText("Свернуть");
            } else {
                tvDescription.setMaxLines(3);
                tvDescription.setEllipsize(TextUtils.TruncateAt.END);
                btnDescriptionMore.setText("Ещё");
            }
        });

        TextView tvTags = view.findViewById(R.id.mbptvTags);
        MaterialButton btnTagsMore = view.findViewById(R.id.mbpbtnTagsMore);
        btnTagsMore.setOnClickListener(v -> {
            isTagsExpanded = !isTagsExpanded;
            if (isTagsExpanded) {
                tvTags.setMaxLines(Integer.MAX_VALUE);
                tvTags.setEllipsize(null);
                btnTagsMore.setText("Свернуть");
            } else {
                tvTags.setMaxLines(2);
                tvTags.setEllipsize(TextUtils.TruncateAt.END);
                btnTagsMore.setText("Ещё");
            }
        });
    }
}

