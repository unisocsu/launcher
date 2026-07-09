package com.example.keylauncher;

import org.json.JSONArray;
import org.json.JSONObject;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;

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

    private RecyclerView recyclerView;
    private LauncherAdapter adapter;
    private List<LauncherItem> launcherItems = new ArrayList<>();
    private AlertDialog openFolderDialog = null;
    private FolderItem currentlyOpenFolderItem = null; 

    private TextView dateTimeTextView;
    private Handler timeHandler = new Handler();
    private Runnable timeRunnable;

    private static final int HOST_ID = 1024;
    private static final int REQUEST_PICK_WIDGET = 1;
    private static final int REQUEST_CREATE_WIDGET = 2;
    private static final int REQUEST_PICK_IMAGE = 3;
    
    private AppWidgetManager widgetManager;
    private AppWidgetHost widgetHost;
    private ViewGroup widgetContainer;

    private LauncherItem pendingMoveItem = null;
    private int pendingMovePosition = -1;
    private boolean isPickingDestination = false;
    private boolean isCopyOperation = false;
    private int folderPositionForIconPick = -1;

    // ניהול עכבר פנימי וירטואלי
    private boolean isVirtualMouseActive = false;
    private int currentWidgetId = -1;

    // מפת מקשי קיצור לפתיחה מהירה (Key -> PackageName/FolderTitle)
    private Map<Integer, String> shortcutKeysMap = new HashMap<>();

    public static abstract class LauncherItem {
        public String title;
        public String type; 
        public int shortcutKey = -1; // מאפיין מקש קיצור חדש
        public abstract boolean isFolder();
    }

    public static class AppItem extends LauncherItem {
        public String packageName;
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
        
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER,
            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER
        );
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.launcher_recycler_view);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 3);
        recyclerView.setLayoutManager(gridLayoutManager);

        dateTimeTextView = findViewById(R.id.date_time_text);
        if (dateTimeTextView == null) {
            dateTimeTextView = new TextView(this); 
        }
        startTimeUpdate();

        widgetContainer = findViewById(R.id.widget_container);
        if (widgetContainer != null) {
            // הוספת אפשרות לחיצה ארוכה על מיכל הווידג'טים הראשי
            widgetContainer.setOnLongClickListener(v -> {
                showWidgetContextMenu(v);
                return true;
            });
        }
        
        widgetManager = AppWidgetManager.getInstance(this);
        widgetHost = new AppWidgetHost(this, HOST_ID);
        widgetHost.startListening();

        if (!loadLauncherState()) {
            loadLauncherItems();
        } else {
            syncAndCleanLauncherItems(); 
        }

        adapter = new LauncherAdapter(this, launcherItems);
        recyclerView.setAdapter(adapter);
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
    protected void onDestroy() {
        super.onDestroy();
        timeHandler.removeCallbacks(timeRunnable);
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
        shortcutKeysMap.clear();

        for (LauncherItem item : launcherItems) {
            // בנייה מחדש של מפת מקשי הקיצור בזמן הטעינה
            if (item.shortcutKey != -1) {
                shortcutKeysMap.put(item.shortcutKey, item.isFolder() ? item.title : ((AppItem) item).packageName);
            }

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
                int shortcut = obj.optInt("shortcutKey", -1);
                
                if (type.equals("folder")) {
                    FolderItem folder = new FolderItem(title);
                    folder.shortcutKey = shortcut;
                    folder.customIconPath = obj.optString("customIconPath", null);
                    folder.useFirstAppIcon = obj.optBoolean("useFirstAppIcon", false);
                    JSONArray appsArray = obj.optJSONArray("appsInside");
                    if (appsArray != null) {
                        for (int j = 0; j < appsArray.length(); j++) {
                            JSONObject appObj = appsArray.getJSONObject(j);
                            folder.appsInside.add(new AppItem(appObj.getString("title"), appObj.getString("packageName")));
                        }
                    }
                    launcherItems.add(folder);
                } else {
                    AppItem app = new AppItem(title, obj.getString("packageName"));
                    app.shortcutKey = shortcut;
                    launcherItems.add(app);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void showContextMenu(View anchorView, int position) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        LauncherItem selectedItem = (openFolderDialog != null && currentlyOpenFolderItem != null) ? 
                currentlyOpenFolderItem.appsInside.get(position) : launcherItems.get(position);

        popup.getMenu().add(0, 1, 0, "העבר מיקום / מזג לתיקייה");
        if (!selectedItem.isFolder()) {
            popup.getMenu().add(0, 8, 1, "העתק / שכפל למיקום אחר");
        }
        popup.getMenu().add(0, 9, 2, "הגדר מקש קיצור מהיר"); // אפשרות ד' החדשה
        
        if (selectedItem.isFolder()) {
            popup.getMenu().add(0, 3, 3, "שנה שם תיקייה");
        } else {
            popup.getMenu().add(0, 6, 3, "הסר התקנת אפליקציה");
        }
        popup.getMenu().add(0, 2, 4, "ביטול");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                pendingMoveItem = launcherItems.get(position);
                pendingMovePosition = position;
                isPickingDestination = true;
                isCopyOperation = false;
            } else if (id == 8) {
                pendingMoveItem = launcherItems.get(position);
                pendingMovePosition = position;
                isPickingDestination = true;
                isCopyOperation = true;
            } else if (id == 9) {
                showShortcutKeyDialog(selectedItem);
            } else if (id == 3) {
                showRenameFolderDialog((FolderItem) selectedItem);
            } else if (id == 6) {
                Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + ((AppItem)selectedItem).packageName));
                startActivity(intent);
            }
            return true;
        });
        popup.show();
    }

    // תפריט הקשר מיוחד לווידג'טים (דרישה ג')
    private void showWidgetContextMenu(View anchorView) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        popup.getMenu().add(0, 10, 0, "הסר ווידג'ט נוכחי");
        popup.getMenu().add(0, 11, 1, "הוסף ווידג'ט חדש (מקש 9)");
        popup.getMenu().add(0, 2, 2, "ביטול");

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 10 && widgetContainer != null && widgetContainer.getChildCount() > 0) {
                widgetContainer.removeAllViews();
                if (currentWidgetId != -1) {
                    widgetHost.deleteAppWidgetId(currentWidgetId);
                    currentWidgetId = -1;
                }
                Toast.makeText(this, "הווידג'ט הוסר", Toast.LENGTH_SHORT).show();
            } else if (item.getItemId() == 11) {
                selectWidget();
            }
            return true;
        });
        popup.show();
    }

    // דיאלוג לקליטת מקש קיצור (דרישה ד')
    private void showShortcutKeyDialog(LauncherItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("הגדר מקש מהיר (0-9)");
        final EditText input = new EditText(this);
        input.setHint("הכנס ספרה אחת");
        builder.setView(input);

        builder.setPositiveButton("שמור", (dialog, which) -> {
            String keyStr = input.getText().toString().trim();
            if (!keyStr.isEmpty() && Character.isDigit(keyStr.charAt(0))) {
                int targetDigit = Character.getNumericValue(keyStr.charAt(0));
                int androidKeyCode = KeyEvent.KEYCODE_0 + targetDigit;
                
                item.shortcutKey = androidKeyCode;
                shortcutKeysMap.put(androidKeyCode, item.isFolder() ? item.title : ((AppItem) item).packageName);
                saveLauncherState();
                Toast.makeText(this, "המקש " + targetDigit + " הוגדר בהצלחה עבור " + item.title, Toast.LENGTH_SHORT).show();
            }
        });
        builder.show();
    }

    public void handleDestinationSelected(int targetPosition) {
        if (!isCopyOperation && pendingMovePosition == targetPosition) { cancelMoveMode(); return; }
        LauncherItem targetItem = launcherItems.get(targetPosition);
        if (pendingMoveItem.isFolder()) { cancelMoveMode(); return; }

        AppItem sourceApp = (AppItem) pendingMoveItem;
        AppItem appToPlace = isCopyOperation ? new AppItem(sourceApp.title, sourceApp.packageName) : sourceApp;

        if (targetItem.isFolder()) {
            ((FolderItem) targetItem).appsInside.add(appToPlace);
            if (!isCopyOperation) launcherItems.remove(pendingMovePosition);
        } else {
            FolderItem newFolder = new FolderItem("תיקייה חדשה");
            newFolder.appsInside.add((AppItem) targetItem);
            newFolder.appsInside.add(appToPlace);
            if (!isCopyOperation) launcherItems.remove(pendingMovePosition);
            launcherItems.set(targetPosition, newFolder);
        }
        adapter.notifyDataSetChanged();
        saveLauncherState(); 
        cancelMoveMode();
    }

    public void cancelMoveMode() {
        isPickingDestination = false; isCopyOperation = false; pendingMoveItem = null; pendingMovePosition = -1;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // בדיקת מקשי קיצור מהירים במסך הראשי (דרישה ד')
        if (openFolderDialog == null && shortcutKeysMap.containsKey(keyCode)) {
            String target = shortcutKeysMap.get(keyCode);
            for (LauncherItem item : launcherItems) {
                if (item.isFolder() && item.title.equals(target)) {
                    openFolder(((FolderItem) item));
                    return true;
                } else if (!item.isFolder() && ((AppItem) item).packageName.equals(target)) {
                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(target);
                    if (launchIntent != null) startActivity(launchIntent);
                    return true;
                }
            }
        }

        // לחיצה קצרה על מקש חיוג מפעילה עכבר וירטואלי פנימי (דרישה ב')
        if (keyCode == KeyEvent.KEYCODE_CALL) {
            isVirtualMouseActive = !isVirtualMouseActive;
            Toast.makeText(this, isVirtualMouseActive ? "עכבר פנימי מופעל (השתמש במקשי החיצים)" : "עכבר פנימי כבוי", Toast.LENGTH_SHORT).show();
            return true;
        }

        // ניתוב מקשים עבור העכבר הפנימי
        if (isVirtualMouseActive) {
            View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return currentFocus.focusSearch(View.FOCUS_RIGHT).requestFocus();
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return currentFocus.focusSearch(View.FOCUS_LEFT).requestFocus();
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return currentFocus.focusSearch(View.FOCUS_UP).requestFocus();
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return currentFocus.focusSearch(View.FOCUS_DOWN).requestFocus();
            }
        }

        if (keyCode == KeyEvent.KEYCODE_9) { selectWidget(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    public void selectWidget() {
        int appWidgetId = widgetHost.allocateAppWidgetId();
        Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
        pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        startActivityForResult(pickIntent, REQUEST_PICK_WIDGET);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && requestCode == REQUEST_PICK_WIDGET) {
            currentWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            createWidgetView(currentWidgetId, widgetManager.getAppWidgetInfo(currentWidgetId));
        }
    }

    private void createWidgetView(int appWidgetId, AppWidgetProviderInfo appWidgetInfo) {
        if (widgetContainer == null || appWidgetInfo == null) return;
        widgetContainer.removeAllViews();
        AppWidgetHostView hostView = widgetHost.createView(this, appWidgetId, appWidgetInfo);
        hostView.setAppWidget(appWidgetId, appWidgetInfo);
        widgetContainer.addView(hostView); 
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            syncAndCleanLauncherItems();
            adapter.notifyDataSetChanged();
        }
    }
}
