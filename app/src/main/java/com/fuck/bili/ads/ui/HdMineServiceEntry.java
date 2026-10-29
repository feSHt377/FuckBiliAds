package com.fuck.bili.ads.ui;

import android.content.Context;
import android.os.Handler;

import com.fuck.bili.ads.core.Host;
import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Adds the settings item to HD's flat Mine service list and its page manager. */
public final class HdMineServiceEntry {

    private static final String FRAGMENT = "tv.danmaku.bilibilihd.ui.main.mine.HdHomeUserCenterFragment";
    private static final String ITEM = "com.bilibili.lib.homepage.mine.MenuGroup$Item";
    private static final String SETTINGS_URI = "bilibili://fuckbiliads/settings";
    private static final long ITEM_ID = 0x4642414CL;

    private static volatile boolean installed;
    private static volatile int attempts;
    private static final java.util.Set<Class<?>> CLICK_HOOKED =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<Class<?>, Boolean>());

    private HdMineServiceEntry() {
    }

    public static void hook(ClassLoader loader) {
        if (!Host.isHd()) {
            return;
        }
        try {
            Class<?> fragment = Reflect.findClass(FRAGMENT, loader);
            NativeHooks.hookAllMethods("HdMineServiceEntry.onViewCreated", fragment, "onViewCreated",
                    chain -> {
                        Object result = chain.proceed();
                        schedule(chain.getThisObject());
                        return result;
                    });
            NativeHooks.hookAllMethods("HdMineServiceEntry.onResume", fragment, "onResume",
                    chain -> {
                        Object result = chain.proceed();
                        schedule(chain.getThisObject());
                        return result;
                    });
            hookPageManager(loader);
            hookDirectItemClick(loader);
            FbaRuntime.mark("设置入口", true);
            FbaLog.i("[HD] 我的页设置入口 Hook 已安装");
        } catch (Throwable error) {
            FbaLog.e("[HD] 我的页设置入口安装失败", error);
        }
    }

    /** Registers our URI in the same MinePageManager map used by host entries. */
    private static void hookPageManager(ClassLoader loader) {
        try {
            Class<?> manager = Reflect.findClass("tv.danmaku.bili.ui.main2.mine.b", loader);
            Class<?> base = Reflect.findClass("com.bilibili.lib.ui.BaseFragment", loader);
            NativeHooks.hookAllMethods("HdMineServiceEntry.PageManagerRegister", manager, "b", chain -> {
                Object item = null;
                for (Object arg : chain.getArgs()) {
                    if (arg != null && ITEM.equals(arg.getClass().getName())) {
                        item = arg;
                        break;
                    }
                }
                if (item != null && SETTINGS_URI.equals(Reflect.tryGetObjectField(item, "uri"))) {
                    FbaLog.i("[HD] 打开模块设置");
                    Object fragment = chain.getArg(0);
                    Context context = contextOf(fragment);
                    FbaSettings.show(context);
                    return null;
                }
                return chain.proceed();
            });
            FbaLog.i("[HD] 设置项路由已安装");
        } catch (Throwable error) {
            FbaLog.e("[HD] 设置项路由安装失败", error);
        }
    }

    /** Intercepts the actual HD item callback, not just registration. */
    private static void hookDirectItemClick(ClassLoader loader) {
        try {
            Class<?> callback = Reflect.findClass(FRAGMENT + "$h", loader);
            Class<?> item = Reflect.findClass(ITEM, loader);
            NativeHooks.hookAllMethods("HdMineServiceEntry.DirectItemClick", callback, "a", chain -> {
                Object clicked = chain.getArg(0);
                if (clicked != null && SETTINGS_URI.equals(Reflect.tryGetObjectField(clicked, "uri"))) {
                    Object listener = chain.getThisObject();
                    Context context = FbaRuntime.context();
                    Object owner = Reflect.tryGetObjectField(listener, "this$0");
                    Object activity = Reflect.tryCallMethod(owner, "getActivity");
                    if (activity instanceof Context) {
                        context = (Context) activity;
                    }
                    FbaLog.i("[HD] 设置项点击，打开模块设置");
                    FbaSettings.show(context);
                    return null;
                }
                return chain.proceed();
            });
            FbaLog.i("[HD] 设置项直接点击回调已安装");
        } catch (Throwable error) {
            FbaLog.e("[HD] 设置项直接点击回调安装失败", error);
        }
    }

    private static Context contextOf(Object fragment) {
        Object activity = Reflect.tryCallMethod(fragment, "getActivity");
        return activity instanceof Context ? (Context) activity : FbaRuntime.context();
    }

    private static void inject(Object fragment) {
        if (fragment == null) {
            return;
        }
        try {
            FbaLog.d("[HD] 菜单注入尝试: " + fragment.getClass().getName());
            Object section = Reflect.tryGetObjectField(fragment, "mSectionListV2");
            if (!(section instanceof List)) {
                return;
            }
            @SuppressWarnings("unchecked")
            List<Object> items = (List<Object>) section;
            for (Object item : items) {
                if (SETTINGS_URI.equals(Reflect.tryGetObjectField(item, "uri"))) {
                    installed = true;
                    return;
                }
            }
            boolean hasOldItems = !items.isEmpty();
            Class<?> itemType = Reflect.findClass(ITEM, fragment.getClass().getClassLoader());
            Object item = Reflect.newInstance(itemType);
            set(item, new String[]{"id", "f102955id"}, ITEM_ID);
            set(item, new String[]{"title"}, "FuckBiliAds");
            set(item, new String[]{"uri"}, SETTINGS_URI);
            set(item, new String[]{"needLogin"}, 0);
            set(item, new String[]{"visible"}, 0);
            Context context = contextOf(fragment);
            if (context != null) {
                int icon = context.getResources().getIdentifier("ic_mine_setting", "drawable",
                        context.getPackageName());
                if (icon != 0) {
                    set(item, new String[]{"iconResId"}, icon);
                }
            }
            // Register before appending, matching the host's own Mine item setup.
            Object manager = Reflect.tryGetObjectField(fragment, "mPageManager");
            Object base = Reflect.tryGetObjectField(fragment, "mHdMineFragment");
            if (manager != null && base != null) {
                Reflect.callMethod(manager, "b", base, item);
            }
            int groupIndex = injectIntoMoreServices(fragment, item);
            // V2 grouped list is what the visible More Services renders; only fall
            // back to the flat list when no group accepted the entry.
            if (hasOldItems && groupIndex < 0) {
                items.add(item);
            }
            if (groupIndex < 0) {
                if (attempts++ < 40) {
                    postDelayed(() -> inject(fragment), 500L);
                }
                return;
            }
            Object adapter = Reflect.tryGetObjectField(fragment, "mAdapter");
            if (adapter != null) {
                Reflect.tryCallMethod(adapter, "notifyDataSetChanged");
            }
            Object adapterV2 = Reflect.tryGetObjectField(fragment, "mAdapterV2");
            if (adapterV2 != null) {
                Reflect.tryCallMethod(adapterV2, "notifyDataSetChanged");
            }
            installed = true;
            FbaRuntime.mark("服务入口", true);
            FbaLog.i("[HD] 我的页设置入口已注入，旧列表=" + hasOldItems
                    + "，更多服务分组=" + groupIndex);
        } catch (Throwable error) {
            FbaLog.e("[HD] 我的页设置入口注入失败", error);
        }
    }

    /** Injects into the grouped adapter used by the visible HD More Services section. */
    private static int injectIntoMoreServices(Object fragment, Object item) {
        Object model = Reflect.tryGetObjectField(fragment, "mNewStyleV2");
        Object rawGroups = Reflect.tryCallMethod(model, "a");
        if (!(rawGroups instanceof List)) {
            return -1;
        }
        @SuppressWarnings("unchecked")
        List<Object> groups = (List<Object>) rawGroups;
        int fallback = -1;
        for (int i = 0; i < groups.size(); i++) {
            Object group = groups.get(i);
            Object title = Reflect.tryCallMethod(group, "getTitle");
            Object rawItems = Reflect.tryCallMethod(group, "a");
            if (!(rawItems instanceof List)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Object> items = (List<Object>) rawItems;
            for (Object existing : items) {
                if (SETTINGS_URI.equals(Reflect.tryGetObjectField(existing, "uri"))) {
                    return i;
                }
            }
            if (String.valueOf(title).contains("更多服务")) {
                items.add(item);
                return i;
            }
            if (String.valueOf(title).contains("更多服务")) {
                fallback = i;
            }
        }
        // A missing group title is not enough to select an arbitrary service group.
        return fallback;
    }

    private static void postDelayed(Runnable action, long delayMs) {
        Handler handler = FbaRuntime.handler();
        if (handler != null) {
            handler.postDelayed(action, delayMs);
        }
    }

    private static void schedule(Object fragment) {
        attempts = 0;
        inject(fragment);
        for (int i = 1; i <= 12; i++) {
            postDelayed(() -> inject(fragment), i * 500L);
        }
    }

    private static void set(Object target, String[] names, Object value) throws Exception {
        for (String name : names) {
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    if (field.getType() == long.class && value instanceof Number) {
                        field.setLong(target, ((Number) value).longValue());
                    } else if (field.getType() == int.class && value instanceof Number) {
                        field.setInt(target, ((Number) value).intValue());
                    } else if (!field.getType().isPrimitive()) {
                        field.set(target, value);
                    }
                    return;
                } catch (NoSuchFieldException ignored) {
                    // Try the next known field name.
                }
            }
        }
    }
}
