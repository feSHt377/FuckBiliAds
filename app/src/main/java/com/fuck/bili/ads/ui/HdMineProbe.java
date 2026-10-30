package com.fuck.bili.ads.ui;

import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.fuck.bili.ads.core.Reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * One-shot runtime structure dump for the HD 我的 page.
 *
 * <p>HD is obfuscated and its Kotlin metadata lies about field names, so the
 * only trustworthy source is the live object graph. This probe prints the view
 * tree plus every adapter's fields once per process, so the injection logic can
 * be written against what actually exists at runtime.
 */
final class HdMineProbe {

    private static final String TAG = "[HD][PROBE]";
    private static final int MAX_NODES = 300;
    private static final int MAX_DUMPS = 3;
    private static volatile int dumps;
    private static volatile long lastDumpAt;

    private HdMineProbe() {
    }

    /** Dumps at most three times, so a slow page still gets a full tree. */
    static void dumpOnce(Object fragment) {
        long now = System.currentTimeMillis();
        if (dumps >= MAX_DUMPS || now - lastDumpAt < 1200L) {
            return;
        }
        lastDumpAt = now;
        dumps++;
        try {
            Object view = Reflect.tryCallMethod(fragment, "getView");
            FbaLog.i(TAG + " dump#" + dumps + " fragment=" + fragment.getClass().getName()
                    + " view=" + (view == null ? "null" : view.getClass().getName()));
            if (!(view instanceof View)) {
                return;
            }
            dumpTree((View) view);
            dumpFields(fragment, "fragment", 2);
            dumpItemClass(fragment.getClass().getClassLoader());
        } catch (Throwable error) {
            FbaLog.e(TAG + " dump failed", error);
        }
    }

    /** The menu item model: field names HD actually uses at runtime. */
    private static void dumpItemClass(ClassLoader loader) {
        Class<?> item = Reflect.findClassIfExists("com.bilibili.lib.homepage.mine.MenuGroup$Item", loader);
        if (item == null) {
            FbaLog.w(TAG + " MenuGroup$Item not found");
            return;
        }
        StringBuilder out = new StringBuilder(TAG + " item class " + item.getName() + " :");
        for (Field field : item.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            out.append(' ').append(field.getName()).append(':').append(field.getType().getSimpleName());
        }
        FbaLog.i(out.toString());
    }

