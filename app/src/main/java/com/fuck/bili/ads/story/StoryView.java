package com.fuck.bili.ads.story;

import android.app.Activity;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only helpers for the vertical short-video page (故事模式 / 小视频).
 *
 * <p>Host builds shuffle class names on every release (the story adapter was
 * {@code L} in 9.15, {@code X} in 9.16, the holder changed too), so nothing in
 * here matches a class <em>name</em> when it can match a class <em>shape</em>:
 * a card model is any object owning an {@code adInfo} field, and the list is
 * the RecyclerView whose adapter holds such objects.
 */
final class StoryView {

    private static final String RV_CLASS = "androidx.recyclerview.widget.RecyclerView";

    private StoryView() {
    }

    /**
     * Picks the story list: the RecyclerView whose adapter carries card models,
     * falling back to the largest one on screen.
     */
    static ViewGroup findRecycler(Activity activity) {
        if (activity == null) {
            return null;
        }
        try {
            List<ViewGroup> candidates = new ArrayList<>();
            collect(activity.getWindow().getDecorView(), candidates);

            ViewGroup best = null;
            long bestScore = -1;
            for (int i = 0; i < candidates.size(); i++) {
                ViewGroup rv = candidates.get(i);
                Object adapter = adapterOf(rv);
                if (adapter == null) {
                    continue;
                }
                long score = dominantArea(rv);
                if (holdsStoryItems(adapter)) {
                    // The real pager: its adapter list is full of card models.
                    score += 1L << 40;
                } else {
                    Object holderItem = itemOfHolder(rv, dominantChild(rv));
                    if (holderItem != null) {
                        score += 1L << 30;
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = rv;
                }
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void collect(View v, List<ViewGroup> out) {
        if (!(v instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) v;
        if (extendsClass(group, RV_CLASS)) {
            out.add(group);
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            collect(group.getChildAt(i), out);
        }
    }

    /** True when {@code obj}'s class or one of its superclasses is {@code className}. */
    private static boolean extendsClass(Object obj, String className) {
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            if (className.equals(c.getName())) {
                return true;
            }
        }
        return false;
    }

    static Object adapterOf(ViewGroup rv) {
        return call(rv, "getAdapter");
    }

    /** The child covering the largest visible area, i.e. the card being watched. */
    static View dominantChild(ViewGroup rv) {
        if (rv == null) {
            return null;
        }
        View best = null;
        long bestArea = 0;
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE) {
                continue;
            }
            Rect r = new Rect();
            if (!child.getGlobalVisibleRect(r)) {
                continue;
            }
            long area = (long) r.width() * r.height();
            if (area > bestArea) {
                bestArea = area;
                best = child;
            }
        }
        return best;
    }

    private static long dominantArea(ViewGroup rv) {
        View child = dominantChild(rv);
        if (child == null) {
            return 0;
        }
        Rect r = new Rect();
        if (!child.getGlobalVisibleRect(r)) {
            return 0;
        }
        return (long) r.width() * r.height();
    }

    /** Adapter position of the dominant child, {@code -1} when unknown. */
    static int dominantPos(ViewGroup rv) {
        return adapterPosition(rv, dominantChild(rv));
    }

    static int adapterPosition(ViewGroup rv, View child) {
        if (rv == null || child == null) {
            return -1;
        }
        Object pos = call(rv, "getChildAdapterPosition", child);
        if (pos instanceof Integer && (Integer) pos >= 0) {
            return (Integer) pos;
        }
        // getChildAdapterPosition returns NO_POSITION while the list is being
        // updated; the layout position is a usable stand-in for "what is shown".
        pos = call(rv, "getChildLayoutPosition", child);
        return pos instanceof Integer ? (Integer) pos : -1;
    }

    /** The card model backing the dominant child, {@code null} when unknown. */
    static Object itemOf(ViewGroup rv, View child) {
        if (rv == null || child == null) {
            return null;
        }
        Object item = itemOfHolder(rv, child);
        if (item != null) {
            return item;
        }
        return findItem(adapterOf(rv), adapterPosition(rv, child));
    }

    /** Looks for the card model inside the child's ViewHolder (depth limited). */
    private static Object itemOfHolder(ViewGroup rv, View child) {
        if (rv == null || child == null) {
            return null;
        }
        Object holder = call(rv, "getChildViewHolder", child);
        return searchItem(holder, 2);
    }

    private static Object searchItem(Object target, int depth) {
        if (target == null || depth < 0) {
            return null;
        }
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            Field[] fields = c.getDeclaredFields();
            for (Field f : fields) {
                if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                Object value;
                try {
                    f.setAccessible(true);
                    value = f.get(target);
                } catch (Throwable ignore) {
                    continue;
                }
                if (value == null) {
                    continue;
                }
                if (isStoryItem(value)) {
                    return value;
                }
                String name = value.getClass().getName();
                if (depth > 0 && (name.startsWith("com.bilibili.") || name.startsWith("tv.danmaku."))) {
                    Object nested = searchItem(value, depth - 1);
                    if (nested != null) {
                        return nested;
                    }
                }
            }
        }
        return null;
    }

    /** A story card model: owns {@code adInfo} (ads) and {@code adType}. */
    static boolean isStoryItem(Object value) {
        if (value == null) {
            return false;
        }
        Class<?> c = value.getClass();
        if (c.isPrimitive() || c.isArray() || value instanceof CharSequence) {
            return false;
        }
        return hasField(c, "adInfo") && hasField(c, "adType");
    }

    private static boolean hasField(Class<?> c, String name) {
        for (; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                c.getDeclaredField(name);
                return true;
            } catch (Throwable ignore) {
                // keep walking
            }
        }
        return false;
    }

