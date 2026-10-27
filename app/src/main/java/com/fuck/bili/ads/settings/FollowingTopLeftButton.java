package com.fuck.bili.ads.settings;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Adds the following-feed filter settings button to ExhibitionFragment. */
public final class FollowingTopLeftButton {
    private static final String TAG = "FollowingTopLeft";
    private static final String BUTTON_TAG = "fredoseep.following.settings.v1";
    private static final Set<Class<?>> HOOKED =
            Collections.newSetFromMap(new IdentityHashMap<Class<?>, Boolean>());

    public static void hook(ClassLoader classLoader) {
        try {
            Class<?> fragment = Reflect.findClass(
                    "com.bilibili.bplus.following.home.ui.exhibition.ExhibitionFragment", classLoader);
            synchronized (HOOKED) {
                if (HOOKED.contains(fragment)) return;
                Method onCreateView = NativeHooks.method(fragment, "onCreateView",
                        LayoutInflater.class, ViewGroup.class, Bundle.class);
                NativeHooks.hook("FollowingTopLeftButton.onCreateView", onCreateView, chain -> {
                    Object result;
                    try {
                        result = chain.proceed();
                    } catch (Throwable t) {
                        Log.e(TAG, "Following fragment onCreateView failed", t);
                        throw t;
                    }
                    if (!(result instanceof View)) return result;
                    try {
                        addButton((View) result);
                    } catch (Throwable t) {
                        Log.e(TAG, "Failed to add settings button", t);
                    }
                    return result;
                });
                HOOKED.add(fragment);
            }
            Log.d(TAG, "ExhibitionFragment.onCreateView hook installed");
        } catch (Throwable t) {
            Log.e(TAG, "Hook installation failed", t);
        }
    }

    private static void addButton(View root) {
        if (root.findViewWithTag(BUTTON_TAG) != null) return;
        // Numeric fallbacks correspond to the supplied fbC.xml, not other builds.
        View toolbar = find(root, "nav_top_bar", 0x7f0921f0);
        View tabs = find(root, "toolbar_tabs", 0x7f094791);
        View title = find(root, "title", 0x7f09319e);
        View publish = find(root, "fo_publish_menu", 0x7f091787);
        if (!(toolbar instanceof ViewGroup)
                || !toolbar.getClass().getName().endsWith("Toolbar")
                || tabs == null || !tabs.getClass().getName().endsWith("DynamicTabStrip")
                || !(tabs.getParent() instanceof ViewGroup)) {
            Log.e(TAG, "Expected toolbar/tab structure not found; check resource IDs for this version");
            return;
        }
        ViewGroup container = (ViewGroup) tabs.getParent();
        if (!inside(container, toolbar) || publish == null || publish.getParent() != container) {
            Log.e(TAG, "Toolbar child hierarchy differs from supplied XML");
            return;
        }
        ViewGroup.LayoutParams existing = tabs.getLayoutParams();
        if (!existing.getClass().getName().contains("constraintlayout.widget.ConstraintLayout$LayoutParams")) {
            Log.e(TAG, "Expected ConstraintLayout.LayoutParams, got " + existing.getClass().getName());
            return;
        }

        Context context = root.getContext();
        int size = dp(context, 48);
        Object created = null;
        try {
            created = Reflect.newInstance(existing.getClass(), size, size);
        } catch (Throwable t) {
            Log.e(TAG, "Unable to create constraint layout params", t);
            return;
        }
        if (!(created instanceof ViewGroup.LayoutParams)) {
            Log.e(TAG, "Unexpected layout params type: " + created.getClass().getName());
            return;
        }
        ViewGroup.LayoutParams buttonParams = (ViewGroup.LayoutParams) created;
        try {
            Reflect.setIntField(buttonParams, "startToStart", 0);
            Reflect.setIntField(buttonParams, "topToTop", 0);
            Reflect.setIntField(buttonParams, "bottomToBottom", 0);
        } catch (Throwable t) {
            Log.d(TAG, "Constraint params fields unavailable: " + t);
        }

        TextView button = new TextView(context);
        button.setId(View.generateViewId());
        button.setTag(BUTTON_TAG);
        button.setText("设置");
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription("关注页设置");
        button.setClickable(true);
        button.setFocusable(true);
        if (title instanceof TextView) button.setTextColor(((TextView) title).getTextColors());
        TypedValue background = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, background, true)
                && background.resourceId != 0) {
            button.setBackgroundResource(background.resourceId);
        }
        if (Build.VERSION.SDK_INT >= 26) button.setTooltipText("关注页设置");
        button.setOnClickListener(v -> showSettings(v.getContext()));

        // Keep the centered tab strip/title clear of both edge buttons.
        reserveSpace(tabs, size);
        if (title != null && title.getParent() == container) reserveSpace(title, size);
        container.addView(button, buttonParams);
        publish.post(() -> {
            // A wider publish action may require additional symmetric space.
            int inset = Math.max(size, publish.getWidth());
            reserveSpace(tabs, inset);
            if (title != null && title.getParent() == container) reserveSpace(title, inset);
        });
        Log.d(TAG, "Settings button added to " + container.getClass().getName());
    }

    private static void reserveSpace(View view, int inset) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (!(raw instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) raw;
        params.setMarginStart(Math.max(params.getMarginStart(), inset));
        params.setMarginEnd(Math.max(params.getMarginEnd(), inset));
        // ConstraintLayout wrap_content must respect its two side constraints.
        try {
            Reflect.setBooleanField(params, "constrainedWidth", true);
        } catch (Throwable ignored) {
            Log.d(TAG, "constrainedWidth unavailable in host ConstraintLayout");
        }
        view.setLayoutParams(params);
    }

    private static boolean inside(View view, View ancestor) {
        View current = view;
        while (current != null) {
            if (current == ancestor) return true;
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return false;
    }

    private static View find(View root, String name, int fallbackId) {
        Context context = root.getContext();
        int id = context.getResources().getIdentifier(name, "id", context.getPackageName());
        View result = id == 0 ? null : root.findViewById(id);
        return result != null ? result : root.findViewById(fallbackId);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void showSettings(Context context) {
        FollowingFilterSettings.show(context);
    }
}
