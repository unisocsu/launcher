package com.example.keylauncher;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public class HiddenAppsManager {

    private static final String PREF_NAME = "HiddenAppsPrefs";
    private static final String KEY_HIDDEN_PACKAGES = "hidden_packages";

    public static Set<String> getHiddenPackages(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return new HashSet<>(prefs.getStringSet(KEY_HIDDEN_PACKAGES, new HashSet<>()));
    }

    public static void setAppHidden(Context context, String packageName, boolean isHidden) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> hiddenApps = getHiddenPackages(context);

        if (isHidden) {
            hiddenApps.add(packageName);
        } else {
            hiddenApps.remove(packageName);
        }

        prefs.edit().putStringSet(KEY_HIDDEN_PACKAGES, hiddenApps).apply();
    }

    public static boolean isAppHidden(Context context, String packageName) {
        return getHiddenPackages(context).contains(packageName);
    }
}
