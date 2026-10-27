package com.fuck.bili.ads;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BiliGsonLobotomyTest {

    @Test
    public void pureAdIdentifiersAreReplaced() {
        assertTrue(BiliGsonLobotomy.shouldReplaceIdentifier("cm"));
        assertTrue(BiliGsonLobotomy.shouldReplaceIdentifier("CM_V2"));
        assertTrue(BiliGsonLobotomy.shouldReplaceIdentifier("ad_av"));
    }

    @Test
    public void compositeViewTypesArePreserved() {
        // Real crash in 9.16.1: this value is used as RecyclerView viewType.
        assertFalse(BiliGsonLobotomy.shouldReplaceIdentifier("cm_v2:3"));
        assertFalse(BiliGsonLobotomy.shouldReplaceIdentifier("ad_av:0"));
        assertFalse(BiliGsonLobotomy.shouldReplaceIdentifier(null));
    }

    @Test
    public void ordinaryStringsAreNotTouched() {
        assertFalse(BiliGsonLobotomy.shouldReplaceIdentifier("av"));
        assertFalse(BiliGsonLobotomy.shouldReplaceIdentifier("advertisement"));
        assertFalse(BiliGsonLobotomy.shouldReplaceIdentifier("unknown"));
    }
}
