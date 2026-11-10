package com.fuck.bili.ads.story;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.fuck.bili.ads.ui.FbaLog;
import com.fuck.bili.ads.ui.FbaPrefs;

/**
 * 竖屏页点击 UP 头像直接进入 UP 主页（实验性功能，默认关闭）。
 *
 * <p>只在列表静止、且开关打开时给当前卡片的头像挂点击；找不到头像控件就
 * 什么都不做，避免影响原有的点击/滑动手势。
 */
final class StoryAvatarJump {

    private static final String[] AVATAR_IDS = {"avatar", "author_avatar", "up_avatar", "face"};

    private StoryAvatarJump() {
    }

    static void attach(final Activity activity, ViewGroup card, Object item) {
        if (activity == null || card == null || item == null || !FbaPrefs.storyAvatarJump()) {
            return;
        }
        final long mid = ownerMid(item);
        if (mid <= 0) {
            return;
        }
        View avatar = findAvatar(card, 0);
        if (avatar == null) {
            return;
        }
        avatar.setOnClickListener(v -> openSpace(activity, mid));
        avatar.setClickable(true);
    }

    private static long ownerMid(Object item) {
        Object owner = StoryView.fieldValue(item, "owner");
        Object mid = StoryView.fieldValue(owner, "mid");
        return mid instanceof Number ? ((Number) mid).longValue() : -1L;
    }

    private static View findAvatar(View view, int depth) {
        if (view == null || depth > 5) {
            return null;
        }
        if (view instanceof ImageView && looksLikeAvatar(view)) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findAvatar(group.getChildAt(i), depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean looksLikeAvatar(View view) {
        int id = view.getId();
        if (id != View.NO_ID) {
            try {
                String name = view.getResources().getResourceEntryName(id);
                if (name != null) {
                    String lower = name.toLowerCase();
                    for (String key : AVATAR_IDS) {
                        if (lower.contains(key)) {
                            return true;
                        }
                    }
                }
            } catch (Throwable ignore) {
                // resource lookup unavailable
            }
        }
        CharSequence description = view.getContentDescription();
        return description != null && description.toString().contains("头像");
    }

    private static void openSpace(Activity activity, long mid) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("bilibili://space/" + mid));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            FbaLog.i("story avatar jump: mid=" + mid);
        } catch (Throwable t) {
            FbaLog.w("story avatar jump failed: " + t);
        }
    }
}
