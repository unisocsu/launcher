package com.example.keylauncher;

import android.content.Context;
import android.content.Intent;
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
        // תיקון: שימוש ב-.get(position) עבור List במקום סוגריים מרובעים של מערך
        MainActivity.LauncherItem item = items.get(position);
        
        if (item.isFolder()) {
            holder.textView.setText(item.title);
            // תיקון: שימוש באייקון שנמצא בתיקיית ה-mipmap (R.mipmap.ic_launcher)
            holder.imageView.setImageResource(R.mipmap.ic_launcher);
        } else {
            MainActivity.AppItem appItem = (MainActivity.AppItem) item;
            holder.textView.setText(appItem.title);
            
            try {
                holder.imageView.setImageDrawable(context.getPackageManager().getApplicationIcon(appItem.packageName));
            } catch (Exception e) {
                holder.imageView.setImageResource(android.R.drawable.sym_def_app_icon);
            }
        }

        // 1. האזנה ללחיצה ארוכה (Long Click) - מעבר למצב הזזה
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

        // 2. טיפול בלחיצה רגילה (Enter / קצר)
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
            
            // פקודה קריטית לעבודה עם שלטים ומקשים
            itemView.setFocusable(true);
            itemView.setClickable(true);
        }
    }
}
