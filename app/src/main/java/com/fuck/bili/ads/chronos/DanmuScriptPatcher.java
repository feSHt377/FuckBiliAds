package com.fuck.bili.ads.chronos;

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
        validateAnchors(manifest, main, worker);
        // 先检查所有原始片段，再进行任何替换；每一处必须唯一命中。
        JSONArray patches = manifest.getJSONArray("mainPatches");
        String workerAnchor = manifest.getString("workerAnchor");
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

    /** 回退模式下只检查兼容性，不生成或加载修改后的包。 */
    static void checkCompatibility(Context host, java.io.File file) throws Exception {
        try (ZipFile zip = new ZipFile(file)) {
            String base = "res/src/danmaku/business/demand/entry_point/";
            String main = script(zip, base + "index_main.js");
            String worker = script(zip, base + "@cronc.worker.dfm_script.js");
            validateAnchors(new JSONObject(asset(host, "latest.json")), main, worker);
        }
    }

    private static String script(ZipFile zip, String path) throws Exception {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null) throw new ScriptMismatchException("Script missing: " + path);
        try (InputStream input = zip.getInputStream(entry)) {
            return new String(read(input, 4 * 1024 * 1024), StandardCharsets.UTF_8);
        }
    }

    private static void validateAnchors(JSONObject manifest, String main, String worker)
            throws Exception {
        JSONArray patches = manifest.getJSONArray("mainPatches");
        for (int i = 0; i < patches.length(); i++) {
            JSONObject patch = patches.getJSONObject(i);
            requireUnique(main, patch.getString("find"), patch.getString("name"));
        }
        requireUnique(worker, manifest.getString("workerAnchor"), "worker-decoder");
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

    /** 内置原始包，必须通过调用方校验 SHA-256 后再启用。 */
    static byte[] bundledPackage(Context host) throws Exception {
        return moduleBytes(host, "chronos-3.12.0.zip", 32 * 1024 * 1024);
    }

    private static String asset(Context host, String name) throws Exception {
        return new String(moduleBytes(host, name, 1024 * 1024), StandardCharsets.UTF_8);
    }

    /** 读取模块自己的 APK，不能使用 host.getAssets()。 */
    private static byte[] moduleBytes(Context host, String name, int maximum) throws Exception {
        String path = "assets/danmu/" + name;
        ClassLoader loader = DanmuScriptPatcher.class.getClassLoader();
        InputStream resource = loader == null ? null : loader.getResourceAsStream(path);
        if (resource != null) {
            try (InputStream input = resource) { return read(input, maximum); }
        }
        String apk = host.getPackageManager().getApplicationInfo(MODULE_PACKAGE, 0).sourceDir;
        try (ZipFile zip = new ZipFile(apk)) {
            ZipEntry entry = zip.getEntry(path);
            if (entry == null) throw new IOException("Module resource missing: " + path);
            try (InputStream input = zip.getInputStream(entry)) { return read(input, maximum); }
        }
    }

    private static byte[] read(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > maximum) throw new IOException("Module resource too large");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
