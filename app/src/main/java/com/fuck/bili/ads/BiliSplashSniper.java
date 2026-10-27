package com.fuck.bili.ads;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.Log;
import android.view.View;
import android.view.Window;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.fuck.bili.ads.ui.FbaPrefs;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** 只拦截两个开屏接口；展示层跳过继续作为缓存广告兜底。 */
public final class BiliSplashSniper {
    private static final String TAG = "BiliSplashSniper";
    private static final boolean DEBUG_INTERVAL = false;
    private static final int DEBUG_SHOW_SECONDS = 1;
    private static final int DEBUG_PULL_SECONDS = 5;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicLong LIST_BLOCKED = new AtomicLong();
    private static final AtomicLong SHOW_BLOCKED = new AtomicLong();
    private static final AtomicLong BRAND_HIDDEN = new AtomicLong();
    private static final AtomicLong BRAND_SEEN = new AtomicLong();

    public static void hook(ClassLoader classLoader, DexKitHelper helper) {
        hookEarly(classLoader);
        hookDisplayFallback(classLoader, helper);
    }

    /** 不依赖 DexKit 的部分：请求拦截、品牌开屏隐藏、启动页配色。 */
    public static void hookEarly(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        installRequestBlock(classLoader);
        if (DEBUG_INTERVAL) installDebugIntervals(classLoader);
        installBrandSplashBlock(classLoader);
        installSplashThemeHook(classLoader);
    }

    /** 依赖 DexKit 解析结果的兜底：存量缓存广告的跳过。 */
    public static void hookDisplayFallback(ClassLoader classLoader, DexKitHelper helper) {
        installDisplayFallback(classLoader, helper);
    }

