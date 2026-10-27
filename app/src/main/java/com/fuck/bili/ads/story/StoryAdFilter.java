package com.fuck.bili.ads.story;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;

import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;
import com.fuck.bili.ads.ui.FbaLog;
import com.fuck.bili.ads.ui.FbaPrefs;
import com.fuck.bili.ads.ui.FbaRuntime;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Removes ad cards from the story playlist before the host ever renders them.
 *
 * <p>The host refreshes its playlist in batches only: the first page, every
 * "load more", and the back-fill insert when the user swipes back up. All three
 * go through {@code StoryPagerPlayer}: {@code T0(List)} appends, {@code t1(List)}
 * prepends and the static {@code V1(player, List, ...)} replaces the whole list.
 * Filtering the {@link List} argument inside those methods means the host's own
 * bookkeeping (item count, current index, player slices, exposure reporting)
 * only ever sees real videos.
 *
 * <p>Cards already present when the hooks are installed (usually only the very
 * first batch) are pruned through the host's own removal API
 * {@code StoryPagerPlayer.remove(int)}, which has a dedicated branch for ad
 * cards and keeps the player and indices in sync. We never mutate the adapter
 * list directly.
 *
 * <p>Everything degrades gracefully: no class, no matching signature or an
 * unexpected exception simply leaves the list untouched and the polling sniper
 * in {@link StoryAdSniper} still skips the ad by scrolling.
 */
public final class StoryAdFilter {

    private static final String PLAYER_CLASS = "com.bilibili.video.story.player.StoryPagerPlayer";

    /** Never remove more than this many cards in a single scan pass. */
    private static final int MAX_PER_PASS = 4;

    /** Prefix of the removal toast; the count is filled in at runtime. */
    private static final String TOAST_REMOVED = "已剔除 %d 条广告！FUCK BILI ADS！";
    private static final String TOAST_REMOVED_OFFSET =
            "已剔除 %d 条广告（后%d条）！FUCK BILI ADS！";

    private static final Set<String> HOOKED = new HashSet<>();

    private static volatile boolean entryHooked;
    private static volatile long filteredTotal;
    private static volatile long prunedTotal;
    private static volatile String lastError;

    private static Object cachedPlayer;
    private static Activity cachedActivity;

    private StoryAdFilter() {
    }

    // ------------------------------------------------------------------ entry

    /**
     * Installs the batch-entry hooks once. Safe to call repeatedly (every page
     * session calls it); a second call is a no-op.
     */
    public static boolean install(ClassLoader loader) {
        if (loader == null || entryHooked) {
            return entryHooked;
        }
        synchronized (HOOKED) {
            if (entryHooked) {
                return true;
            }
            Class<?> player = Reflect.findClassIfExists(PLAYER_CLASS, loader);
            if (player == null) {
                lastError = "class missing";
                FbaLog.w("story filter: " + PLAYER_CLASS + " missing");
                return false;
            }
            int installed = 0;
            for (Method method : player.getDeclaredMethods()) {
                try {
                    if (method.getReturnType() != void.class) {
                        continue;
                    }
                    Class<?>[] types = method.getParameterTypes();
                    boolean isStatic = Modifier.isStatic(method.getModifiers());
                    if (!isStatic && types.length == 1 && List.class.isAssignableFrom(types[0])) {
                        installed += hook(method, 0) ? 1 : 0;
                    } else if (isStatic && types.length >= 2 && types[0] == player
                            && List.class.isAssignableFrom(types[1])) {
                        installed += hook(method, 1) ? 1 : 0;
                    }
                } catch (Throwable t) {
                    FbaLog.w("story filter: skip " + method.getName() + " (" + t + ")");
                }
            }
            entryHooked = installed > 0;
            if (!entryHooked) {
                lastError = "no batch method";
            }
            FbaLog.i("story filter: entry hooks=" + installed + " class=" + player.getName());
            return entryHooked;
        }
    }

