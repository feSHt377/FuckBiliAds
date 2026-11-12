package com.fuck.bili.ads;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.fuck.bili.ads.core.Host;
import com.fuck.bili.ads.core.HookRegistry;
import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;
import com.fuck.bili.ads.settings.FollowingTopLeftButton;
import com.fuck.bili.ads.story.StoryAdSniper;
import com.fuck.bili.ads.ui.FbaLog;
import com.fuck.bili.ads.ui.FbaPrefs;
import com.fuck.bili.ads.ui.FbaRuntime;
import com.fuck.bili.ads.ui.MineServiceEntry;
import com.fuck.bili.ads.ui.StoryShareMark;
import com.fuck.bili.ads.ui.UpdateChecker;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/** Native libxposed API 102 entry point. */
public class MainHook extends XposedModule {

    private static final String PACKAGE_NAME = "tv.danmaku.bili";
    private static final String PACKAGE_NAME_ALIAS = "tv.danmaku.bilj";
    private static final String INTERNATIONAL_PACKAGE_NAME = "com.bilibili.app.in";
    private static final String HD_PACKAGE_NAME = "tv.danmaku.bilibilihd";

    public static boolean isInternational = false;

    /** One installation per process; repeated callbacks would stack hooks. */
    private static final java.util.concurrent.atomic.AtomicBoolean LOADED =
            new java.util.concurrent.atomic.AtomicBoolean();

    public MainHook() {
        super();
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (param == null) {
            return;
        }
        final String packageName = param.getPackageName();
        if (!PACKAGE_NAME.equals(packageName)
                && !INTERNATIONAL_PACKAGE_NAME.equals(packageName)
                && !PACKAGE_NAME_ALIAS.equals(packageName)
                && !HD_PACKAGE_NAME.equals(packageName)) {
            return;
        }
        // LSPosed 在同一进程里可能多次回调 onPackageLoaded（实测 HD + LSPosed 2.2.1
        // 会在几百毫秒内连调 8 次）。没有这道闸门时 Hook 会一层层叠加，
        // 启动阶段直接被拖死，表现为「卡在启动页」。
        if (!LOADED.compareAndSet(false, true)) {
            FbaLog.d("[SKIP] duplicate onPackageLoaded: " + packageName);
            return;
        }
        if (INTERNATIONAL_PACKAGE_NAME.equals(packageName)) {
            isInternational = true;
        }
        final boolean hd = Host.isHdPackage(packageName);
        Host.setPackageName(packageName);

        NativeHooks.init(this);
        FbaLog.init(Host.currentApplication());
        FbaPrefs.init(Host.currentApplication());

        final ApplicationInfo appInfo = param.getApplicationInfo();
        final ClassLoader classLoader = resolveClassLoader(param, packageName);
        if (classLoader == null) {
            return;
        }
        final String processName = Host.processName();
        final boolean mainProcess = processName.isEmpty() || processName.equals(packageName);

        FbaLog.i("module load start: pkg=" + packageName
                + " process=" + processName + " main=" + mainProcess
                + " api=" + getApiVersion()
                + " framework=" + getFrameworkName() + " " + getFrameworkVersion());

        if (!mainProcess) {
            // 只有主进程有界面：其它进程装 hook 没有收益，却要多扫一遍 268 MB dex。
            FbaLog.i("[SKIP] non-main process, no hooks installed: " + processName);
            return;
        }

        try {
            System.loadLibrary("dexkit");
        } catch (Throwable t) {
            FbaLog.e("加载 DexKit 动态库失败", t);
        }

        // ---- 不依赖 DexKit 的 hook：立刻装，启动页/弹幕/小视频等功能不等扫描 ----
        // 开屏请求拦截必须尽早装（OkHttp 客户端可能早于设置读取就建好），
        // 开关在拦截器里运行时判断；播放页 / 关注页的 Hook 可以等 Application 就绪后再装。
        install("开屏拦截", () -> BiliSplashSniper.hookEarly(classLoader));
        FbaRuntime.whenReady(() -> {
            if (FbaPrefs.blockPlay()) {
                if (hd) {
                    install("HD播放页广告", () -> HdAdSniper.hook(classLoader, appInfo));
                } else {
                    install("播放页广告", () -> BiliAdSniper.hook(classLoader));
                }
            } else {
                FbaLog.i("[SKIP] 播放页广告拦截已在设置中关闭");
            }
            if (FbaPrefs.blockFollow()) {
                install("关注页广告", () -> FollowingListAdSniper.hook(classLoader));
                install("关注页设置", () -> FollowingTopLeftButton.hook(classLoader));
            } else {
                FbaLog.i("[SKIP] 关注页广告拦截已在设置中关闭");
            }
        });
        install("底部导航", () -> BiliBottomNavSniper.hook(classLoader));
        install("抓包", () -> PacketSniffer.hook(classLoader));
        if (!hd) {
            install("小视频", () -> StoryAdSniper.hook(classLoader));
            install("分享标记", () -> StoryShareMark.hook(classLoader));
        } else {
            FbaLog.i("[HD] 无手机版竖屏 Story，跳过小视频与分享 Hook");
        }
        install("设置入口", () -> MineServiceEntry.hook(classLoader));
        install("更新检查", () -> UpdateChecker.startAutoCheck(processName, packageName));

        final boolean shouldRunDescCopyFix = !isOldHost(appInfo, packageName);
        final DexKitHelper helper = new DexKitHelper();
        final Runnable dependent = () -> installDependent(packageName, classLoader, helper,
                shouldRunDescCopyFix);

        if (helper.cacheValid(appInfo)) {
            long started = System.currentTimeMillis();
            try {
                helper.resolve(appInfo);
            } catch (Throwable t) {
                FbaLog.e("[HOOK FAIL] DexKit resolve", t);
            }
            FbaLog.i("[PERF] dexkit cache hit, load=" + (System.currentTimeMillis() - started) + "ms");
            dependent.run();
        } else {
            FbaLog.i("[PERF] dexkit cache miss, scanning in background (first launch after update)");
            helper.resolveAsync(appInfo, dependent);
        }
    }

