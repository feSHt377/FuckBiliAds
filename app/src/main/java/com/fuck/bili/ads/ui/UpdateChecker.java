package com.fuck.bili.ads.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.widget.Toast;

import com.fuck.bili.ads.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Update check against this project's GitHub releases.
 *
 * <p>Both channels are read from {@code /repos/.../releases} in one request:
 * GitHub returns drafts and pre-releases in the same list, so the newest entry
 * overall is picked and compared with the build that is actually installed.
 * A release is considered newer when its version core is higher, or — for the
 * same core — when the release's {@code versionCode} (published in the release
 * body by CI) is higher. That second rule is what turns a running
 * {@code 1.0.9-pre} build into an update once {@code 1.0.9} goes final.
 *
 * <p>Network work always happens on a worker thread; UI and prefs are only
 * touched on the main thread. Failures are silent for the automatic check
 * (log only) and reported inline for the manual one.
 */
public final class UpdateChecker {

    public static final String REPO = "feSHt377/FuckBiliAds";
    public static final String RELEASES_PAGE = "https://github.com/" + REPO + "/releases";

    private static final String API =
            "https://api.github.com/repos/" + REPO + "/releases?per_page=20";
    /** Public Atom feed: same releases, no API quota. */
    private static final String FEED =
            "https://github.com/" + REPO + "/releases.atom";

    /** Automatic check runs at most this often, so launches stay cheap. */
    private static final long AUTO_COOLDOWN_MS = 6L * 60L * 60L * 1000L;
    private static final long AUTO_DELAY_MS = 12_000L;
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 8_000;
    private static final int NOTES_LIMIT = 480;

    private static final Pattern VERSION =
            Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
    private static final Pattern VERSION_CODE =
            Pattern.compile("(?i)versioncode\\D{0,12}(\\d+)");

