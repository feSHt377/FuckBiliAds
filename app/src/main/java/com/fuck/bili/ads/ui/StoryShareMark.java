package com.fuck.bili.ads.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.fuck.bili.ads.story.StoryAdSniper;
import com.fuck.bili.ads.story.StoryDebug;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * "标记广告" chip shown together with the story share sheet.
 *
 * <p>The share sheet is Compose-based and its controller class is obfuscated and
 * renamed on every release ({@code r0} in 9.15, {@code k0} in 9.16). The chip is
 * therefore triggered from a stable, name-cheap anchor: the static share-content
 * builder {@code StoryActionHelper} (matched by method signature, not by name),
 * and it is attached to the topmost window so it stays visible above a dialog
 * based panel.
 */
public final class StoryShareMark {

    private static final String TAG = FbaRuntime.TAG;

    private static final String ACTION_HELPER = "com.bilibili.video.story.helper.StoryActionHelper";
    private static final String COORDINATOR = "kntr.common.share.api.session.ShareSessionCoordinator";
    private static final String[] CONTROLLERS = {
            "com.bilibili.video.story.action.k0",
            "com.bilibili.video.story.action.r0"
    };

    private static final String CHIP_TAG = "fuckbiliads.mark.v2";
    private static final int CHIP_BG = 0xF2FB7299;

    private static final Map<Activity, View> CHIPS = new WeakHashMap<>();

    private StoryShareMark() {
    }

    public static void hook(ClassLoader classLoader) {
        boolean helper = hookActionHelper(classLoader);
        boolean controller = hookControllers(classLoader);
        boolean dismiss = hookDismiss(classLoader);
        hookPause();
        FbaRuntime.mark("分享标记", helper);
        FbaLog.i("share mark hooks: helper=" + helper + " controller=" + controller
                + " dismiss=" + dismiss);
    }

