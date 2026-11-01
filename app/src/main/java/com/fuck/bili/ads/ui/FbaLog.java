package com.fuck.bili.ads.ui;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Event log for the whole module.
 *
 * <p>Two sinks are kept on purpose: a small in-memory ring (always available,
 * survives a broken/unavailable app context and is what "本次运行" shows) and a
 * file under the host's private data dir (persistent, exported from the
 * settings page). Both are mirrored to logcat with tag {@code FuckBiliAds}.
 */
public final class FbaLog {

    private static final String TAG = FbaRuntime.TAG;
    private static final String DIR = "fuckbiliads";
    private static final String FILE = "events.log";
    private static final String ROTATED = "events.log.1";

    /**
     * Single bounded file: once full, replace its beginning with the newest tail.
     * No rotated file is used, so historical entries cannot exceed this budget.
     */
    static final long MAX_BYTES = 100 * 1024L;
    /** Trims back to this much below {@link #MAX_BYTES} so one rewrite covers
     * many appends instead of re-trimming on every line. */
    static final long TRIM_TARGET_BYTES = MAX_BYTES * 4 / 5;
    private static final int MAX_READ_BYTES = 256 * 1024;
    private static final int MEMORY_LINES = 500;
    /** Maximum lines kept in the pending queue before the oldest are dropped. */
    private static final int PENDING_LIMIT = 2000;

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static final Object LOCK = new Object();
    private static final Deque<String> MEMORY = new ArrayDeque<>();
    /** Lines accepted by {@link #write} but not yet persisted by the worker. */
    private static final Deque<String> PENDING = new ArrayDeque<>();
    private static final ExecutorService IO =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "FbaLogIO");
                thread.setDaemon(true);
                return thread;
            });
    private static final AtomicBoolean DRAINING = new AtomicBoolean(false);

    private static volatile String lastError = "";
    private static volatile boolean fileOk;

    private FbaLog() {
    }

    public static void init(Context ctx) {
        FbaRuntime.init(ctx);
    }

    public static void d(String message) {
        write("D", message);
    }

    public static void i(String message) {
        write("I", message);
    }

    public static void w(String message) {
        write("W", message);
    }

    public static void e(String message, Throwable t) {
        write("E", t == null ? message : message + " :: " + t);
    }

    /**
     * Logs one line. File I/O is always off the calling thread: hook callbacks
     * and the story scanner run on the UI thread, where a trim + fsync per line
     * would drop frames once the file is full.
     */
    public static void write(String level, String message) {
        String line = stamp() + " [" + level + "] " + message;
        Log.d(TAG, message);
        synchronized (LOCK) {
            MEMORY.addLast(line);
            while (MEMORY.size() > MEMORY_LINES) {
                MEMORY.removeFirst();
            }
            PENDING.addLast(line);
            while (PENDING.size() > PENDING_LIMIT) {
                PENDING.removeFirst();
            }
        }
        scheduleDrain();
    }

    /** Starts the single background drain if it is not already running. */
    private static void scheduleDrain() {
        if (DRAINING.compareAndSet(false, true)) {
            IO.execute(FbaLog::drain);
        }
    }

    /** Persists queued lines in order until the queue is empty. */
    private static void drain() {
        try {
            while (true) {
                String line;
                synchronized (LOCK) {
                    line = PENDING.pollFirst();
                }
                if (line == null) {
                    DRAINING.set(false);
                    // Recheck to avoid losing a line appended between poll and flag clear.
                    synchronized (LOCK) {
                        if (PENDING.isEmpty()) {
                            return;
                        }
                    }
                    if (!DRAINING.compareAndSet(false, true)) {
                        return;
                    }
                    continue;
                }
                Context ctx = FbaRuntime.context();
                if (ctx == null) {
                    synchronized (LOCK) {
                        lastError = "no application context";
                    }
                    continue;
                }
                appendLine(ctx, line);
            }
        } catch (Throwable t) {
            DRAINING.set(false);
            synchronized (LOCK) {
                fileOk = false;
                lastError = String.valueOf(t);
            }
            // A later write will schedule a new drain; lines stay queued.
        }
    }

    /** Writes one queued line to disk. Only called by the background worker. */
    private static void appendLine(Context ctx, String line) {
        synchronized (LOCK) {
            FileOutputStream out = null;
            try {
                File dir = new File(ctx.getFilesDir(), DIR);
                if (!dir.exists() && !dir.mkdirs()) {
                    lastError = "mkdir failed: " + dir;
                    return;
                }
                File file = new File(dir, FILE);
                trimToBudget(file, MAX_BYTES, TRIM_TARGET_BYTES);
                out = new FileOutputStream(file, true);
                out.write((line + "\n").getBytes(UTF8));
                fileOk = true;
                lastError = "";
            } catch (Throwable t) {
                fileOk = false;
                lastError = String.valueOf(t);
            } finally {
                close(out);
            }
        }
    }

    /** Rewrites one file, retaining only the newest bytes within the byte budget. */
    static void trimToBudget(File file, long maxBytes) throws Exception {
        trimToBudget(file, maxBytes, TRIM_TARGET_BYTES);
    }

    /** Rewrites one file so it ends at or below {@code targetBytes}. */
    static void trimToBudget(File file, long maxBytes, long targetBytes) throws Exception {
        if (file == null || !file.exists() || file.length() <= maxBytes) {
            return;
        }
        long target = Math.max(1L, Math.min(targetBytes, maxBytes));
        FileInputStream input = null;
        FileOutputStream output = null;
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            input = new FileInputStream(file);
            long skip = file.length() - target;
            long remaining = skip;
            while (remaining > 0) {
                long stepped = input.skip(remaining);
                if (stepped <= 0) {
                    if (input.read() < 0) {
                        break;
                    }
                    stepped = 1;
                }
                remaining -= stepped;
            }
            output = new FileOutputStream(tmp, false);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }
            output.getFD().sync();
            close(input);
            input = null;
            close(output);
            output = null;
            if (!tmp.renameTo(file)) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                if (!tmp.renameTo(file)) {
                    throw new java.io.IOException("log trim rename failed");
                }
            }
        } finally {
            close(input);
            close(output);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    /** Persistent log file (tail-truncated), newest last. */
    public static String read() {
        Context ctx = FbaRuntime.context();
        if (ctx == null) {
            return "";
        }
        // Snapshot the file plus any lines still queued, so the viewer and the
        // exporter include the last few seconds even before the drain catches up.
        String pending = pendingText();
        synchronized (LOCK) {
            String all = readFile(new File(new File(ctx.getFilesDir(), DIR), FILE), true);
            if (!pending.isEmpty()) {
                all = all + pending;
            }
            if (all.length() > MAX_READ_BYTES) {
                return all.substring(all.length() - MAX_READ_BYTES);
            }
            return all;
        }
    }

    /** Queued lines formatted exactly as they will be written to the file. */
    private static String pendingText() {
        synchronized (LOCK) {
            if (PENDING.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (String line : PENDING) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    /** Test hook: waits for the background writer to finish queued lines. */
    static void flushForTest() {
        synchronized (LOCK) {
            if (PENDING.isEmpty()) {
                return;
            }
        }
        while (true) {
            synchronized (LOCK) {
                if (PENDING.isEmpty()) {
                    break;
                }
            }
            try {
                Thread.sleep(5L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /** Test hook: number of lines not yet written to the file. */
    static int pendingCountForTest() {
        synchronized (LOCK) {
            return PENDING.size();
        }
    }

    /** Everything logged since the process started, file or not. */
    public static String recent() {
        synchronized (LOCK) {
            StringBuilder sb = new StringBuilder();
            for (String line : MEMORY) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    public static boolean fileOk() {
        return fileOk;
    }

    public static String lastError() {
        return lastError;
    }

    public static void clear() {
        Context ctx = FbaRuntime.context();
        synchronized (LOCK) {
            MEMORY.clear();
            PENDING.clear();
            if (ctx == null) {
                return;
            }
            File dir = new File(ctx.getFilesDir(), DIR);
            //noinspection ResultOfMethodCallIgnored
            new File(dir, FILE).delete();
            //noinspection ResultOfMethodCallIgnored
            new File(dir, ROTATED).delete();
        }
    }

    public static File file() {
        Context ctx = FbaRuntime.context();
        if (ctx == null) {
            return null;
        }
        return new File(new File(ctx.getFilesDir(), DIR), FILE);
    }

    /** Timestamped file name used for the "save to Download" action. */
    public static String exportFileName() {
        return "FuckBiliAds-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".log";
    }

    private static String readFile(File file, boolean tail) {
        if (file == null || !file.exists()) {
            return "";
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            long length = file.length();
            if (tail && length > MAX_READ_BYTES) {
                //noinspection ResultOfMethodCallIgnored
                in.skip(length - MAX_READ_BYTES);
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), UTF8);
        } catch (Throwable t) {
            return "";
        } finally {
            close(in);
        }
    }

    private static String stamp() {
        return new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    private static void close(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Throwable ignore) {
            // ignore
        }
    }
}
