package com.fuck.bili.ads;

import android.util.Log;

import com.fuck.bili.ads.core.Host;
import com.fuck.bili.ads.ui.FbaLog;
import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;

import java.lang.reflect.Method;

public class FreeDataToastSniper {
    private static final String TAG = "FreeDataToast";

    public static void hook(ClassLoader classLoader, DexKitHelper helper) {
        Log.i(TAG, "开始注册 Hook，loader=" + classLoader);

        try {
            if (Host.isHdPackage(Host.processName())) {
                Class<?> type = Reflect.findClass(
                        "com.bilibili.ship.theseus.united.player.mediaplay.network.NetworkShowToastService", classLoader);
                int count = 0;
                for (Method method : type.getDeclaredMethods()) {
                    // HD showCellularToast(String, boolean, Continuation).
                    if (method.getParameterCount() == 3
                            && method.getParameterTypes()[0] == String.class
                            && method.getParameterTypes()[1] == boolean.class) {
                        Object unit = Reflect.getStaticObjectField(
                                Reflect.findClass("kotlin.Unit", classLoader), "INSTANCE");
                        NativeHooks.hook("FreeDataToastSniper.HD", method, chain -> unit);
                        count++;
                    }
                }
                FbaLog.i("[HD] 免流提示拦截方法=" + count);
                return;
            }
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
