package com.fuck.bili.ads.story;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 存量清理的节流规则：只在会话开头的时间窗内执行，且单次删除之间要有间隔。
 *
 * <p>背景：播放中频繁调用宿主 {@code remove(pos)} 会重排播放器，实测表现为
 * 每 7–10 秒一次 0.5 秒卡顿（Choreographer Skipped 30+ frames）。
 */
public class StoryPrunePolicyTest {

    private static final long WINDOW_MS = 20_000L;
    private static final long GAP_MS = 3_000L;

    @Test
    public void allowsPassRightAfterSessionStart() {
        assertTrue(StoryAdFilter.passAllowed(3_000L, 0L, 0L, WINDOW_MS, GAP_MS));
    }

    @Test
    public void blocksPassWhileGapNotElapsed() {
        assertFalse(StoryAdFilter.passAllowed(2_000L, 0L, 1_000L, WINDOW_MS, GAP_MS));
        assertTrue(StoryAdFilter.passAllowed(4_000L, 0L, 1_000L, WINDOW_MS, GAP_MS));
    }

    @Test
    public void blocksPassAfterWindowEvenWhenIdle() {
        assertFalse(StoryAdFilter.passAllowed(20_000L, 0L, 0L, WINDOW_MS, GAP_MS));
        assertFalse(StoryAdFilter.passAllowed(120_000L, 0L, 100_000L, WINDOW_MS, GAP_MS));
    }

    @Test
    public void cooldownGatesConsecutiveRemovals() {
        assertFalse(StoryAdFilter.cooldownElapsed(500L, 0L));
        assertTrue(StoryAdFilter.cooldownElapsed(1_200L, 0L));
    }
}