    private static boolean hook(Method method, final int index) {
        String key = method.getDeclaringClass().getName() + "#" + method.getName()
                + "#" + method.getParameterCount();
        if (!HOOKED.add(key)) {
            return false;
        }
        final boolean prepend = "t1".equals(method.getName());
        final boolean replace = Modifier.isStatic(method.getModifiers());
        try {
            NativeHooks.hook("StoryAdFilter." + method.getName(), method, chain -> {
                try {
                    if (!FbaPrefs.storyFilter()) {
                        return chain.proceed();
                    }
                    Object raw = chain.getArg(index);
                    if (!(raw instanceof List)) {
                        return chain.proceed();
                    }

                    // 只有开启“附带位置”时才读取当前可见位置；否则不额外遍历视图。
                    Activity activity = null;
                    int currentPos = -1;
                    int baseIndex = -1;
                    if (FbaPrefs.storyFilterOffset()) {
                        activity = StoryAdSniper.currentActivity();
                        if (activity == null) {
                            activity = FbaRuntime.topActivity();
                        }
                        if (activity != null && !prepend) {
                            ViewGroup rv = StoryView.findRecycler(activity);
                            if (rv != null) {
                                currentPos = StoryView.dominantPos(rv);
                                int oldCount = StoryView.itemCount(StoryView.adapterOf(rv));
                                // T0 追加在当前列表之后；V1 是整表替换。
                                baseIndex = replace ? 0 : oldCount;
                            }
                        }
                    }

                    FilterOutcome outcome = withoutAds((List<?>) raw, activity,
                            currentPos, baseIndex);
                    if (outcome == null) {
                        return chain.proceed();
                    }
                    Object[] args = chain.getArgs().toArray();
                    args[index] = outcome.kept;
                    return chain.proceed(args);
                } catch (Throwable t) {
                    lastError = String.valueOf(t);
                    FbaLog.w("story filter: entry hook failed (" + t + ")");
                    return chain.proceed();
                }
            });
            return true;
        } catch (Throwable t) {
            HOOKED.remove(key);
            FbaLog.w("story filter: hook " + method.getName() + " failed (" + t + ")");
            return false;
        }
    }

    private static final class FilterOutcome {
        final List<Object> kept;
        FilterOutcome(List<Object> kept) {
            this.kept = kept;
        }
    }

    /**
     * Copy of {@code src} without ad cards, or {@code null} when nothing has to
     * change. A batch that would end up empty is kept as-is: the host stops
     * asking for more once the list runs dry, so an empty result would strand
     * the pager on the last card.
     *
     * @param baseIndex  ad index 0 corresponds to this position in the current
     *                   list; -1 means the relative position is unknown/above.
     * @param currentPos currently visible adapter position; -1 when unknown.
     */
    private static FilterOutcome withoutAds(List<?> src, Activity activity,
                                             int currentPos, int baseIndex) {
        if (src.size() < 2) {
            return null;
        }
        List<Object> kept = null;
        int dropped = 0;
        int nearestBehind = -1;
        for (int i = 0; i < src.size(); i++) {
            Object item = src.get(i);
            if (isAd(item)) {
                if (kept == null) {
                    kept = new ArrayList<>(src.subList(0, i));
                }
                dropped++;
                if (baseIndex >= 0 && currentPos >= 0) {
                    int behind = baseIndex + i - currentPos;
                    if (behind > 0 && (nearestBehind < 0 || behind < nearestBehind)) {
                        nearestBehind = behind;
                    }
                }
                continue;
            }
            if (kept != null) {
                kept.add(item);
            }
        }
        if (kept == null || kept.isEmpty()) {
            return null;
        }
        filteredTotal += dropped;
        notifyRemoved(activity, dropped, "批次 " + src.size() + "→" + kept.size(),
                nearestBehind);
        return new FilterOutcome(kept);
    }

    /**
     * Logs one removal event and — when the user kept the toast enabled — tells
     * them how many cards were just dropped. Runs on the main thread because the
     * host toast helper touches views.
     *
     * @param activity page activity when known; otherwise the foreground one is used
     * @param count    cards removed by this event
     * @param source   short, human readable origin, used for the log only
     * @param behind   distance from the current video; -1 when unknown
     */
    private static void notifyRemoved(Activity activity, int count, String source, int behind) {
        if (count <= 0) {
            return;
        }
        boolean showBehind = FbaPrefs.storyFilterOffset() && behind > 0;
        long total = filteredTotal + prunedTotal;
        FbaLog.i("[FILTER] 剔除 " + count + " 条广告（" + source
                + (showBehind ? "，后" + behind + "条" : "") + "），累计 " + total);
        if (!FbaPrefs.storyFilterToast()) {
            return;
        }
        Context context = activity != null ? activity : FbaRuntime.topActivity();
        if (context == null) {
            context = FbaRuntime.context();
        }
        if (context == null) {
            return;
        }
        final Context target = context;
        final String text = formatRemovalToast(count, behind, showBehind);
        FbaRuntime.onMain(new Runnable() {
            @Override
            public void run() {
                HostToast.show(target, text);
                FbaLog.i("[TOAST] " + text);
            }
        });
    }

