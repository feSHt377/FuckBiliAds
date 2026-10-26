package com.fuck.bili.ads;

import java.lang.reflect.Field;
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
                                Object instance = param.thisObject;

                                // 实施物理摘除：清空底层广告数据块及特征字符串
                                for (Field field : targetClass.getDeclaredFields()) {
                                    field.setAccessible(true);
                                    Object fieldValue = field.get(instance);

                                    if (fieldValue != null) {
                                        if (fieldValue.getClass().getName().endsWith("AdInfo")) {
                                            field.set(instance, null);
                                        } else if (fieldValue instanceof String) {
                                            String strVal = (String) fieldValue;
                                            if (strVal.contains("ad") || strVal.contains("cm")) {
                                                field.set(instance, "unknown");
                                            }
                                        }
                                    }
                                }

                                // 篡改表面返回值
                                param.setResult("unknown");
                                MainHook.log("成功拦截并物理摧毁广告卡片实例，原始标识: " + originalResult);
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