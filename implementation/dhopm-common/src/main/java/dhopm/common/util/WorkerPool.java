package dhopm.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Shared worker-pool wrapper (scaffold at G0, used from G1+).
 *
 * <p>D4 default: pool size = {@link Runtime#availableProcessors()}; the debug app (G4) will
 * additionally let the user pick any thread count &gt; 0. Instances must be {@link #shutdown()}
 * after use. Not tied to any algorithm — engines take the pool as a dependency.
 */
public final class WorkerPool implements AutoCloseable {

    private final ExecutorService executor;
    private final int workers;
    private final List<Future<?>> pending = new ArrayList<>();

    public WorkerPool(int workers) {
        if (workers < 1) {
            throw new IllegalArgumentException("workers must be >= 1: " + workers);
        }
        this.workers = workers;
        this.executor = Executors.newFixedThreadPool(workers, Thread.ofPlatform().name("dhopm-worker-", 0).factory());
    }

    public static WorkerPool withCpuCount() {
        return new WorkerPool(Runtime.getRuntime().availableProcessors());
    }

    public int workers() {
        return workers;
    }

    public void submit(Runnable task) {
        synchronized (pending) {
            pending.add(executor.submit(task));
        }
    }

    /** Waits for all submitted tasks to finish; rethrows the first failure. */
    public void awaitAll() {
        RuntimeException failure = null;
        synchronized (pending) {
            for (Future<?> f : pending) {
                try {
                    f.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failure = new IllegalStateException("interrupted while awaiting workers", e);
                    break;
                } catch (Exception e) {
                    if (failure == null) {
                        failure = new IllegalStateException("worker task failed", e);
                    }
                }
            }
            pending.clear();
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Runs all tasks, waits for completion, and collects results <em>in task order</em>
     * (merge-after-join, deterministic). Rethrows the first task failure.
     * Invokes {@code onTaskCompleted} with the 1-based completed index and its result
     * as each task finishes — real-time progress without touching run order (INV-E).
     */
    public <T> List<T> invokeAll(List<Callable<T>> tasks) {
        return invokeAll(tasks, null);
    }

    /** @see #invokeAll(List) — overlaid with a per-task completion callback. */
    public <T> List<T> invokeAll(List<Callable<T>> tasks, BiConsumer<Integer, T> onTaskCompleted) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        List<Future<T>> futures;
        try {
            futures = executor.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while invoking workers", e);
        }
        List<T> results = new ArrayList<>(futures.size());
        for (int i = 0; i < futures.size(); i++) {
            results.add(null);
        }
        RuntimeException failure = null;
        for (int i = 0; i < futures.size(); i++) {
            T value = null;
            try {
                value = futures.get(i).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while awaiting workers", e);
            } catch (ExecutionException e) {
                if (failure == null) {
                    failure = new IllegalStateException("worker task failed", e.getCause());
                }
            }
            results.set(i, value);
            if (onTaskCompleted != null) {
                onTaskCompleted.accept(i + 1, value);
            }
        }
        if (failure != null) {
            throw failure;
        }
        return results;
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        shutdown();
    }
}