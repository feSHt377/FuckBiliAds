package com.fuck.bili.ads;

import android.app.AndroidAppHelper;
import android.util.Log;
import android.widget.Toast;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class VIPPredict {
    private static final String TAG = "VIP_Predict";

    public static void hook(ClassLoader classLoader,DexKitHelper helper) {
        if(BuildConfig.DEBUG) {
            try {
                XposedHelpers.findAndHookMethod(
                        "com.bilibili.tensorflow.model.mem.VipOpenMembershipPredictTFClient",
                        classLoader,
                        helper.VIP_OPEN_MEMBERSHIP_PREDICT_SCORE_METHOD_NAME,
                        List.class,
                        ByteBuffer.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> android.widget.Toast.makeText(AndroidAppHelper.currentApplication(), "🎯 成功拦截并篡改VIP预测模型！", android.widget.Toast.LENGTH_SHORT).show());
                                List<?> featureList = (List<?>) param.args[0];
                                ByteBuffer buffer = (ByteBuffer) param.args[1];

                                // 1. 组装要记录的日志内容
                                StringBuilder sb = new StringBuilder();
                                String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
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

                                // 👉 新增：抓取并打印完整的调用堆栈
                                sb.append("[Trigger StackTrace]\n");
                                sb.append(Log.getStackTraceString(new Throwable())).append("\n");

                                sb.append("=========================================\n\n");

                                // 2. 写入 B站的包数据目录
                                String logDirPath = "/data/data/tv.danmaku.bili/files/";
                                File logDir = new File(logDirPath);
                                if (!logDir.exists()) {
                                    logDir.mkdirs();
                                }

                                File logFile = new File(logDirPath, "vip_model_trigger_log.txt");
                                try (FileWriter fw = new FileWriter(logFile, true)) { // true 表示追加写入
                                    fw.write(sb.toString());
                                    fw.flush();
                                    Log.d("webviewAd", "📝 成功记录模型触发参数及堆栈，保存至: " + logFile.getAbsolutePath());
                                } catch (Exception e) {
                                    Log.e("webviewAd", "文件写入失败: " + e.getMessage());
                                }

                                // 3. 截断原方法执行，强行篡改结果
                                Log.d("webviewAd", "🎯 拦截模型执行，强制返回 0.95f");
                                param.setResult(0.95f);
                            }
                        }
                );
            } catch (Exception e) {
                Log.e("webviewAd", "Hook 预测模型失败: " + e.getMessage());
            }
        }
        else {
            try {

                XposedHelpers.findAndHookMethod(helper.VIP_PREDICT_REQUEST_CLASS_NAME, classLoader, "onResponse", "retrofit2.Call", "retrofit2.Response", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                        Object call = param.args[0];
                        Object response = param.args[1];

                        if (call == null || response == null) return;

                        try {
                            Object request = XposedHelpers.callMethod(call, "request");
                            Object httpUrl = XposedHelpers.callMethod(request, "url");
                            String urlString = httpUrl.toString();

                            // 1. 锁定我们要拦截的三个“广告/活动”物料接口
                            boolean isAdTarget = urlString.contains("/pgc/activity/deliver/material/receive") ||
                                    urlString.contains("/x/vip/gsm/activity/preload") ||
                                    urlString.contains("/pgc/season/player/ogv/cards");

                            if (isAdTarget) {
                                Log.d(TAG, "🔥 逮到目标接口，准备拦截篡改: " + urlString);

                                Object bodyObj = XposedHelpers.callMethod(response, "body");

                                // 2. 确认返回体是 B站的成功包装类
                                if (bodyObj != null && bodyObj.getClass().getName().endsWith("BiliApiResponse$Success")) {

                                    // 3. 暴力反射，将包装类里面的泛型数据（比如 f285876a 或者 a 字段）全部置为 null
                                    Field[] fields = bodyObj.getClass().getDeclaredFields();
                                    for (Field field : fields) {
                                        field.setAccessible(true);
                                        // 因为泛型擦除后类型是 Object，所以可以直接赋 null
                                        field.set(bodyObj, null);
                                    }

                                    Log.d(TAG, "✅ 拦截成功！已将该接口返回的真实数据强制置空。");
                                }
                            }

                        } catch (Exception e) {
                            Log.e(TAG, "拦截篡改发生异常: " + e.getMessage());
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "response hook found exception: " + e.getMessage());

            }
        }
    }
}