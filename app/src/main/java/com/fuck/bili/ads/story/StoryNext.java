package com.fuck.bili.ads.story;

import android.view.ViewGroup;

import java.lang.reflect.Method;

/**
 * Drives the story list to the next card. Only uses the RecyclerView scroll
 * API that the host itself uses, so playback switching stays host-owned.
 */
final class StoryNext {

    private StoryNext() {
    }

    /**
     * Scrolls from {@code fromPos} to the next card.
     *
     * @return {@code true} when one of the scroll APIs accepted the call.
     */
    static boolean toNext(ViewGroup rv, int fromPos) {
        if (rv == null || fromPos < 0) {
            return false;
        }
        Object adapter = StoryView.adapterOf(rv);
        int count = StoryView.itemCount(adapter);
        int target = fromPos + 1;
        if (count >= 0 && target >= count) {
            return false;
        }
        if (invoke(rv, "smoothScrollToPosition", new Class<?>[]{int.class}, target)) {
            return true;
        }
        if (invoke(rv, "scrollToPosition", new Class<?>[]{int.class}, target)) {
            return true;
        }
        // Last resort: move exactly one screen so the host's snap helper still runs.
        int height = rv.getHeight();
        if (height > 0) {
            return invoke(rv, "smoothScrollBy", new Class<?>[]{int.class, int.class}, 0, height);
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
