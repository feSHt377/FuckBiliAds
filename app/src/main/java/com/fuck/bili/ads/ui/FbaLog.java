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

    /** Keep the file small: the viewer only ever shows the tail. */
    private static final long MAX_BYTES = 512 * 1024L;
    private static final int MAX_READ_BYTES = 256 * 1024;
    private static final int MEMORY_LINES = 500;

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static final Object LOCK = new Object();
    private static final Deque<String> MEMORY = new ArrayDeque<>();

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

    public static void write(String level, String message) {
        String line = stamp() + " [" + level + "] " + message;
        Log.d(TAG, message);
        synchronized (LOCK) {
            MEMORY.addLast(line);
            while (MEMORY.size() > MEMORY_LINES) {
                MEMORY.removeFirst();
            }
        }
        Context ctx = FbaRuntime.context();
        if (ctx == null) {
            synchronized (LOCK) {
                lastError = "no application context";
            }
            return;
        }
        synchronized (LOCK) {
            FileOutputStream out = null;
            try {
                File dir = new File(ctx.getFilesDir(), DIR);
                if (!dir.exists() && !dir.mkdirs()) {
                    lastError = "mkdir failed: " + dir;
                    return;
                }
                File file = new File(dir, FILE);
                if (file.length() > MAX_BYTES) {
                    File old = new File(dir, ROTATED);
                    if (old.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        old.delete();
                    }
                    //noinspection ResultOfMethodCallIgnored
                    file.renameTo(old);
                }
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

    /** Persistent log file (tail-truncated), newest last. */
    public static String read() {
        Context ctx = FbaRuntime.context();
        if (ctx == null) {
            return "";
        }
        synchronized (LOCK) {
            String current = readFile(new File(new File(ctx.getFilesDir(), DIR), ROTATED), false);
            String latest = readFile(new File(new File(ctx.getFilesDir(), DIR), FILE), true);
            String all = current.isEmpty() ? latest : current + latest;
            if (all.length() > MAX_READ_BYTES) {
                return all.substring(all.length() - MAX_READ_BYTES);
            }
            return all;
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
