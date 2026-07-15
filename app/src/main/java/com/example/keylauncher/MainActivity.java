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
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
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
    private static final int REQUEST_PICK_APP_ICON = 300;
    private static final int REQUEST_PICK_FOLDER_ICON = 200;
    private static final int REQUEST_BIND_WIDGET = 100;
    
    private AppWidgetManager widgetManager;
    private AppWidgetHost widgetHost;
    private ViewGroup widgetContainer;

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
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } catch (Exception e) { /* הגנה */ }
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
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 3);
        recyclerView.setLayoutManager(gridLayoutManager);

        dateTimeTextView = findViewById(R.id.date_time_text);
        if (dateTimeTextView == null) {
            dateTimeTextView = new TextView(this); 
        }
        startTimeUpdate();

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
            widgetHost.startListening();
        } catch (Exception e) {
            Toast.makeText(this, "התקן זה לא תומך בווידג'טים", Toast.LENGTH_LONG).show();
        }

        if (!loadLauncherState()) {
            loadLauncherItems();
        } else {
            syncAndCleanLauncherItems(); 
        }

        currentWidgetId = sharedPreferences.getInt("saved_widget_id", -1);
        if (currentWidgetId != -1 && widgetManager != null) {
            AppWidgetProviderInfo info = widgetManager.getAppWidgetInfo(currentWidgetId);
            if (info != null) {
                createWidgetView(currentWidgetId, info);
            }
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

    // תפריט מוגן - במקרה שהעיצוב המותאם קורס או חסר, נעשה שימוש אוטומטי בתפריט המערכת
    private PopupMenu createStyledPopupMenu(View anchorView) {
        try {
            Context wrapper = new ContextThemeWrapper(this, R.style.PurplePopupMenu);
            return new PopupMenu(wrapper, anchorView);
        } catch (Exception e) {
            return new PopupMenu(this, anchorView);
        }
    }

    public void showContextMenu(View anchorView, int position) {
        PopupMenu popup = createStyledPopupMenu(anchorView);
        
        LauncherItem selectedItem = (currentlyOpenFolderItem != null) ? 
                currentlyOpenFolderItem.appsInside.get(position) : launcherItems.get(position);

        if (currentlyOpenFolderItem == null) {
            popup.getMenu().add(0, 1, 0, "העבר מיקום / מזג לתיקייה");
            if (!selectedItem.isFolder()) {
                popup.getMenu().add(0, 8, 1, "העתק / שכפל למיקום אחר");
            }
            popup.getMenu().add(0, 9, 2, "הגדר מקש קיצור מהיר למיקום");
        }
        
        if (!selectedItem.isFolder()) {
            popup.getMenu().add(0, 20, 3, "ערוך שם ואייקון אפליקציה ✏️");
            popup.getMenu().add(0, 21, 4, "הגדרות עכבר ייעודיות 🖱️");
            popup.getMenu().add(0, 6, 5, "הסר התקנת אפליקציה");
        } else {
            popup.getMenu().add(0, 3, 3, "שנה שם תיקייה");
            popup.getMenu().add(0, 22, 4, "שנה אייקון תיקייה 🖼️");
        }
        popup.getMenu().add(0, 2, 6, "ביטול");

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
                showShortcutKeyDialog(position);
            } else if (id == 3) {
                showRenameFolderDialog((FolderItem) selectedItem);
            } else if (id == 20) {
                showEditAppDialog((AppItem) selectedItem);
            } else if (id == 21) {
                showAppMouseConfigDialog((AppItem) selectedItem);
            } else if (id == 22) {
                showFolderIconConfigDialog((FolderItem) selectedItem);
            } else if (id == 6) {
                try {
                    Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + ((AppItem)selectedItem).packageName));
                    startActivity(uninstallIntent);
                } catch (Exception e) {
                    Toast.makeText(this, "לא ניתן להסיר את האפליקציה", Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        });
        popup.show();
    }

    private void showFolderIconConfigDialog(FolderItem folder) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("הגדרת אייקון לתיקייה: " + folder.title);
        String[] options = {"השתמש באייקון האפליקציה הראשונה בתיקייה", "בחר תמונה מהמכשיר (גלריה)", "איפוס לאייקון ברירת מחדל"};
        
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                folder.useFirstAppIcon = true;
                folder.customIconPath = null;
                Toast.makeText(this, "הוגדר אייקון האפליקציה הראשונה", Toast.LENGTH_SHORT).show();
                refreshViews();
            } else if (which == 1) {
                folderItemEditingNow = folder; 
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(Intent.createChooser(intent, "בחר תמונה לאייקון"), REQUEST_PICK_FOLDER_ICON);
            } else {
                folder.useFirstAppIcon = false;
                folder.customIconPath = null;
                refreshViews();
            }
            saveLauncherState();
        });
        builder.show();
    }

    private void showEditAppDialog(AppItem appItem) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("עריכת אפליקציה");
        
        final EditText input = new EditText(this);
        input.setText(appItem.customTitle != null ? appItem.customTitle : appItem.title);
        builder.setView(input);

        builder.setPositiveButton("שמור שם", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                appItem.customTitle = newName;
                saveLauncherState();
                refreshViews();
            }
        });
        
        builder.setNeutralButton("החלף אייקון מהגלריה", (dialog, which) -> {
            appItemEditingNow = appItem; 
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "בחר אייקון"), REQUEST_PICK_APP_ICON);
        });

        builder.setNegativeButton("ביטול", null);
        builder.show();
    }

    private void showAppMouseConfigDialog(AppItem appItem) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("הגדרת עכבר עבור: " + appItem.title);
        
        try {
            String currentList = android.provider.Settings.Global.getString(getContentResolver(), "mouse_support_list");
            if (currentList == null) currentList = "";
            
            final boolean isCurrentlyEnabled = currentList.contains(appItem.packageName);
            builder.setMessage(isCurrentlyEnabled ? "תמיכת עכבר כרגע מופעלת עבור יישום זה. האם תרצה לכבות אותה?" : "תמיכת עכבר כרגע כבויה עבור יישום זה. האם תרצה להפעיל אותה?");
            
            final String finalCurrentList = currentList;
            builder.setPositiveButton(isCurrentlyEnabled ? "כבה עכבר" : "הפעל עכבר", (dialog, which) -> {
                try {
                    String newList;
                    if (isCurrentlyEnabled) {
                        newList = finalCurrentList.replace(appItem.packageName + ",", "");
                    } else {
                        newList = finalCurrentList.endsWith(",") || finalCurrentList.isEmpty() ? 
                                  finalCurrentList + appItem.packageName + "," : 
                                  finalCurrentList + "," + appItem.packageName + ",";
                    }
                    
                    String cmd = "settings put global mouse_support_list \"" + newList + "\"";
                    Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
                    Toast.makeText(this, "ההגדרה עודכנה במערכת!", Toast.LENGTH_SHORT).show();
                    
                    new Handler().postDelayed(() -> {
                        try {
                            Intent restartIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                            if (restartIntent != null) {
                                restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(restartIntent);
                                Runtime.getRuntime().exec(new String[]{"su", "-c", "pkill -f " + getPackageName()});
                            }
                        } catch (Exception e) {}
                    }, 400);

                } catch (Exception e) {
                    Toast.makeText(this, "שגיאה בביצוע פקודת המערכת", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            builder.setMessage("לא ניתן לגשת להגדרות המערכת במכשיר זה.");
        }
        
        builder.setNegativeButton("סגור", null);
        builder.show();
    }

    public void showWidgetContextMenu(View anchorView) {
        PopupMenu popup = createStyledPopupMenu(anchorView);
        popup.getMenu().add(0, 10, 0, "הסר ווידג'ט נוכחי");
        popup.getMenu().add(0, 11, 1, "הוסף ווידג'ט חדש");
        popup.getMenu().add(0, 2, 2, "ביטול");

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == 10 && widgetContainer != null && widgetContainer.getChildCount() > 0) {
                widgetContainer.removeAllViews();
                if (currentWidgetId != -1 && widgetHost != null) {
                    try {
                        widgetHost.deleteAppWidgetId(currentWidgetId);
                    } catch (Exception e) { /* הגנה */ }
                    
                    currentWidgetId = -1;
                    getSharedPreferences("LauncherPrefs", MODE_PRIVATE).edit().putInt("saved_widget_id", -1).apply();
                }
                Toast.makeText(this, "הווידג'ט הוסר", Toast.LENGTH_SHORT).show();
            } else if (itemId == 11) {
                selectWidget();
            }
            return true;
        });
        popup.show();
    }

    private void showShortcutKeyDialog(int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("הגדר מקש מהיר למיקום זה (0-9)");
        final EditText input = new EditText(this);
        input.setHint("הכנס ספרה אחת");
        builder.setView(input);

        builder.setPositiveButton("שמור", (dialog, which) -> {
            String keyStr = input.getText().toString().trim();
            if (!keyStr.isEmpty() && Character.isDigit(keyStr.charAt(0))) {
                int targetDigit = Character.getNumericValue(keyStr.charAt(0));
                int androidKeyCode = KeyEvent.KEYCODE_0 + targetDigit;
                
                shortcutPositionsMap.put(androidKeyCode, position);
                
                SharedPreferences sharedPreferences = getSharedPreferences("LauncherPrefs", MODE_PRIVATE);
                sharedPreferences.edit().putInt("shortcut_pos_" + androidKeyCode, position).apply();
                
                Toast.makeText(this, "המקש " + targetDigit + " הוגדר למיקום " + position, Toast.LENGTH_SHORT).show();
            }
        });
        builder.show();
    }

    private void showRenameFolderDialog(FolderItem folder) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("שנה שם תיקייה");
        final EditText input = new EditText(this);
        input.setText(folder.title);
        builder.setView(input);
        builder.setPositiveButton("אישור", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                folder.title = newName;
                saveLauncherState();
                refreshViews();
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
        refreshViews();
        saveLauncherState(); 
        cancelMoveMode();
    }

    public void cancelMoveMode() {
        isPickingDestination = false; isCopyOperation = false; pendingMoveItem = null; pendingMovePosition = -1;
    }

    public boolean isPickingDestination() {
        return isPickingDestination;
    }

    public void openFolder(FolderItem folderItem) {
        currentlyOpenFolderItem = folderItem; 
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_folder, null);
        builder.setView(dialogView);

        TextView folderTitle = dialogView.findViewById(R.id.folder_title);
        folderTitle.setText(folderItem.customTitle != null ? folderItem.customTitle : folderItem.title);

        RecyclerView folderRecyclerView = dialogView.findViewById(R.id.folder_recycler_view);
        folderRecyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        
        List<LauncherItem> folderContents = new ArrayList<>(folderItem.appsInside);
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

        if (keyCode == KeyEvent.KEYCODE_CALL) {
            try {
                String launcherPackage = getPackageName();
                String currentList = android.provider.Settings.Global.getString(getContentResolver(), "mouse_support_list");
                if (currentList == null) currentList = "";

                String newList;
                if (currentList.contains(launcherPackage)) {
                    newList = currentList.replace(launcherPackage + ",", "");
                    Toast.makeText(this, "העכבר מבוטל. הלאנצ'ר קם מחדש...", Toast.LENGTH_SHORT).show();
                } else {
                    newList = currentList.endsWith(",") || currentList.isEmpty() ? 
                              currentList + launcherPackage + "," : 
                              currentList + "," + launcherPackage + ",";
                    Toast.makeText(this, "העכבר מופעל! הלאנצ'ר קם מחדש...", Toast.LENGTH_SHORT).show();
                }

                String cmd = "settings put global mouse_support_list \"" + newList + "\"";
                Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});

                new Handler().postDelayed(() -> {
                    try {
                        Intent restartIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                        if (restartIntent != null) {
                            restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(restartIntent);
                            Runtime.getRuntime().exec(new String[]{"su", "-c", "pkill -f " + getPackageName()});
                        }
                    } catch (Exception e) {}
                }, 500);

            } catch (Exception e) {
                Toast.makeText(this, "שגיאה בשינוי ההגדרה והפעלה מחדש", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    // תהליך בחירת הווידג'ט והקצאתו המקורית
    public void selectWidget() {
        if (widgetHost == null) {
            Toast.makeText(this, "מארח הווידג'טים לא אותחל כראוי", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            // שלב א': הקצאת מזהה ווידג'ט חדש עבור הבחירה
            int appWidgetId = widgetHost.allocateAppWidgetId();
            
            // שלב ב': פתיחת הדיאלוג של אנדרואיד המציג רשימה לבחירת הווידג'טים
            Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
            pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            startActivityForResult(pickIntent, REQUEST_PICK_WIDGET);
            
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            
            // אם המשתמש אישר את הביינד (Bind) בחלון המערכת
            if (requestCode == REQUEST_BIND_WIDGET && data != null) {
                int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                if (appWidgetId != -1 && widgetManager != null) {
                    currentWidgetId = appWidgetId;
                    getSharedPreferences("LauncherPrefs", MODE_PRIVATE).edit().putInt("saved_widget_id", currentWidgetId).apply();
                    createWidgetView(currentWidgetId, widgetManager.getAppWidgetInfo(currentWidgetId));
                }
                return;
            }
            
            if (data != null) {
                // המשתמש בחר ווידג'ט מתוך רשימת המערכת
                if (requestCode == REQUEST_PICK_WIDGET) {
                    int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                    android.content.ComponentName provider = data.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER);
                    
                    if (widgetManager != null && provider != null) {
                        // בודקים אם יש לנו אישור רשמי של המערכת לחיבור הווידג'ט
                        if (widgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider)) {
                            // יש הרשאה, אפשר להציג אותו מייד!
                            currentWidgetId = appWidgetId;
                            getSharedPreferences("LauncherPrefs", MODE_PRIVATE).edit().putInt("saved_widget_id", currentWidgetId).apply();
                            createWidgetView(currentWidgetId, widgetManager.getAppWidgetInfo(currentWidgetId));
                        } else {
                            // אין הרשאה – נקפיץ את הבקשה הרשמית המובנית של המערכת לאישור ה-Bind
                            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider);
                            startActivityForResult(intent, REQUEST_BIND_WIDGET);
                        }
                    }
                }
                else if (requestCode == REQUEST_PICK_APP_ICON && appItemEditingNow != null) { 
                    appItemEditingNow.customIconUri = data.getData().toString();
                    appItemEditingNow = null; 
                    saveLauncherState();
                    refreshViews();
                } 
                else if (requestCode == REQUEST_PICK_FOLDER_ICON && folderItemEditingNow != null) { 
                    folderItemEditingNow.customIconPath = data.getData().toString();
                    folderItemEditingNow.useFirstAppIcon = false;
                    folderItemEditingNow = null; 
                    saveLauncherState();
                    refreshViews();
                }
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

    private void createWidgetView(int appWidgetId, AppWidgetProviderInfo appWidgetInfo) {
        if (widgetContainer == null || appWidgetInfo == null || widgetHost == null) return;
        try {
            widgetContainer.removeAllViews();
            AppWidgetHostView hostView = widgetHost.createView(this, appWidgetId, appWidgetInfo);
            hostView.setAppWidget(appWidgetId, appWidgetInfo);
            
            hostView.setFocusable(true);
            hostView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            
            widgetContainer.addView(hostView); 
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בטעינת תצוגת הווידג'ט", Toast.LENGTH_SHORT).show();
        }
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
