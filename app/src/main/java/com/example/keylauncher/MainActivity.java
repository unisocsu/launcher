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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
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
    private boolean isCopyOperation = false;

    private int currentWidgetId = -1;
    
    private AppItem appItemEditingNow = null;
    private FolderItem folderItemEditingNow = null;

    private Map<Integer, Integer> shortcutPositionsMap = new HashMap<>();

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
        if (recyclerView == null) {
            recyclerView = findViewById(R.id.recycler_view);
        }
        
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        }

        dateTimeTextView = findViewById(R.id.date_time_text);
        if (dateTimeTextView == null) {
            dateTimeTextView = new TextView(this);
        }
        startTimeUpdate();

        widgetPlaceholderText = findViewById(R.id.widget_placeholder_text);
        widgetContainer = findViewById(R.id.widget_container);
        if (widgetContainer != null) {
            widgetContainer.setOnLongClickListener(v -> {
                showWidgetContextMenu(v);
                return true;
            });
        }

        try {
            widgetManager = AppWidgetManager.getInstance(this);
            widgetHost = new AppWidgetHost(this, HOST_ID);
        } catch (Exception e) {
            Toast.makeText(this, "התקן זה לא תומך בווידג'טים", Toast.LENGTH_LONG).show();
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

        currentWidgetId = sharedPreferences.getInt("saved_widget_id", -1);
        if (currentWidgetId != -1 && widgetManager != null) {
            createWidgetView(currentWidgetId, widgetManager.getAppWidgetInfo(currentWidgetId));
        }
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

    private void syncAndCleanLauncherItems() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> availableActivities = pm.queryIntentActivities(intent, 0);

        Set<String> installedPackages = new HashSet<>();
        for (ResolveInfo ri : availableActivities) {
            installedPackages.add(ri.activityInfo.packageName);
        }

        List<LauncherItem> itemsToRemove = new ArrayList<>();

        for (LauncherItem item : launcherItems) {
            if (item.isFolder()) {
                FolderItem folder = (FolderItem) item;
                List<AppItem> appsInsideToRemove = new ArrayList<>();
                for (AppItem app : folder.appsInside) {
                    if (!installedPackages.contains(app.packageName)) {
                        appsInsideToRemove.add(app);
                    }
                }
                folder.appsInside.removeAll(appsInsideToRemove);
                if (folder.appsInside.isEmpty() && folder.customIconPath == null) {
                    itemsToRemove.add(folder);
                }
            } else {
                AppItem app = (AppItem) item;
                if (!installedPackages.contains(app.packageName)) {
                    itemsToRemove.add(app);
                }
            }
        }
        launcherItems.removeAll(itemsToRemove);
        saveLauncherState();
    }

    public void selectWidget() {
        if (widgetHost == null) {
            Toast.makeText(this, "מארח הווידג'טים לא אותחל כראוי", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            int appWidgetId = widgetHost.allocateAppWidgetId();
            currentWidgetId = appWidgetId;
            Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
            pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            startActivityForResult(pickIntent, REQUEST_PICK_WIDGET);
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void configureAndBuildWidget(int appWidgetId, AppWidgetProviderInfo info) {
        currentWidgetId = appWidgetId;
        if (info != null && info.configure != null) {
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            intent.setComponent(info.configure);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            startActivityForResult(intent, REQUEST_CREATE_WIDGET);
        } else {
            saveWidgetId(currentWidgetId);
            createWidgetView(currentWidgetId, info);
        }
    }

    private void saveWidgetId(int widgetId) {
        getSharedPreferences("LauncherPrefs", MODE_PRIVATE).edit().putInt("saved_widget_id", widgetId).apply();
    }

    private void createWidgetView(int appWidgetId, AppWidgetProviderInfo appWidgetInfo) {
        if (widgetContainer == null || widgetHost == null) return;

        widgetContainer.removeAllViews();

        if (appWidgetInfo == null) {
            if (widgetPlaceholderText != null) {
                widgetPlaceholderText.setVisibility(View.VISIBLE);
            }
            return;
        }

        try {
            if (widgetPlaceholderText != null) {
                widgetPlaceholderText.setVisibility(View.GONE);
            }

            AppWidgetHostView hostView = widgetHost.createView(this, appWidgetId, appWidgetInfo);
            hostView.setAppWidget(appWidgetId, appWidgetInfo);

            ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            hostView.setLayoutParams(layoutParams);
            hostView.setFocusable(true);
            hostView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

            widgetContainer.addView(hostView);
            hostView.postInvalidate();
            widgetContainer.requestLayout();

        } catch (Exception e) {
            if (widgetPlaceholderText != null) {
                widgetPlaceholderText.setVisibility(View.VISIBLE);
            }
            Toast.makeText(this, "שגיאה ברנדור הווידג'ט: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public void showWidgetContextMenu(View anchorView) {
        PopupMenu popup = createStyledPopupMenu(anchorView);
        popup.getMenu().add(0, 10, 0, "הסר ווידג'ט נוכחי");
        popup.getMenu().add(0, 11, 1, "הוסף ווידג'ט חדש");
        popup.getMenu().add(0, 2, 2, "ביטול");

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == 10) {
                removeCurrentWidget();
            } else if (itemId == 11) {
                selectWidget();
            }
            return true;
        });
        popup.show();
    }

    private void removeCurrentWidget() {
        if (widgetContainer != null) {
            widgetContainer.removeAllViews();
        }
        if (widgetPlaceholderText != null) {
            widgetPlaceholderText.setVisibility(View.VISIBLE);
        }
        if (currentWidgetId != -1 && widgetHost != null) {
            try {
                widgetHost.deleteAppWidgetId(currentWidgetId);
            } catch (Exception e) {}
            currentWidgetId = -1;
            saveWidgetId(-1);
        }
        Toast.makeText(this, "הווידג'ט הוסר", Toast.LENGTH_SHORT).show();
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

        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_BIND_WIDGET) {
                int appWidgetId = (data != null) ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, currentWidgetId) : currentWidgetId;
                if (appWidgetId != -1 && widgetManager != null) {
                    currentWidgetId = appWidgetId;
                    saveWidgetId(currentWidgetId);
                    createWidgetView(currentWidgetId, widgetManager.getAppWidgetInfo(currentWidgetId));
                }
                return;
            }

            if (requestCode == REQUEST_PICK_WIDGET) {
                int appWidgetId = (data != null) ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, currentWidgetId) : currentWidgetId;
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
                        Toast.makeText(this, "שגיאה: לא ניתן לקרוא את נתוני הווידג'ט", Toast.LENGTH_SHORT).show();
                    }
                }
                return;
            }

            if (requestCode == REQUEST_CREATE_WIDGET) {
                int appWidgetId = (data != null) ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, currentWidgetId) : currentWidgetId;
                if (appWidgetId != -1 && widgetManager != null) {
                    currentWidgetId = appWidgetId;
                    saveWidgetId(currentWidgetId);
                    createWidgetView(currentWidgetId, widgetManager.getAppWidgetInfo(currentWidgetId));
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
            if (currentWidgetId != -1 && widgetHost != null) {
                try {
                    widgetHost.deleteAppWidgetId(currentWidgetId);
                } catch (Exception e) {}
                currentWidgetId = -1;
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

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            WidgetKeyController.startActiveListening(this, keyCode);
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (WidgetKeyController.handleWidgetKey(this, keyCode)) {
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

    @Override
    protected void onResume() {
        super.onResume();

        if (widgetHost != null) {
            try {
                widgetHost.startListening();
            } catch (Exception e) {}
        }

        SharedPreferences sharedPreferences = getSharedPreferences("LauncherPrefs", MODE_PRIVATE);
        currentWidgetId = sharedPreferences.getInt("saved_widget_id", -1);

        if (currentWidgetId != -1 && widgetManager != null) {
            AppWidgetProviderInfo info = widgetManager.getAppWidgetInfo(currentWidgetId);
            if (info != null) {
                createWidgetView(currentWidgetId, info);
            } else {
                if (widgetPlaceholderText != null) {
                    widgetPlaceholderText.setVisibility(View.VISIBLE);
                }
            }
        } else {
            if (widgetPlaceholderText != null) {
                widgetPlaceholderText.setVisibility(View.VISIBLE);
            }
        }

        if (adapter != null) {
            syncAndCleanLauncherItems();
            adapter.notifyDataSetChanged();
        }
    }

    public void cancelMoveMode() {
        isPickingDestination = false;
        isCopyOperation = false;
        pendingMoveItem = null;
        pendingMovePosition = -1;
    }

    public void handleDestinationSelected(int targetPosition) {
        if (!isCopyOperation && pendingMovePosition == targetPosition) {
            cancelMoveMode();
            return;
        }

        LauncherItem targetItem = launcherItems.get(targetPosition);
        if (pendingMoveItem.isFolder()) {
            cancelMoveMode();
            return;
        }

        AppItem sourceApp = (AppItem) pendingMoveItem;
        AppItem appToPlace = isCopyOperation ? new AppItem(sourceApp.title, sourceApp.packageName) : sourceApp;

        if (targetItem.isFolder()) {
            FolderItem folder = (FolderItem) targetItem;
            folder.appsInside.add(appToPlace);
            if (!isCopyOperation) {
                launcherItems.remove(pendingMovePosition);
            }
        } else {
            FolderItem newFolder = new FolderItem("תיקייה חדשה");
            newFolder.appsInside.add((AppItem) targetItem);
            newFolder.appsInside.add(appToPlace);
            if (!isCopyOperation) {
                launcherItems.remove(pendingMovePosition);
            }
            launcherItems.set(targetPosition, newFolder);
        }

        refreshViews();
        saveLauncherState();
        cancelMoveMode();
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
