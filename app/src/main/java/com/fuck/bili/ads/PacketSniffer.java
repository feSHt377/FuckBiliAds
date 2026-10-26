package com.fuck.bili.ads;

import android.util.Log;

import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Type;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 哔哩哔哩全局 API 抓包工具 (流式全覆盖版)
 *
 * 改进点：拦截所有 fromJson 重载方法，特别是针对 Reader 的流式解析。
 * 完美解决大包数据绕过 String 拦截直接进入 JsonReader 的问题。
 */
public class PacketSniffer {

    private static final String TAG = "BiliSniffer";

    public static void hook(ClassLoader classLoader) {
        // 核心安全锁：Release 构建下直接跳过，零性能损耗
        if (!BuildConfig.DEBUG) {
            return;
        }

        Log.d(TAG, "已处于 Debug 模式，全局 API 抓包模块启动");

        try {
            Class<?> gsonClass = XposedHelpers.findClass("com.google.gson.Gson", classLoader);

            // hookAllMethods 能够一次性拦截 fromJson 的所有重载形式 (String, Reader, JsonElement, JsonReader)
            XposedBridge.hookAllMethods(gsonClass, "fromJson", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args == null || param.args.length < 2) return;

                    Object inputArg = param.args[0];
                    Object typeArg = param.args[1];
                    String typeName = (typeArg instanceof Type) ? typeArg.toString() : (typeArg != null ? typeArg.getClass().getName() : "UnknownType");

                    if (inputArg instanceof String) {
                        // 1. 命中普通 String 解析
                        String jsonString = (String) inputArg;
                        if (isTargetApi(jsonString)) {
                            printLog("[String]", typeName, jsonString);
                        }
                    }
                    else if (inputArg instanceof Reader) {
                        // 2. 命中 Reader 流式解析 (Retrofit/OkHttp 常用)
                        // ⚠️ 警告: 读取 Reader 会消耗掉流，必须克隆流重新赋值给参数
                        Reader reader = (Reader) inputArg;
                        StringBuilder sb = new StringBuilder();
                        char[] buffer = new char[4096];
                        int charsRead;
                        while ((charsRead = reader.read(buffer)) != -1) {
                            sb.append(buffer, 0, charsRead);
                        }
                        String jsonString = sb.toString();

                        // 重建一个 StringReader 替换原参数，保证原应用能继续正常读取
                        param.args[0] = new StringReader(jsonString);

                        if (isTargetApi(jsonString)) {
                            printLog("[Reader Stream]", typeName, jsonString);
                        }
                    }
                    else if (inputArg != null) {
                        // 3. 命中 JsonElement 或被混淆的 JsonReader (如源码中的 OR0.a)
                        String className = inputArg.getClass().getName();
                        if (className.contains("JsonElement") || className.contains("JsonObject") || className.contains("JsonArray")) {
                            String jsonString = inputArg.toString();
                            if (isTargetApi(jsonString)) {
                                printLog("[JsonElement]", typeName, jsonString);
                            }
                        }
                        // 注: 源码中 OR0.a (JsonReader) 内部也包裹了 Reader，
                        // 但大部分框架会优先触发 fromJson(Reader, Type) 这层外壳，已被上面的 if 捕获。
                    }
                }
            });
            Log.d(TAG, "Gson 全局拦截器挂载成功");
        } catch (Throwable t) {
            Log.e(TAG, "Hook Gson.fromJson 失败", t);
        }
    }

    /**
     * 过滤规则：只打印包含 B 站标准 API 格式的 JSON，防止打印过多无关配置
     */
    private static boolean isTargetApi(String jsonString) {
        if (jsonString == null || jsonString.length() < 50) return false;
        // B 站接口基本都含有 "code" 和 "message" 字段
        return jsonString.startsWith("{") && jsonString.contains("\"code\"") && jsonString.contains("\"message\"");
    }

    /**
     * 分段打印长日志，防止 Logcat 截断
     */
    private static void printLog(String tagPrefix, String typeName, String jsonString) {
        Log.d(TAG, "==== " + tagPrefix + " 目标类: " + typeName + " ====");

        // Logcat 单条上限 4000 字符，超长 JSON 需分段打印
        int maxLogSize = 3900;
        for (int i = 0; i <= jsonString.length() / maxLogSize; i++) {
            int start = i * maxLogSize;
            int end = Math.min((i + 1) * maxLogSize, jsonString.length());
            Log.d(TAG, jsonString.substring(start, end));
        }
    }
}