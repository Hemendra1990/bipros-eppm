package com.bipros.api.dprreport;

import com.bipros.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * On-demand trigger for the Daily DPR Report — the SAME engine ({@link DprReportService}) the
 * {@link DprReportScheduler} runs, but fired immediately so it can be tested without waiting for
 * the 15-minute tick and without the cadence blocking re-runs (this uses {@code trigger =
 * ON_DEMAND}, so it never writes a {@code SCHEDULED} row). Emails only the given address (or the
 * configured {@code dpr_report_recipients_override}) and posts no in-app notifications. Returns
 * the delivery outcome so a tester can see SENT / PREVIEW / FAILED right away (PREVIEW = SMTP not
 * configured).
 *
 * <p>Port note (2026-08-05): rewired off the retired command-center service; the window comes
 * from {@link DprReportWindow#ofPreset} presets (LAST_1_DAY, LAST_7_DAYS, LAST_30_DAYS,
 * THIS_MONTH, PROJECT_TO_DATE).
 */
@RestController
@RequestMapping("/v1/projects/{projectId}/dpr-report")
@RequiredArgsConstructor
public class DprReportTestController {

    private final DprReportService reportService;
    private final DprReportConfig config;

    @PostMapping("/test-send")
    @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'REPORT.READ')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testSend(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String window) {

        DprReportWindow w = DprReportWindow.ofPreset(parsePreset(window), LocalDate.now(config.zone()), null);
        List<String> recipients = (email != null && !email.isBlank())
                ? List.of(email.trim())
                : config.recipientOverrideEmails();

        DprAgentReport report = reportService.generate(new ReportRequest(
                projectId, w.from(), w.to(), w.label(),
                null, null, null,
                "ON_DEMAND", null,
                recipients, false));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reportId", report.getId());
        out.put("status", report.getStatus());                 // SUCCESS | PARTIAL | FAILED
        out.put("deliveryStatus", report.getDeliveryStatus()); // SENT | PREVIEW | FAILED
        out.put("deliveredTo", report.getDeliveredTo());
        out.put("window", w.label());
        out.put("errorMessage", report.getErrorMessage());
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    private DprReportConfig.WindowPreset parsePreset(String w) {
        if (w == null || w.isBlank()) {
            return config.window();
        }
        try {
            return DprReportConfig.WindowPreset.valueOf(w.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException notAPreset) {
            return config.window();
        }
    }
}
