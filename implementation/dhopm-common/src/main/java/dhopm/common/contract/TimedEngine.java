package dhopm.common.contract;

import dhopm.common.transaction.Transaction;

import java.util.List;

/**
 * Decorator (GoF) that times the two contract-level operations of an {@link Engine}
 * without touching the engine internals — benchmark/logging independence (OVERALL-PLAN 4.1).
 * For per-stage timings use {@code PhaseAwareEngine} + a {@code PhaseListener} instead.
 */
public final class TimedEngine implements Engine {

    private final Engine delegate;
    private long lastLoadNanos;
    private long lastMineNanos;

    public TimedEngine(Engine delegate) {
        this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public void loadBatch(List<Transaction> batch) {
        long t = System.nanoTime();
        delegate.loadBatch(batch);
        lastLoadNanos = System.nanoTime() - t;
    }

    @Override
    public MineResult mineNow() {
        long t = System.nanoTime();
        MineResult result = delegate.mineNow();
        lastMineNanos = System.nanoTime() - t;
        return result;
    }

    public long lastLoadMs() {
        return lastLoadNanos / 1_000_000L;
    }

    public long lastMineMs() {
        return lastMineNanos / 1_000_000L;
    }
}