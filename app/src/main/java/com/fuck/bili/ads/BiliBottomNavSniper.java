package com.fuck.bili.ads;

import android.util.Log;
import java.lang.reflect.Field;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class BiliBottomNavSniper {

    private static final String TAG = "BiliBottomNavTracker";
    private static final String TARGET_CLASS = "tv.danmaku.bili.ui.main2.basic.BaseMainFrameFragment";

    public static void hook(final ClassLoader classLoader) {
        try {
            final Class<?> targetClass = XposedHelpers.findClass(TARGET_CLASS, classLoader);
            Log.d(TAG, "Successfully found BaseMainFrameFragment. Injecting data filters...");

            XC_MethodHook listCleanerHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    // 传入 targetClass 确保我们扫描的是父类的字段
                    cleanBottomNavList(param.thisObject, targetClass, param.method.getName() + " (before)");
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    cleanBottomNavList(param.thisObject, targetClass, param.method.getName() + " (after)");
                }
            };

            String[] targetMethods = {"onCreate", "onViewCreated", "onActivityCreated"};

            for (String methodName : targetMethods) {
                try {
                    XposedBridge.hookAllMethods(targetClass, methodName, listCleanerHook);
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to hook method: " + methodName, t);
                }
            }

            Log.d(TAG, "Bottom Navigation data filters applied successfully.");

        } catch (Throwable t) {
            Log.e(TAG, "Failed to initialize BaseMainFrameFragment hook.", t);
        }
    }

    /**
     * 核心修复：直接扫描指定的 baseClass 的声明字段，而不是实例运行时的 getClass()
     */
    private static void cleanBottomNavList(Object fragmentInstance, Class<?> baseClass, String phase) {
        if (fragmentInstance == null || baseClass == null) return;

        try {
            // 这里使用 baseClass.getDeclaredFields()，确保能拿到 BaseMainFrameFragment 里的 f493935a0
            for (Field field : baseClass.getDeclaredFields()) {
                if (List.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    List<?> list = (List<?>) field.get(fragmentInstance);

                    if (list != null && !list.isEmpty()) {
                        boolean hasRemoved = false;

                        for (int i = list.size() - 1; i >= 0; i--) {
                            Object item = list.get(i);
                            if (isUnwantedTab(item)) {
                                Log.d(TAG, "Intercepted and removed unwanted tab at index [" + i + "] during " + phase);
                                list.remove(i);
                                hasRemoved = true;
                            }
                        }

                        if (hasRemoved) {
                            Log.d(TAG, "List cleanup complete during " + phase + ". Remaining tabs: " + list.size());
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error while cleaning tab list during " + phase, t);
        }
    }

    private static boolean isUnwantedTab(Object obj) {
        if (obj == null) return false;

        try {
            for (Field field : obj.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object val = field.get(obj);

                if (val instanceof String) {
                    if (matchesKeyword((String) val)) return true;
                } else if (val != null && !val.getClass().isPrimitive() && !val.getClass().getName().startsWith("java.")) {
                    for (Field innerField : val.getClass().getDeclaredFields()) {
                        innerField.setAccessible(true);
                        Object innerVal = innerField.get(val);
                        if (innerVal instanceof String) {
                            if (matchesKeyword((String) innerVal)) return true;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // 忽略反射权限异常
        }
        return false;
    }

    private static boolean matchesKeyword(String str) {
        if (str == null) return false;
        return str.contains("uper/center_plus") ||
                str.contains("mall/home") ||
                str.contains("会员购") ||
                str.contains("发布");
    }
}