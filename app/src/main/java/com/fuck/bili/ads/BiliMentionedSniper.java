package com.fuck.bili.ads;

import android.content.Context;
import android.util.Log;
import android.widget.Space;

import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;
import com.fuck.bili.ads.ui.FbaLog;

import java.lang.reflect.Method;

public class BiliMentionedSniper {

    private static final String TAG = "BiliMentionedTracker";

    /** Only the two methods that create/bind the ad component need interception. */
    static boolean shouldHook(String methodName) {
        return "createViewEntry".equals(methodName) || "bindToView".equals(methodName);
    }

    public static void hook(ClassLoader classLoader, DexKitHelper helper) {
        try {
            Class<?> targetClass = Reflect.findClass(helper.VIDEO_MENTIONED_COMPONENT_CLASS_NAME, classLoader);
            Class<?> componentBClass = Reflect.findClass(helper.UI_COMPONENT_B_CLASS_NAME, classLoader);
            Class<?> unitClass = Reflect.findClass("kotlin.Unit", classLoader);
            Object unitInstance = Reflect.getStaticObjectField(unitClass, "INSTANCE");

            int hooked = 0;
            for (Method method : targetClass.getDeclaredMethods()) {
                if (!shouldHook(method.getName())) {
                    continue;
                }
                if ("createViewEntry".equals(method.getName())) {
                    NativeHooks.hook("BiliMentionedSniper.createViewEntry", method, chain -> {
                        try {
                            Context context = (Context) chain.getArg(0);
                            Space space = new Space(context);
                            return Reflect.newInstance(componentBClass, space);
                        } catch (Throwable t) {
                            FbaLog.e("视频提及组件替换失败，已保留原组件", t);
                            return chain.proceed();
                        }
                    });
                } else {
                    // Suppress only the ad binding; every other component method is untouched.
                    NativeHooks.hook("BiliMentionedSniper.bindToView", method, chain -> unitInstance);
                }
                hooked++;
            }
            FbaLog.i("视频提及组件 Hook 完成: " + hooked + " 个必要方法（其余方法不拦截）");
        } catch (Throwable t) {
            Log.e(TAG, "Critical failure during hook initialization.", t);
        }
    }
}
