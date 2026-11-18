package com.fuck.bili.ads;

import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;
import com.fuck.bili.ads.ui.FbaLog;
import com.fuck.bili.ads.ui.FbaPrefs;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Locale;

/**
 * 直播间 H5 广告资源拦截。
 *
 * <p>历史实现只做 {@code url.contains("nuwa")} 的子串匹配，而 {@code nuwa} 是 B站
 * 直播活动页的模块联邦平台：活动页所有资源都在
 * {@code s1.hdslb.com/bfs/live-activity/nuwa/**} 下。命中后返回「200 + image/png +
 * 空 body」，导致 {@code remoteEntry.js} 被当成图片、容器无法注册，活动页直接白屏。
 *
 * <p>现在的规则分三层收窄：
 * <ol>
 *   <li>结构性资源（js/css/json）一律放行——即使误判也最多少一张图，不会白屏；</li>
 *   <li>直播活动目录（{@code /live-activity/}）属于宿主 UI，白名单放行；</li>
 *   <li>只有明确带广告特征的资源才拦截。</li>
 * </ol>
 *
 * <p>开关改为在 hook 内运行时判断，改设置后无需重启宿主。
 */
public class LiveRoomAdSniper {
    private static final String TAG = "LiveRoomAdSniper";

    public static void hook(ClassLoader classLoader, DexKitHelper helper) {
        try {
            Class<?> type = Reflect.findClass(helper.NATIVE_WEBVIEW_SUB_CLASS_NAME, classLoader);
            Method method = NativeHooks.method(type, "shouldInterceptRequest",
                    WebView.class, WebResourceRequest.class);
            NativeHooks.hook("LiveRoomAdSniper.shouldInterceptRequest", method, chain -> {
                // 运行时判断：开关改动立即生效，无需重启宿主。
                if (!FbaPrefs.blockLive()) {
                    return chain.proceed();
                }
                WebResourceRequest request = (WebResourceRequest) chain.getArg(1);
                if (request == null || request.getUrl() == null) {
                    return chain.proceed();
                }
                String url = request.getUrl().toString();
                if (!shouldBlock(url)) {
                    return chain.proceed();
                }
                FbaLog.d("[LIVEAD] sniped: " + url);
                return new WebResourceResponse(
                        "image/png",
                        "UTF-8",
                        200,
                        "OK",
                        Collections.emptyMap(),
                        new ByteArrayInputStream(new byte[0])
                );
            });
        } catch (Throwable t) {
            FbaLog.w("LiveRoomAdSniper hook failed: " + t);
        }
    }

    /**
     * 纯逻辑判定，方便单测覆盖。
     *
     * <p>顺序很重要：先放行结构资源与活动目录，再看广告特征。宁可少拦一张图，
     * 也不能让整页 JS 变成空文件。
     */
    static boolean shouldBlock(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        int query = lower.indexOf('?');
        String path = query >= 0 ? lower.substring(0, query) : lower;

        // 1) 结构性资源一律放行：空白 JS 会让整个 H5 白屏。
        if (path.endsWith(".js") || path.endsWith(".mjs")
                || path.endsWith(".css") || path.endsWith(".json")) {
            return false;
        }
        // 2) 直播活动页目录属于宿主 UI（nuwa 模板、remoteEntry 等）。
        if (path.contains("/live-activity/")) {
            return false;
        }
        // 3) 仅拦明确带广告特征的资源。
        return path.contains("mortal_cultivator")
                || path.contains("/ad/")
                || path.contains("advert")
                || path.contains("/promotion/")
                || path.contains("feed_ad");
    }
}
