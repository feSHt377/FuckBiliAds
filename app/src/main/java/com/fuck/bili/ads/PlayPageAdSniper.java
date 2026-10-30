package com.fuck.bili.ads;

import android.content.pm.ApplicationInfo;
import android.view.ViewGroup;
import android.widget.Space;

import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;
import com.fuck.bili.ads.ui.FbaLog;
import com.fuck.bili.ads.ui.FbaPrefs;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.result.ClassData;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Signature-based playback-page ad hooks shared by hosts using the Gripper API. */
public final class PlayPageAdSniper {

    private static final String UNDER_PLAYER =
            "com.bilibili.gripper.api.ad.biz.videodetail.underplayer.IAdUnderPlayer";
    private static final String UNDER_CALLBACK = UNDER_PLAYER + "Callback";
    private static final String RELATE = "com.bilibili.gripper.api.ad.biz.videodetail.relate.IAdVideoRelate";
    private static final String RELATE_ENTRY = RELATE.replace("IAdVideoRelate", "IAdRelateViewEntry");

    private PlayPageAdSniper() {
    }

    static boolean isUnderPlayerFactory(String name) {
        return "getUpperAdView".equals(name)
                || "getUpperHDView".equals(name)
                || "getUpperNestView".equals(name);
    }

    static boolean isRelateFactory(String name) {
        return "getAdRelateView".equals(name);
    }

    /** Matches descriptor structure rather than an obfuscated implementation name. */
    static boolean hasSignature(Method method, String returnType, String... parameterTypes) {
        if (!returnType.equals(method.getReturnType().getName())) {
            return false;
        }
        Class<?>[] actual = method.getParameterTypes();
        if (actual.length != parameterTypes.length) {
            return false;
        }
        for (int i = 0; i < actual.length; i++) {
            if (!parameterTypes[i].equals(actual[i].getName())) {
                return false;
            }
        }
        return true;
    }

    /** Locates implementations through stable public interfaces, never their obfuscated names. */
    public static int hook(ClassLoader loader, ApplicationInfo appInfo) {
        if (appInfo == null || appInfo.sourceDir == null) {
            FbaLog.w("[PLAY] 无法读取宿主 APK，跳过 Gripper 特征匹配");
            return 0;
        }
        int hooked = 0;
        for (String source : sources(loader, appInfo.sourceDir)) {
            try (DexKitBridge bridge = DexKitBridge.create(source)) {
                if (bridge == null) {
                    continue;
                }
                // LSPatch may keep the real host DEX in a nested original APK.
                // Continue when the outer shell has classes but no Gripper API.
                hooked += hookUnderPlayer(loader, bridge);
                hooked += hookRelate(loader, bridge);
                if (hooked > 0) {
                    break;
                }
            } catch (Throwable error) {
                FbaLog.e("[PLAY] Gripper 特征匹配失败: " + source, error);
            }
        }
        FbaLog.i("[PLAY] Gripper 接口特征 Hook 数量=" + hooked);
        return hooked;
    }

    private static int hookUnderPlayer(ClassLoader loader, DexKitBridge bridge) throws Throwable {
        Class<?> api = Reflect.findClass(UNDER_PLAYER, loader);
        Class<?> callback = Reflect.findClass(UNDER_CALLBACK, loader);
        int hooked = 0;
        for (ClassData candidate : implementations(bridge, UNDER_PLAYER)) {
            Class<?> implementation = Reflect.findClass(candidate.getName(), loader);
            for (Method method : implementation.getDeclaredMethods()) {
                if (!isUnderPlayerFactory(method.getName()) || method.getReturnType() != callback) {
                    continue;
                }
                NativeHooks.hook("PlayPageAdSniper.UnderPlayer", method, chain -> {
                    Object result = chain.proceed();
                    if (result == null || !FbaPrefs.blockPlay()) {
                        return result;
                    }
                    try {
                        Object parent = chain.getArg(0);
                        if (!(parent instanceof ViewGroup)) {
                            return result;
                        }
                        return blankUnderPlayer(result, callback,
                                new Space(((ViewGroup) parent).getContext()));
                    } catch (Throwable error) {
                        // Both callers handle a missing under-player view without creating it.
                        FbaLog.e("[PLAY] 浮层空视图包装失败，使用宿主空实现", error);
                        return null;
                    }
                });
                hooked++;
            }
        }
        FbaLog.i("[PLAY] 播放器下方/标题上方浮层 Hook 数量=" + hooked);
        return hooked;
    }

