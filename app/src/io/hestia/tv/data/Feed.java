package io.hestia.tv.data;

import android.os.Handler;
import android.os.Looper;

import io.hestia.tv.core.AppLog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A source of data from the network, cached on disk: weather, headlines, photos. One instance per process, one worker
 * thread each, fetched only while a screen is visible and asks (refreshIfStale); listeners hear about it on the main
 * thread. New sources extend this instead of redoing the threads, the staleness or the error handling.
 */
public abstract class Feed {
    public interface Listener { void onUpdated(); }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final String name;
    private final long maxAge;
    private volatile boolean fetching;
    protected volatile long fetchedAt;

    protected Feed(String name, long maxAge) {
        this.name = name;
        this.maxAge = maxAge;
    }

    /** Fetches and stores; runs on the worker. Throwing is fine: logged safely, the cache stays. */
    protected abstract void fetch() throws Exception;

    /** Empty after a clear cache: fetch whatever the age says. */
    protected boolean isEmpty() {
        return false;
    }

    public final void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public final void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** The screens call this when they appear and regularly while they're visible. */
    public final void refreshIfStale() {
        if (fetching || (!isEmpty() && !Freshness.isStale(System.currentTimeMillis(), fetchedAt, maxAge))) return;
        fetching = true;
        worker.execute(() -> {
            try {
                fetch();
            } catch (Exception e) {
                AppLog.w(name + ": " + Net.safeError(e));
            } finally {
                fetching = false;
            }
        });
    }

    protected final void onWorker(Runnable work) {
        worker.execute(work);
    }

    protected final void notifyListeners() {
        main.post(() -> { for (Listener l : new ArrayList<>(listeners)) l.onUpdated(); });
    }
}
