package com.fuck.bili.ads.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fuck.bili.ads.core.Host;

/**
 * Runtime plumbing shared by every feature: application context, hook status,
 * foreground activity/window, and the "module loaded" announcement.
 *
 * <p>Context resolution goes through several paths because the module runs on
 * different loaders (LSPosed, LSPatch, ...) where the helper class is
 * not always available.
 */
public final class FbaRuntime {

    public static final String TAG = "FuckBiliAds";

    private static final Map<String, Boolean> STATUS = new LinkedHashMap<>();

    private static volatile Handler mainHandler;
    private static Context application;
    private static boolean announced;

    private FbaRuntime() {
    }

    public static void init(Context context) {
        if (context != null && application == null) {
            application = context.getApplicationContext();
        }
    }

    /**
     * Runs {@code task} once the Application is available.
     *
     * <p>Preferences are unreadable during {@code onPackageLoaded}, so any hook
     * whose installation depends on a user switch has to wait: reading them too
     * early silently falls back to the defaults.
     */
    public static void whenReady(final Runnable task) {
        Handler handler = handler();
        if (handler == null) {
            task.run();
            return;
        }
        handler.post(new Runnable() {
            private int attempts;

            @Override
            public void run() {
                Context ctx = context();
                if (ctx == null && attempts++ < 25) {
                    Handler again = handler();
                    if (again != null) {
                        again.postDelayed(this, 200L);
                    } else {
                        task.run();
                    }
                    return;
                }
                if (ctx != null) {
                    FbaPrefs.init(ctx);
                }
                task.run();
            }
        });
    }

    /** Best-effort Application instance; may return null very early. */
    public static Context context() {
        if (application != null) {
            return application;
        }
        try {
            Context ctx = Host.currentApplication();
            if (ctx != null) {
                application = ctx.getApplicationContext();
                return application;
            }
        } catch (Throwable ignore) {
            // not available on this loader
        }
        try {
            Class<?> thread = Class.forName("android.app.ActivityThread");
            Method current = thread.getDeclaredMethod("currentApplication");
            current.setAccessible(true);
            Object ctx = current.invoke(null);
            if (ctx instanceof Context) {
                application = ((Context) ctx).getApplicationContext();
            }
        } catch (Throwable ignore) {
            // still too early
        }
        return application;
    }

    public static String processName() {
        try {
            Class<?> thread = Class.forName("android.app.ActivityThread");
            Method current = thread.getDeclaredMethod("currentProcessName");
            current.setAccessible(true);
            Object name = current.invoke(null);
            return name == null ? "" : String.valueOf(name);
        } catch (Throwable ignore) {
            return "";
        }
    }

    // ------------------------------------------------------------- hook status

    public static void mark(String feature, boolean ok) {
        synchronized (STATUS) {
            STATUS.put(feature, ok);
        }
        FbaLog.i((ok ? "[HOOK OK] " : "[HOOK FAIL] ") + feature);
    }

    public static boolean ok(String feature) {
        synchronized (STATUS) {
            Boolean value = STATUS.get(feature);
            return value != null && value;
        }
    }

    public static String summary() {
        StringBuilder sb = new StringBuilder();
        synchronized (STATUS) {
            for (Map.Entry<String, Boolean> entry : STATUS.entrySet()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(entry.getKey()).append(entry.getValue() ? "✓" : "✗");
            }
        }
        return sb.length() == 0 ? "未执行" : sb.toString();
    }

    public static boolean allOk() {
        synchronized (STATUS) {
            for (Boolean value : STATUS.values()) {
                if (!value) {
                    return false;
                }
            }
            return !STATUS.isEmpty();
        }
    }

    // ------------------------------------------------------------- announcement