    /** Kept package-visible for the unit test. */
    static String formatRemovalToast(int count, int behind, boolean showBehind) {
        if (showBehind && behind > 0) {
            return String.format(java.util.Locale.US, TOAST_REMOVED_OFFSET, count, behind);
        }
        return String.format(java.util.Locale.US, TOAST_REMOVED, count);
    }

    /**
     * Debug entry used by the settings page: fires the removal toast without
     * touching the playlist, so the wording and the switch can be checked
     * without waiting for a real ad.
     */
    public static void debugToast(Activity activity, int count) {
        debugToast(activity, count, -1);
    }

    public static void debugToast(Activity activity, int count, int behind) {
        notifyRemoved(activity, count, "调试", behind);
    }

    // ------------------------------------------------------------------ prune
    /**
     * Removes ad cards that are already in the list ahead of {@code currentPos}
     * (the first batch usually arrives before our hooks are in place).
     *
     * @return number of cards removed in this pass
     */
    public static int pruneAhead(Activity activity, ViewGroup recycler, int currentPos) {
        if (activity == null || recycler == null || currentPos < 0) {
            return 0;
        }
        if (!FbaPrefs.storyFilter()) {
            return 0;
        }
        try {
            Object player = player(activity);
            if (player == null) {
                return 0;
            }
            Object adapter = StoryView.adapterOf(recycler);
            int count = StoryView.itemCount(adapter);
            int removed = 0;
            int nearestBehind = -1;
            for (int pos = count - 1; pos > currentPos && removed < MAX_PER_PASS; pos--) {
                Object item = StoryView.findItem(adapter, pos);
                if (!isAd(item)) {
                    continue;
                }
                if (Boolean.TRUE.equals(callRemove(player, pos))) {
                    removed++;
                    if (FbaPrefs.storyFilterOffset()) {
                        int behind = pos - currentPos;
                        if (behind > 0 && (nearestBehind < 0 || behind < nearestBehind)) {
                            nearestBehind = behind;
                        }
                    }
                }
            }
            if (removed > 0) {
                prunedTotal += removed;
                notifyRemoved(activity, removed, "存量 pos>" + currentPos, nearestBehind);
            }
            return removed;
        } catch (Throwable t) {
            lastError = String.valueOf(t);
            FbaLog.w("story filter: prune failed (" + t + ")");
            return 0;
        }
    }

    private static Boolean callRemove(Object player, int pos) {
        try {
            Object result = Reflect.tryCallMethod(player, "remove", pos);
            return result instanceof Boolean ? (Boolean) result : Boolean.FALSE;
        } catch (Throwable t) {
            lastError = String.valueOf(t);
            return Boolean.FALSE;
        }
    }

    /** The module page holds the player on the fragment; walk fields to find it. */
    private static Object player(Activity activity) {
        if (cachedPlayer != null && cachedActivity == activity) {
            return cachedPlayer;
        }
        Object found = search(activity, 3);
        if (found != null) {
            cachedPlayer = found;
            cachedActivity = activity;
        }
        return found;
    }

    private static Object search(Object target, int depth) {
        if (target == null || depth < 0) {
            return null;
        }
        if (PLAYER_CLASS.equals(target.getClass().getName())) {
            return target;
        }
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            Field[] fields;
            try {
                fields = type.getDeclaredFields();
            } catch (Throwable t) {
                continue;
            }
            for (Field field : fields) {
                Class<?> fieldType = field.getType();
                if (fieldType.isPrimitive() || fieldType.isArray()) {
                    continue;
                }
                String name = fieldType.getName();
                if (!PLAYER_CLASS.equals(name) && !name.startsWith("com.bilibili.video.story.")) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    Object found = search(value, depth - 1);
                    if (found != null) {
                        return found;
                    }
                } catch (Throwable ignore) {
                    // inaccessible field: keep looking
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------- stats

    static boolean isAd(Object item) {
        if (item == null) {
            return false;
        }
        Object ad = StoryView.fieldValue(item, "adInfo");
        return ad != null && Boolean.TRUE.equals(StoryView.fieldValue(ad, "feedIsAd"));
    }

    public static boolean isEntryHooked() {
        return entryHooked;
    }

    public static String stats() {
        String state = entryHooked ? "入口过滤已启用" : "入口过滤不可用";
        if (lastError != null) {
            state = state + " · 最近错误：" + lastError;
        }
        return state + " · 本会话剔除 " + filteredTotal + " 条（批次）+ "
                + prunedTotal + " 条（存量）";
    }

    static void forget(Activity activity) {
        if (cachedActivity == activity) {
            cachedActivity = null;
            cachedPlayer = null;
        }
    }
}
