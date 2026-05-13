package com.example.samsungproject.Fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.samsungproject.LocalDB.UserDatabaseHelper;
import com.example.samsungproject.R;
import com.example.samsungproject.domain.User;
import com.google.android.material.button.MaterialButton;

public class GeneralFragment extends Fragment {

    private static final String PREFS_NAME = "user_prefs";
    private static final String KEY_SERVER_USER_ID = "server_user_id";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_NAME = "name";

    /**
     * Создаёт фрагмент и привязывает разметку экрана "Общее".
     */
    public GeneralFragment() {
        super(R.layout.activity_general);
    }

    /**
     * Настраивает обработчики нажатий и навигацию по кнопкам экрана.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton btnAddBook = view.findViewById(R.id.btnAddBook);
        btnAddBook.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_generalFragment_to_addBookFragment)
        );

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

        refreshUserHeader(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        View v = getView();
        if (v != null) {
            refreshUserHeader(v);
        }
    }

    private void refreshUserHeader(@NonNull View view) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long uid = prefs.getLong(KEY_SERVER_USER_ID, -1L);

        TextView txtUserName = view.findViewById(R.id.txtUserName);
        ImageView imgUser = view.findViewById(R.id.imgUser);

        if (uid < 0) {
            txtUserName.setText(User.getName_null(requireContext()));
            imgUser.setImageResource(User.getIcon_null());
            return;
        }

        String prefName = prefs.getString(KEY_NAME, "");
        UserDatabaseHelper db = new UserDatabaseHelper(requireContext());
        UserDatabaseHelper.UserRow row = db.getUser(uid);
        String name = prefName;
        if (row != null && row.name != null && !row.name.isEmpty()) {
            name = row.name;
        }
        txtUserName.setText(name.isEmpty() ? User.getName_null(requireContext()) : name);

        if (row != null && row.icon != null && row.icon.length > 0) {
            Bitmap bm = BitmapFactory.decodeByteArray(row.icon, 0, row.icon.length);
            if (bm != null) {
                imgUser.setImageBitmap(bm);
            } else {
                imgUser.setImageResource(User.getIcon_null());
            }
        } else {
            imgUser.setImageResource(User.getIcon_null());
        }
    }
}
