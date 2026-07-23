package com.example.keylauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.io.InputStream;
import java.util.List;

public class LauncherAdapter extends RecyclerView.Adapter<LauncherAdapter.ViewHolder> {

    private final MainActivity activityContext;
    private final List<MainActivity.LauncherItem> items;
    private final PackageManager packageManager;

    public LauncherAdapter(MainActivity context, List<MainActivity.LauncherItem> items) {
        this.activityContext = context;
        this.items = items;
        this.packageManager = context.getPackageManager();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_launcher, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MainActivity.LauncherItem item = items.get(position);

        // 🏷️ הצגת שם האפליקציה / התיקייה
        String displayTitle = (item.customTitle != null && !item.customTitle.isEmpty()) 
                ? item.customTitle 
                : item.title;
        holder.titleTextView.setText(displayTitle);

        // 🖱️ תמיכה בפוקוס, מקשים ועכבר
        holder.itemView.setFocusable(true);
        holder.itemView.setFocusableInTouchMode(true);

        // 🖼️ טעינת האייקון
        if (item.isFolder()) {
            loadFolderIcon((MainActivity.FolderItem) item, holder.iconImageView);
        } else {
            loadAppIcon((MainActivity.AppItem) item, holder.iconImageView);
        }

        // 🎯 לחיצה רגילה
        holder.itemView.setOnClickListener(v -> {
            if (activityContext.isPickingDestination()) {
                activityContext.handleDestinationSelected(position, holder.itemView);
            } else if (item.isFolder()) {
                activityContext.openFolder((MainActivity.FolderItem) item);
            } else {
                MainActivity.AppItem appItem = (MainActivity.AppItem) item;
                Intent launchIntent = packageManager.getLaunchIntentForPackage(appItem.packageName);
                if (launchIntent != null) {
                    activityContext.startActivity(launchIntent);
                }
            }
        });

        // ⏳ לחיצה ארוכה
        holder.itemView.setOnLongClickListener(v -> {
            if (!activityContext.isPickingDestination()) {
                activityContext.showContextMenu(holder.itemView, position);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    // 🖼️ טעינת אייקון אפליקציה עם סגירה בטוחה של ה-Stream
    private void loadAppIcon(MainActivity.AppItem appItem, ImageView imageView) {
        if (appItem.customIconUri != null) {
            try (InputStream inputStream = activityContext.getContentResolver().openInputStream(Uri.parse(appItem.customIconUri))) {
                if (inputStream != null) {
                    Drawable drawable = Drawable.createFromStream(inputStream, appItem.customIconUri);
                    if (drawable != null) {
                        imageView.setImageDrawable(drawable);
                        return;
                    }
                }
            } catch (Exception e) {
                /* נמשיך לאייקון המקורי אם הייתה שגיאה בקריאת הקובץ */
            }
        }

        try {
            Drawable icon = packageManager.getApplicationIcon(appItem.packageName);
            imageView.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            imageView.setImageResource(android.R.drawable.sym_def_app_icon);
        }
    }

    // 📁 טעינת אייקון תיקייה
    private void loadFolderIcon(MainActivity.FolderItem folderItem, ImageView imageView) {
        if (folderItem.customIconPath != null) {
            try (InputStream inputStream = activityContext.getContentResolver().openInputStream(Uri.parse(folderItem.customIconPath))) {
                if (inputStream != null) {
                    Drawable drawable = Drawable.createFromStream(inputStream, folderItem.customIconPath);
                    if (drawable != null) {
                        imageView.setImageDrawable(drawable);
                        return;
                    }
                }
            } catch (Exception e) {
                /* תמונה מותאמת לא זמינה */
            }
        }

        if (folderItem.useFirstAppIcon && !folderItem.appsInside.isEmpty()) {
            loadAppIcon(folderItem.appsInside.get(0), imageView);
            return;
        }

        imageView.setImageResource(android.R.drawable.ic_dialog_info);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView iconImageView;
        final TextView titleTextView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            iconImageView = itemView.findViewById(R.id.item_icon);
            titleTextView = itemView.findViewById(R.id.item_title);
        }
    }
}
