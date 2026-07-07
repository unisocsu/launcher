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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
    private boolean isCopyOperation = false; // משתנה חדש לבדיקה האם זו העתקה או העברה
    private int folderPositionForIconPick = -1;

    public static abstract class LauncherItem {
        public String title;
        public String type; 
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
        if (widgetContainer == null) {
            widgetContainer = (ViewGroup) findViewById(android.R.id.content);
        }
        
        widgetManager = AppWidgetManager.getInstance(this);
        widgetHost = new AppWidgetHost(this, HOST_ID);
        widgetHost.startListening();

        // טעינה וסנכרון חכם מול המכשיר בכל הפעלה
        if (!loadLauncherState()) {
            loadLauncherItems();
        } else {
            syncAndCleanLauncherItems(); 
        }

        adapter = new LauncherAdapter(this, launcherItems);
        recyclerView.setAdapter(adapter);

        recyclerView.post(() -> {
            if (recyclerView.getChildCount() > 0) {
                recyclerView.getChildAt(0).requestFocus();
            }
        });
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
        
        FolderItem testFolder = new FolderItem("מערכת וכלים");
        testFolder.appsInside.add(new AppItem("הגדרות", "com.android.settings"));
        launcherItems.add(0, testFolder); 
        saveLauncherState();
    }

    // פונקציה חדשה: סורקת את המכשיר, מוחקת יישומים שלא קיימים ומוסיפה יישומים חדשים
    private void syncAndCleanLauncherItems() {
        PackageManager pm = getPackageManager();
        
        // 1. השגת כל האפליקציות שבאמת מותקנות כרגע במכשיר
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> availableActivities = pm.queryIntentActivities(intent, 0);
        
        Set<String> installedPackages = new HashSet<>();
        for (ResolveInfo ri : availableActivities) {
            installedPackages.add(ri.activityInfo.packageName);
        }

        // 2. ניקוי אפליקציות שכבר לא קיימות מהמסך הראשי ומתוך תיקיות
        List<LauncherItem> itemsToRemove = new ArrayList<>();
        Set<String> currentlyDisplayedPackages = new HashSet<>();

        for (LauncherItem item : launcherItems) {
            if (item.isFolder()) {
                FolderItem folder = (FolderItem) item;
                List<AppItem> appsInsideToRemove = new ArrayList<>();
                for (AppItem app : folder.appsInside) {
                    if (!installedPackages.contains(app.packageName)) {
                        appsInsideToRemove.add(app);
                    } else {
                        currentlyDisplayedPackages.add(app.packageName);
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
                } else {
                    currentlyDisplayedPackages.add(app.packageName);
                }
            }
        }
        launcherItems.removeAll(itemsToRemove);

        // 3. אופציונלי: הוספת אפליקציות חדשות שהותקנו לאחרונה לסוף הרשימה
        for (ResolveInfo ri : availableActivities) {
            String pkg = ri.activityInfo.packageName;
            if (!pkg.equals(getPackageName()) && !currentlyDisplayedPackages.contains(pkg)) {
                String label = ri.loadLabel(pm).toString();
                launcherItems.add(new AppItem(label, pkg));
            }
        }
        saveLauncherState();
    }

    public void saveLauncherState() {
        SharedPreferences sharedPreferences = getSharedPreferences("LauncherPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        
        Gson gson = new Gson();
        String jsonText = gson.toJson(launcherItems);
        
        editor.putString("launcher_structure", jsonText);
        editor.apply();
    }

    private boolean loadLauncherState() {
        SharedPreferences sharedPreferences = getSharedPreferences("LauncherPrefs", MODE_PRIVATE);
        String jsonText = sharedPreferences.getString("launcher_structure", null);
        
        if (jsonText == null || jsonText.isEmpty()) {
            return false; 
        }

        try {
            launcherItems.clear();
            JSONArray jsonArray = new JSONArray(jsonText);
            
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                String type = obj.optString("type", "app");
                String title = obj.getString("title");
                
                if (type.equals("folder")) {
                    FolderItem folder = new FolderItem(title);
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
                    launcherItems.add(new AppItem(title, obj.getString("packageName")));
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void showContextMenu(View anchorView, int position) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        LauncherItem selectedItem;
        boolean isInFolder = (openFolderDialog != null && openFolderDialog.isShowing() && currentlyOpenFolderItem != null);

        if (isInFolder) {
            selectedItem = currentlyOpenFolderItem.appsInside.get(position);
        } else {
            selectedItem = launcherItems.get(position);
        }

        if (!isInFolder) {
            popup.getMenu().add(0, 1, 0, "העבר מיקום / מזג לתיקייה");
            if (!selectedItem.isFolder()) {
                popup.getMenu().add(0, 8, 0, "העתק / שכפל למיקום אחר"); // פריט חדש בשביל העתקה
            }
        }
        
        if (selectedItem.isFolder()) {
            popup.getMenu().add(0, 3, 1, "שנה שם תיקייה");
            popup.getMenu().add(0, 4, 2, "בחר אייקון מתמונה");
            popup.getMenu().add(0, 5, 3, "השתמש באייקון האפליקציה הראשונה בתיקייה");
        } else {
            popup.getMenu().add(0, 6, 1, "הסר התקנת אפליקציה");
            if (isInFolder) {
                popup.getMenu().add(0, 7, 2, "הסר מתיקייה");
            }
        }
        
        popup.getMenu().add(0, 2, 4, "ביטול");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                pendingMoveItem = launcherItems.get(position);
                pendingMovePosition = position;
                isPickingDestination = true;
                isCopyOperation = false; // מצב העברה רגיל
                Toast.makeText(MainActivity.this, "בחר יעד ולחץ לאישור המיזוג/העברה", Toast.LENGTH_LONG).show();
            } else if (id == 8) {
                pendingMoveItem = launcherItems.get(position);
                pendingMovePosition = position;
                isPickingDestination = true;
                isCopyOperation = true; // הפעלת מצב העתקה!
                Toast.makeText(MainActivity.this, "בחר תיקייה או אפליקציה שאליה תרצה לשכפל", Toast.LENGTH_LONG).show();
            } else if (id == 3) {
                showRenameFolderDialog((FolderItem) selectedItem);
            } else if (id == 4) {
                folderPositionForIconPick = position;
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, REQUEST_PICK_IMAGE);
            } else if (id == 5) {
                FolderItem folder = (FolderItem) selectedItem;
                folder.useFirstAppIcon = true;
                folder.customIconPath = null;
                adapter.notifyDataSetChanged();
                saveLauncherState();
            } else if (id == 6) {
                AppItem appItem = (AppItem) selectedItem;
                Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
                intent.setData(Uri.parse("package:" + appItem.packageName));
                intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
                startActivity(intent);
            } else if (id == 7) {
                AppItem appToExtract = (AppItem) selectedItem;
                launcherItems.add(appToExtract);
                currentlyOpenFolderItem.appsInside.remove(appToExtract);
                
                if (currentlyOpenFolderItem.appsInside.isEmpty()) {
                    launcherItems.remove(currentlyOpenFolderItem);
                }
                
                if (openFolderDialog != null) {
                    openFolderDialog.dismiss();
                }
                adapter.notifyDataSetChanged();
                saveLauncherState();
                Toast.makeText(this, "האפליקציה הועברה למסך הראשי", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        popup.show();
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
                adapter.notifyDataSetChanged();
                saveLauncherState();
            }
        });
        builder.setNegativeButton("ביטול", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    public void handleDestinationSelected(int targetPosition) {
        if (!isCopyOperation && pendingMovePosition == targetPosition) {
            cancelMoveMode();
            return;
        }

        LauncherItem targetItem = launcherItems.get(targetPosition);

        if (pendingMoveItem.isFolder()) {
            Toast.makeText(this, "לא ניתן להעתיק או להעביר תיקייה שלמה", Toast.LENGTH_SHORT).show();
            cancelMoveMode();
            return;
        }

        AppItem sourceApp = (AppItem) pendingMoveItem;
        // יצירת עותק נקי בשביל פעולת ההעתקה
        AppItem appToPlace = isCopyOperation ? new AppItem(sourceApp.title, sourceApp.packageName) : sourceApp;

        if (targetItem.isFolder()) {
            FolderItem targetFolder = (FolderItem) targetItem;
            targetFolder.appsInside.add(appToPlace);
            
            if (!isCopyOperation) {
                launcherItems.remove(pendingMovePosition);
            }
        } else {
            AppItem targetApp = (AppItem) targetItem;

            FolderItem newFolder = new FolderItem("תיקייה חדשה");
            newFolder.appsInside.add(targetApp);
            newFolder.appsInside.add(appToPlace);

            if (!isCopyOperation) {
                launcherItems.remove(pendingMovePosition);
                int actualTargetPosition = targetPosition;
                if (pendingMovePosition < targetPosition) {
                    actualTargetPosition--;
                }
                launcherItems.set(actualTargetPosition, newFolder);
            } else {
                launcherItems.set(targetPosition, newFolder);
            }
        }

        adapter.notifyDataSetChanged();
        saveLauncherState(); 
        cancelMoveMode();
        Toast.makeText(this, isCopyOperation ? "האפליקציה שוכפלה בהצלחה!" : "העברה הושלמה בהצלחה!", Toast.LENGTH_SHORT).show();
    }

    public void cancelMoveMode() {
        isPickingDestination = false;
        isCopyOperation = false;
        pendingMoveItem = null;
        pendingMovePosition = -1;
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
        folderTitle.setText(folderItem.title);

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

        folderRecyclerView.post(() -> {
            if (folderRecyclerView.getChildCount() > 0) {
                folderRecyclerView.getChildAt(0).requestFocus();
            }
        });

        openFolderDialog.setOnDismissListener(dialog -> {
            openFolderDialog = null;
            currentlyOpenFolderItem = null; 
            syncAndCleanLauncherItems(); // סנכרון חלון התיקייה בסגירה
            adapter.notifyDataSetChanged(); 
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_CALL) {
            event.startTracking();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_9) {
            selectWidget();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isPickingDestination) {
                cancelMoveMode();
                Toast.makeText(this, "מצב עריכה בוטל", Toast.LENGTH_SHORT).show();
                return true;
            }
            if (openFolderDialog != null && openFolderDialog.isShowing()) {
                openFolderDialog.dismiss();
                recyclerView.post(() -> {
                    if (recyclerView.getChildCount() > 0) {
                        recyclerView.getChildAt(0).requestFocus();
                    }
                });
                return true; 
            }
            return true; 
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_CALL) {
            try {
                Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                startActivity(dialIntent);
            } catch (Exception e) {
                Toast.makeText(this, "לא נמצאה אפליקציית חייגן במכשיר", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_CALL) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
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
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == REQUEST_PICK_WIDGET) {
                int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                AppWidgetProviderInfo appWidgetInfo = widgetManager.getAppWidgetInfo(appWidgetId);
                if (appWidgetInfo.configure != null) {
                    Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
                    intent.setComponent(appWidgetInfo.configure);
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                    startActivityForResult(intent, REQUEST_CREATE_WIDGET);
                } else {
                    createWidgetView(appWidgetId, appWidgetInfo);
                }
            } else if (requestCode == REQUEST_CREATE_WIDGET) {
                int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                createWidgetView(appWidgetId, widgetManager.getAppWidgetInfo(appWidgetId));
            } else if (requestCode == REQUEST_PICK_IMAGE && folderPositionForIconPick != -1) {
                Uri selectedImageUri = data.getData();
                if (selectedImageUri != null) {
                    FolderItem folder = (FolderItem) launcherItems.get(folderPositionForIconPick);
                    folder.customIconPath = selectedImageUri.toString();
                    folder.useFirstAppIcon = false;
                    adapter.notifyDataSetChanged();
                    saveLauncherState();
                }
                folderPositionForIconPick = -1;
            }
        }
    }

    private void createWidgetView(int appWidgetId, AppWidgetProviderInfo appWidgetInfo) {
        if (widgetContainer == null) return;
        AppWidgetHostView hostView = widgetHost.createView(this, appWidgetId, appWidgetInfo);
        hostView.setAppWidget(appWidgetId, appWidgetInfo);
        hostView.setFocusable(true);
        hostView.setClickable(true);
        widgetContainer.addView(hostView, 0); 
    }

    @Override
    protected void onStop() {
        super.onStop();
        widgetHost.stopListening();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            syncAndCleanLauncherItems(); // סנכרון חם גם כשחוזרים מאפליקציה אחרת
            adapter.notifyDataSetChanged();
        }
    }
}
