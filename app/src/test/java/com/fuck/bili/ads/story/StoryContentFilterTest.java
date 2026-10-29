package com.fuck.bili.ads.story;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StoryContentFilterTest {

    @Test
    public void selectsOnlyExplicitlyEnabledContentTypes() {
        assertEquals(StoryContentFilter.LIVE,
                StoryContentFilter.match(item("vertical_live"), StoryContentFilter.ALL));
        assertEquals(StoryContentFilter.PGC,
                StoryContentFilter.match(item("vertical_pgc"), StoryContentFilter.ALL));
        assertEquals(StoryContentFilter.COURSE,
                StoryContentFilter.match(item("vertical_course"), StoryContentFilter.ALL));
        assertEquals(StoryContentFilter.PREVIEW,
                StoryContentFilter.match(item("vertical_preview_av"), StoryContentFilter.ALL));
    }

    @Test
    public void disabledTypesAndCommercialLiveAreNotOrganicLive() {
        assertEquals(0, StoryContentFilter.match(item("vertical_live"), 0));
        assertEquals(0, StoryContentFilter.match(item("vertical_live"), StoryContentFilter.PGC));
        assertEquals(0, StoryContentFilter.match(item("vertical_ad_live"), StoryContentFilter.ALL));
        assertEquals(0, StoryContentFilter.match(item("vertical_av"), StoryContentFilter.ALL));
        assertEquals(0, StoryContentFilter.match(item(null), StoryContentFilter.ALL));
    }

    @Test
    public void missingModelOrFieldIsSafe() {
        assertEquals(0, StoryContentFilter.match(null, StoryContentFilter.ALL));
        assertEquals(0, StoryContentFilter.match(new Object(), StoryContentFilter.ALL));
    }

    private static Object item(String type) {
        return new Model(type);
    }

    @Test
    public void unreadableTypeDoesNotEnableContentRemoval() {
        assertFalse(StoryAdFilter.shouldRemove(new Object()));
        assertFalse(StoryAdFilter.shouldRemove(item("vertical_live")));
    }

    private static final class Model {
        private final String type;

        Model(String type) {
            this.type = type;
        }

        public String getGoto() {
            return type;
        }
    }
}
