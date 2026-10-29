package com.fuck.bili.ads.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import com.fuck.bili.ads.core.Host;
import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds the module entry to 我的 → 更多服务.
 *
 * <p>The page is server-driven: {@code MenuGroup} cards carry a list of
 * {@code MenuGroup.Item} (title + icon + uri). Appending one item to the right
 * group therefore renders with the host's own grid item, and the click is
 * attached directly to the injected row.
 */
public final class MineServiceEntry {

    private static final String TAG = FbaRuntime.TAG;

    private static final String FRAGMENT = "tv.danmaku.bili.ui.main2.mine.HomeUserCenterFragment";
    private static final String GROUP = "com.bilibili.lib.homepage.mine.MenuGroup";
    private static final String ITEM = "com.bilibili.lib.homepage.mine.MenuGroup$Item";

    private static final String SETTINGS_URI = "bilibili://fuckbiliads/settings";
    private static final String ITEM_TITLE = "FuckBiliAds";
    private static final long ITEM_ID = 0x4642414CL;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile boolean injectedOnce;

    private MineServiceEntry() {
    }

    public static void hook(ClassLoader classLoader) {
        if (Host.isHd()) {
            HdMineServiceEntry.hook(classLoader);
            return;
        }
        boolean router = true; // routing is handled by the direct click hook
        boolean page = hookMinePage(classLoader);
        FbaRuntime.mark("设置入口", router && page);
    }

    private static boolean hookMinePage(ClassLoader classLoader) {
        try {
            final Class<?> fragment = Reflect.findClass(FRAGMENT, classLoader);
            NativeHooks.hookAllMethods("MineServiceEntry.onViewCreated", fragment, "onViewCreated",
                    chain -> {
                        Object self = chain.getThisObject();
                        Object result = chain.proceed();
                        if (isMineFragment(self, fragment)) {
                            schedule(self);
                        }
                        return result;
                    });
            NativeHooks.hookAllMethods("MineServiceEntry.onResume", fragment, "onResume",
                    chain -> {
                        Object self = chain.getThisObject();
                        Object result = chain.proceed();
                        if (isMineFragment(self, fragment)) {
                            schedule(self);
                        }
                        return result;
                    });
            FbaLog.i("mine entry hook installed");
            return true;
        } catch (Throwable t) {
            FbaLog.e("mine entry hook failed", t);
            return false;
        }
    }

    /** The hooks may land on Fragment.onViewCreated, which every fragment calls. */
    private static boolean isMineFragment(Object fragment, Class<?> mineFragment) {
        return fragment != null && mineFragment.isInstance(fragment);
    }

    /** Retries while the server-driven list is still loading. */
    private static void schedule(final Object fragment) {
        inject(fragment);
        for (int i = 1; i <= 20; i++) {
            MAIN.postDelayed(() -> inject(fragment), i * 500L);
        }
    }

    private static void inject(Object fragment) {
        try {
            if (fragment == null) {
                return;
            }
            Object view = Reflect.tryCallMethod(fragment, "getView");
            if (!(view instanceof View)) {
                return;
            }
            Object adapter = findAdapter((View) view);
            if (adapter == null) {
                return;
            }
            hookBind(adapter);
            if (ensure(adapter, true)) {
                if (!injectedOnce) {
                    injectedOnce = true;
                    FbaRuntime.mark("服务入口", true);
                    FbaLog.i("mine entry injected");
                }
            }
        } catch (Throwable t) {
            FbaLog.e("mine entry inject failed", t);
        }
    }

    /** Re-applies the entry before every bind, so data refreshes keep it. */
    private static void hookBind(final Object adapter) {
        final Class<?> type = adapter.getClass();
        if (HOOKED.contains(type)) {
            return;
        }
        HOOKED.add(type);
        try {
            NativeHooks.hookAllMethods("MineServiceEntry.onBindViewHolder", type, "onBindViewHolder",
                    chain -> {
                        Object self = chain.getThisObject();
                        try {
                            if (self != null) {
                                long now = System.currentTimeMillis();
                                if (now - lastEnsureAt >= 400L) {
                                    lastEnsureAt = now;
                                    ensure(self, false);
                                }
                            }
                        } catch (Throwable t) {
                            FbaLog.e("mine entry bind ensure failed", t);
                        }
                        Object result = chain.proceed();
                        try {
                            List<Object> args = chain.getArgs();
                            Object holder = args.isEmpty() ? null : args.get(0);
                            Object position = args.size() > 1 ? args.get(1) : null;
                            if (holder != null && position instanceof Integer) {
                                final Object adapterRef = self;
                                final Object holderRef = holder;
                                final int index = (Integer) position;
                                Handler handler = FbaRuntime.handler();
                                if (handler != null) {
                                    handler.postDelayed(() -> attachClick(adapterRef, holderRef, index, 0), 60L);
                                }
                            }
                        } catch (Throwable t) {
                            FbaLog.e("mine entry click schedule failed", t);
                        }
                        return result;
                    });
            FbaLog.i("mine adapter bound: " + type.getName());
        } catch (Throwable t) {
            FbaLog.e("mine bind hook failed", t);
        }
    }

