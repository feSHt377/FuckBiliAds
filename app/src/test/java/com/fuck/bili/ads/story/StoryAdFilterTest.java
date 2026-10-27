package com.fuck.bili.ads.story;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StoryAdFilterTest {

    @Test
    public void offsetSuffixOnlyWhenEnabledAndKnown() {
        assertEquals("已剔除 1 条广告！FUCK BILI ADS！",
                StoryAdFilter.formatRemovalToast(1, 3, false));
        assertEquals("已剔除 1 条广告（后3条）！FUCK BILI ADS！",
                StoryAdFilter.formatRemovalToast(1, 3, true));
        // Unknown distance must not invent a number.
        assertEquals("已剔除 2 条广告！FUCK BILI ADS！",
                StoryAdFilter.formatRemovalToast(2, -1, true));
    }

    @Test
    public void offsetIsCompact() {
        String text = StoryAdFilter.formatRemovalToast(1, 8, true);
        assertEquals("已剔除 1 条广告（后8条）！FUCK BILI ADS！", text);
    }
}
