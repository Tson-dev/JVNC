package dhopm.v3.metrics;

import dhopm.v3.dho.DHONodeOptimized;
import dhopm.v3.window.WindowBufferOptimized;

/**
 * DO (Damped Occupancy) metric calculator - optimized for V3 structures.
 * Computes DO = Σ (1/|T|) * f^(TL - Td) over live entries of a node.
 * Identical formula to V1/V2, adapted for SoA node + WindowBuffer.
 */
public final class MetricCalculator {

    private MetricCalculator() {}

    /**
     * Computes DO for a node given current TL and decay values in window buffer.
     * DO = Σ (ref2[i])^-1 * decay[slot] for live entries.
     */
    public static double computeDO(DHONodeOptimized node, WindowBufferOptimized window, int tl) {
        double sum = 0.0;
        for (int i = node.head; i < node.size; i++) {
            int slot = node.txSlot[i];
            if (window.alive[slot] == 1 && window.txId[slot] == window.txId[slot]) {
                double decay = window.decay[slot];
                if (decay > 0) {
                    sum += decay / node.ref2[i];
                }
            }
        }
        return sum;
    }

    /**
     * Computes support (number of live entries).
     */
    public static int computeSupport(DHONodeOptimized node, WindowBufferOptimized window) {
        int count = 0;
        for (int i = node.head; i < node.size; i++) {
            int slot = node.txSlot[i];
            if (window.alive[slot] == 1 && window.txId[slot] == window.txId[slot]) {
                count++;
            }
        }
        return count;
    }

    /**
     * Computes occupancy for a live entry: (1/|T|) * f^(TL - tid).
     */
    public static double occupancy(int txLen, double decay) {
        return decay / txLen;
    }
}