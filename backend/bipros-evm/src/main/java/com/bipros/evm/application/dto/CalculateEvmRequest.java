package com.bipros.evm.application.dto;

import com.bipros.evm.domain.entity.EtcMethod;
import com.bipros.evm.domain.entity.EvmTechnique;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CalculateEvmRequest(
        @NotNull(message = "EVM technique is required")
        EvmTechnique technique,

        @NotNull(message = "ETC method is required")
        EtcMethod etcMethod
) {
}
