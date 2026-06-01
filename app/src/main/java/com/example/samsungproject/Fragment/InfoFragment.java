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



import com.example.samsungproject.LocalDB.BookDatabaseHelper;

import com.example.samsungproject.LocalDB.UserDatabaseHelper;

import com.example.samsungproject.R;

import com.example.samsungproject.domain.User;

import com.example.samsungproject.util.SessionHelper;

import com.google.android.material.button.MaterialButton;



public class InfoFragment extends Fragment {



    private static final String PREFS_NAME = "user_prefs";

    private static final String KEY_SERVER_USER_ID = "server_user_id";

    private static final String KEY_EMAIL = "email";

    private static final String KEY_NAME = "name";



    private static final long GUEST_LOCAL_ID = 0L;



    /**
     * Создаёт фрагмент экрана информации о профиле пользователя.
     */
    public InfoFragment() {

        super(R.layout.info);

    }



    /**
     * Настраивает навигацию, авторизацию, выход и удаление аккаунта.
     *
     * @param view               корневое представление фрагмента
     * @param savedInstanceState сохранённое состояние или {@code null}
     */
    @Override

    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);



        MaterialButton btnBackInfo = view.findViewById(R.id.btnBackInfo);

        btnBackInfo.setOnClickListener(v ->

                NavHostFragment.findNavController(this)

                        .navigate(R.id.action_infoFragment_to_generalFragment));



        view.findViewById(R.id.btnRegistration).setOnClickListener(v ->

                NavHostFragment.findNavController(this)

                        .navigate(R.id.action_infoFragment_to_registrationFragment));



        view.findViewById(R.id.btnSignIn).setOnClickListener(v ->

                NavHostFragment.findNavController(this)

                        .navigate(R.id.action_infoFragment_to_authorizationFragment));



        view.findViewById(R.id.btnLogout).setOnClickListener(v -> performLogout(true));



        view.findViewById(R.id.btnDeleteAccount).setOnClickListener(v -> {

            if (!SessionHelper.isLoggedIn(requireContext())) {

                SessionHelper.showLoginRequiredToast(requireContext());

                return;

            }

            NavHostFragment.findNavController(this)

                    .navigate(R.id.action_infoFragment_to_deleteAccountDialogFragment);

        });



        getParentFragmentManager().setFragmentResultListener(

                DeleteAccountDialogFragment.REQUEST_KEY,

                getViewLifecycleOwner(),

                (requestKey, result) -> {

                    if (result.getBoolean(DeleteAccountDialogFragment.BUNDLE_SUCCESS, false)) {

                        performLogout(false);

                        Toast.makeText(requireContext(), R.string.delete_account_success, Toast.LENGTH_SHORT).show();

                    }

                }

        );

    }



    /**
     * При возврате на экран обновляет кнопки авторизации и данные профиля.
     */
    @Override

    public void onResume() {

        super.onResume();

        View v = getView();

        if (v != null) {

            bindAuthButtons(v);

            applyProfile(v);

        }

    }



    /**
     * Показывает или скрывает кнопки входа, выхода и удаления аккаунта.
     *
     * @param view корневое представление фрагмента
     */
    private void bindAuthButtons(@NonNull View view) {

        boolean loggedIn = SessionHelper.isLoggedIn(requireContext());



        View guestRow = view.findViewById(R.id.guestAuthRow);

        MaterialButton btnLogout = view.findViewById(R.id.btnLogout);

        MaterialButton btnDelete = view.findViewById(R.id.btnDeleteAccount);

        guestRow.setVisibility(loggedIn ? View.GONE : View.VISIBLE);

        btnLogout.setVisibility(loggedIn ? View.VISIBLE : View.GONE);

        btnDelete.setVisibility(loggedIn ? View.VISIBLE : View.GONE);

    }



    /**
     * Заполняет имя, email и аватар пользователя на экране профиля.
     *
     * @param view корневое представление фрагмента
     */
    private void applyProfile(@NonNull View view) {

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        long uid = prefs.getLong(KEY_SERVER_USER_ID, -1L);



        TextView txtUserName = view.findViewById(R.id.txtUserName);

        TextView txtEmail = view.findViewById(R.id.txtEmail);

        ImageView imgUser = view.findViewById(R.id.imgUser);



        if (uid < 0) {

            txtUserName.setText(User.getName_null(requireContext()));

            txtEmail.setText(R.string.info_not_logged_in);

            imgUser.setImageResource(User.getIcon_null());

            return;

        }



        String prefName = prefs.getString(KEY_NAME, "");

        String prefEmail = prefs.getString(KEY_EMAIL, "");



        UserDatabaseHelper db = new UserDatabaseHelper(requireContext());

        UserDatabaseHelper.UserRow row = db.getUser(uid);

        String name = prefName;

        if (row != null && row.name != null && !row.name.isEmpty()) {

            name = row.name;

        }

        txtUserName.setText(name.isEmpty() ? User.getName_null(requireContext()) : name);

        txtEmail.setText(getString(R.string.info_email_line, prefEmail.isEmpty() ? "—" : prefEmail));



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



    /**
     * Очищает локальные данные пользователя, закладки и обновляет UI после выхода.
     *
     * @param showLogoutToast показывать ли сообщение об успешном выходе
     */
    private void performLogout(boolean showLogoutToast) {

        Context ctx = requireContext();

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        long oldId = prefs.getLong(KEY_SERVER_USER_ID, -1L);



        prefs.edit().clear().apply();



        UserDatabaseHelper db = new UserDatabaseHelper(ctx);

        if (oldId > 0) {

            db.deleteUser(oldId);

        }

        db.upsertUser(GUEST_LOCAL_ID, User.getName_null(ctx), null);



        try (BookDatabaseHelper bookDb = new BookDatabaseHelper(ctx)) {

            bookDb.clearAllBookmarks();

        }

        if (showLogoutToast) {

            Toast.makeText(ctx, R.string.logout_done, Toast.LENGTH_SHORT).show();

        }

        View v = getView();

        if (v != null) {

            bindAuthButtons(v);

            applyProfile(v);

        }

    }

}
