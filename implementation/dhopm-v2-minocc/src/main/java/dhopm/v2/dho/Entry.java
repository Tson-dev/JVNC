package dhopm.v2.dho;

import dhopm.v2.window.Handle;

/**
 * One occurrence of a node inside a transaction (G2-D13). A three-field record:
 *
 * <pre>Entry(Handle ref1, int len, int tid)</pre>
 *
 * <p>The docs conflict on the third slot (plan 02 §5.7 calls it {@code ref2}, the Draft Idea
 * reserves it for a second reference). Per the early-access interpretation it carries the cached
 * distinct-item length {@code |T|} of the transaction (the decay math needs it for every entry and
 * reading it while walking is cheaper than dereferencing the handle). {@code ref1 = null}
 * denotes paper mode (no window): the entry is always live and the pipeline degenerates to V1.
 *
 * <p>Entries of a node are appended in TID order (INV-B). Liveness (D39):
 * {@code ref1 == null ⇒ live}, else {@code ref1.tx != null && ref1.tid == tid}.
 */
public record Entry(Handle ref1, int len, int tid) {

    /** Test/verification factory: an always-live (paper-mode) entry, @see the V1 golden path. */
    public static Entry paper(int len, int tid) {
        return new Entry(null, len, tid);
    }

    /** D39 liveness check — a live entry must point at the handle that still carries its tid. */
    public boolean isLive() {
        return ref1 == null || (ref1.tx != null && ref1.tid == tid);
    }

    @Override
    public String toString() {
        return "Entry{tid=" + tid + ", len=" + len + ", live=" + isLive() + "}";
    }
}