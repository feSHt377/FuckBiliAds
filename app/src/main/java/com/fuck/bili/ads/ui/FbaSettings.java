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
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.fuck.bili.ads.BuildConfig;
import com.fuck.bili.ads.story.StoryAdFilter;
import com.fuck.bili.ads.story.StoryAdSniper;
import com.fuck.bili.ads.story.StoryContentFilter;
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

            // ---- 小视频 ----
            content.addView(section(context, "小视频", "自动跳过或从播放列表剔除广告", subColor));
            content.addView(switchRow(context, "自动跳过广告",
                    "检测到当前条目为广告时，自动滑动到下一条",
                    FbaPrefs.storySkip(), titleColor, subColor, divider,
                    checked -> FbaPrefs.setStorySkip(checked)));
            content.addView(switchRow(context, "跳过时显示提示",
                    "自动跳过后显示「检测到广告，已跳过」Toast",
                    FbaPrefs.storyToast(), titleColor, subColor, divider,
                    checked -> FbaPrefs.setStoryToast(checked)));
            content.addView(contentFilterGroup(context, titleColor, subColor, divider));
            content.addView(switchRow(context, "剔除时显示提示",
                    "剔除内容后通过 Toast 显示本次剔除总数",
                    FbaPrefs.storyFilterToast(), titleColor, subColor, divider,
                    checked -> FbaPrefs.setStoryFilterToast(checked)));
            content.addView(switchRow(context, "显示详细剔除内容",
                    "在提示中追加各类型数量，例如「广告 *1，直播 *2」；默认关闭",
                    FbaPrefs.storyFilterVerbose(), titleColor, subColor, divider,
                    checked -> FbaPrefs.setStoryFilterVerbose(checked)));
            content.addView(switchRow(context, "提示中显示内容位置",
                    "在剔除提示后追加距当前内容的「（后 N 条）」；默认关闭",
                    FbaPrefs.storyFilterOffset(), titleColor, subColor, divider,
                    checked -> FbaPrefs.setStoryFilterOffset(checked)));

            // ---- 分享与标记 ----
            content.addView(section(context, "分享与标记", "用于采集小视频广告信息", subColor));
            content.addView(switchRow(context, "分享面板显示「标记广告」",
                    "在小视频分享面板添加入口，将当前条目信息写入日志",
                    FbaPrefs.shareMark(), titleColor, subColor, divider,
                    checked -> FbaPrefs.setShareMark(checked)));

            // ---- 启动页 ----
            content.addView(section(context, "启动页", "开屏外观设置，下次启动生效", subColor));
            content.addView(actionRow(context, "启动页配色",
                    splashThemeLabel() + " · 可选择跟随系统、浅色或深色",
                    titleColor, subColor, divider, v -> chooseSplashTheme(context)));
            content.addView(switchRow(context, "隐藏品牌开屏立绘",
                    "隐藏「22娘/33娘」本地立绘，不影响广告拦截；默认关闭",
                    FbaPrefs.brandSplashBlock(), titleColor, subColor, divider,
                    checked -> FbaPrefs.setBrandSplashBlock(checked)));

            // ---- 更新 ----
            content.addView(section(context, "模块更新", "通过 GitHub Releases 检查新版本", subColor));
            UpdateChecker.Release update = UpdateChecker.available();
            if (update != null) {
                content.addView(updateBanner(context, update, subColor, divider));
            }
            content.addView(switchRow(context, "启动时自动检查更新",
                    "冷启动约 12 秒后查询一次，6 小时内不重复",
                    FbaPrefs.updateAuto(), titleColor, subColor, divider,
                    checked -> FbaPrefs.setUpdateAuto(checked)));
            content.addView(switchRow(context, "接收测试版更新",
                    "将 pre-release 纳入更新检查；关闭后只检查正式版",
                    FbaPrefs.updatePrerelease(), titleColor, subColor, divider,
                    checked -> FbaPrefs.setUpdatePrerelease(checked)));
            content.addView(infoRow(context, "当前安装版本", UpdateChecker.currentVersion(),
                    titleColor, subColor, divider));
            content.addView(infoRow(context, "最近检查结果", UpdateChecker.latestLabel(),
                    titleColor, subColor, divider));
            content.addView(actionRow(context, "立即检查更新",
                    UpdateChecker.statusHint(), titleColor, subColor, divider,
                    v -> UpdateChecker.check(context, true)));
            content.addView(actionRow(context, "查看全部版本",
                    "打开 GitHub Releases 页面",
                    titleColor, subColor, divider,
                    v -> UpdateChecker.openReleasePage(context)));

            // ---- 调试工具 ----
            content.addView(section(context, "调试工具", "仅在排查问题时使用", subColor));
            content.addView(actionRow(context, "标记当前小视频条目",
                    "将当前可见条目的 aid、cid、adInfo 写入日志",
                    titleColor, subColor, divider, v -> markCurrent(context)));
            content.addView(actionRow(context, "复制当前页面诊断",
                    "复制页面结构快照，便于反馈问题",
                    titleColor, subColor, divider, v -> copySnapshot(context)));
            content.addView(switchRow(context, "记录详细日志",
                    "记录每轮可见条目判定，可能增加日志量；默认关闭",
                    FbaPrefs.verbose(), titleColor, subColor, divider,
                    checked -> FbaPrefs.setVerbose(checked)));
            content.addView(actionRow(context, "测试宿主 Toast",
                    "验证能否使用 B 站自身的 Toast 提示",
                    titleColor, subColor, divider, v -> testToast(context)));
            content.addView(actionRow(context, "测试广告剔除提示",
                    FbaPrefs.storyFilterOffset()
                            ? "模拟「已剔除 3 个内容（后 3 条）」，不修改播放列表"
                            : "模拟「已剔除 3 个内容」，不修改播放列表",
                    titleColor, subColor, divider,
                    v -> StoryAdFilter.debugToast(activityOf(context), 3, 3)));
            content.addView(actionRow(context, "播报模块加载状态",
                    "通过 Toast 显示各项功能的 Hook 安装结果",
                    titleColor, subColor, divider,
                    v -> FbaRuntime.toastNow("FuckBiliAds · " + FbaRuntime.summary())));

            // ---- 日志管理 ----
            content.addView(section(context, "日志管理", "查看、导出或清理模块日志", subColor));
            content.addView(actionRow(context, "查看最近日志",
                    lineCountHint(context), titleColor, subColor, divider,
                    v -> showLog(context, night, bg, titleColor, subColor)));
            content.addView(actionRow(context, "导出日志",
                    "分享日志文本，或保存至 Download/FuckBiliAds",
                    titleColor, subColor, divider, v -> exportLog(context)));
            content.addView(actionRow(context, "清空日志",
                    "删除 events.log；此操作不可撤销",
                    titleColor, subColor, divider, v -> confirmClear(context)));

            // ---- 运行诊断 ----
            content.addView(section(context, "运行诊断", "本次启动后的模块运行状态", subColor));
            content.addView(infoRow(context, "Hook 安装状态", FbaRuntime.summary(), titleColor, subColor, divider));
            content.addView(infoRow(context, "小视频页面扫描", StoryDiag.describe(), titleColor, subColor, divider));
            content.addView(infoRow(context, "广告剔除统计", StoryAdFilter.stats(), titleColor, subColor, divider));
            content.addView(infoRow(context, "日志文件状态", FbaLog.fileOk() ? "正常写入" : ("写入失败：" + FbaLog.lastError()),
                    titleColor, subColor, divider));

            // ---- 关于 ----
            content.addView(section(context, "关于模块", null, subColor));
            content.addView(infoRow(context, "模块版本", versionName(context), titleColor, subColor, divider));
            content.addView(infoRow(context, "宿主版本", hostVersion(context), titleColor, subColor, divider));
            content.addView(infoRow(context, "小视频 Hook", StoryAdSniper.isReady() ? "已就绪" : "未就绪",
                    titleColor, subColor, divider));
            File log = FbaLog.file();
            content.addView(infoRow(context, "日志保存路径",
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
        UpdateChecker.Release update = UpdateChecker.available();
        if (update != null) {
            version.setText(versionName(context) + " · 可更新");
            version.setTextColor(ACCENT);
            version.setPadding(0, dp(context, 4), 0, dp(context, 4));
            version.setOnClickListener(v -> UpdateChecker.showDialog(context, update));
        } else {
            version.setText(versionName(context));
            version.setTextColor(subColor);
        }
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        bar.addView(version);
        return bar;
    }

    // ------------------------------------------------------------------- rows

    /** Expandable multi-select group sharing the playlist filter with ad removal. */
    private static View contentFilterGroup(Context context, int titleColor, int subColor, int divider) {
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);

        LinearLayout options = new LinearLayout(context);
        options.setOrientation(LinearLayout.VERTICAL);
        options.setVisibility(View.GONE);
        TextView arrow = new TextView(context);
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        arrow.setTextColor(subColor);

        TextView summary = new TextView(context);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        summary.setTextColor(subColor);
        summary.setPadding(dp(context, 16), dp(context, 2), dp(context, 16), dp(context, 8));

        View header = row(context, "自定义剔除内容", "展开后选择要从竖屏列表剔除的类型", arrow,
                titleColor, subColor, divider, null);
        group.addView(header);
        group.addView(summary);
        group.addView(options);

        final int[] bits = {StoryContentFilter.AD, StoryContentFilter.LIVE,
                StoryContentFilter.PGC, StoryContentFilter.COURSE, StoryContentFilter.PREVIEW};
        final String[] labels = {"广告（默认）", "直播推荐", "影视 / 番剧", "课程", "充电 / 预览视频"};
        final String[] descriptions = {"adInfo.feedIsAd", "vertical_live", "vertical_pgc",
                "vertical_course", "vertical_preview_av"};

        Switch master = new Switch(context);
        master.setThumbTintList(ColorStateList.valueOf(ACCENT));
        master.setTrackTintList(ColorStateList.valueOf(0x66FB7299));
        master.setChecked(FbaPrefs.storyFilter());
        master.setOnCheckedChangeListener((button, checked) -> {
            FbaPrefs.setStoryFilter(checked);
            updateFilterState(master, options, summary);
            FbaLog.i("pref 从列表剔除广告及所选内容 = " + checked);
        });
        View masterRow = row(context, "从列表剔除广告及所选内容 · 实验",
                "开启后按下方选择过滤竖屏推荐；广告默认勾选。关闭后保留自动跳过，所选类型不移除",
                master, titleColor, subColor, divider, null);
        group.addView(masterRow, 0);

        for (int i = 0; i < bits.length; i++) {
            final int bit = bits[i];
            CheckBox check = new CheckBox(context);
            check.setText(labels[i] + "  ·  " + descriptions[i]);
            check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            check.setTextColor(titleColor);
            check.setButtonTintList(ColorStateList.valueOf(ACCENT));
            check.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
            check.setChecked((FbaPrefs.storyContentMask() & bit) != 0);
            check.setOnCheckedChangeListener((button, checked) -> {
                int mask = FbaPrefs.storyContentMask();
                FbaPrefs.setStoryContentMask(checked ? mask | bit : mask & ~bit);
                if (master.isChecked()) {
                    summary.setText(contentFilterSummary());
                }
                FbaLog.i("pref 自定义剔除内容 = " + contentFilterSummary());
            });
            options.addView(check);
        }

        TextView note = new TextView(context);
        note.setText("除广告外的类型默认不勾选；当前正在播放的条目不会被移除。");
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        note.setTextColor(subColor);
        note.setPadding(dp(context, 16), dp(context, 4), dp(context, 16), dp(context, 12));
        options.addView(note);
        summary.setText(contentFilterSummary());

        header.setOnClickListener(v -> {
            boolean expand = options.getVisibility() != View.VISIBLE;
            options.setVisibility(expand ? View.VISIBLE : View.GONE);
            summary.setVisibility(expand ? View.GONE : View.VISIBLE);
            arrow.setText(expand ? "⌃" : "⌄");
        });
        arrow.setText("⌄");
        updateFilterState(master, options, summary);
        return group;
    }

    private static String contentFilterSummary() {
        int mask = FbaPrefs.storyContentMask();
        if (mask == 0) {
            return "广告 · 点击展开选择更多类型";
        }
        StringBuilder text = new StringBuilder("已选 ");
        int count = 0;
        int[] bits = {StoryContentFilter.AD, StoryContentFilter.LIVE, StoryContentFilter.PGC,
                StoryContentFilter.COURSE, StoryContentFilter.PREVIEW};
        for (int bit : bits) {
            if ((mask & bit) != 0) {
                if (count++ > 0) {
                    text.append("、");
                }
                text.append(StoryContentFilter.label(bit));
            }
        }
        return text.append(" · 点击修改").toString();
    }

    private static void updateFilterState(Switch master, ViewGroup options, TextView summary) {
        boolean enabled = master.isChecked();
        options.setAlpha(enabled ? 1f : 0.45f);
        int count = options.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = options.getChildAt(i);
            if (child instanceof CheckBox) {
                child.setEnabled(enabled);
            }
        }
        if (!enabled) {
            summary.setText("已关闭 · 所选内容不会从列表剔除");
        } else {
            summary.setText(contentFilterSummary());
        }
    }

    private interface OnSwitch {
        void onChanged(boolean checked);
    }

    private static View section(Context context, String text, String subtitle, int subColor) {
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(dp(context, 16), dp(context, 22), dp(context, 16), dp(context, 6));

        TextView title = new TextView(context);
        title.setText(text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(ACCENT);
        group.addView(title);

        if (!TextUtils.isEmpty(subtitle)) {
            TextView description = new TextView(context);
            description.setText(subtitle);
            description.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            description.setTextColor(subColor);
            description.setPadding(0, dp(context, 2), 0, 0);
            group.addView(description);
        }
        return group;
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

    /** Accent banner with the "前往下载" button, only when a newer release exists. */
    private static View updateBanner(final Context context, final UpdateChecker.Release release,
                                     int subColor, int divider) {
        LinearLayout wrap = new LinearLayout(context);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout banner = new LinearLayout(context);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setBackgroundColor(0x12FB7299);
        banner.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(context);
        title.setText("发现新版本 " + release.version());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(ACCENT);
        texts.addView(title);

        TextView sub = new TextView(context);
        sub.setText(release.channel() + " · " + release.dateText());
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sub.setTextColor(subColor);
        sub.setPadding(0, dp(context, 2), 0, 0);
        texts.addView(sub);
        banner.addView(texts);

        TextView button = pill(context, "前往下载");
        button.setOnClickListener(v -> UpdateChecker.open(context, release.url));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.leftMargin = dp(context, 12);
        banner.addView(button, buttonParams);

        banner.setClickable(true);
        banner.setOnClickListener(v -> UpdateChecker.showDialog(context, release));
        wrap.addView(banner);

        View lineDivider = new View(context);
        lineDivider.setBackgroundColor(divider);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(context, 0.5f)));
        dividerParams.leftMargin = dp(context, 16);
        wrap.addView(lineDivider, dividerParams);
        return wrap;
    }

    /** Small accent-filled call-to-action chip. */
    private static TextView pill(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 14), dp(context, 7), dp(context, 14), dp(context, 7));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(ACCENT);
        shape.setCornerRadius(dp(context, 18));
        view.setBackground(shape);
        view.setClickable(true);
        return view;
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

    private static String splashThemeLabel() {
        String mode = FbaPrefs.splashTheme();
        if ("light".equals(mode)) {
            return "浅色";
        }
        if ("dark".equals(mode)) {
            return "深色";
        }
        return "跟随系统";
    }

    private static void chooseSplashTheme(final Context context) {
        final String[] values = {"auto", "light", "dark"};
        String mode = FbaPrefs.splashTheme();
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(mode)) {
                checked = i;
            }
        }
        new AlertDialog.Builder(context)
                .setTitle("启动页配色")
                .setSingleChoiceItems(new String[]{"跟随系统", "浅色", "深色"}, checked, (dialog, which) -> {
                    FbaPrefs.setSplashTheme(values[which]);
                    FbaLog.i("pref 启动页配色 = " + values[which]);
                    dialog.dismiss();
                    if (current != null && current.isShowing()) {
                        current.dismiss();
                    }
                    show(context);
                })
                .setNegativeButton("取消", null)
                .show();
    }

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
