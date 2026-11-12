package com.fuck.bili.ads.story;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.ui.FbaLog;
import com.fuck.bili.ads.ui.FbaPrefs;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

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

    private static final Map<View, AvatarBinding> BOUND = new WeakHashMap<>();
    private static final java.util.concurrent.atomic.AtomicReference<AvatarBinding> LATEST =
            new java.util.concurrent.atomic.AtomicReference<>();
    private static boolean hooked;

    /** Handles avatar taps at the touch boundary, where this host actually receives them. */
    static void install() {
        if (hooked) {
            return;
        }
        try {
            Method dispatch = NativeHooks.method(View.class, "dispatchTouchEvent", MotionEvent.class);
            NativeHooks.hook("StoryAvatarJump.Touch", dispatch, chain -> {
                Object self = chain.getThisObject();
                Object arg = chain.getArg(0);
                if (!(self instanceof View) || !(arg instanceof MotionEvent)) {
                    return chain.proceed();
                }
                View view = (View) self;
                MotionEvent event = (MotionEvent) arg;
                AvatarBinding binding = bindingNear(view);
                if (binding == null) {
                    AvatarBinding latest = LATEST.get();
                    if (latest != null && latest.isAvatarPoint(view, event.getRawX(), event.getRawY())) {
                        binding = latest;
                    }
                }
                if (binding == null || !FbaPrefs.storyAvatarJump()) {
                    return chain.proceed();
                }
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    binding.downX = event.getRawX();
                    binding.downY = event.getRawY();
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                        && Math.abs(event.getRawX() - binding.downX) < 30f
                        && Math.abs(event.getRawY() - binding.downY) < 30f) {
                    Activity activity = binding.activity.get();
                    if (activity != null) {
                        openSpace(activity, binding.mid);
                        return true;
                    }
                }
                return chain.proceed();
            });
            hooked = true;
            FbaLog.i("story avatar jump touch hook installed");
        } catch (Throwable t) {
            FbaLog.w("story avatar jump touch hook failed: " + t);
        }
    }

    private static AvatarBinding bindingNear(View view) {
        View current = view;
        for (int i = 0; i < 8 && current != null; i++) {
            AvatarBinding binding = BOUND.get(current);
            if (binding != null) {
                return binding;
            }
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return null;
    }

    static void attach(final Activity activity, ViewGroup card, Object item) {
        if (activity == null || card == null || item == null || !FbaPrefs.storyAvatarJump()) {
            return;
        }
        final long mid = ownerMid(item);
        if (mid <= 0) {
            FbaLog.w("story avatar jump: owner.mid unavailable on "
                    + item.getClass().getName());
            return;
        }
        View avatar = findAvatar(card, 0);
        if (avatar == null) {
            avatar = findAvatarContainer(card, 0);
        }
        if (avatar == null) {
            FbaLog.w("story avatar jump: no avatar view, root=" + card.getClass().getName());
            dumpCandidateViews(card, 0, 0);
            return;
        }
        // The visible ImageView is inside an avatar_layout container in the host;
        // that outer view owns the tap area. Prefer it instead of the image child.
        View target = avatarTarget(avatar);
        synchronized (BOUND) {
            BOUND.put(target, new AvatarBinding(activity, mid));
        }
        LATEST.set(new AvatarBinding(activity, mid, target));
        target.setClickable(true);
        FbaLog.d("story avatar jump attached: mid=" + mid
                + " view=" + target.getClass().getName()
                + " bounds=" + screenBounds(target));
    }

    private static long ownerMid(Object item) {
        Object owner = StoryView.fieldValue(item, "owner");
        Object mid = StoryView.fieldValue(owner, "mid");
        return mid instanceof Number ? ((Number) mid).longValue() : -1L;
    }

    private static View findAvatar(View view, int depth) {
        if (view == null || depth > 5 || view.getVisibility() != View.VISIBLE
                || view.getWidth() <= 0 || view.getHeight() <= 0 || !view.isShown()) {
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

    private static View findAvatarContainer(View view, int depth) {
        if (view == null || depth > 8 || view.getVisibility() != View.VISIBLE
                || view.getWidth() <= 0 || view.getHeight() <= 0 || !view.isShown()) {
            return null;
        }
        if (looksLikeAvatar(view)) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findAvatarContainer(group.getChildAt(i), depth + 1);
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

    private static final class AvatarBinding {
        final java.lang.ref.WeakReference<Activity> activity;
        final long mid;
        final java.lang.ref.WeakReference<View> avatar;
        float downX;
        float downY;

        AvatarBinding(Activity activity, long mid) {
            this(activity, mid, null);
        }

        AvatarBinding(Activity activity, long mid, View avatar) {
            this.activity = new java.lang.ref.WeakReference<>(activity);
            this.mid = mid;
            this.avatar = new java.lang.ref.WeakReference<>(avatar);
        }

        boolean isAvatarPoint(View touched, float x, float y) {
            View target = avatar.get();
            if (target == null || !target.isAttachedToWindow()) {
                return false;
            }
            android.graphics.Rect rect = new android.graphics.Rect();
            if (!target.getGlobalVisibleRect(rect)) {
                return false;
            }
            rect.inset(-12, -12);
            if (!rect.contains((int) x, (int) y)) {
                return false;
            }
            int[] a = new int[2];
            int[] b = new int[2];
            target.getLocationOnScreen(a);
            touched.getLocationOnScreen(b);
            return Math.abs(a[0] - b[0]) <= rect.width()
                    && Math.abs(a[1] - b[1]) <= rect.height();
        }
    }

    /** Walks up to the avatar container, without escaping the current card. */
    private static View avatarTarget(View avatar) {
        View current = avatar;
        View best = avatar;
        for (int depth = 0; depth < 3; depth++) {
            if (looksLikeAvatar(current)) {
                best = current;
            }
            if (!(current.getParent() instanceof View)) {
                break;
            }
            current = (View) current.getParent();
        }
        // The container may have no id/description of its own; its bounds still
        // identify the small avatar tap target without hijacking the author row.
        View parent = avatar.getParent() instanceof View ? (View) avatar.getParent() : null;
        if (parent != null && parent.getWidth() > 0
                && parent.getWidth() <= avatar.getWidth() * 2
                && parent.getHeight() <= avatar.getHeight() * 2) {
            best = parent;
        }
        return best;
    }

    private static long lastDumpAt;

    /** Logs a bounded view sample once per page, instead of guessing its shape. */
    private static void dumpCandidateViews(View view, int depth, int count) {
        if (System.currentTimeMillis() - lastDumpAt < 10000L || depth > 5 || count >= 60) {
            return;
        }
        lastDumpAt = System.currentTimeMillis();
        CharSequence description = view.getContentDescription();
        String id = "";
        try {
            id = view.getId() == View.NO_ID ? "" : view.getResources().getResourceEntryName(view.getId());
        } catch (Throwable ignore) {
            // Resource names are diagnostic only.
        }
        FbaLog.i("story avatar candidate: depth=" + depth
                + " class=" + view.getClass().getName() + " id=" + id
                + " desc=" + description + " size=" + view.getWidth() + "x" + view.getHeight());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                dumpCandidateViews(group.getChildAt(i), depth + 1, count + i + 1);
            }
        }
    }

    private static String screenBounds(View view) {
        if (view == null) {
            return "null";
        }
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return location[0] + "," + location[1] + "," + view.getWidth() + "x" + view.getHeight();
    }

    private static void openSpace(Activity activity, long mid) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("bilibili://space/" + mid));
            intent.setPackage(activity.getPackageName());
            activity.startActivity(intent);
            FbaLog.i("story avatar jump: mid=" + mid);
        } catch (Throwable t) {
            FbaLog.w("story avatar jump failed: " + t);
        }
    }
}
