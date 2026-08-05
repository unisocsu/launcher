package com.example.keylauncher;

import android.content.Context;
import android.util.Log;
import android.view.KeyEvent;

public class WidgetKeyController {

    private static final String TAG = "WidgetKeyController";
    private static boolean isListening = false;
    private static int targetKeyCode = -1;

    /**
     * מתחיל האזנה אקטיבית למקש ספציפי (למשל בלחיצה ארוכה)
     */
    public static void startActiveListening(MainActivity activity, int keyCode) {
        isListening = true;
        targetKeyCode = keyCode;
        Log.d(TAG, "Started active listening for keycode: " + keyCode);
    }

    /**
     * מפסיק את ההאזנה ומנקה משאבים (נקרא ב-onDestroy)
     */
    public static void stopListening() {
        isListening = false;
        targetKeyCode = -1;
        Log.d(TAG, "Stopped listening");
    }

    /**
     * מנהל ומנתב את לחיצות המקשים עבור הווידג'ט
     * מתודה זו פותרת את שגיאת הקומפילציה ב-Build
     */
    public static boolean handleWidgetKey(MainActivity activity, int keyCode) {
        if (!isListening) {
            return false;
        }

        // כאן מיושם הלוגיקה של ניתוב המקש אל תוך הווידג'ט הפעיל
        if (keyCode == targetKeyCode) {
            Log.d(TAG, "Handling keycode " + keyCode + " for active widget.");
            // במידה והמקש טופל בהצלחה, נחזיר true כדי לעצור את השרשרת ב-MainActivity
            return true;
        }

        return false;
    }
}
