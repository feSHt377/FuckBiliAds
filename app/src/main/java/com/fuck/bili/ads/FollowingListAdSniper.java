package com.fuck.bili.ads;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.fuck.bili.ads.core.Host;
import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;
import com.fuck.bili.ads.settings.FollowingFilterConfig;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Removes matching feed items by building a copy of the protobuf response. */
public final class FollowingListAdSniper {
    public static final String TAG = "FollowingList";
    private static final String PREFIX = "com.bapis.bilibili.app.dynamic.v2.";
    private static final Set<Class<?>> INSTALLED =
            Collections.newSetFromMap(new IdentityHashMap<Class<?>, Boolean>());
    private static final ThreadLocal<Boolean> FILTERING = new ThreadLocal<>();
    private static WeakReference<Object> cachedInput = new WeakReference<>(null);
    private static WeakReference<Object> cachedOutput = new WeakReference<>(null);
    private static FollowingFilterConfig.Snapshot cachedConfig;
    private static boolean applicationHooked;
    private static FollowingFilterConfig.Snapshot loggedConfig;
    private static final boolean DEBUG_FORWARD_MATCH = true;

    public static void hook(ClassLoader classLoader) {
        initContext();
        try {
            Class<?> reply = Reflect.findClass(PREFIX + "DynAllReply", classLoader);
            synchronized (INSTALLED) {
                if (!INSTALLED.add(reply)) return;
            }
            // Capture direct Parser consumers as well as static parseFrom callers.
            try {
                Method getDynamicList = NativeHooks.method(reply, "getDynamicList");
                NativeHooks.hook("FollowingListAdSniper.getDynamicList", getDynamicList, chain -> {
                    Object result;
                    try {
                        result = chain.proceed();
                    } catch (Throwable t) {
                        Log.e(TAG, "[FILTER_ERROR] getDynamicList original call failed", t);
                        throw t;
                    }
                    if (result == null) return result;
                    try {
                        return filterList(result);
                    } catch (Throwable t) {
                        Log.e(TAG, "[FILTER_ERROR] getDynamicList; keeping original result", t);
                        return result;
                    }
                });
                Log.d(TAG, "[INSTALL] getDynamicList filter installed");
            } catch (Throwable t) {
                Log.e(TAG, "[INSTALL] getDynamicList hook failed", t);
            }
            int count = 0;
            for (Method method : reply.getDeclaredMethods()) {
                if (!method.getName().equals("parseFrom") || !Modifier.isStatic(method.getModifiers())
                        || method.getReturnType() != reply) continue;
                NativeHooks.hook("FollowingListAdSniper.parseFrom", method, chain -> {
                    Object result;
                    try {
                        result = chain.proceed();
                    } catch (Throwable t) {
                        Log.e(TAG, "[FILTER_ERROR] parseFrom original call failed", t);
                        throw t;
                    }
                    if (result == null) return result;
                    try {
                        Object originalReply = result;
                        Object originalList = Reflect.getObjectField(originalReply, "dynamicList_");
                        Object filtered = filterList(originalList);
                        if (filtered == originalList) return result;
                        Object builder = Reflect.callMethod(originalReply, "toBuilder");
                        Reflect.callMethod(builder, "setDynamicList", filtered);
                        return Reflect.callMethod(builder, "build");
                    } catch (Throwable t) {
                        Log.e(TAG, "[FILTER_ERROR] parseFrom; keeping original reply", t);
                        return result;
                    }
                });
                count++;
            }
            Log.d(TAG, "[INSTALL] parseFrom filter overloads=" + count);
        } catch (Throwable t) {
            Log.e(TAG, "[INSTALL] following filter failed", t);
        }
    }

    private static synchronized void initContext() {
        Application application = Host.currentApplication();
        if (application != null) FollowingFilterConfig.init(application);
        if (applicationHooked) return;
        try {
            Method attach = NativeHooks.method(Application.class, "attach", Context.class);
            NativeHooks.hook("FollowingListAdSniper.ApplicationAttach", attach, chain -> {
                Object result = chain.proceed();
                FollowingFilterConfig.init((Context) chain.getThisObject());
                return result;
            });
            applicationHooked = true;
        } catch (Throwable t) {
            Log.e(TAG, "Application context hook failed", t);
        }
    }

