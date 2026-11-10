package com.fuck.bili.ads;

import android.util.Log;

import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;
import com.fuck.bili.ads.ui.FbaPrefs;

import java.lang.reflect.Field;
import java.util.List;

public class BiliBottomNavSniper {

    private static final String TAG = "BiliBottomNavTracker";
    private static final String TARGET_CLASS = "tv.danmaku.bili.ui.main2.basic.BaseMainFrameFragment";

    /**
     * Rebuilds the host main page so a tab toggle takes effect without killing
     * the process. The tab views are inflated once per fragment, so a fresh
     * Activity (about a second) is the cheapest reliable refresh.
     */
    public static void reloadHostUi(final android.app.Activity activity) {
        if (activity == null) {
            return;
        }
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.postDelayed(() -> {
            try {
                activity.recreate();
                Log.d(TAG, "host main page recreated for tab change");
            } catch (Throwable t) {
                Log.w(TAG, "recreate failed: " + t);
            }
        }, 400L);
    }

    public static void hook(final ClassLoader classLoader) {
        try {
            final Class<?> targetClass = Reflect.findClass(TARGET_CLASS, classLoader);
            Log.d(TAG, "Successfully found BaseMainFrameFragment. Injecting data filters...");

            String[] targetMethods = {"onCreate", "onViewCreated", "onActivityCreated"};
            for (String methodName : targetMethods) {
                try {
                    NativeHooks.hookAllMethods("BiliBottomNavSniper." + methodName,
                            targetClass, methodName, chain -> {
                                // before
                                cleanBottomNavList(chain.getThisObject(), targetClass,
                                        methodName + " (before)");
                                Object result = chain.proceed();
                                // after
                                cleanBottomNavList(chain.getThisObject(), targetClass,
                                        methodName + " (after)");
                                return result;
                            });
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
        final boolean hideMall = FbaPrefs.navHideMall();
        final boolean hidePublish = FbaPrefs.navHidePublish();
        if (!hideMall && !hidePublish) {
            return false;
        }

        try {
            for (Field field : obj.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object val = field.get(obj);

                if (val instanceof String) {
                    if (matches((String) val, hideMall, hidePublish)) return true;
                } else if (val != null && !val.getClass().isPrimitive() && !val.getClass().getName().startsWith("java.")) {
                    for (Field innerField : val.getClass().getDeclaredFields()) {
                        innerField.setAccessible(true);
                        Object innerVal = innerField.get(val);
                        if (innerVal instanceof String) {
                            if (matches((String) innerVal, hideMall, hidePublish)) return true;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.d(TAG, "tab reflection failed: " + t);
        }
        return false;
    }

    /** Path keywords first (9.18.0 still uses them), Chinese labels as fallback. */
    static boolean matches(String str, boolean hideMall, boolean hidePublish) {
        if (str == null) {
            return false;
        }
        if (hidePublish && (str.contains("uper/center_plus") || str.contains("发布"))) {
            return true;
        }
        if (hideMall && (str.contains("mall/home") || str.contains("会员购"))) {
            return true;
        }
        return false;
    }
}
