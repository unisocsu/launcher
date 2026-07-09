package com.example.keylauncher;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
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
        holder.titleTextView.setText(item.title);

        if (item.isFolder()) {
            holder.iconImageView.setImageResource(android.R.drawable.sym_def_app_icon);
        } else {
            MainActivity.AppItem appItem = (MainActivity.AppItem) item;
            try {
                holder.iconImageView.setImageDrawable(context.getPackageManager().getApplicationIcon(appItem.packageName));
            } catch (Exception e) {
                holder.iconImageView.setImageResource(android.R.drawable.sym_def_app_icon);
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (context instanceof MainActivity) {
                MainActivity mainActivity = (MainActivity) context;
                if (mainActivity.isPickingDestination()) {
                    mainActivity.handleDestinationSelected(position);
                    return;
                }

                if (item.isFolder()) {
                    mainActivity.openFolder((MainActivity.FolderItem) item);
                } else {
                    MainActivity.AppItem appItem = (MainActivity.AppItem) item;
                    try {
                        Intent intent = context.getPackageManager().getLaunchIntentForPackage(appItem.packageName);
                        if (intent != null) {
                            context.startActivity(intent);
                        } else {
                            Toast.makeText(context, "לא ניתן לפתוח את האפליקציה", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(context, "שגיאה בפתיחת האפליקציה", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

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
        ImageView iconImageView;
        TextView titleTextView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            iconImageView = itemView.findViewById(R.id.item_icon);
            titleTextView = itemView.findViewById(R.id.item_title);
        }
    }
}
