package com.fuck.bili.ads.story;

import android.view.ViewGroup;

import java.lang.reflect.Method;

/**
 * Drives the story list to the next card. Only uses the RecyclerView scroll
 * API that the host itself uses, so playback switching stays host-owned.
 */
final class StoryNext {

    /** smoothScrollToPosition: the call the probe verified. */
    static final int MODE_SMOOTH = 0;
    /** scrollToPosition: immediate, used when the smooth scroll did nothing. */
    static final int MODE_DIRECT = 1;
    /** smoothScrollBy(0, height): move exactly one screen, snap helper does the rest. */
    static final int MODE_BY_SCREEN = 2;

    private StoryNext() {
    }

    /**
     * Scrolls from {@code fromPos} to the next card.
     *
     * @return {@code true} when one of the scroll APIs accepted the call.
     */
    static boolean toNext(ViewGroup rv, int fromPos) {
        return toNext(rv, fromPos, MODE_SMOOTH);
    }

    static boolean toNext(ViewGroup rv, int fromPos, int mode) {
        if (rv == null || fromPos < 0) {
            return false;
        }
        int target = fromPos + 1;
        Object adapter = StoryView.adapterOf(rv);
        int count = StoryView.itemCount(adapter);
        if (count >= 0 && target >= count) {
            return false;
        }
        if (mode == MODE_DIRECT) {
            if (invoke(rv, "scrollToPosition", new Class<?>[]{int.class}, target)) {
                return true;
            }
            // fall through to the screen-sized scroll
        }
        if (mode == MODE_SMOOTH) {
            if (invoke(rv, "smoothScrollToPosition", new Class<?>[]{int.class}, target)) {
                return true;
            }
        }
        if (mode != MODE_DIRECT) {
            int height = rv.getHeight();
            if (height > 0) {
                return invoke(rv, "smoothScrollBy", new Class<?>[]{int.class, int.class}, 0, height);
            }
        }
        return false;
    }

    private static boolean invoke(Object target, String name, Class<?>[] types, Object... args) {
        if (target == null) {
            return false;
        }
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, types);
                m.setAccessible(true);
                m.invoke(target, args);
                return true;
            } catch (Throwable ignore) {
                // try parent class
            }
        }
        return false;
    }
}
