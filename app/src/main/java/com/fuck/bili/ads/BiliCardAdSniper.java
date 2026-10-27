package com.fuck.bili.ads;

import android.util.Log;

import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

public class BiliCardAdSniper {
    private static final String TAG = "SearchAndCardAdSniper";

    public static void hook(ClassLoader classLoader, DexKitHelper helper) {
        try {
            Class<?> adapterClass = Reflect.findClass(helper.SEARCH_RESULT_ADAPTER_CLASS_NAME, classLoader);
            Method getItemCount = NativeHooks.method(adapterClass, "getItemCount");
            NativeHooks.hook("BiliCardAdSniper.SearchGetItemCount", getItemCount, chain -> {
                Object adapter = chain.getThisObject();
                cleanSearchList(adapter);
                return chain.proceed();
            });
            Log.d(TAG, "[SearchAd] 搜索流 Hook 注册成功");
        } catch (Throwable e) {
            Log.e(TAG, "[SearchAd] 搜索流 Hook 失败: " + e.getMessage());
        }

        try {
            Class<?> adapterClass = Reflect.findClass(helper.HOME_CARD_ADAPTER_CLASS_NAME, classLoader);
            Method setCards = NativeHooks.method(adapterClass, helper.CARD_INFO_SETTING_METHOD_NAME, List.class);
            NativeHooks.hook("BiliCardAdSniper.BannerSetCards", setCards, chain -> {
                cleanBannerList((List<Object>) chain.getArg(0));
                return chain.proceed();
            });
            Log.d(TAG, "[BannerAd] 首页 Banner Hook 注册成功");
        } catch (Throwable e) {
            Log.e(TAG, "[BannerAd] 首页 Banner Hook 失败: " + e.getMessage());
        }
    }

    private static void cleanSearchList(Object adapter) {
        Class<?> clazz = adapter.getClass();
        while (clazz != null && clazz != Object.class) {
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                if (!List.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                List<?> list;
                try {
                    list = (List<?>) field.get(adapter);
                } catch (Throwable t) {
                    Log.d(TAG, "[SearchAd] read list field failed: " + t);
                    continue;
                }

                if (list == null || list.isEmpty()) continue;
                for (int i = list.size() - 1; i >= 0; i--) {
                    Object item = list.get(i);
                    if (item == null) continue;

                    try {
                        String goTo = (String) Reflect.callMethod(item, "getGoTo");
                        String title = (String) Reflect.getObjectField(item, "title");

                        if (goTo != null && goTo.contains("ad")) {
                            list.remove(i);
                            Log.i(TAG, "[SearchAd] 斩杀常规搜索广告 | goTo: " + goTo + " | 标题: " + title);
                            continue;
                        }
                        if (title != null && (title.contains("下载夸克") || title.contains("广告") || title.contains("拼多多"))) {
                            list.remove(i);
                            Log.i(TAG, "[SearchAd] 斩杀隐蔽搜索广告 | 命中关键字: " + title);
                        }
                    } catch (Throwable t) {
                        Log.d(TAG, "[SearchAd] inspect search item failed: " + t);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private static void cleanBannerList(List<Object> modifiedList) {
        if (modifiedList == null || modifiedList.isEmpty()) return;

        Iterator<Object> iterator = modifiedList.iterator();
        int removeCount = 0;

        while (iterator.hasNext()) {
            Object object = iterator.next();
            if (object == null) continue;
            try {
                String bannerType = (String) Reflect.getObjectField(object, "a");
                if (bannerType != null && bannerType.contains("ad")) {
                    iterator.remove();
                    removeCount++;
                    Log.i(TAG, "[BannerAd] 剔除首页Banner广告 |  " + Reflect.callMethod(object, "toString"));
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
