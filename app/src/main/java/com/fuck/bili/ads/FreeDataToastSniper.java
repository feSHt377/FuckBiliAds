package com.fuck.bili.ads;

import android.util.Log;

import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;

import java.lang.reflect.Method;

public class FreeDataToastSniper {
    private static final String TAG = "FreeDataToast";

    public static void hook(ClassLoader classLoader, DexKitHelper helper) {
        Log.i(TAG, "开始注册 Hook，loader=" + classLoader);

        try {
            Class<?> unitClass = Reflect.findClass("kotlin.Unit", classLoader);
            final Object unitInstance = Reflect.getStaticObjectField(unitClass, "INSTANCE");
            Class<?> service = Reflect.findClass(helper.NETWORK_SHOW_TOAST_SERVICE_CLASS_NAME, classLoader);
            Class<?> continuation = Reflect.findClass(
                    "kotlin.coroutines.jvm.internal.ContinuationImpl", classLoader);
            Method method = NativeHooks.method(service,
                    helper.NETWORK_SHOW_TOAST_SERVICE_METHOD_NAME, continuation);
            NativeHooks.hook("FreeDataToastSniper." + helper.NETWORK_SHOW_TOAST_SERVICE_METHOD_NAME,
                    method, chain -> {
                        Log.i(TAG, "命中并拦截：" + chain.getExecutable());
                        return unitInstance;
                    });

            Log.i(TAG, "Hook 注册成功");
        } catch (Throwable t) {
            Log.e(TAG, "Hook 注册失败", t);
        }
    }
}
