package com.ourgiant.docscrubber.watch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Watches a configurable set of directories (top-level only, not recursive — a watch-listed
 * folder like Downloads is the expected use case, not an arbitrarily deep tree) for newly created
 * files and reports them to a {@link Listener} after a short debounce so a file still being
 * written (e.g. mid-download) has a chance to finish first. This is a best-effort delay, not a
 * guarantee — a very large or slow write can still be scanned before it's complete.
 */
public final class DirectoryWatchService {

    private static final Logger logger = LoggerFactory.getLogger(DirectoryWatchService.class);
    private static final long DEBOUNCE_MILLIS = 750;

    public interface Listener {
        void onNewFile(Path file);
    }

    private final Listener listener;
    private final WatchService watchService;
    private final Map<WatchKey, Path> watchedDirsByKey = new ConcurrentHashMap<>();
    private final ExecutorService watchExecutor = Executors.newSingleThreadExecutor(r -> daemonThread(r, "docscrubber-directory-watch"));
    private final ScheduledExecutorService debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> daemonThread(r, "docscrubber-directory-watch-debounce"));
    private volatile boolean running;

    public DirectoryWatchService(Listener listener) throws IOException {
        this.listener = listener;
        this.watchService = FileSystems.getDefault().newWatchService();
    }

    /** Replaces the full set of watched directories, registering newly-added ones and unregistering removed ones. Non-existent directories are silently skipped. */
    public synchronized void setWatchedDirectories(Collection<Path> directories) {
        Set<Path> desired = new HashSet<>(directories);

        watchedDirsByKey.entrySet().removeIf(entry -> {
            if (!desired.contains(entry.getValue())) {
                entry.getKey().cancel();
                return true;
            }
            return false;
        });

        Set<Path> alreadyWatched = new HashSet<>(watchedDirsByKey.values());
        for (Path dir : desired) {
            if (alreadyWatched.contains(dir)) {
                continue;
            }
            if (!Files.isDirectory(dir)) {
                logger.warn("Skipping watch directory that no longer exists: {}", dir);
                continue;
            }
            try {
                WatchKey key = dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
                watchedDirsByKey.put(key, dir);
            } catch (IOException e) {
                logger.warn("Failed to register watch directory {}", dir, e);
            }
        }

        if (!watchedDirsByKey.isEmpty()) {
            start();
        }
    }

    private synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        watchExecutor.submit(this::watchLoop);
    }

    public synchronized void shutdown() {
        running = false;
        watchExecutor.shutdownNow();
        debounceExecutor.shutdownNow();
        try {
            watchService.close();
        } catch (IOException e) {
            logger.warn("Failed to close directory watch service", e);
        }
    }

    private void watchLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                return;
            }

            Path dir = watchedDirsByKey.get(key);
            if (dir != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    Path fileName = (Path) event.context();
                    Path file = dir.resolve(fileName);
                    debounceExecutor.schedule(() -> notifyIfStillPresent(file), DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
                }
            }

            if (!key.reset()) {
                watchedDirsByKey.remove(key);
            }
        }
    }

    private void notifyIfStillPresent(Path file) {
        if (Files.isRegularFile(file)) {
            listener.onNewFile(file);
        }
    }

    private static Thread daemonThread(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }
}
