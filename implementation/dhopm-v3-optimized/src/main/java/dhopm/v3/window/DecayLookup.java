package dhopm.v3.window;

/**
 * Precomputed decay lookup table: decay[k] = f^k for k = 0..maxAge.
 * maxAge = W-1 (maximum age of a transaction in the window).
 * When W = INFINITE (minOcc=0 or f=1), falls back to Math.pow.
 */
public final class DecayLookup {

    public final double[] table;   // table[k] = f^k for k=0..maxAge
    public final int maxAge;       // W-1, or -1 if infinite window

    private DecayLookup(double[] table, int maxAge) {
        this.table = table;
        this.maxAge = maxAge;
    }

    public static DecayLookup create(double f, int windowSize) {
        if (windowSize == WindowMath.INFINITE || f == 1.0) {
            return new DecayLookup(null, -1);
        }
        int maxAge = windowSize - 1;
        double[] table = new double[maxAge + 1];
        table[0] = 1.0;
        for (int k = 1; k <= maxAge; k++) {
            table[k] = table[k - 1] * f;
        }
        return new DecayLookup(table, maxAge);
    }

    /**
     * Gets f^age. If infinite window or age > maxAge, computes via Math.pow.
     */
    public double get(int age) {
        if (maxAge == -1 || age > maxAge) {
            // Should not happen for finite window (age <= W-1), but fallback for safety
            return Math.pow(table != null ? table[1] : 1.0, age);
        }
        return table[age];
    }

    public boolean isInfinite() {
        return maxAge == -1;
    }
}