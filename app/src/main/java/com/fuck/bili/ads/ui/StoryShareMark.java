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

import com.fuck.bili.ads.story.StoryDebug;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * "标记广告" chip shown together with the story share sheet.
 *
 * <p>The share sheet is Compose-based, so instead of injecting a row into a
 * composition this adds one framework view to the activity decor while the
 * panel is open and removes it when the share session is dismissed.
 */
public final class StoryShareMark {

    private static final String TAG = "FuckBiliAds";
    private static final String CONTROLLER = "com.bilibili.video.story.action.r0";
    private static final String COORDINATOR = "kntr.common.share.api.session.ShareSessionCoordinator";

    private static final String CHIP_TAG = "fuckbiliads.mark.v1";
    private static final int CHIP_BG = 0xF2FB7299;

    private static final Map<Activity, View> CHIPS = new WeakHashMap<>();

    private StoryShareMark() {
    }

    public static void hook(ClassLoader classLoader) {
        hookController(classLoader);
        hookDismiss(classLoader);
        hookPause();
        FbaLog.i("share mark hook installed");
    }

    private static void hookController(ClassLoader classLoader) {
        final Class<?> controller;
        try {
            controller = XposedHelpers.findClass(CONTROLLER, classLoader);
        } catch (Throwable t) {
            FbaLog.e("share mark controller missing", t);
            return;
        }
        // n(...) is the story share entry: attach the chip.
        try {
            XposedBridge.hookAllMethods(controller, "n", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Activity activity = findActivity(param.thisObject);
                        if (activity != null) {
                            showChip(activity);
                        }
                    } catch (Throwable ignore) {
                        // never disturb the host
                    }
                }
            });
        } catch (Throwable t) {
            FbaLog.e("share mark n() hook failed", t);
        }
        // j(...) returns the panel content: use it to sit right above the sheet.
        try {
            XposedBridge.hookAllMethods(controller, "j", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        final Activity activity = findActivity(param.thisObject);
                        if (activity == null) {
                            return;
                        }
                        Object result = param.getResult();
                        if (!(result instanceof View)) {
                            showChip(activity);
                            return;
                        }
                        final View panel = (View) result;
                        panel.post(() -> align(activity, panel));
                    } catch (Throwable ignore) {
                        // never disturb the host
                    }
                }
            });
        } catch (Throwable t) {
            FbaLog.e("share mark j() hook failed", t);
        }
    }

    /** Moves the chip to just above the share panel's top edge. */
    private static void align(Activity activity, View panel) {
        View chip = activity == null ? null : CHIPS.get(activity);
        if (chip == null || chip.getParent() == null) {
            return;
        }
        try {
            int[] location = new int[2];
            panel.getLocationOnScreen(location);
            int top = location[1];
            int minTop = statusBarHeight(activity) + dp(activity, 24);
            if (top <= minTop) {
                return;
            }
            int chipHeight = chip.getHeight() > 0 ? chip.getHeight() : dp(activity, 30);
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) chip.getLayoutParams();
            params.topMargin = Math.max(minTop, top - chipHeight - dp(activity, 8));
            chip.setLayoutParams(params);
        } catch (Throwable ignore) {
            // keep the default position
        }
    }

    private static void hookDismiss(ClassLoader classLoader) {
        try {
            Class<?> coordinator = XposedHelpers.findClass(COORDINATOR, classLoader);
            XposedBridge.hookAllMethods(coordinator, "b", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    hideAll();
                }
            });
        } catch (Throwable t) {
            FbaLog.e("share mark dismiss hook failed", t);
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
        } catch (Throwable t) {
            FbaLog.e("share mark pause hook failed", t);
        }
    }

    private static Activity findActivity(Object target) {
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                try {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (value instanceof Activity) {
                        return (Activity) value;
                    }
                } catch (Throwable ignore) {
                    // next field
                }
            }
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
            final ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
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

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.TOP | Gravity.END;
            params.topMargin = statusBarHeight(activity) + dp(activity, 56);
            params.rightMargin = dp(activity, 12);
            decor.addView(chip, params);
            CHIPS.put(activity, chip);
            // Safety net: never leave an orphan overlay behind.
            chip.postDelayed(() -> hide(activity), 120_000L);
            FbaLog.i("share mark chip shown");
        } catch (Throwable t) {
            FbaLog.e("show share mark chip failed", t);
        }
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
