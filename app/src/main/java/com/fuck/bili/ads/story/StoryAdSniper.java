package com.fuck.bili.ads.story;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.fuck.bili.ads.ui.FbaLog;
import com.fuck.bili.ads.ui.FbaPrefs;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

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
 */
public final class StoryAdSniper {

    private static final String TAG = "FuckBiliAds";

    /** Toast shown right after a successful skip. */
    static final String TOAST_TEXT = "检测到广告，已跳过！FUCK BILI ADS！";

    private static final String STORY_ACTIVITY = "com.bilibili.video.story.StoryVideoActivity";
    private static final String STORY_PKG_PREFIX = "com.bilibili.video.story.";

    private static final long POLL_MS = 400L;
    private static final long COOLDOWN_MS = 700L;
    private static final long CONFIRM_DELAY_MS = 300L;
    private static final long VERIFY_DELAY_MS = 900L;

    /** The same card may be skipped again when the user scrolls back much later. */
    private static final long RESKIP_AFTER_MS = 120_000L;

    /** Hard stop for pathological pages: at most N skips inside a time window. */
    private static final int MAX_SKIPS_PER_WINDOW = 12;
    private static final long WINDOW_MS = 15_000L;

    /** Story page class resolved from the host; null means "match by package". */
    private static Class<?> storyActivity;

    private static volatile boolean ready;

    private static StoryAdSniper.Host current;

    private StoryAdSniper() {
    }

    public static void hook(ClassLoader classLoader) {
        Class<?> resolved = null;
        try {
            resolved = XposedHelpers.findClass(STORY_ACTIVITY, classLoader);
        } catch (Throwable t) {
            // Host renamed the page: keep working through the package prefix.
            Log.d(TAG, "story: page class missing, matching by package");
        }
        storyActivity = resolved;

        // performResume/performPause are the framework-side lifecycle entry points,
        // onResume/onPause cover hosts that drive the page through a fragment.
        hookActivityMethod("performResume", true);
        hookActivityMethod("onResume", true);
        hookActivityMethod("performPause", false);
        hookActivityMethod("onPause", false);
        hookActivityMethod("performDestroy", false);
        hookRecyclerAttach(classLoader);

        ready = true;
        FbaLog.i("story ad auto skip ready (page="
                + (resolved == null ? "package-match" : resolved.getName()) + ")");
    }

    /** True once the story hooks are installed (shown in the settings panel). */
    public static boolean isReady() {
        return ready;
    }

