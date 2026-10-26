package com.fuck.bili.ads.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import android.app.AndroidAppHelper;

/** All configuration belongs to the host app's private storage. */
public final class FollowingFilterConfig {
    public static final String PREFS_NAME = "fredoseep_following_filter";
    private static final String RULES_KEY = "rules_json";
    private static final String FORWARD_LOTTERY_KEY = "block_forward_lottery";
    private static final String RECOMMEND_KEY = "block_up_recommend";
    private static volatile Context appContext;
    private static volatile Snapshot cached;

    public static final class Rule {
        public final String text;
        public final boolean regex;
        public final Pattern compiled;

        public Rule(String text, boolean regex) {
            if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("规则不能为空");
            this.text = text;
            this.regex = regex;
            this.compiled = regex ? Pattern.compile(text) : null;
        }

        public boolean matches(String content) {
            return regex ? compiled.matcher(content).find() : content.contains(text);
        }
    }

    public static final class Snapshot {
        public final boolean blockUpRecommend;
        public final boolean blockForwardLottery;
        public final List<Rule> rules;
        private final String encoded;

        private Snapshot(boolean blockUpRecommend, boolean blockForwardLottery, List<Rule> rules, String encoded) {
            this.blockUpRecommend = blockUpRecommend;
            this.blockForwardLottery = blockForwardLottery;
            this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
            this.encoded = encoded;
        }
    }

    public static void init(Context context) {
        if (context == null) return;
        Context application = context.getApplicationContext();
        appContext = application != null ? application : context;
    }

    private static SharedPreferences preferences() {
        Context context = appContext;
        if (context == null) {
            context = AndroidAppHelper.currentApplication();
            if (context != null) init(context);
        }
        if (context == null) throw new IllegalStateException("Application context is not available yet");
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized Snapshot load() {
        SharedPreferences prefs = preferences();
        String encoded = prefs.getString(RULES_KEY, "[]");
        boolean block = prefs.getBoolean(RECOMMEND_KEY, false);
        boolean forwardLottery = prefs.getBoolean(FORWARD_LOTTERY_KEY, false);
        Snapshot previous = cached;
        if (previous != null && previous.blockUpRecommend == block && previous.blockForwardLottery == forwardLottery
                && previous.encoded.equals(encoded)) {
            return previous;
        }
        List<Rule> rules = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(encoded);
            for (int i = 0; i < array.length(); i++) {
                try {
                    JSONObject object = array.getJSONObject(i);
                    rules.add(new Rule(object.getString("text"), object.optBoolean("regex", false)));
                } catch (Exception e) {
                    Log.e("FollowingList", "Ignoring invalid stored rule at index " + i, e);
                }
            }
        } catch (Exception e) {
            Log.e("FollowingList", "Unable to read stored rules", e);
        }
        cached = new Snapshot(block, forwardLottery, rules, encoded);
        return cached;
    }

    // Existing callers editing keywords/recommendation preserve the new switch.
    public static synchronized boolean save(boolean block, List<Rule> rules) {
        return save(block, load().blockForwardLottery, rules);
    }

    public static synchronized boolean save(boolean block, boolean forwardLottery, List<Rule> rules) {
        try {
            JSONArray array = new JSONArray();
            for (Rule rule : rules) {
                JSONObject object = new JSONObject();
                object.put("text", rule.text);
                object.put("regex", rule.regex);
                array.put(object);
            }
            String encoded = array.toString();
            boolean saved = preferences().edit().putString(RULES_KEY, encoded)
                    .putBoolean(RECOMMEND_KEY, block)
                    .putBoolean(FORWARD_LOTTERY_KEY, forwardLottery).commit();
            // SharedPreferences changes its memory even if the disk write fails.
            cached = null;
            if (!saved) Log.e("FollowingList", "Configuration disk write failed");
            return saved;
        } catch (Exception e) {
            Log.e("FollowingList", "Configuration save failed", e);
            return false;
        }
    }

    private FollowingFilterConfig() {}
}
