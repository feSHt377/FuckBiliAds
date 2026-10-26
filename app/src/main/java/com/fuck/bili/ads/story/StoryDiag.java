package com.fuck.bili.ads.story;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Last observed story-page state, shown by the settings panel and the logs. */
public final class StoryDiag {

    private static volatile String line = "";
    private static volatile long at;

    private StoryDiag() {
    }

    public static void update(String text) {
        line = text;
        at = System.currentTimeMillis();
    }

    public static boolean hasData() {
        return at > 0;
    }

    public static String describe() {
        if (at == 0) {
            return "尚未扫描（进入小视频页后自动记录）";
        }
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(at));
        return time + " · " + line;
    }
}
