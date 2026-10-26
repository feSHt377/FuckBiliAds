package com.fuck.bili.ads.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import com.fuck.bili.ads.chronos.ChronosPackageOverride;

public final class FollowingFilterSettings {
    public static void show(Context context) {
        FollowingFilterConfig.init(context);
        LinearLayout panel = column(context);
        int padding = dp(context, 20);
        panel.setPadding(padding, dp(context, 8), padding, dp(context, 12));

        Button chronos = new Button(context);
        chronos.setText("弹幕引擎 / 恢复原版");
        panel.addView(chronos, rowParams());
        chronos.setOnClickListener(v ->
                ChronosPackageOverride.showSettings(context));

        Switch recommendation = new Switch(context);
        recommendation.setText("过滤 UP主的推荐");
        recommendation.setPadding(0, dp(context, 12), 0, dp(context, 12));
        recommendation.setChecked(FollowingFilterConfig.load().blockUpRecommend);
        panel.addView(recommendation, rowParams());
        recommendation.setOnCheckedChangeListener((button, checked) -> {
            FollowingFilterConfig.Snapshot current = FollowingFilterConfig.load();
            if (!FollowingFilterConfig.save(checked, current.rules)) {
                toast(context, "保存失败，请检查存储空间");
            }
        });

        Switch forwardLottery = new Switch(context);
        forwardLottery.setText("屏蔽被转发的抽奖（原抽奖动态保留）");
        forwardLottery.setPadding(0, dp(context, 12), 0, dp(context, 12));
        forwardLottery.setChecked(FollowingFilterConfig.load().blockForwardLottery);
        panel.addView(forwardLottery, rowParams());
        forwardLottery.setOnCheckedChangeListener((button, checked) -> {
            FollowingFilterConfig.Snapshot current = FollowingFilterConfig.load();
            if (!FollowingFilterConfig.save(current.blockUpRecommend, checked, current.rules)) {
                toast(context, "保存失败，请检查存储空间");
            }
        });

        TextView hint = new TextView(context);
        hint.setText("转发抽奖开关只检查转发原文是否含“抽奖”，不屏蔽独立发布的原动态。\n"
                + "关键词规则独立生效：如添加“抽奖”，原抽奖动态也会被关键词屏蔽。\n"
                + "任一规则命中即隐藏整条动态。匹配动态正文、Opus摘要和视频标题（含转发原文）。\n"
                + "关键词区分大小写；正则使用查找匹配，可用 (?i) 忽略大小写、(?s) 跨行匹配。\n"
                + "配置自动保存，修改后下拉刷新关注列表生效。");
        hint.setTextSize(13);
        panel.addView(hint, rowParams());

        Button add = new Button(context);
        add.setText("添加关键词 / 正则");
        panel.addView(add, rowParams());
        ScrollView scroll = new ScrollView(context);
        LinearLayout rows = column(context);
        scroll.addView(rows);
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.min(dp(context, 220), context.getResources().getDisplayMetrics().heightPixels / 4)));

        Runnable refresh = () -> renderRules(context, rows);
        add.setOnClickListener(v -> editRule(context, -1, refresh));
        refresh.run();
        new AlertDialog.Builder(context).setTitle("关注页设置")
                .setView(panel).setPositiveButton("完成", null).show();
    }

    private static void renderRules(Context context, LinearLayout rows) {
        rows.removeAllViews();
        List<FollowingFilterConfig.Rule> rules = FollowingFilterConfig.load().rules;
        if (rules.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("暂无规则，点击上方按钮添加。");
            empty.setPadding(0, dp(context, 16), 0, dp(context, 16));
            rows.addView(empty);
            return;
        }
        for (int i = 0; i < rules.size(); i++) {
            final int index = i;
            FollowingFilterConfig.Rule rule = rules.get(i);
            TextView row = new TextView(context);
            row.setText((i + 1) + ". [" + (rule.regex ? "正则" : "关键词") + "] " + rule.text);
            row.setTextSize(15);
            row.setPadding(0, dp(context, 14), 0, dp(context, 14));
            if (rule.regex) row.setTypeface(Typeface.MONOSPACE);
            row.setContentDescription("规则 " + (i + 1) + "，点击编辑或删除");
            row.setOnClickListener(v -> new AlertDialog.Builder(context)
                    .setTitle("规则 " + (index + 1))
                    .setItems(new String[]{"编辑", "删除"}, (dialog, choice) -> {
                        if (choice == 0) editRule(context, index, () -> renderRules(context, rows));
                        else new AlertDialog.Builder(context).setTitle("删除这条规则？")
                                .setMessage(rule.text).setNegativeButton("取消", null)
                                .setPositiveButton("删除", (confirm, which) -> {
                                    FollowingFilterConfig.Snapshot current = FollowingFilterConfig.load();
                                    List<FollowingFilterConfig.Rule> edited = new ArrayList<>(current.rules);
                                    if (index < edited.size()) edited.remove(index);
                                    if (!FollowingFilterConfig.save(current.blockUpRecommend, edited)) {
                                        toast(context, "保存失败");
                                    }
                                    renderRules(context, rows);
                                }).show();
                    }).show());
            rows.addView(row, rowParams());
        }
    }

    private static void editRule(Context context, int index, Runnable refresh) {
        LinearLayout form = column(context);
        int padding = dp(context, 20);
        form.setPadding(padding, dp(context, 8), padding, 0);
        EditText input = new EditText(context);
        input.setHint("输入关键词或正则表达式");
        input.setMinLines(2);
        input.setMaxLines(6);
        input.setSingleLine(false);
        form.addView(input, rowParams());
        CheckBox regex = new CheckBox(context);
        regex.setText("使用正则表达式");
        form.addView(regex, rowParams());
        FollowingFilterConfig.Snapshot original = FollowingFilterConfig.load();
        if (index >= 0 && index < original.rules.size()) {
            FollowingFilterConfig.Rule rule = original.rules.get(index);
            input.setText(rule.text);
            regex.setChecked(rule.regex);
        }
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(index < 0 ? "添加规则" : "编辑规则").setView(form)
                .setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    try {
                        FollowingFilterConfig.Rule rule = new FollowingFilterConfig.Rule(
                                input.getText().toString(), regex.isChecked());
                        FollowingFilterConfig.Snapshot current = FollowingFilterConfig.load();
                        List<FollowingFilterConfig.Rule> edited = new ArrayList<>(current.rules);
                        if (index < 0) edited.add(rule);
                        else if (index < edited.size()) edited.set(index, rule);
                        else {
                            input.setError("规则已变化，请重新打开编辑");
                            return;
                        }
                        if (!FollowingFilterConfig.save(current.blockUpRecommend, edited)) {
                            toast(context, "保存失败");
                            return;
                        }
                        dialog.dismiss();
                        refresh.run();
                    } catch (PatternSyntaxException e) {
                        input.setError("正则错误：" + e.getDescription());
                    } catch (IllegalArgumentException e) {
                        input.setError(e.getMessage());
                    }
                }));
        dialog.show();
    }

    private static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private static LinearLayout.LayoutParams rowParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void toast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    private FollowingFilterSettings() {}
}
