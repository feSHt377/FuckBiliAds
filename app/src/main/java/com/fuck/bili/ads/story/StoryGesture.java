package com.fuck.bili.ads.story;

import android.view.ViewGroup;

import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;
import com.fuck.bili.ads.ui.FbaLog;
import com.fuck.bili.ads.ui.FbaPrefs;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tracks whether the story list is being dragged or flung.
 *
 * <p>Every story action that touches the host list or drives the pager
 * ({@code pruneAhead}, {@code skip}, batch rewriting) must wait until the list
 * has been idle for {@link #SETTLE_MS}. Changing the adapter while a fling is in
 * flight cancels it, which the user perceives as a swallowed swipe.
 *
 * <p>State is maintained by a single hook on
 * {@code RecyclerView#onScrollStateChanged(int)}. When that hook cannot be
 * installed, the state is read lazily from the view being asked about.
 */
final class StoryGesture {

    static final int IDLE = 0;
    /** A list must stay idle this long before the module may modify it. */
    static final long SETTLE_MS = 300L;

    private static final Map<ViewGroup, Integer> STATES = new WeakHashMap<>();

    private static volatile boolean hooked;
    private static volatile int globalState = IDLE;
    private static volatile long lastScrollAt;
    private static volatile Listener listener;

    private StoryGesture() {
    }

    /** Notified on the main thread when a list settles back to idle. */
    interface Listener {
        void onScrollIdle();
    }

    static void install(ClassLoader loader) {
        if (hooked) {
            return;
        }
        try {
            Class<?> recycler = Reflect.findClass(
                    "androidx.recyclerview.widget.RecyclerView", loader);
            int count = NativeHooks.hookAllMethods("StoryGesture.Scroll", recycler,
                    "onScrollStateChanged", chain -> {
                        int state = ((Number) chain.getArg(0)).intValue();
                        Object self = chain.getThisObject();
                        note(self instanceof ViewGroup ? (ViewGroup) self : null, state);
                        return chain.proceed();
                    });
            hooked = count > 0;
            FbaLog.i("story gesture hook installed: " + count);
        } catch (Throwable t) {
            FbaLog.w("story gesture hook failed: " + t);
        }
    }

    static void setListener(Listener value) {
        listener = value;
    }

    private static void note(ViewGroup rv, int state) {
        if (rv != null) {
            synchronized (STATES) {
                STATES.put(rv, state);
            }
        }
        globalState = state;
        if (FbaPrefs.verbose()) {
            FbaLog.d("[GESTURE] scrollState=" + state);
        }
        if (state != IDLE) {
            lastScrollAt = System.currentTimeMillis();
            return;
        }
        Listener current = listener;
        if (current != null) {
            current.onScrollIdle();
        }
    }

    /** True while {@code rv} is being dragged or flung. */
    static boolean isScrolling(ViewGroup rv) {
        Integer state = null;
        if (rv != null) {
            synchronized (STATES) {
                state = STATES.get(rv);
            }
        }
        if (state == null) {
            state = readState(rv);
        }
        return state != null && state != IDLE;
    }

    /** True when {@code rv} is idle and has been for the settle window. */
    static boolean isSettled(ViewGroup rv) {
        return !shouldWait(stateOf(rv), lastScrollAt, System.currentTimeMillis());
    }

    /**
     * Guard for code paths that have no view at hand (batch entry hooks): true
     * while any list is scrolling, or shortly after it stopped.
     */
    static boolean isBusy() {
        if (globalState != IDLE) {
            return true;
        }
        if (hooked) {
            return System.currentTimeMillis() - lastScrollAt < SETTLE_MS;
        }
        return isScrolling(StoryAdSniper.currentRecycler());
    }

    /** Pure timing rule, kept free of Android types so it can be unit tested. */
    static boolean shouldWait(int state, long lastScrollAt, long now) {
        return state != IDLE || now - lastScrollAt < SETTLE_MS;
    }

    private static int stateOf(ViewGroup rv) {
        Integer state = null;
        if (rv != null) {
            synchronized (STATES) {
                state = STATES.get(rv);
            }
        }
        if (state == null) {
            state = readState(rv);
        }
        return state == null ? IDLE : state;
    }

    private static Integer readState(ViewGroup rv) {
        if (rv == null) {
            return null;
        }
        Object value = StoryView.call(rv, "getScrollState");
        if (!(value instanceof Integer)) {
            return null;
        }
        int state = (Integer) value;
        if (state != IDLE) {
            lastScrollAt = System.currentTimeMillis();
        }
        return state;
    }
}
