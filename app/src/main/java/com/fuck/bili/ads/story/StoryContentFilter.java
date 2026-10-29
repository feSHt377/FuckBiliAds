package com.fuck.bili.ads.story;

/**
 * Stable, semantic story item type used by the user-configurable playlist filter.
 * Values are read from the host model's {@code goto} field, never obfuscated classes.
 */
public final class StoryContentFilter {

    public static final int AD = 1;
    public static final int LIVE = 1 << 1;
    public static final int PGC = 1 << 2;
    public static final int COURSE = 1 << 3;
    public static final int PREVIEW = 1 << 4;
    public static final int ALL = AD | LIVE | PGC | COURSE | PREVIEW;
    public static final int DEFAULT = AD;

    private StoryContentFilter() {
    }

    /** Returns the enabled filter bit, or zero when this item should be retained. */
    public static int match(Object item, int mask) {
        String type = gotoOf(item);
        if ("vertical_live".equals(type)) {
            return mask & LIVE;
        }
        if ("vertical_pgc".equals(type)) {
            return mask & PGC;
        }
        if ("vertical_course".equals(type)) {
            return mask & COURSE;
        }
        if ("vertical_preview_av".equals(type)) {
            return mask & PREVIEW;
        }
        return 0;
    }

    public static String gotoOf(Object item) {
        Object value = StoryView.fieldValue(item, "goto");
        return value instanceof String ? (String) value : "";
    }

    public static String label(int bit) {
        switch (bit) {
            case AD:
                return "广告";
            case LIVE:
                return "直播";
            case PGC:
                return "影视/番剧";
            case COURSE:
                return "课程";
            case PREVIEW:
                return "充电/预览视频";
            default:
                return "其他内容";
        }
    }
}
