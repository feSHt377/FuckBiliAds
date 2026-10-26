package com.fuck.bili.ads.story;

import android.app.Activity;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import com.fuck.bili.ads.ui.FbaLog;

/**
 * One-line snapshot of the current story page, used by the manual mark action
 * and by the debug page of the settings panel.
 *
 * <p>The line is intentionally flat ({@code key=value}) so it can be grepped in
 * an exported log after a field test.
 */
public final class StoryDebug {

    private StoryDebug() {
    }

    /** Describes the page that is currently on screen. */
    public static String snapshot(Activity activity) {
        StringBuilder sb = new StringBuilder(256);
        if (activity == null) {
            return "activity=<none>";
        }
        sb.append("activity=").append(activity.getClass().getName());
        try {
            ViewGroup rv = StoryView.findRecycler(activity);
            if (rv == null) {
                return sb.append(" rv=<none>").toString();
            }
            Object adapter = StoryView.adapterOf(rv);
            sb.append(" rv=").append(rv.getClass().getName());
            sb.append(" adapter=").append(adapter == null ? "null" : adapter.getClass().getName());
            sb.append(" children=").append(rv.getChildCount());

            View child = StoryView.dominantChild(rv);
            int pos = StoryView.adapterPosition(rv, child);
            sb.append(" pos=").append(pos);
            if (child != null) {
                Rect rect = new Rect();
                if (child.getGlobalVisibleRect(rect)) {
                    sb.append(" visible=").append(rect.width()).append('x').append(rect.height());
                }
            }

            Object item = StoryView.itemOf(rv, child);
            if (item == null) {
                return sb.append(" item=<none>").toString();
            }
            sb.append(" item=").append(item.getClass().getName());
            sb.append(" aid=").append(StoryView.aidOf(item));
            sb.append(" cid=").append(StoryView.cidOf(item));
            Object adType = StoryView.fieldValue(item, "adType");
            sb.append(" adType=").append(adType);
            Object adInfo = StoryView.fieldValue(item, "adInfo");
            if (adInfo == null) {
                sb.append(" adInfo=null");
            } else {
                sb.append(" adInfo=").append(adInfo);
                sb.append(" feedIsAd=").append(StoryView.fieldValue(adInfo, "feedIsAd"));
                sb.append(" creativeId=").append(StoryView.fieldValue(adInfo, "feedCreativeId"));
            }
            return sb.toString();
        } catch (Throwable t) {
            return sb.append(" error=").append(t).toString();
        }
    }

    /** Shows a toast through the host's own ToastHelper (debug page action). */
    public static void testHostToast(Activity activity) {
        if (activity != null) {
            HostToast.show(activity, "FuckBiliAds Toast 测试");
        }
    }

    /** Writes a {@code [MARK]} line for the current page and returns the snapshot. */
    public static String mark(Activity activity, String source) {
        String snapshot = snapshot(activity);
        FbaLog.i("[MARK] source=" + source + " " + snapshot);
        return snapshot;
    }
}
