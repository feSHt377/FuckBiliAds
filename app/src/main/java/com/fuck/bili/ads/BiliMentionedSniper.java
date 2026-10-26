package com.fuck.bili.ads;

import android.content.Context;
import android.util.Log;
import android.widget.Space;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class BiliMentionedSniper {

    private static final String TAG = "BiliMentionedTracker";

    public static void hook(ClassLoader classLoader, DexKitHelper helper) {
        try {
            Class<?> targetClass = XposedHelpers.findClass(helper.VIDEO_MENTIONED_COMPONENT_CLASS_NAME, classLoader);
            Class<?> componentBClass = XposedHelpers.findClass(helper.UI_COMPONENT_B_CLASS_NAME, classLoader);
            Class<?> unitClass = XposedHelpers.findClass("kotlin.Unit", classLoader);
            Object unitInstance = XposedHelpers.getStaticObjectField(unitClass, "INSTANCE");

            Log.d(TAG, "Target classes resolved. Scanning methods...");

            for (Method method : targetClass.getDeclaredMethods()) {
                final String methodName = method.getName();

                if ("createViewEntry".equals(methodName)) {
                    // Replace all overloads of createViewEntry
                    XposedBridge.hookMethod(method, new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            Log.d(TAG, "Intercepted: createViewEntry. Returning Space wrapper.");
                            try {
                                Context context = (Context) param.args[0];
                                Space space = new Space(context);
                                return XposedHelpers.newInstance(componentBClass, space);
                            } catch (Throwable t) {
                                Log.e(TAG, "Failed inside createViewEntry hook.", t);
                                return null;
                            }
                        }
                    });
                } else if ("bindToView".equals(methodName)) {
                    // Replace all overloads (including Object bridge methods) of bindToView
                    XposedBridge.hookMethod(method, new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            Log.d(TAG, "Intercepted: bindToView. Blocking execution to prevent ClassCastException.");
                            return unitInstance;
                        }
                    });
                } else {
                    // Log all other methods in this class to catch any unexpected lifecycle calls
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Log.d(TAG, "Enter: " + methodName);
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.hasThrowable()) {
                                Log.e(TAG, "CRASH DETECTED inside: " + methodName, param.getThrowable());
                            } else {
                                Log.d(TAG, "Exit: " + methodName);
                            }
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