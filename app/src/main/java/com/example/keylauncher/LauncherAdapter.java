package com.example.keylauncher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.io.InputStream;
import java.util.List;

public class LauncherAdapter extends RecyclerView.Adapter<LauncherAdapter.ViewHolder> {

    private List<MainActivity.LauncherItem> items;
    private MainActivity activity;

    public LauncherAdapter(MainActivity activity, List<MainActivity.LauncherItem> items) {
        this.activity = activity;
        this.items = items;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.launcher_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        MainActivity.LauncherItem item = items.get(position);
        
        // הגדרת כותרת (שם מותאם אישית או שם ברירת מחדל)
        holder.textView.setText(item.customTitle != null ? item.customTitle : item.title);

        if (item.isFolder()) {
            MainActivity.FolderItem folder = (MainActivity.FolderItem) item;
            
            if (folder.customIconPath != null) {
                try {
                    InputStream inputStream = activity.getContentResolver().openInputStream(Uri.parse(folder.customIconPath));
                    Drawable drawable = Drawable.createFromStream(inputStream, folder.customIconPath);
                    holder.imageView.setImageDrawable(drawable);
                } catch (Exception e) {
                    holder.imageView.setImageResource(android.R.drawable.ic_menu_myplaces);
                }
            } else if (folder.useFirstAppIcon && !folder.appsInside.isEmpty()) {
                loadAppIcon(holder.imageView, folder.appsInside.get(0));
            } else {
                // שימוש באייקון מובנה של מערכת אנדרואיד כדי למנוע קריסה על קובץ חסר
                holder.imageView.setImageResource(android.R.drawable.ic_menu_myplaces);
            }
        } else {
            loadAppIcon(holder.imageView, (MainActivity.AppItem) item);
        }

        // שינוי צבע רקע זמני אם אנחנו במצב העברת מיקום
        if (activity.isPickingDestination()) {
            holder.itemView.setBackgroundColor(0x33FF0000); 
        } else {
            holder.itemView.setBackgroundColor(0x00000000);
        }

        holder.itemView.setOnClickListener(v -> {
            if (activity.isPickingDestination()) {
                activity.handleDestinationSelected(position);
            } else {
                if (item.isFolder()) {
                    activity.openFolder((MainActivity.FolderItem) item);
                } else {
                    MainActivity.AppItem appItem = (MainActivity.AppItem) item;
                    PackageManager pm = activity.getPackageManager();
                    try {
                        android.content.Intent intent = pm.getLaunchIntentForPackage(appItem.packageName);
                        if (intent != null) {
                            activity.startActivity(intent);
                        }
                    } catch (Exception e) {
                        android.widget.Toast.makeText(activity, "לא ניתן לפתוח את האפליקציה", android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            activity.showContextMenu(v, position);
            return true;
        });
    }

    private void loadAppIcon(ImageView imageView, MainActivity.AppItem appItem) {
        if (appItem.customIconUri != null) {
            try {
                InputStream inputStream = activity.getContentResolver().openInputStream(Uri.parse(appItem.customIconUri));
                Drawable drawable = Drawable.createFromStream(inputStream, appItem.customIconUri);
                imageView.setImageDrawable(drawable);
                return;
            } catch (Exception e) {
                // הגנה במקרה והתמונה נמחקה מהגלריה
            }
        }
        
        try {
            Drawable icon = activity.getPackageManager().getApplicationIcon(appItem.packageName);
            imageView.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            imageView.setImageResource(android.R.drawable.sym_def_app_icon);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView textView;

        public ViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.item_icon);
            textView = itemView.findViewById(R.id.item_title);
        }
    }
}
