package com.example.keylauncher;

import android.content.Context;
import android.content.Intent; // ייבוא חיוני שנשמט וגרם לשגיאה האחרונה
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

    private final Context context;
    private final List<MainActivity.LauncherItem> items;

    public LauncherAdapter(Context context, List<MainActivity.LauncherItem> items) {
        this.context = context;
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_launcher, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MainActivity.LauncherItem item = items.get(position);
        PackageManager pm = context.getPackageManager();

        // קביעת הכותרת
        holder.textView.setText(item.customTitle != null ? item.customTitle : item.title);

        if (item.isFolder()) {
            MainActivity.FolderItem folder = (MainActivity.FolderItem) item;
            
            if (folder.useFirstAppIcon && !folder.appsInside.isEmpty()) {
                try {
                    Drawable icon = pm.getApplicationIcon(folder.appsInside.get(0).packageName);
                    holder.imageView.setImageDrawable(icon);
                } catch (PackageManager.NameNotFoundException e) {
                    holder.imageView.setImageResource(android.R.drawable.ic_menu_save);
                }
            } else if (folder.customIconPath != null) {
                try {
                    InputStream inputStream = context.getContentResolver().openInputStream(Uri.parse(folder.customIconPath));
                    Drawable drawable = Drawable.createFromStream(inputStream, folder.customIconPath);
                    holder.imageView.setImageDrawable(drawable);
                } catch (Exception e) {
                    holder.imageView.setImageResource(android.R.drawable.ic_menu_save);
                }
            } else {
                // שימוש באייקון מערכת מובנה במקום ic_folder החסר כדי למנוע שגיאות קומפילציה
                holder.imageView.setImageResource(android.R.drawable.ic_menu_save);
            }

            holder.itemView.setOnClickListener(v -> {
                if (context instanceof MainActivity) {
                    MainActivity activity = (MainActivity) context;
                    if (activity.isPickingDestination()) {
                        activity.handleDestinationSelected(position);
                    } else {
                        activity.openFolder(folder);
                    }
                }
            });

        } else {
            MainActivity.AppItem app = (MainActivity.AppItem) item;

            if (app.customIconUri != null) {
                try {
                    InputStream inputStream = context.getContentResolver().openInputStream(Uri.parse(app.customIconUri));
                    Drawable drawable = Drawable.createFromStream(inputStream, app.customIconUri);
                    holder.imageView.setImageDrawable(drawable);
                } catch (Exception e) {
                    try {
                        holder.imageView.setImageDrawable(pm.getApplicationIcon(app.packageName));
                    } catch (PackageManager.NameNotFoundException ex) {
                        holder.imageView.setImageResource(android.R.drawable.sym_def_app_icon);
                    }
                }
            } else {
                try {
                    Drawable icon = pm.getApplicationIcon(app.packageName);
                    holder.imageView.setImageDrawable(icon);
                } catch (PackageManager.NameNotFoundException e) {
                    holder.imageView.setImageResource(android.R.drawable.sym_def_app_icon);
                }
            }

            holder.itemView.setOnClickListener(v -> {
                if (context instanceof MainActivity) {
                    MainActivity activity = (MainActivity) context;
                    if (activity.isPickingDestination()) {
                        activity.handleDestinationSelected(position);
                    } else {
                        Intent launchIntent = pm.getLaunchIntentForPackage(app.packageName);
                        if (launchIntent != null) {
                            context.startActivity(launchIntent);
                        }
                    }
                }
            });
        }

        holder.itemView.setOnLongClickListener(v -> {
            if (context instanceof MainActivity) {
                ((MainActivity) context).showContextMenu(v, position);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView textView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.item_icon);
            textView = itemView.findViewById(R.id.item_title);
        }
    }
}
