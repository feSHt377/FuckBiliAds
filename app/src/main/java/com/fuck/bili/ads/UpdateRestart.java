package com.fuck.bili.ads;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.fuck.bili.ads.core.Host;
import com.fuck.bili.ads.ui.FbaLog;
import com.fuck.bili.ads.ui.FbaPrefs;

/**
 * Restarts the host task once after the module itself was updated.
 *
 * <p>A freshly updated module is loaded into a host process that may still hold
 * state produced by the previous version (cached hook list, DexKit results), and
 * the first session after an update occasionally filters ads incorrectly. A
 * clean task restart removes that window; the user is told why before it happens.
 *
 * <p>The host cannot simply kill itself and schedule a new activity here:
 * Android 12+ blocks the relaunch as a background activity start. Rebuilding
 * the current task is the supported equivalent on supported host versions.
 */
final class UpdateRestart {

    /** How long the prompt stays visible before the process is restarted. */
    private static final long PROMPT_MS = 4000L;

    private UpdateRestart() {
    }

    static void check(final Context context) {
        if (context == null) {
            return;
        }
        final int current = BuildConfig.VERSION_CODE;
        final int last = FbaPrefs.lastModuleCode();
        if (last == current) {
            return;
        }
        // No record means either a fresh install or the first launch after an
        // upgrade from a version that did not write the key; both are worth one
        // clean restart, and the key is written first so it happens only once.
        // Persist before restarting so a crash can never cause a restart loop.
        FbaPrefs.setLastModuleCode(current);
        FbaLog.i("[RESTART] module " + (last == 0 ? "first run" : last + " -> " + current) + ", restarting host");

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> Toast.makeText(context, "模块已更新，需要重启哔哩哔哩",
                Toast.LENGTH_LONG).show());
        handler.postDelayed(() -> restart(context), PROMPT_MS);
    }

    /**
     * Rebuilds the current task instead of killing the process: Android 12+
     * rejects a relaunch started from a background/short-lived process, and a
     * foreground task rebuild gives the user the same clean reload.
     */
    private static void restart(Context context) {
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(Host.packageName());
            if (intent == null) {
                FbaLog.w("[RESTART] launcher intent unavailable");
                return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);
            FbaLog.i("[RESTART] host task restarted");
        } catch (Throwable t) {
            FbaLog.w("[RESTART] restart failed: " + t);
        }
    }
}