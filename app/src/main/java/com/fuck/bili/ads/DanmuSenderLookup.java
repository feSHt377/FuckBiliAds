package com.fuck.bili.ads;

import android.app.AndroidAppHelper;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** 弹幕发送者候选查询：客户端缓存、复制触发、主页检查及候选窗口。 */
public final class DanmuSenderLookup {
    private static final String TAG = "FuckBiliAds";
    private static final boolean IS_DEBUG = true;

    private static volatile java.lang.ref.WeakReference<android.app.Activity> senderActivity =
            new java.lang.ref.WeakReference<>(null);
    private static final ConcurrentHashMap<String, Long> RECENT_MSG_TIMES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, List<String>> SENDER_CRC_CACHE = new ConcurrentHashMap<>();
    private static volatile SenderSession senderSession;
    private static volatile ClassLoader senderHostClassLoader;

    private static DexKitHelper dexHelper;

    private static final class SenderAccountResult {
        // 1: 资料可用；-1: 接口明确返回 -404；0: 无法确定，可重试。
        final int status;
        final String name;
        final String detail;
        SenderAccountResult(int status, String name, String detail) {
            this.status = status;
            this.name = name;
            this.detail = detail;
        }
    }

    private static final class SenderSession {
        java.lang.ref.WeakReference<android.app.Activity> owner;
        final String ownerClass;
        final String dmid;
        final List<String> all;
        volatile List<String> passed = java.util.Collections.emptyList();
        volatile List<String> unchecked = java.util.Collections.emptyList();
        final Map<String, String> names = new ConcurrentHashMap<>();
        final Map<String, String> checkDetails = new ConcurrentHashMap<>();
        volatile List<String> excluded = java.util.Collections.emptyList();
        volatile boolean closed;
        volatile boolean resumePending;
        volatile boolean showingUnchecked;
        int scrollPosition;
        java.lang.ref.WeakReference<android.app.AlertDialog> dialog = new java.lang.ref.WeakReference<>(null);
        SenderSession(android.app.Activity activity, String dmid, List<String> all) {
            this.owner = new java.lang.ref.WeakReference<>(activity);
            this.ownerClass = activity.getClass().getName();
            this.dmid = dmid;
            this.all = all;
        }
    }

    public static final ConcurrentHashMap<Long, String> GLOBAL_DANMAKU_DICT = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, Long> RECENT_MSGS = new ConcurrentHashMap<>();

    private static volatile boolean spaceProbeEnabled;

    private static void enableSpaceProbe(String processName) {
        if (spaceProbeEnabled) return;
        // attach() 返回时，ActivityThread.mInitialApplication 仍可能尚未赋值。
        if (AndroidAppHelper.currentApplication() == null) {
            Log.d(TAG, "[SpaceProbe] application not ready, waiting: " + processName);
            return;
        }
        try {
            android.webkit.WebView.setWebContentsDebuggingEnabled(true);
            spaceProbeEnabled = true;
            Log.d(TAG, "[SpaceProbe] WebView debugging enabled: " + processName);
        } catch (Throwable t) {
            // 失败不置成功标志，Activity 恢复时还可重试。
            Log.d(TAG, "[SpaceProbe] enable failed: " + processName + ": " + t);
        }
    }

