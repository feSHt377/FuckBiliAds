package com.fuck.bili.ads.core;

import com.fuck.bili.ads.ui.FbaLog;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.libxposed.api.XposedInterface;

/**
 * Registry of every hook installed by the native libxposed implementation.
 * The dumped member set is the comparison source for migration gate G2.
 */
public final class HookRegistry {

    private static final String TAG = "HookRegistry";

    private static final List<Record> HOOKS = new CopyOnWriteArrayList<>();

    private HookRegistry() {
    }

    public static XposedInterface.HookHandle add(XposedInterface.HookHandle handle,
                                                 Executable executable, String owner) {
        HOOKS.add(new Record(handle, executable, owner));
        return handle;
    }

    public static int size() {
        return HOOKS.size();
    }

    public static List<Record> snapshot() {
        List<Record> copy = new ArrayList<>(HOOKS);
        Collections.sort(copy, new Comparator<Record>() {
            @Override
            public int compare(Record a, Record b) {
                return a.key.compareTo(b.key);
            }
        });
        return copy;
    }

    /** Stable one-line signature, used for both current and baseline dumps. */
    public static String signature(Executable executable) {
        StringBuilder sb = new StringBuilder();
        if (Modifier.isStatic(executable.getModifiers())) {
            sb.append("static ");
        }
        sb.append(executable.getDeclaringClass().getName());
        sb.append("#");
        if (executable instanceof Method) {
            sb.append(((Method) executable).getName());
        } else {
            sb.append("<init>");
        }
        sb.append('(');
        Class<?>[] types = executable.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(types[i].getName());
        }
        sb.append(')');
        return sb.toString();
    }

    public static List<String> dump() {
        List<String> lines = new ArrayList<>(HOOKS.size());
        for (Record record : snapshot()) {
            lines.add(record.key);
        }
        return lines;
    }

    /** Unique member set, which is the actual comparison unit for gate G2. */
    public static List<String> dumpUnique() {
        List<String> all = dump();
        List<String> unique = new ArrayList<>(all.size());
        for (String line : all) {
            if (!unique.contains(line)) {
                unique.add(line);
            }
        }
        return unique;
    }

    public static void dumpToLog() {
        FbaLog.i("[HOOK REGISTRY] size=" + HOOKS.size());
        for (String line : dump()) {
            FbaLog.i("[HOOK REGISTRY] " + line);
        }
    }

    /**
     * Writes {@code current-hooks.txt} once a host Application is available.
     * libxposed may call the module before Application#attach, so retry briefly
     * on the main thread instead of silently losing the G2.2 artifact.
     */
    public static void dumpToFileWhenReady() {
        dumpToFile(0);
    }

    private static void dumpToFile(final int attempt) {
        try {
            android.app.Application application = Host.currentApplication();
            if (application == null) {
                if (attempt >= 20) {
                    FbaLog.w("[HOOK REGISTRY] no application context after retries, file dump skipped");
                    return;
                }
                android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                handler.postDelayed(() -> dumpToFile(attempt + 1), 500L);
                return;
            }
            java.io.File dir = new java.io.File(application.getCacheDir(), "fuckbiliads");
            if (!dir.exists() && !dir.mkdirs()) {
                FbaLog.w("[HOOK REGISTRY] cannot create " + dir);
                return;
            }
            java.io.File file = new java.io.File(dir, "current-hooks.txt");
            java.io.BufferedWriter writer = new java.io.BufferedWriter(
                    new java.io.OutputStreamWriter(
                            new java.io.FileOutputStream(file, false), "UTF-8"));
            List<String> unique = dumpUnique();
            try {
                writer.write("# libxposed API 102 hook registry, handles=" + HOOKS.size()
                        + ", unique=" + unique.size());
                writer.newLine();
                for (String line : unique) {
                    writer.write(line);
                    writer.newLine();
                }
            } finally {
                writer.close();
            }
            FbaLog.i("[HOOK REGISTRY] written " + file.getAbsolutePath()
                    + ", handles=" + HOOKS.size() + ", unique=" + unique.size());
        } catch (Throwable t) {
            FbaLog.e("[HOOK REGISTRY] file dump failed", t);
        }
    }

    public static final class Record {
        public final XposedInterface.HookHandle handle;
        public final String key;
        public final String owner;

        Record(XposedInterface.HookHandle handle, Executable executable, String owner) {
            this.handle = handle;
            this.key = signature(executable);
            this.owner = owner;
        }
    }
}