    /** getDefaultClassLoader() is @RequiresApi(29); fall back on older hosts. */
    private static ClassLoader resolveClassLoader(
            XposedModuleInterface.PackageLoadedParam param, String packageName) {
        try {
            return param.getDefaultClassLoader();
        } catch (Throwable t) {
            FbaLog.w("getDefaultClassLoader unavailable: " + t);
            Object fallback = Reflect.tryCallMethod(param, "getClassLoader");
            if (fallback instanceof ClassLoader) {
                return (ClassLoader) fallback;
            }
            FbaLog.e("no usable host classloader for " + packageName, t);
            return null;
        }
    }

    /** 宿主版本是否低于 descCopyFix 的门槛（8.9.0）。 */
    private static boolean isOldHost(ApplicationInfo appInfo, String packageName) {
        try {
            Object activityThread = Reflect.callStaticMethod(
                    Reflect.findClass("android.app.ActivityThread", null),
                    "currentActivityThread");
            if (activityThread != null) {
                Context systemContext = (Context) Reflect.callMethod(activityThread, "getSystemContext");
                PackageInfo packageInfo = systemContext.getPackageManager()
                        .getPackageInfo(packageName, 0);
                long versionCode = packageInfo.versionCode;
                FbaLog.i("检测到宿主版本号: " + versionCode);
                return versionCode <= 8090300;
            }
        } catch (Throwable t) {
            FbaLog.e("获取宿主版本号失败，将默认执行 descCopyFix", t);
        }
        return false;
    }

    /** 依赖 DexKit 解析结果的 hook：缓存命中时立刻执行，否则等后台扫描完成。 */
    private static void installDependent(String packageName, ClassLoader classLoader,
                                         DexKitHelper helper, boolean shouldRunDescCopyFix) {
        install("基础 Hook", () -> executeOriginalHooks(packageName, classLoader, helper));
        if (FbaPrefs.blockFeed()) {
            install("广告卡片", () -> BiliCardAdSniper.hook(classLoader, helper));
        } else {
            FbaLog.i("[SKIP] 主页广告拦截已在设置中关闭");
        }
        if (FbaPrefs.blockSplash()) {
            install("开屏跳过", () -> BiliSplashSniper.hookDisplayFallback(classLoader, helper));
        }
        if (!Host.isHdPackage(packageName)) {
            install("视频提及", () -> BiliMentionedSniper.hook(classLoader, helper));
        }
        if (FbaPrefs.blockFeed()) {
            install("全局广告", () -> GlobalAdSniperEngine.hook(classLoader, helper));
        }
        if (!Host.isHdPackage(packageName)) {
            install("大会员预测", () -> VIPPredict.hook(classLoader, helper));
        }
        if (FbaPrefs.blockLive() && !Host.isHdPackage(packageName)) {
            install("直播间广告", () -> LiveRoomAdSniper.hook(classLoader, helper));
        }
        install("免流提示", () -> FreeDataToastSniper.hook(classLoader, helper));
        if (shouldRunDescCopyFix) {
            install("简介复制", () -> descCopyFix(classLoader, helper));
        } else {
            FbaLog.i("宿主版本号小于 8.9.0(8090300)，跳过 descCopyFix 逻辑。");
        }
        HookRegistry.dumpToLog();
        HookRegistry.dumpToFileWhenReady();
        FbaLog.i("[BOOT] hooks done: " + FbaRuntime.summary()
                + ", registered=" + HookRegistry.size());
        FbaRuntime.announce(2500L);
        FbaRuntime.whenReady(() -> UpdateRestart.check(Host.currentApplication()));
    }

