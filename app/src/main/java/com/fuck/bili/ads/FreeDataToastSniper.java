package com.fuck.bili.ads;

import android.util.Log;

import org.luckypray.dexkit.query.matchers.ClassMatcher;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class FreeDataToastSniper {
    private static final String TAG = "FreeDataToast";

    public static void hook(ClassLoader classLoader,DexKitHelper helper) {
        Log.i(TAG, "开始注册 Hook，loader=" + classLoader);

        try {
            final Object unitInstance = XposedHelpers.getStaticObjectField(
                    XposedHelpers.findClass("kotlin.Unit", classLoader),
                    "INSTANCE"
            );

            XposedHelpers.findAndHookMethod(
                    helper.NETWORK_SHOW_TOAST_SERVICE_CLASS_NAME,
                    classLoader,
                    helper.NETWORK_SHOW_TOAST_SERVICE_METHOD_NAME,
                    "kotlin.coroutines.jvm.internal.ContinuationImpl",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Log.i(TAG, "命中并拦截：" + param.method);
                            param.setResult(unitInstance);
                        }
                    }
            );

            Log.i(TAG, "Hook 注册成功");
        } catch (Throwable t) {
            Log.e(TAG, "Hook 注册失败", t);
        }
    }
}
