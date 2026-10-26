package com.fuck.bili.ads.chronos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/** 用户确认后替换加载来源；原始缓存不覆盖，同时保存独立原包副本。 */
public final class ChronosPackageOverride {
    private static final String TAG = "DanmuJump";
    private static final String SHA256 =
            "3963780083a6a40a83f6c5cb85fbb316b9193cbba94a30979999a58551c227ec";
    private static final String AUTO = "auto", BUILTIN = "builtin", ORIGINAL = "original";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ChronosPackageIO");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean installed = new AtomicBoolean();
    private static volatile WeakReference<Activity> resumed = new WeakReference<>(null);
    private static Pending pending;
    private static boolean prompted;
    private static boolean dialogShowing;
    private static boolean recoveryPrompted;
    private static volatile Boolean moduleDebug;

    /** 检查模块 APK 的标志，不能使用宿主 B站的 debuggable 标志。无需开启 BuildConfig。 */
    static boolean isDebugBuild(Context context) {
        Boolean cached = moduleDebug;
        if (cached != null) return cached;
        try {
            boolean debug = (context.getPackageManager().getApplicationInfo(
                    "com.fuck.bili.ads", 0).flags
                    & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            moduleDebug = debug;
            Log.d(TAG, "[ChronosOverride] moduleBuild=" + (debug ? "debug" : "release"));
            return debug;
        } catch (Exception error) {
            Log.d(TAG, "[ChronosOverride] cannot determine module build: " + error);
            return false;
        }
    }

    static final class Selection {
        final File file;
        final boolean skipPatching;
        final boolean builtin;
        Selection(File file, boolean skipPatching, boolean builtin) {
            this.file = file; this.skipPatching = skipPatching; this.builtin = builtin;
        }
    }

    private static final class Pending {
        final Context context;
        final File original;
        final String error;
        final boolean recovery;
        final boolean manual;
        final boolean debug;
        final String currentVersion;
        Pending(Context context, File original, String error, boolean recovery) {
            this(context, original, error, recovery, false);
        }
        Pending(Context context, File original, String error, boolean recovery, boolean manual) {
            this(context, original, error, recovery, manual, packageVersion(original));
        }
        Pending(Context context, File original, String error, boolean recovery, boolean manual,
                String currentVersion) {
            Context app = context.getApplicationContext();
            this.context = app == null ? context : app;
            this.original = original; this.error = error; this.recovery = recovery; this.manual = manual;
            this.debug = isDebugBuild(context);
            this.currentVersion = currentVersion;
        }
    }

    static void install(ClassLoader classLoader) {
        if (!installed.compareAndSet(false, true)) return;
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    resumed = new WeakReference<>((Activity) param.thisObject);
                    MAIN.post(ChronosPackageOverride::showPending);
                }
            });
            XposedHelpers.findAndHookMethod(Activity.class, "onPause", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (resumed.get() == param.thisObject) resumed = new WeakReference<>(null);
                }
            });
        } catch (Throwable error) {
            Log.d(TAG, "[ChronosOverride] lifecycle hook failed: " + error);
        }
        try {
            // 视频重进时宿主可能复用已创建的 ChronosPackage，不能只等创建包的 hook。
            XposedHelpers.findAndHookMethod(
                    "tv.danmaku.biliplayerv2.service.interact.biz.InteractLayerService",
                    classLoader, "bindPlayerContainer", "tv.danmaku.biliplayerv2.PlayerContainer",
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Context context = (Context) XposedHelpers.callMethod(param.args[0], "getContext");
                                if (isDebugBuild(context)) checkDebugVideoEntry(context);
                            } catch (Throwable error) {
                                Log.d(TAG, "[ChronosOverride] video entry check failed: " + error);
                            }
                        }
                    });
        } catch (Throwable error) {
            Log.d(TAG, "[ChronosOverride] video entry hook unavailable: " + error);
        }
    }

    private static void checkDebugVideoEntry(Context context) {
        Context app = context.getApplicationContext();
        final Context host = app == null ? context : app;
        IO.execute(() -> {
            try {
                JSONObject state = readState(host);
                File original = findOriginal(host, state);
                if (original == null) return; // 首次载入由 createPackageFromFile 检查。
                File current = BUILTIN.equals(state.optString("mode", AUTO))
                        ? new File(host.getFilesDir(), "danmu_package_override/bundled-3.12.0.zip")
                        : original;
                try {
                    DanmuScriptPatcher.checkCompatibility(host, current);
                } catch (DanmuScriptPatcher.ScriptMismatchException error) {
                    mismatch(host, original, current, error);
                }
            } catch (Exception error) {
                Log.d(TAG, "[ChronosOverride] debug video compatibility check failed: " + error);
            }
        });
    }

    static Selection select(Context context, File original) throws Exception {
        if (!isDanmaku(original)) return new Selection(original, false, false);
        synchronized (ChronosPackageOverride.class) {
            JSONObject state = readState(context);
            boolean target = isTargetPackage(original);
            if (!original.getAbsolutePath().equals(state.optString("observedOriginal"))
                    || state.optBoolean("observedTarget", false) != target) {
                state.put("observedOriginal", original.getAbsolutePath());
                state.put("observedTarget", target);
                writeState(context, state);
            }
            String mode = state.optString("mode", AUTO);
            if (ORIGINAL.equals(mode)) return new Selection(original, true, false);
            if (!BUILTIN.equals(mode)) return new Selection(original, false, false);
            try {
                File bundled = ensureBuiltin(context);
                // 宿主以后下载了不同原包，也保留其副本，不覆盖之前的副本。
                backupOriginal(context, original);
                Log.d(TAG, "[ChronosOverride] using bundled 3.12.0: " + bundled.getAbsolutePath());
                return new Selection(bundled, false, true);
            } catch (Exception error) {
                loadFailed(context, original, error);
                return new Selection(original, true, false);
            }
        }
    }

    static void mismatch(Context context, File original, Throwable error) {
        mismatch(context, original, original, error);
    }

    static void mismatch(Context context, File original, File current, Throwable error) {
        Log.d(TAG, "[ChronosPatch] JS incompatible: " + error.getMessage());
        if (context == null) return;
        final boolean debug = isDebugBuild(context);
        final String version = packageVersion(current);
        final Activity activity = activityOf(context);
        MAIN.post(() -> {
            if (activity != null && alive(activity)) resumed = new WeakReference<>(activity);
            if (dialogShowing) return;
            boolean recovery;
            try {
                JSONObject state = readState(context);
                if (!allowAutomaticPrompt(state, debug)) return;
                recovery = BUILTIN.equals(state.optString("mode", AUTO));
            } catch (Exception invalid) {
                Log.d(TAG, "[ChronosOverride] prompt suppressed: unreadable state");
                return;
            }
            if (!debug && (recovery ? recoveryPrompted : prompted)) return;
            pending = new Pending(context, original, String.valueOf(error.getMessage()), recovery,
                    false, version);
            showPending();
        });
    }

    static void loadFailed(Context context, File original, Throwable error) {
        Log.d(TAG, "[ChronosOverride] bundled load failed: " + error);
        mismatch(context, original,
                new File(context.getFilesDir(), "danmu_package_override/bundled-3.12.0.zip"), error);
    }

    private static void showPending() {
        Activity activity = resumed.get();
        if (pending == null || dialogShowing || !alive(activity)) return;
        Pending request = pending;
        if (!request.manual) {
            if (!request.debug && (request.recovery ? recoveryPrompted : prompted)) return;
            try {
                if (!allowAutomaticPrompt(readState(request.context), request.debug)) { pending = null; return; }
            } catch (Exception invalid) { pending = null; return; }
        }
        android.view.View panel = warningPanel(activity,
                request.recovery ? "内置弹幕包加载失败" : "使用模块内置弹幕包",
                "当前 Chronos：" + request.currentVersion + "\n内置目标：3.12.0\n\n"
                + (request.recovery
                        ? "内置包在当前客户端中未能正常加载，可恢复客户端原版弹幕包。"
                        : "是否用模块内置的 Chronos 3.12.0 替换加载？\n"
                          + "确认后先保存原包副本，再启用内置包。新旧客户端兼容性需要实际验证。\n"
                          + (request.debug
                                ? "Debug 模块：本次不替换后，下次进入视频检测到不适配仍会提示。"
                                : "选择不替换后，今后启动不再自动询问；仍可从设置主动替换。")),
                request.error);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(request.recovery ? "弹幕引擎异常" : "弹幕引擎替换确认")
                .setView(panel).setNegativeButton(request.debug ? "本次不替换" : "不替换，不再提示", null)
                .setPositiveButton(request.recovery ? "恢复原版" : "备份并替换", null)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(ignored -> {
            // 返回键同样视为拒绝；小配置同步提交，确保下次启动保留选择。
            try { rememberDecline(request.context); }
            catch (Exception error) { result(activity, "未能保存选择", error.toString()); }
        });
        dialog.setOnDismissListener(ignored -> dialogShowing = false);
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view -> {
                setButtonsEnabled(dialog, false);
                IO.execute(() -> {
                    try {
                        rememberDecline(request.context);
                        MAIN.post(() -> { if (alive(activity)) dialog.dismiss(); });
                    } catch (Throwable error) {
                        MAIN.post(() -> {
                            if (alive(activity)) {
                                setButtonsEnabled(dialog, true);
                                result(activity, "未能保存选择", error.toString());
                            }
                        });
                    }
                });
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                setButtonsEnabled(dialog, false);
                dialog.setCancelable(false);
                IO.execute(() -> {
                    try {
                        if (request.recovery) restoreOriginal(request.context);
                        else enable(request.context, request.original);
                        MAIN.post(() -> {
                            if (alive(activity)) {
                                dialog.dismiss();
                                result(activity, request.recovery ? "已恢复原版加载" : "已备份并启用内置包",
                                        "请退出当前视频并重新打开；若仍沿用旧引擎，请彻底重启 B站。\n"
                                        + "出现问题可在【关注页左上角设置 → 弹幕引擎 / 恢复原版】回退。");
                            }
                        });
                    } catch (Throwable error) {
                        Log.d(TAG, "[ChronosOverride] change failed: " + error);
                        MAIN.post(() -> {
                            if (alive(activity)) {
                                setButtonsEnabled(dialog, true);
                                dialog.setCancelable(true);
                                result(activity, "操作失败，未切换加载来源", error.toString());
                            }
                        });
                    }
                });
            });
        });
        try {
            dialog.show();
            dialogShowing = true;
            if (request.recovery) recoveryPrompted = true;
            else prompted = true;
            pending = null;
        } catch (Throwable error) {
            Log.d(TAG, "[ChronosOverride] dialog failed: " + error);
        }
    }

    private static void setButtonsEnabled(AlertDialog dialog, boolean enabled) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(enabled);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(enabled);
    }

    private static boolean allowAutomaticPrompt(JSONObject state, boolean debug) {
        return debug || (!ORIGINAL.equals(state.optString("mode", AUTO))
                && !state.optBoolean("suppressMismatchPrompt", false));
    }

    private static synchronized void rememberDecline(Context context) throws Exception {
        JSONObject state = readState(context);
        state.put("suppressMismatchPrompt", true);
        writeState(context, state);
    }

    private static android.view.View warningPanel(Context context, String heading,
                                             String detail, String error) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * context.getResources().getDisplayMetrics().density);
        panel.setPadding(pad, pad / 2, pad, pad / 2);
        TextView warning = new TextView(context);
        warning.setText("重要：若出现弹幕异常、视频错误或闪退，请到\n"
                + "【关注页左上角设置 → 弹幕引擎 / 恢复原版】\n恢复原版，然后彻底重启 B站。");
        warning.setTextSize(17);
        warning.setTypeface(null, Typeface.BOLD);
        warning.setTextColor(Color.rgb(210, 45, 45));
        warning.setPadding(0, 0, 0, pad / 2);
        panel.addView(warning);
        TextView text = new TextView(context);
        text.setText(heading + "\n\n" + detail + "\n\n匹配信息：" + error);
        text.setTextSize(14);
        panel.addView(text);
        android.widget.ScrollView scroll = new android.widget.ScrollView(context);
        scroll.addView(panel);
        return scroll;
    }

    /** 关注页设置的公开入口；不依赖当前播放器是否还存在。 */
    public static void showSettings(Context context) {
        final Activity activity = activityOf(context);
        if (!alive(activity)) return;
        IO.execute(() -> {
            try {
                JSONObject state;
                try { state = readState(context); }
                catch (Exception invalid) {
                    state = new JSONObject();
                    state.put("mode", "invalid");
                }
                String mode = state.optString("mode", AUTO);
                File original = findOriginal(context, state);
                boolean target = BUILTIN.equals(mode) || (original != null && isTargetPackage(original));
                String label = BUILTIN.equals(mode) ? "已启用模块内置 Chronos 3.12.0"
                        : ORIGINAL.equals(mode) ? "已恢复客户端原版（模块脚本修改已停用）"
                        : "invalid".equals(mode) ? "配置读取失败，可强制恢复原版"
                        : "自动适配客户端原包";
                String description = "当前模式：" + label
                        + "\n当前 Chronos：" + packageVersion(BUILTIN.equals(mode)
                                ? new File(context.getFilesDir(), "danmu_package_override/bundled-3.12.0.zip") : original)
                        + (target ? "\n当前包已是目标包，不需要替换。" : original == null
                        ? "\n尚未识别当前包，请先打开一次视频。" : "\n当前原包可主动替换为内置 3.12.0。")
                        + "\n自动替换提示：" + (isDebugBuild(context)
                                ? "Debug：每次检测到不适配均提示"
                                : allowAutomaticPrompt(state, false) ? "开启" : "已关闭，重启后保持")
                        + "\n\n原包副本：" + state.optString("backup", "尚未创建")
                        + "\n\n恢复原版会停止使用内置包并停止修改弹幕脚本。恢复后请彻底重启 B站，副本不会删除。";
                MAIN.post(() -> {
                    if (!alive(activity)) return;
                    LinearLayout panel = new LinearLayout(activity);
                    panel.setOrientation(LinearLayout.VERTICAL);
                    int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
                    panel.setPadding(padding, padding / 2, padding, padding / 2);
                    TextView text = new TextView(activity);
                    text.setText(description);
                    panel.addView(text);
                    android.widget.Button replace = null;
                    if (!target) {
                        replace = new android.widget.Button(activity);
                        replace.setText("主动替换为内置 Chronos 3.12.0");
                        panel.addView(replace);
                    }
                    android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
                    scroll.addView(panel);
                    AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("弹幕引擎 / 恢复原版")
                            .setView(scroll).setNegativeButton("关闭", null)
                            .setPositiveButton("恢复原版", (ignored, which) ->
                                    changeModeFromSettings(activity, ORIGINAL))
                            .setNeutralButton("重新适配原包", (ignored, which) ->
                                    changeModeFromSettings(activity, AUTO)).create();
                    if (replace != null) replace.setOnClickListener(view -> {
                        if (original == null) {
                            result(activity, "尚未找到原包", "请先打开一次视频，再回到此设置页面主动替换。");
                            return;
                        }
                        dialog.dismiss();
                        pending = new Pending(activity, original, "用户从设置主动替换", false, true);
                        resumed = new WeakReference<>(activity);
                        showPending();
                    });
                    dialog.show();
                });
            } catch (Throwable error) {
                MAIN.post(() -> { if (alive(activity)) result(activity, "读取失败", error.toString()); });
            }
        });
    }

    private static File findOriginal(Context context, JSONObject state) throws Exception {
        for (String key : new String[]{"observedOriginal", "originalPath"}) {
            String path = state.optString(key, "");
            if (!path.isEmpty()) {
                File file = new File(path);
                try { if (isDanmaku(file)) return file; }
                catch (Exception unreadable) { Log.d(TAG, "[ChronosOverride] last package is no longer readable"); }
            }
        }
        File[] files = new File(context.getCacheDir(), "chronos_pkg").listFiles();
        if (files != null) {
            java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File file : files) {
                try { if (isDanmaku(file)) return file; }
                catch (Exception unreadable) { Log.d(TAG, "[ChronosOverride] skip unreadable cached package"); }
            }
        }
        return null;
    }

    private static boolean isTargetPackage(File file) throws Exception {
        if (!isDanmaku(file)) return false;
        try (ZipFile zip = new ZipFile(file);
             InputStream input = zip.getInputStream(zip.getEntry("info.json"))) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 1024 * 1024) throw new IOException("Metadata too large");
                output.write(buffer, 0, count);
            }
            JSONObject info = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            return SHA256.equals(hash(file)) || ("3.12.0".equals(info.optString("version"))
                    && "400ac5eae28407c896f2bbcdb6889b935e444da0".equals(info.optString("commit")));
        }
    }

    private static String packageVersion(File file) {
        if (file == null || !file.isFile()) return "未知（未找到包）";
        try (ZipFile zip = new ZipFile(file)) {
            java.util.zip.ZipEntry entry = zip.getEntry("info.json");
            if (entry == null) return "未知（缺少 info.json）";
            try (InputStream input = zip.getInputStream(entry)) {
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 1024 * 1024) throw new IOException("Metadata too large");
                    output.write(buffer, 0, count);
                }
                JSONObject info = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
                String version = info.optString("version", "未知");
                String commit = info.optString("commit", "");
                return version + (commit.isEmpty() ? "" : "（commit "
                        + commit.substring(0, Math.min(8, commit.length())) + "）");
            }
        } catch (Exception error) {
            Log.d(TAG, "[ChronosOverride] read package version failed: " + error);
            return "未知（读取失败）";
        }
    }

    private static void changeModeFromSettings(Activity activity, String mode) {
        IO.execute(() -> {
            try {
                if (ORIGINAL.equals(mode)) restoreOriginal(activity);
                else synchronized (ChronosPackageOverride.class) {
                    JSONObject state = readState(activity);
                    if (ORIGINAL.equals(state.optString("mode", AUTO))) {
                        state.put("suppressMismatchPrompt", true);
                    }
                    state.put("mode", mode);
                    writeState(activity, state);
                }
                MAIN.post(() -> {
                    pending = null;
                    prompted = ORIGINAL.equals(mode);
                    recoveryPrompted = false;
                    if (alive(activity)) result(activity, "已保存",
                            ORIGINAL.equals(mode) ? "已恢复原版加载。请彻底重启 B站生效。"
                                    : "下次加载时重新适配原包。此前拒绝或回退过，自动提示仍保持关闭；需要替换请从设置主动操作。请重启 B站。");
                });
            } catch (Throwable error) {
                MAIN.post(() -> { if (alive(activity)) result(activity, "恢复失败", error.toString()); });
            }
        });
    }

    private static synchronized void enable(Context context, File original) throws Exception {
        // 状态最后提交；缺资源、摘要不符或备份失败，都不会启用替换。
        ensureBuiltin(context);
        JSONObject state = readState(context);
        File backup = backupOriginal(context, original);
        state.put("backup", backup.getAbsolutePath());
        state.put("originalPath", original.getAbsolutePath());
        state.put("mode", BUILTIN);
        state.put("enabledAtMs", System.currentTimeMillis());
        state.put("bundledSha256", SHA256);
        writeState(context, state);
        Log.d(TAG, "[ChronosOverride] backup=" + backup.getAbsolutePath());
    }

    private static synchronized void restoreOriginal(Context context) throws Exception {
        JSONObject state;
        try { state = readState(context); }
        catch (Exception invalid) { state = new JSONObject(); }
        state.put("mode", ORIGINAL);
        state.put("suppressMismatchPrompt", true);
        writeState(context, state);
    }

    private static File ensureBuiltin(Context context) throws Exception {
        File file = new File(directory(context), "bundled-3.12.0.zip");
        if (file.isFile() && SHA256.equals(hash(file))) return file;
        byte[] bytes = DanmuScriptPatcher.bundledPackage(context);
        if (!SHA256.equals(hash(bytes))) throw new IOException("内置包摘要校验失败");
        writeAtomic(file, bytes);
        if (!isDanmaku(file)) throw new IOException("内置文件不是弹幕引擎包");
        return file;
    }

    private static File backupOriginal(Context context, File original) throws Exception {
        if (!isDanmaku(original)) throw new IOException("原包不存在或不是弹幕引擎包");
        String expected = hash(original);
        File backup = new File(directory(context), "original-" + expected + ".zip");
        if (!backup.isFile() || !expected.equals(hash(backup))) {
            AtomicFile atomic = new AtomicFile(backup);
            FileOutputStream output = null;
            try (InputStream input = new FileInputStream(original)) {
                output = atomic.startWrite();
                byte[] buffer = new byte[16384];
                int length;
                while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
                atomic.finishWrite(output);
            } catch (Exception error) {
                if (output != null) atomic.failWrite(output);
                throw error;
            }
        }
        if (!expected.equals(hash(backup))) throw new IOException("原包副本校验失败");
        return backup;
    }

    private static boolean isDanmaku(File file) throws Exception {
        if (file == null || !file.isFile()) return false;
        try (ZipFile zip = new ZipFile(file)) {
            java.util.zip.ZipEntry info = zip.getEntry("info.json");
            if (info == null) return false;
            try (InputStream input = zip.getInputStream(info)) {
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int length;
                while ((length = input.read(buffer)) != -1) {
                    if (output.size() + length > 1024 * 1024) throw new IOException("Metadata too large");
                    output.write(buffer, 0, length);
                }
                return "danmaku-flame-master".equals(new JSONObject(
                        new String(output.toByteArray(), StandardCharsets.UTF_8)).optString("name"));
            }
        }
    }

    private static File directory(Context context) throws IOException {
        File dir = new File(context.getFilesDir(), "danmu_package_override");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create override directory");
        return dir;
    }

    private static synchronized JSONObject readState(Context context) throws Exception {
        AtomicFile atomic = new AtomicFile(new File(directory(context), "state.json"));
        try { return new JSONObject(new String(atomic.readFully(), StandardCharsets.UTF_8)); }
        catch (java.io.FileNotFoundException missing) { return new JSONObject(); }
    }

    private static void writeState(Context context, JSONObject state) throws Exception {
        writeAtomic(new File(directory(context), "state.json"), state.toString(2).getBytes(StandardCharsets.UTF_8));
    }

    private static void writeAtomic(File file, byte[] bytes) throws Exception {
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream output = null;
        try {
            output = atomic.startWrite(); output.write(bytes); atomic.finishWrite(output);
        } catch (Exception error) {
            if (output != null) atomic.failWrite(output);
            throw error;
        }
    }

    private static String hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[16384]; int length;
            while ((length = input.read(buffer)) != -1) digest.update(buffer, 0, length);
        }
        return hex(digest.digest());
    }

    private static String hash(byte[] bytes) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }

    private static Activity activityOf(Context context) {
        for (int i = 0; context != null && i < 10; i++) {
            if (context instanceof Activity) return (Activity) context;
            if (!(context instanceof ContextWrapper)) break;
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == context) break;
            context = next;
        }
        return null;
    }

    private static boolean alive(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private static void result(Activity activity, String title, String message) {
        if (!alive(activity)) return;
        try {
            new AlertDialog.Builder(activity).setTitle(title)
                    .setMessage(message).setPositiveButton("知道了", null).show();
        } catch (Throwable error) {
            Log.d(TAG, "[ChronosOverride] result dialog failed: " + error);
        }
    }

    private ChronosPackageOverride() {}
}