    private static void dumpTree(View root) {
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(root);
        int visited = 0;
        boolean identityLogged = false;
        while (!queue.isEmpty() && visited < MAX_NODES) {
            View view = queue.poll();
            visited++;
            boolean byName = isRecycler(view);
            Object adapter = byName
                    ? Reflect.tryCallMethod(view, "getAdapter") : null;
            StringBuilder line = new StringBuilder(TAG)
                    .append(" view ").append(view.getClass().getName());
            if (view.getId() != View.NO_ID) {
                try {
                    line.append(" id=").append(view.getResources().getResourceEntryName(view.getId()));
                } catch (Throwable ignored) {
                    line.append(" id=0x").append(Integer.toHexString(view.getId()));
                }
            }
            if (byName) {
                android.graphics.Rect rect = new android.graphics.Rect();
                view.getGlobalVisibleRect(rect);
                line.append(" [RecyclerView adapter=")
                        .append(adapter == null ? "null" : adapter.getClass().getName())
                        .append(" count=").append(adapter == null ? "-"
                                : Reflect.tryCallMethod(adapter, "getItemCount"))
                        .append(" rect=").append(rect)
                        .append(']');
            }
            FbaLog.i(line.toString());
            if (byName && !identityLogged) {
                identityLogged = true;
                FbaLog.i(TAG + " identity instanceof=" + (view instanceof RecyclerView)
                        + " sameClass=" + (view.getClass() == RecyclerView.class)
                        + " assignable=" + RecyclerView.class.isAssignableFrom(view.getClass())
                        + " viewLoader=" + view.getClass().getClassLoader()
                        + " rvLoader=" + RecyclerView.class.getClassLoader()
                        + " probeLoader=" + HdMineProbe.class.getClassLoader());
            }
            if (adapter != null) {
                dumpAdapter(adapter);
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    queue.add(group.getChildAt(i));
                }
            }
        }
        FbaLog.i(TAG + " view nodes=" + visited + " recyclerSeen=" + identityLogged);
    }

    /** Field dump for any object, one level deep plus one level into lists. */
    static void dumpFields(Object target, String label, int depth) {
        if (target == null) {
            FbaLog.i(TAG + " " + label + " = null");
            return;
        }
        FbaLog.i(TAG + " " + label + " : " + target.getClass().getName());
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Object value = read(field, target);
                FbaLog.i(TAG + "   " + label + "." + field.getName()
                        + " : " + field.getType().getSimpleName() + " = " + describe(value));
                if (depth > 0 && value instanceof List) {
                    List<?> list = (List<?>) value;
                    if (!list.isEmpty() && list.get(0) != null
                            && !(list.get(0) instanceof String)
                            && !(list.get(0) instanceof Number)) {
                        dumpFields(list.get(0), label + "." + field.getName() + "[0]", depth - 1);
                    }
                }
            }
        }
    }

    /** Dumps the content panel: finds the recycler and prints its adapter items. */
    static void dumpContent(Object fragment, String reason) {
        try {
            Object view = Reflect.tryCallMethod(fragment, "getView");
            FbaLog.i(TAG + " content(" + reason + ") fragment=" + fragment.getClass().getName()
                    + " view=" + (view == null ? "null" : view.getClass().getName()));
            if (!(view instanceof View)) {
                return;
            }
            ArrayDeque<View> queue = new ArrayDeque<>();
            queue.add((View) view);
            int visited = 0;
            while (!queue.isEmpty() && visited < MAX_NODES) {
                View node = queue.poll();
                visited++;
                if (isRecycler(node)) {
                    Object adapter = Reflect.tryCallMethod(node, "getAdapter");
                    FbaLog.i(TAG + "   recycler id=" + idName(node) + " adapter="
                            + (adapter == null ? "null" : adapter.getClass().getName())
                            + " count=" + (adapter == null ? "-" : Reflect.tryCallMethod(adapter, "getItemCount")));
                    if (adapter != null) {
                        dumpFields(adapter, "contentAdapter", 1);
                        dumpMethods(adapter);
                        dumpAccessors(adapter);
                    }
                }
                if (node instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) node;
                    for (int i = 0; i < group.getChildCount(); i++) {
                        queue.add(group.getChildAt(i));
                    }
                }
            }
        } catch (Throwable error) {
            FbaLog.e(TAG + " content dump failed", error);
        }
    }

    private static void dumpMethods(Object adapter) {
        for (Method method : adapter.getClass().getDeclaredMethods()) {
            StringBuilder signature = new StringBuilder(TAG)
                    .append("     method ").append(method.getName()).append('(');
            Class<?>[] parameters = method.getParameterTypes();
            for (int i = 0; i < parameters.length; i++) {
                if (i > 0) {
                    signature.append(',');
                }
                signature.append(parameters[i].getSimpleName());
            }
            FbaLog.i(signature.append(") -> ").append(method.getReturnType().getSimpleName()).toString());
        }
    }

    private static void dumpAccessors(Object adapter) {
        for (Method method : adapter.getClass().getDeclaredMethods()) {
            if (method.getParameterTypes().length != 0 || method.getReturnType() != List.class) {
                continue;
            }
            Object value = call(method, adapter);
            FbaLog.i(TAG + "     accessor " + method.getName() + "() -> " + describe(value));
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                for (int i = 0; i < Math.min(list.size(), 5); i++) {
                    FbaLog.i(TAG + "       item[" + i + "] " + describeItem(list.get(i)));
                }
            }
        }
    }

    private static String idName(View view) {
        if (view.getId() == View.NO_ID) {
            return "none";
        }
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Throwable ignored) {
            return "0x" + Integer.toHexString(view.getId());
        }
    }

    /** Name based recycler detection: the module may hold a different class copy. */
    static boolean isRecycler(View view) {
        return MineEntryCore.isRecycler(view);
    }

    private static void dumpAdapter(Object adapter) {
        Class<?> type = adapter.getClass();
        FbaLog.i(TAG + " adapter " + type.getName() + " super=" + type.getSuperclass()
                + " count=" + Reflect.tryCallMethod(adapter, "getItemCount"));
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                FbaLog.i(TAG + "   field " + c.getSimpleName() + "." + field.getName()
                        + " : " + field.getType().getSimpleName() + " = " + describe(read(field, adapter)));
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (method.getParameterTypes().length == 0 && method.getReturnType() == List.class) {
                Object value = call(method, adapter);
                FbaLog.i(TAG + "   accessor " + method.getName() + "() -> " + describe(value));
                if (value instanceof List) {
                    List<?> list = (List<?>) value;
                    for (int i = 0; i < Math.min(list.size(), 3); i++) {
                        FbaLog.i(TAG + "     item[" + i + "] " + describeItem(list.get(i)));
                    }
                }
            }
        }
    }

    /** name=value pairs of a menu item, so uri/title become obvious. */
    static String describeItem(Object item) {
        if (item == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(item.getClass().getName()).append('{');
        for (Class<?> c = item.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Object value = read(field, item);
                if (value instanceof List) {
                    continue;
                }
                out.append(field.getName()).append('=');
                if (value instanceof String) {
                    String text = (String) value;
                    out.append(text.length() > 30 ? text.substring(0, 30) + "…" : text);
                } else {
                    out.append(value);
                }
                out.append(' ');
            }
        }
        return out.append('}').toString();
    }

    private static Object read(Field field, Object target) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object call(Method method, Object target) {
        try {
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Short description that never recurses deeply. */
    static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            String text = (String) value;
            return "String(" + (text.length() > 40 ? text.substring(0, 40) + "…" : text) + ")";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.getClass().getSimpleName() + "(" + value + ")";
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<String> heads = new ArrayList<>();
            for (int i = 0; i < Math.min(list.size(), 3); i++) {
                Object element = list.get(i);
                heads.add(element == null ? "null" : element.getClass().getName());
            }
            return "List(" + list.size() + ")" + heads;
        }
        return value.getClass().getName();
    }
}
