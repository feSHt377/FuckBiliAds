package com.fuck.bili.ads;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BiliSplashSniperTest {

    @Test
    public void matchesCommercialPageFactoryByStructure() throws Exception {
        assertTrue(BiliSplashSniper.isCommercialPageFactory(
                Fixtures.class.getDeclaredMethod("create", Fixtures.SplashOrder.class)));
    }

    @Test
    public void rejectsOtherFactories() throws Exception {
        assertFalse(BiliSplashSniper.isCommercialPageFactory(
                Fixtures.class.getDeclaredMethod("wrongReturn", Fixtures.SplashOrder.class)));
        assertFalse(BiliSplashSniper.isCommercialPageFactory(
                Fixtures.class.getDeclaredMethod("wrongParameters", String.class)));
        assertFalse(BiliSplashSniper.isCommercialPageFactory(null));
    }

    @Test
    public void blocksEveryColdStartSplashEndpoint() {
        for (String path : new String[]{"/x/v2/splash/list", "/x/v2/splash/show",
                "/x/v2/splash/brand/list"}) {
            assertTrue(path, BiliSplashSniper.isTarget("app.bilibili.com", path, "GET"));
        }
    }

    @Test
    public void ignoresSplashCallsThatAreNotColdStartAds() {
        assertFalse(BiliSplashSniper.isTarget("app.bilibili.com", "/x/v2/splash/brand/set", "GET"));
        assertFalse(BiliSplashSniper.isTarget("app.bilibili.com", "/x/v2/splash/brand/save", "POST"));
        assertFalse(BiliSplashSniper.isTarget("api.bilibili.com", "/x/v2/splash/list", "GET"));
        // 运营活动开屏（拜年纪等）不是广告，保持原样。
        assertFalse(BiliSplashSniper.isTarget("app.bilibili.com", "/x/v2/splash/event/list2", "GET"));
    }

    static final class Fixtures {
        public static BaseSplash create(SplashOrder order) {
            return null;
        }

        public static String wrongReturn(SplashOrder order) {
            return null;
        }

        public static BaseSplash wrongParameters(String value) {
            return null;
        }

        static final class SplashOrder { }
        static final class BaseSplash { }
    }
}
