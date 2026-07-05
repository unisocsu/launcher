package com.example.keylauncher;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class LauncherAdapter extends RecyclerView.Adapter<LauncherAdapter.ViewHolder> {

    private List<MainActivity.LauncherItem> items;
    private Context context;

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
        
        if (item.isFolder()) {
            MainActivity.FolderItem folder = (MainActivity.FolderItem) item;
            holder.textView.setText(folder.title);
            
            // שימוש באייקון מערכת מובנה במקום אייקון מקומי חסר למניעת שגיאות בבנייה
            if (folder.customIconPath != null) {
                try {
                    holder.imageView.setImageURI(Uri.parse(folder.customIconPath));
                } catch (Exception e) {
                    holder.imageView.setImageResource(android.R.drawable.sym_def_app_icon);
                }
            } else if (folder.useFirstAppIcon && !folder.appsInside.isEmpty()) {
                try {
                    MainActivity.AppItem firstApp = folder.appsInside.get(0);
                    holder.imageView.setImageDrawable(context.getPackageManager().getApplicationIcon(firstApp.packageName));
                } catch (Exception e) {
                    holder.imageView.setImageResource(android.R.drawable.sym_def_app_icon);
                }
            } else {
                holder.imageView.setImageResource(android.R.drawable.sym_def_app_icon);
            }
            
        } else {
            MainActivity.AppItem appItem = (MainActivity.AppItem) item;
            holder.textView.setText(appItem.title);
            
            try {
                holder.imageView.setImageDrawable(context.getPackageManager().getApplicationIcon(appItem.packageName));
            } catch (Exception e) {
                holder.imageView.setImageResource(android.R.drawable.sym_def_app_icon);
            }
        }

        // 1. האזנה ללחיצה ארוכה (Long Click)
        holder.itemView.setOnLongClickListener(v -> {
            if (context instanceof MainActivity) {
                MainActivity mainActivity = (MainActivity) context;
                if (!mainActivity.isPickingDestination()) {
                    mainActivity.showContextMenu(holder.itemView, position);
                    return true;
                }
            }
            return false;
        });

        // 2. טיפול בלחיצה רגילה
        holder.itemView.setOnClickListener(v -> {
            if (context instanceof MainActivity) {
                MainActivity mainActivity = (MainActivity) context;

                if (mainActivity.isPickingDestination()) {
                    mainActivity.handleDestinationSelected(position);
                } else {
                    if (item.isFolder()) {
                        mainActivity.openFolder((MainActivity.FolderItem) item);
                    } else {
                        MainActivity.AppItem appItem = (MainActivity.AppItem) item;
                        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(appItem.packageName);
                        if (launchIntent != null) {
                            context.startActivity(launchIntent);
                        }
                    }
                }
            }
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
            textView = itemView.findViewById(R.id.item_text);
            
            itemView.setFocusable(true);
            itemView.setClickable(true);
        }
    }
}
