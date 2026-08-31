package com.bipros.api.dprreport;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReportRequest(
    UUID projectId,
    LocalDate from,
    LocalDate to,
    String windowLabel,
    List<UUID> supervisorUserIds,   // nullable/empty = all
    List<UUID> activityIds,         // nullable/empty = all
    List<UUID> boqItemIds,          // nullable/empty = all
    String trigger,
    UUID requestedByUserId,
    List<String> emailRecipients,   // explicit override; empty = resolve PM+CM
    boolean deliverInApp
) {}
