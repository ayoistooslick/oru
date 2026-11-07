package com.oru.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Light, dark, or whatever the phone is set to. Stored with the other small
 * preferences, applied before the first screen is drawn.
 */
public final class ThemeMode {

    public static final String SYSTEM = "system";
    public static final String LIGHT = "light";
    public static final String DARK = "dark";

    private static final String PREFS = "oru";
    private static final String KEY = "theme-mode";

    private ThemeMode() {
    }

    public static String get(Context context) {
        String stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, SYSTEM);
        return stored != null ? stored : SYSTEM;
    }

    public static void set(Context context, String mode) {
        SharedPreferences preferences =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        preferences.edit().putString(KEY, mode).apply();
        AppCompatDelegate.setDefaultNightMode(delegate(mode));
    }

    /** Called once by the application so the first screen already matches. */
    public static void apply(Context context) {
        AppCompatDelegate.setDefaultNightMode(delegate(get(context)));
    }

    public static int delegate(String mode) {
        if (LIGHT.equals(mode)) {
            return AppCompatDelegate.MODE_NIGHT_NO;
        }
        if (DARK.equals(mode)) {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }
}
