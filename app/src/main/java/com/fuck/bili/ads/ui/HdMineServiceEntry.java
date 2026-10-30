package com.fuck.bili.ads.ui;

import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.fuck.bili.ads.core.Host;
import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Adds the module entry to the HD (tablet) 我的 page.
 *
 * <p>HD renders that page as server driven cards; each card is a wrapper object
 * holding a title plus a {@code List<MenuGroup.Item>}. The flat (uncarded)
 * layout binds the items directly instead. Both shapes are supported here.
 *
 * <p>Every class, field and method this file touches is resolved
 * <em>structurally</em>. HD is R8 obfuscated and keeps Kotlin metadata, so the
 * metadata names a decompiler restores ({@code mSectionListV2}, {@code itemList},
 * {@code title}) do not exist at runtime: name based reflection finds nothing
 * and fails silently.
 */
public final class HdMineServiceEntry {

    private static final String FRAGMENT =
            "tv.danmaku.bilibilihd.ui.main.mine.HdHomeUserCenterFragment";
    private static final String ITEM = "com.bilibili.lib.homepage.mine.MenuGroup$Item";
    /** Anchor used to locate the card that hosts the host's own settings row. */
    private static final String HOST_SETTINGS_URI = "bilibili://main/preference";
    private static final String SETTINGS_URI = "bilibili://fuckbiliads/settings";
    private static final String ITEM_TITLE = "FuckBiliAds";
    private static final String MORE_SERVICES = "更多服务";
    private static final long ITEM_ID = 0x4642414CL;

    private static final Set<Class<?>> BIND_HOOKED =
            Collections.newSetFromMap(new WeakHashMap<Class<?>, Boolean>());
    private static final Set<Class<?>> CLICK_HOOKED =
            Collections.newSetFromMap(new WeakHashMap<Class<?>, Boolean>());

    private static volatile long lastEnsureAt;
    private static volatile boolean injectedOnce;
    private static volatile int misses;

    private HdMineServiceEntry() {
    }

    public static void hook(ClassLoader loader) {
        if (!Host.isHd()) {
            return;
        }
        try {
            // 探针只在需要取证时打开（FbaPrefs.verbose），默认不介入宿主启动路径。
            if (FbaPrefs.verbose()) {
                HdRecyclerProbe.hook(loader);
            }
            Class<?> fragment = Reflect.findClass(FRAGMENT, loader);
            hookLifecycle(fragment, "onViewCreated");
            hookLifecycle(fragment, "onResume");
            int builders = hookMenuBuilders(fragment);
            FbaRuntime.mark("设置入口", true);
            FbaLog.i("[HD] 我的页设置入口 Hook 已安装，菜单构建方法=" + builders);
        } catch (Throwable error) {
            FbaLog.e("[HD] 我的页设置入口安装失败", error);
        }
    }

    /**
     * The right hand panel ("常用功能"/"更多服务" content) is a different
     * fragment; its menu lives in that fragment's own recycler.
     */
    private static void hookContentFragment(ClassLoader loader) {
        Class<?> common = Reflect.findClassIfExists(
                "tv.danmaku.bilibilihd.ui.main.mine.common.HdCommonBaseFragment", loader);
        if (common == null) {
            FbaLog.w("[HD] 未找到内容面板 Fragment");
            return;
        }
        NativeHooks.hookAllMethods("HdMineServiceEntry.common.onViewCreated", common, "onViewCreated",
                chain -> {
                    Object result = chain.proceed();
                    probeContentLater(chain.getThisObject(), 700L);
                    probeContentLater(chain.getThisObject(), 2500L);
                    probeContentLater(chain.getThisObject(), 5000L);
                    return result;
                });
        FbaLog.i("[HD] 内容面板 Hook 已安装: " + common.getName());
    }

    private static void probeContentLater(Object fragment, long delayMs) {
        Handler handler = FbaRuntime.handler();
        if (handler == null) {
            return;
        }
        handler.postDelayed(() -> HdMineProbe.dumpContent(fragment, "+" + delayMs + "ms"), delayMs);
    }

