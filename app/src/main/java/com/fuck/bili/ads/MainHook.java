package com.fuck.bili.ads;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.util.AttributeSet;
import android.view.GestureDetector;
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
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/** Native libxposed API 102 entry point. */
public class MainHook extends XposedModule {

    private static final String PACKAGE_NAME = "tv.danmaku.bili";
    private static final String PACKAGE_NAME_ALIAS = "tv.danmaku.bilj";
    private static final String INTERNATIONAL_PACKAGE_NAME = "com.bilibili.app.in";
    private static final String HD_PACKAGE_NAME = "tv.danmaku.bilibilihd";

    public static boolean isInternational = false;

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
        if (INTERNATIONAL_PACKAGE_NAME.equals(packageName)) {
            isInternational = true;
        }
        final boolean hd = Host.isHdPackage(packageName);

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
        install("开屏拦截", () -> BiliSplashSniper.hookEarly(classLoader));
        if (!hd) {
            install("播放页广告", () -> BiliAdSniper.hook(classLoader));
        } else {
            install("HD播放页广告", () -> HdAdSniper.hook(classLoader));
        }
        install("底部导航", () -> BiliBottomNavSniper.hook(classLoader));
        install("抓包", () -> PacketSniffer.hook(classLoader));
        install("关注页广告", () -> FollowingListAdSniper.hook(classLoader));
        install("关注页设置", () -> FollowingTopLeftButton.hook(classLoader));
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
        install("广告卡片", () -> BiliCardAdSniper.hook(classLoader, helper));
        install("开屏跳过", () -> BiliSplashSniper.hookDisplayFallback(classLoader, helper));
        if (!Host.isHdPackage(packageName)) {
            install("视频提及", () -> BiliMentionedSniper.hook(classLoader, helper));
        }
        install("全局广告", () -> GlobalAdSniperEngine.hook(classLoader, helper));
        if (!Host.isHdPackage(packageName)) {
            install("大会员预测", () -> VIPPredict.hook(classLoader, helper));
        }
        if (!Host.isHdPackage(packageName)) {
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

    private static void descCopyFix(ClassLoader classLoader, DexKitHelper helper) {
        try {
            final WeakHashMap<View, GestureDetector> gestureDetectorMap = new WeakHashMap<>();
            Class<?> type = Reflect.findClass(helper.DESCRIPTION_TEXTVIEW_CLASS_NAME, classLoader);

            Constructor<?> constructor = type.getDeclaredConstructor(
                    Context.class, AttributeSet.class, int.class);
            constructor.setAccessible(true);
            NativeHooks.hook("MainHook.DescriptionConstructor", constructor, chain -> {
                Object result = chain.proceed();
                TextView tv = (TextView) chain.getThisObject();
                tv.setTextIsSelectable(true);
                tv.setFocusable(true);
                tv.setFocusableInTouchMode(true);
                tv.setLongClickable(true);
                tv.setMovementMethod(android.text.method.ArrowKeyMovementMethod.getInstance());
                return result;
            });

            Method onTouch = NativeHooks.method(type, "onTouchEvent", android.view.MotionEvent.class);
            NativeHooks.hook("MainHook.DescriptionTouch", onTouch, chain -> {
                final TextView tv = (TextView) chain.getThisObject();
                android.view.MotionEvent event = (android.view.MotionEvent) chain.getArg(0);
                int action = event.getActionMasked();
                if (action == android.view.MotionEvent.ACTION_DOWN) {
                    tv.requestFocus();
                    if (tv.getParent() != null) {
                        tv.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }

                Object editorObj = null;
                try {
                    java.lang.reflect.Field editorField = TextView.class.getDeclaredField("mEditor");
                    editorField.setAccessible(true);
                    editorObj = editorField.get(tv);
                    if (editorObj != null) {
                        Method onTouchMethod = editorObj.getClass()
                                .getDeclaredMethod("onTouchEvent", android.view.MotionEvent.class);
                        onTouchMethod.setAccessible(true);
                        onTouchMethod.invoke(editorObj, event);
                    }
                } catch (Exception ignored) {
                    FbaLog.d("description editor touch dispatch unavailable: " + ignored);
                }

                final Object finalEditorObj = editorObj;

                GestureDetector gd = gestureDetectorMap.get(tv);
                if (gd == null) {
                    gd = new GestureDetector(tv.getContext(), new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public void onLongPress(android.view.MotionEvent e) {
                            boolean result = tv.performLongClick();
                            FbaLog.d("BiliHook -> performLongClick returned: " + result);

                            if (!result && finalEditorObj != null) {
                                try {
                                    int offset = tv.getOffsetForPosition(e.getX(), e.getY());
                                    int start = Math.max(0, offset - 2);
                                    int end = Math.min(tv.getText().length(), offset + 2);
                                    android.text.Selection.setSelection(
                                            (android.text.Spannable) tv.getText(), start, end);

                                    Method startActionMode = finalEditorObj.getClass()
                                            .getDeclaredMethod("startSelectionActionModeAsync", boolean.class);
                                    startActionMode.setAccessible(true);
                                    startActionMode.invoke(finalEditorObj, false);
                                    FbaLog.d("BiliHook -> Hard launched Selection ActionMode.");
                                } catch (Exception ex) {
                                    FbaLog.e("BiliHook -> Hard launch failed", ex);
                                }
                            }
                        }

                        @Override
                        public boolean onSingleTapUp(android.view.MotionEvent e) {
                            tv.performClick();
                            return true;
                        }
                    });
                    gd.setIsLongpressEnabled(true);
                    gestureDetectorMap.put(tv, gd);
                }

                gd.onTouchEvent(event);

                if (tv.getMovementMethod() != null && tv.getText() instanceof android.text.Spannable) {
                    tv.getMovementMethod().onTouchEvent(tv, (android.text.Spannable) tv.getText(), event);
                }

                return true;
            });
        } catch (Throwable t) {
            FbaLog.e("BiliHook -> descCopyFix failed", t);
        }
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
            NativeHooks.hook("MainHook.PausedPageAd", create, chain ->
                    new View(((ViewGroup) chain.getArg(0)).getContext()));
        } catch (Throwable t) {
            FbaLog.e("error on paused page ad: " + t, t);
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
