package com.fuck.bili.ads.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.bilibili.lib.homepage.mine.MenuGroup;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Guards the structural lookups used on the HD host.
 *
 * <p>The stand-in models copy the obfuscated shape of the shipped classes: the
 * card wrapper exposes a single-letter list field plus a single-letter title
 * field, and the adapter reaches its cards through a no-arg method. Nothing in
 * the production code may depend on the readable names a decompiler restores.
 */
public class HdMineServiceEntryTest {

    private static final String HOST_SETTINGS = "bilibili://main/preference";
    private static final String MORE_SERVICES = "更多服务";

    /** Mirrors the obfuscated card wrapper: {@code a} items, {@code b} title. */
    static final class Card {
        final List<MenuGroup.Item> a = new ArrayList<>();
        String b;
    }

    /** Mirrors the obfuscated model: no-arg {@code a()} returning the cards. */
    static final class Model {
        final List<Object> a = new ArrayList<>();

        List<Object> a() {
            return a;
        }
    }

    /** Mirrors the grouped adapter, whose {@code data} field holds the model. */
    static final class GroupedAdapter {
        final Model data = new Model();
    }

    /** Mirrors the flat adapter, which renders the item list directly. */
    static final class FlatAdapter {
        final List<MenuGroup.Item> data = new ArrayList<>();
    }

    /** An adapter that has nothing to do with the Mine menu. */
    static final class UnrelatedAdapter {
        final List<String> rows = new ArrayList<>();
    }

    @Test
    public void readsCardsBehindTheWrapperMethod() {
        GroupedAdapter adapter = new GroupedAdapter();
        Card card = card(MORE_SERVICES, item(HOST_SETTINGS, "设置"));
        adapter.data.a.add(card);

        List<Object> groups = HdMineServiceEntry.groupList(adapter);

        assertNotNull(groups);
        assertEquals(1, groups.size());
        assertSame(card, groups.get(0));
    }

    @Test
    public void readsItemsAndTitleFromSingleLetterFields() {
        Card card = card(MORE_SERVICES, item("bilibili://main/history", "历史"));

        List<Object> items = HdMineServiceEntry.itemsOf(card);

        assertNotNull(items);
        assertEquals(1, items.size());
        assertEquals("bilibili://main/history", ((MenuGroup.Item) items.get(0)).uri);
        assertEquals(MORE_SERVICES, HdMineServiceEntry.titleOf(card));
    }

    @Test
    public void cardListIsRejectedWhenElementsCarryNoItems() {
        GroupedAdapter adapter = new GroupedAdapter();
        adapter.data.a.add("not a card");

        assertNull(HdMineServiceEntry.groupList(adapter));
    }

    @Test
    public void picksTheCardThatHostsTheHostSettingsRow() {
        List<Object> groups = new ArrayList<>();
        groups.add(card(MORE_SERVICES, item("bilibili://main/history", "历史")));
        groups.add(card("推荐服务", item(HOST_SETTINGS, "设置")));

        assertEquals(1, HdMineServiceEntry.pickGroup(groups));
    }

    @Test
    public void fallsBackToTheMoreServicesCard() {
        List<Object> groups = new ArrayList<>();
        groups.add(card("推荐服务", item("bilibili://main/history", "历史")));
        groups.add(card(MORE_SERVICES, item("bilibili://main/favorite", "收藏")));

        assertEquals(1, HdMineServiceEntry.pickGroup(groups));
    }

    @Test
    public void fallsBackToTheLastPopulatedCard() {
        List<Object> groups = new ArrayList<>();
        groups.add(card("推荐服务", item("bilibili://main/history", "历史")));
        groups.add(card("", item("bilibili://main/favorite", "收藏")));

        assertEquals(1, HdMineServiceEntry.pickGroup(groups));
    }

    @Test
    public void findsTheFlatItemListAndIgnoresUnrelatedAdapters() {
        FlatAdapter adapter = new FlatAdapter();
        adapter.data.add(item("bilibili://main/history", "历史"));

        List<Object> flat = HdMineServiceEntry.flatItems(adapter);

        assertNotNull(flat);
        assertEquals(1, flat.size());

        UnrelatedAdapter other = new UnrelatedAdapter();
        other.rows.add("row");
        assertNull(HdMineServiceEntry.groupList(other));
        assertNull(HdMineServiceEntry.flatItems(other));
    }

    private static Card card(String title, MenuGroup.Item item) {
        Card card = new Card();
        card.b = title;
        card.a.add(item);
        return card;
    }

    private static MenuGroup.Item item(String uri, String title) {
        MenuGroup.Item item = new MenuGroup.Item();
        item.uri = uri;
        item.title = title;
        return item;
    }
}
