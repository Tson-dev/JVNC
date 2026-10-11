package dhopm.cli;

import dhopm.common.config.MiningConfig;
import dhopm.common.contract.Engine;

/** Creates a fresh engine instance for one mining job (one instance per job — engines are stateful). */
@FunctionalInterface
public interface EngineFactory {

    Engine create(MiningConfig config);
}
