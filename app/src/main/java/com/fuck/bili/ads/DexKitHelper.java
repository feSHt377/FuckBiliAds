package com.fuck.bili.ads;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldMatcher;
import org.luckypray.dexkit.query.matchers.FieldsMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.FieldData;
import org.luckypray.dexkit.result.MethodData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import android.content.pm.ApplicationInfo;

public class DexKitHelper {

    public String albumRecycleViewHolderClassName = "PJ0.e";
    public String publishArchiveCollectionFieldName = "a";
    public String albumSelectPageViewClassName = "OH0.X";
    public String RECYCLER_VIEW_FIELD_NAME = "f";
    public String CHRONOS_RPC_CLASS_NAME = "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.a";

    public String INTERNATIONAL_CHRONOS_RPC_CLASS_NAME = "com.bilibili.common.chronoscommon.o";
    public String INTERNATIONAL_INVOKE_METHOD_NAME = "f";
    public String DESCRIPTION_TEXTVIEW_CLASS_NAME = "Ym1.a";
    public String PEGASUS_MODEL_CLASS_NAME = "U9.h";

    // 开屏广告跳过相关的 fallback 值
    public String BASE_SPLASH_CLASS_NAME = "tv.danmaku.bili.splash.ad.page.BaseSplash";
    public String SPLASH_READY_METHOD_NAME = "k8";
    public String SPLASH_SKIP_METHOD_NAME = "Sf";

    // 新增：视频提及控件相关的 fallback 值
    public String VIDEO_MENTIONED_COMPONENT_CLASS_NAME = "com.bilibili.ship.theseus.ugc.intro.videomentioned.module.l";
    public String VIEW_ENTRY_CLASS_NAME = "com.bilibili.app.gemini.ui.UIComponent$ViewEntry";
    public String UI_COMPONENT_B_CLASS_NAME = "com.bilibili.app.gemini.ui.UIComponent$b";

    public String SEARCH_RESULT_ADAPTER_CLASS_NAME = "com.bilibili.search2.result.all.b";

    public String HOME_CARD_ADAPTER_CLASS_NAME = "ct0.c";

    public String CARD_INFO_SETTING_METHOD_NAME = "m";

    public String PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME = "g";

    public String VIP_OPEN_MEMBERSHIP_PREDICT_SCORE_METHOD_NAME = "d";

    public String VIP_PREDICT_REQUEST_CLASS_NAME = "retrofit2.h";

    public String NATIVE_WEBVIEW_SUB_CLASS_NAME = "com.bilibili.app.comm.bh.NativeWebView$d";
    //NetworkShowToastService
    public String NETWORK_SHOW_TOAST_SERVICE_CLASS_NAME = "com.bilibili.ship.theseus.united.player.mediaplay.network.m";

    public String NETWORK_SHOW_TOAST_SERVICE_METHOD_NAME = "f";

    public String REQUEST_INTERCEPTOR = "com.bilibili.app.comm.list.common.api.d";
    private static final String TAG = "dexkitHelper";

    // 开启测试模式，强制扫描并打印结果
    public final boolean IS_TESTING = BuildConfig.DEBUG;

