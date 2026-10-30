package com.fuck.bili.ads.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.fuck.bili.ads.core.Reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared plumbing for the "module settings" entry on every client.
 *
 * <p>The settings UI ({@link FbaSettings}) and the entry model
 * ({@code com.bilibili.lib.homepage.mine.MenuGroup$Item}) are the same on the
 * phone app and HD; only the place the entry is injected into differs. Things
 * that are identical on both clients live here so they can only be fixed once:
 *
 * <ul>
 *   <li>recognising a RecyclerView without {@code instanceof} — the module ships
 *       its own androidx copy, so the host's class is a different class object;</li>
 *   <li>reading the item list of an adapter, whether it is a field or a
 *       zero-argument accessor (HD hides it behind {@code b#h0()});</li>
 *   <li>finding the items of one card, the card title, and {@code uri} matches;</li>
 *   <li>building a {@code MenuGroup.Item} and writing its fields.</li>
 * </ul>
 */
final class MineEntryCore {

    private MineEntryCore() {
    }

    // ------------------------------------------------------------ view helpers

    /**
     * Name based RecyclerView test.
     *
     * <p>{@code view instanceof RecyclerView} is always false inside the host:
     * the module bundles androidx, so its {@code RecyclerView} and the host's are
     * different classes from different class loaders.
     */
    static boolean isRecycler(View view) {
        for (Class<?> c = view.getClass(); c != null; c = c.getSuperclass()) {
            if ("androidx.recyclerview.widget.RecyclerView".equals(c.getName())) {
                return true;
            }
        }
        return false;
    }

    /** Depth-first walk over a view tree, handing every RecyclerView to the sink. */
    static void walkRecyclers(View root, Sink sink) {
        ArrayList<View> queue = new ArrayList<>();
        queue.add(root);
        for (int i = 0; i < queue.size(); i++) {
            View view = queue.get(i);
            if (isRecycler(view)) {
                sink.accept(view);
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int c = 0; c < group.getChildCount(); c++) {
                    queue.add(group.getChildAt(c));
                }
            }
        }
    }

    interface Sink {
        void accept(View recycler);
    }

    // --------------------------------------------------------------- adapters

    /**
     * The first list an adapter exposes that contains {@code itemClass} items.
     *
     * <p>Checks instance fields first, then zero-argument accessors, because HD
     * keeps the menu list behind a method instead of a field.
     */
    static List<Object> listOf(Object adapter, String itemClass) {
        for (Field field : allFields(adapter.getClass())) {
            if (Modifier.isStatic(field.getModifiers()) || !List.class.isAssignableFrom(field.getType())) {
                continue;
            }
            List<Object> list = asItemList(read(field, adapter), itemClass);
            if (list != null) {
                return list;
            }
        }
        for (Method method : allMethods(adapter.getClass())) {
            if (Modifier.isStatic(method.getModifiers())
                    || method.getParameterTypes().length != 0
                    || method.getReturnType() != List.class) {
                continue;
            }
            List<Object> list = asItemList(call(method, adapter), itemClass);
            if (list != null) {
                return list;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asItemList(Object value, String itemClass) {
        if (!(value instanceof List)) {
            return null;
        }
        List<Object> list = (List<Object>) value;
        if (list.isEmpty() || !isItem(list.get(0), itemClass)) {
            return null;
        }
        return list;
    }

    /** Items of one card: the list field/accessor holding menu items. */
    static List<Object> itemsOf(Object group, String itemClass) {
        List<Object> empty = null;
        for (Field field : allFields(group.getClass())) {
            if (Modifier.isStatic(field.getModifiers()) || !List.class.isAssignableFrom(field.getType())) {
                continue;
            }
            Object value = read(field, group);
            if (!(value instanceof List)) {
                continue;
            }
            List<Object> list = (List<Object>) value;
            if (!list.isEmpty() && isItem(list.get(0), itemClass)) {
                return list;
            }
            if (list.isEmpty() && empty == null) {
                empty = list;
            }
        }
        return empty;
    }

    static String titleOf(Object group) {
        for (Field field : allFields(group.getClass())) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                continue;
            }
            Object value = read(field, group);
            if (value instanceof String) {
                return (String) value;
            }
        }
        return "";
    }

    static boolean isItem(Object value, String itemClass) {
        return value != null && itemClass.equals(value.getClass().getName());
    }

    // ------------------------------------------------------------ item building

    static Object buildItem(Context context, String itemClass, String title, String uri, long id) {
        if (context == null) {
            return null;
        }
        try {
            Class<?> type = Reflect.findClass(itemClass, context.getClassLoader());
            Object item = Reflect.newInstance(type);
            setField(item, "id", id);
            setField(item, "title", title);
            setField(item, "uri", uri);
            setField(item, "needLogin", 0);
            setField(item, "visible", 1);
            setField(item, "localRedDot", 0);
            return item;
        } catch (Throwable error) {
            FbaLog.e("[ENTRY] 构造菜单项失败: " + itemClass, error);
            return null;
        }
    }

    static boolean containsUri(List<Object> items, String uri) {
        for (Object item : items) {
            if (uri.equals(stringField(item, "uri"))) {
                return true;
            }
        }
        return false;
    }

    // ----------------------------------------------------------------- helpers

    static List<Field> allFields(Class<?> start) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> type = start; type != null && type != Object.class; type = type.getSuperclass()) {
            Collections.addAll(fields, type.getDeclaredFields());
        }
        return fields;
    }

    static List<Method> allMethods(Class<?> start) {
        List<Method> methods = new ArrayList<>();
        for (Class<?> type = start; type != null && type != Object.class; type = type.getSuperclass()) {
            Collections.addAll(methods, type.getDeclaredMethods());
        }
        return methods;
    }

    static Object read(Field field, Object target) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static Object call(Method method, Object target) {
        try {
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static Object field(Object target, String name) {
        if (target == null) {
            return null;
        }
        for (Field field : allFields(target.getClass())) {
            if (field.getName().equals(name)) {
                return read(field, target);
            }
        }
        return null;
    }

    static String stringField(Object target, String name) {
        Object value = field(target, name);
        return value instanceof String ? (String) value : null;
    }

    static void setField(Object target, String name, Object value) {
        for (Field field : allFields(target.getClass())) {
            if (!field.getName().equals(name)) {
                continue;
            }
            try {
                field.setAccessible(true);
                Class<?> type = field.getType();
                if (type == long.class && value instanceof Number) {
                    field.setLong(target, ((Number) value).longValue());
                } else if (type == int.class && value instanceof Number) {
                    field.setInt(target, ((Number) value).intValue());
                } else if (!type.isPrimitive()) {
                    field.set(target, value);
                }
            } catch (Throwable ignored) {
                // best effort
            }
            return;
        }
    }
}
