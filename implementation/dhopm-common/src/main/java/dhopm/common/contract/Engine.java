package dhopm.common.contract;

import dhopm.common.transaction.Transaction;

import java.util.List;

/**
 * Algorithm engine contract (shared by all version modules).
 *
 * <p>An engine streams transactions in order via {@link #loadBatch}; after the stream ends,
 * {@link #mineNow()} produces the set of DOP patterns. Logging and benchmarking must stay
 * outside the mining hot path (see OVERALL-PLAN 4.1 / NFR-LOG) — implementations here measure
 * raw mining only.
 */
public interface Engine {

    /** Human-readable engine id, e.g. {@code "v1-standard"}. */
    String name();

    /**
     * Feeds transactions to the engine in TID order. Bulk (batched) load — one scan.
     */
    void loadBatch(List<Transaction> batch);

    /**
     * Runs the mining pass(es) over already loaded transactions and returns results.
     */
    MineResult mineNow();
}