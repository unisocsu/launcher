package com.example.keylauncher;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

public class WidgetKeyController {
    
    private static final String TAG = "WidgetKeyController";

    /**
     * פונקציה מרכזית לבדיקת לחיצות מקשים עבור פקודות ווידג'ט מעוקפות.
     * @return true אם המקש טופל ואין להמשיך בלוגיקה הרגילה של הלאנצ'ר.
     */
    public static boolean handleWidgetKey(Context context, int keyCode) {
        
        // כאן הגדרנו את מקש 5 כדוגמה (ניתן לשנות לכל מקש שתרצה, למשל KEYCODE_POUND ל-#)
        if (keyCode == KeyEvent.KEYCODE_5) {
            
            // הדפסה ללוג כדי שנוכל לאשר שהלחיצה הגיעה לכאן בזמן אמת
            Log.d(TAG, "Key 5 pressed! Intercepting for widget action debug...");
            
            try {
                // TODO: אחרי שתבדוק בלוגים מה קורה בלחיצה על הווידג'ט, נחליף את ה-Intent הזה ב-Intent האמיתי!
                Intent debugIntent = new Intent();
                
                // כרגע נשלח פקודת מדיה גלובלית (Play/Pause) כדי שתוכל לבדוק אם המנגנון מגיב
                debugIntent.setAction(Intent.ACTION_MEDIA_BUTTON);
                debugIntent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                
                context.sendBroadcast(debugIntent);
                
                Toast.makeText(context, "נשלחה פקודת בדיקה של הווידג'ט!", Toast.LENGTH_SHORT).show();
                return true; // תפסנו את הלחיצה בהצלחה, מונע מהלאנצ'ר לפתוח קיצורי דרך אחרים של מקש 5
                
            } catch (Exception e) {
                Log.e(TAG, "Error broadcasting simulated intent", e);
                Toast.makeText(context, "שגיאה בשילוח פקודת הבדיקה", Toast.LENGTH_SHORT).show();
            }
        }
        
        return false; // המקש לא שייך לווידג'ט, המערכת תמשיך כרגיל
    }
}
