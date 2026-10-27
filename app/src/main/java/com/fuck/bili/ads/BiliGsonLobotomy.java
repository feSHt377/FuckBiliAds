package com.fuck.bili.ads;

import java.lang.reflect.Method;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class BiliGsonLobotomy {

    public static void hookPegasusAd(ClassLoader classLoader, String targetClassName) {
        try {
            Class<?> targetClass = XposedHelpers.findClass(targetClassName, classLoader);

            for (Method method : targetClass.getDeclaredMethods()) {
                // 仅拦截向外输出卡片身份标识的方法
                if (method.getReturnType() == String.class) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String originalResult = (String) param.getResult();
                            if (originalResult == null) return;

                            if (originalResult.contains("ad_av") || originalResult.contains("cm_v2") || "cm".equalsIgnoreCase(originalResult)) {
                                // 只改返回值，不再动模型里的字段：
                                // 以前会把 AdInfo 置 null、把含 ad/cm 的字符串改成 "unknown"，
                                // 这些对象是宿主正在用的同一份实例，宿主后续做空判断/解析时会直接崩
                                // （小米 15 上 8.92.1 的偶发闪退就是这条链路）。
                                param.setResult("unknown");
                                MainHook.log("拦截广告卡片标识，原始值: " + originalResult);
                            }
                        }
                    });
                }
            }
            MainHook.log("主页广告底层拦截器部署完毕，目标类: " + targetClassName);

        } catch (Throwable t) {
            MainHook.log("Hook " + targetClassName + " 失败: " + t.getMessage());
        }
    }
}