    /**
     * Announces load status with a toast once per process (main process only).
     */
    public static void announce(final long delayMs) {
        try {
            Handler handler = handler();
            if (handler == null) {
                FbaLog.w("[BOOT] no main looper, cannot announce");
                return;
            }
            handler.postDelayed(new Runnable() {
                private int attempts;

                @Override
                public void run() {
                    Context ctx = context();
                    Handler self = handler();
                    if (ctx == null && attempts++ < 20 && self != null) {
                        self.postDelayed(this, 500L);
                        return;
                    }
                    if (ctx == null) {
                        FbaLog.w("[BOOT] no context, cannot announce");
                        return;
                    }
                    String process = processName();
                    String pkg = ctx.getPackageName();
                    boolean mainProcess = process.isEmpty() || process.equals(pkg);
                    FbaLog.i("[BOOT] process=" + process + " main=" + mainProcess
                            + " status=" + summary());
                    if (!mainProcess || announced) {
                        return;
                    }
                    announced = true;
                    toast(ctx, "FuckBiliAds 已加载 · " + summary());
                }
            }, delayMs);
        } catch (Throwable t) {
            FbaLog.e("announce failed", t);
        }
    }

    public static void toastNow(String text) {
        Context ctx = context();
        if (ctx != null) {
            toast(ctx, text);
            FbaLog.i("[TOAST] " + text);
        }
    }

    private static void toast(final Context context, final String text) {
        try {
            Runnable show = () -> {
                try {
                    Toast.makeText(context, text, Toast.LENGTH_LONG).show();
                } catch (Throwable t) {
                    Log.d(TAG, "toast failed: " + t);
                }
            };
            Handler handler = handler();
            if (handler == null || Looper.myLooper() == Looper.getMainLooper()) {
                show.run();
            } else {
                handler.post(show);
            }
        } catch (Throwable ignore) {
            // ignore
        }
    }

    // --------------------------------------------------------- top window/activity

    /** Foreground activity resolved through ActivityThread (works without hooks). */
    public static Activity topActivity() {
        try {
            Class<?> thread = Class.forName("android.app.ActivityThread");
            Method current = thread.getDeclaredMethod("currentActivityThread");
            current.setAccessible(true);
            Object instance = current.invoke(null);
            if (instance == null) {
                return null;
            }
            Field field = thread.getDeclaredField("mActivities");
            field.setAccessible(true);
            Object value = field.get(instance);
            if (!(value instanceof Map)) {
                return null;
            }
            Activity fallback = null;
            for (Object record : ((Map<?, ?>) value).values()) {
                Object activity = member(record, "activity");
                if (!(activity instanceof Activity)) {
                    continue;
                }
                Activity candidate = (Activity) activity;
                if (candidate.isFinishing()) {
                    continue;
                }
                Object paused = member(record, "paused");
                if (Boolean.FALSE.equals(paused)) {
                    return candidate;
                }
                if (fallback == null) {
                    fallback = candidate;
                }
            }
            return fallback;
        } catch (Throwable ignore) {
            return null;
        }
    }

    /** Topmost window root (dialog windows included), used for overlay placement. */
    public static ViewGroup topWindow() {
        try {
            Class<?> global = Class.forName("android.view.WindowManagerGlobal");
            Method getInstance = global.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            Object instance = getInstance.invoke(null);
            Field viewsField = global.getDeclaredField("mViews");
            viewsField.setAccessible(true);
            Object value = viewsField.get(instance);
            if (value instanceof List) {
                List<?> views = (List<?>) value;
                for (int i = views.size() - 1; i >= 0; i--) {
                    Object view = views.get(i);
                    if (view instanceof ViewGroup && ((View) view).isShown()) {
                        return (ViewGroup) view;
                    }
                }
            }
        } catch (Throwable ignore) {
            // fall through
        }
        Activity activity = topActivity();
        if (activity != null) {
            try {
                return (ViewGroup) activity.getWindow().getDecorView();
            } catch (Throwable ignore) {
                // ignore
            }
        }
        return null;
    }

    private static Object member(Object target, String name) {
        if (target == null) {
            return null;
        }
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (Throwable ignore) {
                // next
            }
        }
        return null;
    }

    /** Runs {@code action} on the main thread, now or soon. */
    public static void onMain(Runnable action) {
        Handler handler = handler();
        if (handler == null || Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            handler.post(action);
        }
    }

    public static Handler handler() {
        if (mainHandler == null) {
            try {
                Looper looper = Looper.getMainLooper();
                if (looper != null) {
                    mainHandler = new Handler(looper);
                }
            } catch (Throwable ignore) {
                // no looper yet
            }
        }
        return mainHandler;
    }
}
