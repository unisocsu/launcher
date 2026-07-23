package com.example.keylauncher;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Window;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class AppSearchDialog extends Dialog {

    private final List<MainActivity.LauncherItem> allApps;
    private final List<MainActivity.LauncherItem> filteredList;
    private LauncherAdapter searchAdapter;

    public AppSearchDialog(@NonNull MainActivity activity, List<MainActivity.LauncherItem> launcherItems) {
        super(activity);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_app_search); // דיאלוג חיפוש פשוט

        // סינון פריטים שאינם תיקיות (רק אפליקציות)
        allApps = new ArrayList<>();
        for (MainActivity.LauncherItem item : launcherItems) {
            if (!item.isFolder()) {
                allApps.add(item);
            }
        }
        filteredList = new ArrayList<>(allApps);

        RecyclerView recyclerView = findViewById(R.id.search_recycler_view);
        EditText searchEditText = findViewById(R.id.search_input);

        recyclerView.setLayoutManager(new GridLayoutManager(activity, 3));
        searchAdapter = new LauncherAdapter(activity, filteredList);
        recyclerView.setAdapter(searchAdapter);

        // ⌨️ סינון בלייב לפי הוקלד
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterApps(String query) {
        filteredList.clear();
        if (query.trim().isEmpty()) {
            filteredList.addAll(allApps);
        } else {
            String lowerQuery = query.toLowerCase().trim();
            for (MainActivity.LauncherItem item : allApps) {
                String title = (item.customTitle != null) ? item.customTitle : item.title;
                if (title.toLowerCase().contains(lowerQuery)) {
                    filteredList.add(item);
                }
            }
        }
        searchAdapter.notifyDataSetChanged();
    }
}
