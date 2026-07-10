package com.example.keylauncher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class WidgetKeyController {
    
    private static final String TAG = "WidgetKeyController";
    private static final String PREFS_NAME = "WidgetKeyPrefs";

    /**
     * פונקציה מרכזית לבדיקת לחיצות מקשים בזמן ריצה רגילה.
     * בודקת האם שמור Intent עבור המקש שנלחץ, ואם כן - משגרת אותו.
     */
    public static boolean handleWidgetKey(Context context, int keyCode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedAction = prefs.getString("key_action_" + keyCode, null);
        String savedComponent = prefs.getString("key_cmp_" + keyCode, null);

        if (savedAction != null) {
            try {
                Intent intent = new Intent(savedAction);
                if (savedComponent != null && !savedComponent.isEmpty()) {
                    // אם יש רכיב ספציפי (Component), נגדיר אותו
                    String[] parts = savedComponent.split("/");
                    if (parts.length == 2) {
                        intent.setClassName(parts[0], parts[1].startsWith(".") ? parts[0] + parts[1] : parts[1]);
                    }
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                
                // ננסה לשלוח גם כברודקאסט וגם כאקטיביטי ליתר ביטחון, תלוי בסוג הפקודה
                try {
                    context.sendBroadcast(intent);
                } catch (Exception e) {
                    context.startActivity(intent);
                }

                Toast.makeText(context, "בוצעה פקודת ווידג'ט משויכת!", Toast.LENGTH_SHORT).show();
                return true; // תפסנו את הלחיצה
            } catch (Exception e) {
                Log.e(TAG, "שגיאה בהפעלת ה-Intent השמור", e);
            }
        }
        return false;
    }

    /**
     * הפונקציה שאתה המצאת! מופעלת בלחיצה ארוכה על הווידג'ט.
     * קוראת את הלוג האחרון של המערכת באמצעות רוט, מוצאת את ה-Intent האחרון, ומציגה תפריט שיוך.
     */
    public static void showWidgetBindingDialog(final Context context) {
        Toast.makeText(context, "מחפש את פעולת הווידג'ט האחרונה בלוגים...", Toast.LENGTH_LONG).show();

        // הרצת חוט נפרד (Thread) כדי לא לתקוע את הלאנצ'ר בזמן קריאת הלוגים
        new Thread(new Runnable() {
            @Override
            public void run() {
                String detectedAction = null;
                String detectedComponent = null;

                try {
                    // פקודת רוט שקוראת את 50 השורות האחרונות של ה-ActivityTaskManager מהלוג
                    Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "logcat -d -b events -t 50"});
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    
                    // סורק את הלוגים מלמעלה למטה כדי למצוא את ה-Intent האחרון שנשלח במערכת
                    while ((line = bufferedReader.readLine()) != null) {
                        if (line.contains("am_intent_execute") || line.contains("START u0")) {
                            // חילוץ פשוט של ה-Action וה-Component מתוך השורה בלוג
                            if (line.contains("act=")) {
                                detectedAction = extractValue(line, "act=");
                            }
                            if (line.contains("cmp=")) {
                                detectedComponent = extractValue(line, "cmp=");
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "שגיאה בקריאת הלוגים עם רוט", e);
                }

                final String finalAction = detectedAction;
                final String finalComponent = detectedComponent;

                // חזרה ל-Main Thread כדי להציג את תפריט הבחירה למשתמש
                new android.os.Handler(context.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (finalAction == null) {
                            showManualFallbackDialog(context);
                            return;
                        }

                        // יצירת דיאלוג השיוך החכם שביקשת
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle("שיוך מקש מהיר לווידג'ט");
                        builder.setMessage("נצפתה פקודה אחרונה:\nAction: " + finalAction + 
                                           (finalComponent != null ? "\nComponent: " + finalComponent : "") + 
                                           "\n\nלחץ על המקש הפיזי שברצונך לשייך לפקודה זו (0-9):");

                        builder.setCancelable(true);
                        final AlertDialog dialog = builder.create();

                        // האזנה ללחיצת מקש בזמן שהדיאלוג פתוח כדי לקבוע את השיוך
                        dialog.setOnKeyListener(new android.content.DialogInterface.OnKeyListener() {
                            @Override
                            public boolean onKey(android.content.DialogInterface dialogInterface, int keyCode, KeyEvent event) {
                                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                                    int digit = keyCode - KeyEvent.KEYCODE_0;
                                    
                                    // שמירת ה-Intent בזיכרון המכשיר
                                    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                                    prefs.edit()
                                         .putString("key_action_" + keyCode, finalAction)
                                         .putString("key_cmp_" + keyCode, finalComponent)
                                         .apply();

                                    Toast.makeText(context, "הפעולה שויכה בהצלחה למקש " + digit + "!", Toast.LENGTH_SHORT).show();
                                    dialog.dismiss();
                                    return true;
                                }
                                return false;
                            }
                        });
                        dialog.show();
                    }
                });
            }
        }).start();
    }

    // פונקציית עזר לחילוץ ערכים מתוך שורת הלוג
    private static String extractValue(String line, String marker) {
        try {
            int start = line.indexOf(marker) + marker.length();
            int end = line.indexOf(" ", start);
            if (end == -1) end = line.indexOf("}", start);
            if (end == -1) end = line.length();
            return line.substring(start, end).replace("{", "").replace("}", "");
        } catch (Exception e) {
            return null;
        }
    }

    // במקרה שלא נמצא לוג, נציג פקודת מדיה כללית כברירת מחדל
    private static void showManualFallbackDialog(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("לא נצפתה פקודה");
        builder.setMessage("לא הצלחנו לזהות פקודה אוטומטית בלוג.\nהאם ברצונך לשייך מקש לפקודת Play/Pause כללית של המערכת?");
        builder.setPositiveButton("כן, שייך למקש 5", (d, w) -> {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString("key_action_" + KeyEvent.KEYCODE_5, Intent.ACTION_MEDIA_BUTTON).apply();
            Toast.makeText(context, "מקש 5 שויך לנגן המדיה!", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("ביטול", null);
        builder.show();
    }
}
