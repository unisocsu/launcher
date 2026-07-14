package com.example.keylauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class LauncherAdapter extends RecyclerView.Adapter<LauncherAdapter.ViewHolder> {

    private Context context;
    private List<MainActivity.LauncherItem> items;
    private MainActivity mainActivity;

    public LauncherAdapter(Context context, List<MainActivity.LauncherItem> items) {
        this.context = context;
        this.items = items;
        if (context instanceof MainActivity) {
            this.mainActivity = (MainActivity) context;
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // ודא ששם ה-layout כאן תואם במדויק לקובץ ה-XML שלך (item_launcher או launcher_item)
        View view = LayoutInflater.from(context).inflate(R.layout.item_launcher, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        MainActivity.LauncherItem item = items.get(position);
        
        holder.textView.setText(item.customTitle != null ? item.customTitle : item.title);

        if (mainActivity != null && mainActivity.isPickingDestination()) {
            holder.itemView.setBackgroundColor(Color.parseColor("#44FF0000"));
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        }

        if (item.isFolder()) {
            MainActivity.FolderItem folder = (MainActivity.FolderItem) item;
            
            if (folder.customIconPath != null) {
                try {
                    holder.imageView.setImageURI(Uri.parse(folder.customIconPath));
                } catch (Exception e) {
                    holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            } else if (folder.useFirstAppIcon && !folder.appsInside.isEmpty()) {
                loadAppIcon(folder.appsInside.get(0), holder.imageView);
            } else {
                // שימוש באייקון גלריה מובנה שקיים ב-100% בכל גרסאות אנדרואיד
                holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        } else {
            MainActivity.AppItem app = (MainActivity.AppItem) item;
            loadAppIcon(app, holder.imageView);
        }

        holder.itemView.setOnClickListener(v -> {
            if (mainActivity != null && mainActivity.isPickingDestination()) {
                mainActivity.handleDestinationSelected(position);
                return;
            }

            if (item.isFolder()) {
                if (mainActivity != null) mainActivity.openFolder((MainActivity.FolderItem) item);
            } else {
                MainActivity.AppItem app = (MainActivity.AppItem) item;
                Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(app.packageName);
                if (launchIntent != null) {
                    context.startActivity(launchIntent);
                }
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (mainActivity != null) {
                mainActivity.showContextMenu(v, position);
            }
            return true;
        });
    }

    private void loadAppIcon(MainActivity.AppItem app, ImageView imageView) {
        if (app.customIconUri != null) {
            try {
                imageView.setImageURI(Uri.parse(app.customIconUri));
            } catch (Exception e) {
                imageView.setImageResource(android.R.drawable.sym_def_app_icon);
            }
        } else {
            try {
                imageView.setImageDrawable(context.getPackageManager().getApplicationIcon(app.packageName));
            } catch (PackageManager.NameNotFoundException e) {
                imageView.setImageResource(android.R.drawable.sym_def_app_icon);
            }
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
