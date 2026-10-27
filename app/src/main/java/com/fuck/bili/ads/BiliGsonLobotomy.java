package com.fuck.bili.ads;

import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;
import com.fuck.bili.ads.ui.FbaLog;

import java.lang.reflect.Method;

public class BiliGsonLobotomy {

    public static void hookPegasusAd(ClassLoader classLoader, String targetClassName) {
        try {
            Class<?> targetClass = Reflect.findClass(targetClassName, classLoader);
            if (isRecyclerAdapter(targetClass)) {
                // 该类的 String 方法可能被 RecyclerView 当作 viewType 使用
                // （例如 getItemViewType 返回 cm_v2:3）。把它改成 unknown 会让宿主
                // 在 createViewHolder 时抛 "type:unknown is not exist" 并崩溃。
                FbaLog.w("跳过广告标识 Hook：目标类实际是 RecyclerView.Adapter: "
                        + targetClass.getName());
                return;
            }

            for (Method method : targetClass.getDeclaredMethods()) {
                // 仅拦截向外输出卡片身份标识的方法
                if (method.getReturnType() != String.class) {
                    continue;
                }
                NativeHooks.hook("BiliGsonLobotomy." + method.getName(), method, chain -> {
                    Object result = chain.proceed();
                    if (!(result instanceof String)) {
                        return result;
                    }
                    String originalResult = (String) result;
                    // 宿主把 "cm_v2:3" 这类“标识:索引”直接当 RecyclerView viewType
                    // 使用；改成 unknown 会抛 "type:unknown is not exist"。只处理
                    // 不含冒号的纯广告标识，保留复合 viewType 原值。
                    if (shouldReplaceIdentifier(originalResult)) {
                        // 只改返回值，不再动模型里的字段：
                        // 以前会把 AdInfo 置 null、把含 ad/cm 的字符串改成 "unknown"，
                        // 这些对象是宿主正在用的同一份实例，宿主后续做空判断/解析时会直接崩
                        // （小米 15 上 8.92.1 的偶发闪退就是这条链路）。
                        FbaLog.i("拦截广告卡片标识，原始值: " + originalResult);
                        return "unknown";
                    }
                    return result;
                });
            }
            FbaLog.i("主页广告底层拦截器部署完毕，目标类: " + targetClassName);
        } catch (Throwable t) {
            FbaLog.e("Hook " + targetClassName + " 失败", t);
        }
    }

    /** Structural guard: adapters use String identifiers as RecyclerView view types. */
    private static boolean isRecyclerAdapter(Class<?> type) {
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            String name = current.getName();
            if ("androidx.recyclerview.widget.RecyclerView$Adapter".equals(name)
                    || name.startsWith("androidx.recyclerview.widget.Adapter")
                    || name.contains("RecyclerView.Adapter")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Only pure ad identifiers may be replaced. Composite values such as
     * {@code cm_v2:3} are RecyclerView view types and must stay untouched.
     */
    static boolean shouldReplaceIdentifier(String value) {
        if (value == null || value.indexOf(':') >= 0) {
            return false;
        }
        return value.contains("ad_av")
                || "cm_v2".equalsIgnoreCase(value)
                || "cm".equalsIgnoreCase(value);
    }
}
