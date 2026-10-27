package com.fuck.bili.ads;

import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;

import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.Collections;

public class LiveRoomAdSniper {
    private static final String TAG = "LiveRoomAdSniper";

    public static void hook(ClassLoader classLoader, DexKitHelper helper) {
        try {
            Class<?> type = Reflect.findClass(helper.NATIVE_WEBVIEW_SUB_CLASS_NAME, classLoader);
            Method method = NativeHooks.method(type, "shouldInterceptRequest",
                    WebView.class, WebResourceRequest.class);
            NativeHooks.hook("LiveRoomAdSniper.shouldInterceptRequest", method, chain -> {
                WebResourceRequest request = (WebResourceRequest) chain.getArg(1);
                if (request != null && request.getUrl() != null) {
                    String url = request.getUrl().toString();
                    if (url.contains("nuwa") || url.contains("mortal_cultivator")) {
                        Log.d(TAG, "Sniped by NativeWebView$d: " + url);
                        return new android.webkit.WebResourceResponse(
                                "image/png",
                                "UTF-8",
                                200,
                                "OK",
                                Collections.emptyMap(),
                                new ByteArrayInputStream(new byte[0])
                        );
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            Log.e(TAG, "Hook failed: " + t.getMessage());
        }
    }
}
