package com.fuck.bili.ads.ui;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.app.AndroidAppHelper;

/**
 * Persistent event log shared by every in-host feature.
 *
 * <p>Lines go to logcat (tag {@code FuckBiliAds}) and to
 * {@code files/fuckbiliads/events.log} inside the host's data dir, so the
 * settings panel can show / export them without any storage permission.
 */
public final class FbaLog {

    private static final String TAG = "FuckBiliAds";
    private static final String DIR = "fuckbiliads";
    private static final String FILE = "events.log";
    private static final String ROTATED = "events.log.1";

    /** Keep the file small: the viewer only ever shows the tail. */
    private static final long MAX_BYTES = 512 * 1024L;
    private static final int MAX_READ_BYTES = 256 * 1024;

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static final Object LOCK = new Object();
    private static Context context;

    private FbaLog() {
    }

    public static void init(Context ctx) {
        if (ctx != null) {
            context = ctx.getApplicationContext();
        }
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
        Context ctx = context();
        if (ctx == null) {
            return;
        }
        synchronized (LOCK) {
            FileOutputStream out = null;
            try {
                File dir = new File(ctx.getFilesDir(), DIR);
                if (!dir.exists() && !dir.mkdirs()) {
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
            } catch (Throwable ignore) {
                // logging must never break the host
            } finally {
                close(out);
            }
        }
    }

    /** Whole log (tail-truncated), newest last. */
    public static String read() {
        Context ctx = context();
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

    public static void clear() {
        Context ctx = context();
        if (ctx == null) {
            return;
        }
        synchronized (LOCK) {
            File dir = new File(ctx.getFilesDir(), DIR);
            //noinspection ResultOfMethodCallIgnored
            new File(dir, FILE).delete();
            //noinspection ResultOfMethodCallIgnored
            new File(dir, ROTATED).delete();
        }
    }

    public static File file() {
        Context ctx = context();
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

    private static Context context() {
        if (context == null) {
            try {
                context = AndroidAppHelper.currentApplication();
            } catch (Throwable ignore) {
                // keep null
            }
        }
        return context;
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
