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
import com.example.keylauncher.MainActivity.AppItem;
import com.example.keylauncher.MainActivity.FolderItem;
import com.example.keylauncher.MainActivity.LauncherItem;
import java.io.InputStream;
import java.util.List;

public class LauncherAdapter extends RecyclerView.Adapter<LauncherAdapter.ViewHolder> {

    private final Context context;
    private final List<LauncherItem> items;
    private final PackageManager packageManager;

    public LauncherAdapter(Context context, List<LauncherItem> items) {
        this.context = context;
        this.items = items;
        this.packageManager = context.getPackageManager();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // ודא שיש לך קובץ launcher_item.xml ב-layout שכולל ImageView ו-TextView
        View view = LayoutInflater.from(context).inflate(R.layout.launcher_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LauncherItem item = items.get(position);

        // --- 1. טיפול בהצגת הטקסט (שם האפליקציה או התיקייה) ---
        if (item.customTitle != null && !item.customTitle.isEmpty()) {
            holder.textView.setText(item.customTitle); // מציג שם מותאם אישית אם קיים
        } else {
            holder.textView.setText(item.title); // שם ברירת המחדל
        }

        // --- 2. טיפול בהצגת האייקון (אפליקציה או תיקייה) ---
        if (item.isFolder()) {
            FolderItem folder = (FolderItem) item;
            
            if (folder.customIconPath != null) {
                // אופציה א': טעינת אייקון מותאם אישית לתיקייה מהגלריה
                holder.imageView.setImageURI(Uri.parse(folder.customIconPath));
            } else if (folder.useFirstAppIcon && !folder.appsInside.isEmpty()) {
                // אופציה ב': שימוש באייקון של האפליקציה הראשונה בתוך התיקייה
                loadAppIcon(folder.appsInside.get(0).packageName, folder.appsInside.get(0).customIconUri, holder.imageView);
            } else {
                // אופציה ג': אייקון ברירת מחדל לתיקייה מהמשאבים שלך
                holder.imageView.setImageResource(R.drawable.ic_folder); // ודא שיש לך תמונת תיקייה ב-drawable
            }
        } else {
            // טיפול באפליקציה רגילה
            AppItem app = (AppItem) item;
            loadAppIcon(app.packageName, app.customIconUri, holder.imageView);
        }

        // --- 3. טיפול בלחיצות (קליק רגיל וקליק ארוך) ---
        holder.itemView.setOnClickListener(v -> {
            MainActivity activity = (MainActivity) context;
            
            if (activity.isPickingDestination()) {
                // אם אנחנו במצב העברת מיקום / מיזוג
                activity.handleDestinationSelected(position);
            } else {
                // התנהגות רגילה
                if (item.isFolder()) {
                    activity.openFolder((FolderItem) item);
                } else {
                    AppItem app = (AppItem) item;
                    Intent launchIntent = packageManager.getLaunchIntentForPackage(app.packageName);
                    if (launchIntent != null) {
                        context.startActivity(launchIntent);
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

    /**
     * פונקציית עזר חכמה לטעינת אייקון של אפליקציה (בודקת קודם אם יש אייקון מותאם מהגלריה)
     */
    private void loadAppIcon(String packageName, String customIconUri, ImageView imageView) {
        if (customIconUri != null) {
            try {
                // מנסה לטעון את התמונה שנבחרה מהגלריה
                InputStream inputStream = context.getContentResolver().openInputStream(Uri.parse(customIconUri));
                Drawable drawable = Drawable.createFromStream(inputStream, customIconUri);
                if (drawable != null) {
                    imageView.setImageDrawable(drawable);
                    return;
                }
            } catch (Exception e) {
                // אם נכשל (למשל חסרה הרשאת קריאה זמנית), ימשיך לברירת המחדל למטה
            }
        }

        // אם אין אייקון מותאם אישית או שהטעינה נכשלה – נטען את האייקון המקורי מהמערכת
        try {
            Drawable icon = packageManager.getApplicationIcon(packageName);
            imageView.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            imageView.setImageResource(android.R.drawable.sym_def_app_icon); // אייקון מגירה כללי במקרה של שגיאה
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView textView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.item_icon); // ודא שה-ID תואם ל-XML שלך
            textView = itemView.findViewById(R.id.item_title); // ודא שה-ID תואם ל-XML שלך
        }
    }
}