    private static int hookRelate(ClassLoader loader, DexKitBridge bridge) throws Throwable {
        int hooked = 0;
        for (ClassData candidate : implementations(bridge, RELATE)) {
            Class<?> implementation = Reflect.findClass(candidate.getName(), loader);
            for (Method method : implementation.getDeclaredMethods()) {
                if (!isRelateFactory(method.getName())
                        || !hasSignature(method, RELATE_ENTRY,
                        "android.view.ViewGroup", "int", "boolean")) {
                    continue;
                }
                NativeHooks.hook("PlayPageAdSniper.Relate", method, chain ->
                        FbaPrefs.blockPlay() ? null : chain.proceed());
                hooked++;
            }
        }
        FbaLog.i("[PLAY] 关联推广接口 Hook 数量=" + hooked);
        return hooked;
    }

    /** Reads paths supplied by the runtime class loader; no LSPatch directory is assumed. */
    private static List<String> sources(ClassLoader loader, String fallback) {
        Set<String> paths = new LinkedHashSet<>();
        for (ClassLoader current = loader; current != null; current = current.getParent()) {
            try {
                Field pathList = current.getClass().getDeclaredField("pathList");
                pathList.setAccessible(true);
                Object dexPathList = pathList.get(current);
                Field elements = dexPathList.getClass().getDeclaredField("dexElements");
                elements.setAccessible(true);
                for (Object element : (Object[]) elements.get(dexPathList)) {
                    Field dexFile = element.getClass().getDeclaredField("dexFile");
                    dexFile.setAccessible(true);
                    Object dex = dexFile.get(element);
                    if (dex == null) {
                        continue;
                    }
                    String path = (String) dex.getClass().getMethod("getName").invoke(dex);
                    if (path != null && path.endsWith(".apk") && new File(path).isFile()) {
                        paths.add(path);
                    }
                }
            } catch (Throwable ignored) {
                // A different class-loader implementation simply uses the next source.
            }
        }
        paths.add(fallback);
        return new ArrayList<>(paths);
    }

    private static List<ClassData> implementations(DexKitBridge bridge, String api) {
        return bridge.findClass(FindClass.create().matcher(
                ClassMatcher.create().addInterface(ClassMatcher.create().className(api))));
    }

    /**
     * Keeps host construction and lifecycle intact while presenting its own blank-view contract.
     * No host model, view type or obfuscated callback implementation is modified.
     */
    private static Object blankUnderPlayer(Object delegate, Class<?> callback, Space blank) {
        return Proxy.newProxyInstance(callback.getClassLoader(), new Class<?>[]{callback},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        if ("equals".equals(method.getName())) {
                            return args != null && args.length == 1 && proxy == args[0];
                        }
                        if ("hashCode".equals(method.getName())) {
                            return System.identityHashCode(proxy);
                        }
                        if ("toString".equals(method.getName())) {
                            return "FuckBiliAds blank under-player";
                        }
                    }
                    switch (method.getName()) {
                        case "getRootView":
                            return blank;
                        case "getViewHeight":
                        case "getViewColor":
                            return 0;
                        case "isBlankView":
                            return true;
                        case "isSupportAnimIn":
                        case "defaultContainerVisible":
                            return false;
                        case "getScrollBehavior":
                            if (method.getReturnType().isEnum()) {
                                try {
                                    // Hide is the host's zero-height behavior, not an implementation name.
                                    @SuppressWarnings({"rawtypes", "unchecked"})
                                    Object hide = Enum.valueOf((Class) method.getReturnType(), "Hide");
                                    return hide;
                                } catch (IllegalArgumentException ignored) {
                                    // Older interfaces can delegate their original behavior safely.
                                }
                            }
                            break;
                        default:
                            break;
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException error) {
                        throw error.getCause() == null ? error : error.getCause();
                    }
                });
    }
}