    /**
     * Hooks the static {@code (StoryDetail, StoryShareFrom) -> String} builder.
     * Matching by signature keeps working when the method name changes.
     */
    private static boolean hookActionHelper(ClassLoader classLoader) {
        try {
            Class<?> helper = XposedHelpers.findClass(ACTION_HELPER, classLoader);
            for (final Method method : helper.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 2) {
                    continue;
                }
                if (!params[0].getName().endsWith("StoryDetail")
                        || !params[1].getName().endsWith("StoryShareFrom")) {
                    continue;
                }
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Activity activity = storyActivity();
                            if (activity != null) {
                                showChip(activity);
                            }
                        } catch (Throwable ignore) {
                            // never disturb the host
                        }
                    }
                });
                FbaLog.i("share mark anchored on " + method.getDeclaringClass().getName()
                        + "." + method.getName());
                return true;
            }
            FbaLog.w("share mark: no share builder found in " + helper.getName());
            return false;
        } catch (Throwable t) {
            FbaLog.e("share mark: helper missing", t);
            return false;
        }
    }

    private static boolean hookControllers(ClassLoader classLoader) {
        boolean any = false;
        for (String name : CONTROLLERS) {
            try {
                Class<?> controller = XposedHelpers.findClass(name, classLoader);
                XposedBridge.hookAllMethods(controller, "n", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Activity activity = storyActivity();
                            if (activity != null) {
                                showChip(activity);
                            }
                        } catch (Throwable ignore) {
                            // never disturb the host
                        }
                    }
                });
                any = true;
            } catch (Throwable ignore) {
                // class renamed in this build
            }
        }
        return any;
    }

    private static boolean hookDismiss(ClassLoader classLoader) {
        try {
            Class<?> coordinator = XposedHelpers.findClass(COORDINATOR, classLoader);
            XposedBridge.hookAllMethods(coordinator, "b", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    hideAll();
                }
            });
            return true;
        } catch (Throwable t) {
            FbaLog.d("share dismiss hook skipped: " + t);
            return false;
        }
    }

    private static void hookPause() {
        try {
            XposedBridge.hookAllMethods(Activity.class, "onPause", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof Activity) {
                        hide((Activity) param.thisObject);
                    }
                }
            });
        } catch (Throwable ignore) {
            // framework hooks may be unavailable
        }
    }

    private static Activity storyActivity() {
        Activity activity = StoryAdSniper.currentActivity();
        if (activity != null && !activity.isFinishing()) {
            return activity;
        }
        Activity top = FbaRuntime.topActivity();
        if (top == null) {
            return null;
        }
        String name = top.getClass().getName();
        if (name.contains("story") || name.contains("Story")) {
            return top;
        }
        return null;
    }

    private static void showChip(final Activity activity) {
        if (!FbaPrefs.shareMark()) {
            return;
        }
        if (CHIPS.containsKey(activity)) {
            return;
        }
        try {
            ViewGroup container = FbaRuntime.topWindow();
            if (container == null) {
                container = (ViewGroup) activity.getWindow().getDecorView();
            }
            final TextView chip = new TextView(activity);
            chip.setTag(CHIP_TAG);
            chip.setText("标记广告");
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setTextColor(Color.WHITE);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(activity, 14), dp(activity, 7), dp(activity, 14), dp(activity, 7));
            GradientDrawable background = new GradientDrawable();
            background.setColor(CHIP_BG);
            background.setCornerRadius(dp(activity, 15));
            chip.setBackground(background);
            chip.setElevation(dp(activity, 8));
            chip.setOnClickListener(v -> {
                String snapshot = StoryDebug.mark(activity, "share");
                Toast.makeText(activity, "已标记当前条目，已写入日志", Toast.LENGTH_SHORT).show();
                chip.setText("已标记 ✓");
                chip.postDelayed(() -> chip.setText("标记广告"), 1500L);
                FbaLog.i("manual mark from share sheet: " + snapshot);
            });
            chip.setOnLongClickListener(v -> {
                copy(activity, StoryDebug.snapshot(activity));
                Toast.makeText(activity, "诊断信息已复制", Toast.LENGTH_SHORT).show();
                return true;
            });

            container.addView(chip, layoutParams(activity));
            CHIPS.put(activity, chip);
            keepOnTop(activity, chip);
            // Safety net: never leave an orphan overlay behind.
            chip.postDelayed(() -> hide(activity), 120_000L);
            FbaLog.i("share mark chip shown in " + container.getClass().getName());
        } catch (Throwable t) {
            FbaLog.e("show share mark chip failed", t);
        }
    }

    private static FrameLayout.LayoutParams layoutParams(Activity activity) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.topMargin = statusBarHeight(activity) + dp(activity, 52);
        params.rightMargin = dp(activity, 12);
        return params;
    }

    /**
     * The share panel may open in its own dialog window after the trigger ran.
     * Keep moving the chip onto whatever window is currently on top.
     */
    private static void keepOnTop(final Activity activity, final View chip) {
        final android.os.Handler handler = FbaRuntime.handler();
        if (handler == null) {
            return;
        }
        final Runnable[] holder = new Runnable[1];
        final int[] ticks = {0};
        holder[0] = new Runnable() {
            @Override
            public void run() {
                if (CHIPS.get(activity) != chip || ticks[0]++ > 15) {
                    // Stop following after ~6s: otherwise the chip would be
                    // re-attached to the page once the panel window is gone.
                    return;
                }
                try {
                    ViewGroup top = FbaRuntime.topWindow();
                    if (top != null && chip.getParent() != top) {
                        if (chip.getParent() instanceof ViewGroup) {
                            ((ViewGroup) chip.getParent()).removeView(chip);
                        }
                        top.addView(chip, layoutParams(activity));
                        FbaLog.d("share mark chip moved to " + top.getClass().getName());
                    }
                } catch (Throwable ignore) {
                    // keep trying
                }
                handler.postDelayed(holder[0], 400L);
            }
        };
        handler.postDelayed(holder[0], 400L);
    }

    private static void hide(Activity activity) {
        View chip = CHIPS.remove(activity);
        if (chip == null) {
            return;
        }
        try {
            ViewGroup parent = (ViewGroup) chip.getParent();
            if (parent != null) {
                parent.removeView(chip);
            }
        } catch (Throwable ignore) {
            // already detached
        }
    }

    private static void hideAll() {
        for (Activity activity : new java.util.ArrayList<>(CHIPS.keySet())) {
            hide(activity);
        }
    }

    private static void copy(Context context, String text) {
        try {
            ClipboardManager manager = (ClipboardManager)
                    context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager != null) {
                manager.setPrimaryClip(ClipData.newPlainText("FuckBiliAds", text));
            }
        } catch (Throwable ignore) {
            // clipboard unavailable
        }
    }

    private static int statusBarHeight(Context context) {
        int id = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? context.getResources().getDimensionPixelSize(id) : dp(context, 24);
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
