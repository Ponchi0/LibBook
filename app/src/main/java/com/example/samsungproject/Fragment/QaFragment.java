package com.example.samsungproject.Fragment;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.samsungproject.R;
import com.google.android.material.button.MaterialButton;

public class QaFragment extends Fragment {
    /**
     * Создаёт фрагмент "Вопросы и ответы" и привязывает разметку экрана.
     */
    public QaFragment() {
        super(R.layout.qa);
    }

    /**
     * Настраивает кнопку "Назад" и навигацию на предыдущий экран.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton btnBackQa = view.findViewById(R.id.btnBackQA);
        btnBackQa.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_qaFragment_to_generalFragment)
        );
    }
}

