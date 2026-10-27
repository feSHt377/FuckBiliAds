package com.fuck.bili.ads.core;

import com.fuck.bili.ads.ui.FbaLog;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * Central native-hook façade: install, register and audit every hook.
 *
 * <p>All business code must use these methods instead of calling the module's
 * {@code hook()} directly, so HookRegistry remains the single G2 audit source.
 */
public final class NativeHooks {

    private static final String TAG = "NativeHooks";

    private static volatile XposedInterface xposed;

    private NativeHooks() {
    }

    public static void init(XposedInterface api) {
        if (api == null) {
            throw new IllegalArgumentException("XposedInterface is null");
        }
        xposed = api;
    }

    public static boolean ready() {
        return xposed != null;
    }

    public interface Interceptor {
        Object intercept(XposedInterface.Chain chain) throws Throwable;
    }

    public static XposedInterface.HookHandle hook(String owner, Executable executable, Interceptor body) {
        XposedInterface api = requireApi();
        XposedInterface.Hooker hooker = body::intercept;
        XposedInterface.HookHandle handle = api.hook(executable)
                // Host exceptions from proceed() are always propagated by the
                // framework; PROTECTIVE only guards this module's hooker bugs.
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(hooker);
        HookRegistry.add(handle, executable, owner);
        FbaLog.d("[HOOK INSTALL] " + owner + " -> " + HookRegistry.signature(executable));
        return handle;
    }

    public static int hookAllMethods(String owner, Class<?> type, String name, Interceptor body) {
        int count = 0;
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                hook(owner, method, body);
                count++;
            }
        }
        if (count == 0) {
            FbaLog.w("[HOOK INSTALL] no methods: " + type.getName() + "#" + name + " (" + owner + ")");
        }
        return count;
    }

    public static int hookAllConstructors(String owner, Class<?> type, Interceptor body) {
        int count = 0;
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            hook(owner, constructor, body);
            count++;
        }
        if (count == 0) {
            FbaLog.w("[HOOK INSTALL] no constructors: " + type.getName() + " (" + owner + ")");
        }
        return count;
    }

    /** Finds a declared method by exact parameter types, matching legacy helper semantics. */
    public static Method method(Class<?> type, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameters);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // continue
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }

    private static XposedInterface requireApi() {
        XposedInterface api = xposed;
        if (api == null) {
            throw new IllegalStateException("NativeHooks not initialized");
        }
        return api;
    }
}
