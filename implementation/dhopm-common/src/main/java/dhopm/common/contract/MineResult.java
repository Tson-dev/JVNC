package dhopm.common.contract;

import java.util.List;

/**
 * Result of a {@code mineNow()} invocation.
 *
 * @param patterns          mined DOP patterns
 * @param totalTransactions total transactions loaded at mining time ({@code |DB|})
 * @param lastTid           TID of the latest scanned transaction (TL)
 * @param minSup            effective minimum support at mining time
 */
public record MineResult(List<Pattern> patterns, long totalTransactions, int lastTid, double minSup) {
}