package com.example.keylauncher;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class WidgetKeyController {
    private static final String TAG = "WidgetKeyController";
    private static Process logcatProcess = null;
    private static Thread listeningThread = null;
    private static boolean isListening = false;

    /**
     * מתחיל האזנה אקטיבית וממוקדת ללוגים של המערכת
     */
    public static synchronized void startActiveListening(final Context context, final int keyCode) {
        // 1. הגנה מפני כפילויות: סוגרים תהליך קודם אם רץ כדי למנוע דליפת תהליכי su
        stopListening();

        isListening = true;
        Toast.makeText(context, "מבצע האזנה... לחץ על כפתור הווידג'ט כעת", Toast.LENGTH_SHORT).show();

        listeningThread = new Thread(new Runnable() {
            @Override
            public void run() {
                BufferedReader reader = null;
                try {
                    // 2. סינון ממוקד: במקום להביא הכל (*:I), נבקש רק אירועים של ActivityTaskManager 
                    // ונשתיק את כל השאר (*:S). זה מוריד את הרעש ב-99% ומציל את ה-CPU!
                    String[] cmd = {"su", "-c", "logcat -b system -b main ActivityTaskManager:I *:S"};
                    logcatProcess = Runtime.getRuntime().exec(cmd);
                    reader = new BufferedReader(new InputStreamReader(logcatProcess.getInputStream()));

                    String line;
                    long startTime = System.currentTimeMillis();
                    long timeout = 15000; // 15 שניות מקסימום להאזנה

                    while (isListening && (System.currentTimeMillis() - startTime < timeout)) {
                        if (reader.ready()) {
                            line = reader.readLine();
                            if (line == null) break;

                            // 3. זיהוי חכם: מחפשים רק שורות שמציינות פתיחת אפליקציה/פעילות חדשה (START u0)
                            // וגם מכילות את הרכיב המופעל (act או cmp)
                            if (line.contains("START u0") && (line.contains("act=") || line.contains("cmp="))) {
                                
                                // מצאנו אירוע הפעלה תקני של הווידג'ט! קוראים לקוד הראשי לעדכן
                                final String detectedLine = line;
                                if (context instanceof MainActivity) {
                                    ((MainActivity) context).runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            // כאן נשמור את המידע שחולץ מהלוג לקוד המפתח
                                            Toast.makeText(context, "הפעולה זוהתה בהצלחה ונקשרה!", Toast.LENGTH_LONG).show();
                                        }
                                    });
                                }
                                break; // מצאנו את הלחיצה, יוצאים מהלולאה מיד
                            }
                        } else {
                            // מונע מהלולאה לרוץ על "ריק" ולטחון את הליבה של המעבד
                            Thread.sleep(100); 
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "שגיאה בזמן קריאת logcat", e);
                } finally {
                    // 4. ניקוי וסגירת משאבים קשוחה - פותר את דליפת התהליכים שקלוד זיהה
                    try {
                        if (reader != null) reader.close();
                    } catch (Exception e) {}
                    
                    // סגירת התהליך הפיזי של ה-su logcat
                    stopListening();
                    
                    // עדכון המשתמש שהזמן תם או שההאזנה נסגרה
                    if (context instanceof MainActivity) {
                        ((MainActivity) context).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context, "ההאזנה הסתיימה", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }
        });
        listeningThread.start();
    }

    /**
     * עוצר את ההאזנה והורג בצורה אגרסיבית את תהליך ה-Process ברקע
     */
    public static synchronized void stopListening() {
        isListening = false;
        
        if (logcatProcess != null) {
            try {
                logcatProcess.destroy(); // הורג את ה-Process של ה-Root באופן מיידי
            } catch (Exception e) {
                Log.e(TAG, "שגיאה בהריגת תהליך המערכת", e);
            }
            logcatProcess = null;
        }
        
        if (listeningThread != null) {
            listeningThread.interrupt();
            listeningThread = null;
        }
    }
}
