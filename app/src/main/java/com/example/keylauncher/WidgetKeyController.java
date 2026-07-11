package com.example.keylauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.json.JSONObject;

public class WidgetKeyController {

    private static final String PREFS_NAME = "WidgetKeyPrefs";
    private static final String KEY_LAST_ACTION = "last_detected_action";
    private static boolean isListening = false;

    // פונקציה שנקראת פעם אחת ב-onCreate של MainActivity כדי להתחיל להקשיב ברקע
    public static void startBackgroundListener(Context context) {
        if (isListening) return;
        isListening = true;

        new Thread(() -> {
            try {
                // ניקוי הלוגים הישנים כדי להתחיל מאפס
                Runtime.getRuntime().exec(new String[]{"su", "-c", "logcat -c"});
                
                // פתיחת Logcat רציף שמסנן רק הודעות של ActivityManager שקשורות לשידור Intents
                Process process = Runtime.getRuntime().exec(new String[]{
                    "su", "-c", "logcat -b system ActivityManager:I *:S"
                });

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                
                while (isListening && (line = reader.readLine()) != null) {
                    // מחפשים סימנים ללחיצה על ווידג'ט או שליחת Intent ברקע
                    if (line.contains("send intent") || line.contains("hasIntent") || line.contains("PendingIntent")) {
                        String extractedAction = parseActionFromLogLine(line);
                        if (extractedAction != null) {
                            // שמירת הפעולה האחרונה שזוהתה בהצלחה
                            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                            prefs.edit().putString(KEY_LAST_ACTION, extractedAction).apply();
                            Log.d("WidgetKeyController", "נלכדה פעילות ווידג'ט ברקע: " + extractedAction);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("WidgetKeyController", "שגיאה בהאזנה ברקע לווידג'טים", e);
                isListening = false;
            }
        }).start();
    }

    // פונקציית עזר לחילוץ ה-Intent מהלוג של אנדרואיד
    private static String parseActionFromLogLine(String line) {
        try {
            if (line.contains("act=")) {
                int start = line.indexOf("act=") + 4;
                int end = line.indexOf(" ", start);
                if (end == -1) end = line.indexOf("}", start);
                if (end != -1) {
                    return line.substring(start, end);
                }
            }
            if (line.contains("cmp=")) {
                int start = line.indexOf("cmp=") + 4;
                int end = line.indexOf(" ", start);
                if (end == -1) end = line.indexOf("}", start);
                if (end != -1) {
                    return line.substring(start, end);
                }
            }
        } catch (Exception e) {
            // הגנה מפני קריסה במקרה של מבנה לוג לא צפוי
        }
        return null;
    }

    // קבלת הפעולה האחרונה שנשמרה
    public static String getLastDetectedAction(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_ACTION, null);
    }

    // שיוך מקש פיזי לפעולה ספציפית
    public static void saveBinding(Context context, int keyCode, String action) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString("key_action_" + keyCode, action).apply();
    }

    // בדיקה והפעלה של מקש בתוך onKeyDown שלMainActivity
    public static boolean handleWidgetKey(Context context, int keyCode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedAction = prefs.getString("key_action_" + keyCode, null);

        if (savedAction != null) {
            try {
                Intent intent;
                if (savedAction.contains("/")) {
                    // מדובר בקומפוננטה ספציפית (Component)
                    intent = new Intent();
                    String[] parts = savedAction.split("/");
                    intent.setClassName(parts[0], parts[1]);
                } else {
                    // מדובר בפעולה כללית (Action)
                    intent = new Intent(savedAction);
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                
                // שליחה כ-Broadcast או הפעלה ישירה בהתאם לסוג
                try {
                    context.sendBroadcast(intent);
                } catch (Exception e) {
                    context.startActivity(intent);
                }
                return true; // המקש טופל בהצלחה
            } catch (Exception e) {
                Log.e("WidgetKeyController", "שגיאה בהפעלת פקודת ווידג'ט משויכת", e);
            }
        }
        return false;
    }
}
