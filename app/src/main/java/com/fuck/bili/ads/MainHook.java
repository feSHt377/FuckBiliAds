package com.fuck.bili.ads;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.Log;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import com.fuck.bili.ads.chronos.DanmuJump;
import com.fuck.bili.ads.settings.FollowingTopLeftButton;
import com.fuck.bili.ads.story.StoryAdSniper;

public class MainHook implements IXposedHookLoadPackage {

    private static final String PACKAGE_NAME = "tv.danmaku.bili";

    private static final String PACKAGE_NAME_ALIAS = "tv.danmaku.bilj";
    private static final String INTERNATIONAL_PACKAGE_NAME = "com.bilibili.app.in";
    private static final boolean IS_DEBUG = true;
    private static final String TAG = "FuckBiliAds";
    public static boolean isInternational = false;

    private int dynamicTargetViewId = -1;

    private static final Map<String, Integer> titleIndexMapping = new HashMap<>();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(PACKAGE_NAME) && !lpparam.packageName.equals(INTERNATIONAL_PACKAGE_NAME) && !lpparam.packageName.equals(PACKAGE_NAME_ALIAS))
            return;
        if (lpparam.packageName.equals(INTERNATIONAL_PACKAGE_NAME)) isInternational = true;

        try {
            System.loadLibrary("dexkit");
        } catch (Throwable t) {
            log("加载 DexKit 动态库失败: " + t.getMessage());
        }


        DexKitHelper helper = new DexKitHelper();
        helper.resolve(lpparam);

        log("开始执行业务 Hook 逻辑...");
        executeOriginalHooks(lpparam, helper);
        BiliCardAdSniper.hook(lpparam.classLoader, helper);

        // ---- 新增：版本号检测逻辑 ----
        boolean shouldRunDescCopyFix = true;
        try {
            Object activityThread = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentActivityThread"
            );
            if (activityThread != null) {
                Context systemContext = (Context) XposedHelpers.callMethod(activityThread, "getSystemContext");
                PackageInfo packageInfo = systemContext.getPackageManager().getPackageInfo(lpparam.packageName, 0);
                int versionCode = packageInfo.versionCode;

                log("检测到宿主版本号: " + versionCode);

                if (versionCode <= 8090300) {
                    shouldRunDescCopyFix = false;
                }
            }
        } catch (Throwable t) {
            log("获取宿主版本号失败，将默认执行 descCopyFix: " + t.getMessage());
        }
        BiliAdSniper.hook(lpparam.classLoader);
        BiliSplashSniper.hook(lpparam.classLoader, helper);
        BiliMentionedSniper.hook(lpparam.classLoader, helper);
        BiliBottomNavSniper.hook(lpparam.classLoader);
        GlobalAdSniperEngine.hook(lpparam.classLoader, helper);
        PacketSniffer.hook(lpparam.classLoader);
        VIPPredict.hook(lpparam.classLoader, helper);
        LiveRoomAdSniper.hook(lpparam.classLoader, helper);
        FreeDataToastSniper.hook(lpparam.classLoader, helper);
        DanmuJump.hook(lpparam.classLoader);
        FollowingListAdSniper.hook(lpparam.classLoader);
        FollowingTopLeftButton.hook(lpparam.classLoader);
        StoryAdSniper.hook(lpparam.classLoader);
