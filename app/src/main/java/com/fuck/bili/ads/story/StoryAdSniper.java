package com.fuck.bili.ads.story;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;
import com.fuck.bili.ads.ui.FbaLog;
import com.fuck.bili.ads.ui.FbaPrefs;
import com.fuck.bili.ads.ui.FbaRuntime;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Skips ads inside the vertical short-video page (故事模式 / 小视频).
 *
 * <p>Detection: the card currently covering most of the screen owns an
 * {@code adInfo} whose {@code feedIsAd} flag is {@code true}. That is the only
 * signal that proved reliable during probing — {@code adType} is always -1 and
 * {@code natureAd} flips randomly.
 *
 * <p>The page is polled instead of hooking a "current item changed" callback:
 * the list is a snap RecyclerView, and the visible card is the only thing that
 * matters. Every operation is read-only except the scroll to the next card.
 *
 * <p>Session start prefers hooks on the host's own activity class (works on
 * every loader, including LSPatch) and only then falls back to framework
 * lifecycle hooks.
 */
public final class StoryAdSniper {

    private static final String TAG = "FuckBiliAds";

    /** Toast shown right after a successful skip. */
    static final String TOAST_TEXT = "检测到广告，已跳过！FUCK BILI ADS！";

    private static final String STORY_ACTIVITY = "com.bilibili.video.story.StoryVideoActivity";
    private static final String STORY_PKG_PREFIX = "com.bilibili.video.story.";
    private static final String RV_CLASS = "androidx.recyclerview.widget.RecyclerView";

    private static final long POLL_MS = 400L;
    private static final long COOLDOWN_MS = 700L;
    private static final long CONFIRM_DELAY_MS = 300L;
    private static final long VERIFY_DELAY_MS = 900L;

    /** The same card may be skipped again when the user scrolls back much later. */
    private static final long RESKIP_AFTER_MS = 120_000L;

    /** Hard stop for pathological pages: at most N skips inside a time window. */
    private static final int MAX_SKIPS_PER_WINDOW = 12;
    private static final long WINDOW_MS = 15_000L;

    /** Story page class resolved from the host; null means "match by name". */
    private static Class<?> storyActivity;

    /** Host class loader, kept so the playlist filter can hook its batch methods. */
    static volatile ClassLoader hostLoader;

    private static volatile boolean ready;
    private static volatile Host current;

    private StoryAdSniper() {
    }

    public static void hook(ClassLoader classLoader) {
        Class<?> resolved = null;
        try {
            resolved = Reflect.findClass(STORY_ACTIVITY, classLoader);
        } catch (Throwable t) {
            // Host renamed the page: keep working through the package prefix.
            FbaLog.w("story: page class missing, matching by name");
        }
        storyActivity = resolved;
        hostLoader = classLoader;

        boolean filter = StoryAdFilter.install(classLoader);
        FbaRuntime.mark("剔除", filter);

        boolean appLifecycle = hookAppLifecycle(classLoader);
        boolean frameworkLifecycle = hookFrameworkLifecycle();
        boolean attach = hookRecyclerAttach(classLoader);
        ready = appLifecycle || frameworkLifecycle || attach;

        startWatchdog();
        FbaRuntime.mark("小视频", ready);
        FbaLog.i("story hooks: app=" + appLifecycle + " framework=" + frameworkLifecycle
                + " attach=" + attach + " filter=" + filter + " page="
                + (resolved == null ? "name-match" : resolved.getName()));
    }

    /**
     * Loader-independent safety net: watches the foreground activity every two
     * seconds, so a session starts even when no lifecycle hook fires (LSPatch,
     * fragment-hosted pages, ...).
     */
    private static void startWatchdog() {
        final android.os.Handler handler = FbaRuntime.handler();
        if (handler == null) {
            FbaLog.w("story: watchdog disabled (no main looper)");
            return;
        }
        final Runnable[] holder = new Runnable[1];
        holder[0] = new Runnable() {
            @Override
            public void run() {
                try {
                    Activity top = FbaRuntime.topActivity();
                    if (top != null && !top.isFinishing()) {
                        if (isStoryActivity(top)) {
                            attach(top);
                        } else if (current != null && current.activity != top) {
                            detach(null);
                        }
                    }
                } catch (Throwable ignore) {
                    // keep watching
                }
                handler.postDelayed(holder[0], 2000L);
            }
        };
        handler.postDelayed(holder[0], 2000L);
    }

