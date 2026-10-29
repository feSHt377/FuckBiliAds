package com.fuck.bili.ads;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.fuck.bili.ads.core.Host;
import com.fuck.bili.ads.ui.FbaLog;
import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GlobalAdSniperEngine {

    private static final String TAG = "GlobalAdSniperEngine";


    /**
     * 测试开关：true 时把识别到广告的原始响应追加写入应用外部私有目录
     */
    public static final boolean IS_TESTING = BuildConfig.DEBUG;

    /**
     * 是否关闭主页自动刷新（切后台再回来不自动刷新）
     */
    public static final boolean DISABLE_AUTO_REFRESH = true;

    /**
     * 是否开启自定义主页列数
     */
    public static final boolean ENABLE_CUSTOM_COLUMN = true;
    /**
     * 自定义主页列数（默认通常是 2，改成 1 为单列大图卡片）
     */
    public static final int CUSTOM_COLUMN_COUNT = 2;

    // ====================================================

    private static final long BODY_REQUEST_BYTES = 4L * 1024 * 1024;
    private static final char BACKSLASH = '\\';
    private static final String WS = " \t\r\n";

    private static Class<?> sResponseBodyCls;
    private static Class<?> sMediaTypeCls;
    private static Method sCreateMethod;
    private static Method sContentTypeMethod;

    public static void hook(ClassLoader classLoader,DexKitHelper helper) {

        try {
            Class<?> parser = Reflect.findClass("com.bilibili.pegasus.request.PegasusGsonParser", classLoader);
            Method parse;
            if (Host.isHd()) {
                parse = null;
                for (Method method : parser.getDeclaredMethods()) {
                    if (method.getParameterCount() == 1
                            && method.getParameterTypes()[0].getName().equals("okhttp3.ResponseBody")) {
                        parse = method;
                        break;
                    }
                }
                if (parse == null) {
                    throw new NoSuchMethodException("HD Pegasus parser(ResponseBody)");
                }
            } else {
                parse = NativeHooks.method(parser,
                        helper.PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME,
                        Reflect.findClass("okhttp3.ResponseBody", classLoader));
            }
            NativeHooks.hook("GlobalAdSniperEngine.PegasusParse", parse, chain -> {
                Object responseBody = chain.getArg(0);
                Object[] args = null;
                if (responseBody != null) {
                    try {
                        // 1. 读出原始 JSON 文本
                        String json = readBodyText(responseBody);
                        if (json != null && json.length() >= 64) {
                            // 2. 修改云控配置（禁止刷新、自定义列数）
                            String configModifiedJson = modifyCloudConfig(json);
                            boolean isConfigModified = !configModifiedJson.equals(json);
                            json = configModifiedJson;

                            // 3. 快速预判，如果不含广告且也没改云控，直接放行走原逻辑
                            boolean hasAd = mayContainAd(json);
                            String cleaned = json;
                            if (hasAd) {
                                String noAdJson = removeAdItems(json);
                                if (noAdJson != null && noAdJson.length() < json.length()) {
                                    cleaned = noAdJson;
                                    Log.d(TAG, "[AdFilter] 响应已净化广告: " + json.length() + " -> " + cleaned.length() + " 字符");

                                    if (IS_TESTING) saveJsonToFile(json);
                                }
                            }

                            // 如果文本完全没变，直接返回
                            if (!cleaned.equals(json) || isConfigModified) {
                                // 4. 构造修改后的 ResponseBody 并替换入参
                                Object newBody = buildResponseBody(responseBody, cleaned);
                                if (newBody != null) {
                                    args = chain.getArgs().toArray();
                                    args[0] = newBody;
                                }
                            }
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "净化或修改响应失败，已回退", t);
                    }
                }

                Object result = args == null ? chain.proceed() : chain.proceed(args);

                if (result == null || result.getClass().getName().contains("ResponseBody")) {
                    return result;
                }
                cleanResponseData(result);
                return result;
            });
        } catch (Throwable e) {
            Log.e(TAG, "Hook PegasusGsonParser.g 失败: " + e.getMessage());
        }

        installSplashFilter(classLoader);
    }

    private static volatile boolean splashHookInstalled;
    private static final java.util.concurrent.atomic.AtomicLong gsonCalls =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong splashMatches =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong splashRemoved =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.ArrayDeque<String> recentGson = new java.util.ArrayDeque<>();

    private static void installSplashFilter(ClassLoader classLoader) {
        try {
            Class<?> gsonClass = Reflect.findClass("com.google.gson.Gson", classLoader);
            int hooks = NativeHooks.hookAllMethods("GlobalAdSniperEngine.SplashFromJson",
                    gsonClass, "fromJson", chain -> {
                // 启动期 fromJson 调用量很大：先做一次零分配的判断，
                // 只有命中开屏响应才继续，调试字符串也只在 IS_TESTING 下拼。
                Object result = chain.proceed();
                String actual = result == null ? "null" : result.getClass().getName();
                boolean splash = actual.contains("SplashListResponse");
                java.util.List<Object> args = chain.getArgs();
                if (IS_TESTING) {
                    gsonCalls.incrementAndGet();
                    String target = args.size() >= 2 ? String.valueOf(args.get(1)) : "<unknown>";
                    String input = args.isEmpty() || args.get(0) == null
                            ? "null" : args.get(0).getClass().getName();
                    synchronized (recentGson) {
                        if (recentGson.size() == 40) recentGson.removeFirst();
                        recentGson.addLast("thread=" + Thread.currentThread().getName()
                                + ", input=" + input + ", target=" + target
                                + ", result=" + actual);
                    }
                    if (splash) Log.d(TAG, "[SplashParse] input=" + input
                            + ", target=" + target + ", result=" + actual
                            + ", overload=" + chain.getExecutable());
                }
                // 不读取 Reader/JsonReader，也不消费响应流。所有 fromJson 重载统一处理结果。
                if (!splash || result == null) return result;
                splashMatches.incrementAndGet();
                try {
                    int count = clearSplashList(result);
                    if (count > 0) {
                        splashRemoved.addAndGet(count);
                        FbaLog.i("[SPLASH] 解析层清空开屏列表 " + count + " 条");
                    Log.d(TAG, "[SplashFilter] cleared=" + count + ", result=" + actual);
                        if (IS_TESTING) Log.d(TAG, "[SplashParseStack]\n"
                                + Log.getStackTraceString(new Throwable("Splash parsed")));
                    }
                } catch (Throwable error) {
                    Log.d(TAG, "[SplashFilter] object filtering failed: "
                            + Log.getStackTraceString(error));
                }
                return result;
            });
            splashHookInstalled = hooks > 0;
            Log.d(TAG, "[SplashInstall] installed=" + splashHookInstalled + ", overloads=" + hooks
                    + ", loader=" + gsonClass.getClassLoader() + ", diagnostics=" + IS_TESTING);
        } catch (Throwable error) {
            Log.d(TAG, "[SplashInstall] failed: " + Log.getStackTraceString(error));
        }
    }

    /** 仅处理已确认的 data.list / list，兼容字段混淆但保留 SerializedName 的情况。 */
    private static int clearSplashList(Object response) throws Exception {
        Object owner = response;
        Field listField = findJsonField(owner.getClass(), "list");
        if (listField == null) {
            Field data = findJsonField(owner.getClass(), "data");
            if (data != null) owner = data.get(owner);
            if (owner == null) return 0;
            listField = findJsonField(owner.getClass(), "list");
        }
        if (listField == null) {
            if (IS_TESTING) {
                Log.d(TAG, "[SplashShape] no list field, response=" + response.getClass().getName());
                dumpFields("response", response);
                if (owner != response) dumpFields("data", owner);
            }
            return 0;
        }
        Object value = listField.get(owner);
        if (!(value instanceof java.util.List)) {
            if (IS_TESTING) Log.d(TAG, "[SplashShape] list is "
                    + (value == null ? "null" : value.getClass().getName()));
            return 0;
        }
        java.util.List<?> list = (java.util.List<?>) value;
        int count = list.size();
        if (count == 0) return 0;
        if (listField.getType().isAssignableFrom(ArrayList.class)) {
            listField.set(owner, new ArrayList<>());
        } else {
            list.clear();
        }
        return count;
    }

    private static Field findJsonField(Class<?> clazz, String jsonName) {
        // 先检查注解，再检查 Java 字段名，避免短字段名混淆后的误命中。
        for (int pass = 0; pass < 2; pass++) {
            for (Class<?> current = clazz; current != null && current != Object.class;
                 current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                    boolean matches = pass == 1 && field.getName().equals(jsonName);
                    if (pass == 0) {
                        for (java.lang.annotation.Annotation annotation : field.getDeclaredAnnotations()) {
                            if (!annotation.annotationType().getName().equals("com.google.gson.annotations.SerializedName")) continue;
                            try {
                                Object value = annotation.annotationType().getMethod("value").invoke(annotation);
                                matches = jsonName.equals(value);
                                if (!matches) {
                                    Object alternate = annotation.annotationType().getMethod("alternate").invoke(annotation);
                                    if (alternate instanceof String[]) {
                                        for (String name : (String[]) alternate) if (jsonName.equals(name)) matches = true;
                                    }
                                }
                            } catch (Exception ignored) { }
                        }
                    }
                    if (matches) { field.setAccessible(true); return field; }
                }
            }
        }
        return null;
    }

    /** 由展示层兜底调用；仅 Debug 记录，不阻止原来的跳过逻辑。 */
    public static void traceSplashFallback(Object splash) {
        if (!IS_TESTING) return;
        try {
            Log.d(TAG, "[SplashFallback] installed=" + splashHookInstalled
                    + ", gsonCalls=" + gsonCalls.get() + ", matches=" + splashMatches.get()
                    + ", removed=" + splashRemoved.get()
                    + ", splash=" + (splash == null ? "null" : splash.getClass().getName()));
            Log.d(TAG, "[SplashFallbackStack]\n" + Log.getStackTraceString(new Throwable("Splash ready")));
            List<String> snapshot;
            synchronized (recentGson) { snapshot = new ArrayList<>(recentGson); }
            for (String item : snapshot) Log.d(TAG, "[SplashRecentGson] " + item);
            dumpFields("splash", splash);
        } catch (Throwable error) {
            Log.d(TAG, "[SplashFallback] probe failed: " + Log.getStackTraceString(error));
        }
    }

    /** 只记录类型和集合大小；不输出广告正文、Cookie 或账号字段值。 */
    private static void dumpFields(String label, Object instance) {
        if (instance == null) return;
        int logged = 0;
        for (Class<?> current = instance.getClass(); current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                if (field.getType().isPrimitive() || field.getType() == String.class) continue;
                if (++logged > 45) return;
                try {
                    field.setAccessible(true);
                    Object value = field.get(instance);
                    Log.d(TAG, "[SplashField] " + label + "." + current.getSimpleName() + "." + field.getName()
                            + ": declared=" + field.getType().getName() + ", actual="
                            + (value == null ? "null" : value.getClass().getName())
                            + (value instanceof java.util.Collection ? ", size=" + ((java.util.Collection<?>) value).size() : ""));
                } catch (Exception error) {
                    Log.d(TAG, "[SplashField] " + label + "." + field.getName() + ": " + error.getClass().getSimpleName());
                }
            }
        }
    }

    // ==================== 云控修改逻辑 ====================

    /**
     * 利用正则替换修改 B 站下发的 config 字段
     */
    private static String modifyCloudConfig(String json) {
        String result = json;

        if (DISABLE_AUTO_REFRESH) {
            // 将各类刷新超时时间设置为 99999999 秒（约 3 年不刷新）
            result = result.replaceAll("\"auto_refresh_time\"\\s*:\\s*\\d+", "\"auto_refresh_time\":99999999");
            result = result.replaceAll("\"auto_refresh_time_by_appear\"\\s*:\\s*\\d+", "\"auto_refresh_time_by_appear\":99999999");
            result = result.replaceAll("\"auto_refresh_time_by_active\"\\s*:\\s*\\d+", "\"auto_refresh_time_by_active\":99999999");
            // 关闭切回前台自动刷新开关
            result = result.replaceAll("\"is_back_auto_refresh\"\\s*:\\s*1", "\"is_back_auto_refresh\":0");
        }

        if (ENABLE_CUSTOM_COLUMN) {
            // 修改主页流显示的列数
            result = result.replaceAll("\"column\"\\s*:\\s*\\d+", "\"column\":" + CUSTOM_COLUMN_COUNT);
        }

        if (!result.equals(json)) {
            Log.d(TAG, "[CloudConfig] 已成功修改响应体中的云控参数 (刷新控制/列数)");
        }

        return result;
    }

    // ==================== 响应体读取 / 重建 ====================

    private static String readBodyText(Object responseBody) throws Throwable {
        Object source = responseBody.getClass().getMethod("source").invoke(responseBody);
        source.getClass().getMethod("request", long.class).invoke(source, BODY_REQUEST_BYTES);

        Object buffer = source.getClass().getMethod("getBuffer").invoke(source);
        Object cloned = buffer.getClass().getMethod("clone").invoke(buffer);
        Method readString = cloned.getClass().getMethod("readString", Charset.class);
        return (String) readString.invoke(cloned, StandardCharsets.UTF_8);
    }

    private static Object buildResponseBody(Object originBody, String text) {
        try {
            if (sResponseBodyCls == null) {
                ClassLoader cl = originBody.getClass().getClassLoader();
                sResponseBodyCls = Class.forName("okhttp3.ResponseBody", false, cl);
                sMediaTypeCls = Class.forName("okhttp3.MediaType", false, cl);
                sCreateMethod = sResponseBodyCls.getMethod("create", sMediaTypeCls, String.class);
                sContentTypeMethod = sResponseBodyCls.getMethod("contentType");
            }

            Object mediaType = null;
            try {
                mediaType = sContentTypeMethod.invoke(originBody);
            } catch (Throwable ignored) {
            }
            if (mediaType == null) {
                mediaType = sMediaTypeCls.getMethod("parse", String.class)
                        .invoke(null, "application/json; charset=utf-8");
            }
            return sCreateMethod.invoke(null, mediaType, text);
        } catch (Throwable t) {
            Log.e(TAG, "构造 ResponseBody 失败", t);
            return null;
        }
    }

    // ==================== 广告剔除（括号配平切除） ====================

    private static boolean mayContainAd(String json) {
        return json.contains("\"cm_v2\"")
                || json.contains("\"is_ad_loc\":true")
                || json.contains("\"is_ad_loc\": true")
                || json.contains("\"card_goto\":\"ad_")
                || json.contains("\"card_goto\": \"ad_");
    }

    private static String removeAdItems(String json) {
        int keyAt = json.indexOf("\"items\":[");
        if (keyAt < 0) keyAt = json.indexOf("\"items\": [");
        if (keyAt < 0) return null;

        int bracket = json.indexOf('[', keyAt);
        if (bracket < 0) return null;

        List<int[]> elements = scanArrayElements(json, bracket);
        if (elements.isEmpty()) return null;

        List<int[]> cutSpans = new ArrayList<>();
        for (int[] range : elements) {
            String segment = json.substring(range[0], range[1]);
            if (isAdSegment(segment)) {
                cutSpans.add(expandWithComma(json, range[0], range[1]));
            }
        }
        if (cutSpans.isEmpty()) return null;

        // 从后往前删，避免下标失效
        Collections.sort(cutSpans, new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return b[0] - a[0];
            }
        });

        StringBuilder sb = new StringBuilder(json);
        for (int[] span : cutSpans) {
            // 【新增日志】提取即将被切除的字符串内容并输出
            String removedContent = json.substring(span[0], span[1]);
            if (IS_TESTING) Log.d(TAG, "[AdFilter] ✂️ 成功剔除广告节点数据:\n" + removedContent);
            sb.delete(span[0], span[1]);
        }
        return sb.toString();
    }

    private static List<int[]> scanArrayElements(String s, int start) {
        List<int[]> elements = new ArrayList<>();
        int i = start + 1;
        int depth = 0;
        int elementStart = -1;
        boolean inString = false;
        boolean escaped = false;

        while (i < s.length()) {
            char c = s.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == BACKSLASH) escaped = true;
                else if (c == '"') inString = false;
            } else {
                if (c == '"') {
                    inString = true;
                    if (depth == 0 && elementStart < 0) elementStart = i;
                } else if (c == '[' || c == '{') {
                    if (depth == 0 && elementStart < 0) elementStart = i;
                    depth++;
                } else if (c == ']' || c == '}') {
                    if (c == ']' && depth == 0) {
                        if (elementStart >= 0) elements.add(new int[]{elementStart, i});
                        return elements;
                    }
                    depth--;
                } else if (c == ',') {
                    if (depth == 0) {
                        if (elementStart >= 0) elements.add(new int[]{elementStart, i});
                        elementStart = -1;
                    }
                } else if (WS.indexOf(c) < 0) {
                    if (depth == 0 && elementStart < 0) elementStart = i;
                }
            }
            i++;
        }
        return elements;
    }

    private static boolean isAdSegment(String seg) {
        if (seg.contains("\"is_ad_loc\":true") || seg.contains("\"is_ad_loc\": true")) return true;
        int keyAt = seg.indexOf("\"card_goto\"");
        if (keyAt >= 0) {
            int colon = seg.indexOf(':', keyAt);
            if (colon >= 0) {
                int quote = seg.indexOf('"', colon + 1);
                if (quote >= 0 && seg.startsWith("ad_", quote + 1)) return true;
            }
        }
        return seg.contains("\"card_type\":\"cm_v2\"") || seg.contains("\"card_type\": \"cm_v2\"");
    }

    private static int[] expandWithComma(String json, int from, int to) {
        int j = to;
        while (j < json.length() && WS.indexOf(json.charAt(j)) >= 0) j++;
        if (j < json.length() && json.charAt(j) == ',') return new int[]{from, j + 1};

        int k = from - 1;
        while (k >= 0 && WS.indexOf(json.charAt(k)) >= 0) k--;
        if (k >= 0 && json.charAt(k) == ',') return new int[]{k, to};
        return new int[]{from, to};
    }

    // ==================== 对象层兜底清洗 ====================

    private static void cleanResponseData(Object generalResponse) {
        try {
            Object pegasusResponse = Reflect.getObjectField(generalResponse, "data");
            if (pegasusResponse == null) return;

            List<?> rawList = null;
            Field targetField = null;
            Class<?> currentClass = pegasusResponse.getClass();

            while (currentClass != null && currentClass != Object.class) {
                for (Field field : currentClass.getDeclaredFields()) {
                    if (List.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        Object val = field.get(pegasusResponse);
                        if (val instanceof List) {
                            rawList = (List<?>) val;
                            targetField = field;
                            break;
                        }
                    }
                }
                if (rawList != null) break;
                currentClass = currentClass.getSuperclass();
            }

            if (rawList == null || rawList.isEmpty()) return;

            List<Object> cleanList = new ArrayList<>();
            int adCount = 0;
            for (Object item : rawList) {
                if (isAdItem(item)) adCount++;
                else cleanList.add(item);
            }

            if (adCount > 0 && targetField != null) {
                targetField.set(pegasusResponse, cleanList);
                Log.d(TAG, "[PegasusFilter] 对象层兜底剔除 " + adCount + " 条广告");
            }
        } catch (Throwable t) {
            Log.e(TAG, "对象层清洗异常", t);
        }
    }

    private static boolean isAdItem(Object item) {
        if (item == null) return false;
        Class<?> clazz = item.getClass();

        if ("aa.e".equals(clazz.getName())) return true;
        for (Class<?> iface : clazz.getInterfaces()) {
            String name = iface.getName();
            if (name.contains("IAdInfo") || name.contains("IAdFeedTagMoveInfo")) return true;
        }
        try {
            Method getBizType = clazz.getMethod("getBizType");
            Object biz = getBizType.invoke(item);
            if (biz != null && biz.toString().toUpperCase().contains("AD")) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    // ==================== 测试存档 ====================

    private static void saveJsonToFile(String jsonString) {
        try {
            Application currentApp = Host.currentApplication();
            if (currentApp == null) return;
            File dir = currentApp.getExternalFilesDir(null);
            if (dir == null) return;
            File logFile = new File(dir, "bili_ad_jsons.log");
            FileWriter writer = new FileWriter(logFile, true);
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            writer.append("\n================ [").append(timestamp).append("] ================\n");
            writer.append(jsonString).append("\n");
            writer.flush();
            writer.close();
        } catch (Exception e) {
            Log.e(TAG, "保存 JSON 到文件失败", e);
        }
    }
}