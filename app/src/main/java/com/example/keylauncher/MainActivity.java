package com.example.keylauncher;

import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PICK_APPWIDGET = 1001;
    private static final int REQUEST_CREATE_APPWIDGET = 1002;
    private static final int APPWIDGET_HOST_ID = 1024;

    private AppWidgetManager appWidgetManager;
    private AppWidgetHost appWidgetHost;
    private FrameLayout widgetContainer;

    private RecyclerView recyclerView;
    private LauncherAdapter adapter;
    private List<LauncherItem> allItems = new ArrayList<>();
    private List<LauncherItem> displayedItems = new ArrayList<>();

    private SharedPreferences prefs;
    private Set<String> hiddenPackages = new HashSet<>();
    private boolean showHiddenApps = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 📶 א. השארת ה-Status Bar גלוי
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("LauncherPrefs", Context.MODE_PRIVATE);
        hiddenPackages = prefs.getStringSet("hidden_apps", new HashSet<>());

        widgetContainer = findViewById(R.id.widget_container);
        recyclerView = findViewById(R.id.apps_recycler_view);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 4));

        // 🧩 ז. אתחול מנגנון הוידג'טים
        appWidgetManager = AppWidgetManager.getInstance(this);
        appWidgetHost = new AppWidgetHost(this, APPWIDGET_HOST_ID);

        loadSavedWidget();
        loadApps();
    }

    // ----------------------------------------------------
    // 🖱️ ב. הפעלת/כיבוי העכבר מתוך הדה-קומפילציה
    // ----------------------------------------------------
    public void toggleMousePointer() {
        Intent intent = new Intent("com.android.settings.POWER_POINTER_TOGGLE");
        sendBroadcast(intent);
        Toast.makeText(this, "מצב עכבר שונה 🖱️", Toast.LENGTH_SHORT).show();
    }

    // ----------------------------------------------------
    // 📞 ב + ט. זיהוי מקש חיוג (לחיצה קצרה לעכבר, ארוכה לחייגן)
    // ----------------------------------------------------
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_CALL) {
            if (event.isLongPress()) {
                // 📞 לחיצה ארוכה: פתיחת החייגן
                Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                startActivity(dialIntent);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_CALL) {
            if ((event.getFlags() & KeyEvent.FLAG_CANCELED_LONG_PRESS) == 0) {
                // 🖱️ לחיצה קצרה: הפעלה/כיבוי של העכבר
                toggleMousePointer();
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    // ----------------------------------------------------
    // 📱 ג-ו. תפריט לחיצה ארוכה על אפליקציה (PopupMenu)
    // ----------------------------------------------------
    public void showContextMenu(View view, int position) {
        LauncherItem item = displayedItems.get(position);
        if (item.isFolder()) return;

        AppItem app = (AppItem) item;
        PopupMenu popup = new PopupMenu(this, view);

        // ג. הפעלת עכבר 🖱️
        popup.getMenu().add("הפעל/כבוי עכבר 🖱️").setOnMenuItemClickListener(m -> {
            toggleMousePointer();
            return true;
        });

        // ו. עריכת שם ✏️
        popup.getMenu().add("ערוך שם ✏️").setOnMenuItemClickListener(m -> {
            showEditTitleDialog(app);
            return true;
        });

        // ה. הסתרת אפליקציה 👁️‍🗨️
        popup.getMenu().add("הסתר אפליקציה 👁️‍🗨️").setOnMenuItemClickListener(m -> {
            hideApp(app.packageName);
            return true;
        });

        // ד. הסרת התקנה 🗑️
        popup.getMenu().add("הסר התקנה 🗑️").setOnMenuItemClickListener(m -> {
            Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
            uninstallIntent.setData(Uri.parse("package:" + app.packageName));
            startActivity(uninstallIntent);
            return true;
        });

        popup.show();
    }

    private void showEditTitleDialog(AppItem app) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("עריכת שם אפליקציה ✏️");
        final EditText input = new EditText(this);
        input.setText(app.customTitle != null ? app.customTitle : app.title);
        builder.setView(input);

        builder.setPositiveButton("שמור", (dialog, which) -> {
            app.customTitle = input.getText().toString();
            prefs.edit().putString("custom_name_" + app.packageName, app.customTitle).apply();
            adapter.notifyDataSetChanged();
        });
        builder.setNegativeButton("ביטול", null);
        builder.show();
    }

    private void hideApp(String packageName) {
        hiddenPackages.add(packageName);
        prefs.edit().putStringSet("hidden_apps", hiddenPackages).apply();
        filterApps();
        Toast.makeText(this, "האפליקציה הוסתרה 🙈", Toast.LENGTH_SHORT).show();
    }

    // ----------------------------------------------------
    // ⚙️ י. תפריט אפשרויות מובנה (Options Menu) + חיפוש 🔍
    // ----------------------------------------------------
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (showHiddenApps) {
            menu.add(0, 100, 0, "הסתר אפליקציות מוסתרות 🙈");
        } else {
            menu.add(0, 100, 0, "הצג אפליקציות מוסתרות 👁️");
        }
        menu.add(0, 101, 0, "הוסף וידג'ט 🧩");
        menu.add(0, 102, 0, "חפש אפליקציה 🔍");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == 100) {
            showHiddenApps = !showHiddenApps;
            filterApps();
            invalidateOptionsMenu();
            return true;
        } else if (id == 101) {
            selectWidget();
            return true;
        } else if (id == 102) {
            AppSearchDialog searchDialog = new AppSearchDialog(this, allItems);
            searchDialog.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void filterApps() {
        displayedItems.clear();
        for (LauncherItem item : allItems) {
            if (!item.isFolder()) {
                AppItem app = (AppItem) item;
                if (showHiddenApps || !hiddenPackages.contains(app.packageName)) {
                    displayedItems.add(app);
                }
            } else {
                displayedItems.add(item);
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void loadApps() {
        filterApps();
        adapter = new LauncherAdapter(this, displayedItems);
        recyclerView.setAdapter(adapter);
    }

    // ----------------------------------------------------
    // 🧩 ז. מנגנון הוספה ורינדור של וידג'טים
    // ----------------------------------------------------
    private void selectWidget() {
        int appWidgetId = appWidgetHost.allocateAppWidgetId();
        Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
        pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_PICK_APPWIDGET) {
                configureWidget(data);
            } else if (requestCode == REQUEST_CREATE_APPWIDGET) {
                createWidget(data);
            }
        } else if (resultCode == RESULT_CANCELED && data != null) {
            int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            if (appWidgetId != -1) {
                appWidgetHost.deleteAppWidgetId(appWidgetId);
            }
        }
    }

    private void configureWidget(Intent data) {
        int appWidgetId = data.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        AppWidgetProviderInfo appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId);

        if (appWidgetInfo.configure != null) {
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            intent.setComponent(appWidgetInfo.configure);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            startActivityForResult(intent, REQUEST_CREATE_APPWIDGET);
        } else {
            createWidget(data);
        }
    }

    private void createWidget(Intent data) {
        int appWidgetId = data.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        prefs.edit().putInt("saved_widget_id", appWidgetId).apply();
        renderWidget(appWidgetId);
    }

    private void renderWidget(int appWidgetId) {
        AppWidgetProviderInfo appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId);
        if (appWidgetInfo == null) return;

        AppWidgetHostView hostView = appWidgetHost.createView(getApplicationContext(), appWidgetId, appWidgetInfo);
        hostView.setAppWidget(appWidgetId, appWidgetInfo);

        widgetContainer.removeAllViews();
        widgetContainer.addView(hostView);
        widgetContainer.setVisibility(View.VISIBLE);
    }

    private void loadSavedWidget() {
        int savedId = prefs.getInt("saved_widget_id", -1);
        if (savedId != -1) {
            renderWidget(savedId);
        } else {
            widgetContainer.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (appWidgetHost != null) appWidgetHost.startListening();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (appWidgetHost != null) appWidgetHost.stopListening();
    }

    // ----------------------------------------------------
    // 📦 מחלקות עזר למבנה הנתונים (מתוקן ומותאם ל-Adapter!)
    // ----------------------------------------------------
    public abstract static class LauncherItem {
        public String title;
        public String customTitle;
        public abstract boolean isFolder();
    }

    public static class AppItem extends LauncherItem {
        public String packageName;
        public String customIconUri; // 🖼️ נתיב לאייקון מותאם אישית של אפליקציה

        @Override
        public boolean isFolder() { 
            return false; 
        }
    }

    public static class FolderItem extends LauncherItem {
        public List<AppItem> appsInside = new ArrayList<>(); // 👈 הותאם ל-LauncherAdapter
        public String customIconPath;                       // 👈 הותאם ל-LauncherAdapter
        public boolean useFirstAppIcon = false;            // 👈 הותאם ל-LauncherAdapter

        @Override
        public boolean isFolder() { 
            return true; 
        }
    }

    public boolean isPickingDestination() { 
        return false; 
    }

    public void handleDestinationSelected(int pos, View v) {}

    public void openFolder(FolderItem folder) {}
}
