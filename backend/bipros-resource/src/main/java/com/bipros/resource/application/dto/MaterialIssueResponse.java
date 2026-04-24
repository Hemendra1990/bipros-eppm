package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.MaterialIssue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MaterialIssueResponse(
    UUID id,
    UUID projectId,
    String challanNumber,
    UUID materialId,
    LocalDate issueDate,
    BigDecimal quantity,
    UUID issuedToUserId,
    UUID stretchId,
    UUID activityId,
    BigDecimal wastageQuantity,
    String remarks
) {
    public static MaterialIssueResponse from(MaterialIssue i) {
        return new MaterialIssueResponse(
            i.getId(), i.getProjectId(), i.getChallanNumber(), i.getMaterialId(),
            i.getIssueDate(), i.getQuantity(), i.getIssuedToUserId(),
            i.getStretchId(), i.getActivityId(), i.getWastageQuantity(), i.getRemarks()
        );
    }
}
