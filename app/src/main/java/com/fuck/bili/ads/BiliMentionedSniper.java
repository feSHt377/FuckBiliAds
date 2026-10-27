package com.fuck.bili.ads;

import android.content.Context;
import android.util.Log;
import android.widget.Space;

import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;

import java.lang.reflect.Method;

public class BiliMentionedSniper {

    private static final String TAG = "BiliMentionedTracker";

    public static void hook(ClassLoader classLoader, DexKitHelper helper) {
        try {
            Class<?> targetClass = Reflect.findClass(helper.VIDEO_MENTIONED_COMPONENT_CLASS_NAME, classLoader);
            Class<?> componentBClass = Reflect.findClass(helper.UI_COMPONENT_B_CLASS_NAME, classLoader);
            Class<?> unitClass = Reflect.findClass("kotlin.Unit", classLoader);
            Object unitInstance = Reflect.getStaticObjectField(unitClass, "INSTANCE");

            Log.d(TAG, "Target classes resolved. Scanning methods...");

            for (Method method : targetClass.getDeclaredMethods()) {
                final String methodName = method.getName();

                if ("createViewEntry".equals(methodName)) {
                    // Replace all overloads of createViewEntry
                    NativeHooks.hook("BiliMentionedSniper.createViewEntry", method, chain -> {
                        Log.d(TAG, "Intercepted: createViewEntry. Returning Space wrapper.");
                        try {
                            Context context = (Context) chain.getArg(0);
                            Space space = new Space(context);
                            return Reflect.newInstance(componentBClass, space);
                        } catch (Throwable t) {
                            Log.e(TAG, "Failed inside createViewEntry hook.", t);
                            return null;
                        }
                    });
                } else if ("bindToView".equals(methodName)) {
                    // Replace all overloads (including Object bridge methods) of bindToView
                    NativeHooks.hook("BiliMentionedSniper.bindToView", method, chain -> {
                        Log.d(TAG, "Intercepted: bindToView. Blocking execution to prevent ClassCastException.");
                        return unitInstance;
                    });
                } else {
                    // Log all other methods in this class to catch any unexpected lifecycle calls
                    NativeHooks.hook("BiliMentionedSniper." + methodName, method, chain -> {
                        Log.d(TAG, "Enter: " + methodName);
                        try {
                            Object result = chain.proceed();
                            Log.d(TAG, "Exit: " + methodName);
                            return result;
                        } catch (Throwable t) {
                            Log.e(TAG, "CRASH DETECTED inside: " + methodName, t);
                            throw t;
                        }
                    });
                }
            }
            Log.d(TAG, "All methods hooked successfully.");
        } catch (Throwable t) {
            Log.e(TAG, "Critical failure during hook initialization.", t);
        }
    }
}
