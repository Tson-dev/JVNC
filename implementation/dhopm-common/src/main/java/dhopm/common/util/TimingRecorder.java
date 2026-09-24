package dhopm.common.util;

import dhopm.common.contract.Phase;
import dhopm.common.contract.PhaseListener;

import java.util.EnumMap;
import java.util.Map;

/**
 * Collects per-phase timing and peak heap from a {@link PhaseListener}.
 * Shared by benchmark drivers and the app — independent of any algorithm.
 */
public final class TimingRecorder implements PhaseListener {

    private final EnumMap<Phase, Long> nanos = new EnumMap<>(Phase.class);
    private long peakHeapBytes;

    public TimingRecorder() {
        for (Phase p : Phase.values()) {
            nanos.put(p, 0L);
        }
    }

    @Override
    public void onPhase(Phase phase, long startNanos, long endNanos) {
        long elapsed = Math.max(0L, endNanos - startNanos);
        nanos.merge(phase, elapsed, Long::sum);
        peakHeapBytes = Math.max(peakHeapBytes, Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
    }

    public long phaseNanos(Phase phase) {
        return nanos.getOrDefault(phase, 0L);
    }

    public long phaseMs(Phase phase) {
        return phaseNanos(phase) / 1_000_000L;
    }

    public long totalMs() {
        return nanos.values().stream().mapToLong(Long::longValue).sum() / 1_000_000L;
    }

    public long peakHeapBytes() {
        return peakHeapBytes;
    }

    public Map<Phase, Long> snapshot() {
        return new EnumMap<>(nanos);
    }

    @Override
    public String toString() {
        return "TimingRecorder{C=" + phaseMs(Phase.CONSTRUCTION) + "ms, R=" + phaseMs(Phase.RECONSTRUCTION)
                + "ms, M=" + phaseMs(Phase.MINING) + "ms, heap=" + peakHeapBytes + "B}";
    }
}