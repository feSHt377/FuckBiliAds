package com.fuck.bili.ads.core;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Host process utilities. This is not a compatibility layer for the legacy
 * Xposed API: it only resolves the current {@link Application} and process name
 * through plain Java reflection.
 */
public final class Host {

    private static volatile Application application;

    private Host() {
    }

    /** Current host application, or {@code null} if called too early. */
    public static Application currentApplication() {
        Application local = application;
        if (local != null) {
            return local;
        }
        local = fromActivityThread("currentApplication");
        if (local != null) {
            application = local;
        }
        return local;
    }

    /** Current process name; empty string when it cannot be resolved. */
    public static String processName() {
        Application application = currentApplication();
        if (application != null && Build.VERSION.SDK_INT >= 28) {
            try {
                String name = application.getProcessName();
                if (name != null) {
                    return name;
                }
            } catch (Throwable ignored) {
                // Stay safe on OEM forks.
            }
        }
        Object name = callActivityThread("currentProcessName");
        return name == null ? "" : String.valueOf(name);
    }

    private static Application fromActivityThread(String methodName) {
        Object value = callActivityThread(methodName);
        return value instanceof Application ? (Application) value : null;
    }

    private static Object callActivityThread(String methodName) {
        try {
            Class<?> thread = Class.forName("android.app.ActivityThread");
            Method method = thread.getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
