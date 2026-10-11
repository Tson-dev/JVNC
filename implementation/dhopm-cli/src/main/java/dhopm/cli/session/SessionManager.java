package dhopm.cli.session;

import dhopm.cli.Codes;
import dhopm.cli.ManagerException;
import dhopm.cli.Params;
import dhopm.common.config.MiningConfig;
import dhopm.common.transaction.Transaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holds streaming sessions (G5-M3): DHO-List state fed incrementally before mining. Sessions are
 * independent; the Manager serialises requests per session (the socket read loop is serial, and
 * {@link #get} hands back a synchronised state holder).
 */
public final class SessionManager {

    /** One session's accumulated stream + its mining config. */
    public static final class Session {
        private final String id;
        private final MiningConfig config;
        private final List<Transaction> transactions = new ArrayList<>();
        private int lastTid = 0;

        private Session(String id, MiningConfig config) {
            this.id = id;
            this.config = config;
        }

        public String id() {
            return id;
        }

        public MiningConfig config() {
            return config;
        }

        public synchronized List<Transaction> transactions() {
            return List.copyOf(transactions);
        }

        public synchronized int lastTid() {
            return lastTid;
        }

        public synchronized int feed(List<Transaction> batch) {
            for (Transaction t : batch) {
                if (t.tid() <= lastTid) {
                    throw ManagerException.invalid("feed TIDs must strictly increase: " + t.tid() + " <= " + lastTid);
                }
                transactions.add(t);
                lastTid = t.tid();
            }
            return batch.size();
        }
    }

    private final Map<String, Session> sessions = new LinkedHashMap<>();
    private final AtomicInteger counter = new AtomicInteger();

    public synchronized Session create(MiningConfig config) {
        String id = "s" + counter.incrementAndGet();
        Session session = new Session(id, config);
        sessions.put(id, session);
        return session;
    }

    public synchronized Session get(String id) {
        Session session = sessions.get(id);
        if (session == null) {
            throw new ManagerException(Codes.SESSION_NOT_FOUND, "no such session: " + id);
        }
        return session;
    }

    /** Parses the JSON {@code transactions[]} array into canonical transactions. */
    public static List<Transaction> parseTransactions(Params p) {
        Object raw = p.raw().get("transactions");
        if (!(raw instanceof List<?> list)) {
            throw ManagerException.missing("transactions");
        }
        List<Transaction> out = new ArrayList<>(list.size());
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                throw ManagerException.invalid("each transaction must be an object {tid, items}");
            }
            Object tidObj = m.get("tid");
            if (!(tidObj instanceof Number n)) {
                throw ManagerException.invalid("transaction.tid must be an integer");
            }
            Object itemsObj = m.get("items");
            if (!(itemsObj instanceof List<?> items)) {
                throw ManagerException.invalid("transaction.items must be an array");
            }
            String[] arr = new String[items.size()];
            for (int i = 0; i < items.size(); i++) {
                arr[i] = String.valueOf(items.get(i));
            }
            try {
                out.add(new Transaction(n.intValue(), arr));
            } catch (IllegalArgumentException e) {
                throw ManagerException.invalid(e.getMessage());
            }
        }
        return out;
    }
}
