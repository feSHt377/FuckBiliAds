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
 * <p>The story list is a {@link android.view.ViewGroup} whose class extends
 * {@code androidx.recyclerview.widget.RecyclerView}. The card currently on
 * screen is the child that covers the largest visible area — never "the item
 * that was bound last", because the adapter pre-binds several off-screen pages.
 *
 * <p>Every reflective lookup here must be tolerant: host builds rename classes,
 * wrap the adapter, and the runtime view type is a subclass of the declared
 * parameter type. Method lookup therefore matches by name + arity + assignable
 * parameter types instead of exact {@code Class} equality.
 */
final class StoryView {

    private static final String RV_CLASS = "androidx.recyclerview.widget.RecyclerView";
    private static final String STORY_PKG = "com.bilibili.video.story";
    private static final String VIEWPAGER2_PKG = "androidx.viewpager2";

    private StoryView() {
    }

    /**
     * Finds the story list inside the activity.
     *
     * <p>Preference order: a RecyclerView whose adapter lives in
     * {@code com.bilibili.video.story} (that is the probed adapter), then any
     * RecyclerView that is not the hosting ViewPager2, picking the one whose
     * visible child covers the most screen area so nested lists (comments,
     * action bar) never win.
     */
    static ViewGroup findRecycler(Activity activity) {
        if (activity == null) {
            return null;
        }
        try {
            List<ViewGroup> candidates = new ArrayList<>();
            collect(activity.getWindow().getDecorView(), candidates);

            ViewGroup bestStory = null;
            long bestStoryArea = -1;
            ViewGroup bestOther = null;
            long bestOtherArea = -1;
            for (int i = 0; i < candidates.size(); i++) {
                ViewGroup rv = candidates.get(i);
                Object adapter = adapterOf(rv);
                if (adapter == null) {
                    continue;
                }
                String name = adapter.getClass().getName();
                long area = dominantArea(rv);
                if (name.startsWith(STORY_PKG)) {
                    if (area > bestStoryArea) {
                        bestStoryArea = area;
                        bestStory = rv;
                    }
                } else if (!name.startsWith(VIEWPAGER2_PKG)) {
                    if (area > bestOtherArea) {
                        bestOtherArea = area;
                        bestOther = rv;
                    }
                }
            }
            return bestStory != null ? bestStory : bestOther;
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

    /**
     * The item backing the dominant child.
     *
     * <p>Primary source is the item held by the child's ViewHolder (that object
     * is what {@code bindViewHolder} received during probing); the adapter's
     * backing list is only a fallback because its index space does not have to
     * match adapter positions (headers, wrapped adapters).
     */
    static Object itemOf(ViewGroup rv, View child) {
        if (rv == null || child == null) {
            return null;
        }
        Object item = storyItemOf(call(rv, "getChildViewHolder", child));
        if (item != null) {
            return item;
        }
        return findItem(adapterOf(rv), adapterPosition(rv, child));
    }

    /** Finds a story model instance among the ViewHolder's fields. */
    private static Object storyItemOf(Object holder) {
        if (holder == null) {
            return null;
        }
        Object fallback = null;
        for (Class<?> c = holder.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            Field[] fields = c.getDeclaredFields();
            for (Field f : fields) {
                if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                Object value;
                try {
                    f.setAccessible(true);
                    value = f.get(holder);
                } catch (Throwable ignore) {
                    continue;
                }
                if (value == null || !isStoryModel(value.getClass().getName())) {
                    continue;
                }
                if (hasField(value.getClass(), "adInfo")) {
                    return value;
                }
                if (fallback == null) {
                    fallback = value;
                }
            }
        }
        return fallback;
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

    /**
     * Best-effort lookup of the item at {@code pos}: scans the adapter's own
     * List / array fields (read-only reflection) and only accepts story models.
     */
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
        if (item != null && isStoryModel(item.getClass().getName())) {
            return item;
        }
        return null;
    }

    /**
     * True for the story feature's own model types. The exact package is the
     * probed one; the other two checks keep working after a host refactor that
     * only renames the package (the model name itself stays "StoryDetail").
     */
    private static boolean isStoryModel(String className) {
        return className.startsWith(STORY_PKG)
                || className.endsWith("StoryDetail")
                || className.contains(".story.");
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
