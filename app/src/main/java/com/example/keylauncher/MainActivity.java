package com.example.keylauncher;

import org.json.JSONArray;
import org.json.JSONObject;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ContextThemeWrapper;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetProviderInfo;
import android.appwidget.AppWidgetHostView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
import com.google.gson.Gson;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int HOST_ID = 1024;
    private static final int REQUEST_PICK_WIDGET = 1;
    private static final int REQUEST_CREATE_WIDGET = 2;
    private static final int REQUEST_BIND_WIDGET = 100;
    private static final int REQUEST_PICK_APP_ICON = 300;
    private static final int REQUEST_PICK_FOLDER_ICON = 200;

    private RecyclerView recyclerView;
    private LauncherAdapter adapter;
    private List<LauncherItem> launcherItems = new ArrayList<>();
    private AlertDialog openFolderDialog = null;
    private FolderItem currentlyOpenFolderItem = null;

    private TextView dateTimeTextView;
    private Handler timeHandler = new Handler(Looper.getMainLooper());
    private Runnable timeRunnable;

    private AppWidgetManager widgetManager;
    private AppWidgetHost widgetHost;
    private ViewGroup widgetContainer;
    private View widgetPlaceholderText;

    private LauncherItem pendingMoveItem = null;
    private int pendingMovePosition = -1;
    private boolean isPickingDestination = false;

    private int currentWidgetId1 = -1;
    private int currentWidgetId2 = -1;
    private int pendingSlotForPick = 1;

    private AppItem appItemEditingNow = null;
    private FolderItem folderItemEditingNow = null;

    private Map<Integer, Integer> shortcutPositionsMap = new HashMap<>();
    
    // 📞 משתנה לזיהוי לחיצה ארוכה על מקש החיוג
    private boolean isCallKeyLongPressed = false;

    public static abstract class LauncherItem {
        public String title;
        public String customTitle = null;
        public String type;
        public int shortcutKey = -1;
        public abstract boolean isFolder();
    }

    public static class AppItem extends LauncherItem {
        public String packageName;
        public String customIconUri = null;

        public AppItem(String title, String packageName) {
            this.title = title;
            this.packageName = packageName;
            this.type = "app";
        }
        @Override
        public boolean isFolder() { return false; }
    }

    public static class FolderItem extends LauncherItem {
        public List<AppItem> appsInside = new ArrayList<>();
        public String customIconPath = null;
        public boolean useFirstAppIcon = false;

        public FolderItem(String title) {
            this.title = title;
            this.type = "folder";
        }
        @Override
        public boolean isFolder() { return true; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER,
                WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER
            );
        } catch (Exception e) { /* Ignored */ }

        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        setContentView(R.layout.activity_main);

        SharedPreferences sharedPreferences = getSharedPreferences("LauncherPrefs", MODE_PRIVATE);
        for (int i = 0; i <= 9; i++) {
            int androidKeyCode = KeyEvent.KEYCODE_0 + i;
            int savedPos = sharedPreferences.getInt("shortcut_pos_" + androidKeyCode, -1);
            if (savedPos != -1) {
                shortcutPositionsMap.put(androidKeyCode, savedPos);
            }
        }

        recyclerView = findViewById(R.id.launcher_recycler_view);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
            recyclerView.setFocusable(true);
            recyclerView.setFocusableInTouchMode(true);
            recyclerView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        }

        dateTimeTextView = findViewById(R.id.date_time_text);
        if (dateTimeTextView == null) {
            dateTimeTextView = new TextView(this);
        }
        startTimeUpdate();

        widgetPlaceholderText = findViewById(R.id.widget_placeholder_text);
        widgetContainer = findViewById(R.id.widget_container);
        if (widgetContainer != null) {
            widgetContainer.setFocusable(true);
            widgetContainer.setFocusableInTouchMode(true);
            widgetContainer.setClickable(true);
            widgetContainer.setOnLongClickListener(v -> {
                showWidgetContextMenu(v);
                return true;
            });
        }

        try {
            widgetManager = AppWidgetManager.getInstance(this);
            widgetHost = new AppWidgetHost(this, HOST_ID);
        } catch (Exception e) {
            Toast.makeText(this, "התקן זה לא תומך בווידג'טים ⚠️", Toast.LENGTH_LONG).show();
        }

        if (!loadLauncherState()) {
            loadLauncherItems();
        } else {
            syncAndCleanLauncherItems();
        }

        adapter = new LauncherAdapter(this, launcherItems);
        if (recyclerView != null) {
            recyclerView.setAdapter(adapter);
        }

        currentWidgetId1 = sharedPreferences.getInt("saved_widget_id_1", -1);
        currentWidgetId2 = sharedPreferences.getInt("saved_widget_id_2", -1);
        renderAllWidgets();
    }

    private void startTimeUpdate() {
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                SimpleDateFormat sdf = new SimpleDateFormat("EEEE, d MMMM yyyy • HH:mm", Locale.getDefault());
                dateTimeTextView.setText(sdf.format(new Date()));
                timeHandler.postDelayed(this, 1000);
            }
        };
        timeHandler.post(timeRunnable);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (widgetHost != null) {
            try {
                widgetHost.startListening();
            } catch (Exception e) {}
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (widgetHost != null) {
            try {
                widgetHost.stopListening();
            } catch (Exception e) {}
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timeHandler != null && timeRunnable != null) {
            timeHandler.removeCallbacks(timeRunnable);
        }
        WidgetKeyController.stopListening();
    }

    private void loadLauncherItems() {
        launcherItems.clear();
        PackageManager pm = getPackageManager();

        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> availableActivities = pm.queryIntentActivities(intent, 0);

        for (ResolveInfo ri : availableActivities) {
            String label = ri.loadLabel(pm).toString();
            String packageName = ri.activityInfo.packageName;

            if (!packageName.equals(getPackageName())) {
                launcherItems.add(new AppItem(label, packageName));
            }
        }
        saveLauncherState();
    }

    public void saveLauncherState() {
        SharedPreferences sharedPreferences = getSharedPreferences("LauncherPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Gson gson = new Gson();
        editor.putString("launcher_structure", gson.toJson(launcherItems));
        editor.apply();
    }

    private boolean loadLauncherState() {
        SharedPreferences sharedPreferences = getSharedPreferences("LauncherPrefs", MODE_PRIVATE);
        String jsonText = sharedPreferences.getString("launcher_structure", null);
        if (jsonText == null || jsonText.isEmpty()) return false;

        try {
            launcherItems.clear();
            JSONArray jsonArray = new JSONArray(jsonText);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                String type = obj.optString("type", "app");
                String title = obj.getString("title");

                if (type.equals("folder")) {
                    FolderItem folder = new FolderItem(title);
                    folder.customTitle = obj.optString("customTitle", null);
                    folder.customIconPath = obj.optString("customIconPath", null);
                    folder.useFirstAppIcon = obj.optBoolean("useFirstAppIcon", false);
                    JSONArray appsArray = obj.optJSONArray("appsInside");
                    if (appsArray != null) {
                        for (int j = 0; j < appsArray.length(); j++) {
                            JSONObject appObj = appsArray.getJSONObject(j);
                            AppItem appItem = new AppItem(appObj.getString("title"), appObj.getString("packageName"));
                            appItem.customTitle = appObj.optString("customTitle", null);
                            appItem.customIconUri = appObj.optString("customIconUri", null);
                            folder.appsInside.add(appItem);
                        }
                    }
                    launcherItems.add(folder);
                } else {
                    AppItem app = new AppItem(title, obj.getString("packageName"));
                    app.customTitle = obj.optString("customTitle", null);
                    app.customIconUri = obj.optString("customIconUri", null);
                    launcherItems.add(app);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 🔍 סריקה משופרת: מסירה אפליקציות שנמחקו ומוסיפה אוטומטית אפליקציות חדשות!
     */
    private void syncAndCleanLauncherItems() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> availableActivities = pm.queryIntentActivities(intent, 0);

        Map<String, String> installedAppsMap = new HashMap<>();
        for (ResolveInfo ri : availableActivities) {
            String pkg = ri.activityInfo.packageName;
            if (!pkg.equals(getPackageName())) {
                installedAppsMap.put(pkg, ri.loadLabel(pm).toString());
            }
        }

        Set<String> existingPackagesInLauncher = new HashSet<>();
        List<LauncherItem> itemsToRemove = new ArrayList<>();

        // 1. הסרת אפליקציות שאינן מותקנות עוד
        for (LauncherItem item : launcherItems) {
            if (item.isFolder()) {
                FolderItem folder = (FolderItem) item;
                List<AppItem> appsInsideToRemove = new ArrayList<>();
                for (AppItem app : folder.appsInside) {
                    if (!installedAppsMap.containsKey(app.packageName)) {
                        appsInsideToRemove.add(app);
                    } else {
                        existingPackagesInLauncher.add(app.packageName);
                    }
                }
                folder.appsInside.removeAll(appsInsideToRemove);
                if (folder.appsInside.isEmpty() && folder.customIconPath == null) {
                    itemsToRemove.add(folder);
                }
            } else {
                AppItem app = (AppItem) item;
                if (!installedAppsMap.containsKey(app.packageName)) {
                    itemsToRemove.add(app);
                } else {
                    existingPackagesInLauncher.add(app.packageName);
                }
            }
        }
        launcherItems.removeAll(itemsToRemove);

        // 2. זיהוי והוספה של אפליקציות חדשות שהותקנו במכשיר! ✨
        for (Map.Entry<String, String> entry : installedAppsMap.entrySet()) {
            String pkg = entry.getKey();
            String label = entry.getValue();
            if (!existingPackagesInLauncher.contains(pkg)) {
                launcherItems.add(new AppItem(label, pkg));
            }
        }

        saveLauncherState();
    }

    public void showContextMenu(View view, int position) {
        if (position < 0 || position >= launcherItems.size()) return;

        PopupMenu popup = createStyledPopupMenu(view);
        LauncherItem item = launcherItems.get(position);

        popup.getMenu().add(0, 1, 0, "העבר פריט 🔄");
        popup.getMenu().add(0, 2, 1, "הסר מהמסך 🗑️");

        popup.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == 1) {
                pendingMoveItem = item;
                pendingMovePosition = position;
                isPickingDestination = true;
                Toast.makeText(this, "נווט ליעד ולחץ אישור 🎯", Toast.LENGTH_SHORT).show();
            } else if (id == 2) {
                launcherItems.remove(position);
                saveLauncherState();
                refreshViews();
            }
            return true;
        });

        popup.show();
    }

    public void selectWidget() {
        if (widgetHost == null) {
            Toast.makeText(this, "מארח הווידג'טים לא אותחל כראוי ⚠️", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentWidgetId1 == -1) {
            pendingSlotForPick = 1;
        } else if (currentWidgetId2 == -1) {
            pendingSlotForPick = 2;
        } else {
            pendingSlotForPick = 2;
        }

        try {
            int appWidgetId = widgetHost.allocateAppWidgetId();
            if (pendingSlotForPick == 1) currentWidgetId1 = appWidgetId;
            else currentWidgetId2 = appWidgetId;

            Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
            pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            startActivityForResult(pickIntent, REQUEST_PICK_WIDGET);
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void configureAndBuildWidget(int appWidgetId, AppWidgetProviderInfo info) {
        if (info != null && info.configure != null) {
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            intent.setComponent(info.configure);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            startActivityForResult(intent, REQUEST_CREATE_WIDGET);
        } else {
            saveWidgetIds();
            renderAllWidgets();
        }
    }

    private void saveWidgetIds() {
        getSharedPreferences("LauncherPrefs", MODE_PRIVATE).edit()
            .putInt("saved_widget_id_1", currentWidgetId1)
            .putInt("saved_widget_id_2", currentWidgetId2)
            .apply();
    }

    private void renderAllWidgets() {
        if (widgetContainer == null || widgetHost == null) return;

        widgetContainer.removeAllViews();

        boolean hasWidget = false;

        if (currentWidgetId1 != -1 && widgetManager != null) {
            AppWidgetProviderInfo info1 = widgetManager.getAppWidgetInfo(currentWidgetId1);
            if (info1 != null) {
                addSingleWidgetViewToContainer(currentWidgetId1, info1);
                hasWidget = true;
            }
        }

        if (currentWidgetId2 != -1 && widgetManager != null) {
            AppWidgetProviderInfo info2 = widgetManager.getAppWidgetInfo(currentWidgetId2);
            if (info2 != null) {
                addSingleWidgetViewToContainer(currentWidgetId2, info2);
                hasWidget = true;
            }
        }

        if (widgetPlaceholderText != null) {
            widgetPlaceholderText.setVisibility(hasWidget ? View.GONE : View.VISIBLE);
        }
    }

    private void addSingleWidgetViewToContainer(int appWidgetId, AppWidgetProviderInfo appWidgetInfo) {
        try {
            AppWidgetHostView hostView = widgetHost.createView(this, appWidgetId, appWidgetInfo);
            hostView.setAppWidget(appWidgetId, appWidgetInfo);

            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1.0f
            );
            layoutParams.setMargins(8, 8, 8, 8);
            hostView.setLayoutParams(layoutParams);
            
            hostView.setFocusable(true);
            hostView.setFocusableInTouchMode(true);

            widgetContainer.addView(hostView);
            hostView.postInvalidate();
            widgetContainer.requestLayout();
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה ברינדור: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public void showWidgetContextMenu(View anchorView) {
        PopupMenu popup = createStyledPopupMenu(anchorView);
        popup.getMenu().add(0, 10, 0, "הסר ווידג'טים 🗑️");
        popup.getMenu().add(0, 11, 1, "הוסף ווידג'ט חדש ➕");
        popup.getMenu().add(0, 2, 2, "ביטול ❌");

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == 10) {
                removeCurrentWidgets();
            } else if (itemId == 11) {
                selectWidget();
            }
            return true;
        });
        popup.show();
    }

    private void removeCurrentWidgets() {
        if (widgetContainer != null) {
            widgetContainer.removeAllViews();
        }
        if (widgetPlaceholderText != null) {
            widgetPlaceholderText.setVisibility(View.VISIBLE);
        }
        if (widgetHost != null) {
            try {
                if (currentWidgetId1 != -1) widgetHost.deleteAppWidgetId(currentWidgetId1);
                if (currentWidgetId2 != -1) widgetHost.deleteAppWidgetId(currentWidgetId2);
            } catch (Exception e) {}
        }
        currentWidgetId1 = -1;
        currentWidgetId2 = -1;
        saveWidgetIds();
        Toast.makeText(this, "הווידג'טים הוסרו ✨", Toast.LENGTH_SHORT).show();
    }

    private PopupMenu createStyledPopupMenu(View anchorView) {
        try {
            Context wrapper = new ContextThemeWrapper(this, R.style.AppTheme);
            return new PopupMenu(wrapper, anchorView);
        } catch (Exception e) {
            return new PopupMenu(this, anchorView);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        int activeWidgetId = (pendingSlotForPick == 1) ? currentWidgetId1 : currentWidgetId2;

        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_BIND_WIDGET) {
                int appWidgetId = (data != null) ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, activeWidgetId) : activeWidgetId;
                if (appWidgetId != -1 && widgetManager != null) {
                    if (pendingSlotForPick == 1) currentWidgetId1 = appWidgetId;
                    else currentWidgetId2 = appWidgetId;
                    saveWidgetIds();
                    renderAllWidgets();
                }
                return;
            }

            if (requestCode == REQUEST_PICK_WIDGET) {
                int appWidgetId = (data != null) ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, activeWidgetId) : activeWidgetId;
                android.content.ComponentName provider = (data != null) ? data.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER) : null;

                if (appWidgetId != -1 && widgetManager != null) {
                    AppWidgetProviderInfo info = widgetManager.getAppWidgetInfo(appWidgetId);

                    if (provider != null && info == null) {
                        try {
                            if (widgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider)) {
                                info = widgetManager.getAppWidgetInfo(appWidgetId);
                            } else {
                                Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider);
                                startActivityForResult(intent, REQUEST_BIND_WIDGET);
                                return;
                            }
                        } catch (SecurityException se) {
                            info = widgetManager.getAppWidgetInfo(appWidgetId);
                        }
                    }

                    if (info != null) {
                        configureAndBuildWidget(appWidgetId, info);
                    } else {
                        Toast.makeText(this, "שגיאה: לא ניתן לקרוא את נתוני הווידג'ט ⚠️", Toast.LENGTH_SHORT).show();
                    }
                }
                return;
            }

            if (requestCode == REQUEST_CREATE_WIDGET) {
                int appWidgetId = (data != null) ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, activeWidgetId) : activeWidgetId;
                if (appWidgetId != -1 && widgetManager != null) {
                    if (pendingSlotForPick == 1) currentWidgetId1 = appWidgetId;
                    else currentWidgetId2 = appWidgetId;
                    saveWidgetIds();
                    renderAllWidgets();
                }
                return;
            }

            if (data != null) {
                if (requestCode == REQUEST_PICK_APP_ICON && appItemEditingNow != null) {
                    appItemEditingNow.customIconUri = data.getData().toString();
                    appItemEditingNow = null;
                    saveLauncherState();
                    refreshViews();
                } else if (requestCode == REQUEST_PICK_FOLDER_ICON && folderItemEditingNow != null) {
                    folderItemEditingNow.customIconPath = data.getData().toString();
                    folderItemEditingNow.useFirstAppIcon = false;
                    folderItemEditingNow = null;
                    saveLauncherState();
                    refreshViews();
                }
            }
        } else if (resultCode == RESULT_CANCELED) {
            if (activeWidgetId != -1 && widgetHost != null) {
                try {
                    widgetHost.deleteAppWidgetId(activeWidgetId);
                } catch (Exception e) {}
                if (pendingSlotForPick == 1) currentWidgetId1 = -1;
                else currentWidgetId2 = -1;
            }
        }
    }

    private void refreshViews() {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (openFolderDialog != null && currentlyOpenFolderItem != null) {
            FolderItem folder = currentlyOpenFolderItem;
            openFolderDialog.dismiss();
            openFolder(folder);
        }
    }

    // 📞 זיהוי לחיצה ארוכה (מקשי 0-9 ומקש חיוג)
    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            WidgetKeyController.startActiveListening(this, keyCode);
            return true;
        }
        
        // 📞 לחיצה ארוכה על מקש החיוג -> פתיחת החייגן
        if (keyCode == KeyEvent.KEYCODE_CALL) {
            isCallKeyLongPressed = true;
            try {
                Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                startActivity(dialIntent);
            } catch (Exception e) {
                Toast.makeText(this, "לא ניתן לפתוח את החייגן ⚠️", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (WidgetKeyController.handleWidgetKey(this, keyCode)) {
            return true;
        }

        // 📞 מעקב אחר מקש החיוג לצורך הבחנה בין לחיצה קצרה לארוכה
        if (keyCode == KeyEvent.KEYCODE_CALL) {
            isCallKeyLongPressed = false;
            event.startTracking();
            return true;
        }

        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            event.startTracking();
            return true;
        }

        if (openFolderDialog == null && shortcutPositionsMap.containsKey(keyCode)) {
            int targetPosition = shortcutPositionsMap.get(keyCode);
            if (targetPosition >= 0 && targetPosition < launcherItems.size()) {
                LauncherItem item = launcherItems.get(targetPosition);
                if (item.isFolder()) {
                    openFolder((FolderItem) item);
                } else {
                    AppItem app = (AppItem) item;
                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(app.packageName);
                    if (launchIntent != null) {
                        startActivity(launchIntent);
                    }
                }
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    // 📞 טיפול בלחיצה קצרה על מקש החיוג
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_CALL) {
            if (!isCallKeyLongPressed) {
                // לחיצה קצרה: רענון הלאנצ'ר והפעלת העכבר/פוקוס
                syncAndCleanLauncherItems();
                refreshViews();
                Toast.makeText(this, "רענון לאנצ'ר והפעלת עכבר 🖱️🔄", Toast.LENGTH_SHORT).show();
            }
            isCallKeyLongPressed = false;
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (widgetHost != null) {
            try {
                widgetHost.startListening();
            } catch (Exception e) {}
        }

        SharedPreferences sharedPreferences = getSharedPreferences("LauncherPrefs", MODE_PRIVATE);
        currentWidgetId1 = sharedPreferences.getInt("saved_widget_id_1", -1);
        currentWidgetId2 = sharedPreferences.getInt("saved_widget_id_2", -1);

        renderAllWidgets();

        if (adapter != null) {
            syncAndCleanLauncherItems();
            adapter.notifyDataSetChanged();
        }
    }

    public void cancelMoveMode() {
        isPickingDestination = false;
        pendingMoveItem = null;
        pendingMovePosition = -1;
    }

    /**
     * 🎯 ניווט בפוקוס עד המיקום הרצוי + הקפצת תפריט בחירה (החלף מיקום / מזג לתיקייה)
     */
    public void handleDestinationSelected(int targetPosition, View targetView) {
        if (pendingMovePosition == targetPosition) {
            cancelMoveMode();
            return;
        }

        PopupMenu popup = createStyledPopupMenu(targetView != null ? targetView : recyclerView);
        popup.getMenu().add(0, 1, 0, "החליפו מיקומים 🔄");
        popup.getMenu().add(0, 2, 1, "מזגו לתיקייה 📁");
        popup.getMenu().add(0, 3, 2, "ביטול ❌");

        popup.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == 1) {
                // החלפת מיקומים ברשימה (Swap)
                LauncherItem targetItem = launcherItems.get(targetPosition);
                launcherItems.set(pendingMovePosition, targetItem);
                launcherItems.set(targetPosition, pendingMoveItem);
                saveLauncherState();
                refreshViews();
            } else if (id == 2) {
                // מיזוג לתוך תיקייה
                performMergeToFolder(targetPosition);
            }
            cancelMoveMode();
            return true;
        });

        popup.setOnDismissListener(p -> cancelMoveMode());
        popup.show();
    }

    private void performMergeToFolder(int targetPosition) {
        LauncherItem targetItem = launcherItems.get(targetPosition);
        if (pendingMoveItem.isFolder()) {
            Toast.makeText(this, "לא ניתן למזג תיקייה לתוך תיקייה ⚠️", Toast.LENGTH_SHORT).show();
            return;
        }

        AppItem sourceApp = (AppItem) pendingMoveItem;

        if (targetItem.isFolder()) {
            FolderItem folder = (FolderItem) targetItem;
            folder.appsInside.add(sourceApp);
            launcherItems.remove(pendingMovePosition);
        } else {
            FolderItem newFolder = new FolderItem("תיקייה חדשה ✨");
            newFolder.appsInside.add((AppItem) targetItem);
            newFolder.appsInside.add(sourceApp);
            launcherItems.remove(pendingMovePosition);
            int adjustedTarget = (pendingMovePosition < targetPosition) ? targetPosition - 1 : targetPosition;
            launcherItems.set(adjustedTarget, newFolder);
        }

        saveLauncherState();
        refreshViews();
    }

    public boolean isPickingDestination() {
        return isPickingDestination;
    }

    public void openFolder(FolderItem folder) {
        currentlyOpenFolderItem = folder;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_folder, null);
        builder.setView(dialogView);

        TextView folderTitle = dialogView.findViewById(R.id.folder_title);
        folderTitle.setText(folder.customTitle != null ? folder.customTitle : folder.title);

        RecyclerView folderRecyclerView = dialogView.findViewById(R.id.folder_recycler_view);
        folderRecyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        folderRecyclerView.setFocusable(true);
        folderRecyclerView.setFocusableInTouchMode(true);

        List<LauncherItem> folderContents = new ArrayList<>(folder.appsInside);
        LauncherAdapter folderAdapter = new LauncherAdapter(this, folderContents);
        folderRecyclerView.setAdapter(folderAdapter);

        openFolderDialog = builder.create();
        if (openFolderDialog.getWindow() != null) {
            openFolderDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        openFolderDialog.show();

        openFolderDialog.setOnDismissListener(dialog -> {
            openFolderDialog = null;
            currentlyOpenFolderItem = null;
            syncAndCleanLauncherItems();
            adapter.notifyDataSetChanged();
        });
    }
}
