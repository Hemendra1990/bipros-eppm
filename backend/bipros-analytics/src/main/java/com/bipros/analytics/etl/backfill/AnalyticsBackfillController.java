package com.bipros.analytics.etl.backfill;

import com.bipros.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsBackfillController {

    private final AnalyticsBackfillService backfillService;

    @PostMapping("/backfill")
    public ResponseEntity<ApiResponse<AnalyticsBackfillService.BackfillReport>> backfill(
            @RequestParam(defaultValue = "all") String fact,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) UUID projectId) {

        log.info("Admin backfill request: fact={} from={} to={} projectId={}", fact, from, to, projectId);

        AnalyticsBackfillService.BackfillReport report = switch (fact) {
            case "dpr" -> new AnalyticsBackfillService.BackfillReport(
                    backfillService.backfillDpr(from, to, projectId), 0, 0, 0, 0);
            case "activity" -> new AnalyticsBackfillService.BackfillReport(
                    0, backfillService.backfillActivityProgress(from, to, projectId), 0, 0, 0);
            case "cost" -> new AnalyticsBackfillService.BackfillReport(
                    0, 0, backfillService.backfillCost(from, to, projectId), 0, 0);
            case "evm" -> new AnalyticsBackfillService.BackfillReport(
                    0, 0, 0, backfillService.backfillEvm(from, to, projectId), 0);
            case "risk" -> new AnalyticsBackfillService.BackfillReport(
                    0, 0, 0, 0, backfillService.backfillRiskSnapshot(from, to, projectId));
            default -> backfillService.backfillAll(from, to, projectId);
        };

        return ResponseEntity.ok(ApiResponse.ok(report));
    }
}