    /** 在 OkHttp 调用链继续执行前返回成功的空响应，不进入后续网络链。 */
    private static void installRequestBlock(ClassLoader loader) {
        try {
            final Class<?> requestClass = XposedHelpers.findClass("okhttp3.Request", loader);
            final Class<?> responseClass = XposedHelpers.findClass("okhttp3.Response", loader);
            final Class<?> builderClass = XposedHelpers.findClass("okhttp3.Response$Builder", loader);
            final Class<?> mediaClass = XposedHelpers.findClass("okhttp3.MediaType", loader);
            final Class<?> bodyClass = XposedHelpers.findClass("okhttp3.ResponseBody", loader);
            final Class<?> protocolClass = XposedHelpers.findClass("okhttp3.Protocol", loader);
            final Method parseMedia = mediaClass.getMethod("parse", String.class);
            final Method createBody = bodyClass.getMethod("create", mediaClass, String.class);
            final Object jsonMedia = parseMedia.invoke(null, "application/json; charset=utf-8");
            final Object protocol = protocolClass.getField("HTTP_1_1").get(null);
            // 预检构建响应所需的 API，安装失败时不要误报已拦截。
            builderClass.getConstructor();
            builderClass.getMethod("request", requestClass);
            builderClass.getMethod("protocol", protocolClass);
            builderClass.getMethod("code", int.class);
            builderClass.getMethod("message", String.class);
            builderClass.getMethod("body", bodyClass);
            builderClass.getMethod("build");
            final Class<?> clientBuilder = XposedHelpers.findClass("okhttp3.OkHttpClient$Builder", loader);
            // 从公开 addInterceptor API 获取接口类型，不依赖 Interceptor/Chain 的实际类名。
            Method addInterceptor = null;
            for (Method method : clientBuilder.getMethods()) {
                if ("addInterceptor".equals(method.getName()) && method.getParameterTypes().length == 1
                        && method.getParameterTypes()[0].isInterface()) {
                    addInterceptor = method;
                    break;
                }
            }
            if (addInterceptor == null) throw new NoSuchMethodException("Builder.addInterceptor");
            final Class<?> interceptorType = addInterceptor.getParameterTypes()[0];
            Method intercept = null;
            for (Method method : interceptorType.getMethods()) {
                if (method.getReturnType() == responseClass && method.getParameterTypes().length == 1) {
                    intercept = method;
                    break;
                }
            }
            if (intercept == null) throw new NoSuchMethodException("Interceptor method returning Response");
            final Method interceptMethod = intercept;
            final Class<?> chainType = interceptMethod.getParameterTypes()[0];
            final Method getRequest = findMethod(chainType, requestClass);
            final Method proceed = findMethod(chainType, responseClass, requestClass);
            final Method interceptors = clientBuilder.getMethod("interceptors");
            final Object blocker = java.lang.reflect.Proxy.newProxyInstance(
                    interceptorType.getClassLoader(), new Class<?>[]{interceptorType},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            if ("equals".equals(method.getName())) return args != null && args.length == 1 && proxy == args[0];
                            if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                            if ("toString".equals(method.getName())) return "BiliSplashRequestBlocker";
                        }
                        if (!method.equals(interceptMethod)) {
                            throw new UnsupportedOperationException(method.toString());
                        }
                        Object chain = args[0];
                        Object request = invoke(getRequest, chain);
                        String path;
                        boolean target;
                        try {
                            Object url = XposedHelpers.callMethod(request, "url");
                            String host = (String) XposedHelpers.callMethod(url, "host");
                            path = (String) XposedHelpers.callMethod(url, "encodedPath");
                            String requestMethod = (String) XposedHelpers.callMethod(request, "method");
                            target = isTarget(host, path, requestMethod);
                        } catch (Throwable error) {
                            Log.d(TAG, "[SplashBlock] request inspection failed: " + error);
                            return invoke(proceed, chain, request);
                        }
                        // 放行必须位于检查 try/catch 外，网络异常不能被误认为检查失败而重试。
                        if (!target) return invoke(proceed, chain, request);
                        try {
                            String json = emptyResponse(path);
                            Object body = createBody.invoke(null, jsonMedia, json);
                            Object builder = builderClass.getConstructor().newInstance();
                            XposedHelpers.callMethod(builder, "request", request);
                            XposedHelpers.callMethod(builder, "protocol", protocol);
                            XposedHelpers.callMethod(builder, "code", 200);
                            XposedHelpers.callMethod(builder, "message", "OK");
                            XposedHelpers.callMethod(builder, "header", "Content-Type", "application/json; charset=utf-8");
                            XposedHelpers.callMethod(builder, "header", "X-BiliSplashSniper", "blocked");
                            XposedHelpers.callMethod(builder, "body", body);
                            Object response = XposedHelpers.callMethod(builder, "build");
                            long count = (path.endsWith("/list") ? LIST_BLOCKED : SHOW_BLOCKED).incrementAndGet();
                            Log.d(TAG, "[SplashBlock] BLOCKED " + path + ", count=" + count
                                    + ", HTTP=200, emptyResponse=true, networkSkipped=true");
                            return response;
                        } catch (Throwable error) {
                            // 构建失败不让目标广告请求继续联网；以正常的 IOException 交给宿主错误处理。
                            Log.d(TAG, "[SplashBlock] BLOCKED_WITH_ERROR " + path
                                    + ", networkSkipped=true, error=" + error);
                            throw new java.io.IOException("Splash request blocked", error);
                        }
                    });
            XposedHelpers.findAndHookMethod(clientBuilder, "build", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        @SuppressWarnings("unchecked")
                        java.util.List<Object> list = (java.util.List<Object>) interceptors.invoke(param.thisObject);
                        for (Object item : list) if (item == blocker) return;
                        list.add(0, blocker);
                        if (DEBUG_INTERVAL) Log.d(TAG, "[SplashClient] request interceptor attached");
                    } catch (Throwable error) {
                        Log.d(TAG, "[SplashClient] FAILED to attach interceptor: " + error);
                    }
                }
            });
            Log.d(TAG, "[SplashBlockInstall] installed via OkHttpClient.Builder.build"
                    + ", interface=" + interceptorType.getName() + ", chainInterface=" + chainType.getName()
                    + ", targets=/x/v2/splash/list,/x/v2/splash/show, debugInterval=" + DEBUG_INTERVAL);
        } catch (Throwable error) {
            Log.d(TAG, "[SplashBlockInstall] FAILED: " + Log.getStackTraceString(error));
        }
    }

    /** 通过签名取得 Chain API，接口成员改名仍可识别。 */
    private static Method findMethod(Class<?> owner, Class<?> result, Class<?>... parameters)
            throws NoSuchMethodException {
        for (Method method : owner.getMethods()) {
            if (method.getReturnType() == result
                    && java.util.Arrays.equals(method.getParameterTypes(), parameters)) return method;
        }
        throw new NoSuchMethodException(owner.getName() + " -> " + result.getName());
    }

    private static Object invoke(Method method, Object target, Object... args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
    }

    private static boolean isTarget(String host, String path, String method) {
        return "app.bilibili.com".equalsIgnoreCase(host) && "GET".equalsIgnoreCase(method)
                && ("/x/v2/splash/list".equals(path) || "/x/v2/splash/show".equals(path));
    }

    private static String emptyResponse(String path) {
        if ("/x/v2/splash/show".equals(path)) {
            return "{\"code\":0,\"message\":\"OK\",\"ttl\":1,\"data\":{\"show\":[],\"splash_request_id\":\"\"}}";
        }
        int show = DEBUG_INTERVAL ? DEBUG_SHOW_SECONDS : 900;
        int pull = DEBUG_INTERVAL ? DEBUG_PULL_SECONDS : 900;
        return "{\"code\":0,\"message\":\"OK\",\"ttl\":1,\"data\":{"
                + "\"max_time\":0,\"min_interval\":" + show + ",\"pull_interval\":" + pull
                + ",\"disperse_interval\":0,\"list\":[],\"show\":[],\"keep_ids\":[],\"splash_request_id\":\"\"}}";
    }

    /** 仅 Debug 缩短间隔；不持久改写旧缓存文件，也不创建广告或强制展示。 */
    private static void installDebugIntervals(ClassLoader loader) {
        hookInterval(loader, "getIntervalForShow", DEBUG_SHOW_SECONDS);
        hookInterval(loader, "getIntervalForUpdate", DEBUG_PULL_SECONDS);
    }

    private static void hookInterval(ClassLoader loader, String method, int seconds) {
        try {
            XposedHelpers.findAndHookMethod("tv.danmaku.bili.splash.ad.model.SplashListResponse",
                    loader, method, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable()) return;
                            Object original = param.getResult();
                            param.setResult(seconds);
                            Log.d(TAG, "[SplashInterval] DEBUG " + method + ": " + original
                                    + " -> " + seconds + " seconds");
                        }
                    });
            Log.d(TAG, "[SplashIntervalInstall] DEBUG " + method + "=" + seconds + " seconds");
        } catch (Throwable error) {
            Log.d(TAG, "[SplashIntervalInstall] FAILED " + method + ": " + error);
        }
    }

    private static void installDisplayFallback(ClassLoader loader, DexKitHelper helper) {
        try {
            XposedHelpers.findAndHookMethod(helper.BASE_SPLASH_CLASS_NAME, loader,
                    helper.SPLASH_READY_METHOD_NAME, boolean.class, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            Object splash = param.thisObject;
                            Log.d(TAG, "[SplashFallback] ready=" + param.args[0]
                                    + ", class=" + splash.getClass().getName()
                                    + ", listBlocked=" + LIST_BLOCKED.get()
                                    + ", showBlocked=" + SHOW_BLOCKED.get());
                            try {
                                XposedHelpers.callMethod(splash, helper.SPLASH_SKIP_METHOD_NAME);
                                Log.d(TAG, "[SplashSkip] success: " + helper.SPLASH_SKIP_METHOD_NAME);
                            } catch (Throwable first) {
                                try {
                                    XposedHelpers.callMethod(splash, "Nf", null, null, false);
                                    Log.d(TAG, "[SplashSkip] success: Nf fallback");
                                } catch (Throwable second) {
                                    Log.d(TAG, "[SplashSkip] FAILED: " + first + "; fallback=" + second);
                                }
                            }
                        }
                    });
            Log.d(TAG, "[SplashFallbackInstall] installed: " + helper.BASE_SPLASH_CLASS_NAME
                    + "." + helper.SPLASH_READY_METHOD_NAME);
        } catch (Throwable error) {
            Log.d(TAG, "[SplashFallbackInstall] FAILED: " + error);
        }
    }

    /**
     * 品牌开屏（22娘/33娘立绘）来自本地 {@code BrandSplashStorage}，不走
     * {@code /x/v2/splash/*}，所以请求拦截对它无效。这里在 Fragment 视图创建/恢复时
     * 把它的视图设为 GONE：宿主的计时、回调、跳转全部照常，只是不画出来。
     */
    private static void installBrandSplashBlock(ClassLoader loader) {
        try {
            final Class<?> fragment = XposedHelpers.findClass("androidx.fragment.app.Fragment", loader);
            final XC_MethodHook hide = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object self = param.thisObject;
                        if (self == null) {
                            return;
                        }
                        String name = self.getClass().getName();
                        String lower = name.toLowerCase(java.util.Locale.US);
                        boolean brand = lower.contains("brandsplash");
                        if (!brand && lower.contains("splash")) {
                            long seen = BRAND_SEEN.incrementAndGet();
                            if (seen <= 20) {
                                Log.d(TAG, "[SplashSeen] fragment " + name);
                            }
                        }
                        if (!brand) {
                            return;
                        }
                        Object view = XposedHelpers.callMethod(self, "getView");
                        if (!(view instanceof View)) {
                            return;
                        }
                        ((View) view).setVisibility(View.GONE);
                        long count = BRAND_HIDDEN.incrementAndGet();
                        if (count <= 5) {
                            Log.d(TAG, "[BrandSplash] hidden " + name + ", count=" + count);
                        }
                    } catch (Throwable ignore) {
                        // 不打断宿主
                    }
                }
            };
            int hooked = 0;
            for (String method : new String[]{"performViewCreated", "performResume", "onViewCreated"}) {
                hooked += XposedBridge.hookAllMethods(fragment, method, hide).size();
            }
            Log.d(TAG, "[BrandSplashBlock] installed hooks=" + hooked);
            installSplashActivityProbe(loader);
        } catch (Throwable error) {
            Log.d(TAG, "[BrandSplashBlock] FAILED: " + error);
        }
    }

    /** 只记日志：下次品牌开屏出现时，日志会告诉我们它到底是哪个类。 */
    private static void installSplashActivityProbe(ClassLoader loader) {
        try {
            final Class<?> activity = XposedHelpers.findClass("android.app.Activity", loader);
            XposedBridge.hookAllMethods(activity, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object self = param.thisObject;
                        if (self == null) {
                            return;
                        }
                        String name = self.getClass().getName();
                        if (!name.toLowerCase(java.util.Locale.US).contains("splash")) {
                            return;
                        }
                        long seen = BRAND_SEEN.incrementAndGet();
                        if (seen <= 20) {
                            Log.d(TAG, "[SplashSeen] activity " + name);
                        }
                    } catch (Throwable ignore) {
                        // 不打断宿主
                    }
                }
            });
        } catch (Throwable error) {
            Log.d(TAG, "[SplashProbe] FAILED: " + error);
        }
    }

    /**
     * 启动页跟主题走。宿主的 splash 主题把底色写在 windowBackground 的 layer-list 里
     * （纯色层 + 居中的 logo 位图），这里只替换纯色层，logo 的位置和尺寸保持原样。
     */
    private static void installSplashThemeHook(ClassLoader loader) {
        try {
            final Class<?> activity = XposedHelpers.findClass("android.app.Activity", loader);
            XposedBridge.hookAllMethods(activity, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object self = param.thisObject;
                        if (!(self instanceof Activity)) {
                            return;
                        }
                        String name = self.getClass().getName();
                        if (!name.endsWith("SplashShellActivity") && !name.endsWith("MainActivityV2")) {
                            return;
                        }
                        applySplashTheme((Activity) self);
                    } catch (Throwable ignore) {
                        // 不打断宿主
                    }
                }
            });
            Log.d(TAG, "[SplashTheme] hook installed");
        } catch (Throwable error) {
            Log.d(TAG, "[SplashTheme] FAILED: " + error);
        }
    }

    private static void applySplashTheme(Activity activity) {
        boolean night = FbaPrefs.splashNight();
        int color = night ? 0xFF17181A : 0xFFFFFFFF;
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }
        Drawable themed = null;
        Drawable background = window.getDecorView().getBackground();
        if (background instanceof LayerDrawable) {
            try {
                Drawable.ConstantState state = background.getConstantState();
                Drawable copy = state == null ? null : state.newDrawable(activity.getResources()).mutate();
                if (copy instanceof LayerDrawable) {
                    LayerDrawable layers = (LayerDrawable) copy;
                    boolean tinted = false;
                    for (int i = 0; i < layers.getNumberOfLayers(); i++) {
                        Drawable layer = layers.getDrawable(i);
                        if (layer instanceof ColorDrawable) {
                            ((ColorDrawable) layer).setColor(color);
                            tinted = true;
                        }
                    }
                    if (tinted) {
                        themed = layers;
                    }
                }
            } catch (Throwable ignore) {
                // 退回纯色背景
            }
        }
        if (themed == null) {
            themed = new ColorDrawable(color);
        }
        window.setBackgroundDrawable(themed);

        View decor = window.getDecorView();
        if (decor != null) {
            int flags = decor.getSystemUiVisibility();
            if (night) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }
        Log.d(TAG, "[SplashTheme] applied night=" + night + " pref=" + FbaPrefs.splashTheme()
                + " activity=" + activity.getClass().getSimpleName()
                + " layered=" + (themed instanceof LayerDrawable));
    }

    private BiliSplashSniper() { }}