    /**
     * Runs one feature's hook installer in isolation: one failure must not stop the rest.
     * A successful return does not imply the feature found its host classes, so features
     * report their own fine-grained status via {@link FbaRuntime#mark(String, boolean)}.
     */
    private static void install(String name, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            FbaLog.e("[HOOK FAIL] " + name, t);
            FbaRuntime.mark(name, false);
        }
    }

    private static void executeOriginalHooks(String packageName, ClassLoader classLoader,
                                             DexKitHelper helper) {
        if (!isInternational && !Host.isHdPackage(packageName)) {
            removeAdUnderPlayer(classLoader);
            try {
                BiliGsonLobotomy.hookPegasusAd(classLoader, helper.PEGASUS_MODEL_CLASS_NAME);
            } catch (Throwable t) {
                FbaLog.e("Pegasus ad hook failed", t);
            }
        }
        DanmuSenderLookup.hook(packageName, Host.processName(), classLoader, helper);
    }

    /**
     * 简介长按复制开关。
     *
     * <p>宿主的 ExpandableTextView 自己重写了 performLongClick() 并在其中复制文本；
     * 虚分派只会走到子类实现，因此 hook 基类 View#performLongClick 是拦不到的。
     * 这里直接挂该子类声明的两个重载：关闭开关时消费事件且不复制，开启时交给宿主。
     */
    private static void descCopyFix(ClassLoader classLoader, DexKitHelper helper) {
        try {
            java.util.List<Class<?>> targets = new java.util.ArrayList<>();
            Class<?> known = Reflect.findClassIfExists(
                    "tv.danmaku.bili.videopage.common.widget.view.ExpandableTextView", classLoader);
            if (known != null) {
                targets.add(known);
            }
            Class<?> base = Reflect.findClassIfExists(helper.DESCRIPTION_TEXTVIEW_CLASS_NAME, classLoader);
            if (base != null && !targets.contains(base)) {
                targets.add(base);
            }
            int hooked = 0;
            for (Class<?> type : targets) {
            for (Method method : type.getDeclaredMethods()) {
                if (!"performLongClick".equals(method.getName())) {
                    continue;
                }
                NativeHooks.hook("MainHook.DescriptionLongClick", method, chain -> {
                    if (FbaPrefs.descCopy()) {
                        return chain.proceed();
                    }
                    FbaLog.i("description long-press blocked (switch off)");
                    return Boolean.TRUE;
                });
                hooked++;
            }
            }
            FbaLog.i("description long-click hooks=" + hooked + " targets=" + targets.size());

            // 宿主的长按复制由简介服务直接写剪贴板，不经过任何 performLongClick；
            // 这里按调用方拦截：开关关闭时丢弃来自简介服务的剪贴板写入。
            Method setClip = NativeHooks.method(android.content.ClipboardManager.class,
                    "setPrimaryClip", android.content.ClipData.class);
            NativeHooks.hook("MainHook.DescriptionClipboardGuard", setClip, chain -> {
                if (!FbaPrefs.descCopy() && isDescriptionCopy()) {
                    FbaLog.i("description copy blocked (switch off)");
                    return null;
                }
                return chain.proceed();
            });

            // 宿主的「已复制…」提示由 BToastV3 发出；开关关闭时按文案拦截，
            // 不依赖复制是否走同一条路径，避免"提示复制成功"的误导。
            Class<?> bToast = Reflect.findClassIfExists("com.bilibili.droid.toast.BToastV3", classLoader);
            if (bToast != null) {
                try {
                    Method showText = NativeHooks.method(bToast, "show", Context.class, String.class,
                            int.class, int.class);
                    NativeHooks.hook("MainHook.DescriptionCopyToast", showText, chain -> {
                        Object text = chain.getArg(1);
                        if (!FbaPrefs.descCopy() && text != null && text.toString().contains("复制")) {
                            FbaLog.i("description copy toast suppressed");
                            return null;
                        }
                        return chain.proceed();
                    });
                } catch (Throwable ignored) {
                    // overload missing on this host
                }
            }
        } catch (Throwable t) {
            FbaLog.e("BiliHook -> descCopyFix failed", t);
        }
    }
    /** True when the clipboard write comes from the host description service. */
    private static boolean isDescriptionCopy() {
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            String owner = e.getClassName();
            if (owner.startsWith("com.bilibili.ship.theseus.ugc.intro")
                    || owner.toLowerCase(java.util.Locale.ROOT).contains("headline")) {
                return true;
            }
        }
        return false;
    }
    private static void removeAdUnderPlayer(ClassLoader classLoader) {
        try {
            Class<?> viewBindings = Reflect.findClass("androidx.viewbinding.ViewBindings", classLoader);
            Method findChild = NativeHooks.method(viewBindings, "findChildViewById",
                    View.class, int.class);
            NativeHooks.hook("MainHook.UnderPlayerContainer", findChild, chain -> {
                Object result = chain.proceed();
                int targetId = resolveUnderPlayerId((View) chain.getArg(0));
                if (targetId > 0 && (Integer) chain.getArg(1) == targetId) {
                    View targetView = (View) result;
                    if (targetView != null) {
                        ViewGroup.LayoutParams params = targetView.getLayoutParams();
                        if (params != null) {
                            params.width = 0;
                            params.height = 0;
                            targetView.setLayoutParams(params);
                        }
                        targetView.setVisibility(View.GONE);
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            FbaLog.e("error removing ad: " + t, t);
        }

        try {
            Class<?> panel = Reflect.findClass(
                    "com.bilibili.ad.adview.videodetail.pausedpage.AdPausedPagePanel", classLoader);
            Method create = NativeHooks.method(panel, "onCreateView", ViewGroup.class);
            NativeHooks.hook("MainHook.PausedPageAd", create, chain -> {
                // Return an empty panel, then ask the host to dismiss it right
                // away: the empty view alone still leaves the 3-second countdown
                // overlay running.
                Object self = chain.getThisObject();
                View empty = new View(((ViewGroup) chain.getArg(0)).getContext());
                FbaRuntime.onMain(() -> {
                    Object dismissed = Reflect.tryCallMethod(self, "requestDismiss", true);
                    FbaLog.i("paused page ad dismissed: " + dismissed);
                });
                return empty;
            });
        } catch (Throwable t) {
            FbaLog.e("error on paused page ad: " + t, t);
        }

        // 9.18.0 keeps the "3s 后可关闭" countdown outside the ad panel: the view
        // is produced by VDPausedPage#getCountDownView. Swap it for an empty view
        // so the countdown never appears.
        try {
            Class<?> vdPaused = Reflect.findClass(
                    "com.bilibili.ad.adview.videodetail.pausedpage.VDPausedPage", classLoader);
            int hooked = 0;
            for (Method method : vdPaused.getDeclaredMethods()) {
                if (!"getCountDownView".equals(method.getName())) {
                    continue;
                }
                NativeHooks.hook("MainHook.PausedCountDown", method, chain -> {
                    Context context = null;
                    for (Object arg : chain.getArgs()) {
                        if (arg instanceof Context) {
                            context = (Context) arg;
                            break;
                        }
                    }
                    FbaLog.i("paused countdown view suppressed");
                    return context == null ? null : new View(context);
                });
                hooked++;
            }
            FbaLog.i("paused countdown hooks=" + hooked);
        } catch (Throwable t) {
            FbaLog.w("paused countdown hook unavailable: " + t);
        }

        try {
            Class<?> detailAdService = Reflect.findClass(
                    "com.bilibili.ship.theseus.ugc.ad.DetailAdService", classLoader);
            for (Method method : detailAdService.getDeclaredMethods()) {
                if ("showPanel".equals(method.getName())) {
                    FbaLog.i("showPanel Method found");
                    NativeHooks.hook("MainHook.DetailAdShowPanel", method, chain -> null);
                    break;
                }
            }
        } catch (Throwable t) {
            FbaLog.e("error on detail ad: " + t, t);
        }
    }

    private static int resolveUnderPlayerId(View rootView) {
        if (rootView == null || rootView.getContext() == null) {
            return 0;
        }
        return rootView.getContext().getResources()
                .getIdentifier("underplayer_container", "id", PACKAGE_NAME);
    }

    public static void log(String message) {
        FbaLog.d(message);
    }
}
