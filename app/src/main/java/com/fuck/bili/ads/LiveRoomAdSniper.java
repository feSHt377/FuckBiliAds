package com.fuck.bili.ads;

import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class LiveRoomAdSniper {
    private  static final  String TAG = "LiveRoomAdSniper";
    public static  void hook(ClassLoader classLoader,DexKitHelper helper){
        try {
            XposedHelpers.findAndHookMethod(
                    helper.NATIVE_WEBVIEW_SUB_CLASS_NAME,
                    classLoader,
                    "shouldInterceptRequest",
                    android.webkit.WebView.class,
                    android.webkit.WebResourceRequest.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            android.webkit.WebResourceRequest request = (android.webkit.WebResourceRequest) param.args[1];
                            if (request != null && request.getUrl() != null) {
                                String url = request.getUrl().toString();
                                if (url.contains("nuwa") || url.contains("mortal_cultivator")) {
                                    Log.d(TAG, "Sniped by NativeWebView$d: " + url);
                                    android.webkit.WebResourceResponse emptyResponse = new android.webkit.WebResourceResponse(
                                            "image/png",
                                            "UTF-8",
                                            200,
                                            "OK",
                                            Collections.emptyMap(),
                                            new ByteArrayInputStream(new byte[0])
                                    );
                                    param.setResult(emptyResponse);
                                }
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            Log.e(TAG, "Hook failed: " + t.getMessage());
        }

    }
}