    private static final Pattern FEED_ENTRY =
            Pattern.compile("<entry>(.*?)</entry>", Pattern.DOTALL);
    private static final Pattern FEED_LINK =
            Pattern.compile("<link[^>]*rel=\"alternate\"[^>]*href=\"([^\"]+)\"");
    private static final Pattern FEED_TITLE =
            Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);
    private static final Pattern FEED_UPDATED =
            Pattern.compile("<updated>(.*?)</updated>", Pattern.DOTALL);
    private static final Pattern FEED_CONTENT =
            Pattern.compile("<content[^>]*>(.*?)</content>", Pattern.DOTALL);

    private static final Object LOCK = new Object();

    private static volatile boolean loaded;
    private static volatile boolean checking;
    private static volatile Release latest;
    private static volatile Release available;
    private static volatile String error = "";
    private static volatile String promptedTag = "";

    private UpdateChecker() {
    }

    // ------------------------------------------------------------------ model

    /** One GitHub release entry, trimmed to what the UI needs. */
    public static final class Release {

        public final String tag;
        public final String title;
        public final String url;
        public final String notes;
        public final boolean prerelease;
        public final long publishedAt;
        public final int versionCode;
        public final int[] core;

        Release(String tag, String title, String url, String notes,
                boolean prerelease, long publishedAt, int versionCode, int[] core) {
            this.tag = tag == null ? "" : tag;
            this.title = title == null ? "" : title;
            this.url = url == null ? "" : url;
            this.notes = notes == null ? "" : notes;
            this.prerelease = prerelease;
            this.publishedAt = publishedAt;
            this.versionCode = versionCode;
            this.core = core;
        }

        /** {@code v1.0.9-pre} → {@code 1.0.9}. */
        public String version() {
            if (core == null) {
                return tag;
            }
            return core[0] + "." + core[1] + "." + core[2];
        }

        public String channel() {
            return prerelease ? "测试版" : "正式版";
        }

        public String dateText() {
            if (publishedAt <= 0) {
                return "未知时间";
            }
            return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    .format(new Date(publishedAt));
        }

        public String heading() {
            return title.isEmpty() ? tag : title;
        }

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("tag", tag);
                o.put("title", title);
                o.put("url", url);
                o.put("notes", notes);
                o.put("pre", prerelease);
                o.put("at", publishedAt);
                o.put("code", versionCode);
                o.put("core", version());
            } catch (Throwable ignore) {
                // best effort
            }
            return o;
        }

        static Release fromJson(JSONObject o) {
            if (o == null) {
                return null;
            }
            String tag = o.optString("tag", "");
            if (tag.isEmpty()) {
                return null;
            }
            int[] core = parseCore(o.optString("core", tag));
            if (core == null) {
                core = parseCore(tag);
            }
            return new Release(tag, o.optString("title", ""), o.optString("url", ""),
                    o.optString("notes", ""), o.optBoolean("pre", false),
                    o.optLong("at", 0L), o.optInt("code", 0), core);
        }
    }

    // ------------------------------------------------------------- public API

    /**
     * Schedules the launch-time check. Cheap and idempotent: it returns right
     * away unless the auto switch is on, this is the main process, and the last
     * check is older than {@link #AUTO_COOLDOWN_MS}.
     */
    public static void startAutoCheck(String processName, String packageName) {
        try {
            if (!FbaPrefs.updateAuto()) {
                FbaLog.i("[UPDATE] auto check disabled");
                return;
            }
            if (!isMainProcess(processName, packageName)) {
                FbaLog.i("[UPDATE] skip auto check in process " + processName);
                return;
            }
            long last = FbaPrefs.updateCheckedAt();
            long now = System.currentTimeMillis();
            if (last > 0 && now - last < AUTO_COOLDOWN_MS) {
                FbaLog.i("[UPDATE] auto check skipped, last "
                        + (now - last) / 60_000L + " min ago");
                return;
            }
            Handler handler = FbaRuntime.handler();
            if (handler == null) {
                return;
            }
            handler.postDelayed(() -> check(null, false), AUTO_DELAY_MS);
            FbaLog.i("[UPDATE] auto check scheduled in " + (AUTO_DELAY_MS / 1000) + "s");
        } catch (Throwable t) {
            FbaLog.e("[UPDATE] schedule failed", t);
        }
    }

    /**
     * Runs one check on a worker thread.
     *
     * @param context used for the manual toasts/dialogs; may be null for auto
     * @param manual  when true the result is always surfaced to the user
     */
    public static void check(final Context context, final boolean manual) {
        synchronized (LOCK) {
            if (checking) {
                if (manual && context != null) {
                    toast(context, "正在检查更新…");
                }
                return;
            }
            checking = true;
        }
        FbaLog.i("[UPDATE] checking " + REPO + (manual ? " (manual)" : ""));
        new Thread(() -> {
            Release found = null;
            String failure = "";
            boolean includePre = FbaPrefs.updatePrerelease();
            try {
                found = newest(fetchReleases(), includePre);
                if (found == null) {
                    failure = includePre ? "没有找到可用版本" : "没有找到正式版";
                }
            } catch (Throwable t) {
                failure = describe(t);
            }
            final Release result = found;
            final String failed = failure;
            FbaRuntime.onMain(() -> finish(context, manual, result, failed));
        }, "FuckBiliAds-update").start();
    }

    /** Release newer than this build; restored from prefs across launches. */
    public static Release available() {
        ensureLoaded();
        return available;
    }

    public static boolean hasUpdate() {
        return available() != null;
    }

    /** Newest release seen by the last successful check, any channel. */
    public static Release latest() {
        ensureLoaded();
        return latest;
    }

    public static String lastError() {
        ensureLoaded();
        return error;
    }

    public static boolean isChecking() {
        return checking;
    }

    // --------------------------------------------------------------- settings

    public static String currentVersion() {
        return BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")";
    }

    /** One line for the settings row: newest release, or why there is none. */
    public static String latestLabel() {
        ensureLoaded();
        Release l = latest;
        if (l != null) {
            return l.version() + " · " + l.channel() + " · " + l.dateText();
        }
        if (!error.isEmpty()) {
            return "检查失败";
        }
        long at = FbaPrefs.updateCheckedAt();
        if (at > 0) {
            return "未发现更新条目";
        }
        return "未检查";
    }

    /** Subtitle under the manual check row. */
    public static String statusHint() {
        ensureLoaded();
        if (!error.isEmpty()) {
            return error;
        }
        long at = FbaPrefs.updateCheckedAt();
        if (at <= 0) {
            return "向 GitHub 查询正式版与测试版";
        }
        Release a = available;
        if (a != null) {
            return "可更新到 " + a.version() + " · " + a.channel();
        }
        return "已是最新 · " + new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(new Date(at));
    }

    /** Opens the release page (all versions), used by the 关于 row. */
    public static void openReleasePage(Context context) {
        open(context, RELEASES_PAGE);
    }

    /** Opens a URL, falling back to copying it when no browser can handle it. */
    public static void open(Context context, String url) {
        if (context == null || url == null || url.isEmpty()) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
            FbaLog.i("[UPDATE] opened " + url);
        } catch (Throwable t) {
            FbaLog.e("[UPDATE] open failed: " + url, t);
            copy(context, url);
            toast(context, "没有可用的浏览器，链接已复制");
        }
    }

    /** Update dialog: headline, channel, notes, and the jump-to-release button. */
    public static void showDialog(final Context context, final Release release) {
        if (context == null || release == null) {
            return;
        }
        Activity activity = activityOf(context);
        if (activity == null || activity.isFinishing()) {
            activity = FbaRuntime.topActivity();
        }
        if (activity == null || activity.isFinishing()) {
            toast(context, "发现新版本 " + release.version() + "，请到设置页「更新」查看");
            return;
        }
        final Context ui = activity;
        try {
            StringBuilder message = new StringBuilder();
            message.append(release.heading());
            if (!release.heading().contains(release.version())) {
                message.append(" · ").append(release.version());
            }
            message.append('\n')
                    .append("渠道：").append(release.channel())
                    .append("　发布：").append(release.dateText())
                    .append('\n')
                    .append("当前版本：").append(currentVersion())
                    .append("\n\n");
            String notes = trimNotes(release.notes);
            if (!notes.isEmpty()) {
                message.append(notes).append("\n\n");
            }
            message.append("点「前往下载」打开 GitHub Release 页面。");

            new AlertDialog.Builder(ui)
                    .setTitle("发现新版本 " + release.version())
                    .setMessage(message.toString())
                    .setPositiveButton("前往下载", (dialog, which) -> open(ui, release.url))
                    .setNeutralButton("忽略此版本", (dialog, which) -> {
                        FbaPrefs.setUpdateDismissed(release.tag);
                        promptedTag = release.tag;
                        FbaLog.i("[UPDATE] dismissed " + release.tag);
                        toast(context, "已忽略 " + release.version() + "，可在设置页重新打开");
                    })
                    .setNegativeButton("稍后", null)
                    .show();
            promptedTag = release.tag;
        } catch (Throwable t) {
            FbaLog.e("[UPDATE] dialog failed", t);
            toast(context, "发现新版本 " + release.version() + "，请到设置页「更新」查看");
        }
    }

    // ------------------------------------------------------------ check result

    private static void finish(Context context, boolean manual, Release found, String failure) {
        checking = false;
        if (found == null) {
            error = failure.isEmpty() ? "检查失败" : failure;
            FbaPrefs.setUpdateError(error);
            FbaLog.w("[UPDATE] check failed: " + error);
            if (manual && context != null) {
                toast(context, "检查更新失败：" + error);
            }
            return;
        }

        error = "";
        FbaPrefs.setUpdateError("");
        // Only a successful query counts for the cooldown, so an offline launch
        // retries next time instead of going quiet for six hours.
        FbaPrefs.setUpdateCheckedAt(System.currentTimeMillis());
        latest = found;
        FbaPrefs.setUpdateLatest(found.toJson().toString());

        Release newer = newerThanLocal(found) ? found : null;
        available = newer;
        FbaPrefs.setUpdateAvailable(newer == null ? "" : newer.toJson().toString());
        FbaLog.i("[UPDATE] newest=" + found.tag + " pre=" + found.prerelease
                + " local=" + BuildConfig.VERSION_NAME + "(" + BuildConfig.VERSION_CODE + ")"
                + " update=" + (newer != null));

        if (!manual) {
            if (newer != null && !newer.tag.equals(FbaPrefs.updateDismissed())) {
                promptWhenReady(newer, 0);
            }
            return;
        }

        if (context == null) {
            return;
        }
        if (newer == null) {
            toast(context, "已是最新版本（" + BuildConfig.VERSION_NAME + "）");
            return;
        }
        showDialog(context, newer);
    }

    /** Waits for a real activity (the host may still be on its splash screen). */
    private static void promptWhenReady(final Release release, final int attempt) {
        Handler handler = FbaRuntime.handler();
        if (handler == null || attempt > 10) {
            return;
        }
        handler.postDelayed(() -> {
            try {
                if (release.tag.equals(promptedTag)) {
                    return;
                }
                Activity activity = FbaRuntime.topActivity();
                if (activity == null || activity.isFinishing()) {
                    promptWhenReady(release, attempt + 1);
                    return;
                }
                FbaLog.i("[UPDATE] prompting " + release.tag);
                showDialog(activity, release);
            } catch (Throwable t) {
                FbaLog.e("[UPDATE] prompt failed", t);
            }
        }, attempt == 0 ? 2_000L : 3_000L);
    }

    // ------------------------------------------------------------- comparison

    private static boolean newerThanLocal(Release release) {
        int[] local = parseCore(BuildConfig.VERSION_NAME);
        if (local == null || release.core == null) {
            return false;
        }
        int cmp = compareCore(release.core, local);
        if (cmp != 0) {
            return cmp > 0;
        }
        // Same version number: let CI's versionCode decide (pre → final of the
        // same version is exactly this case).
        if (release.versionCode > 0 && BuildConfig.VERSION_CODE > 0
                && release.versionCode != BuildConfig.VERSION_CODE) {
            return release.versionCode > BuildConfig.VERSION_CODE;
        }
        // Without a versionCode only a channel change is actionable.
        boolean localPre = isPrereleaseName(BuildConfig.VERSION_NAME);
        return release.prerelease != localPre && !release.prerelease;
    }

    private static int compareCore(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            int left = a.length > i ? a[i] : 0;
            int right = b.length > i ? b[i] : 0;
            if (left != right) {
                return left > right ? 1 : -1;
            }
        }
        return 0;
    }

    private static Release newest(List<Release> releases, boolean includePre) {
        Release best = null;
        for (Release release : releases) {
            if (release == null || release.core == null) {
                continue;
            }
            if (release.prerelease && !includePre) {
                continue;
            }
            if (best == null || isNewerRelease(release, best)) {
                best = release;
            }
        }
        return best;
    }

    private static boolean isNewerRelease(Release a, Release b) {
        int cmp = compareCore(a.core, b.core);
        if (cmp != 0) {
            return cmp > 0;
        }
        if (a.prerelease != b.prerelease) {
            return !a.prerelease;
        }
        if (a.versionCode > 0 && b.versionCode > 0 && a.versionCode != b.versionCode) {
            return a.versionCode > b.versionCode;
        }
        return a.publishedAt > b.publishedAt;
    }

    // --------------------------------------------------------------- fetching

    /**
     * Tries the public release feed first: it lists pre-releases too and, unlike
     * the REST API, is not subject to the 60-requests-per-hour per-IP limit that
     * shared mobile addresses (and CI hosts) hit constantly. The REST API stays
     * as a fallback for the day the feed markup changes.
     */
    private static List<Release> fetchReleases() throws Exception {
        Throwable feedError;
        try {
            String body = fetchText(FEED, "application/atom+xml");
            List<Release> releases = parseFeed(body);
            if (!releases.isEmpty()) {
                FbaLog.i("[UPDATE] source=feed entries=" + releases.size());
                return releases;
            }
            feedError = new IllegalStateException("release feed 为空");
        } catch (Throwable t) {
            feedError = t;
            FbaLog.w("[UPDATE] feed failed: " + describe(t));
        }
        try {
            String body = fetchText(API, "application/vnd.github+json");
            List<Release> releases = parseApi(body);
            FbaLog.i("[UPDATE] source=api entries=" + releases.size());
            return releases;
        } catch (Throwable t) {
            throw new IllegalStateException("feed: " + describe(feedError)
                    + "；api: " + describe(t));
        }
    }

    private static String fetchText(String url, String accept) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", accept);
            connection.setRequestProperty("User-Agent", "FuckBiliAds/" + BuildConfig.VERSION_NAME);
            int code = connection.getResponseCode();
            InputStream stream = code >= 400
                    ? connection.getErrorStream() : connection.getInputStream();
            String body = readAll(stream);
            if (code != 200) {
                throw new IllegalStateException("HTTP " + code
                        + (body.isEmpty() ? "" : " · " + firstLine(body)));
            }
            return body;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Parses {@code releases.atom}; drafts never appear in the feed. */
    static List<Release> parseFeed(String xml) {
        List<Release> releases = new ArrayList<>();
        if (xml == null || xml.isEmpty()) {
            return releases;
        }
        Matcher entries = FEED_ENTRY.matcher(xml);
        while (entries.find()) {
            String block = entries.group(1);
            String link = group(FEED_LINK, block);
            String title = unescapeXml(group(FEED_TITLE, block));
            String updated = group(FEED_UPDATED, block);
            String tag = tagFromLink(link);
            if (tag.isEmpty()) {
                continue;
            }
            String text = htmlToText(unescapeXml(group(FEED_CONTENT, block)));
            int[] core = parseCore(tag);
            if (core == null) {
                core = parseCore(title);
            }
            if (core == null) {
                continue;
            }
            boolean prerelease = isPrereleaseName(tag) || title.contains("测试版");
            releases.add(new Release(tag, title, link, text, prerelease,
                    parseTime(updated), parseVersionCode(text), core));
        }
        return releases;
    }

    /** Parses the REST payload; drafts are dropped here. */
    static List<Release> parseApi(String json) throws Exception {
        JSONArray array = new JSONArray(json);
        List<Release> releases = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null || item.optBoolean("draft", false)) {
                continue;
            }
            String tag = item.optString("tag_name", "");
            String name = item.optString("name", "");
            String body = item.optString("body", "");
            String url = item.optString("html_url", "");
            if (url.isEmpty() && !tag.isEmpty()) {
                url = "https://github.com/" + REPO + "/releases/tag/" + tag;
            }
            int[] core = parseCore(tag);
            if (core == null) {
                core = parseCore(name);
            }
            if (core == null) {
                continue;
            }
            boolean prerelease = item.optBoolean("prerelease", false)
                    || isPrereleaseName(tag)
                    || name.contains("测试版");
            releases.add(new Release(tag, name, url, body, prerelease,
                    parseTime(item.optString("published_at", item.optString("created_at", ""))),
                    parseVersionCode(body), core));
        }
        return releases;
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        } finally {
            try {
                stream.close();
            } catch (Throwable ignore) {
                // ignore
            }
        }
        return new String(out.toByteArray(), "UTF-8");
    }

    // ------------------------------------------------------------------ utils

    static int[] parseCore(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Matcher matcher = VERSION.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return new int[]{
                number(matcher.group(1)),
                number(matcher.group(2)),
                number(matcher.group(3))
        };
    }

    private static int number(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (Throwable ignore) {
            return 0;
        }
    }

    private static String group(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    /** {@code .../releases/tag/v1.0.9-pre} → {@code v1.0.9-pre}. */
    private static String tagFromLink(String link) {
        if (link == null) {
            return "";
        }
        int index = link.indexOf("/releases/tag/");
        if (index < 0) {
            return "";
        }
        String tag = link.substring(index + "/releases/tag/".length());
        int cut = tag.indexOf('?');
        if (cut >= 0) {
            tag = tag.substring(0, cut);
        }
        cut = tag.indexOf('#');
        if (cut >= 0) {
            tag = tag.substring(0, cut);
        }
        return tag;
    }

    private static String unescapeXml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    /** Release notes arrive as escaped HTML in the feed; keep the plain text. */
    private static String htmlToText(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String text = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)</li>", "\n")
                .replaceAll("(?s)<[^>]+>", "");
        text = unescapeXml(text);
        return text.replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    static int parseVersionCode(String body) {
        if (body == null || body.isEmpty()) {
            return 0;
        }
        Matcher matcher = VERSION_CODE.matcher(body);
        return matcher.find() ? number(matcher.group(1)) : 0;
    }

    private static boolean isPrereleaseName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.US);
        return lower.contains("-pre") || lower.contains("-beta")
                || lower.contains("-alpha") || lower.contains("-rc");
    }

    private static long parseTime(String iso) {
        if (iso == null || iso.isEmpty()) {
            return 0L;
        }
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (Throwable ignore) {
            // fall back to the legacy parser below
        }
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date date = format.parse(iso);
            return date == null ? 0L : date.getTime();
        } catch (Throwable ignore) {
            return 0L;
        }
    }

    /** Release notes are markdown; keep a readable prefix for the dialog. */
    private static String trimNotes(String notes) {
        if (notes == null || notes.trim().isEmpty()) {
            return "";
        }
        String text = notes.replace("\r\n", "\n").replace("\r", "\n");
        StringBuilder sb = new StringBuilder();
        String[] lines = text.split("\n");
        int kept = 0;
        for (String line : lines) {
            String value = line.trim();
            if (value.startsWith("<!--")
                    || value.startsWith("**Full Changelog**")
                    || value.startsWith("Full Changelog")) {
                continue;
            }
            value = value.replaceAll("^#{1,6}\\s*", "")
                    .replaceAll("^[*+-]\\s+", "· ")
                    .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                    .replaceAll("`", "");
            if (value.isEmpty()) {
                continue;
            }
            sb.append(value).append('\n');
            if (++kept >= 8 || sb.length() >= NOTES_LIMIT) {
                break;
            }
        }
        String result = sb.toString().trim();
        return result.length() > NOTES_LIMIT ? result.substring(0, NOTES_LIMIT) + "…" : result;
    }

    private static String describe(Throwable t) {
        if (t instanceof UnknownHostException) {
            return "无法连接 GitHub（DNS 解析失败）";
        }
        if (t instanceof SocketTimeoutException) {
            return "连接 GitHub 超时";
        }
        String message = t.getMessage();
        return message == null || message.isEmpty() ? t.getClass().getSimpleName() : message;
    }

    private static String firstLine(String body) {
        int index = body.indexOf('\n');
        String line = index > 0 ? body.substring(0, index) : body;
        return line.length() > 120 ? line.substring(0, 120) + "…" : line;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (LOCK) {
            if (loaded) {
                return;
            }
            latest = Release.fromJson(parseJson(FbaPrefs.updateLatest()));
            Release stored = Release.fromJson(parseJson(FbaPrefs.updateAvailable()));
            // The stored hint may predate an upgrade: re-check it against the
            // build that is running now, otherwise a fresh install keeps
            // offering the version it was just updated to.
            if (stored != null && newerThanLocal(stored)) {
                available = stored;
            } else {
                available = null;
                if (stored != null) {
                    FbaPrefs.setUpdateAvailable("");
                }
            }
            error = FbaPrefs.updateError();
            loaded = true;
        }
    }

    private static JSONObject parseJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(json);
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static void copy(Context context, String text) {
        try {
            ClipboardManager manager = (ClipboardManager)
                    context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager != null) {
                manager.setPrimaryClip(ClipData.newPlainText("FuckBiliAds", text));
            }
        } catch (Throwable ignore) {
            // ignore
        }
    }

    private static void toast(Context context, String text) {
        try {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show();
        } catch (Throwable ignore) {
            // ignore
        }
    }

    private static Activity activityOf(Context context) {
        Context current = context;
        for (int i = 0; i < 4 && current != null; i++) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            if (!(current instanceof android.content.ContextWrapper)) {
                return null;
            }
            current = ((android.content.ContextWrapper) current).getBaseContext();
        }
        return null;
    }

    /** The check must not run once per host process (B站 spawns several). */
    private static boolean isMainProcess(String processName, String packageName) {
        String process = processName == null ? "" : processName;
        String pkg = packageName == null ? "" : packageName;
        if (process.isEmpty()) {
            process = FbaRuntime.processName();
        }
        if (process.isEmpty()) {
            return true;
        }
        return process.equals(pkg) || !process.contains(":");
    }
}
