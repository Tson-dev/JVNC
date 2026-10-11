package dhopm.common.contract;

/**
 * Engine that can stream fine-grained mining events to tooling (GoF Observer) — the CLI
 * {@code stream} command and the FE mining screen. Contract (plan 00 §4.2):
 * <ul>
 *   <li>the listener is invoked from mining worker threads → implementations must be thread-safe;</li>
 *   <li>a {@code null} listener must be a zero-cost no-op path;</li>
 *   <li>enabling the listener must NOT change results (INV-E).</li>
 * </ul>
 *
 * <p>Event policy:
 * <ul>
 *   <li>{@link MiningEvent.Kind#PATTERN} events are always emitted while a listener is set;</li>
 *   <li>{@link MiningEvent.Kind#DOP_CHECK}/{@link MiningEvent.Kind#UBDO} step events are emitted
 *       only when {@code detail ≥ 0}, filtered by level (contract 6.4):
 *       {@code 2} = accepted DOP only, {@code 1} = + accepted prune, {@code 0} = + rejected cases.</li>
 * </ul>
 */
public interface StreamAwareEngine extends Engine {

    /**
     * @param listener event sink ({@code null} disables streaming)
     * @param detail   step detail level: {@code < 0} = PATTERN only, {@code 0..2} = contract 6.4
     */
    void setMiningEventListener(MiningEventListener listener, int detail);
}
