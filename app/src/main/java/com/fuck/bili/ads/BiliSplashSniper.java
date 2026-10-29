package com.fuck.bili.ads;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.Log;
import android.view.View;
import android.view.Window;

import com.fuck.bili.ads.core.Host;
import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;
import com.fuck.bili.ads.ui.FbaLog;
import com.fuck.bili.ads.ui.FbaPrefs;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** 只拦截两个开屏接口；展示层跳过继续作为缓存广告兜底。 */
public final class BiliSplashSniper {
    private static final String TAG = "BiliSplashSniper";
    private static final boolean DEBUG_INTERVAL = false;
    private static final int DEBUG_SHOW_SECONDS = 1;
    private static final int DEBUG_PULL_SECONDS = 5;
    /** 商业开屏、展示上报、品牌开屏三条链路；运营活动开屏不属于广告，保持原样。 */
    private static final String SPLASH_LIST_PATH = "/x/v2/splash/list";
    private static final String SPLASH_SHOW_PATH = "/x/v2/splash/show";
    private static final String SPLASH_BRAND_PATH = "/x/v2/splash/brand/list";
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
        installEarlySplashGate(classLoader);
        installHdSplashGate(classLoader);
        if (Host.isHd()) installHdDecisionGate(classLoader);
        if (DEBUG_INTERVAL) installDebugIntervals(classLoader);
        installBrandSplashBlock(classLoader);
        installSplashThemeHook(classLoader);
    }

    /**
     * HD attaches the splash fragment only when this factory returns non-null,
     * so this gate holds even if the page factory below is refactored away.
     */
    private static void installHdDecisionGate(ClassLoader loader) {
        try {
            Class<?> helper = Reflect.findClass("tv.danmaku.bili.ui.splash.MainSplashHelper", loader);
            Class<?> splash = Reflect.findClass("tv.danmaku.bili.ui.splash.ad.model.Splash", loader);
            Class<?> fragment = Reflect.findClass("androidx.fragment.app.Fragment", loader);
            Method target = null;
            for (Method method : helper.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (method.getReturnType() == fragment && parameters.length == 2
                        && parameters[0] == splash && parameters[1] == boolean.class) {
                    target = method;
                    break;
                }
            }
            if (target == null) {
                throw new NoSuchMethodException("MainSplashHelper splash fragment factory");
            }
            NativeHooks.hook("BiliSplashSniper.HdDecisionGate", target, chain -> {
                FbaLog.i("[SPLASH][HD] 开屏片段创建已拦截（决策层）");
                return null;
            });
            FbaLog.i("[SPLASH][HD] 决策层拦截已安装");
        } catch (Throwable error) {
            FbaLog.e("[SPLASH][HD] 决策层拦截安装失败", error);
        }
    }

    /**
     * Prevents cached commercial pages from being attached before the module is
     * injected. Returning null follows the host's existing no-ad exit branch.
     */
    private static void installEarlySplashGate(ClassLoader loader) {
        try {
            Class<?> manager = Reflect.findClass("tv.danmaku.bili.splash.ad.core.SplashManager", loader);
            Method factory = null;
            for (Method method : manager.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        && method.getParameterTypes().length == 1
                        && method.getParameterTypes()[0].getName().endsWith("SplashOrder")
                        && method.getReturnType().getName().endsWith("BaseSplash")) {
                    factory = method;
                    break;
                }
            }
            if (factory == null) {
                throw new NoSuchMethodException("SplashManager commercial page factory");
            }
            if (!isCommercialPageFactory(factory)) {
                throw new IllegalArgumentException("unexpected page factory signature");
            }
            NativeHooks.hook("BiliSplashSniper.CachedPageGate", factory, chain -> {
                Object order = chain.getArg(0);
                FbaLog.i("[SPLASH] 缓存开屏页创建已拦截，走宿主无广告退出流程");
                return null;
            });
            FbaLog.i("[SPLASH] 缓存广告页创建拦截已安装");
        } catch (Throwable error) {
            FbaLog.e("[SPLASH] 缓存广告页创建拦截安装失败", error);
        }
    }

    /** HD's cached page factory returns a Fragment; null follows its no-ad branch. */
    private static void installHdSplashGate(ClassLoader loader) {
        try {
            Class<?> factory = Reflect.findClass("tv.danmaku.bili.ui.splash.ad.page.x", loader);
            Method create = NativeHooks.method(factory, "a",
                    Reflect.findClass("tv.danmaku.bili.ui.splash.ad.model.Splash", loader));
            NativeHooks.hook("BiliSplashSniper.HdCachedPageGate", create, chain -> {
                FbaLog.i("[SPLASH][HD] 缓存开屏页创建已拦截，走宿主无广告退出流程");
                return null;
            });
            FbaLog.i("[SPLASH][HD] 缓存广告页创建拦截已安装");
        } catch (Throwable error) {
            FbaLog.e("[SPLASH][HD] 缓存广告页创建拦截安装失败", error);
        }
    }

    /** 依赖 DexKit 解析结果的兜底：存量缓存广告的跳过。 */
    public static void hookDisplayFallback(ClassLoader classLoader, DexKitHelper helper) {
        if (Host.isHd()) {
            installHdDisplayFallback(classLoader);
        } else {
            installDisplayFallback(classLoader, helper);
        }
    }

    /** 在 OkHttp 调用链继续执行前返回成功的空响应，不进入后续网络链。 */
    private static void installRequestBlock(ClassLoader loader) {
        try {
            final Class<?> requestClass = Reflect.findClass("okhttp3.Request", loader);
            final Class<?> responseClass = Reflect.findClass("okhttp3.Response", loader);
            final Class<?> builderClass = Reflect.findClass("okhttp3.Response$Builder", loader);
            final Class<?> mediaClass = Reflect.findClass("okhttp3.MediaType", loader);
            final Class<?> bodyClass = Reflect.findClass("okhttp3.ResponseBody", loader);
            final Class<?> protocolClass = Reflect.findClass("okhttp3.Protocol", loader);
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
            final Class<?> clientBuilder = Reflect.findClass("okhttp3.OkHttpClient$Builder", loader);
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
                            Object url = Reflect.callMethod(request, "url");
                            String host = (String) Reflect.callMethod(url, "host");
                            path = (String) Reflect.callMethod(url, "encodedPath");
                            String requestMethod = (String) Reflect.callMethod(url, "method");
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
                            Reflect.callMethod(builder, "request", request);
                            Reflect.callMethod(builder, "protocol", protocol);
                            Reflect.callMethod(builder, "code", 200);
                            Reflect.callMethod(builder, "message", "OK");
                            Reflect.callMethod(builder, "header", "Content-Type", "application/json; charset=utf-8");
                            Reflect.callMethod(builder, "header", "X-BiliSplashSniper", "blocked");
                            Reflect.callMethod(builder, "body", body);
                            Object response = Reflect.callMethod(builder, "build");
                            long count = (path.endsWith("/list") ? LIST_BLOCKED : SHOW_BLOCKED).incrementAndGet();
                            FbaLog.i("[SPLASH] 拦截开屏请求 " + path);
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
            Method build = NativeHooks.method(clientBuilder, "build");
            NativeHooks.hook("BiliSplashSniper.OkHttpClientBuild", build, chain -> {
                try {
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> list = (java.util.List<Object>) interceptors.invoke(chain.getThisObject());
                    for (Object item : list) if (item == blocker) return chain.proceed();
                    list.add(0, blocker);
                    if (DEBUG_INTERVAL) Log.d(TAG, "[SplashClient] request interceptor attached");
                } catch (Throwable error) {
                    Log.d(TAG, "[SplashClient] FAILED to attach interceptor: " + error);
                }
                return chain.proceed();
            });
            FbaLog.i("[SPLASH] 请求拦截已安装: 商业/上报/品牌 三条开屏链路");
            Log.d(TAG, "[SplashBlockInstall] installed via OkHttpClient.Builder.build"
                    + ", interface=" + interceptorType.getName() + ", chainInterface=" + chainType.getName()
                    + ", targets=" + SPLASH_LIST_PATH + "," + SPLASH_SHOW_PATH + ","
                            + SPLASH_BRAND_PATH + ", debugInterval=" + DEBUG_INTERVAL);
        } catch (Throwable error) {
            FbaLog.e("[SPLASH] 请求拦截安装失败", error);
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

    static boolean isTarget(String host, String path, String method) {
        return "app.bilibili.com".equalsIgnoreCase(host) && "GET".equalsIgnoreCase(method)
                && (SPLASH_LIST_PATH.equals(path) || SPLASH_SHOW_PATH.equals(path)
                || SPLASH_BRAND_PATH.equals(path));
    }

    private static String emptyResponse(String path) {
        if (SPLASH_SHOW_PATH.equals(path)) {
            return "{\"code\":0,\"message\":\"OK\",\"ttl\":1,\"data\":{\"show\":[],\"splash_request_id\":\"\"}}";
        }
        if (SPLASH_BRAND_PATH.equals(path)) {
            // 品牌开屏（商业品牌图，本地缓存）：回空列表，宿主视为“无素材”。
            return "{\"code\":0,\"message\":\"OK\",\"ttl\":1,\"data\":{"
                    + "\"list\":[],\"collection_list\":[],\"pull_interval\":900}}";
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
            Class<?> type = Reflect.findClass("tv.danmaku.bili.splash.ad.model.SplashListResponse", loader);
            Method target = NativeHooks.method(type, method);
            NativeHooks.hook("BiliSplashSniper." + method, target, chain -> {
                Object result = chain.proceed();
                Log.d(TAG, "[SplashInterval] DEBUG " + method + ": " + result
                        + " -> " + seconds + " seconds");
                return seconds;
            });
            Log.d(TAG, "[SplashIntervalInstall] DEBUG " + method + "=" + seconds + " seconds");
        } catch (Throwable error) {
            Log.d(TAG, "[SplashIntervalInstall] FAILED " + method + ": " + error);
        }
    }

    private static void installDisplayFallback(ClassLoader loader, DexKitHelper helper) {
        try {
            Class<?> type = Reflect.findClass(helper.BASE_SPLASH_CLASS_NAME, loader);
            Method target = NativeHooks.method(type, helper.SPLASH_READY_METHOD_NAME, boolean.class);
            NativeHooks.hook("BiliSplashSniper.onSplashReady", target, chain -> {
                Object splash = chain.getThisObject();
                FbaLog.i("[SPLASH] 缓存开屏进入展示层，准备跳过");
                Object result = null;
                Log.d(TAG, "[SplashFallback] ready=" + chain.getArg(0)
                        + ", class=" + splash.getClass().getName()
                        + ", listBlocked=" + LIST_BLOCKED.get()
                        + ", showBlocked=" + SHOW_BLOCKED.get());
                try {
                    callSkipMethod(splash, helper.SPLASH_SKIP_METHOD_NAME);
                    FbaLog.i("[SPLASH] 已调用宿主跳过: " + helper.SPLASH_SKIP_METHOD_NAME);
                    Log.d(TAG, "[SplashSkip] success: " + helper.SPLASH_SKIP_METHOD_NAME);
                } catch (Throwable first) {
                    try {
                        Reflect.callMethod(splash, "Nf", null, null, false);
                        Log.d(TAG, "[SplashSkip] success: Nf fallback");
                    } catch (Throwable second) {
                        Log.d(TAG, "[SplashSkip] FAILED: " + first + "; fallback=" + second);
                    }
                }
                return result;
            });
            FbaLog.i("[SPLASH] 缓存展示兜底已安装");
            Log.d(TAG, "[SplashFallbackInstall] installed: " + helper.BASE_SPLASH_CLASS_NAME
                    + "." + helper.SPLASH_READY_METHOD_NAME);
        } catch (Throwable error) {
            FbaLog.e("[SPLASH] 缓存展示兜底安装失败: " + error, null);
            Log.d(TAG, "[SplashFallbackInstall] FAILED: " + error);
        }
    }

    static boolean isCommercialPageFactory(Method method) {
        return method != null
                && java.lang.reflect.Modifier.isStatic(method.getModifiers())
                && method.getParameterTypes().length == 1
                && method.getParameterTypes()[0].getName().endsWith("SplashOrder")
                && method.getReturnType().getName().endsWith("BaseSplash");
    }

    /** Invokes the host's no-argument skip method, or closes via its activity. */
    private static void callSkipMethod(Object splash, String preferred) throws Throwable {
        for (Class<?> type = splash.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getParameterCount() == 0 && method.getReturnType() == void.class
                        && method.getName().equals(preferred)) {
                    method.setAccessible(true);
                    method.invoke(splash);
                    return;
                }
            }
        }
        Object activity = Reflect.tryCallMethod(splash, "getActivity");
        if (activity != null) {
            Reflect.callMethod(activity, "finish");
            FbaLog.i("[SPLASH] 无参跳过方法不可用，已关闭开屏 Activity");
            return;
        }
        throw new NoSuchMethodException("zero-argument splash skip: " + preferred);
    }

    /** HD skips before its business splash view has completed rendering. */
    private static void installHdDisplayFallback(ClassLoader loader) {
        try {
            Class<?> type = Reflect.findClass("tv.danmaku.bili.ui.splash.ad.page.BaseSplash", loader);
            Method ready = NativeHooks.method(type, "ap");
            NativeHooks.hook("BiliSplashSniper.HdSplashReady", ready, chain -> {
                Object splash = chain.getThisObject();
                FbaLog.i("[SPLASH][HD] 开屏展示兜底触发");
                Reflect.callMethod(splash, "wo", false);
                return null;
            });
            FbaLog.i("[SPLASH][HD] 展示兜底已安装");
        } catch (Throwable error) {
            FbaLog.e("[SPLASH][HD] 展示兜底安装失败", error);
        }
    }

    /**
     * 品牌开屏（22娘/33娘立绘）来自本地 {@code BrandSplashStorage}，不走
     * {@code /x/v2/splash/*}，所以请求拦截对它无效。这里在 Fragment 视图创建/恢复时
     * 把它的视图设为 GONE：宿主的计时、回调、跳转全部照常，只是不画出来。
     */
    private static void installBrandSplashBlock(ClassLoader loader) {
        try {
            final Class<?> fragment = Reflect.findClass("androidx.fragment.app.Fragment", loader);
            int hooked = 0;
            for (String name : new String[]{"performViewCreated", "performResume", "onViewCreated"}) {
                hooked += NativeHooks.hookAllMethods("BiliSplashSniper.BrandSplash." + name,
                        fragment, name, chain -> {
                            Object result = chain.proceed();
                            try {
                                Object self = chain.getThisObject();
                                if (self == null) {
                                    return result;
                                }
                                String className = self.getClass().getName();
                                String lower = className.toLowerCase(java.util.Locale.US);
                                boolean brand = lower.contains("brandsplash");
                                if (!brand && lower.contains("splash")) {
                                    long seen = BRAND_SEEN.incrementAndGet();
                                    if (seen <= 20) {
                                        FbaLog.i("[SPLASH] 页面 Fragment: " + className);
                                        Log.d(TAG, "[SplashSeen] fragment " + className);
                                    }
                                }
                                if (!brand) {
                                    return result;
                                }
                                // 默认不跳过：不少用户就是用品牌开屏来看个性化动画的。
                                if (!FbaPrefs.brandSplashBlock()) {
                                    return result;
                                }
                                Object view = Reflect.tryCallMethod(self, "getView");
                                if (!(view instanceof View)) {
                                    return result;
                                }
                                ((View) view).setVisibility(View.GONE);
                                long count = BRAND_HIDDEN.incrementAndGet();
                                if (count <= 5) {
                                    Log.d(TAG, "[BrandSplash] hidden " + className + ", count=" + count);
                                }
                            } catch (Throwable ignore) {
                                // 不打断宿主
                            }
                            return result;
                        });
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
            final Class<?> activity = Reflect.findClass("android.app.Activity", loader);
            NativeHooks.hookAllMethods("BiliSplashSniper.SplashActivityProbe", activity, "onCreate",
                    chain -> {
                        Object result = chain.proceed();
                        try {
                            Object self = chain.getThisObject();
                            if (self == null) {
                                return result;
                            }
                            String name = self.getClass().getName();
                            if (!name.toLowerCase(java.util.Locale.US).contains("splash")) {
                                return result;
                            }
                            long seen = BRAND_SEEN.incrementAndGet();
                            if (seen <= 20) {
                                FbaLog.i("[SPLASH] 页面 Activity: " + name);
                                Log.d(TAG, "[SplashSeen] activity " + name);
                            }
                        } catch (Throwable ignore) {
                            // 不打断宿主
                        }
                        return result;
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
            final Class<?> activity = Reflect.findClass("android.app.Activity", loader);
            NativeHooks.hookAllMethods("BiliSplashSniper.SplashTheme", activity, "onCreate", chain -> {
                Object result = chain.proceed();
                try {
                    Object self = chain.getThisObject();
                    if (!(self instanceof Activity)) {
                        return result;
                    }
                    String name = self.getClass().getName();
                    if (!name.endsWith("SplashShellActivity") && !name.endsWith("MainActivityV2")) {
                        return result;
                    }
                    applySplashTheme((Activity) self);
                } catch (Throwable ignore) {
                    // 不打断宿主
                }
                return result;
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

    private BiliSplashSniper() { }
}
