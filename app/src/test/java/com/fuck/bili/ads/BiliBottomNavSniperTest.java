package com.fuck.bili.ads;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BiliBottomNavSniperTest {

    @Test
    public void mallTabHonoursItsSwitch() {
        assertTrue(BiliBottomNavSniper.matches("bilibili://mall/home", true, false));
        assertFalse(BiliBottomNavSniper.matches("bilibili://mall/home", false, false));
    }

    @Test
    public void publishTabHonoursItsSwitch() {
        assertTrue(BiliBottomNavSniper.matches("bilibili://uper/center_plus", false, true));
        assertFalse(BiliBottomNavSniper.matches("bilibili://uper/center_plus", false, false));
        assertFalse(BiliBottomNavSniper.matches("bilibili://uper/center_plus", true, false));
    }

    @Test
    public void chineseLabelsActAsFallback() {
        assertTrue(BiliBottomNavSniper.matches("会员购", true, false));
        assertTrue(BiliBottomNavSniper.matches("发布", false, true));
        assertFalse(BiliBottomNavSniper.matches("发布", true, false));
    }

    @Test
    public void unrelatedTabsAreKept() {
        assertFalse(BiliBottomNavSniper.matches("bilibili://home", true, true));
        assertFalse(BiliBottomNavSniper.matches("我的", true, true));
        assertFalse(BiliBottomNavSniper.matches(null, true, true));
    }
}