    /** True once at least one trigger is installed (shown in the settings panel). */
    public static boolean isReady() {
        return ready;
    }

    /** Activity of the running story session, used by the share-sheet marker. */
    public static Activity currentActivity() {
        Host host = current;
        return host == null ? null : host.activity;
    }

    // ------------------------------------------------------------- hook triggers

    /**
     * Hooks the host's own activity class. Resolves inherited methods, so it
     * still lands on app code (and stays hookable under LSPatch).
     */
    private static boolean hookAppLifecycle(ClassLoader classLoader) {
        Class<?> page = storyActivity;
        if (page == null) {
            try {
                page = Reflect.findClass(STORY_ACTIVITY, classLoader);
            } catch (Throwable t) {
                return false;
            }
        }
        int installed = 0;
        installed += hookApp(page, "onCreate", new Class<?>[]{Bundle.class}, true);
        installed += hookApp(page, "onResume", new Class<?>[0], true);
        installed += hookApp(page, "onPause", new Class<?>[0], false);
        installed += hookApp(page, "onDestroy", new Class<?>[0], false);
        return installed > 0;
    }

    private static int hookApp(Class<?> page, String method, Class<?>[] params, final boolean resume) {
        try {
            Method target = params.length == 0
                    ? NativeHooks.method(page, method)
                    : NativeHooks.method(page, method, params);
            NativeHooks.hook("StoryAdSniper." + method, target, chain -> {
                Object result = chain.proceed();
                try {
                    if (!(chain.getThisObject() instanceof Activity)) {
                        return result;
                    }
                    Activity activity = (Activity) chain.getThisObject();
                    if (!isStoryActivity(activity)) {
                        return result;
                    }
                    if (resume) {
                        attach(activity);
                    } else {
                        detach(activity);
                    }
                } catch (Throwable t) {
                    FbaLog.d("story lifecycle callback failed: " + t);
                }
                return result;
            });
            FbaLog.d("story: hooked " + page.getName() + "." + method);
            return 1;
        } catch (Throwable t) {
            FbaLog.d("story: hook " + method + " failed: " + t);
            return 0;
        }
    }

    private static boolean hookFrameworkLifecycle() {
        boolean any = false;
        any |= hookFramework("performResume", true);
        any |= hookFramework("onResume", true);
        any |= hookFramework("performPause", false);
        any |= hookFramework("onPause", false);
        any |= hookFramework("performDestroy", false);
        return any;
    }

    private static boolean hookFramework(String method, final boolean resume) {
        try {
            int hooks = NativeHooks.hookAllMethods("StoryAdSniper.Framework." + method,
                    Activity.class, method, chain -> {
                Object result = chain.proceed();
                try {
                    Object self = chain.getThisObject();
                    if (self instanceof Activity && isStoryActivity((Activity) self)) {
                        Activity activity = (Activity) self;
                        if (resume) {
                            attach(activity);
                        } else {
                            detach(activity);
                        }
                    }
                } catch (Throwable t) {
                    FbaLog.d("story framework callback failed: " + t);
                }
                return result;
            });
            return hooks > 0;
        } catch (Throwable t) {
            FbaLog.d("story: framework hook " + method + " failed: " + t);
            return false;
        }
    }

    /**
     * Fallback trigger: the story list attaching to the window. Used when the
     * page is hosted without a fresh activity resume.
     */
    private static boolean hookRecyclerAttach(ClassLoader classLoader) {
        try {
            final Class<?> recycler = Reflect.findClass(RV_CLASS, classLoader);
            // Only hook the override declared by RecyclerView itself, never
            // View.onAttachedToWindow (that would fire for every view).
            boolean any = false;
            for (Method m : recycler.getDeclaredMethods()) {
                if (!"onAttachedToWindow".equals(m.getName())) {
                    continue;
                }
                NativeHooks.hook("StoryAdSniper.RecyclerAttach", m, chain -> {
                    Object result = chain.proceed();
                    try {
                        Object self = chain.getThisObject();
                        if (self instanceof ViewGroup) {
                            Activity activity = activityOf(((View) self).getContext());
                            if (activity != null && isStoryActivity(activity)) {
                                attach(activity);
                            }
                        }
                    } catch (Throwable t) {
                        FbaLog.d("story recycler attach callback failed: " + t);
                    }
                    return result;
                });
                any = true;
            }
            return any;
        } catch (Throwable t) {
            FbaLog.e("story: recycler attach hook failed", t);
            return false;
        }
    }