    private static void hookLifecycle(Class<?> fragment, String method) {
        NativeHooks.hookAllMethods("HdMineServiceEntry." + method, fragment, method, chain -> {
            Object result = chain.proceed();
            schedule(chain.getThisObject());
            return result;
        });
    }

    /**
     * Hooks the two private menu builders by descriptor: {@code (Context, List)}
     * rebuilds the flat list, {@code (Context, <model>)} rebuilds the cards.
     * Matching on the signature keeps this independent of the obfuscated names.
     */
    private static int hookMenuBuilders(Class<?> fragment) {
        int hooked = 0;
        for (Method method : fragment.getDeclaredMethods()) {
            if (method.getReturnType() != void.class) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != 2 || parameters[0] != Context.class) {
                continue;
            }
            Class<?> second = parameters[1];
            if (second != List.class && (second.isPrimitive() || second == String.class)) {
                continue;
            }
            NativeHooks.hook("HdMineServiceEntry.build", method, chain -> {
                Object result = chain.proceed();
                try {
                    inject(chain.getThisObject());
                } catch (Throwable error) {
                    FbaLog.e("[HD] 我的页菜单构建后注入失败", error);
                }
                return result;
            });
            hooked++;
        }
        return hooked;
    }

    /** Retries while the server driven menu is still loading. */
    private static void schedule(Object fragment) {
        inject(fragment);
        Handler handler = FbaRuntime.handler();
        if (handler == null) {
            return;
        }
        for (int i = 1; i <= 24; i++) {
            final long delay = i * 700L;
            handler.postDelayed(() -> inject(fragment), delay);
        }
    }

    /** Menu rebuilding may run off the main thread; views must not. */
    private static void inject(Object fragment) {
        if (fragment == null) {
            return;
        }
        FbaRuntime.onMain(() -> injectNow(fragment));
    }

    private static void injectNow(Object fragment) {
        try {
            Object view = Reflect.tryCallMethod(fragment, "getView");
            if (!(view instanceof View)) {
                return;
            }
            Object adapter = findMenuAdapter((View) view);
            if (adapter == null) {
                if (FbaPrefs.verbose()) {
                    HdMineProbe.dumpOnce(fragment);
                }
                if (misses++ % 8 == 0) {
                    FbaLog.d("[HD] 我的页菜单尚未就绪，第 " + misses + " 次探测");
                }
                return;
            }
            hookBind(adapter);
            if (ensure(adapter, true)) {
                if (!injectedOnce) {
                    injectedOnce = true;
                    FbaRuntime.mark("服务入口", true);
                    FbaLog.i("[HD] 我的页设置入口已注入，适配器=" + adapter.getClass().getName());
                }
            } else if (!injectedOnce && misses++ % 4 == 0) {
                FbaLog.d("[HD] 菜单已找到但条目未写入，适配器=" + adapter.getClass().getName());
            }
        } catch (Throwable error) {
            FbaLog.e("[HD] 我的页设置入口注入失败", error);
        }
    }

    // ------------------------------------------------------------------ adapter

    /** Finds the RecyclerView adapter that owns the Mine menu. */
    private static Object findMenuAdapter(View root) {
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            View view = queue.poll();
            // 不能用 instanceof：模块自带一份 androidx，类身份与宿主不同。
            if (HdMineProbe.isRecycler(view)) {
                Object adapter = Reflect.tryCallMethod(view, "getAdapter");
                if (adapter != null && (groupList(adapter) != null || flatItems(adapter) != null)) {
                    return adapter;
                }
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

    /**
     * Re-applies the entry on every bind so host refreshes keep it, and hooks
     * the row click class of whichever holders appear.
     */
    private static void hookBind(final Object adapter) {
        final Class<?> type = adapter.getClass();
        if (!BIND_HOOKED.add(type)) {
            return;
        }
        try {
            NativeHooks.hookAllMethods("HdMineServiceEntry.onBindViewHolder", type,
                    "onBindViewHolder", chain -> {
                        Object self = chain.getThisObject();
                        try {
                            long now = System.currentTimeMillis();
                            if (now - lastEnsureAt >= 400L) {
                                lastEnsureAt = now;
                                ensure(self, false);
                            }
                        } catch (Throwable error) {
                            FbaLog.e("[HD] 菜单绑定时补注入失败", error);
                        }
                        Object result = chain.proceed();
                        try {
                            hookRow(chain.getArg(0));
                        } catch (Throwable error) {
                            FbaLog.e("[HD] 菜单行 Hook 失败", error);
                        }
                        return result;
                    });
            FbaLog.i("[HD] 菜单适配器已挂载: " + type.getName());
        } catch (Throwable error) {
            FbaLog.e("[HD] 菜单适配器挂载失败: " + type.getName(), error);
        }
    }

    /** Hooks the row click and follows nested RecyclerViews (card layout). */
    private static void hookRow(Object holder) {
        if (holder == null) {
            return;
        }
        Object itemView = Reflect.tryGetObjectField(holder, "itemView");
        if (!(itemView instanceof View)) {
            return;
        }
        hookRowClick(holder);
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add((View) itemView);
        while (!queue.isEmpty()) {
            View view = queue.poll();
            if (HdMineProbe.isRecycler(view)) {
                Object nested = Reflect.tryCallMethod(view, "getAdapter");
                if (nested != null) {
                    hookBind(nested);
                }
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    queue.add(group.getChildAt(i));
                }
            }
        }
    }

    private static void hookRowClick(Object holder) {
        final Class<?> type = holder.getClass();
        if (!CLICK_HOOKED.add(type)) {
            return;
        }
        Method onClick = null;
        for (Class<?> c = type; c != null && c != Object.class && onClick == null; c = c.getSuperclass()) {
            try {
                onClick = c.getDeclaredMethod("onClick", View.class);
            } catch (Throwable ignored) {
                // keep walking
            }
        }
        if (onClick == null) {
            return;
        }
        try {
            NativeHooks.hook("HdMineServiceEntry.onClick", onClick, chain -> {
                Object item = itemField(chain.getThisObject());
                if (item == null || !SETTINGS_URI.equals(stringField(item, "uri"))) {
                    return chain.proceed();
                }
                Context context = null;
                Object view = Reflect.tryGetObjectField(chain.getThisObject(), "itemView");
                if (view instanceof View) {
                    context = ((View) view).getContext();
                }
                if (context == null) {
                    context = FbaRuntime.context();
                }
                FbaLog.i("[HD] 设置项点击，打开模块设置");
                FbaSettings.show(context);
                return null;
            });
            FbaLog.i("[HD] 设置项点击已挂载: " + type.getName());
        } catch (Throwable error) {
            FbaLog.e("[HD] 设置项点击挂载失败", error);
        }
    }

    /** The {@code MenuGroup.Item} a row holder is currently bound to. */
    private static Object itemField(Object holder) {
        for (Field field : allFields(holder.getClass())) {
            if (Modifier.isStatic(field.getModifiers()) || !isItemType(field.getType())) {
                continue;
            }
            try {
                field.setAccessible(true);
                return field.get(holder);
            } catch (Throwable ignored) {
                // next field
            }
        }
        return null;
    }

    // ------------------------------------------------------------------- models

    /**
     * Returns the card list: either an adapter field that is itself a list of
     * cards, or a wrapper object exposing such a list through a no-arg method.
     */
    @SuppressWarnings("unchecked")
    static List<Object> groupList(Object adapter) {
        for (Field field : allFields(adapter.getClass())) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            Object value = read(field, adapter);
            if (value instanceof List) {
                List<Object> list = (List<Object>) value;
                if (isGroupList(list)) {
                    return list;
                }
                continue;
            }
            if (value == null || value instanceof View || value instanceof CharSequence) {
                continue;
            }
            for (Method method : value.getClass().getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        || method.getParameterTypes().length != 0
                        || method.getReturnType() != List.class) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    Object result = method.invoke(value);
                    if (result instanceof List && isGroupList((List<Object>) result)) {
                        return (List<Object>) result;
                    }
                } catch (Throwable ignored) {
                    // try next method
                }
            }
        }
        return null;
    }

    private static boolean isGroupList(List<Object> list) {
        if (list.isEmpty()) {
            return false;
        }
        Object first = list.get(0);
        return first != null && itemsOf(first) != null;
    }

    /** The flat item list an adapter renders directly (no cards). */
    @SuppressWarnings("unchecked")
    static List<Object> flatItems(Object adapter) {
        for (Field field : allFields(adapter.getClass())) {
            if (Modifier.isStatic(field.getModifiers()) || !List.class.isAssignableFrom(field.getType())) {
                continue;
            }
            Object value = read(field, adapter);
            if (!(value instanceof List)) {
                continue;
            }
            List<Object> list = (List<Object>) value;
            if (!list.isEmpty() && isItem(list.get(0))) {
                return list;
            }
        }
        // HD 的菜单适配器把列表藏在访问器后面（例如 b#h0()），字段里没有。
        for (Method method : allMethods(adapter.getClass())) {
            if (Modifier.isStatic(method.getModifiers())
                    || method.getParameterTypes().length != 0
                    || method.getReturnType() != List.class) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object value = method.invoke(adapter);
                if (value instanceof List) {
                    List<Object> list = (List<Object>) value;
                    if (!list.isEmpty() && isItem(list.get(0))) {
                        return list;
                    }
                }
            } catch (Throwable ignored) {
                // try next accessor
            }
        }
        return null;
    }

    private static List<Method> allMethods(Class<?> start) {
        List<Method> methods = new ArrayList<>();
        for (Class<?> type = start; type != null && type != Object.class; type = type.getSuperclass()) {
            Collections.addAll(methods, type.getDeclaredMethods());
        }
        return methods;
    }

    /** Items of one card: the list field whose elements are menu items. */
    static List<Object> itemsOf(Object group) {
        return MineEntryCore.itemsOf(group, ITEM);
    }

    static String titleOf(Object group) {
        return MineEntryCore.titleOf(group);
    }

    private static boolean isItem(Object value) {
        return MineEntryCore.isItem(value, ITEM);
    }

    /** Compact card dump so a single log line explains where the entry landed. */
    private static String describe(List<Object> groups) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < groups.size(); i++) {
            List<Object> items = itemsOf(groups.get(i));
            if (i > 0) {
                builder.append(" | ");
            }
            builder.append(i).append(':').append(titleOf(groups.get(i)))
                    .append('(').append(items == null ? -1 : items.size()).append(')');
        }
        return builder.toString();
    }

    private static boolean isItemType(Class<?> type) {
        return ITEM.equals(type.getName());
    }

    // ---------------------------------------------------------------- injection

    private static boolean ensure(Object adapter, boolean notify) {
        if (adapter == null) {
            return false;
        }
        List<Object> groups = groupList(adapter);
        if (groups != null && !groups.isEmpty()) {
            if (containsOurItem(groups)) {
                return true;
            }
            int index = pickGroup(groups);
            if (index < 0) {
                return false;
            }
            Object group = groups.get(index);
            Object item = buildItem(FbaRuntime.context(), groups);
            if (item == null || !append(group, item)) {
                return false;
            }
            FbaLog.i("[HD] 设置项写入卡片 " + index + "，卡片=" + describe(groups));
            if (notify) {
                try {
                    Reflect.callMethod(adapter, "notifyItemChanged", index);
                } catch (Throwable error) {
                    FbaLog.d("[HD] notifyItemChanged 失败: " + error);
                }
            }
            return true;
        }
        List<Object> flat = flatItems(adapter);
        if (flat == null) {
            return false;
        }
        if (containsUri(flat, SETTINGS_URI)) {
            return true;
        }
        Object item = buildItem(FbaRuntime.context(), null);
        if (item == null || !flat.add(item)) {
            return false;
        }
        FbaLog.i("[HD] 设置项写入平铺列表");
        if (notify) {
            Reflect.tryCallMethod(adapter, "notifyDataSetChanged");
        }
        return true;
    }

    private static boolean containsOurItem(List<Object> groups) {
        for (Object group : groups) {
            List<Object> items = itemsOf(group);
            if (items != null && containsUri(items, SETTINGS_URI)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsUri(List<Object> items, String uri) {
        return MineEntryCore.containsUri(items, uri);
    }

    /**
     * Picks the card that should hold the entry: the card hosting the host's own
     * settings row wins, then the "更多服务" card, then the last populated card.
     */
    static int pickGroup(List<Object> groups) {
        for (int i = 0; i < groups.size(); i++) {
            List<Object> items = itemsOf(groups.get(i));
            if (items != null && containsUri(items, HOST_SETTINGS_URI)) {
                return i;
            }
        }
        for (int i = 0; i < groups.size(); i++) {
            if (titleOf(groups.get(i)).contains(MORE_SERVICES)) {
                return i;
            }
        }
        for (int i = groups.size() - 1; i >= 0; i--) {
            List<Object> items = itemsOf(groups.get(i));
            if (items != null && !items.isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private static boolean append(Object group, Object item) {
        List<Object> items = itemsOf(group);
        if (items != null) {
            try {
                items.add(item);
                return true;
            } catch (Throwable error) {
                FbaLog.e("[HD] 追加设置项失败", error);
                return false;
            }
        }
        List<Object> created = new ArrayList<>();
        created.add(item);
        for (Method method : group.getClass().getDeclaredMethods()) {
            if (method.getReturnType() != void.class
                    || method.getParameterTypes().length != 1
                    || method.getParameterTypes()[0] != List.class) {
                continue;
            }
            try {
                method.setAccessible(true);
                method.invoke(group, created);
                return true;
            } catch (Throwable ignored) {
                // try next setter
            }
        }
        return false;
    }

    private static Object buildItem(Context context, List<Object> groups) {
        Object item = MineEntryCore.buildItem(context, ITEM, ITEM_TITLE, SETTINGS_URI, ITEM_ID);
        if (item == null) {
            return null;
        }
        int icon = hostSettingsIcon(groups);
        if (icon == 0) {
            icon = context.getResources().getIdentifier("ic_mine_setting", "drawable",
                    context.getPackageName());
        }
        if (icon != 0) {
            setField(item, "iconResId", icon);
        }
        return item;
    }

    /** Reuses the icon of the host's own settings row so the entry looks native. */
    private static int hostSettingsIcon(List<Object> groups) {
        if (groups == null) {
            return 0;
        }
        for (Object group : groups) {
            List<Object> items = itemsOf(group);
            if (items == null) {
                continue;
            }
            for (Object item : items) {
                if (!HOST_SETTINGS_URI.equals(stringField(item, "uri"))) {
                    continue;
                }
                Object icon = field(item, "iconResId");
                if (icon instanceof Integer && (Integer) icon != 0) {
                    return (Integer) icon;
                }
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------ helpers
    // 通用反射 / 菜单模型逻辑统一在 MineEntryCore，手机版与 HD 不再各写一份。

    private static List<Field> allFields(Class<?> start) {
        return MineEntryCore.allFields(start);
    }

    private static Object read(Field field, Object target) {
        return MineEntryCore.read(field, target);
    }

    private static Object field(Object target, String name) {
        return MineEntryCore.field(target, name);
    }

    private static String stringField(Object target, String name) {
        return MineEntryCore.stringField(target, name);
    }

    private static void setField(Object target, String name, Object value) {
        MineEntryCore.setField(target, name, value);
    }
}
