package com.fuck.bili.ads;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Method;

public class PlayPageAdSniperTest {

    @Test
    public void recognizesAllUnderPlayerVariants() {
        assertTrue(PlayPageAdSniper.isUnderPlayerFactory("getUpperHDView"));
        assertTrue(PlayPageAdSniper.isUnderPlayerFactory("getUpperNestView"));
        assertTrue(PlayPageAdSniper.isUnderPlayerFactory("getUpperAdView"));
        assertFalse(PlayPageAdSniper.isUnderPlayerFactory("getAdRelateView"));
    }

    @Test
    public void relateFactoryUsesExactPublicDescriptor() throws Exception {
        Method expected = Stub.class.getDeclaredMethod("expected",
                android.view.ViewGroup.class, int.class, boolean.class);
        Method wrongName = Stub.class.getDeclaredMethod("wrongName",
                android.view.ViewGroup.class, int.class, boolean.class);
        Method wrongReturn = Stub.class.getDeclaredMethod("wrongReturn",
                android.view.ViewGroup.class, int.class, boolean.class);
        Method wrongParameter = Stub.class.getDeclaredMethod("wrongParameter",
                android.view.ViewGroup.class, long.class, boolean.class);

        assertTrue(PlayPageAdSniper.hasSignature(expected, Stub.class.getName(),
                "android.view.ViewGroup", "int", "boolean"));
        assertFalse(PlayPageAdSniper.hasSignature(wrongName, Stub.class.getName(),
                "android.view.ViewGroup", "int", "boolean")
                && PlayPageAdSniper.isRelateFactory(wrongName.getName()));
        assertFalse(PlayPageAdSniper.hasSignature(wrongReturn, Stub.class.getName(),
                "android.view.ViewGroup", "int", "boolean"));
        assertFalse(PlayPageAdSniper.hasSignature(wrongParameter, Stub.class.getName(),
                "android.view.ViewGroup", "int", "boolean"));
        assertTrue(PlayPageAdSniper.isRelateFactory("getAdRelateView"));
    }

    @SuppressWarnings("unused")
    private static final class Stub {
        private Stub expected(android.view.ViewGroup parent, int viewType, boolean fresh) {
            return this;
        }

        private Stub wrongName(android.view.ViewGroup parent, int viewType, boolean fresh) {
            return this;
        }

        private String wrongReturn(android.view.ViewGroup parent, int viewType, boolean fresh) {
            return "";
        }

        private Stub wrongParameter(android.view.ViewGroup parent, long viewType, boolean fresh) {
            return this;
        }
    }
}
