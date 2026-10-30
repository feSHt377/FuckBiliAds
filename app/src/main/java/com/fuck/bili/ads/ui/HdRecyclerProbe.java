package com.fuck.bili.ads.ui;

import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Observes every {@code RecyclerView#setAdapter} call in HD.
 *
 * <p>Why a hook instead of a type check: the module ships its own copy of
 * androidx, so {@code view instanceof RecyclerView} is always false inside the
 * host (the two classes come from different class loaders). Hooking the method
 * on the host's class sidesteps the identity problem entirely.
 */
final class HdRecyclerProbe {

    private static final String TAG = "[HD][RV]";
    private static int logs;
    private static final java.util.Set<String> dumped = new java.util.HashSet<>();

    private HdRecyclerProbe() {
    }

    static void hook(ClassLoader loader) {
        try {
            Class<?> recycler = Reflect.findClass("androidx.recyclerview.widget.RecyclerView", loader);
            Class<?> adapter = Reflect.findClass("androidx.recyclerview.widget.RecyclerView$Adapter", loader);
            Method setAdapter = recycler.getDeclaredMethod("setAdapter", adapter);
            NativeHooks.hook("HdRecyclerProbe.setAdapter", setAdapter, chain -> {
                Object result = chain.proceed();
                try {
                    observe(chain.getThisObject(), chain.getArg(0));
                } catch (Throwable error) {
                    FbaLog.e(TAG + " observe failed", error);
                }
                return result;
            });
            FbaLog.i(TAG + " setAdapter 观察已安装");
        } catch (Throwable error) {
            FbaLog.e(TAG + " 安装失败", error);
        }
    }

    private static void dumpLater(final Object adapter, long delayMs) {
        android.os.Handler handler = FbaRuntime.handler();
        if (handler == null) {
            return;
        }
        handler.postDelayed(() -> {
            try {
                FbaLog.i(TAG + " late dump +" + delayMs + "ms count="
                        + Reflect.tryCallMethod(adapter, "getItemCount"));
                HdMineProbe.dumpFields(adapter, "lateAdapter", 0);
            } catch (Throwable error) {
                FbaLog.e(TAG + " late dump failed", error);
            }
        }, delayMs);
    }

    private static void observe(Object recycler, Object adapter) {
        if (adapter == null || logs > 60) {
            return;
        }
        String id = "?";
        try {
            Object idValue = Reflect.callMethod(recycler, "getId");
            if (idValue instanceof Integer) {
                Object res = Reflect.callMethod(recycler, "getResources");
                id = String.valueOf(Reflect.callMethod(res, "getResourceEntryName", idValue));
            }
        } catch (Throwable ignored) {
            // keep "?"
        }
        int count = -1;
        Object size = Reflect.tryCallMethod(adapter, "getItemCount");
        if (size instanceof Integer) {
            count = (Integer) size;
        }
        logs++;
        FbaLog.i(TAG + " id=" + id + " adapter=" + adapter.getClass().getName() + " count=" + count);
        // The left drawer adapter (mine.b) is already known; dump everything else.
        if (adapter.getClass().getName().endsWith("ui.main.mine.b") || dumped.contains(adapter.getClass().getName())) {
            return;
        }
        dumped.add(adapter.getClass().getName());
        HdMineProbe.dumpFields(adapter, "adapter[" + adapter.getClass().getSimpleName() + "]", 0);
        for (Method method : adapter.getClass().getDeclaredMethods()) {
            StringBuilder signature = new StringBuilder(TAG)
                    .append("   method ").append(method.getName()).append('(');
            Class<?>[] parameters = method.getParameterTypes();
            for (int i = 0; i < parameters.length; i++) {
                if (i > 0) {
                    signature.append(',');
                }
                signature.append(parameters[i].getSimpleName());
            }
            FbaLog.i(signature.append(") -> ").append(method.getReturnType().getSimpleName()).toString());
        }
        dumpLater(adapter, 1500);
        dumpLater(adapter, 4000);
        for (Method method : adapter.getClass().getDeclaredMethods()) {
            if (method.getParameterTypes().length != 0 || method.getReturnType() != List.class) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object value = method.invoke(adapter);
                FbaLog.i(TAG + "   accessor " + method.getName() + "() -> "
                        + HdMineProbe.describe(value));
                if (value instanceof List) {
                    List<?> list = (List<?>) value;
                    for (int i = 0; i < Math.min(list.size(), 4); i++) {
                        FbaLog.i(TAG + "     item[" + i + "] " + HdMineProbe.describeItem(list.get(i)));
                    }
                }
            } catch (Throwable ignored) {
                // next accessor
            }
        }
    }
}
