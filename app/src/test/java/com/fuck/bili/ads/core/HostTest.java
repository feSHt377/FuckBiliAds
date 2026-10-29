package com.fuck.bili.ads.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HostTest {

    @Test
    public void distinguishesHdFromPhoneHosts() {
        assertTrue(Host.isHdPackage("tv.danmaku.bilibilihd"));
        assertFalse(Host.isHdPackage("tv.danmaku.bili"));
        assertFalse(Host.isHdPackage("tv.danmaku.bilj"));
        assertFalse(Host.isHdPackage("com.bilibili.app.in"));
        assertFalse(Host.isHdPackage(null));
    }
}
