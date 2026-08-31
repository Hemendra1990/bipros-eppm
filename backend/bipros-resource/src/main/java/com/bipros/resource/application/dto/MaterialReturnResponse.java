package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.MaterialReturn;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MaterialReturnResponse(
    UUID id,
    UUID projectId,
    UUID materialIssueId,
    UUID materialId,
    LocalDate returnDate,
    BigDecimal quantity,
    MaterialReturn.ReturnCondition condition,
    UUID returnedByUserId,
    UUID receivedByUserId,
    String remarks
) {
    public static MaterialReturnResponse from(MaterialReturn r) {
        return new MaterialReturnResponse(
            r.getId(), r.getProjectId(), r.getMaterialIssueId(), r.getMaterialId(),
            r.getReturnDate(), r.getQuantity(), r.getCondition(),
            r.getReturnedByUserId(), r.getReceivedByUserId(), r.getRemarks()
        );
    }
}
