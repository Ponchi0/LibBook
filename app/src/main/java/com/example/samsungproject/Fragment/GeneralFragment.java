package com.example.samsungproject.Fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.samsungproject.LocalDB.UserDatabaseHelper;
import com.example.samsungproject.R;
import com.example.samsungproject.domain.User;
import com.example.samsungproject.util.SessionHelper;
import com.google.android.material.button.MaterialButton;

public class GeneralFragment extends Fragment {

    private static final String PREFS_NAME = "user_prefs";
    private static final String KEY_SERVER_USER_ID = "server_user_id";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_NAME = "name";

    /**
     * Создаёт фрагмент главного экрана «Общее».
     */
    public GeneralFragment() {
        super(R.layout.activity_general);
    }

    /**
     * Привязывает кнопки навигации, управления книгами и авторизации; обновляет шапку пользователя.
     *
     * @param view               корневое представление фрагмента
     * @param savedInstanceState сохранённое состояние или {@code null}
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton btnAddBook = view.findViewById(R.id.btnAddBook);
        btnAddBook.setOnClickListener(v -> {
            if (!SessionHelper.isLoggedIn(requireContext())) {
                SessionHelper.showLoginRequiredToast(requireContext());
                return;
            }
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_generalFragment_to_addBookFragment);
        });

        MaterialButton btnEditBook = view.findViewById(R.id.btnEditBook);
        btnEditBook.setOnClickListener(v -> startEditBookFlow());

        MaterialButton btnDeleteBook = view.findViewById(R.id.btnDeleteBook);
        btnDeleteBook.setOnClickListener(v -> startDeleteBookFlow());

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

        MaterialButton btnSettings = view.findViewById(R.id.btn_Settings);
        btnSettings.setOnClickListener(v -> {
            if (!SessionHelper.isLoggedIn(requireContext())) {
                SessionHelper.showLoginRequiredToast(requireContext());
                return;
            }
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_generalFragment_to_settingsFragment);
        });

        view.findViewById(R.id.btnRegistration).setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_generalFragment_to_registrationFragment));

        view.findViewById(R.id.btnSignIn).setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_generalFragment_to_authorizationFragment));

        refreshUserHeader(view);
        bindAuthButtons(view);
    }

    /**
     * При возврате на экран обновляет шапку пользователя и видимость кнопок входа.
     */
    @Override
    public void onResume() {
        super.onResume();
        View v = getView();
        if (v != null) {
            refreshUserHeader(v);
            bindAuthButtons(v);
        }
    }

    /**
     * Показывает или скрывает блок кнопок регистрации и входа для гостя.
     *
     * @param view корневое представление фрагмента
     */
    private void bindAuthButtons(@NonNull View view) {
        boolean loggedIn = SessionHelper.isLoggedIn(requireContext());
        view.findViewById(R.id.guestAuthRow).setVisibility(loggedIn ? View.GONE : View.VISIBLE);
    }

    /**
     * Открывает диалог выбора книги для редактирования, если пользователь авторизован.
     */
    private void startEditBookFlow() {
        if (!SessionHelper.isLoggedIn(requireContext())) {
            Toast.makeText(requireContext(), R.string.edit_book_not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }
        long userId = SessionHelper.getServerUserId(requireContext());
        Bundle args = new Bundle();
        args.putLong(EditBookListDialogFragment.ARG_USER_ID, userId);
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_generalFragment_to_editBookListDialogFragment, args);
    }

    /**
     * Открывает диалог удаления книг пользователя, если пользователь авторизован.
     */
    private void startDeleteBookFlow() {
        if (!SessionHelper.isLoggedIn(requireContext())) {
            Toast.makeText(requireContext(), R.string.delete_book_not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }
        long userId = SessionHelper.getServerUserId(requireContext());
        Bundle args = new Bundle();
        args.putLong(DeleteBookDialogFragment.ARG_USER_ID, userId);
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_generalFragment_to_deleteBookDialogFragment, args);
    }

    /**
     * Заполняет имя и аватар пользователя в шапке экрана из локальных данных.
     *
     * @param view корневое представление фрагмента
     */
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
