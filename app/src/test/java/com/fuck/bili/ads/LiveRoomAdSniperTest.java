package com.fuck.bili.ads;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 直播间 H5 拦截规则：结构资源与活动目录必须放行，只有广告特征才拦。
 */
public class LiveRoomAdSniperTest {

    @Test
    public void liveActivityModuleEntryIsNotBlocked() {
        assertFalse(LiveRoomAdSniper.shouldBlock(
                "https://s1.hdslb.com/bfs/live-activity/nuwa/sailing_fans_routine_template/remoteEntry.js"));
        assertFalse(LiveRoomAdSniper.shouldBlock(
                "https://s1.hdslb.com/bfs/live-activity/nuwa/stone_play_template/remoteEntry.js"));
        assertFalse(LiveRoomAdSniper.shouldBlock(
                "https://s1.hdslb.com/bfs/live-activity/nuwa/streamer_content_calendar/remoteEntry.js"));
    }

    @Test
    public void structuralResourcesAreNeverBlocked() {
        // 空白 JS/CSS/JSON 会让整页白屏，即使命中广告特征也必须放行。
        assertFalse(LiveRoomAdSniper.shouldBlock("https://x.hdslb.com/ad/bundle.js"));
        assertFalse(LiveRoomAdSniper.shouldBlock("https://x.hdslb.com/ad/style.css"));
        assertFalse(LiveRoomAdSniper.shouldBlock("https://x.hdslb.com/ad/config.json"));
        assertFalse(LiveRoomAdSniper.shouldBlock("https://x.hdslb.com/ad/module.mjs"));
    }

    @Test
    public void adResourcesAreStillBlocked() {
        assertTrue(LiveRoomAdSniper.shouldBlock(
                "https://x.hdslb.com/bfs/live/mortal_cultivator/banner.png"));
        assertTrue(LiveRoomAdSniper.shouldBlock("https://x.hdslb.com/ad/banner.png"));
        assertTrue(LiveRoomAdSniper.shouldBlock("https://x.hdslb.com/advert/splash.webp"));
        assertTrue(LiveRoomAdSniper.shouldBlock("https://x.hdslb.com/promotion/pic.jpg"));
        assertTrue(LiveRoomAdSniper.shouldBlock("https://x.hdslb.com/feed_ad/cover.png"));
    }

    @Test
    public void queryStringDoesNotChangeVerdict() {
        assertFalse(LiveRoomAdSniper.shouldBlock(
                "https://s1.hdslb.com/bfs/live-activity/nuwa/a/remoteEntry.js?v=2"));
        assertTrue(LiveRoomAdSniper.shouldBlock("https://x.hdslb.com/ad/banner.png?w=100"));
    }

    @Test
    public void unrelatedResourcesAreUnaffected() {
        assertFalse(LiveRoomAdSniper.shouldBlock("https://i0.hdslb.com/bfs/face/avatar.jpg"));
        assertFalse(LiveRoomAdSniper.shouldBlock(null));
        assertFalse(LiveRoomAdSniper.shouldBlock(""));
    }
}
