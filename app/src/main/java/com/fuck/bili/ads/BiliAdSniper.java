package com.fuck.bili.ads;

import android.view.ViewGroup;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;

public class BiliAdSniper {

    private static final String TARGET_FACTORY = "com.bilibili.ad.adview.videodetail.relate.VideoRelateAdViewFactory";
    private static final String TARGET_METHOD = "createViewHolder";

    public static void hook(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    TARGET_FACTORY,
                    classLoader,
                    TARGET_METHOD,
                    ViewGroup.class,  // 参数1: viewGroup
                    int.class,        // 参数2: i12 (type)
                    boolean.class,    // 参数3: z12
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            // 直接短路原逻辑，强行返回 null
                            // 这会触发 RelateCMComponent 内部生成没有任何大小的 Space 占位符
                            return null;
                        }
                    }
            );
        } catch (Throwable t) {
            // 记录异常，正常情况不会触发
        }
    }
}