    private static void installSpaceProbe(XC_LoadPackage.LoadPackageParam lpparam) {
        final String processName = lpparam.processName;
        if (!lpparam.packageName.equals(processName)
                && !(lpparam.packageName + ":web").equals(processName)) return;
        try {
            final android.os.Handler mainHandler =
                    new android.os.Handler(android.os.Looper.getMainLooper());
            XposedHelpers.findAndHookMethod(android.app.Application.class, "attach",
                    Context.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            mainHandler.post(() -> enableSpaceProbe(processName));
                        }
                    });
            XposedHelpers.findAndHookMethod(android.app.Activity.class, "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            enableSpaceProbe(processName);
                        }
                    });
            // 兼容安装 Hook 时 attach() 已经执行的情况。
            mainHandler.post(() -> enableSpaceProbe(processName));
            Log.d(TAG, "[SpaceProbe] lifecycle hooks installed: " + processName);
        } catch (Throwable t) {
            Log.d(TAG, "[SpaceProbe] hook failed: " + processName + ": " + t);
        }
    }

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam, DexKitHelper helper) {
        senderHostClassLoader = lpparam.classLoader;
        dexHelper = helper;
        installSpaceProbe(lpparam);
        new DanmuSenderLookup().installLookupHooks(lpparam, helper);
    }

    private void installLookupHooks(XC_LoadPackage.LoadPackageParam lpparam, DexKitHelper helper) {
        // 通用 RPC 数据分发处理
        XC_MethodHook universalRpcHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args == null || param.args.length < 3) return;

                Object payload = param.args[2];
                if (payload == null) return;

                String className = payload.getClass().getName();

                if (className.endsWith("EventReport$Request")) {
                    parseEventReport(payload);
                }
            }
        };

        if ("com.bilibili.app.in".equals(lpparam.packageName)) {
            try {
                Class<?> function6Class = XposedHelpers.findClass("kotlin.jvm.functions.Function6", lpparam.classLoader);
                Class<?> oClass = XposedHelpers.findClass(helper.INTERNATIONAL_CHRONOS_RPC_CLASS_NAME, lpparam.classLoader);

                XposedHelpers.findAndHookMethod(oClass, helper.INTERNATIONAL_INVOKE_METHOD_NAME, Class.class, function6Class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object handlerImpl = param.args[1];
                        if (handlerImpl != null) {
                            XposedBridge.hookAllMethods(handlerImpl.getClass(), "invoke", universalRpcHook);
                        }
                    }
                });
            } catch (Throwable t) {
                log("bilibili.in fails to pin: " + t.toString());
            }
        } else {
            // 【新版本策略】：使用 DexKit 获取到的动态类名
            try {
                if (helper.CHRONOS_RPC_CLASS_NAME != null && !helper.CHRONOS_RPC_CLASS_NAME.isEmpty()) {
                    Class<?> targetClass = XposedHelpers.findClass(helper.CHRONOS_RPC_CLASS_NAME, lpparam.classLoader);
                    for (java.lang.reflect.Method method : targetClass.getDeclaredMethods()) {
                        if (method.getName().equals("invoke")) {
                            XposedBridge.hookMethod(method, universalRpcHook);
                            log("✅ Hook 新版 ChronosRpc 成功绑定类: " + helper.CHRONOS_RPC_CLASS_NAME);
                            break;
                        }
                    }
                }
            } catch (Throwable t) {
                log("Hook 新版 ChronosRpc 失败 (该版本可能不支持): " + t.getMessage());
            }

            // 【旧版本策略】：硬编码挂载经典的 ChronosMessageHandler
            try {
                String hardcodedTargetClass = "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.ChronosMessageHandler";
                Class<?> targetClass = XposedHelpers.findClass(hardcodedTargetClass, lpparam.classLoader);
                XposedBridge.hookAllMethods(targetClass, "invoke", universalRpcHook);
                log("✅ Hook 旧版 ChronosRpc 成功硬编码绑定类: " + hardcodedTargetClass);
            } catch (Throwable t) {
                log("Hook 旧版 ChronosRpc 失败 (新版可能不存在此旧类): " + t.getMessage());
            }
        }
        installSenderActivityTracking(lpparam);
        hookClipboardToJump(lpparam);
    }


    private void parseEventReport(Object payload) {
        try {
            java.lang.reflect.Field extendsArgsField = payload.getClass().getDeclaredField("extendsArgs");
            extendsArgsField.setAccessible(true);
            Object extendsArgsObj = extendsArgsField.get(payload);

            if (extendsArgsObj instanceof Map) {
                Map<?, ?> argsMap = (Map<?, ?>) extendsArgsObj;
                String keyStr = String.valueOf(argsMap.get("key"));

                log("【RPC 事件流】侦测到 Key: " + keyStr);

                // 核心兼容点：同时放行 DmsegLoader(旧) 与 CommonDanmakuWorkFlows(新)
                if ((keyStr.contains("DmsegLoader") || keyStr.contains("CommonDanmakuWorkFlows")) && argsMap.containsKey("video_id")) {
                    String cid = String.valueOf(argsMap.get("video_id"));
                    log("【客户端弹幕】加载 cid=" + cid + "，等待 worker 解码数据，不请求 XML");
                } else if (argsMap.containsKey("dmid") && argsMap.containsKey("msg")) {
                    String dmidStr = String.valueOf(argsMap.get("dmid"));
                    String msg = String.valueOf(argsMap.get("msg"));
                    try {
                        RECENT_MSGS.put(msg, Long.parseLong(dmidStr));
                        RECENT_MSG_TIMES.put(msg, android.os.SystemClock.elapsedRealtime());
                        log("【状态记录】存入近期点击记录: " + msg + " -> " + dmidStr);
                    } catch (Exception e) {
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    private void hookClipboardToJump(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("android.content.ClipboardManager", lpparam.classLoader, "setPrimaryClip", ClipData.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    ClipData clipData = (ClipData) param.args[0];
                    if (clipData != null && clipData.getItemCount() > 0) {
                        CharSequence text = clipData.getItemAt(0).getText();
                        if (text == null) return;
                        final String copiedText = text.toString();
                        log("【剪贴板】检测到复制动作，内容: " + copiedText);

                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Long targetDmid = RECENT_MSGS.remove(copiedText);
                                Long recordedAt = RECENT_MSG_TIMES.remove(copiedText);
                                if (targetDmid != null && recordedAt != null
                                        && android.os.SystemClock.elapsedRealtime() - recordedAt <= 30000L) {
                                    log("复制内容匹配！准备解析 Hash 并跳转主页...");
                                    crackAndJump(String.valueOf(targetDmid));
                                    RECENT_MSGS.remove(copiedText);
                                }
                            }
                        }, 300);
                    }
                }
            });
        } catch (Exception e) {
            log("剪贴板 Hook 失败: " + e.getMessage());
        }
    }

    public static void cacheClientDanmaku(String jsonText) {
        try {
            org.json.JSONObject batch = new org.json.JSONObject(jsonText);
            org.json.JSONArray rows = batch.getJSONArray("rows");
            int added = 0;
            for (int i = 0; i < rows.length(); i++) {
                org.json.JSONObject row = rows.getJSONObject(i);
                String hash = row.optString("hash", "");
                if (!hash.matches("(?i)[0-9a-f]{1,8}")) continue;
                try {
                    long dmid = Long.parseLong(row.getString("id"));
                    if (dmid > 0) {
                        GLOBAL_DANMAKU_DICT.put(dmid, hash);
                        added++;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            log("【客户端弹幕】cid=" + batch.optString("videoId") + ", cached=" + added);
        } catch (Throwable t) {
            log("客户端弹幕缓存失败: " + t);
        }
    }

    private void installSenderActivityTracking(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(android.app.Activity.class, "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            android.app.Activity activity = (android.app.Activity) param.thisObject;
                            senderActivity = new java.lang.ref.WeakReference<>(activity);
                            SenderSession session = senderSession;
                            android.app.Activity owner = session == null ? null : session.owner.get();
                            if (session != null && !session.closed && session.resumePending
                                    && (owner == activity
                                    || ((owner == null || owner.isDestroyed())
                                    && session.ownerClass.equals(activity.getClass().getName())))) {
                                session.owner = new java.lang.ref.WeakReference<>(activity);
                                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                    if (senderSession == session && !session.closed
                                            && senderActivity.get() == activity) {
                                        session.resumePending = false;
                                        showSenderCandidates(activity, session, session.showingUnchecked);
                                    }
                                }, 250L);
                            }
                        }
                    });
        } catch (Throwable t) {
            log("记录当前 Activity 失败: " + t);
        }
    }

    private void crackAndJump(String dmidStr) {
        final String hash;
        try {
            hash = GLOBAL_DANMAKU_DICT.get(Long.parseLong(dmidStr));
        } catch (NumberFormatException e) {
            return;
        }
        if (hash == null) {
            log("客户端缓存尚未找到 dmid=" + dmidStr);
            return;
        }
        final java.lang.ref.WeakReference<android.app.Activity> targetActivity =
                new java.lang.ref.WeakReference<>(senderActivity.get());
        new Thread(() -> {
            try {
                List<String> candidates = SENDER_CRC_CACHE.get(hash);
                if (candidates == null) {
                    candidates = BiliDanmuCrack.crack(hash);
                    if (SENDER_CRC_CACHE.size() >= 64) SENDER_CRC_CACHE.clear();
                    SENDER_CRC_CACHE.put(hash, new java.util.ArrayList<>(candidates));
                } else {
                    log("复用 CRC32 候选缓存，数量=" + candidates.size());
                }
                android.app.Activity activity = targetActivity.get();
                if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
                final SenderSession session = new SenderSession(activity, dmidStr,
                        new java.util.ArrayList<>(candidates));
                SenderSession previous = senderSession;
                if (previous != null) previous.closed = true;
                senderSession = session;
                log("开始接口检查，CRC32 候选数=" + candidates.size());
                checkSenderCandidates(session, session.all);
            } catch (Throwable t) {
                log("发送者候选解析失败: " + t);
            }
        }, "DanmuSenderCandidates").start();
    }

    private static SenderAccountResult checkSenderAccount(String uid) {
        HttpURLConnection connection = null;
        try {
            if (!uid.matches("[1-9][0-9]{0,18}")) {
                return new SenderAccountResult(0, "", "UID 格式错误");
            }
            ClassLoader loader = senderHostClassLoader;
            if (loader == null) throw new IllegalStateException("宿主 ClassLoader 未就绪");
            long timezoneHours = java.util.TimeZone.getDefault().getRawOffset() / 3600000L;
            String url = "https://app.bilibili.com/x/v2/space?vmid=" + uid
                    + "&from=&local_time=" + timezoneHours;

            // 使用宿主同款拦截器添加公共参数、预加载参数及请求头。
            // 不在模块中硬编码签名或登录凭证，也不打印最终 URL/请求头。
            Class<?> builderClass = XposedHelpers.findClass("okhttp3.Request$Builder", loader);
            Object builder = XposedHelpers.newInstance(builderClass);
            XposedHelpers.callMethod(builder, "url", url);
            XposedHelpers.callMethod(builder, "get");
            Object request = XposedHelpers.callMethod(builder, "build");
            Object interceptor = XposedHelpers.newInstance(XposedHelpers.findClass(
                    dexHelper.REQUEST_INTERCEPTOR, loader));
            Object prepared = XposedHelpers.callMethod(interceptor, "intercept", request);
            String preparedUrl = XposedHelpers.callMethod(prepared, "url").toString();
            URL parsedUrl = new URL(preparedUrl);
            if (!"https".equals(parsedUrl.getProtocol())
                    || !"app.bilibili.com".equals(parsedUrl.getHost())
                    || !"/x/v2/space".equals(parsedUrl.getPath())) {
                throw new IllegalStateException("拦截器返回了非预期地址");
            }
            connection = (HttpURLConnection) parsedUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            Object headers = XposedHelpers.callMethod(prepared, "headers");
            int headerCount = ((Number) XposedHelpers.callMethod(headers, "size")).intValue();
            for (int i = 0; i < headerCount; i++) {
                String key = (String) XposedHelpers.callMethod(headers, "name", i);
                String value = (String) XposedHelpers.callMethod(headers, "value", i);
                if (!"Host".equalsIgnoreCase(key) && !"Content-Length".equalsIgnoreCase(key)
                        && !"Accept-Encoding".equalsIgnoreCase(key)) {
                    connection.addRequestProperty(key, value);
                }
            }
            // 固定读取未压缩 JSON，避免继承宿主网络栈的压缩约定。
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Accept", "application/json");
            int httpStatus = connection.getResponseCode();
            if (httpStatus != 200) {
                return new SenderAccountResult(0, "", "HTTP " + httpStatus);
            }
            try (java.io.InputStream input = connection.getInputStream();
                 java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 4 * 1024 * 1024) {
                        throw new java.io.IOException("主页响应过大");
                    }
                    output.write(buffer, 0, count);
                }
                return classifySenderAccount(uid, new org.json.JSONObject(
                        output.toString("UTF-8")));
            }
        } catch (Throwable t) {
            // 异常只记录类型，避免宿主异常消息包含 access_key/sign 等参数。
            Log.d(TAG, "[SenderSpaceCheck] uid=" + uid + " failed=" + t.getClass().getName());
            return new SenderAccountResult(0, "", "请求或解析失败：" + t.getClass().getSimpleName());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static SenderAccountResult classifySenderAccount(String uid, org.json.JSONObject json)
            throws org.json.JSONException {
        int code = json.getInt("code");
        if (code == -404) return new SenderAccountResult(-1, "", "主页接口返回 -404");
        if (code != 0) return new SenderAccountResult(0, "", "业务错误码 " + code);
        org.json.JSONObject data = json.optJSONObject("data");
        org.json.JSONObject card = data == null ? null : data.optJSONObject("card");
        if (card == null || !uid.equals(card.optString("mid", ""))) {
            return new SenderAccountResult(0, "", "返回资料缺失或 UID 不匹配");
        }
        return new SenderAccountResult(1, card.optString("name", ""), "主页资料检查通过");
    }

    private static void checkSenderCandidates(SenderSession session, List<String> toCheck) {
        java.util.LinkedHashSet<String> passed = new java.util.LinkedHashSet<>(session.passed);
        java.util.LinkedHashSet<String> unchecked = new java.util.LinkedHashSet<>(session.unchecked);
        java.util.LinkedHashSet<String> excluded = new java.util.LinkedHashSet<>(session.excluded);
        for (String uid : toCheck) {
            if (session.closed || senderSession != session) return;
            SenderAccountResult result = checkSenderAccount(uid);
            if (session.closed || senderSession != session) return;
            passed.remove(uid);
            unchecked.remove(uid);
            excluded.remove(uid);
            session.checkDetails.put(uid, result.detail);
            session.names.remove(uid);
            if (result.status == 1) {
                passed.add(uid);
                session.names.put(uid, result.name);
            } else if (result.status == -1) {
                excluded.add(uid);
            } else {
                unchecked.add(uid);
            }
            Log.d(TAG, "[SenderSpaceCheck] uid=" + uid + " status=" + result.status
                    + " detail=" + result.detail);
        }
        session.passed = new java.util.ArrayList<>(passed);
        session.unchecked = new java.util.ArrayList<>(unchecked);
        session.excluded = new java.util.ArrayList<>(excluded);
        log("主页检查完成：通过=" + passed.size() + "，明确 -404=" + excluded.size()
                + "，检查失败=" + unchecked.size());
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            android.app.Activity activity = session.owner.get();
            if (session.closed || senderSession != session || activity == null
                    || activity.isFinishing() || activity.isDestroyed()) return;
            if (senderActivity.get() == activity) {
                showSenderCandidates(activity, session, false);
            } else {
                session.resumePending = true;
            }
        });
    }

    private static void showSenderCandidates(android.app.Activity activity,
                                             SenderSession session, boolean showUnchecked) {
        if (session.closed || senderSession != session || activity.isFinishing()
                || activity.isDestroyed()) return;
        if (session.showingUnchecked != showUnchecked) session.scrollPosition = 0;
        session.showingUnchecked = showUnchecked;
        android.app.AlertDialog old = session.dialog.get();
        if (old != null && old.isShowing()) old.dismiss();
        final List<String> visible = new java.util.ArrayList<>(
                showUnchecked ? session.unchecked : session.passed);
        String[] labels = new String[visible.size()];
        for (int i = 0; i < labels.length; i++) {
            String uid = visible.get(i);
            String name = session.names.get(uid);
            labels[i] = (name == null || name.isEmpty() ? "" : name + "\n") + "UID " + uid;
            if (showUnchecked) labels[i] += "\n" + session.checkDetails.get(uid);
        }
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(activity)
                .setTitle(showUnchecked ? "检查失败的候选（可重试）"
                        : "主页检查通过的候选（身份未确认）")
                .setNegativeButton("关闭", (dialog, which) -> session.closed = true);
        if (visible.isEmpty()) {
            builder.setMessage(session.all.isEmpty()
                    ? "未找到候选。当前反推范围为 1～11 位 UID。"
                    : "暂时没有候选通过检查。已排除 " + session.excluded.size()
                            + " 个主页接口返回 -404 的候选；其他检查失败项可重试。");
        } else {
            // Install the click listener ourselves after show(): default setItems
            // dismisses the dialog and loses navigation state.
            builder.setAdapter(new android.widget.ArrayAdapter<>(activity,
                    android.R.layout.simple_list_item_1, labels), null);
        }
        if (!showUnchecked && !session.unchecked.isEmpty()) {
            builder.setNeutralButton("检查失败(" + session.unchecked.size() + ")",
                    (dialog, which) -> showSenderCandidates(activity, session, true));
        } else if (showUnchecked) {
            builder.setNeutralButton("返回已通过列表",
                    (dialog, which) -> showSenderCandidates(activity, session, false));
            builder.setPositiveButton("重新检查", (dialog, which) -> {
                new Thread(() -> checkSenderCandidates(session,
                        new java.util.ArrayList<>(session.unchecked)), "DanmuSenderRecheck").start();
            });
        }
        android.app.AlertDialog dialog = builder.create();
        session.dialog = new java.lang.ref.WeakReference<>(dialog);
        dialog.setOnCancelListener(ignored -> session.closed = true);
        dialog.show();
        if (!visible.isEmpty()) {
            dialog.getListView().setSelection(session.scrollPosition);
            dialog.getListView().setOnItemClickListener((parent, view, position, id) -> {
                session.scrollPosition = dialog.getListView().getFirstVisiblePosition();
                session.resumePending = true;
                dialog.dismiss();
                try {
                    activity.startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("bilibili://space/" + visible.get(position))));
                } catch (Throwable t) {
                    session.resumePending = false;
                    log("打开候选主页失败: " + t);
                    showSenderCandidates(activity, session, showUnchecked);
                }
            });
        }
    }

    private static void log(String msg) {
        if (IS_DEBUG) Log.d(TAG, msg);
    }
}
