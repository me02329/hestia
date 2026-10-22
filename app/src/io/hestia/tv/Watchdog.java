package io.hestia.tv;

import android.content.Context;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Records Java crashes. The next home launch shows recovery UI; no system changes. */
final class Watchdog {
    private Watchdog() {}

    private static final long WINDOW = 5 * 60_000L;
    private static final int LIMIT = 3;
    private static boolean installed;

    static synchronized void install(Context c) {
        if (installed) return;
        installed = true;
        final Context app = c.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                record(app, System.currentTimeMillis());
            } catch (Throwable ignored) {
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    /** Our home screen crashed too often just now: don't retry (avoids a crash loop until the user chooses recovery). */
    static boolean crashedTooOften(Context c) {
        return recent(c, System.currentTimeMillis()).size() >= LIMIT;
    }

    /** Explicit retry from the recovery screen resets the counter. */
    static void clear(Context c) {
        new AtomicFile(file(c)).delete();
    }

    private static synchronized int record(Context c, long now) throws IOException {
        List<Long> list = recent(c, now);
        list.add(now);
        StringBuilder sb = new StringBuilder();
        for (long at : list) sb.append(at).append('\n');
        AtomicFile target = new AtomicFile(file(c));
        FileOutputStream out = null;
        try {
            out = target.startWrite();
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            target.finishWrite(out);
        } catch (IOException e) {
            target.failWrite(out);
            throw e;
        }
        return list.size();
    }

    private static List<Long> recent(Context c, long now) {
        List<Long> list = new ArrayList<>();
        File f = file(c);
        try (FileInputStream in = new AtomicFile(f).openRead()) {
            byte[] data = new byte[(int) Math.min(f.length(), 4096)];
            int read = in.read(data);
            if (read <= 0) return list;
            for (String line : new String(data, 0, read, StandardCharsets.UTF_8).split("\n")) {
                try {
                    long at = Long.parseLong(line.trim());
                    if (now - at < WINDOW && at <= now) list.add(at);
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return list;
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), "home-crashes.txt");
    }
}
