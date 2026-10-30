package com.fuck.bili.ads;

import android.content.pm.ApplicationInfo;

import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;
import com.fuck.bili.ads.ui.FbaLog;

import java.lang.reflect.Method;

/** HD playback-page relate and paused-page ad suppression, without mobile-only classes. */
public final class HdAdSniper {

    private HdAdSniper() {
    }

    public static void hook(ClassLoader loader, ApplicationInfo appInfo) {
        PlayPageAdSniper.hook(loader, appInfo);
        hookPaused(loader);
    }

    /** The HD package does not contain the mobile paused-page ad class. */
    private static void hookPaused(ClassLoader loader) {
        Class<?> panel = Reflect.findClassIfExists(
                "com.bilibili.ad.adview.videodetail.pausedpage.AdPausedPagePanel", loader);
        if (panel == null) {
            FbaLog.i("[HD] 无手机版暂停页广告类，跳过");
            return;
        }
        try {
            Method create = NativeHooks.method(panel, "onCreateView",
                    Reflect.findClass("android.view.ViewGroup", loader));
            NativeHooks.hook("HdAdSniper.Paused", create, chain -> null);
            FbaLog.i("[HD] 暂停页广告已拦截");
        } catch (Throwable error) {
            FbaLog.e("[HD] 暂停页广告拦截失败", error);
        }
    }
}