    private static void attachClick(Object adapter, Object holder, int position, int attempt) {
        try {
            List<Object> groups = menuGroups(adapter);
            if (groups == null || position < 0 || position >= groups.size()) {
                return;
            }
            Object group = groups.get(position);
            int index = indexOfOurItem(group);
            if (index < 0) {
                return;
            }
            Object root = Reflect.tryGetObjectField(holder, "itemView");
            if (!(root instanceof View)) {
                return;
            }
            Object recycler = findRecyclerIn((View) root);
            if (recycler == null) {
                return;
            }
            Object viewHolder = Reflect.tryCallMethod(recycler, "findViewHolderForAdapterPosition", index);
            Object itemView = viewHolder == null ? null
                    : Reflect.tryGetObjectField(viewHolder, "itemView");
            if (!(itemView instanceof View)) {
                if (attempt < 5) {
                    Handler handler = FbaRuntime.handler();
                    if (handler != null) {
                        handler.postDelayed(() -> attachClick(adapter, holder, position, attempt + 1), 120L);
                    }
                }
                return;
            }
            hookHolderClick(viewHolder);
            FbaLog.i("mine entry click hooked (group " + position + ", item " + index + ")");
        } catch (Throwable t) {
            FbaLog.e("mine entry click attach failed", t);
        }
    }

    /**
     * Hooks the grid item's {@code onClick(View)} on its holder class.
     *
     * <p>Replacing the listener on the item view would leak to recycled rows;
     * hooking the holder method only reacts when the bound item is ours and
     * lets every other item keep the host behaviour.
     */
    private static void hookHolderClick(Object holder) {
        if (holder == null) {
            return;
        }
        final Class<?> type = holder.getClass();
        if (!CLICK_HOOKED.add(type)) {
            return;
        }
        Method onClick = null;
        for (Class<?> c = type; c != null && c != Object.class && onClick == null; c = c.getSuperclass()) {
            try {
                onClick = c.getDeclaredMethod("onClick", View.class);
            } catch (Throwable ignore) {
                // keep walking
            }
        }
        if (onClick == null) {
            FbaLog.w("mine item holder has no onClick(View): " + type.getName());
            return;
        }
        try {
            NativeHooks.hook("MineServiceEntry.onClick", onClick, chain -> {
                try {
                    Object item = itemField(chain.getThisObject());
                    if (item == null || !SETTINGS_URI.equals(field(item, "uri"))) {
                        return chain.proceed();
                    }
                    Context context = null;
                    try {
                        Object itemView = Reflect.tryGetObjectField(chain.getThisObject(), "itemView");
                        if (itemView instanceof View) {
                            context = ((View) itemView).getContext();
                        }
                    } catch (Throwable ignore) {
                        // fall back to the app context
                    }
                    if (context == null) {
                        context = FbaRuntime.context();
                    }
                    FbaLog.i("mine entry clicked");
                    FbaSettings.show(context);
                    return null;
                } catch (Throwable t) {
                    FbaLog.e("mine entry click hook failed", t);
                    return chain.proceed();
                }
            });
            FbaLog.i("mine holder click hooked: " + type.getName() + "." + onClick.getName());
        } catch (Throwable t) {
            FbaLog.e("mine holder click hook failed", t);
        }
    }