    public void resolve(ApplicationInfo appInfo) {
        long start = System.currentTimeMillis();
        String cacheKey = cacheKeyFor(appInfo);
        File cacheFile = cacheFileFor(appInfo);
        Properties cacheProps = new Properties();
        boolean needScan = true;

        if (cacheFile != null && cacheFile.exists()) {
            try (FileInputStream fis = new FileInputStream(cacheFile)) {
                cacheProps.load(fis);
                String cachedKey = cacheProps.getProperty("cache_key");

                if (cacheKey.equals(cachedKey)) {
                    Log.d(TAG, "Hit DexKit cache. Host/module unchanged, skipping scan.");
                    albumRecycleViewHolderClassName = cacheProps.getProperty("albumRecycleViewHolderClassName", albumRecycleViewHolderClassName);
                    publishArchiveCollectionFieldName = cacheProps.getProperty("publishArchiveCollectionFieldName", publishArchiveCollectionFieldName);
                    albumSelectPageViewClassName = cacheProps.getProperty("albumSelectPageViewClassName", albumSelectPageViewClassName);
                    RECYCLER_VIEW_FIELD_NAME = cacheProps.getProperty("RECYCLER_VIEW_FIELD_NAME", RECYCLER_VIEW_FIELD_NAME);
                    CHRONOS_RPC_CLASS_NAME = cacheProps.getProperty("chronosRpcClassName", CHRONOS_RPC_CLASS_NAME);
                    DESCRIPTION_TEXTVIEW_CLASS_NAME = cacheProps.getProperty("DESCRIPTION_TEXTVIEW_CLASS_NAME", DESCRIPTION_TEXTVIEW_CLASS_NAME);
                    INTERNATIONAL_CHRONOS_RPC_CLASS_NAME = cacheProps.getProperty("INTERNATIONAL_CHRONOS_RPC_CLASS_NAME", INTERNATIONAL_CHRONOS_RPC_CLASS_NAME);
                    INTERNATIONAL_INVOKE_METHOD_NAME = cacheProps.getProperty("INTERNATIONAL_INVOKE_METHOD_NAME", INTERNATIONAL_INVOKE_METHOD_NAME);
                    PEGASUS_MODEL_CLASS_NAME = cacheProps.getProperty("PEGASUS_MODEL_CLASS_NAME", PEGASUS_MODEL_CLASS_NAME);

                    BASE_SPLASH_CLASS_NAME = cacheProps.getProperty("BASE_SPLASH_CLASS_NAME", BASE_SPLASH_CLASS_NAME);
                    SPLASH_READY_METHOD_NAME = cacheProps.getProperty("SPLASH_READY_METHOD_NAME", SPLASH_READY_METHOD_NAME);
                    SPLASH_SKIP_METHOD_NAME = cacheProps.getProperty("SPLASH_SKIP_METHOD_NAME", SPLASH_SKIP_METHOD_NAME);

                    // 读取新增的视频提及缓存
                    VIDEO_MENTIONED_COMPONENT_CLASS_NAME = cacheProps.getProperty("VIDEO_MENTIONED_COMPONENT_CLASS_NAME", VIDEO_MENTIONED_COMPONENT_CLASS_NAME);
                    UI_COMPONENT_B_CLASS_NAME = cacheProps.getProperty("UI_COMPONENT_B_CLASS_NAME", UI_COMPONENT_B_CLASS_NAME);
                    VIEW_ENTRY_CLASS_NAME = cacheProps.getProperty("VIEW_ENTRY_CLASS_NAME", VIEW_ENTRY_CLASS_NAME);
                    SEARCH_RESULT_ADAPTER_CLASS_NAME = cacheProps.getProperty("SEARCH_RESULT_ADAPTER_CLASS_NAME", SEARCH_RESULT_ADAPTER_CLASS_NAME);
                    HOME_CARD_ADAPTER_CLASS_NAME = cacheProps.getProperty("HOME_CARD_ADAPTER_CLASS_NAME", HOME_CARD_ADAPTER_CLASS_NAME);
                    CARD_INFO_SETTING_METHOD_NAME = cacheProps.getProperty("CARD_INFO_SETTING_METHOD_NAME", CARD_INFO_SETTING_METHOD_NAME);

                    PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME = cacheProps.getProperty("PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME", PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME);
                    VIP_OPEN_MEMBERSHIP_PREDICT_SCORE_METHOD_NAME = cacheProps.getProperty("VIP_OPEN_MEMBERSHIP_PREDICT_SCORE_METHOD_NAME", VIP_OPEN_MEMBERSHIP_PREDICT_SCORE_METHOD_NAME);
                    VIP_PREDICT_REQUEST_CLASS_NAME = cacheProps.getProperty("VIP_PREDICT_REQUEST_CLASS_NAME", VIP_PREDICT_REQUEST_CLASS_NAME);
                    NATIVE_WEBVIEW_SUB_CLASS_NAME = cacheProps.getProperty("NATIVE_WEBVIEW_SUB_CLASS_NAME", NATIVE_WEBVIEW_SUB_CLASS_NAME);
                    NETWORK_SHOW_TOAST_SERVICE_CLASS_NAME = cacheProps.getProperty("NETWORK_SHOW_TOAST_SERVICE_CLASS_NAME", NETWORK_SHOW_TOAST_SERVICE_CLASS_NAME);
                    NETWORK_SHOW_TOAST_SERVICE_METHOD_NAME = cacheProps.getProperty("NETWORK_SHOW_TOAST_SERVICE_METHOD_NAME", NETWORK_SHOW_TOAST_SERVICE_METHOD_NAME);
                    REQUEST_INTERCEPTOR = cacheProps.getProperty("REQUEST_INTERCEPTOR", REQUEST_INTERCEPTOR);

                    needScan = false;
                }
            } catch (Exception e) {
                Log.d(TAG, "Failed to read cache, forcing rescan: " + e.getMessage());
            }
        }
        if (needScan || IS_TESTING) {
            Log.d(TAG, "Starting DexKit deep scan...");
            try (DexKitBridge bridge = DexKitBridge.create(appInfo.sourceDir)) {
                if (bridge == null) {
                    Log.d(TAG, "DexKit initialization failed!");
                    return;
                }
                MethodMatcher matcher1 = MethodMatcher.create()
                        .name("onBindViewHolder")
                        .paramTypes("androidx.recyclerview.widget.RecyclerView$ViewHolder", "int");

                List<MethodData> result1 = bridge.findMethod(FindMethod.create().matcher(matcher1));
                if (!result1.isEmpty()) {
                    albumRecycleViewHolderClassName = result1.get(0).getClassName();
                    Log.d(TAG, "找到 AlbumRecycleViewHolder: " + albumRecycleViewHolderClassName);

                    FieldMatcher fieldMatcher = FieldMatcher.create()
                            .declaredClass(albumRecycleViewHolderClassName)
                            .type("java.util.List");

                    List<FieldData> fields = bridge.findField(FindField.create().matcher(fieldMatcher));
                    if (!fields.isEmpty()) {
                        publishArchiveCollectionFieldName = fields.get(0).getName();
                        Log.d(TAG, "找到 List 字段名: " + publishArchiveCollectionFieldName);
                    }
                }


                ClassMatcher viewBindingMatcher = ClassMatcher.create()
                        .addInterface(ClassMatcher.create().className("androidx.viewbinding.ViewBinding"))
                        .addMethod(MethodMatcher.create().name("inflate").paramTypes("android.view.LayoutInflater", "android.view.ViewGroup", "boolean"))
                        .addField(FieldMatcher.create().type("androidx.recyclerview.widget.RecyclerView"))
                        .addField(FieldMatcher.create().type("com.bilibili.magicasakura.widgets.TintConstraintLayout"))
                        .addField(FieldMatcher.create().type("com.bilibili.magicasakura.widgets.TintLinearLayout"));

                List<ClassData> result2 = bridge.findClass(FindClass.create().matcher(viewBindingMatcher));
                if (!result2.isEmpty()) {
                    albumSelectPageViewClassName = result2.get(0).getName();
                    Log.d(TAG, "找到 AlbumSelectPageView: " + albumSelectPageViewClassName);

                    FieldMatcher rvFieldMatcher = FieldMatcher.create()
                            .declaredClass(albumSelectPageViewClassName)
                            .type("androidx.recyclerview.widget.RecyclerView");

                    List<FieldData> fields = bridge.findField(FindField.create().matcher(rvFieldMatcher));
                    if (!fields.isEmpty()) {
                        RECYCLER_VIEW_FIELD_NAME = fields.get(0).getName();
                        Log.d(TAG, "找到 RecyclerView 字段名: " + RECYCLER_VIEW_FIELD_NAME);
                    }
                }

                MethodMatcher matcher3 = MethodMatcher.create()
                        .usingStrings("DmsegLoader");

                List<MethodData> result3 = bridge.findMethod(FindMethod.create().matcher(matcher3));
                if (!result3.isEmpty()) {
                    CHRONOS_RPC_CLASS_NAME = result3.get(0).getClassName();
                    Log.d(TAG, "找到 ChronosRpc: " + CHRONOS_RPC_CLASS_NAME);
                }

                List<ClassData> descriptionTextViewClassDataList = bridge.findClass(FindClass.create()
                        .matcher(ClassMatcher.create().usingStrings(
                                "UgcIntroductionComponent",
                                "onLongClick DescTagSpan"
                        ))
                );
                if (descriptionTextViewClassDataList.isEmpty()) {
                    Log.d(TAG, "DexKit fail to find descriptionTextViewClass");
                } else {
                    ClassData outerClass = getOuterClass(bridge, descriptionTextViewClassDataList.get(0));
                    if (outerClass != null) {
                        DESCRIPTION_TEXTVIEW_CLASS_NAME = outerClass.getName();
                    } else {
                        DESCRIPTION_TEXTVIEW_CLASS_NAME = descriptionTextViewClassDataList.get(0).getName();
                    }
                }

                List<ClassData> internationalChronosRPCClassDataList = bridge.findClass(FindClass.create()
                        .matcher(ClassMatcher.create().usingStrings(
                                "EnhancedChronosPackageRunner",
                                "DestroyInputSurface: chronos engine is invalid!"
                        ))
                );

                if (internationalChronosRPCClassDataList.isEmpty()) {
                    Log.d(TAG, "DexKit fail to find internationalChronosRPCClass");
                } else {
                    ClassData internationalChronosRPCClassData = getOuterClass(bridge, internationalChronosRPCClassDataList.get(0));

                    if (internationalChronosRPCClassData != null) {
                        INTERNATIONAL_CHRONOS_RPC_CLASS_NAME = internationalChronosRPCClassData.getName();

                        List<MethodData> internationalInvokeMethodDataList = bridge.findMethod(FindMethod.create()
                                .searchInClass(Collections.singleton(internationalChronosRPCClassData))
                                .matcher(MethodMatcher.create()
                                        .paramCount(2)
                                        .returnType("void")
                                        .paramTypes("java.lang.Class", "kotlin.jvm.functions.Function6")
                                )
                        );
                        if (internationalInvokeMethodDataList.isEmpty()) {
                            Log.d(TAG, "DexKit fail to find internationalInvokeMethod");
                        } else {
                            INTERNATIONAL_INVOKE_METHOD_NAME = internationalInvokeMethodDataList.get(0).getName();
                        }
                    } else {
                        Log.d(TAG, "Warning: internationalChronosRPCClassData 的外部类未找到 (可能已不再是内部类)");
                    }


                }

                ClassMatcher pegasusMatcher = ClassMatcher.create()
                        .addInterface(ClassMatcher.create().className("com.bilibili.pegasus.data.base.BasePegasusPlayerData"))
                        .addField(FieldMatcher.create().type("com.bilibili.adcommon.data.AdInfo"))
                        .addField(FieldMatcher.create().type("com.bilibili.pegasus.HolderExtra"))
                        .addField(FieldMatcher.create().type("com.bilibili.ad.adview.pegasus.data.AdMode"));

                List<ClassData> pegasusResult = bridge.findClass(FindClass.create().matcher(pegasusMatcher));

                if (!pegasusResult.isEmpty()) {
                    PEGASUS_MODEL_CLASS_NAME = pegasusResult.get(0).getName();
                } else {
                    Log.d(TAG, "❌ 未匹配到推荐流模型类，退回使用硬编码默认值: " + PEGASUS_MODEL_CLASS_NAME);
                }


                ClassMatcher mentionedMatcher = ClassMatcher.create()
                        .usingStrings("VideoMentionedModuleComponent.TabContainer (VideoMentionedModuleComponent.kt");

                List<ClassData> mentionedResult = bridge.findClass(FindClass.create().matcher(mentionedMatcher));


                if (!mentionedResult.isEmpty()) {
                    ClassData mentionedClassData = mentionedResult.get(0);
                    VIDEO_MENTIONED_COMPONENT_CLASS_NAME = mentionedClassData.getName();
                    Log.d(TAG, "Found Video Mentioned Component: " + VIDEO_MENTIONED_COMPONENT_CLASS_NAME);

                    MethodMatcher createViewEntryMatcher = MethodMatcher.create()
                            .paramTypes("android.content.Context", "android.view.ViewGroup");

                    List<MethodData> createMethods = bridge.findMethod(FindMethod.create()
                            .searchInClass(Collections.singleton(mentionedClassData))
                            .matcher(createViewEntryMatcher));

                    for (MethodData md : createMethods) {
                        String returnType = md.getReturnTypeName();
                        if (!returnType.equals("java.lang.Object")) {
                            VIEW_ENTRY_CLASS_NAME = returnType;
                            Log.d(TAG, "动态推导出 ViewEntry 接口: " + VIEW_ENTRY_CLASS_NAME);
                            break;
                        }
                    }

                    int dollarIndex = VIEW_ENTRY_CLASS_NAME.indexOf('$');
                    if (dollarIndex != -1) {
                        String outerClassPrefix = VIEW_ENTRY_CLASS_NAME.substring(0, dollarIndex + 1);

                        ClassMatcher bMatcher = ClassMatcher.create()
                                .addInterface(ClassMatcher.create().className(VIEW_ENTRY_CLASS_NAME))
                                .addMethod(MethodMatcher.create().name("<init>").paramCount(1));

                        List<ClassData> bClassList = bridge.findClass(FindClass.create().matcher(bMatcher));

                        for (ClassData bClass : bClassList) {
                            if (bClass.getName().startsWith(outerClassPrefix)) {
                                UI_COMPONENT_B_CLASS_NAME = bClass.getName();
                                Log.d(TAG, "动态推导出 ViewEntry 实现类 (原 subclass b): " + UI_COMPONENT_B_CLASS_NAME);
                                break;
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "Failed to find Video Mentioned Component, using fallback.");
                }
                ClassMatcher splashMatcher = ClassMatcher.create()
                        .usingStrings("onSplashReady， realReady = ", "showSkipButton, skip clicked");

                List<ClassData> splashClassResult = bridge.findClass(FindClass.create().matcher(splashMatcher));


                if (!splashClassResult.isEmpty()) {
                    ClassData splashClassData = splashClassResult.get(0);
                    BASE_SPLASH_CLASS_NAME = splashClassData.getName();
                    Log.d(TAG, "找到 BaseSplash 类: " + BASE_SPLASH_CLASS_NAME);

                    // 查找 onSplashReady 方法
                    MethodMatcher readyMethodMatcher = MethodMatcher.create()
                            .paramTypes("boolean")
                            .usingStrings("onSplashReady， realReady = ");

                    List<MethodData> readyMethodResult = bridge.findMethod(FindMethod.create()
                            .searchInClass(Collections.singleton(splashClassData))
                            .matcher(readyMethodMatcher));

                    if (!readyMethodResult.isEmpty()) {
                        SPLASH_READY_METHOD_NAME = readyMethodResult.get(0).getName();
                        Log.d(TAG, "找到 onSplashReady 方法: " + SPLASH_READY_METHOD_NAME);
                    }

                    // 查找 skip clicked 方法
                    MethodMatcher skipMethodMatcher = MethodMatcher.create()
                            .paramCount(0)
                            .usingStrings("showSkipButton, skip clicked");

                    List<MethodData> skipMethodResult = bridge.findMethod(FindMethod.create()
                            .searchInClass(Collections.singleton(splashClassData))
                            .matcher(skipMethodMatcher));

                    if (!skipMethodResult.isEmpty()) {
                        SPLASH_SKIP_METHOD_NAME = skipMethodResult.get(0).getName();
                        Log.d(TAG, "找到 SkipButton 方法: " + SPLASH_SKIP_METHOD_NAME);
                    }
                } else {
                    Log.d(TAG, "❌ 未匹配到 BaseSplash 类，退回使用硬编码默认值");
                }

                List<ClassData> searchResultAdapterClassDataList = bridge.findClass(FindClass.create()
                        .searchPackages("com.bilibili.search2.result.all")
                        .matcher(
                                ClassMatcher.create()
                                        .superClass("androidx.recyclerview.widget.RecyclerView$Adapter")
                                        .addMethod(
                                                MethodMatcher.create().name("getItemCount")
                                        )
                                        .addMethod(MethodMatcher.create().name("getItemViewType"))
                                        .addMethod(MethodMatcher.create().name("onCreateViewHolder"))
                                        .addMethod(MethodMatcher.create().name("onBindViewHolder"))
                        )
                );
                SEARCH_RESULT_ADAPTER_CLASS_NAME = searchResultAdapterClassDataList.get(0).getName();

                List<ClassData> homeCardAdapterClassDataList = bridge.findClass(FindClass.create()
                        .matcher(
                                ClassMatcher.create()
                                        .usingStrings(
                                                "BannerV8Data(idx=",
                                                ", cardType=",
                                                ", extraRptFields="
                                        )
                        )
                );
                HOME_CARD_ADAPTER_CLASS_NAME = homeCardAdapterClassDataList.get(0).getName();
                List<MethodData> cardInfoSettingMethodDataList = bridge.findMethod(FindMethod.create()
                        .searchInClass(homeCardAdapterClassDataList)
                        .matcher(
                                MethodMatcher.create()
                                        .paramCount(1)
                                        .paramTypes("java.util.List")
                        )
                );
                CARD_INFO_SETTING_METHOD_NAME = cardInfoSettingMethodDataList.get(0).getMethodName();


                List<MethodData> pegasusGsonParserParserMethodDataList = bridge.findMethod(FindMethod.create()
                        .searchInClass(bridge.findClass(FindClass.create()
                                .matcher(ClassMatcher.create().className("com.bilibili.pegasus.request.PegasusGsonParser"))
                        ))
                        .matcher(MethodMatcher.create()
                                .paramCount(1)
                                .paramTypes("okhttp3.ResponseBody")
                        )
                );
                PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME = pegasusGsonParserParserMethodDataList.get(0).getMethodName();

                List<MethodData> vipOpenMembershipPredictScoreMethodDataList = bridge.findMethod(FindMethod.create()
                        .searchInClass(bridge.findClass(FindClass.create()
                                .matcher(ClassMatcher.create().className("com.bilibili.tensorflow.model.mem.VipOpenMembershipPredictTFClient"))
                        ))
                        .matcher(MethodMatcher.create()
                                .paramCount(2)
                                .paramTypes("java.util.List", "java.nio.ByteBuffer")
                        )
                );
                VIP_OPEN_MEMBERSHIP_PREDICT_SCORE_METHOD_NAME = vipOpenMembershipPredictScoreMethodDataList.get(0).getMethodName();
                List<ClassData> vipPredictRequestClassDataList = bridge.findClass(FindClass.create()
                        .searchPackages("retrofit2")
                        .matcher(ClassMatcher.create()
                                .usingStrings("Response from ", " was null but response body type was declared as non-null")
                        )
                );
                VIDEO_MENTIONED_COMPONENT_CLASS_NAME = vipPredictRequestClassDataList.get(0).getName();

                List<ClassData> nativeWebViewSubClassDataList = bridge.findClass(FindClass.create()
                        .searchPackages("com.bilibili.app.comm")
                        .matcher(ClassMatcher.create()
                                // 1. 父类必须是 WebViewClient
                                .superClass("android.webkit.WebViewClient")
                                // 2. 字段总数严格等于 2 (去掉对未知类名的字段类型匹配)
                                .fieldCount(2)
                                // 3. 实现了 shouldInterceptRequest(WebView, WebResourceRequest)
                                .addMethod(MethodMatcher.create()
                                        .name("shouldInterceptRequest")
                                        .paramTypes(
                                                "android.webkit.WebView",
                                                "android.webkit.WebResourceRequest"
                                        )
                                )
                                // 4. 重写了 Ssl 错误处理（加这一层双保险，彻底排除其它无关实现）
                                .addMethod(MethodMatcher.create()
                                        .name("onReceivedSslError")
                                        .paramTypes(
                                                "android.webkit.WebView",
                                                "android.webkit.SslErrorHandler",
                                                "android.net.http.SslError"
                                        )
                                )
                        )
                );
                NATIVE_WEBVIEW_SUB_CLASS_NAME = nativeWebViewSubClassDataList.get(0).getName();
                List<ClassData> networkShowToastServiceClassDataList = bridge.findClass(FindClass.create()
                        .searchPackages("com.bilibili.ship.theseus.united.player.mediaplay.network")
                        .matcher(ClassMatcher.create()
                                .usingStrings("show cellular toast, app once: ", ", already show: ")
                        )
                );
                NETWORK_SHOW_TOAST_SERVICE_CLASS_NAME = networkShowToastServiceClassDataList.get(0).getName();

                List<MethodData> networkShowToastServiceMethodDataList = bridge.findMethod(FindMethod.create()
                        .searchInClass(networkShowToastServiceClassDataList)
                        .matcher(MethodMatcher.create()
                                .usingStrings("NetworkShowToastService", "not china sim user, skip free data guide toast")
                        )
                );
                NETWORK_SHOW_TOAST_SERVICE_METHOD_NAME = networkShowToastServiceMethodDataList.get(0).getMethodName();

                List<ClassData> requestInterceptorClassDataList =
                        bridge.findClass(FindClass.create()
                                .searchPackages("com.bilibili.app.comm.list.common.api")
                                .matcher(ClassMatcher.create()
                                        .superClass(
                                                "com.bilibili.okretro.interceptor.DefaultRequestInterceptor"
                                        )
                                        .fieldCount(1)
                                )
                        );

                if (requestInterceptorClassDataList.isEmpty())
                    Log.e(TAG, "requestInterceptorClassDataList is empty");
                else REQUEST_INTERCEPTOR = requestInterceptorClassDataList.get(0).getName();

                cacheProps.setProperty("cache_key", cacheKey);
                cacheProps.setProperty("albumRecycleViewHolderClassName", albumRecycleViewHolderClassName);
                cacheProps.setProperty("publishArchiveCollectionFieldName", publishArchiveCollectionFieldName);
                cacheProps.setProperty("albumSelectPageViewClassName", albumSelectPageViewClassName);
                cacheProps.setProperty("RECYCLER_VIEW_FIELD_NAME", RECYCLER_VIEW_FIELD_NAME);
                cacheProps.setProperty("CHRONOS_RPC_CLASS_NAME", CHRONOS_RPC_CLASS_NAME);
                cacheProps.setProperty("DESCRIPTION_TEXTVIEW_CLASS_NAME", DESCRIPTION_TEXTVIEW_CLASS_NAME);
                cacheProps.setProperty("INTERNATIONAL_CHRONOS_RPC_CLASS_NAME", INTERNATIONAL_CHRONOS_RPC_CLASS_NAME);
                cacheProps.setProperty("INTERNATIONAL_INVOKE_METHOD_NAME", INTERNATIONAL_INVOKE_METHOD_NAME);
                cacheProps.setProperty("PEGASUS_MODEL_CLASS_NAME", PEGASUS_MODEL_CLASS_NAME);

                cacheProps.setProperty("BASE_SPLASH_CLASS_NAME", BASE_SPLASH_CLASS_NAME);
                cacheProps.setProperty("SPLASH_READY_METHOD_NAME", SPLASH_READY_METHOD_NAME);
                cacheProps.setProperty("SPLASH_SKIP_METHOD_NAME", SPLASH_SKIP_METHOD_NAME);

                // 保存新增的缓存
                cacheProps.setProperty("VIDEO_MENTIONED_COMPONENT_CLASS_NAME", VIDEO_MENTIONED_COMPONENT_CLASS_NAME);
                cacheProps.setProperty("UI_COMPONENT_B_CLASS_NAME", UI_COMPONENT_B_CLASS_NAME);
                cacheProps.setProperty("VIEW_ENTRY_CLASS_NAME", VIEW_ENTRY_CLASS_NAME);
                cacheProps.setProperty("SEARCH_RESULT_ADAPTER_CLASS_NAME", SEARCH_RESULT_ADAPTER_CLASS_NAME);
                cacheProps.setProperty("HOME_CARD_ADAPTER_CLASS_NAME", HOME_CARD_ADAPTER_CLASS_NAME);
                cacheProps.setProperty("CARD_INFO_SETTING_METHOD_NAME", CARD_INFO_SETTING_METHOD_NAME);

                cacheProps.setProperty("PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME", PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME);
                cacheProps.setProperty("VIP_OPEN_MEMBERSHIP_PREDICT_SCORE_METHOD_NAME", VIP_OPEN_MEMBERSHIP_PREDICT_SCORE_METHOD_NAME);
                cacheProps.setProperty("VIP_PREDICT_REQUEST_CLASS_NAME", VIP_PREDICT_REQUEST_CLASS_NAME);
                cacheProps.setProperty("NATIVE_WEBVIEW_SUB_CLASS_NAME", NATIVE_WEBVIEW_SUB_CLASS_NAME);
                cacheProps.setProperty("NETWORK_SHOW_TOAST_SERVICE_CLASS_NAME", NETWORK_SHOW_TOAST_SERVICE_CLASS_NAME);
                cacheProps.setProperty("NETWORK_SHOW_TOAST_SERVICE_METHOD_NAME", NETWORK_SHOW_TOAST_SERVICE_METHOD_NAME);
                cacheProps.setProperty("REQUEST_INTERCEPTOR", REQUEST_INTERCEPTOR);


                cacheFile.getParentFile().mkdirs();
                // Atomic: write a sibling file first, then rename, so a second process
                // scanning in parallel can never read a half-written cache.
                File tmp = new File(cacheFile.getParentFile(), cacheFile.getName() + ".tmp");
                try (FileOutputStream fos = new FileOutputStream(tmp)) {
                    cacheProps.store(fos, "DexKit Obfuscation Cache for BiliBili");
                }
                if (!tmp.renameTo(cacheFile)) {
                    //noinspection ResultOfMethodCallIgnored
                    cacheFile.delete();
                    if (!tmp.renameTo(cacheFile)) {
                        Log.w(TAG, "cache rename failed, left " + tmp.getName());
                    }
                }
                Log.d(TAG, "DexKit scan complete in " + (System.currentTimeMillis() - start)
                        + "ms, cache saved, key=" + cacheKey);
            } catch (Exception e) {
                Log.d(TAG, "Exception during DexKit scan: " + e.getMessage());
            }
        }
    }

    /**
     * Cache identity: module build + host APK fingerprint. LSPosed 注入的 classloader
     * 拿不到 CodeSource（null），所以不能用模块 APK 的 mtime 做 key。
     */
    private static String cacheKeyFor(ApplicationInfo appInfo) {
        long apkTime = 0L;
        long apkSize = 0L;
        try {
            File apk = new File(appInfo.sourceDir);
            apkTime = apk.lastModified();
            apkSize = apk.length();
        } catch (Throwable ignore) {
            // 只影响 key 精度
        }
        return "m" + BuildConfig.VERSION_CODE + "-h" + apkTime + "-s" + apkSize;
    }

    private static File cacheFileFor(ApplicationInfo appInfo) {
        try {
            return new File(appInfo.dataDir, "cache/dexkit_hook_cache.properties");
        } catch (Throwable t) {
            return null;
        }
    }

    /** 快速判断（几毫秒）：当前宿主 + 模块构建是否已有可用缓存。 */
    public boolean cacheValid(ApplicationInfo appInfo) {
        File file = cacheFileFor(appInfo);
        if (file == null || !file.exists()) {
            return false;
        }
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
            return cacheKeyFor(appInfo).equals(props.getProperty("cache_key"));
        } catch (Throwable t) {
            return false;
        }
    }

    /** 后台线程扫描；{@code onDone} 一定回到主线程执行。 */
    public void resolveAsync(final ApplicationInfo appInfo, final Runnable onDone) {
        final Handler handler = new Handler(Looper.getMainLooper());
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    resolve(appInfo);
                } catch (Throwable t) {
                    Log.d(TAG, "async resolve failed: " + t);
                } finally {
                    if (onDone != null) {
                        handler.post(onDone);
                    }
                }
            }
        }, "fba-dexkit");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.start();
    }

    public ClassData getOuterClass(DexKitBridge bridge, ClassData innerClassData) {
        String innerClassName = innerClassData.getName();
        int dollarIndex = innerClassName.indexOf('$');
        if (dollarIndex == -1) {
            return null;
        }
        String outerClassName = innerClassName.substring(0, dollarIndex);
        return bridge.getClassData(outerClassName);
    }

    private static void listDataPrint(List<?> dataList) {
        if (dataList.isEmpty()) {
            Log.d(TAG, "error: data list is empty");
            return;
        }
        Log.d(TAG, "found " + dataList.size() + " items");
        for (Object data : dataList) {
            if (data instanceof ClassData) {
                Log.d(TAG, "found class name: " + ((ClassData) data).getName());
            } else if (data instanceof MethodData) {
                Log.d(TAG, "found method name: " + ((MethodData) data).getName());
            } else if (data instanceof FieldData) {
                Log.d(TAG, "found field name: " + ((FieldData) data).getName());
            }
        }
    }
}