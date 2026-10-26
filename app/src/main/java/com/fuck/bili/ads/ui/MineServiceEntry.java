package com.fuck.bili.ads.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Adds the module entry to 我的 → 更多服务.
 *
 * <p>The page is server-driven: {@code MenuGroup} cards carry a list of
 * {@code MenuGroup.Item} (title + icon + uri). Appending one item to the right
 * group therefore renders with the host's own grid item, and the click is
 * caught in {@code BLRouter} by its private uri.
 *
 * <p>The mine page reloads its data on every silent refresh, which throws away
 * injected items. The entry is therefore re-applied on every adapter bind and
 * on a short retry window after each resume, instead of a one-shot flag.
 */
public final class MineServiceEntry {

    private static final String TAG = FbaRuntime.TAG;

    private static final String FRAGMENT = "tv.danmaku.bili.ui.main2.mine.HomeUserCenterFragment";
    private static final String GROUP = "com.bilibili.lib.homepage.mine.MenuGroup";
    private static final String ITEM = "com.bilibili.lib.homepage.mine.MenuGroup$Item";
    private static final String ROUTER = "com.bilibili.lib.blrouter.BLRouter";

    private static final String SETTINGS_URI = "bilibili://fuckbiliads/settings";
    private static final String ITEM_TITLE = "FuckBiliAds";
    private static final long ITEM_ID = 0x4642414CL;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile boolean injectedOnce;

    private MineServiceEntry() {
    }

    public static void hook(ClassLoader classLoader) {
        boolean router = hookRouter(classLoader);
        boolean page = hookMinePage(classLoader);
        FbaRuntime.mark("设置入口", router && page);
    }

    private static boolean hookMinePage(ClassLoader classLoader) {
        try {
            final Class<?> fragment = XposedHelpers.findClass(FRAGMENT, classLoader);
            XposedBridge.hookAllMethods(fragment, "onViewCreated", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (isMineFragment(param.thisObject, fragment)) {
                        schedule(param.thisObject);
                    }
                }
            });
            XposedBridge.hookAllMethods(fragment, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (isMineFragment(param.thisObject, fragment)) {
                        schedule(param.thisObject);
                    }
                }
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

    private static boolean hookRouter(ClassLoader classLoader) {
        try {
            Class<?> router = XposedHelpers.findClass(ROUTER, classLoader);
            XposedBridge.hookAllMethods(router, "routeTo", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args.length == 0 || param.args[0] == null) {
                            return;
                        }
                        Object uri = XposedHelpers.callMethod(param.args[0], "getTargetUri");
                        if (uri == null || !SETTINGS_URI.equals(String.valueOf(uri))) {
                            return;
                        }
                        final Context context = findContext(param.args);
                        if (context != null) {
                            MAIN.post(() -> {
                                try {
                                    FbaSettings.show(context);
                                } catch (Throwable t) {
                                    FbaLog.e("settings launch failed", t);
                                }
                            });
                        }
                        param.setResult(null);
                    } catch (Throwable ignore) {
                        // never break routing
                    }
                }
            });
            FbaLog.i("router hook installed");
            return true;
        } catch (Throwable t) {
            FbaLog.e("router hook failed", t);
            return false;
        }
    }

    private static Context findContext(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Context) {
                return (Context) arg;
            }
        }
        // routeTo(RouteRequest, Fragment)
        for (Object arg : args) {
            if (arg == null || arg instanceof CharSequence) {
                continue;
            }
            try {
                Object context = XposedHelpers.callMethod(arg, "getContext");
                if (context instanceof Context) {
                    return (Context) context;
                }
            } catch (Throwable ignore) {
                // not a fragment
            }
            try {
                Object activity = XposedHelpers.callMethod(arg, "getActivity");
                if (activity instanceof Context) {
                    return (Context) activity;
                }
            } catch (Throwable ignore) {
                // not a fragment
            }
        }
        return null;
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
            Object view = XposedHelpers.callMethod(fragment, "getView");
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
            XposedBridge.hookAllMethods(type, "onBindViewHolder", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Object self = param.thisObject;
                        if (self == null) {
                            return;
                        }
                        long now = System.currentTimeMillis();
                        if (now - lastEnsureAt < 400L) {
                            return;
                        }
                        lastEnsureAt = now;
                        ensure(self, false);
                    } catch (Throwable ignore) {
                        // never disturb the list
                    }
                }
            });
            FbaLog.i("mine adapter bound: " + type.getName());
        } catch (Throwable t) {
            FbaLog.e("mine bind hook failed", t);
        }
    }

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
                XposedHelpers.callMethod(adapter, "notifyItemChanged", index);
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
                    Object adapter = XposedHelpers.callMethod(view, "getAdapter");
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
            Class<?> itemClass = XposedHelpers.findClass(ITEM, cl);
            Object item = XposedHelpers.newInstance(itemClass);
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
