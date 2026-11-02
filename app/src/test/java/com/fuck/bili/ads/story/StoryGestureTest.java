package com.fuck.bili.ads.story;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StoryGestureTest {

    @Test
    public void waitsWhileTheListIsScrolling() {
        long now = 10_000L;
        assertTrue("dragging", StoryGesture.shouldWait(1, 0L, now));
        assertTrue("settling", StoryGesture.shouldWait(2, 0L, now));
    }

    @Test
    public void waitsUntilTheSettleWindowPasses() {
        long idleAt = 10_000L;
        assertTrue(StoryGesture.shouldWait(StoryGesture.IDLE, idleAt,
                idleAt + StoryGesture.SETTLE_MS - 1));
        assertFalse(StoryGesture.shouldWait(StoryGesture.IDLE, idleAt,
                idleAt + StoryGesture.SETTLE_MS));
    }

    @Test
    public void allowsActionsAfterLongIdle() {
        assertFalse(StoryGesture.shouldWait(StoryGesture.IDLE, 1_000L, 10_000L));
    }
}
