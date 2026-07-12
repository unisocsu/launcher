package com.example.keylauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class WidgetKeyController {

    private static final String PREFS_NAME = "WidgetKeyPrefs";
    private static boolean isListening = false;
    private static int pendingKeyCode = -1;
    private static Thread listenerThread = null;

    // פונקציה שנכנסת למצב האזנה אקטיבי עבור מקש ספציפי שנלחץ ארוך
    public static void startActiveListening(Context context, int keyCode) {
        if (isListening) {
            stopListening();
        }

        pendingKeyCode = keyCode;
        isListening = true;
        int displayKey = keyCode - 7; // הפיכה של ה-KeyCode לספרה המוצגת למשתמש (0-9)

        Toast.makeText(context, "🕵️‍♂️ לאנצ'ר במצב האזנה... לחץ כעת על הכפתור בווידג'ט!", Toast.LENGTH_LONG).show();

        listenerThread = new Thread(() -> {
            try {
                // מנקים לוגים קודמים כדי לא לקרוא זבל מהעבר
                Runtime.getRuntime().exec(new String[]{"su", "-c", "logcat -c"});
                
                // האזנה רציפה ורחבה יותר לכל מה שקשור לשידורי מערכת ופעילויות
                Process process = Runtime.getRuntime().exec(new String[]{
                    "su", "-c", "logcat -b system -b main ActivityManager:I Intent:I *:S"
                });

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;

                // מקשיבים למשך 15 שניות לכל היותר כדי לא לבזבז סוללה
                long startTime = System.currentTimeMillis();
                while (isListening && (System.currentTimeMillis() - startTime < 15000)) {
                    if (reader.ready() && (line = reader.readLine()) != null) {
                        
                        // מחפשים פקודות שידור רחבות (Intents / Component / Broadcasts)
                        if (line.contains("act=") || line.contains("cmp=") || line.contains("PendingIntent") || line.contains("flg=")) {
                            String extractedAction = parseActionFromLogLine(line);
                            
                            if (extractedAction != null && !extractedAction.contains("keylauncher")) { // מתעלמים מהלאנצ'ר עצמו
                                saveBinding(context, pendingKeyCode, extractedAction);
                                isListening = false;
                                
                                // חזרה ל-Main Thread כדי להציג Toast הצלחה
                                final String finalAction = extractedAction;
                                if (context instanceof android.app.Activity) {
                                    ((android.app.Activity) context).runOnUiThread(() -> {
                                        Toast.makeText(context, "✅ המקש " + displayKey + " שויך בהצלחה לפעולה!", Toast.LENGTH_LONG).show();
                                    });
                                }
                                break;
                            }
                        }
                    }
                    Thread.sleep(100);
                }

                if (isListening) { // אם עברו 15 שניות ולא נתפס כלום
                    isListening = false;
                    if (context instanceof android.app.Activity) {
                        ((android.app.Activity) context).runOnUiThread(() -> {
                            Toast.makeText(context, "⏱️ הזמן קצוב להאזנה הסתיים. נסה שוב.", Toast.LENGTH_SHORT).show();
                        });
                    }
                }

            } catch (Exception e) {
                Log.e("WidgetKeyController", "שגיאה במצב האזנה אקטיבי", e);
                isListening = false;
            }
        });
        listenerThread.start();
    }

    public static void stopListening() {
        isListening = false;
        if (listenerThread != null) {
            listenerThread.interrupt();
            listenerThread = null;
        }
    }

    // מנגנון סינון משופר ואגרסיבי לחילוץ המידע מכל סוגי ה-Logcat של היצרנים השונים
    private static String parseActionFromLogLine(String line) {
        try {
            // פורמט 1: מחפש פעולה ישירה (Action)
            if (line.contains("act=")) {
                int start = line.indexOf("act=") + 4;
                int end = line.indexOf(" ", start);
                if (end == -1) end = line.indexOf("}", start);
                if (end == -1) end = line.indexOf(")", start);
                if (end != -1) return line.substring(start, end).trim();
            }
            // פורמט 2: מחפש קומפוננטה (Component / Class)
            if (line.contains("cmp=")) {
                int start = line.indexOf("cmp=") + 4;
                int end = line.indexOf(" ", start);
                if (end == -1) end = line.indexOf("}", start);
                if (end == -1) end = line.indexOf(")", start);
                if (end != -1) return line.substring(start, end).trim();
            }
            // פורמט 3: גיבוי כללי למבני לוג ישנים של אנדרואיד
            if (line.contains("Intent {")) {
                int start = line.indexOf("Intent {") + 8;
                int end = line.indexOf("}", start);
                if (end != -1) {
                    String sub = line.substring(start, end);
                    String[] parts = sub.split(" ");
                    for (String part : parts) {
                        if (!part.contains("=") && part.contains(".")) {
                            return part.trim();
                        }
                    }
                }
            }
        } catch (Exception e) {
            // הגנה
        }
        return null;
    }

    public static void saveBinding(Context context, int keyCode, String action) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString("key_action_" + keyCode, action).apply();
    }

    public static boolean handleWidgetKey(Context context, int keyCode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedAction = prefs.getString("key_action_" + keyCode, null);

        if (savedAction != null) {
            try {
                Intent intent;
                if (savedAction.contains("/")) {
                    intent = new Intent();
                    String[] parts = savedAction.split("/");
                    intent.setClassName(parts[0], parts[1]);
                } else {
                    intent = new Intent(savedAction);
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                
                try {
                    context.sendBroadcast(intent);
                } catch (Exception e) {
                    context.startActivity(intent);
                }
                return true; 
            } catch (Exception e) {
                Log.e("WidgetKeyController", "שגיאה בהפעלת פקודה", e);
            }
        }
        return false;
    }
}
