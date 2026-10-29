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
