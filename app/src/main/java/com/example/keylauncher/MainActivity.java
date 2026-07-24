package com.example.keylauncher;

import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private boolean isShowingHiddenApps = false;
    private AppWidgetManager appWidgetManager;
    private AppWidgetHost appWidgetHost;
    private FrameLayout widgetContainer;
    private RecyclerView recyclerView;
    private AppAdapter appAdapter;
    private List<LauncherItem> launcherItems;

    private static final int APPWIDGET_HOST_ID = 1024;
    private static final int REQUEST_PICK_APPWIDGET = 1;
    private static final int REQUEST_CREATE_APPWIDGET = 2;
    private static final String PREF_NAME = "LauncherPrefs";
    private static final String KEY_WIDGET_ID = "saved_widget_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. תצוגת מערכת - סרגל מצב גלוי 📶
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        widgetContainer = findViewById(R.id.widget_container);
        recyclerView = findViewById(R.id.recycler_view);

        // 7. תצוגת רשת 4 עמודות 📊
        recyclerView.setLayoutManager(new GridLayoutManager(this, 4));
        
        loadMockItems();
        appAdapter = new AppAdapter(this, launcherItems, this::showAppPopupMenu);
        recyclerView.setAdapter(appAdapter);

        // 5. ניהול וידג'טים מתקדם 🧩
        appWidgetManager = AppWidgetManager.getInstance(this);
        appWidgetHost = new AppWidgetHost(this, APPWIDGET_HOST_ID);
        loadSavedWidget();
    }

    @Override
    protected void onStart() {
        super.onStart();
        appWidgetHost.startListening();
    }

    @Override
    protected void onStop() {
        super.onStop();
        appWidgetHost.stopListening();
    }

    // 2. ניהול מקש חיוג (KEYCODE_CALL) 📞🖱️
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_CALL) {
            event.startTracking();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_CALL && !event.isCanceled()) {
            toggleMousePointer(); // לחיצה קצרה: עכבר
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_CALL) {
            openDialer(); // לחיצה ארוכה: חייגן
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    private void toggleMousePointer() {
        sendBroadcast(new Intent("POWER_POINTER_TOGGLE"));
        Toast.makeText(this, "🖱️ שינוי מצב עכבר", Toast.LENGTH_SHORT).show();
    }

    private void openDialer() {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    // פתיחת תיקייה (נקרא על ידי ה-Adapter) 📂
    public void openFolder(FolderItem folder) {
        Toast.makeText(this, "📂 פתיחת תיקייה: " + folder.folderName, Toast.LENGTH_SHORT).show();
    }

    // 4. תפריט אפשרויות מובנה (Options Menu) ⚙️
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, isShowingHiddenApps ? "🙈 הסתר אפליקציות מוסתרות" : "👁️ הצג אפליקציות מוסתרות");
        menu.add(0, 2, 1, "🧩 הוסף וידג'ט");
        menu.add(0, 3, 2, "🔍 חפש אפליקציה");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                isShowingHiddenApps = !isShowingHiddenApps;
                invalidateOptionsMenu();
                filterApps();
                return true;
            case 2:
                selectWidget();
                return true;
            case 3:
                // אפשר לפתוח כאן את ה-AppSearchDialog
                Toast.makeText(this, "🔍 פתיחת חיפוש מהיר", Toast.LENGTH_SHORT).show();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // 3. תפריט בלחיצה ארוכה על אפליקציה (PopupMenu) 📱
    private void showAppPopupMenu(View anchor, AppItem app) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "🖱️ הפעל/כבוי עכבר");
        popup.getMenu().add(0, 2, 1, "✏️ ערוך שם");
        popup.getMenu().add(0, 3, 2, app.isHidden ? "👁️‍🗨️ הצג אפליקציה" : "👁️‍🗨️ הסתר אפליקציה");
        popup.getMenu().add(0, 4, 3, "🗑️ הסר התקנה");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    toggleMousePointer();
                    return true;
                case 2:
                    showRenameDialog(app);
                    return true;
                case 3:
                    app.isHidden = !app.isHidden;
                    filterApps();
                    return true;
                case 4:
                    Intent intent = new Intent(Intent.ACTION_DELETE);
                    intent.setData(Uri.parse("package:" + app.packageName));
                    startActivity(intent);
                    return true;
            }
            return false;
        });
        popup.show();
    }

    // ניהול וידג'טים (AppWidgetHost) 🧩
    private void selectWidget() {
        int appWidgetId = appWidgetHost.allocateAppWidgetId();
        Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
        pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_PICK_APPWIDGET) configureWidget(data);
            else if (requestCode == REQUEST_CREATE_APPWIDGET) createWidget(data);
        } else if (requestCode == REQUEST_PICK_APPWIDGET && data != null) {
            int id = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            if (id != -1) appWidgetHost.deleteAppWidgetId(id);
        }
    }

    private void configureWidget(Intent data) {
        Bundle extras = data.getExtras();
        int id = extras != null ? extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) : -1;
        AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(id);
        if (info != null && info.configure != null) {
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            intent.setComponent(info.configure);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            startActivityForResult(intent, REQUEST_CREATE_APPWIDGET);
        } else {
            createWidget(data);
        }
    }

    private void createWidget(Intent data) {
        Bundle extras = data.getExtras();
        int id = extras != null ? extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) : -1;
        if (id == -1) return;
        AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(id);
        if (info == null) return;
        AppWidgetHostView hostView = appWidgetHost.createView(this, id, info);
        hostView.setAppWidget(id, info);
        widgetContainer.removeAllViews();
        widgetContainer.addView(hostView);
        widgetContainer.setVisibility(View.VISIBLE);
        getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().putInt(KEY_WIDGET_ID, id).apply();
    }

    private void loadSavedWidget() {
        int id = getSharedPreferences(PREF_NAME, MODE_PRIVATE).getInt(KEY_WIDGET_ID, -1);
        if (id != -1) {
            AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(id);
            if (info != null) {
                AppWidgetHostView hostView = appWidgetHost.createView(this, id, info);
                hostView.setAppWidget(id, info);
                widgetContainer.removeAllViews();
                widgetContainer.addView(hostView);
                widgetContainer.setVisibility(View.VISIBLE);
                return;
            }
        }
        widgetContainer.setVisibility(View.GONE);
    }

    private void loadMockItems() {
        launcherItems = new ArrayList<>();
        launcherItems.add(new AppItem("com.android.settings", "הגדרות", null, false));
        launcherItems.add(new AppItem("com.android.dialer", "חייגן", null, false));
    }

    private void filterApps() {
        List<LauncherItem> filtered = new ArrayList<>();
        for (LauncherItem itemz : launcherItems) {
            if (itemz instanceof AppItem) {
                AppItem app = (AppItem) itemz;
                if (isShowingHiddenApps || !app.isHidden) {
                    filtered.add(app);
                }
            } else {
                filtered.add(itemz);
            }
        }
        if (appAdapter != null) {
            appAdapter.updateList(filtered);
        }
    }

    private void showRenameDialog(AppItem app) {
        EditText input = new EditText(this);
        input.setText(app.customName);
        new AlertDialog.Builder(this)
            .setTitle("✏️ ערוך שם")
            .setView(input)
            .setPositiveButton("שמור", (d, w) -> {
                app.customName = input.getText().toString();
                appAdapter.notifyDataSetChanged();
            })
            .setNegativeButton("ביטול", null)
            .show();
    }

    // --- מחלקות מודל נדרשות לתאימות עם שאר הקבצים (LauncherItem, AppItem, FolderItem) ---
    public static class LauncherItem {
        // מחלקת בסיס משותפת
    }

    public static class AppItem extends LauncherItem {
        public String packageName;
        public String customName;
        public String customIconUri;
        public boolean isHidden;

        public AppItem(String packageName, String customName, String customIconUri, boolean isHidden) {
            this.packageName = packageName;
            this.customName = customName;
            this.customIconUri = customIconUri;
            this.isHidden = isHidden;
        }
    }

    public static class FolderItem extends LauncherItem {
        public String folderName;
        public List<AppItem> appsInside;
        public String customIconPath;
        public boolean useFirstAppIcon;

        public FolderItem(String folderName, List<AppItem> appsInside, String customIconPath, boolean useFirstAppIcon) {
            this.folderName = folderName;
            this.appsInside = appsInside;
            this.customIconPath = customIconPath;
            this.useFirstAppIcon = useFirstAppIcon;
        }
    }
}
