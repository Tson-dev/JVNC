package dhopm.common.testkit;

import dhopm.common.contract.Pattern;
import dhopm.common.transaction.Transaction;

import java.util.List;

/**
 * One golden test case: input stream + parameters + expected DOP set.
 * Data taken from the hand-run document (Nhom01_VDChayTay.md, "Phần 11").
 *
 * @param id           case id (TC1..TC8)
 * @param transactions input transactions in TID order
 * @param decayFactor  decay factor f
 * @param delta        support threshold ∂
 * @param expected     expected patterns (item-set + 4-decimal DO)
 */
public record GoldenCase(String id, List<Transaction> transactions, double decayFactor, double delta,
                         List<Pattern> expected) {

    public int totalTransactions() {
        return transactions.size();
    }

    public int lastTid() {
        return transactions.isEmpty() ? 0 : transactions.get(transactions.size() - 1).tid();
    }
}