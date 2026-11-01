package io.hestia.tv.core;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The one worker thread for screen work that can't block the UI (decoding photos, building cards). */
public final class Background {
    private Background() {}

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void run(Runnable work) {
        WORKER.execute(work);
    }

    public static void onMain(Runnable work) {
        MAIN.post(work);
    }
}
