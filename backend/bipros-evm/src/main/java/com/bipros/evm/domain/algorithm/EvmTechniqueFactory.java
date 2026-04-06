package com.bipros.evm.domain.algorithm;

import com.bipros.evm.domain.entity.EvmTechnique;

import java.util.EnumMap;
import java.util.Map;

public final class EvmTechniqueFactory {

    private static final Map<EvmTechnique, EvmTechniqueStrategy> STRATEGIES = new EnumMap<>(EvmTechnique.class);

    static {
        STRATEGIES.put(EvmTechnique.ACTIVITY_PERCENT_COMPLETE, new ActivityPercentCompleteStrategy());
        STRATEGIES.put(EvmTechnique.ZERO_ONE_HUNDRED, new ZeroOneHundredStrategy());
        STRATEGIES.put(EvmTechnique.FIFTY_FIFTY, new FiftyFiftyStrategy());
        STRATEGIES.put(EvmTechnique.WEIGHTED_STEPS, new WeightedStepsStrategy());
        STRATEGIES.put(EvmTechnique.LEVEL_OF_EFFORT, new LevelOfEffortStrategy());
    }

    private EvmTechniqueFactory() {}

    public static EvmTechniqueStrategy getStrategy(EvmTechnique technique) {
        var strategy = STRATEGIES.get(technique);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown EVM technique: " + technique);
        }
        return strategy;
    }
}
