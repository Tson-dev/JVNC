package dhopm.config;

/**
 * Immutable mining configuration: decaying factor {@code f} and min-support
 * ratio {@code delta}. Built via a {@link Builder} (fluent, with validation).
 */
public record MiningParameters(double f, double delta) {

    public MiningParameters {
        if (!(f > 0.0 && f <= 1.0)) {
            throw new IllegalArgumentException("Decaying factor f must be in range (0, 1]");
        }
        if (!(delta > 0.0 && delta <= 1.0)) {
            throw new IllegalArgumentException("Threshold delta must be in range (0, 1]");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** minSup = delta * |DB|, where |DB| is the number of transactions scanned so far. */
    public double minSup(int dbSize) {
        return delta * dbSize;
    }

    public static final class Builder {
        private double f = 0.9;
        private double delta = 0.15;

        public Builder f(double f) {
            this.f = f;
            return this;
        }

        public Builder delta(double delta) {
            this.delta = delta;
            return this;
        }

        public MiningParameters build() {
            return new MiningParameters(f, delta);
        }
    }
}