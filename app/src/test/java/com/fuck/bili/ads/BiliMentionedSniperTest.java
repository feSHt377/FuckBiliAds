package com.fuck.bili.ads;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BiliMentionedSniperTest {

    @Test
    public void hooksOnlyComponentCreationAndBinding() {
        assertTrue(BiliMentionedSniper.shouldHook("createViewEntry"));
        assertTrue(BiliMentionedSniper.shouldHook("bindToView"));
    }

    @Test
    public void leavesLifecycleAndCommentMethodsUnhooked() {
        assertFalse(BiliMentionedSniper.shouldHook("onBindViewHolder"));
        assertFalse(BiliMentionedSniper.shouldHook("onResume"));
        assertFalse(BiliMentionedSniper.shouldHook("onPause"));
        assertFalse(BiliMentionedSniper.shouldHook("onDestroy"));
        assertFalse(BiliMentionedSniper.shouldHook("bind"));
        assertFalse(BiliMentionedSniper.shouldHook(null));
    }
}