    /** True when the adapter keeps a list of card models (not a header/pager list). */
    private static boolean holdsStoryItems(Object adapter) {
        for (Class<?> c = adapter.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            Field[] fields = c.getDeclaredFields();
            for (Field f : fields) {
                if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    Object value = f.get(adapter);
                    if (!(value instanceof List)) {
                        continue;
                    }
                    List<?> list = (List<?>) value;
                    for (int i = 0; i < Math.min(list.size(), 5); i++) {
                        if (isStoryItem(list.get(i))) {
                            return true;
                        }
                    }
                } catch (Throwable ignore) {
                    // next field
                }
            }
        }
        return false;
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
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch (Throwable ignore) {
                // try parent class
            }
        }
        // Kotlin data classes / Java beans: fall back to the accessor.
        String cap = name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
        Object value = call(obj, "get" + cap);
        return value != null ? value : call(obj, "is" + cap);
    }

    /** Best-effort lookup of the card model at {@code pos}. */
    static Object findItem(Object adapter, int pos) {
        if (adapter == null || pos < 0) {
            return null;
        }
        Class<?> c = adapter.getClass();
        for (int level = 0; level < 6 && c != null && c != Object.class; level++, c = c.getSuperclass()) {
            Field[] fields = c.getDeclaredFields();
            for (Field f : fields) {
                if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                Object value;
                try {
                    f.setAccessible(true);
                    value = f.get(adapter);
                } catch (Throwable ignore) {
                    continue;
                }
                Object item = storyElementAt(value, pos);
                if (item != null) {
                    return item;
                }
            }
        }
        return null;
    }

    private static Object storyElementAt(Object value, int pos) {
        Object item = elementAt(value, pos);
        if (isStoryItem(item)) {
            return item;
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

    /**
     * Invokes {@code name} on {@code target} with the given arguments.
     *
     * <p>Parameter types are matched by assignability (a {@code FrameLayout}
     * argument satisfies a {@code View} parameter), never by exact class —
     * exact matching silently failed for {@code getChildAdapterPosition(View)}.
     */
    static Object call(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            Method[] methods = c.getDeclaredMethods();
            for (Method m : methods) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) {
                    continue;
                }
                if (!accepts(m.getParameterTypes(), args)) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                    return m.invoke(target, args);
                } catch (Throwable ignore) {
                    return null;
                }
            }
        }
        return null;
    }

    private static boolean accepts(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            if (args[i] == null) {
                if (types[i].isPrimitive()) {
                    return false;
                }
                continue;
            }
            if (!box(types[i]).isAssignableFrom(args[i].getClass())) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) {
            return c;
        }
        if (c == int.class) {
            return Integer.class;
        }
        if (c == long.class) {
            return Long.class;
        }
        if (c == boolean.class) {
            return Boolean.class;
        }
        if (c == float.class) {
            return Float.class;
        }
        if (c == double.class) {
            return Double.class;
        }
        if (c == short.class) {
            return Short.class;
        }
        if (c == byte.class) {
            return Byte.class;
        }
        if (c == char.class) {
            return Character.class;
        }
        return c;
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

    /**
     * Stable-ish identity of a story card, used to skip each ad only once.
     *
     * <p>Ads must be identified by {@code adInfo.feedCreativeId}: their
     * {@code aid} is 0 for every ad, so aid-based identities collapse all ads
     * into one and the second ad would be treated as "already skipped".
     */
    static String identityOf(Object item, int pos) {
        Object ad = fieldValue(item, "adInfo");
        if (ad != null) {
            Object creative = fieldValue(ad, "feedCreativeId");
            String value = creative == null ? null : String.valueOf(creative);
            if (value != null && !"0".equals(value) && !"null".equals(value)) {
                return "c" + value;
            }
        }
        String aid = aidOf(item);
        if (!"-".equals(aid) && !"0".equals(aid)) {
            return "a" + aid;
        }
        return "p" + pos;
    }
}
