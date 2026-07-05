package com.example.keylauncher;

import org.json.JSONArray;
import org.json.JSONObject;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu;
import android.view.MenuItem;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;

import com.google.gson.Gson;
// תיקון: ה-imports הכפולים והמשובשים של GSON הוסרו מכאן

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private RecyclerView recyclerView;
    private LauncherAdapter adapter;
    private List<LauncherItem> launcherItems = new ArrayList<>();
    private AlertDialog openFolderDialog = null;

    private static final int HOST_ID = 1024;
    private static final int REQUEST_PICK_WIDGET = 1;
    private static final int REQUEST_CREATE_WIDGET = 2;
    private AppWidgetManager widgetManager;
    private AppWidgetHost widgetHost;
    private ViewGroup widgetContainer;

    private LauncherItem pendingMoveItem = null;
    private int pendingMovePosition = -1;
    private boolean isPickingDestination = false;

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
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.launcher_recycler_view);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 4);
        recyclerView.setLayoutManager(gridLayoutManager);

        // הגנה מפני קריסה: מחפש קודם כל את widget_container, ואם לא נמצא משתמש ב-Root של המערכת
        widgetContainer = findViewById(R.id.widget_container);
        if (widgetContainer == null) {
            widgetContainer = findViewById(android.R.id.content);
        }
        
        widgetManager = AppWidgetManager.getInstance(this);
        widgetHost = new AppWidgetHost(this, HOST_ID);
        widgetHost.startListening();

        if (!loadLauncherState()) {
            loadLauncherItems();
        }

        adapter = new LauncherAdapter(this, launcherItems);
        recyclerView.setAdapter(adapter);

        recyclerView.post(() -> {
            if (recyclerView.getChildCount() > 0) {
                recyclerView.getChildAt(0).requestFocus();
            }
        });
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

    private void saveLauncherState() {
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
        popup.getMenu().add(0, 1, 0, "העבר מיקום / מזג לתיקייה");
        popup.getMenu().add(0, 2, 1, "ביטול");

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == 1) {
                    pendingMoveItem = launcherItems.get(position);
                    pendingMovePosition = position;
                    isPickingDestination = true;
                    Toast.makeText(MainActivity.this, "ניווט עם הפוקוס ליעד ולחץ Enter לאישור", Toast.LENGTH_LONG).show();
                }
                return true;
            }
        });
        popup.show();
    }

    public void handleDestinationSelected(int targetPosition) {
        if (pendingMovePosition == targetPosition) {
            cancelMoveMode();
            return;
        }

        LauncherItem targetItem = launcherItems.get(targetPosition);

        if (pendingMoveItem.isFolder()) {
            Toast.makeText(this, "לא ניתן להעביר תיקייה שלמה", Toast.LENGTH_SHORT).show();
            cancelMoveMode();
            return;
        }

        AppItem sourceApp = (AppItem) pendingMoveItem;

        if (targetItem.isFolder()) {
            FolderItem targetFolder = (FolderItem) targetItem;
            targetFolder.appsInside.add(sourceApp);
            launcherItems.remove(pendingMovePosition);
        } else {
            AppItem targetApp = (AppItem) targetItem;

            FolderItem newFolder = new FolderItem("תיקייה חדשה");
            newFolder.appsInside.add(targetApp);
            newFolder.appsInside.add(sourceApp);

            launcherItems.remove(pendingMovePosition);
            
            int actualTargetPosition = targetPosition;
            if (pendingMovePosition < targetPosition) {
                actualTargetPosition--;
            }
            
            launcherItems.set(actualTargetPosition, newFolder);
        }

        adapter.notifyDataSetChanged();
        saveLauncherState(); 
        cancelMoveMode();
        Toast.makeText(this, "העברה הושלמה בהצלחה!", Toast.LENGTH_SHORT).show();
    }

    public void cancelMoveMode() {
        isPickingDestination = false;
        pendingMoveItem = null;
        pendingMovePosition = -1;
    }

    public boolean isPickingDestination() {
        return isPickingDestination;
    }

    public void openFolder(FolderItem folderItem) {
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

        openFolderDialog.setOnDismissListener(dialog -> openFolderDialog = null);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_9) {
            selectWidget();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isPickingDestination) {
                cancelMoveMode();
                Toast.makeText(this, "מצב העברה בוטל", Toast.LENGTH_SHORT).show();
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
            int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            if (requestCode == REQUEST_PICK_WIDGET) {
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
                createWidgetView(appWidgetId, widgetManager.getAppWidgetInfo(appWidgetId));
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
}
