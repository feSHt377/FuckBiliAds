package com.fuck.bili.ads.story;

import android.content.Context;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Shows the message with Bilibili's own toast helper so it looks like the app's
 * bottom toast instead of a system toast. Falls back to the system toast when
 * the helper is missing or changed shape.
 */
final class HostToast {

    private static final String HELPER = "com.bilibili.droid.ToastHelper";

    private static Method cached;
    private static boolean resolved;

    private HostToast() {
    }

    static void show(Context context, String text) {
        if (context == null) {
            return;
        }
        try {
            Method m = helper(context);
            if (m != null) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 2) {
                    m.invoke(null, context, text);
                } else {
                    m.invoke(null, context, text, 0);
                }
                return;
            }
        } catch (Throwable ignore) {
            // fall through to the system toast
        }
        try {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignore) {
            // never disturb the host
        }
    }

    private static Method helper(Context context) {
        if (resolved) {
            return cached;
        }
        resolved = true;
        try {
            Class<?> cls = Class.forName(HELPER, false, context.getClassLoader());
            for (Method m : cls.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                Class<?>[] p = m.getParameterTypes();
                if (p.length < 2 || p.length > 3 || !Context.class.isAssignableFrom(p[0])) {
                    continue;
                }
                if (p[1] != CharSequence.class && p[1] != String.class) {
                    continue;
                }
                if (p.length == 3 && p[2] != int.class) {
                    continue;
                }
                m.setAccessible(true);
                cached = m;
                break;
            }
        } catch (Throwable ignore) {
            cached = null;
        }
        return cached;
    }
}
