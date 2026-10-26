package com.fuck.bili.ads.story;

import android.app.Activity;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Read-only helpers for the vertical short-video page (故事模式 / 小视频).
 *
 * <p>The story list is a plain {@link androidx.recyclerview.widget.RecyclerView}
 * owned by {@code com.bilibili.video.story}. The item currently on screen is the
 * child that covers the largest visible area — never "the item that was bound
 * last", because the adapter pre-binds several off-screen pages.
 */
final class StoryView {

    private static final String STORY_ADAPTER_PREFIX = "com.bilibili.video.story";

    private StoryView() {
    }

    /** Finds the story RecyclerView inside the activity's view tree. */
    static ViewGroup findRecycler(Activity activity) {
        if (activity == null) {
            return null;
        }
        try {
            return findRecycler(activity.getWindow().getDecorView());
        } catch (Throwable t) {
            return null;
        }
    }

    private static ViewGroup findRecycler(View v) {
        if (!(v instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) v;
        if ("androidx.recyclerview.widget.RecyclerView".equals(group.getClass().getName())) {
            Object adapter = call(group, "getAdapter");
            if (adapter != null && adapter.getClass().getName().startsWith(STORY_ADAPTER_PREFIX)) {
                return group;
            }
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            ViewGroup found = findRecycler(group.getChildAt(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    static Object adapterOf(ViewGroup rv) {
        return call(rv, "getAdapter");
    }

    /** Adapter position of the child that currently covers the largest visible area. */
    static int dominantPos(ViewGroup rv) {
        if (rv == null) {
            return -1;
        }
        int bestPos = -1;
        long bestArea = 0;
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            Rect r = new Rect();
            if (!child.getGlobalVisibleRect(r)) {
                continue;
            }
            long area = (long) r.width() * r.height();
            if (area > bestArea) {
                bestArea = area;
                Object pos = call(rv, "getChildAdapterPosition", child);
                bestPos = pos instanceof Integer ? (Integer) pos : -1;
            }
        }
        return bestPos;
    }

    static int itemCount(Object adapter) {
        Object n = call(adapter, "getItemCount");
        return n instanceof Integer ? (Integer) n : -1;
    }

    /** Reads field {@code name} from the object or one of its superclasses. */
    static Object fieldValue(Object obj, String name) {
        if (obj == null) {
            return null;
        }
        Class<?> c = obj.getClass();
        for (int i = 0; i < 6 && c != null && c != Object.class; i++, c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch (Throwable ignore) {
                // try parent class
            }
        }
        return null;
    }

    /**
     * Best-effort lookup of the item backed by {@code pos}: scans the adapter's
     * own List / array fields (read-only reflection).
     */
    static Object findItem(Object adapter, int pos) {
        if (adapter == null || pos < 0) {
            return null;
        }
        Class<?> c = adapter.getClass();
        for (int level = 0; level < 5 && c != null && c != Object.class; level++, c = c.getSuperclass()) {
            try {
                for (Field f : c.getDeclaredFields()) {
                    Object value;
                    try {
                        if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())) {
                            continue;
                        }
                        f.setAccessible(true);
                        value = f.get(adapter);
                    } catch (Throwable ignore) {
                        continue;
                    }
                    Object item = elementAt(value, pos);
                    if (item != null) {
                        return item;
                    }
                }
            } catch (Throwable ignore) {
                // keep looking in the superclass
            }
        }
        return null;
    }

    private static Object elementAt(Object value, int pos) {
        try {
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                return pos < list.size() ? list.get(pos) : null;
            }
            if (value != null && value.getClass().isArray()
                    && !value.getClass().getComponentType().isPrimitive()) {
                return pos < Array.getLength(value) ? Array.get(value, pos) : null;
            }
        } catch (Throwable ignore) {
            // unusable collection
        }
        return null;
    }

    static Object call(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i] == null ? Object.class : args[i].getClass();
        }
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, types);
                m.setAccessible(true);
                return m.invoke(target, args);
            } catch (Throwable ignore) {
                // try parent class
            }
        }
        return null;
    }

    static String aidOf(Object item) {
        return idOf(item, "aid");
    }

    static String cidOf(Object item) {
        return idOf(item, "cid");
    }

    private static String idOf(Object item, String name) {
        if (item == null) {
            return "-";
        }
        Object holder = fieldValue(item, "playerArgs");
        if (holder == null) {
            holder = fieldValue(item, "playerParams");
        }
        if (holder != null) {
            Object value = fieldValue(holder, name);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        Object value = fieldValue(item, name);
        return value == null ? "-" : String.valueOf(value);
    }

    /** Stable-ish identity of a story card, used to skip each ad only once. */
    static String identityOf(Object item, int pos) {
        String aid = aidOf(item);
        if (!"-".equals(aid)) {
            return "a" + aid;
        }
        Object ad = fieldValue(item, "adInfo");
        Object creative = ad == null ? null : fieldValue(ad, "feedCreativeId");
        if (creative != null) {
            return "c" + creative;
        }
        return "p" + pos;
    }
}