    private static Object itemField(Object holder) {
        for (Class<?> c = holder.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                try {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    if (!field.getType().getName().endsWith("MenuGroup$Item")) {
                        continue;
                    }
                    field.setAccessible(true);
                    return field.get(holder);
                } catch (Throwable ignore) {
                    // next field
                }
            }
        }
        return null;
    }

    private static int indexOfOurItem(Object group) {
        List<Object> items = itemsOf(group);
        for (int i = 0; i < items.size(); i++) {
            if (SETTINGS_URI.equals(field(items.get(i), "uri"))) {
                return i;
            }
        }
        return -1;
    }

    private static Object findRecyclerIn(View root) {
        java.util.ArrayDeque<View> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            View view = queue.poll();
            if (view.getClass().getName().endsWith("RecyclerView")) {
                return view;
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    queue.add(group.getChildAt(i));
                }
            }
        }
        return null;
    }

    private static final java.util.Set<Class<?>> CLICK_HOOKED =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<Class<?>, Boolean>());

    private static final java.util.Set<Class<?>> HOOKED =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<Class<?>, Boolean>());
    private static long lastEnsureAt;

    /**
     * Makes sure the entry exists in the target group.
     *
     * @param notify whether to call {@code notifyItemChanged} (not safe inside bind)
     */
    private static boolean ensure(Object adapter, boolean notify) {
        List<Object> groups = menuGroups(adapter);
        if (groups == null || groups.isEmpty()) {
            return false;
        }
        if (containsOurItem(groups)) {
            return true;
        }
        int index = pickGroup(groups);
        if (index < 0) {
            return false;
        }
        Object group = groups.get(index);
        Object item = buildItem(FbaRuntime.context());
        if (item == null) {
            return false;
        }
        if (!appendItem(group, item)) {
            return false;
        }
        if (notify) {
            try {
                Reflect.callMethod(adapter, "notifyItemChanged", index);
            } catch (Throwable t) {
                FbaLog.d("notifyItemChanged failed: " + t);
            }
        }
        return true;
    }

    private static Object findAdapter(View root) {
        List<View> stack = new ArrayList<>();
        stack.add(root);
        while (!stack.isEmpty()) {
            View view = stack.remove(stack.size() - 1);
            if (view.getClass().getName().endsWith("RecyclerView")) {
                try {
                    Object adapter = Reflect.callMethod(view, "getAdapter");
                    if (adapter != null && menuGroups(adapter) != null) {
                        return adapter;
                    }
                } catch (Throwable ignore) {
                    // not a recycler we can use
                }
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    stack.add(group.getChildAt(i));
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> menuGroups(Object adapter) {
        for (Class<?> c = adapter.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                try {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object value = field.get(adapter);
                    if (!(value instanceof List)) {
                        continue;
                    }
                    List<Object> list = (List<Object>) value;
                    if (list.isEmpty()) {
                        continue;
                    }
                    Object first = list.get(0);
                    if (first != null && GROUP.equals(first.getClass().getName())) {
                        return list;
                    }
                } catch (Throwable ignore) {
                    // try next field
                }
            }
        }
        return null;
    }

    private static boolean containsOurItem(List<Object> groups) {
        for (Object group : groups) {
            for (Object item : itemsOf(group)) {
                if (SETTINGS_URI.equals(field(item, "uri"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Index of the group that should receive the entry. */
    private static int pickGroup(List<Object> groups) {
        int fallback = -1;
        for (int i = 0; i < groups.size(); i++) {
            Object group = groups.get(i);
            String title = String.valueOf(field(group, "title"));
            for (Object item : itemsOf(group)) {
                String itemTitle = String.valueOf(field(item, "title"));
                if (itemTitle.contains("更多服务")) {
                    return i;
                }
            }
            if (title.contains("更多服务")) {
                return i;
            }
            if (i > 0 && title.contains("服务")) {
                fallback = i;
            }
        }
        if (fallback >= 0) {
            return fallback;
        }
        for (int i = groups.size() - 1; i >= 0; i--) {
            if (!itemsOf(groups.get(i)).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> itemsOf(Object group) {
        Object value = field(group, "itemList");
        return value instanceof List ? (List<Object>) value : java.util.Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private static boolean appendItem(Object group, Object item) {
        Object value = field(group, "itemList");
        if (value instanceof List) {
            try {
                ((List<Object>) value).add(item);
                return true;
            } catch (Throwable t) {
                FbaLog.e("append mine item failed", t);
                return false;
            }
        }
        try {
            List<Object> created = new ArrayList<>();
            created.add(item);
            setField(group, new String[]{"itemList"}, created);
            return true;
        } catch (Throwable t) {
            FbaLog.e("create mine item list failed", t);
            return false;
        }
    }

    private static Object buildItem(Context context) {
        if (context == null) {
            return null;
        }
        try {
            ClassLoader cl = context.getClassLoader();
            Class<?> itemClass = Reflect.findClass(ITEM, cl);
            Object item = Reflect.newInstance(itemClass);
            setField(item, new String[]{"id", "f267193id"}, ITEM_ID);
            setField(item, new String[]{"title"}, ITEM_TITLE);
            setField(item, new String[]{"uri"}, SETTINGS_URI);
            setField(item, new String[]{"needLogin"}, 0);
            setField(item, new String[]{"visible"}, 0);
            int icon = 0;
            for (String name : new String[]{"ic_mine_setting", "ic_mine_feedback"}) {
                icon = context.getResources().getIdentifier(name, "drawable",
                        context.getPackageName());
                if (icon != 0) {
                    break;
                }
            }
            if (icon != 0) {
                setField(item, new String[]{"iconResId"}, icon);
            }
            return item;
        } catch (Throwable t) {
            FbaLog.e("build mine item failed", t);
            return null;
        }
    }

    private static Object field(Object target, String name) {
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (Throwable ignore) {
                // next class or name
            }
        }
        return null;
    }

    private static void setField(Object target, String[] names, Object value) {
        for (String name : names) {
            for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    Field field = c.getDeclaredField(name);
                    field.setAccessible(true);
                    Class<?> type = field.getType();
                    if (type == long.class && value instanceof Number) {
                        field.setLong(target, ((Number) value).longValue());
                    } else if (type == int.class && value instanceof Number) {
                        field.setInt(target, ((Number) value).intValue());
                    } else if (!type.isPrimitive()) {
                        field.set(target, value);
                    }
                    return;
                } catch (NoSuchFieldException ignore) {
                    // try parent
                } catch (Throwable ignore) {
                    return;
                }
            }
        }
    }
}
