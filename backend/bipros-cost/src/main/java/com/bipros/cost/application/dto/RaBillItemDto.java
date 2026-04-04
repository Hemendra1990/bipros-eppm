package com.bipros.cost.application.dto;

import com.bipros.cost.domain.entity.RaBillItem;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class RaBillItemDto {
    private UUID id;
    private UUID raBillId;
    private String itemCode;
    private String description;
    private String unit;
    private BigDecimal rate;
    private Double previousQuantity;
    private Double currentQuantity;
    private Double cumulativeQuantity;
    private BigDecimal amount;

    public static RaBillItemDto from(RaBillItem entity) {
        return RaBillItemDto.builder()
                .id(entity.getId())
                .raBillId(entity.getRaBillId())
                .itemCode(entity.getItemCode())
                .description(entity.getDescription())
                .unit(entity.getUnit())
                .rate(entity.getRate())
                .previousQuantity(entity.getPreviousQuantity())
                .currentQuantity(entity.getCurrentQuantity())
                .cumulativeQuantity(entity.getCumulativeQuantity())
                .amount(entity.getAmount())
                .build();
    }
}
