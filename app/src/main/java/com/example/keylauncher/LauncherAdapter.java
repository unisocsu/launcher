package com.example.keylauncher;

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

        String displayTitle = (item.customTitle != null && !item.customTitle.isEmpty()) 
                ? item.customTitle 
                : item.title;
        holder.titleTextView.setText(displayTitle);

        holder.itemView.setFocusable(true);
        holder.itemView.setFocusableInTouchMode(true);

        if (item.isFolder()) {
            loadFolderIcon((MainActivity.FolderItem) item, holder.iconImageView);
        } else {
            loadAppIcon((MainActivity.AppItem) item, holder.iconImageView);
        }

        holder.itemView.setOnClickListener(v -> {
            if (activityContext.isPickingDestination()) {
                activityContext.handleDestinationSelected(position, v);
            } else {
                if (item.isFolder()) {
                    activityContext.openFolder((MainActivity.FolderItem) item);
                } else {
                    MainActivity.AppItem app = (MainActivity.AppItem) item;
                    try {
                        activityContext.startActivity(packageManager.getLaunchIntentForPackage(app.packageName));
                    } catch (Exception e) {
                        // השגחה במקרה שאין אפשרות להפעיל
                    }
                }
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            activityContext.showContextMenu(v, position);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private void loadAppIcon(MainActivity.AppItem app, ImageView imageView) {
        if (app.customIconUri != null) {
            try (InputStream inputStream = activityContext.getContentResolver().openInputStream(Uri.parse(app.customIconUri))) {
                Drawable drawable = Drawable.createFromStream(inputStream, app.customIconUri);
                if (drawable != null) {
                    imageView.setImageDrawable(drawable);
                    return;
                }
            } catch (Exception e) {
                // נפילה לאייקון ברירת מחדל במקרה שגיאה
            }
        }
        try {
            imageView.setImageDrawable(packageManager.getApplicationIcon(app.packageName));
        } catch (Exception e) {
            imageView.setImageResource(android.R.drawable.sym_def_app_icon);
        }
    }

    private void loadFolderIcon(MainActivity.FolderItem folder, ImageView imageView) {
        if (folder.customIconPath != null) {
            try (InputStream inputStream = activityContext.getContentResolver().openInputStream(Uri.parse(folder.customIconPath))) {
                Drawable drawable = Drawable.createFromStream(inputStream, folder.customIconPath);
                if (drawable != null) {
                    imageView.setImageDrawable(drawable);
                    return;
                }
            } catch (Exception e) {
                // נפילה לאייקון ברירת מחדל
            }
        }
        if (folder.useFirstAppIcon && !folder.appsInside.isEmpty()) {
            loadAppIcon(folder.appsInside.get(0), imageView);
        } else {
            imageView.setImageResource(android.R.drawable.ic_menu_agenda);
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView iconImageView;
        TextView titleTextView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            iconImageView = itemView.findViewById(R.id.item_icon);
            titleTextView = itemView.findViewById(R.id.item_title);
        }
    }
}
