package com.fuck.bili.ads;

import android.util.Log;

import java.util.Iterator;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class BiliCardAdSniper {
    private static final String TAG = "SearchAndCardAdSniper";

    public static void hook(ClassLoader classLoader, DexKitHelper helper) {
        try {
            XposedHelpers.findAndHookMethod(
                    helper.SEARCH_RESULT_ADAPTER_CLASS_NAME,
                    classLoader,
                    "getItemCount",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Object adapter = param.thisObject;
                            Class<?> clazz = adapter.getClass();

                            while (clazz != null && clazz != Object.class) {
                                for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                                    if (java.util.List.class.isAssignableFrom(field.getType())) {
                                        field.setAccessible(true);
                                        java.util.List<?> list = (java.util.List<?>) field.get(adapter);

                                        if (list != null && !list.isEmpty()) {
                                            for (int i = list.size() - 1; i >= 0; i--) {
                                                Object item = list.get(i);
                                                if (item == null) continue;

                                                try {
                                                    String goTo = (String) XposedHelpers.callMethod(item, "getGoTo");
                                                    String title = (String) XposedHelpers.getObjectField(item, "title");

                                                    if (goTo != null && goTo.contains("ad")) {
                                                        list.remove(i);
                                                        Log.i(TAG, "[SearchAd] 斩杀常规搜索广告 | goTo: " + goTo + " | 标题: " + title);
                                                        continue;
                                                    }
                                                    if (title != null && (title.contains("下载夸克") || title.contains("广告") || title.contains("拼多多"))) {
                                                        list.remove(i);
                                                        Log.i(TAG, "[SearchAd] 斩杀隐蔽搜索广告 | 命中关键字: " + title);
                                                    }
                                                } catch (Throwable ignored) {
                                                }
                                            }
                                        }
                                    }
                                }
                                clazz = clazz.getSuperclass();
                            }
                        }
                    }
            );
            Log.d(TAG, "[SearchAd] 搜索流 Hook 注册成功");
        } catch (Throwable e) {
            Log.e(TAG, "[SearchAd] 搜索流 Hook 失败: " + e.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod(
                    helper.HOME_CARD_ADAPTER_CLASS_NAME,
                    classLoader,
                    helper.CARD_INFO_SETTING_METHOD_NAME,
                    "java.util.List",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            List<Object> modifiedList = (List<Object>) param.args[0];
                            if (modifiedList == null || modifiedList.isEmpty()) return;

                            Iterator<Object> iterator = modifiedList.iterator();
                            int removeCount = 0;

                            while (iterator.hasNext()) {
                                Object object = iterator.next();
                                if (object == null) continue;
//                                Log.d(TAG, (String) XposedHelpers.callMethod(object,"toString"));
                                try {
                                    String bannerType = (String) XposedHelpers.getObjectField(object, "a");
                                    if (bannerType != null && bannerType.contains("ad")) {
                                        iterator.remove();
                                        removeCount++;
                                        Log.i(TAG, "[BannerAd] 剔除首页Banner广告 |  " + XposedHelpers.callMethod(object,"toString"));
                                    }
                                } catch (Throwable t) {
                                    Log.w(TAG, "[BannerAd] 读取 Banner 字段异常: " + t.getMessage());
                                }
                            }

                            if (removeCount > 0) {
                                Log.i(TAG, "[BannerAd] 本次刷新共清理了 " + removeCount + " 个广告卡片");
                            }
                        }
                    }
            );
            Log.d(TAG, "[BannerAd] 首页 Banner Hook 注册成功");
        } catch (Throwable e) {
            Log.e(TAG, "[BannerAd] 首页 Banner Hook 失败: " + e.getMessage());
        }

        // ==========================================
        // Hook 3: UI 层级文本暴力追踪 (溯源专用)
        // ==========================================
//        try {
//            XposedHelpers.findAndHookMethod(
//                    "android.widget.TextView",
//                    classLoader,
//                    "setText",
//                    CharSequence.class,
//                    new XC_MethodHook() {
//                        @Override
//                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                            CharSequence text = (CharSequence) param.args[0];
//                            if (text != null) {
//                                String str = text.toString();
//                                // 只拦截高度可疑的广告文本，减少日志刷屏
//                                if (str.contains("微信") || str.contains("小程序") || str.contains("王者万象")||str.contains("乐高")||str.contains("可乐")) {
//                                    Log.w(TAG, "[Trace] 发现可疑广告文本渲染: [" + str + "]", new Throwable("Text Tracing Stack"));
//                                }
//                            }
//                        }
//                    }
//            );
//            Log.d(TAG, "[Trace] 文本追踪 Hook 注册成功");
//        } catch (Throwable e) {
//            Log.e(TAG, "[Trace] 文本追踪 Hook 失败: " + e.getMessage());
//        }

    }
}