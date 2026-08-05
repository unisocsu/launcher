package com.example.keylauncher;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Window;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AppSearchDialog extends AppCompatDialog {

    private final List<MainActivity.LauncherItem> allApps;
    private final List<MainActivity.LauncherItem> filteredList;
    private LauncherAdapter searchAdapter;
    private final MainActivity activityContext;

    public AppSearchDialog(@NonNull MainActivity activity, List<MainActivity.LauncherItem> launcherItems) {
        super(activity);
        this.activityContext = activity;

        allApps = new ArrayList<>();
        for (MainActivity.LauncherItem item : launcherItems) {
            if (!item.isFolder()) {
                allApps.add(item);
            }
        }
        filteredList = new ArrayList<>(allApps);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_app_search);

        RecyclerView recyclerView = findViewById(R.id.search_recycler_view);
        EditText searchEditText = findViewById(R.id.search_input);

        if (recyclerView != null) {
            recyclerView.setLayoutManager(new GridLayoutManager(activityContext, 3));
            searchAdapter = new LauncherAdapter(activityContext, filteredList);
            recyclerView.setAdapter(searchAdapter);
        }

        if (searchEditText != null) {
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
    }

    private void filterApps(String query) {
        filteredList.clear();
        if (query.trim().isEmpty()) {
            filteredList.addAll(allApps);
        } else {
            String lowerQuery = query.toLowerCase().trim();
            for (MainActivity.LauncherItem item : allApps) {
                String title = (item.customTitle != null) ? item.customTitle : item.title;
                if (title != null && title.toLowerCase().contains(lowerQuery)) {
                    filteredList.add(item);
                }
            }
        }
        if (searchAdapter != null) {
            searchAdapter.notifyDataSetChanged();
        }
    }
}