    private static boolean isStoryActivity(Activity activity) {
        if (activity == null) {
            return false;
        }
        if (storyActivity != null && storyActivity.isInstance(activity)) {
            return true;
        }
        String name = activity.getClass().getName();
        return name.startsWith(STORY_PKG_PREFIX) || name.contains("StoryVideo");
    }

    private static Activity activityOf(Context context) {
        Context c = context;
        for (int i = 0; i < 6 && c != null; i++) {
            if (c instanceof Activity) {
                return (Activity) c;
            }
            if (!(c instanceof ContextWrapper)) {
                return null;
            }
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private static synchronized void attach(Activity activity) {
        Host host = current;
        if (host != null && host.activity == activity) {
            host.start();
            return;
        }
        detach(null);
        StoryAdFilter.install(hostLoader);
        current = new Host(activity);
        current.start();
        FbaLog.i("story session start: " + activity.getClass().getName());
    }

    private static synchronized void detach(Activity activity) {
        Host host = current;
        if (host == null) {
            return;
        }
        if (activity != null && host.activity != activity) {
            return;
        }
        host.stop();
        StoryAdFilter.forget(host.activity);
        current = null;
    }

    // ------------------------------------------------------------------ session

    /** One story page session. */
    private static final class Host implements Runnable {

        final Activity activity;
        final Handler handler = new Handler(Looper.getMainLooper());

        /** card identity -> last skip timestamp. */
        final Map<String, Long> skippedAt = new HashMap<>();

        boolean running;
        String pendingId;
        long lastSkipAt;
        long windowStart;
        int windowCount;

        /** Throttle for pruning ad cards that are already in the playlist. */
        long lastPruneAt;

        /** Diagnostics. */
        long lastMissingLogAt;
        long lastNotAdLogAt;
        String lastDetectedId;
        String lastDiag;
        long lastHeartbeatAt;
        boolean itemMissLogged;

        Host(Activity activity) {
            this.activity = activity;
        }

        void start() {
            if (running) {
                return;
            }
            running = true;
            handler.removeCallbacks(this);
            handler.postDelayed(this, 600L);
        }

        void stop() {
            running = false;
            handler.removeCallbacks(this);
        }

        @Override
        public void run() {
            if (!running) {
                return;
            }
            try {
                scan();
            } catch (Throwable t) {
                FbaLog.e("story scan failed", t);
            }
            if (running) {
                handler.postDelayed(this, POLL_MS);
            }
        }

        private void scan() {
            if (!FbaPrefs.storySkip()) {
                StoryDiag.update("自动跳过已关闭");
                return;
            }
            ViewGroup rv = StoryView.findRecycler(activity);
            if (rv == null) {
                StoryDiag.update("未找到小视频列表（页面结构未匹配）");
                reportMissingRecycler();
                return;
            }
            View child = StoryView.dominantChild(rv);
            int pos = StoryView.adapterPosition(rv, child);
            Object item = StoryView.itemOf(rv, child);
            Object adapter = StoryView.adapterOf(rv);
            boolean ad = isAd(item);
            String diag = "rv=" + simple(rv) + " adapter=" + simple(adapter) + " pos=" + pos
                    + " item=" + simple(item) + " ad=" + ad + " " + adInfo(item);
            StoryDiag.update(diag);
            reportDiag(diag);
            if (pos < 0) {
                return;
            }
            if (FbaPrefs.storyFilter() && now() - lastPruneAt > 2000L) {
                lastPruneAt = now();
                StoryAdFilter.pruneAhead(activity, rv, pos);
            }
            if (item == null) {
                if (!itemMissLogged) {
                    itemMissLogged = true;
                    FbaLog.w("story: item not resolved, " + diag);
                    if (FbaPrefs.verbose()) {
                        HostToast.show(activity, "FBA 无法识别当前条目");
                    }
                }
                return;
            }
            long now = System.currentTimeMillis();
            if (!ad) {
                pendingId = null;
                reportNotAd(item, pos, now);
                return;
            }
            String id = StoryView.identityOf(item, pos);
            if (id.equals(pendingId)) {
                return;
            }
            Long seenAt = skippedAt.get(id);
            if (seenAt != null) {
                if (now - seenAt < RESKIP_AFTER_MS) {
                    return;
                }
                skippedAt.remove(id);
            }
            if (now - lastSkipAt < COOLDOWN_MS) {
                return;
            }
            if (!allowAnotherSkip(now)) {
                return;
            }
            if (!id.equals(lastDetectedId)) {
                lastDetectedId = id;
                FbaLog.i("story: ad detected at pos " + pos + " id=" + id + " | " + diag);
                if (FbaPrefs.verbose()) {
                    HostToast.show(activity, "FBA 检测到广告 pos=" + pos);
                }
            }
            pendingId = id;
            skip(id, pos);
        }

        private void reportDiag(String diag) {
            long now = System.currentTimeMillis();
            boolean changed = !diag.equals(lastDiag);
            if (changed || now - lastHeartbeatAt > 30_000L) {
                lastDiag = diag;
                lastHeartbeatAt = now;
                FbaLog.i("story scan " + diag);
            }
        }

        private String adInfo(Object item) {
            Object ad = StoryView.fieldValue(item, "adInfo");
            if (ad == null) {
                return "adInfo=null";
            }
            return "adInfo{feedIsAd=" + StoryView.fieldValue(ad, "feedIsAd")
                    + ",creativeId=" + StoryView.fieldValue(ad, "feedCreativeId") + "}";
        }

        private void reportMissingRecycler() {
            long now = System.currentTimeMillis();
            if (now - lastMissingLogAt < 10_000L) {
                return;
            }
            lastMissingLogAt = now;
            FbaLog.w("story: story list not found (activity="
                    + activity.getClass().getName() + ")");
        }

        private void reportNotAd(Object item, int pos, long now) {
            Object ad = StoryView.fieldValue(item, "adInfo");
            if (ad == null) {
                return; // ordinary card, nothing to report
            }
            if (now - lastNotAdLogAt < 5_000L) {
                return;
            }
            lastNotAdLogAt = now;
            FbaLog.w("story: card at " + pos + " has adInfo but feedIsAd="
                    + StoryView.fieldValue(ad, "feedIsAd"));
        }

        private boolean allowAnotherSkip(long now) {
            if (now - windowStart > WINDOW_MS) {
                windowStart = now;
                windowCount = 0;
            }
            if (windowCount >= MAX_SKIPS_PER_WINDOW) {
                return false;
            }
            windowCount++;
            return true;
        }

        private void skip(final String id, final int pos) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!running) {
                        return;
                    }
                    ViewGroup rv = StoryView.findRecycler(activity);
                    if (rv == null || StoryView.dominantPos(rv) != pos) {
                        // The user already moved on: do not fight the gesture.
                        pendingId = null;
                        return;
                    }
                    if (!StoryNext.toNext(rv, pos)) {
                        pendingId = null;
                        FbaLog.w("story: skip failed at pos " + pos);
                        return;
                    }
                    lastSkipAt = System.currentTimeMillis();
                    skippedAt.put(id, lastSkipAt);
                    if (skippedAt.size() > 400) {
                        skippedAt.clear();
                        skippedAt.put(id, lastSkipAt);
                    }
                    FbaLog.i("story ad skipped at pos " + pos + " id=" + id);
                    if (FbaPrefs.storyToast()) {
                        HostToast.show(activity, TOAST_TEXT);
                    }
                    verifyLater(pos, 0);
                }
            }, CONFIRM_DELAY_MS);
        }

        /**
         * Confirms the list actually advanced; a silent no-op scroll is retried
         * with the progressively more primitive scroll APIs (0 -> 1 -> 2).
         */
        private void verifyLater(final int fromPos, final int attempt) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!running || attempt > 2) {
                        return;
                    }
                    ViewGroup rv = StoryView.findRecycler(activity);
                    if (rv == null) {
                        return;
                    }
                    int now = StoryView.dominantPos(rv);
                    if (now >= 0 && now != fromPos) {
                        return; // the list moved, nothing to fix
                    }
                    FbaLog.w("story: skip did not advance, retry " + attempt + " at pos " + fromPos);
                    StoryNext.toNext(rv, fromPos, attempt == 0
                            ? StoryNext.MODE_DIRECT : StoryNext.MODE_BY_SCREEN);
                    verifyLater(fromPos, attempt + 1);
                }
            }, VERIFY_DELAY_MS);
        }

        private long now() {
            return System.currentTimeMillis();
        }

        private boolean isAd(Object item) {
            if (item == null) {
                return false;
            }
            Object ad = StoryView.fieldValue(item, "adInfo");
            return ad != null && Boolean.TRUE.equals(StoryView.fieldValue(ad, "feedIsAd"));
        }
    }

    private static String simple(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
