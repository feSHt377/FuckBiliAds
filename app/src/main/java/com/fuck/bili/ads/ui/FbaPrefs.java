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
    private static final String KEY_STORY_FILTER = "story_filter";
    private static final String KEY_STORY_FILTER_TOAST = "story_filter_toast";
    private static final String KEY_VERBOSE = "verbose";
    private static final String KEY_SHARE_MARK = "share_mark";
    private static final String KEY_SPLASH_THEME = "splash_theme";
    private static final String KEY_BRAND_SPLASH_BLOCK = "brand_splash_block";

    // ---- update checker ----
    private static final String KEY_UPDATE_AUTO = "update_auto";
    private static final String KEY_UPDATE_PRERELEASE = "update_prerelease";
    private static final String KEY_UPDATE_CHECKED_AT = "update_checked_at";
    private static final String KEY_UPDATE_DISMISSED = "update_dismissed";
    private static final String KEY_UPDATE_LATEST = "update_latest";
    private static final String KEY_UPDATE_AVAILABLE = "update_available";
    private static final String KEY_UPDATE_ERROR = "update_error";

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

    /** Drop ad cards from the playlist on arrival instead of scrolling past them. */
    public static boolean storyFilter() {
        return getBoolean(KEY_STORY_FILTER, true);
    }

    public static void setStoryFilter(boolean value) {
        putBoolean(KEY_STORY_FILTER, value);
    }

    /** Toast how many ad cards were dropped from the playlist. */
    public static boolean storyFilterToast() {
        return getBoolean(KEY_STORY_FILTER_TOAST, true);
    }

    public static void setStoryFilterToast(boolean value) {
        putBoolean(KEY_STORY_FILTER_TOAST, value);
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

    // ------------------------------------------------------------ splash theme

    /** Startup page palette: "auto" (follow system), "light" or "dark". */
    public static String splashTheme() {
        return getString(KEY_SPLASH_THEME, "auto");
    }

    public static void setSplashTheme(String mode) {
        putString(KEY_SPLASH_THEME, mode == null ? "auto" : mode);
    }

    /** 是否隐藏品牌开屏（本地立绘）。默认关闭：很多用户想看个性化开屏动画。 */
    public static boolean brandSplashBlock() {
        return getBoolean(KEY_BRAND_SPLASH_BLOCK, false);
    }

    public static void setBrandSplashBlock(boolean value) {
        putBoolean(KEY_BRAND_SPLASH_BLOCK, value);
    }
    /** Resolved night flag for the startup page. */
    public static boolean splashNight() {
        String mode = splashTheme();
        if ("dark".equals(mode)) {
            return true;
        }
        if ("light".equals(mode)) {
            return false;
        }
        int uiMode = android.content.res.Resources.getSystem().getConfiguration().uiMode;
        return (uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    // ------------------------------------------------------------- update check

    /** Query GitHub for a newer release shortly after the host starts. */
    public static boolean updateAuto() {
        return getBoolean(KEY_UPDATE_AUTO, true);
    }

    public static void setUpdateAuto(boolean value) {
        putBoolean(KEY_UPDATE_AUTO, value);
    }

    /** Include pre-releases in the comparison. */
    public static boolean updatePrerelease() {
        return getBoolean(KEY_UPDATE_PRERELEASE, true);
    }

    public static void setUpdatePrerelease(boolean value) {
        putBoolean(KEY_UPDATE_PRERELEASE, value);
    }

    public static long updateCheckedAt() {
        return getLong(KEY_UPDATE_CHECKED_AT, 0L);
    }

    public static void setUpdateCheckedAt(long value) {
        putLong(KEY_UPDATE_CHECKED_AT, value);
    }

    /** Tag the user asked to skip; it stays reachable from the release page. */
    public static String updateDismissed() {
        return getString(KEY_UPDATE_DISMISSED, "");
    }

    public static void setUpdateDismissed(String tag) {
        putString(KEY_UPDATE_DISMISSED, tag == null ? "" : tag);
    }

    /** Newest release seen by the last successful check, as JSON. */
    public static String updateLatest() {
        return getString(KEY_UPDATE_LATEST, "");
    }

    public static void setUpdateLatest(String json) {
        putString(KEY_UPDATE_LATEST, json);
    }

    /** Release that was newer than this build at the last check, as JSON. */
    public static String updateAvailable() {
        return getString(KEY_UPDATE_AVAILABLE, "");
    }

    public static void setUpdateAvailable(String json) {
        putString(KEY_UPDATE_AVAILABLE, json);
    }

    public static String updateError() {
        return getString(KEY_UPDATE_ERROR, "");
    }

    public static void setUpdateError(String message) {
        putString(KEY_UPDATE_ERROR, message == null ? "" : message);
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

    private static long getLong(String key, long def) {
        try {
            SharedPreferences sp = prefs();
            return sp == null ? def : sp.getLong(key, def);
        } catch (Throwable t) {
            Log.d(TAG, "prefs read failed: " + t);
            return def;
        }
    }

    private static void putLong(String key, long value) {
        try {
            SharedPreferences sp = prefs();
            if (sp != null) {
                sp.edit().putLong(key, value).apply();
            }
        } catch (Throwable t) {
            Log.d(TAG, "prefs write failed: " + t);
        }
    }

    private static String getString(String key, String def) {
        try {
            SharedPreferences sp = prefs();
            String value = sp == null ? null : sp.getString(key, def);
            return value == null ? def : value;
        } catch (Throwable t) {
            Log.d(TAG, "prefs read failed: " + t);
            return def;
        }
    }

    private static void putString(String key, String value) {
        try {
            SharedPreferences sp = prefs();
            if (sp != null) {
                sp.edit().putString(key, value).apply();
            }
        } catch (Throwable t) {
            Log.d(TAG, "prefs write failed: " + t);
        }
    }
}
