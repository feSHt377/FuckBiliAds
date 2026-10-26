package com.fuck.bili.ads.story;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;

import java.util.HashSet;
import java.util.Set;

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

    /** Toast shown after a successful skip. */
    static final String TOAST_TEXT = "检测到广告，已跳过！FUCK BILI ADS！";

    private static final String STORY_ACTIVITY = "com.bilibili.video.story.StoryVideoActivity";

    private static final long POLL_MS = 400L;
    private static final long COOLDOWN_MS = 700L;
    private static final long CONFIRM_DELAY_MS = 300L;
    private static final long VERIFY_DELAY_MS = 900L;

    /** Hard stop for pathological pages: at most N skips inside a time window. */
    private static final int MAX_SKIPS_PER_WINDOW = 12;
    private static final long WINDOW_MS = 15_000L;

    private static StoryAdSniper.Host current;

    private StoryAdSniper() {
    }

    public static void hook(ClassLoader classLoader) {
        final Class<?> storyActivity;
        try {
            storyActivity = XposedHelpers.findClass(STORY_ACTIVITY, classLoader);
        } catch (Throwable t) {
            Log.d(TAG, "story page class not found, auto skip disabled");
            return;
        }

        hookLifecycle(Activity.class, "performResume", storyActivity, true);
        hookLifecycle(Activity.class, "performPause", storyActivity, false);
        hookLifecycle(Activity.class, "performDestroy", storyActivity, false);
        Log.d(TAG, "story ad auto skip ready");
    }

    private static void hookLifecycle(Class<?> hookClass, String method,
                                      final Class<?> storyActivity, final boolean resume) {
        try {
            XposedBridge.hookAllMethods(hookClass, method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object self = param.thisObject;
                        if (!(self instanceof Activity) || !storyActivity.isInstance(self)) {
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

    private static synchronized void attach(Activity activity) {
        if (current != null && current.activity == activity) {
            current.start();
            return;
        }
        detach(null);
        current = new Host(activity);
        current.start();
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
        final Set<String> skipped = new HashSet<>();

        boolean running;
        String pendingId;
        int pendingPos = -1;
        long lastSkipAt;
        long windowStart;
        int windowCount;

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
            ViewGroup rv = StoryView.findRecycler(activity);
            if (rv == null) {
                return;
            }
            int pos = StoryView.dominantPos(rv);
            if (pos < 0) {
                return;
            }
            Object item = StoryView.findItem(StoryView.adapterOf(rv), pos);
            if (!isAd(item)) {
                pendingId = null;
                return;
            }
            String id = StoryView.identityOf(item, pos);
            if (id.equals(pendingId) || skipped.contains(id)) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastSkipAt < COOLDOWN_MS) {
                return;
            }
            if (!allowAnotherSkip(now)) {
                return;
            }
            pendingId = id;
            pendingPos = pos;
            skip(id, pos);
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
            lastSkipAt = System.currentTimeMillis();
            skipped.add(id);
            if (skipped.size() > 400) {
                skipped.clear();
                skipped.add(id);
            }

            HostToast.show(activity, TOAST_TEXT);

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!running) {
                        return;
                    }
                    ViewGroup rv = StoryView.findRecycler(activity);
                    if (rv == null || StoryView.dominantPos(rv) != pos) {
                        // The user already moved on: do not fight the gesture.
                        return;
                    }
                    if (!StoryNext.toNext(rv, pos)) {
                        skipped.remove(id);
                        return;
                    }
                    verifyLater(pos);
                }
            }, CONFIRM_DELAY_MS);
        }

        private void verifyLater(final int fromPos) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!running) {
                        return;
                    }
                    ViewGroup rv = StoryView.findRecycler(activity);
                    if (rv == null || StoryView.dominantPos(rv) > fromPos) {
                        return;
                    }
                    // One silent retry with the fallback scroll APIs.
                    StoryNext.toNext(rv, fromPos);
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
