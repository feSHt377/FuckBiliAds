package com.fuck.bili.ads;

import android.content.Context;
import android.widget.Space;

import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;
import com.fuck.bili.ads.ui.FbaLog;

import java.lang.reflect.Method;

/** HD playback-page relate and paused-page ad suppression, without mobile-only classes. */
public final class HdAdSniper {

    private HdAdSniper() {
    }

    public static void hook(ClassLoader loader) {
        hookRelate(loader);
        hookPaused(loader);
    }

    /** Creates the same empty component wrapper the host uses for unavailable ads. */
    private static void hookRelate(ClassLoader loader) {
        try {
            Class<?> type = Reflect.findClass(
                    "com.bilibili.ship.theseus.united.page.intro.module.relate.ad.RelateCMComponent", loader);
            Method create = NativeHooks.method(type, "b", Context.class,
                    Reflect.findClass("android.view.ViewGroup", loader));
            Class<?> wrapper = Reflect.findClass(
                    "com.bilibili.ship.theseus.united.page.intro.module.relate.ad.a", loader);
            Class<?> empty = Reflect.findClass("ma.c", loader);
            NativeHooks.hook("HdAdSniper.Relate", create, chain -> {
                Context context = (Context) chain.getArg(0);
                return Reflect.newInstance(wrapper, Reflect.newInstance(empty, new Space(context)));
            });
            FbaLog.i("[HD] 播放页关联推广已拦截");
        } catch (Throwable error) {
            FbaLog.e("[HD] 播放页关联推广拦截失败", error);
        }
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