//        VipJsonTestHook.hook(lpparam.classLoader);
//        EffectiveVipTesting.hook(lpparam.classLoader);


        if (shouldRunDescCopyFix) {
            descCopyFix(lpparam, helper);
        } else {
            log("宿主版本号小于 8.9.0(8090300)，跳过 descCopyFix 逻辑。");
        }
    }

    private void descCopyFix(XC_LoadPackage.LoadPackageParam lpparam, DexKitHelper helper) {
        try {
            final String TAG = "BiliHook -> ";
            final WeakHashMap<View, GestureDetector> gestureDetectorMap = new WeakHashMap<>();
            XposedHelpers.findAndHookConstructor(
                    helper.DESCRIPTION_TEXTVIEW_CLASS_NAME,
                    lpparam.classLoader,
                    Context.class,
                    AttributeSet.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            android.widget.TextView tv = (android.widget.TextView) param.thisObject;
                            tv.setTextIsSelectable(true);
                            tv.setFocusable(true);
                            tv.setFocusableInTouchMode(true);
                            tv.setLongClickable(true);
                            tv.setMovementMethod(android.text.method.ArrowKeyMovementMethod.getInstance());
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    helper.DESCRIPTION_TEXTVIEW_CLASS_NAME,
                    lpparam.classLoader,
                    "onTouchEvent",
                    android.view.MotionEvent.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            final android.widget.TextView tv = (android.widget.TextView) param.thisObject;
                            android.view.MotionEvent event = (android.view.MotionEvent) param.args[0];
                            int action = event.getActionMasked();
                            if (action == android.view.MotionEvent.ACTION_DOWN) {
                                tv.requestFocus();
                                if (tv.getParent() != null) {
                                    tv.getParent().requestDisallowInterceptTouchEvent(true);
                                }
                            }

                            Object editorObj = null;
                            try {
                                java.lang.reflect.Field editorField = android.widget.TextView.class.getDeclaredField("mEditor");
                                editorField.setAccessible(true);
                                editorObj = editorField.get(tv);
                                if (editorObj != null) {
                                    java.lang.reflect.Method onTouchMethod = editorObj.getClass().getDeclaredMethod("onTouchEvent", android.view.MotionEvent.class);
                                    onTouchMethod.setAccessible(true);
                                    onTouchMethod.invoke(editorObj, event);
                                }
                            } catch (Exception ignored) {
                            }

                            final Object finalEditorObj = editorObj;

                            android.view.GestureDetector gd = gestureDetectorMap.get(tv);
                            if (gd == null) {
                                gd = new android.view.GestureDetector(tv.getContext(), new android.view.GestureDetector.SimpleOnGestureListener() {
                                    @Override
                                    public void onLongPress(android.view.MotionEvent e) {
                                        boolean result = tv.performLongClick();
                                        log(TAG + "performLongClick returned: " + result);

                                        if (!result && finalEditorObj != null) {
                                            try {
                                                int offset = tv.getOffsetForPosition(e.getX(), e.getY());
                                                int start = Math.max(0, offset - 2);
                                                int end = Math.min(tv.getText().length(), offset + 2);
                                                android.text.Selection.setSelection((android.text.Spannable) tv.getText(), start, end);

                                                java.lang.reflect.Method startActionMode = finalEditorObj.getClass().getDeclaredMethod("startSelectionActionModeAsync", boolean.class);
                                                startActionMode.setAccessible(true);
                                                startActionMode.invoke(finalEditorObj, false);
                                                log(TAG + "Hard launched Selection ActionMode.");
                                            } catch (Exception ex) {
                                                log(TAG + "Hard launch failed: " + ex.getMessage());
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
                        }
                    }
            );
        } catch (Exception e) {
            log("BiliHook -> Error: " + e.toString());
        }
    }


    private void executeOriginalHooks(XC_LoadPackage.LoadPackageParam lpparam, DexKitHelper helper) {
        if (!isInternational) {
            removeAdUnderPlayer(lpparam);
            try {
                BiliGsonLobotomy.hookPegasusAd(lpparam.classLoader, helper.PEGASUS_MODEL_CLASS_NAME);
            } catch (Throwable t) {
            }

        }
        DanmuSenderLookup.hook(lpparam, helper);
    }

    private void removeAdUnderPlayer(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> ViewBindingsClass = XposedHelpers.findClass("androidx.viewbinding.ViewBindings", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(ViewBindingsClass, "findChildViewById", View.class, int.class, new XC_MethodHook() {

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (dynamicTargetViewId == -1) {
                        View rootView = (View) param.args[0];
                        if (rootView != null && rootView.getContext() != null) {
                            dynamicTargetViewId = rootView.getContext().getResources().getIdentifier("underplayer_container", "id", PACKAGE_NAME);

                            if (dynamicTargetViewId == 0) {
                                log("未能动态获取到控件 ID，请检查控件名是否正确");
                                dynamicTargetViewId = 0;
                            } else {
                                log("成功动态绑定控件 ID: " + dynamicTargetViewId);
                            }
                        }
                    }

                    if (dynamicTargetViewId > 0 && (int) param.args[1] == dynamicTargetViewId) {
                        View targetView = (View) param.getResult();
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
                }
            });
        } catch (Throwable t) {
            log("error removing ad: " + t.toString());
        }

        try {
            Class<?> AdPausedPagePanelClass = XposedHelpers.findClass("com.bilibili.ad.adview.videodetail.pausedpage.AdPausedPagePanel", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(AdPausedPagePanelClass, "onCreateView", ViewGroup.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Context context = ((ViewGroup) param.args[0]).getContext();
                    param.setResult(new View(context));
                }
            });
        } catch (Throwable t) {
            log("error on paused page ad: " + t.toString());
        }

        try {
            Class<?> DetailAdServiceClass = XposedHelpers.findClass("com.bilibili.ship.theseus.ugc.ad.DetailAdService", lpparam.classLoader);
            for (Method method : DetailAdServiceClass.getDeclaredMethods()) {
                if (method.getName().equals("showPanel")) {
                    log("showPanel Method found");
                    XposedBridge.hookMethod(method, XC_MethodReplacement.DO_NOTHING);
                    break;
                }
            }
        } catch (Throwable t) {
            log("error on detail ad: " + t);
        }
    }

    public static void log(String msg) {
        if (IS_DEBUG) {
            Log.d(TAG, msg);
        }
    }
}