    private static Object filterList(Object original) {
        if (original == null || Boolean.TRUE.equals(FILTERING.get())) return original;
        FILTERING.set(true);
        try {
            FollowingFilterConfig.Snapshot config = FollowingFilterConfig.load();
            logConfig(config);
            if (!config.blockUpRecommend && !config.blockForwardLottery && config.rules.isEmpty()) return original;
            synchronized (FollowingListAdSniper.class) {
                Object output = cachedOutput.get();
                if (cachedInput.get() == original && cachedConfig == config && output != null) return output;
            }
            List<?> items = (List<?>) Reflect.tryGetObjectField(original, "list_");
            List<Object> kept = new ArrayList<>(items.size());
            int removed = 0;
            for (int i = 0; i < items.size(); i++) {
                Object item = items.get(i);
                String reason = null;
                try {
                    Content content = new Content(config);
                    Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
                    collectDynamic(item, "item", content, visited, 0);
                    if (DEBUG_FORWARD_MATCH && content.forwarded) logForwardMatch(i, content);
                    if (config.blockUpRecommend && content.recommended) reason = "UP主的推荐";
                    if (reason == null && config.blockForwardLottery && content.forwarded
                            && content.originalText.indexOf("抽奖") >= 0) {
                        reason = "被转发的抽奖（原文含抽奖）";
                    }
                    if (reason == null && !config.rules.isEmpty()) {
                        String text = content.text.toString();
                        for (int r = 0; r < config.rules.size(); r++) {
                            if (config.rules.get(r).matches(text)) {
                                reason = "规则#" + (r + 1) + (config.rules.get(r).regex ? "(正则)" : "(关键词)");
                                break;
                            }
                        }
                    }
                    if (DEBUG_FORWARD_MATCH && content.forwarded) {
                        Log.d(TAG, "[MATCH_RESULT] index=" + i + " result="
                                + (reason == null ? "KEEP (no rule matched)" : "DROP " + reason));
                    }
                } catch (Throwable t) {
                    // A malformed/unknown item should not prevent other items from being processed.
                    Log.e(TAG, "[ITEM_ERROR] index=" + i + "; keeping item", t);
                }
                if (reason == null) kept.add(item);
                else {
                    removed++;
                    Log.d(TAG, "[FILTER_DROP] index=" + i + " reason=" + reason);
                }
            }
            Object result = original;
            if (removed > 0) {
                Object builder = Reflect.tryCallMethod(original, "toBuilder");
                Reflect.tryCallMethod(builder, "clearList");
                Reflect.tryCallMethod(builder, "addAllList", kept);
                result = Reflect.tryCallMethod(builder, "build");
                Log.d(TAG, "[FILTER] before=" + items.size() + " removed=" + removed + " after=" + kept.size());
            }
            synchronized (FollowingListAdSniper.class) {
                cachedInput = new WeakReference<>(original);
                cachedOutput = new WeakReference<>(result);
                cachedConfig = config;
            }
            return result;
        } finally {
            FILTERING.remove();
        }
    }

    private static final class Content {
        final FollowingFilterConfig.Snapshot config;
        final StringBuilder text = new StringBuilder();
        final StringBuilder originalText = new StringBuilder();
        final List<String> sources = new ArrayList<>();
        boolean recommended;
        boolean forwarded;
        Content(FollowingFilterConfig.Snapshot config) { this.config = config; }
        void append(String path, Object value, boolean fromOriginal) {
            if (value instanceof String && !((String) value).isEmpty()) {
                if (text.length() > 0) text.append('\n');
                text.append(value);
                sources.add(path + " = " + value);
                if (fromOriginal) {
                    if (originalText.length() > 0) originalText.append('\n');
                    originalText.append(value);
                }
            }
        }
    }

    /** Follow message boundaries rather than traversing rendering/style metadata. */
    private static void collectDynamic(Object item, String path, Content out,
                                       Set<Object> visited, int forwardDepth) {
        if (item == null || !visited.add(item)) return;
        if (forwardDepth > 16) throw new IllegalStateException("Forward nesting exceeds 16");
        Object raw = Reflect.tryGetObjectField(item, "modules_");
        if (!(raw instanceof List<?>)) return;
        List<?> modules = (List<?>) raw;
        boolean needsText = !out.config.rules.isEmpty()
                || (out.config.blockForwardLottery && forwardDepth > 0);
        for (int i = 0; i < modules.size(); i++) {
            Object module = modules.get(i);
            String modulePath = path + ".modules_[" + i + "]";
            int selected = (Integer) Reflect.tryGetObjectField(module, "moduleItemCase_");
            Object payload = Reflect.tryGetObjectField(module, "moduleItem_");
            if (payload == null) continue;
            if (selected == 4 && needsText) {
                out.append(modulePath + ".desc.text_", Reflect.tryGetObjectField(payload, "text_"), forwardDepth > 0);
            } else if (selected == 30 && needsText) {
                out.append(modulePath + ".opus", extractOpusText(payload), forwardDepth > 0);
            } else if (selected == 5) {
                int bodyCase = (Integer) Reflect.tryGetObjectField(payload, "moduleItemCase_");
                Object body = Reflect.tryGetObjectField(payload, "moduleItem_");
                if (body == null) continue;
                if (bodyCase == 6 && body.getClass().getName().equals(PREFIX + "MdlDynForward")) {
                    out.forwarded = true;
                    String originPath = modulePath + ".moduleItem_.moduleItem_.item_";
                    if (DEBUG_FORWARD_MATCH) Log.d(TAG, "[FORWARD_PATH] " + originPath);
                    collectDynamic(Reflect.tryGetObjectField(body, "item_"), originPath,
                            out, visited, forwardDepth + 1);
                } else if (body.getClass().getName().equals(PREFIX + "MdlDynArchive")
                        && needsText) {
                    out.append(modulePath + ".archive.title_", Reflect.tryGetObjectField(body, "title_"), forwardDepth > 0);
                }
            } else if (selected == 8 && out.config.blockUpRecommend
                    && (Integer) Reflect.tryGetObjectField(payload, "itemCase_") == 3) {
                Object goods = Reflect.tryGetObjectField(payload, "item_");
                if (isRecommendation(optionalField(goods, "rcmdDesc_"))) out.recommended = true;
                Object entries = optionalField(goods, "goodsItems_");
                if (entries instanceof List<?>) {
                    for (Object entry : (List<?>) entries) {
                        if (isRecommendation(optionalField(entry, "adMark_"))) out.recommended = true;
                    }
                }
            }
        }
        // The captured fast-share item also carries original text as Description[].
        // This provides a second text source when an embedded body is abbreviated.
        if (!out.config.rules.isEmpty() || (out.config.blockForwardLottery && out.forwarded)) {
            Object extend = optionalField(item, "extend_");
            Object descriptions = optionalField(extend, "origDesc_");
            if (descriptions instanceof List<?>) {
                StringBuilder originalText = new StringBuilder();
                for (Object description : (List<?>) descriptions) {
                    Object text = optionalField(description, "origText_");
                    if (!(text instanceof String) || ((String) text).isEmpty()) {
                        text = optionalField(description, "text_");
                    }
                    if (text instanceof String) originalText.append(text);
                }
                if (originalText.length() > 0) out.append(path + ".extend_.origDesc_", originalText.toString(), true);
            }
        }
    }

