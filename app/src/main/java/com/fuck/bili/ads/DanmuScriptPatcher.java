package com.fuck.bili.ads;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** 最新弹幕包适配：所有混淆片段放在 assets/danmu/latest.json。 */
final class DanmuScriptPatcher {
    private static final String TAG = "DanmuJump";
    private static final String MODULE_PACKAGE = "io.github.fesht377.biliads";

    static final class ScriptMismatchException extends IOException {
        ScriptMismatchException(String message) { super(message); }
    }

    static final class Result {
        final String main;
        final String worker;
        Result(String main, String worker) { this.main = main; this.worker = worker; }
    }

    static Result patch(Context host, String main, String worker, JSONObject config) throws Exception {
        JSONObject manifest = new JSONObject(asset(host, "latest.json"));
        // 先检查所有原始片段，再进行任何替换；每一处必须唯一命中。
        JSONArray patches = manifest.getJSONArray("mainPatches");
        for (int i = 0; i < patches.length(); i++) {
            JSONObject patch = patches.getJSONObject(i);
            requireUnique(main, patch.getString("find"), patch.getString("name"));
        }
        String workerAnchor = manifest.getString("workerAnchor");
        requireUnique(worker, workerAnchor, "worker-decoder");
        String bridge = asset(host, "jump-bridge.js");
        String prompt = asset(host, "ad-prompt.js");
        String parser = asset(host, "time-parser.js");
        String candidates = asset(host, "worker-candidates.js");
        String settings = config.toString();
        for (int i = 0; i < patches.length(); i++) {
            JSONObject patch = patches.getJSONObject(i);
            String replacement = patch.getString("replace")
                    .replace("{{CONFIG}}", settings)
                    .replace("{{BRIDGE}}", bridge)
                    .replace("{{PROMPT}}", prompt);
            main = main.replace(patch.getString("find"), replacement);
        }
        worker = parser + "\nconst __DJ_CONFIG=" + settings + ";\n"
                + worker.replace(workerAnchor, manifest.getString("workerReplacement")
                        .replace("{{WORKER}}", candidates));
        main = "try{cron.log('[DanmuJumpJS] main script entered "
                + manifest.getString("id") + "');}catch(error){}\n" + main;
        Log.d(TAG, "[ChronosPatch] adapter=" + manifest.getString("id"));
        return new Result(main, worker);
    }

    private static void requireUnique(String source, String anchor, String name)
            throws ScriptMismatchException {
        if (anchor.isEmpty()) throw new ScriptMismatchException(name + ": empty anchor");
        int count = 0;
        int from = 0;
        while ((from = source.indexOf(anchor, from)) >= 0) {
            count++;
            from += anchor.length();
        }
        if (count != 1) throw new ScriptMismatchException(name + ": expected 1 match, got " + count);
    }

    /** 用模块 ClassLoader 读模块 APK，不能读 host.getAssets()。 */
    private static String asset(Context host, String name) throws Exception {
        String path = "assets/danmu/" + name;
        ClassLoader loader = DanmuScriptPatcher.class.getClassLoader();
        InputStream resource = loader == null ? null : loader.getResourceAsStream(path);
        if (resource != null) {
            try (InputStream input = resource) { return read(input); }
        }
        // 部分 Xposed ClassLoader 不暴露非 class 资源，回退直接读取已安装模块 APK。
        String apk = host.getPackageManager().getApplicationInfo(MODULE_PACKAGE, 0).sourceDir;
        try (ZipFile zip = new ZipFile(apk)) {
            ZipEntry entry = zip.getEntry(path);
            if (entry == null) throw new IOException("Module resource missing: " + path);
            try (InputStream input = zip.getInputStream(entry)) { return read(input); }
        }
    }

    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > 1024 * 1024) throw new IOException("Module script too large");
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
