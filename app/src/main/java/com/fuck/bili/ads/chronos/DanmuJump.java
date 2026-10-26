package com.fuck.bili.ads.chronos;

import android.util.Log;


import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import com.fuck.bili.ads.DanmuSenderLookup;

public class DanmuJump {
    private static final String TAG = "DanmuJump";
    private static final boolean TEST_AUTO_JUMP = false;
    private static final long TEST_TARGET_MS = 60_000L;
    private static final java.util.concurrent.atomic.AtomicBoolean testScheduled =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static volatile java.lang.ref.WeakReference<Object> activeEngine =
            new java.lang.ref.WeakReference<>(null);

    private static volatile java.io.File voteLogDirectory;
    private static final java.util.concurrent.ExecutorService voteLogWriter =
            java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "DanmuVoteLogWriter");
                thread.setDaemon(true);
                return thread;
            });

    /** 每次提示一条摘要，每张有效票一条记录；磁盘写入不占用播放器线程。 */
    private static void appendVoteLog(String jsonText) {
        final java.io.File directory = voteLogDirectory;
        final long receivedAt = System.currentTimeMillis();
        if (directory == null) {
            Log.d(TAG, "[DanmuVoteLog] directory not ready");
            return;
        }
        voteLogWriter.execute(() -> {
            try {
                org.json.JSONObject record = new org.json.JSONObject(jsonText);
                record.put("loggedAtMs", receivedAt);
                java.text.SimpleDateFormat timestamp = new java.text.SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss.SSS Z", java.util.Locale.ROOT);
                record.put("loggedAt", timestamp.format(new java.util.Date(receivedAt)));
                for (String key : new String[]{"appearanceMs", "targetMs", "currentMs"}) {
                    if (record.has(key) && !record.isNull(key)) {
                        record.put(key.replace("Ms", "Time"),
                                formatVideoTime(record.getLong(key)));
                    }
                }
                long sentAtSeconds = record.optLong("sentAtSeconds", 0L);
                if (sentAtSeconds > 0 && sentAtSeconds <= Long.MAX_VALUE / 1000L) {
                    record.put("sentAt", timestamp.format(new java.util.Date(sentAtSeconds * 1000L)));
                }
                if (!directory.isDirectory() && !directory.mkdirs()) {
                    throw new java.io.IOException("Cannot create vote log directory");
                }
                String day = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
                        .format(new java.util.Date(receivedAt));
                java.io.File file = new java.io.File(directory, "votes-" + day + ".jsonl");
                byte[] bytes = (record.toString() + "\n")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                try (java.io.FileOutputStream output = new java.io.FileOutputStream(file, true)) {
                    output.write(bytes);
                }
            } catch (Throwable t) {
                Log.d(TAG, "[DanmuVoteLog] write failed: " + t.getClass().getName());
            }
        });
    }

    private static String formatVideoTime(long milliseconds) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d.%03d",
                milliseconds / 3600000L, (milliseconds / 60000L) % 60L,
                (milliseconds / 1000L) % 60L, milliseconds % 1000L);
    }

    public static void hook(ClassLoader classLoader) {
        ChronosPackageOverride.install(classLoader);
        installChronosProbe(classLoader);
        try{
            XposedHelpers.findAndHookMethod(
                    "com.bilibili.cron.ChronosPackage",
                    classLoader,
                    "createPackageFromFile",
                    android.content.Context.class,
                    java.io.File.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                java.io.File source = (java.io.File) param.args[1];
                                android.content.Context context =
                                        (android.content.Context) param.args[0];
                                if (voteLogDirectory == null && context != null) {
                                    voteLogDirectory = new java.io.File(context.getFilesDir(), "danmu_jump_logs");
                                    Log.d(TAG, "[DanmuVoteLog] directory=" + voteLogDirectory.getAbsolutePath());
                                }
                                param.setObjectExtra("dj_original", source);
                                ChronosPackageOverride.Selection selection =
                                        ChronosPackageOverride.select(context, source);
                                param.setObjectExtra("dj_builtin", selection.builtin);
                                param.setObjectExtra("dj_selected", selection.file);
                                if (selection.skipPatching) {
                                    if (ChronosPackageOverride.isDebugBuild(context)) {
                                        try {
                                            DanmuScriptPatcher.checkCompatibility(context, selection.file);
                                        } catch (DanmuScriptPatcher.ScriptMismatchException mismatch) {
                                            ChronosPackageOverride.mismatch(context, selection.file, mismatch);
                                        } catch (Exception checkError) {
                                            Log.d(TAG, "[ChronosOverride] debug compatibility check failed: " + checkError);
                                        }
                                    }
                                    param.args[1] = selection.file;
                                    Log.d(TAG, "[ChronosOverride] restored original, skip module patches");
                                    return;
                                }
                                java.io.File patched = buildTracePackage(context, selection.file);
                                if (patched != null) {
                                    param.args[1] = patched;
                                    Log.d(TAG, "[ChronosPatch] redirected="
                                            + patched.getAbsolutePath());
                                }
                            } catch (DanmuScriptPatcher.ScriptMismatchException e) {
                                ChronosPackageOverride.mismatch((android.content.Context) param.args[0],
                                        (java.io.File) param.getObjectExtra("dj_original"),
                                        (java.io.File) param.getObjectExtra("dj_selected"), e);
                            } catch (Throwable t) {
                                Log.d(TAG, "[ChronosPatch] error\n"
                                        + Log.getStackTraceString(t));
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            java.io.File file = (java.io.File) param.args[1];

                            Log.d(TAG, "[ChronosPackage] source="
                                    + file.getAbsolutePath()
                                    + ", size=" + file.length());

                            if (Boolean.TRUE.equals(param.getObjectExtra("dj_builtin"))
                                    && (param.hasThrowable() || param.getResult() == null)) {
                                ChronosPackageOverride.loadFailed((android.content.Context) param.args[0],
                                        (java.io.File) param.getObjectExtra("dj_original"),
                                        param.hasThrowable() ? param.getThrowable()
                                                : new java.io.IOException("内置包创建结果为空"));
                            }
                            if (param.hasThrowable()) {
                                Log.d(TAG, "[ChronosPackage] error\n"
                                        + Log.getStackTraceString(param.getThrowable()));
                                return;
                            }

                            Object pkg = param.getResult();
                            if (pkg == null) {
                                Log.d(TAG, "[ChronosPackage] result=null");
                                return;
                            }

                            try {
                                Log.d(TAG, "[ChronosPackage] info="
                                        + XposedHelpers.callMethod(pkg, "getInfo"));
                                Log.d(TAG, "[ChronosPackage] sandbox="
                                        + XposedHelpers.callMethod(
                                        pkg, "getSandBoxDirectory"));
                            } catch (Throwable t) {
                                Log.d(TAG, "[ChronosPackage] inspect error\n"
                                        + Log.getStackTraceString(t));
                            }
                        }
                    }
            );
            Log.d(TAG, "[ChronosPackage] hook installed");
        } catch (Exception e) {
            Log.d(TAG, "fail to hook method\n" + Log.getStackTraceString(e));
        }
    }

    /** Test JS bootstrap execution; does not seek or change the danmaku package. */
    private static void installChronosProbe(ClassLoader classLoader) {
        try {
            Class<?> engineClass = XposedHelpers.findClass(
                    "com.bilibili.cron.ChronosEngine", classLoader);
            Class<?> loggerInterface = XposedHelpers.findClass(
                    "com.bilibili.cron.ChronosPackageRunner$ExternalLogger",
                    classLoader);

            XposedBridge.hookAllConstructors(engineClass, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length != 4) {
                        return;
                    }
                    try {
                        final Object originalLogger = param.args[3];
                        final Object ownerEngine = param.thisObject;
                        Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                                loggerInterface.getClassLoader(),
                                new Class<?>[]{loggerInterface},
                                (proxyObject, method, args) -> {
                                    if ("log".equals(method.getName())
                                            && args != null && args.length == 1) {
                                        String message = String.valueOf(args[0]);
                                        int voteIndex = message.indexOf("[DanmuJumpVote]");
                                        if (voteIndex >= 0) {
                                            appendVoteLog(message.substring(
                                                    voteIndex + "[DanmuJumpVote]".length()));
                                        }
                                        int senderIndex = message.indexOf("[DanmuSenderData]");
                                        if (senderIndex >= 0) {
                                            DanmuSenderLookup.cacheClientDanmaku(message.substring(
                                                    senderIndex + "[DanmuSenderData]".length()));
                                        }
                                        int dataIndex = message.indexOf("[DanmuJumpData]");
                                        if (dataIndex >= 0) {
                                            relayCandidates(ownerEngine,
                                                    message.substring(dataIndex + "[DanmuJumpData]".length()));
                                        }
                                        if (message.contains("[DanmuJumpJS]")) {
                                            Log.d(TAG, "[ChronosJS] " + message);
                                            if (TEST_AUTO_JUMP && message.contains(
                                                    "[DanmuJumpJS] airborne controller ready")
                                                    && testScheduled.compareAndSet(false, true)) {
                                                final Object expectedEngine = activeEngine.get();
                                                new android.os.Handler(android.os.Looper.getMainLooper())
                                                        .postDelayed(() -> {
                                                            if (expectedEngine != null
                                                                    && activeEngine.get() == expectedEngine) {
                                                                jumpTo(TEST_TARGET_MS);
                                                            }
                                                        }, 8000L);
                                                Log.d(TAG, "[ChronosBridge] test scheduled in 8s, targetMs="
                                                        + TEST_TARGET_MS);
                                            }
                                        }
                                        if (originalLogger != null) {
                                            try {
                                                method.invoke(originalLogger, args);
                                            } catch (java.lang.reflect.InvocationTargetException e) {
                                                throw e.getCause();
                                            }
                                        }
                                        return null;
                                    }
                                    switch (method.getName()) {
                                        case "toString":
                                            return "DanmuJumpChronosLogger";
                                        case "hashCode":
                                            return System.identityHashCode(proxyObject);
                                        case "equals":
                                            return args != null && args.length == 1
                                                    && proxyObject == args[0];
                                        default:
                                            return null;
                                    }
                                });
                        param.args[3] = proxy;
                        Log.d(TAG, "[ChronosProbe] logger attached");
                    } catch (Throwable t) {
                        Log.d(TAG, "[ChronosProbe] logger error\n"
                                + Log.getStackTraceString(t));
                    }
                }
            });

            XposedHelpers.findAndHookMethod(
                    engineClass, "runPackage",
                    "com.bilibili.cron.ChronosPackage",
                    String.class,
                    "com.bilibili.cron.ChronosPackage$LoadCompleteCallback",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object pkg = param.args[0];
                            if (pkg == null) {
                                return;
                            }
                            try {
                                String info = (String) XposedHelpers.callMethod(pkg, "getInfo");
                                if (info == null || !"danmaku-flame-master".equals(
                                        new org.json.JSONObject(info).optString("name"))) {
                                    return;
                                }
                                activeEngine = new java.lang.ref.WeakReference<>(param.thisObject);
                                String original = (String) param.args[1];
                                if (original == null || original.trim().isEmpty()
                                        || original.contains("[DanmuJumpJS]")) {
                                    return;
                                }
                                String expression = original.trim();
                                while (expression.endsWith(";")) {
                                    expression = expression.substring(
                                            0, expression.length() - 1).trim();
                                }
                                param.args[1] = "(function(){"
                                        + "var value=(" + expression + ");"
                                        + "try{cron.log('[DanmuJumpJS] bootstrap executed');}catch(e){}"
                                        + "return value;})();";
                                Log.d(TAG, "[ChronosProbe] bootstrap marker inserted");
                            } catch (Throwable t) {
                                Log.d(TAG, "[ChronosProbe] bootstrap error\n"
                                        + Log.getStackTraceString(t));
                            }
                        }
                    });
            Log.d(TAG, "[ChronosProbe] hooks installed");
        } catch (Throwable t) {
            Log.d(TAG, "[ChronosProbe] install error\n" + Log.getStackTraceString(t));
        }
    }

    private static final String DM_WORKER_SCRIPT =
            "res/src/danmaku/business/demand/entry_point/@cronc.worker.dfm_script.js";
    private static final String MAIN_SCRIPT =
            "res/src/danmaku/business/demand/entry_point/index_main.js";

    /** Builds a temporary trace-only copy. Never edits the original cache package. */
    private static synchronized java.io.File buildTracePackage(
            android.content.Context context, java.io.File source) throws Exception {
        if (source == null || !source.isFile()) {
            return null;
        }
        java.io.File output = null;
        boolean complete = false;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(source)) {
            java.util.zip.ZipEntry infoEntry = zip.getEntry("info.json");
            if (infoEntry == null) {
                return null;
            }
            String info;
            try (java.io.InputStream input = zip.getInputStream(infoEntry)) {
                info = new String(readLimited(input, 1024 * 1024),
                        java.nio.charset.StandardCharsets.UTF_8);
            }
            org.json.JSONObject metadata = new org.json.JSONObject(info);
            if (!"danmaku-flame-master".equals(metadata.optString("name"))) {
                return null;
            }
            org.json.JSONObject config = loadRules(context);
            java.util.zip.ZipEntry mainEntry = zip.getEntry(MAIN_SCRIPT);
            if (mainEntry == null) {
                throw new DanmuScriptPatcher.ScriptMismatchException("Main script is missing");
            }
            String script;
            try (java.io.InputStream input = zip.getInputStream(mainEntry)) {
                script = new String(readLimited(input, 4 * 1024 * 1024),
                        java.nio.charset.StandardCharsets.UTF_8);
            }
            java.util.zip.ZipEntry workerEntry = zip.getEntry(DM_WORKER_SCRIPT);
            if (workerEntry == null) {
                throw new DanmuScriptPatcher.ScriptMismatchException("Danmaku worker script is missing");
            }
            String worker;
            try (java.io.InputStream input = zip.getInputStream(workerEntry)) {
                worker = new String(readLimited(input, 4 * 1024 * 1024),
                        java.nio.charset.StandardCharsets.UTF_8);
            }
            DanmuScriptPatcher.Result result = DanmuScriptPatcher.patch(context, script, worker, config);
            byte[] patchedScript = result.main.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] patchedWorker = result.worker.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            java.io.File directory = new java.io.File(context.getCacheDir(), "danmu_jump_probe");
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new java.io.IOException("Cannot create probe directory");
            }
            output = java.io.File.createTempFile("latest-chronos-", ".zip", directory);
            try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(
                    new java.io.BufferedOutputStream(new java.io.FileOutputStream(output)))) {
                java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
                byte[] buffer = new byte[16384];
                while (entries.hasMoreElements()) {
                    java.util.zip.ZipEntry original = entries.nextElement();
                    java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(original.getName());
                    if (original.getTime() >= 0) {
                        entry.setTime(original.getTime());
                    }
                    out.putNextEntry(entry);
                    if (MAIN_SCRIPT.equals(original.getName())) {
                        out.write(patchedScript);
                    } else if (DM_WORKER_SCRIPT.equals(original.getName())) {
                        out.write(patchedWorker);
                    } else if (!original.isDirectory()) {
                        try (java.io.InputStream input = zip.getInputStream(original)) {
                            int length;
                            while ((length = input.read(buffer)) != -1) {
                                out.write(buffer, 0, length);
                            }
                        }
                    }
                    out.closeEntry();
                }
            }
            complete = true;
            Log.d(TAG, "[ChronosPatch] trace package ready, version="
                    + metadata.optString("version") + ", size=" + output.length());
            return output;
        } finally {
            if (!complete && output != null) {
                output.delete();
            }
        }
    }

    private static byte[] readLimited(java.io.InputStream input, int maximum)
            throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        while ((length = input.read(buffer)) != -1) {
            if (length > maximum - out.size()) {
                throw new java.io.IOException("Script or metadata exceeds size limit");
            }
            out.write(buffer, 0, length);
        }
        return out.toByteArray();
    }

    public static void jumpTo(long targetMs) {
        if (targetMs < 0 || targetMs > Integer.MAX_VALUE) {
            Log.d(TAG, "[ChronosBridge] invalid targetMs=" + targetMs);
            return;
        }
        final Object engine = activeEngine.get();
        if (engine == null) {
            Log.d(TAG, "[ChronosBridge] no active engine");
            return;
        }
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                if (activeEngine.get() != engine
                        || !Boolean.TRUE.equals(XposedHelpers.callMethod(engine, "isValid"))) {
                    Log.d(TAG, "[ChronosBridge] engine is no longer active");
                    return;
                }
                org.json.JSONObject args = new org.json.JSONObject();
                args.put("targetSec", targetMs / 1000.0);
                org.json.JSONObject request = new org.json.JSONObject();
                request.put("method", "DanmuJump_Jump");
                request.put("args", args);
                byte[] json = request.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] message = java.nio.ByteBuffer.allocate(5 + json.length)
                        .order(java.nio.ByteOrder.BIG_ENDIAN)
                        .put((byte) 0).putInt(json.length).put(json).array();
                Class<?> callbackType = XposedHelpers.findClass(
                        "com.bilibili.cron.ChronosPackageRunner$MessageHandledCallback",
                        engine.getClass().getClassLoader());
                Object callback = java.lang.reflect.Proxy.newProxyInstance(
                        callbackType.getClassLoader(), new Class<?>[]{callbackType},
                        (proxy, method, values) -> {
                            if ("onComplete".equals(method.getName())) {
                                byte[] response = values == null ? null : (byte[]) values[0];
                                Log.d(TAG, "[ChronosBridge] response=" + decodeResponse(response));
                                return null;
                            }
                            switch (method.getName()) {
                                case "toString": return "DanmuJumpResponseCallback";
                                case "hashCode": return System.identityHashCode(proxy);
                                case "equals": return values != null && values.length == 1
                                        && proxy == values[0];
                                default: return null;
                            }
                        });
                XposedHelpers.callMethod(engine, "sendMessageAsync", message, callback);
                Log.d(TAG, "[ChronosBridge] sent targetMs=" + targetMs);
            } catch (Throwable t) {
                Log.d(TAG, "[ChronosBridge] send error\n" + Log.getStackTraceString(t));
            }
        });
    }

    private static String decodeResponse(byte[] response) {
        if (response == null) {
            return "null";
        }
        if (response.length >= 5 && response[0] == 0) {
            int length = java.nio.ByteBuffer.wrap(response, 1, 4)
                    .order(java.nio.ByteOrder.BIG_ENDIAN).getInt();
            if (length >= 0 && length <= response.length - 5) {
                return new String(response, 5, length, java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return "unrecognized frame, length=" + response.length;
    }

    private static org.json.JSONObject loadRules(android.content.Context context) throws Exception {
        java.io.File file = new java.io.File(context.getFilesDir(), "danmu_jump_rules.json");
        if (!file.exists()) {
            org.json.JSONObject initial = new org.json.JSONObject();
            initial.put("enabled", true);
            initial.put("minVotes", 3);
            initial.put("targetToleranceSeconds", 5);
            initial.put("sourceWindowSeconds", 30);
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
                output.write(initial.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        org.json.JSONObject config;
        try (java.io.InputStream input = new java.io.FileInputStream(file)) {
            config = new org.json.JSONObject(new String(readLimited(input, 65536),
                    java.nio.charset.StandardCharsets.UTF_8));
        }
        Log.d(TAG, "[DanmuRules] file=" + file.getAbsolutePath()
                + ", enabled=" + config.optBoolean("enabled", true)
                + ", minVotes=" + config.optInt("minVotes", 3));
        config.put("enabled", config.optBoolean("enabled", true));
        config.put("minVotes", Math.max(2, Math.min(20, config.optInt("minVotes", 3))));
        config.put("targetToleranceSeconds", Math.max(1, Math.min(10,
                config.optInt("targetToleranceSeconds", 5))));
        config.put("sourceWindowSeconds", Math.max(5, Math.min(120,
                config.optInt("sourceWindowSeconds", 30))));
        return config;
    }

    private static void relayCandidates(Object engine, String jsonText) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                if (!Boolean.TRUE.equals(XposedHelpers.callMethod(engine, "isValid"))) {
                    return;
                }
                org.json.JSONObject request = new org.json.JSONObject();
                request.put("method", "DanmuJump_AddCandidates");
                request.put("args", new org.json.JSONObject(jsonText));
                byte[] json = request.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] frame = java.nio.ByteBuffer.allocate(5 + json.length)
                        .order(java.nio.ByteOrder.BIG_ENDIAN)
                        .put((byte) 0).putInt(json.length).put(json).array();
                Class<?> callbackClass = XposedHelpers.findClass(
                        "com.bilibili.cron.ChronosPackageRunner$MessageHandledCallback",
                        engine.getClass().getClassLoader());
                XposedHelpers.callMethod(engine, "sendMessageAsync",
                        new Class<?>[]{byte[].class, callbackClass}, frame, null);
            } catch (Throwable t) {
                Log.d(TAG, "[ChronosBridge] candidate relay error\n" + Log.getStackTraceString(t));
            }
        });
    }


}