    private static synchronized void logConfig(FollowingFilterConfig.Snapshot config) {
        if (loggedConfig == config) return;
        loggedConfig = config;
        Log.d(TAG, "[CONFIG] forward-aware v3 rules=" + config.rules.size()
                + " blockUpRecommend=" + config.blockUpRecommend
                + " blockForwardLottery=" + config.blockForwardLottery);
        if (DEBUG_FORWARD_MATCH) {
            for (int i = 0; i < config.rules.size(); i++) {
                FollowingFilterConfig.Rule rule = config.rules.get(i);
                Log.d(TAG, "[RULE] #" + (i + 1) + " regex=" + rule.regex + " text=" + rule.text);
            }
        }
    }

    private static void logForwardMatch(int index, Content content) {
        for (String source : content.sources) {
            logChunks("[MATCH_SOURCE] index=" + index + " ", source);
        }
        logChunks("[MATCH_TEXT] index=" + index + " ", content.text.toString());
        logChunks("[FORWARD_ORIGINAL_TEXT] index=" + index + " ", content.originalText.toString());
    }

    private static void logChunks(String prefix, String text) {
        if (text.isEmpty()) { Log.d(TAG, prefix + "(empty)"); return; }
        for (int i = 0; i < text.length(); i += 800) {
            Log.d(TAG, prefix + text.substring(i, Math.min(i + 800, text.length())));
        }
    }

    private static boolean isRecommendation(Object value) {
        if (!(value instanceof String)) return false;
        String normalized = ((String) value).replaceAll("\\s|\\u3000", "").toLowerCase(Locale.ROOT);
        return normalized.equals("up主的推荐") || normalized.equals("up主推荐")
                || normalized.equals("up主的推薦") || normalized.equals("up主推薦");
    }

    /** Returns the text present in the feed response; does not fetch the full article. */
    public static String extractOpusText(Object opus) {
        if (opus == null) return "";
        StringBuilder result = new StringBuilder();
        Object paragraphs = optionalField(opus, "summaries_");
        if (paragraphs instanceof List<?>) {
            for (Object paragraph : (List<?>) paragraphs) {
                String text = paragraphText(paragraph);
                if (text.isEmpty()) continue;
                if (result.length() > 0) result.append('\n');
                result.append(text);
            }
        }
        // Legacy single paragraph is an alternative, not extra text to append.
        if (result.length() == 0) {
            result.append(paragraphText(optionalField(opus, "summary_")));
        }
        return result.toString();
    }

    private static String paragraphText(Object paragraph) {
        if (paragraph == null) return "";
        Object content = optionalField(paragraph, "content_");
        // Observed contentCase=3 is TextParagraph; pictures have a different case.
        if (content == null || !content.getClass().getName().equals(PREFIX + "TextParagraph")) {
            return "";
        }
        Object nodes = optionalField(content, "nodes_");
        if (!(nodes instanceof List<?>)) return "";
        StringBuilder result = new StringBuilder();
        for (Object node : (List<?>) nodes) {
            Object raw = optionalField(node, "rawText_");
            String text = raw instanceof String ? (String) raw : "";
            if (text.isEmpty()) {
                Object nodeContent = optionalField(node, "text_");
                if (nodeContent != null && nodeContent.getClass().getName().equals(PREFIX + "WordNode")) {
                    Object words = optionalField(nodeContent, "words_");
                    if (words instanceof String) text = (String) words;
                }
            }
            // Keep node order and existing spacing, including links, mentions and emoji raw text.
            result.append(text);
        }
        return result.toString();
    }

    private static Object optionalField(Object object, String name) {
        return Reflect.tryGetObjectField(object, name);
    }


    private FollowingListAdSniper() {}
}