    /**
     * Fallback trigger: when the page is a fragment inside an already resumed
     * activity, no lifecycle callback fires again. The story list attaching to
     * the window is the point where a session can start.
     */
    private static void hookRecyclerAttach(ClassLoader classLoader) {
        try {
            final Class<?> recycler = XposedHelpers.findClass(
                    "androidx.recyclerview.widget.RecyclerView", classLoader);
            XC_MethodHook callback = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object self = param.thisObject;
                        if (!(self instanceof ViewGroup)) {
                            return;
                        }
                        // The hook may land on View.onAttachedToWindow; ignore
                        // everything that is not a RecyclerView.
                        String name = self.getClass().getName();
                        boolean isRecycler = name.equals("androidx.recyclerview.widget.RecyclerView");
                        for (Class<?> c = self.getClass(); !isRecycler && c != null
                                && c != Object.class; c = c.getSuperclass()) {
                            isRecycler = "androidx.recyclerview.widget.RecyclerView".equals(c.getName());
                        }
                        if (!isRecycler) {
                            return;
                        }
                        Object adapter = StoryView.adapterOf((ViewGroup) self);
                        if (adapter == null
                                || !adapter.getClass().getName().startsWith(STORY_PKG_PREFIX)) {
                            return;
                        }
                        Activity activity = activityOf(((View) self).getContext());
                        if (activity != null) {
                            attach(activity);
                        }
                    } catch (Throwable ignore) {
                        // never disturb the host
                    }
                }
            };
            // Only hook the override declared by RecyclerView itself, never
            // View.onAttachedToWindow (that would fire for every view).
            boolean any = false;
            for (Method m : recycler.getDeclaredMethods()) {
                if (!"onAttachedToWindow".equals(m.getName())) {
                    continue;
                }
                XposedBridge.hookMethod(m, callback);
                any = true;
            }
            if (!any) {
                Log.d(TAG, "story: RecyclerView.onAttachedToWindow not declared, fallback off");
            }
        } catch (Throwable t) {
            Log.d(TAG, "story: recycler attach hook skipped: " + t);
        }
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

    private static void hookActivityMethod(String method, final boolean resume) {
        try {
            XposedBridge.hookAllMethods(Activity.class, method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object self = param.thisObject;
                        if (!(self instanceof Activity) || !isStoryActivity((Activity) self)) {
                            return;
                        }
                        Activity activity = (Activity) self;
                        if (resume) {
                            attach(activity);
                        } else {
                            detach(activity);
                        }
                    } catch (Throwable ignore) {
                        // never disturb the host
                    }
                }
            });
        } catch (Throwable t) {
            Log.d(TAG, "hooking " + method + " failed: " + t);
        }
    }

    private static boolean isStoryActivity(Activity activity) {
        if (storyActivity != null && storyActivity.isInstance(activity)) {
            return true;
        }
        return activity.getClass().getName().startsWith(STORY_PKG_PREFIX);
    }

    private static synchronized void attach(Activity activity) {
        if (current != null && current.activity == activity) {
            current.start();
            return;
        }
        detach(null);
        current = new Host(activity);
        current.start();
        Log.d(TAG, "story session start: " + activity.getClass().getName());
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
        current = null;
    }

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

        /** Diagnostics: rate-limited so a stuck page cannot flood logcat. */
        long lastMissingLogAt;
        long lastNotAdLogAt;
        String lastDetectedId;
        int lastLogPos = -1;
        String lastLogItem;

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
            } catch (Throwable ignore) {
                // keep polling
            }
            if (running) {
                handler.postDelayed(this, POLL_MS);
            }
        }

        private void scan() {
            if (!FbaPrefs.storySkip()) {
                return;
            }
            ViewGroup rv = StoryView.findRecycler(activity);
            if (rv == null) {
                reportMissingRecycler();
                return;
            }
            View child = StoryView.dominantChild(rv);
            int pos = StoryView.adapterPosition(rv, child);
            if (pos < 0) {
                return;
            }
            Object item = StoryView.itemOf(rv, child);
            if (FbaPrefs.verbose()) {
                logScan(pos, item);
            }
            long now = System.currentTimeMillis();
            if (!isAd(item)) {
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
                FbaLog.i("story: ad detected at pos " + pos + " id=" + id
                        + " | " + StoryDebug.snapshot(activity));
            }
            pendingId = id;
            skip(id, pos);
        }

        /** Detailed log: one line per visible card change, only when enabled. */
        private void logScan(int pos, Object item) {
            String name = item == null ? "null" : item.getClass().getName();
            if (pos == lastLogPos && name.equals(lastLogItem)) {
                return;
            }
            lastLogPos = pos;
            lastLogItem = name;
            FbaLog.d("scan pos=" + pos + " item=" + name + " ad=" + isAd(item));
        }

        private void reportMissingRecycler() {
            long now = System.currentTimeMillis();
            if (now - lastMissingLogAt < 10_000L) {
                return;
            }
            lastMissingLogAt = now;
            Log.d(TAG, "story: story list not found (activity="
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
            Log.d(TAG, "story: card at " + pos + " has adInfo but feedIsAd="
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
                    // Only a scroll that was actually issued counts as "skipped",
                    // otherwise an aborted attempt would blacklist the card.
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
                    Log.d(TAG, "story: skip did not advance, retry " + attempt + " at pos " + fromPos);
                    StoryNext.toNext(rv, fromPos, attempt == 0
                            ? StoryNext.MODE_DIRECT : StoryNext.MODE_BY_SCREEN);
                    verifyLater(fromPos, attempt + 1);
                }
            }, VERIFY_DELAY_MS);
        }

        private boolean isAd(Object item) {
            if (item == null) {
                return false;
            }
            Object ad = StoryView.fieldValue(item, "adInfo");
            return ad != null && Boolean.TRUE.equals(StoryView.fieldValue(ad, "feedIsAd"));
        }
    }
}
