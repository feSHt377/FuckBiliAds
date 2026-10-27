package com.fuck.bili.ads;

import android.util.Log;
import android.widget.Toast;

import com.fuck.bili.ads.core.Host;
import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class VIPPredict {
    private static final String TAG = "VIP_Predict";

    public static void hook(ClassLoader classLoader, DexKitHelper helper) {
        if (BuildConfig.DEBUG) {
            try {
                Class<?> type = Reflect.findClass(
                        "com.bilibili.tensorflow.model.mem.VipOpenMembershipPredictTFClient", classLoader);
                Method target = Reflect.findMethod(type,
                        helper.VIP_OPEN_MEMBERSHIP_PREDICT_SCORE_METHOD_NAME,
                        new Object[]{null, null}, false);
                NativeHooks.hook("VIPPredict.DebugScore", target, chain -> {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                            Toast.makeText(Host.currentApplication(),
                                    "🎯 成功拦截并篡改VIP预测模型！", Toast.LENGTH_SHORT).show());
                    List<?> featureList = (List<?>) chain.getArg(0);
                    ByteBuffer buffer = (ByteBuffer) chain.getArg(1);

                    StringBuilder sb = new StringBuilder();
                    String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(new Date());
                    sb.append("========== ").append(time).append(" ==========\n");

                    if (featureList != null) {
                        sb.append("[Feature List (Size: ").append(featureList.size()).append(")]\n");
                        sb.append(featureList.toString()).append("\n");
                    } else {
                        sb.append("[Feature List]: null\n");
                    }

                    if (buffer != null) {
                        sb.append("[ByteBuffer (Model Capacity)]\n");
                        sb.append(buffer.capacity()).append(" bytes\n");
                    }

                    sb.append("[Trigger StackTrace]\n");
                    sb.append(Log.getStackTraceString(new Throwable())).append("\n");
                    sb.append("=========================================\n\n");

                    String logDirPath = "/data/data/tv.danmaku.bili/files/";
                    File logDir = new File(logDirPath);
                    if (!logDir.exists()) {
                        logDir.mkdirs();
                    }

                    File logFile = new File(logDirPath, "vip_model_trigger_log.txt");
                    try (FileWriter fw = new FileWriter(logFile, true)) {
                        fw.write(sb.toString());
                        fw.flush();
                        Log.d("webviewAd", "📝 成功记录模型触发参数及堆栈，保存至: " + logFile.getAbsolutePath());
                    } catch (Exception e) {
                        Log.e("webviewAd", "文件写入失败: " + e.getMessage());
                    }

                    Log.d("webviewAd", "🎯 拦截模型执行，强制返回 0.95f");
                    return 0.95f;
                });
            } catch (Exception e) {
                Log.e("webviewAd", "Hook 预测模型失败: " + e.getMessage());
            }
        } else {
            try {
                Class<?> type = Reflect.findClass(helper.VIP_PREDICT_REQUEST_CLASS_NAME, classLoader);
                Class<?> call = Reflect.findClass("retrofit2.Call", classLoader);
                Class<?> response = Reflect.findClass("retrofit2.Response", classLoader);
                Method target = NativeHooks.method(type, "onResponse", call, response);
                NativeHooks.hook("VIPPredict.ReleaseResponse", target, chain -> {
                    Object callArg = chain.getArg(0);
                    Object responseArg = chain.getArg(1);
                    if (callArg == null || responseArg == null) {
                        return chain.proceed();
                    }

                    try {
                        Object request = Reflect.callMethod(callArg, "request");
                        Object httpUrl = Reflect.callMethod(request, "url");
                        String urlString = httpUrl.toString();

                        // 1. 锁定我们要拦截的三个“广告/活动”物料接口
                        boolean isAdTarget = urlString.contains("/pgc/activity/deliver/material/receive") ||
                                urlString.contains("/x/vip/gsm/activity/preload") ||
                                urlString.contains("/pgc/season/player/ogv/cards");

                        if (isAdTarget) {
                            Log.d(TAG, "🔥 逮到目标接口，准备拦截篡改: " + urlString);
                            Object bodyObj = Reflect.callMethod(responseArg, "body");

                            // 2. 确认返回体是 B站的成功包装类
                            if (bodyObj != null
                                    && bodyObj.getClass().getName().endsWith("BiliApiResponse$Success")) {
                                // 3. 暴力反射，将包装类里面的泛型数据全部置为 null
                                Field[] fields = bodyObj.getClass().getDeclaredFields();
                                for (Field field : fields) {
                                    field.setAccessible(true);
                                    field.set(bodyObj, null);
                                }
                                Log.d(TAG, "✅ 拦截成功！已将该接口返回的真实数据强制置空。");
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "拦截篡改发生异常: " + e.getMessage());
                    }
                    return chain.proceed();
                });
            } catch (Exception e) {
                Log.e(TAG, "response hook found exception: " + e.getMessage());
            }
        }
    }
}
