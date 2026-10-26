package com.fuck.bili.ads.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import android.app.AndroidAppHelper;

/**
 * Module preferences, stored in the host app's own data dir.
 *
 * <p>All UI runs inside the Bilibili process, so plain SharedPreferences is
 * enough — no cross-process file tricks.
 */
public final class FbaPrefs {

    private static final String TAG = "FuckBiliAds";
    private static final String NAME = "fuck_bili_ads";

    private static final String KEY_STORY_SKIP = "story_skip";
    private static final String KEY_STORY_TOAST = "story_toast";
    private static final String KEY_VERBOSE = "verbose";
    private static final String KEY_SHARE_MARK = "share_mark";

    private static Context context;

    private FbaPrefs() {
    }

    public static void init(Context ctx) {
        if (ctx != null) {
            context = ctx.getApplicationContext();
        }
    }

    private static SharedPreferences prefs() {
        Context ctx = context;
        if (ctx == null) {
            try {
                ctx = AndroidAppHelper.currentApplication();
                context = ctx;
            } catch (Throwable ignore) {
                // keep null
            }
        }
        return ctx == null ? null : ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static boolean storySkip() {
        return getBoolean(KEY_STORY_SKIP, true);
    }

    public static void setStorySkip(boolean value) {
        putBoolean(KEY_STORY_SKIP, value);
    }

    public static boolean storyToast() {
        return getBoolean(KEY_STORY_TOAST, true);
    }

    public static void setStoryToast(boolean value) {
        putBoolean(KEY_STORY_TOAST, value);
    }

    public static boolean verbose() {
        return getBoolean(KEY_VERBOSE, false);
    }

    public static void setVerbose(boolean value) {
        putBoolean(KEY_VERBOSE, value);
    }

    public static boolean shareMark() {
        return getBoolean(KEY_SHARE_MARK, true);
    }

    public static void setShareMark(boolean value) {
        putBoolean(KEY_SHARE_MARK, value);
    }

    private static boolean getBoolean(String key, boolean def) {
        try {
            SharedPreferences sp = prefs();
            return sp == null ? def : sp.getBoolean(key, def);
        } catch (Throwable t) {
            Log.d(TAG, "prefs read failed: " + t);
            return def;
        }
    }

    private static void putBoolean(String key, boolean value) {
        try {
            SharedPreferences sp = prefs();
            if (sp != null) {
                sp.edit().putBoolean(key, value).apply();
            }
        } catch (Throwable t) {
            Log.d(TAG, "prefs write failed: " + t);
        }
    }
}
