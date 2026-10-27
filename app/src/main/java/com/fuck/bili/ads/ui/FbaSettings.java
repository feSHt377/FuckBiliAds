package com.fuck.bili.ads.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.fuck.bili.ads.BuildConfig;
import com.fuck.bili.ads.story.StoryAdFilter;
import com.fuck.bili.ads.story.StoryAdSniper;
import com.fuck.bili.ads.story.StoryDebug;
import com.fuck.bili.ads.story.StoryDiag;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * The module's settings page, rendered inside the host process.
 *
 * <p>Everything is built from framework widgets on purpose: no host resources,
 * no androidx types (the module bundles its own copies, which would not be
 * assignable to the host's classes). Colours follow Bilibili's palette so the
 * page looks native next to 更多服务.
 */
public final class FbaSettings {

    private static final int ACCENT = 0xFFFB7299;
    private static final int LIGHT_BG = 0xFFFFFFFF;
    private static final int LIGHT_TITLE = 0xFF18191C;
    private static final int LIGHT_SUB = 0xFF9499A0;
    private static final int LIGHT_DIVIDER = 0x12000000;

    private static final int DARK_BG = 0xFF17181A;
    private static final int DARK_TITLE = 0xFFE3E5E7;
    private static final int DARK_SUB = 0xFF9499A0;
    private static final int DARK_DIVIDER = 0x1FFFFFFF;

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static Dialog current;

    private FbaSettings() {
    }

    public static void show(Context context) {
        if (context == null) {
            return;
        }
        if (current != null && current.isShowing()) {
            return;
        }
        if (activityOf(context) == null) {
            Toast.makeText(context, "请在哔哩哔哩内打开设置", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            final boolean night = isNight(context);
            final int bg = night ? DARK_BG : LIGHT_BG;
            final int titleColor = night ? DARK_TITLE : LIGHT_TITLE;
            final int subColor = night ? DARK_SUB : LIGHT_SUB;
            final int divider = night ? DARK_DIVIDER : LIGHT_DIVIDER;

            final Dialog dialog = new Dialog(context, night
                    ? android.R.style.Theme_DeviceDefault_NoActionBar
                    : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
            current = dialog;

            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(bg);

            root.addView(buildTopBar(context, dialog, titleColor, subColor, night));

            ScrollView scroll = new ScrollView(context);
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(0, 0, 0, dp(context, 28));
            scroll.addView(content, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(scroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            // ---- 功能 ----
            content.addView(section(context, "功能", subColor));
            content.addView(switchRow(context, "小视频广告自动跳过",
                    "命中 adInfo.feedIsAd 时自动翻到下一条",
                    FbaPrefs.storySkip(), titleColor, subColor, divider,
                    checked -> FbaPrefs.setStorySkip(checked)));
            content.addView(switchRow(context, "彻底剔除广告（实验）",
                    "在列表入口把广告卡摘掉，不再进入播放列表（失败自动退回滑动跳过）",
                    FbaPrefs.storyFilter(), titleColor, subColor, divider,
                    checked -> FbaPrefs.setStoryFilter(checked)));
            content.addView(switchRow(context, "跳过时提示",
                    "使用 B 站自带的底部 Toast",
                    FbaPrefs.storyToast(), titleColor, subColor, divider,
                    checked -> FbaPrefs.setStoryToast(checked)));
            content.addView(switchRow(context, "分享面板标记入口",
                    "在小视频分享面板显示「标记广告」按钮",
                    FbaPrefs.shareMark(), titleColor, subColor, divider,
                    checked -> FbaPrefs.setShareMark(checked)));

            // ---- 调试 ----
            content.addView(section(context, "调试", subColor));
            content.addView(actionRow(context, "标记当前小视频条目",
                    "把当前可见条目的 aid/cid/adInfo 写入日志",
                    titleColor, subColor, divider, v -> markCurrent(context)));
            content.addView(actionRow(context, "复制当前页面诊断",
                    "把页面结构快照复制到剪贴板",
                    titleColor, subColor, divider, v -> copySnapshot(context)));
            content.addView(switchRow(context, "详细日志",
                    "记录每一轮可见条目判定（排查用）",
                    FbaPrefs.verbose(), titleColor, subColor, divider,
                    checked -> FbaPrefs.setVerbose(checked)));
            content.addView(actionRow(context, "测试宿主 Toast",
                    "验证 ToastHelper 是否可用",
                    titleColor, subColor, divider, v -> testToast(context)));

            // ---- 诊断 ----
            content.addView(section(context, "诊断", subColor));
            content.addView(infoRow(context, "模块状态", FbaRuntime.summary(), titleColor, subColor, divider));
            content.addView(infoRow(context, "小视频扫描", StoryDiag.describe(), titleColor, subColor, divider));
            content.addView(infoRow(context, "广告剔除", StoryAdFilter.stats(), titleColor, subColor, divider));
            content.addView(infoRow(context, "日志落盘", FbaLog.fileOk() ? "正常" : ("失败：" + FbaLog.lastError()),
                    titleColor, subColor, divider));
            content.addView(actionRow(context, "播报模块状态",
                    "立即用 Toast 显示各功能安装结果",
                    titleColor, subColor, divider, v -> FbaRuntime.toastNow("FuckBiliAds · " + FbaRuntime.summary())));

            // ---- 日志 ----
            content.addView(section(context, "日志", subColor));
            content.addView(actionRow(context, "查看最近日志",
                    lineCountHint(context), titleColor, subColor, divider,
                    v -> showLog(context, night, bg, titleColor, subColor)));
            content.addView(actionRow(context, "导出日志",
                    "分享文本或保存到 Download/FuckBiliAds",
                    titleColor, subColor, divider, v -> exportLog(context)));
            content.addView(actionRow(context, "清空日志",
                    "删除 events.log",
                    titleColor, subColor, divider, v -> confirmClear(context)));

            // ---- 关于 ----
            content.addView(section(context, "关于", subColor));
            content.addView(infoRow(context, "版本", versionName(context), titleColor, subColor, divider));
            content.addView(infoRow(context, "宿主", hostVersion(context), titleColor, subColor, divider));
            content.addView(infoRow(context, "小视频 Hook", StoryAdSniper.isReady() ? "已就绪" : "未就绪",
                    titleColor, subColor, divider));
            File log = FbaLog.file();
            content.addView(infoRow(context, "日志文件",
                    log == null ? "-" : log.getAbsolutePath(), titleColor, subColor, divider));

            dialog.setContentView(root);
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(bg));
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
            dialog.show();
            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
            FbaLog.i("settings opened");
        } catch (Throwable t) {
            current = null;
            FbaLog.e("settings panel failed", t);
            Toast.makeText(context, "设置页打开失败：" + t, Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- top bar

    private static View buildTopBar(Context context, final Dialog dialog,
                                    int titleColor, int subColor, boolean night) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(context, 8), statusBarHeight(context) + dp(context, 6),
                dp(context, 16), dp(context, 6));

        TextView back = new TextView(context);
        back.setText("←");
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        back.setTextColor(titleColor);
        back.setGravity(Gravity.CENTER);
        int size = dp(context, 40);
        back.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        back.setOnClickListener(v -> dialog.dismiss());
        bar.addView(back);

        TextView title = new TextView(context);
        title.setText("FuckBiliAds");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(titleColor);
        title.setPadding(dp(context, 4), 0, 0, 0);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(title, titleParams);

        TextView version = new TextView(context);
        version.setText(versionName(context));
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        version.setTextColor(subColor);
        bar.addView(version);
        return bar;
    }

    // ------------------------------------------------------------------- rows

    private interface OnSwitch {
        void onChanged(boolean checked);
    }

    private static View section(Context context, String text, int subColor) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextColor(subColor);
        tv.setPadding(dp(context, 16), dp(context, 20), dp(context, 16), dp(context, 6));
        return tv;
    }

    private static View switchRow(Context context, String title, String subtitle,
                                  boolean checked, int titleColor, int subColor, int divider,
                                  final OnSwitch listener) {
        Switch toggle = new Switch(context);
        toggle.setChecked(checked);
        toggle.setThumbTintList(ColorStateList.valueOf(ACCENT));
        toggle.setTrackTintList(ColorStateList.valueOf(0x66FB7299));
        toggle.setOnCheckedChangeListener((button, value) -> {
            listener.onChanged(value);
            FbaLog.i("pref " + title + " = " + value);
        });
        return row(context, title, subtitle, toggle, titleColor, subColor, divider, null);
    }

    private static View actionRow(Context context, String title, String subtitle,
                                  int titleColor, int subColor, int divider, View.OnClickListener click) {
        TextView arrow = new TextView(context);
        arrow.setText("›");
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        arrow.setTextColor(subColor);
        return row(context, title, subtitle, arrow, titleColor, subColor, divider, click);
    }

    private static View infoRow(Context context, String title, String value,
                                int titleColor, int subColor, int divider) {
        TextView tv = new TextView(context);
        tv.setText(value);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTextColor(subColor);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        tv.setMaxWidth(dp(context, 200));
        return row(context, title, null, tv, titleColor, subColor, divider, null);
    }

    private static View row(final Context context, String title, String subtitle, View right,
                            int titleColor, int subColor, int divider, View.OnClickListener click) {
        LinearLayout wrap = new LinearLayout(context);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        texts.setLayoutParams(textParams);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        titleView.setTextColor(titleColor);
        texts.addView(titleView);

        if (!TextUtils.isEmpty(subtitle)) {
            TextView subView = new TextView(context);
            subView.setText(subtitle);
            subView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            subView.setTextColor(subColor);
            subView.setPadding(0, dp(context, 2), 0, 0);
            texts.addView(subView);
        }
        line.addView(texts);
        if (right != null) {
            LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rightParams.leftMargin = dp(context, 12);
            line.addView(right, rightParams);
        }
        wrap.addView(line);

        if (click != null) {
            line.setClickable(true);
            line.setFocusable(true);
            line.setOnClickListener(click);
        }

        View lineDivider = new View(context);
        lineDivider.setBackgroundColor(divider);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(context, 0.5f)));
        dividerParams.leftMargin = dp(context, 16);
        wrap.addView(lineDivider, dividerParams);
        return wrap;
    }

    // ---------------------------------------------------------------- actions

    private static void markCurrent(Context context) {
        Activity activity = activityOf(context);
        String snapshot = StoryDebug.mark(activity, "settings");
        Toast.makeText(context, "已写入日志", Toast.LENGTH_SHORT).show();
        showText(context, "标记结果", snapshot);
    }

    private static void copySnapshot(Context context) {
        Activity activity = activityOf(context);
        String snapshot = StoryDebug.snapshot(activity);
        try {
            ClipboardManager manager = (ClipboardManager)
                    context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager != null) {
                manager.setPrimaryClip(ClipData.newPlainText("FuckBiliAds", snapshot));
                Toast.makeText(context, "诊断信息已复制", Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable t) {
            Toast.makeText(context, "复制失败：" + t, Toast.LENGTH_SHORT).show();
        }
        FbaLog.i("[DIAG] " + snapshot);
    }

    private static void testToast(Context context) {
        try {
            StoryDebug.testHostToast(activityOf(context));
            Toast.makeText(context, "已请求宿主 Toast", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(context, "宿主 Toast 失败：" + t, Toast.LENGTH_SHORT).show();
        }
    }

    private static void confirmClear(final Context context) {
        new AlertDialog.Builder(context)
                .setTitle("清空日志")
                .setMessage("将删除当前全部事件日志，无法恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (d, w) -> {
                    FbaLog.clear();
                    Toast.makeText(context, "日志已清空", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private static void showLog(Context context, boolean night, int bg, int titleColor, int subColor) {
        String content = logText();
        if (content.isEmpty()) {
            Toast.makeText(context, "暂无日志", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        TextView title = new TextView(context);
        title.setText("最近日志");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTextColor(titleColor);
        title.setPadding(dp(context, 16), statusBarHeight(context) + dp(context, 12),
                dp(context, 16), dp(context, 8));
        root.addView(title);

        ScrollView scroll = new ScrollView(context);
        TextView body = new TextView(context);
        body.setText(content);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        body.setTextColor(subColor);
        body.setTypeface(Typeface.MONOSPACE);
        body.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 16));
        body.setTextIsSelectable(true);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button copy = new Button(context);
        copy.setText("复制全部");
        copy.setOnClickListener(v -> {
            ClipboardManager manager = (ClipboardManager)
                    context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager != null) {
                manager.setPrimaryClip(ClipData.newPlainText("FuckBiliAds log", content));
                Toast.makeText(context, "日志已复制", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(copy, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Dialog dialog = new Dialog(context, night
                ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(bg));
        }
        dialog.show();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    /** Current-run ring buffer first, then the persistent file, both tail limited. */
    private static String logText() {
        String recent = FbaLog.recent();
        String file = FbaLog.read();
        StringBuilder sb = new StringBuilder();
        if (!recent.isEmpty()) {
            sb.append("=== 本次运行 ===\n").append(recent);
        }
        if (!file.isEmpty()) {
            sb.append("\n=== 历史日志 ===\n").append(file);
        }
        String all = sb.toString();
        int limit = 300 * 1024;
        return all.length() > limit ? all.substring(all.length() - limit) : all;
    }

    private static void exportLog(Context context) {
        String content = logText();
        if (content.isEmpty()) {
            Toast.makeText(context, "暂无日志", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = FbaLog.exportFileName();

        // 1) save a copy where the user can find it
        String saved = saveToDownloads(context, name, content);

        // 2) and offer the share sheet with the same text
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, "FuckBiliAds log " + name);
            send.putExtra(Intent.EXTRA_TEXT, content);
            send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(Intent.createChooser(send, "导出日志")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) {
            FbaLog.e("share log failed", t);
        }
        if (saved != null) {
            Toast.makeText(context, "已保存到 " + saved, Toast.LENGTH_LONG).show();
        }
    }

    /** Returns a human readable location, or null when writing failed. */
    private static String saveToDownloads(Context context, String name, String content) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, name);
                values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                values.put(MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/FuckBiliAds");
                Uri uri = context.getContentResolver()
                        .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    return null;
                }
                OutputStream out = context.getContentResolver().openOutputStream(uri);
                if (out == null) {
                    return null;
                }
                try {
                    out.write(content.getBytes(UTF8));
                } finally {
                    out.close();
                }
                return "Download/FuckBiliAds/" + name;
            }
            File dir = new File(context.getExternalFilesDir(null), "FuckBiliAds");
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            File out = new File(dir, name);
            FileOutputStream stream = new FileOutputStream(out);
            try {
                stream.write(content.getBytes(UTF8));
            } finally {
                stream.close();
            }
            return out.getAbsolutePath();
        } catch (Throwable t) {
            FbaLog.e("save log failed", t);
            return null;
        }
    }

    private static void showText(Context context, String title, String content) {
        TextView view = new TextView(context);
        view.setText(content);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextIsSelectable(true);
        int pad = dp(context, 16);
        view.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(view);
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("确定", null)
                .show();
    }

    // ------------------------------------------------------------------ util

    private static String lineCountHint(Context context) {
        String content = logText();
        if (content.isEmpty()) {
            return "暂无日志";
        }
        int lines = 1;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines + " 行 · " + (content.length() / 1024) + " KB";
    }

    private static String versionName(Context context) {
        // 模块自身版本，必须读模块的 BuildConfig（context 通常是宿主 Activity）。
        try {
            return BuildConfig.VERSION_NAME;
        } catch (Throwable t) {
            return "1.0.7";
        }
    }

    private static String hostVersion(Context context) {
        try {
            String pkg = context.getPackageName();
            return pkg + " " + context.getPackageManager().getPackageInfo(pkg, 0).versionName;
        } catch (Throwable t) {
            return "?";
        }
    }

    private static Activity activityOf(Context context) {
        Context c = context;
        for (int i = 0; i < 4 && c != null; i++) {
            if (c instanceof Activity) {
                return (Activity) c;
            }
            if (!(c instanceof android.content.ContextWrapper)) {
                return null;
            }
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private static boolean isNight(Context context) {
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int statusBarHeight(Context context) {
        int id = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? context.getResources().getDimensionPixelSize(id) : dp(context, 24);
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
