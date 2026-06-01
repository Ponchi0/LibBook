package com.example.samsungproject;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.samsungproject.net.ApiConfig;
import com.example.samsungproject.util.BookmarkSyncHelper;
import com.example.samsungproject.util.SessionHelper;

public class MainActivity extends AppCompatActivity {
    static {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    /**
     * Инициализирует главный экран, обновляет адрес API и синхронизирует закладки при входе.
     *
     * @param savedInstanceState сохранённое состояние активности
     */
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ApiConfig.refreshAsync(this);
        if (SessionHelper.isLoggedIn(this)) {
            BookmarkSyncHelper.syncAsync(this, null);
        }
    }
}
