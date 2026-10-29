package com.fuck.bili.ads.story;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StoryAdFilterTest {

    @Test
    public void offsetSuffixOnlyWhenEnabledAndKnown() {
        assertEquals("已剔除 1 个内容！FUCK BILI ADS！",
                StoryAdFilter.formatRemovalToast(1, 3, false));
        assertEquals("已剔除 1 个内容（后3条）！FUCK BILI ADS！",
                StoryAdFilter.formatRemovalToast(1, 3, true));
        // Unknown distance must not invent a number.
        assertEquals("已剔除 2 个内容！FUCK BILI ADS！",
                StoryAdFilter.formatRemovalToast(2, -1, true));
    }

    @Test
    public void offsetIsCompact() {
        String text = StoryAdFilter.formatRemovalToast(1, 8, true);
        assertEquals("已剔除 1 个内容（后8条）！FUCK BILI ADS！", text);
    }

    @Test
    public void breakdownUsesContentTypesAndCounts() {
        assertEquals("广告 *1，直播 *2，影视/番剧 *1",
                StoryAdFilter.breakdown(new int[]{1, 2, 1, 0, 0}));
        assertEquals("课程 *1，充电/预览视频 *2",
                StoryAdFilter.breakdown(new int[]{0, 0, 0, 1, 2}));
        assertEquals("", StoryAdFilter.breakdown(new int[5]));
    }
}
