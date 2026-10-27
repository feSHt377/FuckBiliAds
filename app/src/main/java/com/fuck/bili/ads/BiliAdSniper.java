package com.fuck.bili.ads;

import android.view.ViewGroup;

import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;
import com.fuck.bili.ads.ui.FbaLog;

import java.lang.reflect.Method;

public class BiliAdSniper {

    private static final String TARGET_FACTORY = "com.bilibili.ad.adview.videodetail.relate.VideoRelateAdViewFactory";
    private static final String TARGET_METHOD = "createViewHolder";

    public static void hook(ClassLoader classLoader) {
        try {
            Class<?> factoryClass = Reflect.findClass(TARGET_FACTORY, classLoader);
            Method method = NativeHooks.method(
                    factoryClass,
                    TARGET_METHOD,
                    ViewGroup.class,  // 参数1: viewGroup
                    int.class,        // 参数2: i12 (type)
                    boolean.class     // 参数3: z12
            );
            // replace 语义：不调用 proceed，直接短路原逻辑，强行返回 null
            // 这会触发 RelateCMComponent 内部生成没有任何大小的 Space 占位符
            NativeHooks.hook("BiliAdSniper", method, chain -> {
                // 直接短路原逻辑，强行返回 null
                // 这会触发 RelateCMComponent 内部生成没有任何大小的 Space 占位符
                return null;
            });
        } catch (Throwable t) {
            // 记录异常，正常情况不会触发
            FbaLog.e("BiliAdSniper hook failed: " + TARGET_FACTORY + "#" + TARGET_METHOD, t);
        }
    }
}
