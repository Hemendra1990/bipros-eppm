package com.bipros.api.notification;

import com.bipros.api.dprreport.DprReportMetrics;
import com.bipros.api.dprreport.DprReportSnapshot;
import com.bipros.api.dprreport.ReportRequest;
import com.bipros.api.email.EmailMessage;
import com.bipros.api.email.EmailService;
import com.bipros.common.notification.NotificationService;
import com.bipros.project.domain.model.ProjectRole;
import com.bipros.project.domain.model.ProjectTeamMember;
import com.bipros.project.domain.repository.ProjectTeamRepository;
import com.bipros.reporting.application.dto.CapacityUtilizationReport;
import com.bipros.reporting.application.service.CapacityUtilizationReportService;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Supervisor capacity utilization mail (AI Agent sheet, Capacity Utilization row: "Draft and
 * send a mail to his immediate boss/project control team, a summary with key points").
 *
 * <p>Rides the SCHEDULED daily DPR report send (owner decision 2026-08-10) — called by
 * {@code DprReportService} after the main report goes out, so it inherits the report's
 * once-per-day due logic and never fires on on-demand runs. Supervisors = the distinct
 * (userId, name) pairs of the window's approved DPRs. Per user-linked supervisor the figures
 * come from the canonical Capacity Util. engine ({@link CapacityUtilizationReportService}
 * supervisor-scoped build — the same numbers as the tab's supervisor view); name-only
 * supervisors can't be scoped by the engine, so they appear with filed/qty/contribution only.
 *
 * <p>Routing mirrors {@link DprMissingAlertService}: boss = Team-tab reports-to (seat active on
 * the window end), one email + in-app per boss; supervisors without a resolvable boss go to the
 * PROJECT_CONTROL seats (PM fallback) in a single roll-up mail.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupervisorCapacityMailService {

    private static final DecimalFormat QTY = new DecimalFormat("#,##0.##");
    private static final DecimalFormat PCT = new DecimalFormat("#,##0.0");
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0");

    private record SupBlock(UUID userId, String name, DprReportSnapshot.SupervisorPerfRow perf,
                            CapacityUtilizationReport capacity) {}

    private final CapacityUtilizationReportService capacityService;
    private final ProjectTeamRepository teamRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final DprAlertConfig alertConfig;
    private final AgentMailLogService mailLogService;

    public void send(ReportRequest req, DprReportSnapshot snapshot, DprReportMetrics metrics) {
        if (snapshot.dprs() == null || snapshot.dprs().isEmpty()) return;
        UUID projectId = req.projectId();

        // Distinct supervisors of the window's approved DPRs, keyed like the collector keys
        // its SupervisorPerfRow rows (trimmed name, else userId string) so perf figures line
        // up. The snapshot rows carry income/expense (DBS engine) which the metrics copy drops.
        Map<String, DprReportSnapshot.SupervisorPerfRow> perfByName = new LinkedHashMap<>();
        for (var p : snapshot.supervisorPerformance()) perfByName.putIfAbsent(p.name(), p);
        Map<String, SupBlock> blocks = new LinkedHashMap<>();
        for (var d : snapshot.dprs()) {
            String name = d.supervisorName() != null && !d.supervisorName().isBlank()
                ? d.supervisorName().trim()
                : (d.supervisorUserId() != null ? d.supervisorUserId().toString() : "(unnamed)");
            UUID uid = d.supervisorUserId();
            SupBlock existing = blocks.get(name);
            // Prefer the user-linked identity when the same name appears both linked and
            // free-text across the window's DPRs — only a linked id can be engine-scoped.
            if (existing == null || (existing.userId() == null && uid != null)) {
                blocks.put(name, new SupBlock(uid, name, perfByName.get(name),
                    uid == null ? null
                        : capacityService.build(projectId, req.from(), req.to(),
                            "RESOURCE_TYPE", null, uid)));
            }
        }
        if (blocks.isEmpty()) return;
        if ("WHATSAPP".equals(alertConfig.channel())) {
            log.info("[SupervisorCapacityMail] dpr_alert_channel=WHATSAPP but no provider is configured — falling back to email");
        }

        // Boss per supervisor — same seat/active-window convention as DprMissingAlertService.
        LocalDate anchor = req.to() != null ? req.to() : LocalDate.now();
        Map<UUID, ProjectTeamMember> seatByUser = new LinkedHashMap<>();
        for (ProjectTeamMember m : teamRepository.findByProjectId(projectId)) {
            if (m.getUserId() != null && activeOn(m, anchor)) seatByUser.putIfAbsent(m.getUserId(), m);
        }
        Map<UUID, List<SupBlock>> byBoss = new LinkedHashMap<>();
        List<SupBlock> unroutable = new ArrayList<>();
        for (SupBlock blk : blocks.values()) {
            ProjectTeamMember seat = blk.userId() != null ? seatByUser.get(blk.userId()) : null;
            UUID boss = seat != null ? seat.getReportsToUserId() : null;
            if (boss != null) byBoss.computeIfAbsent(boss, k -> new ArrayList<>()).add(blk);
            else unroutable.add(blk);
        }

        String cur = metrics.currencyCode == null ? "" : metrics.currencyCode;
        String link = "/projects/" + projectId + "/capacity-utilization";
        for (Map.Entry<UUID, List<SupBlock>> e : byBoss.entrySet()) {
            User boss = userRepository.findById(e.getKey()).orElse(null);
            if (boss == null) continue;
            deliver(boss, projectId, req.windowLabel(), metrics.projectName, e.getValue(), false, cur, link);
        }
        if (!unroutable.isEmpty()) {
            for (UUID controlId : controlSeatIds(projectId, anchor)) {
                User user = userRepository.findById(controlId).orElse(null);
                if (user == null) continue;
                deliver(user, projectId, req.windowLabel(), metrics.projectName, unroutable, true, cur, link);
            }
        }
        log.info("[SupervisorCapacityMail] project={} window={} bosses={} unroutable={}",
            projectId, req.windowLabel(), byBoss.size(), unroutable.size());
    }

    // ---------------------------------------------------------------- routing helpers
    // (same conventions as DprMissingAlertService — kept local so the tested alert
    // service stays untouched)

    private static boolean activeOn(ProjectTeamMember m, LocalDate date) {
        boolean fromOk = m.getActiveFrom() == null || !m.getActiveFrom().isAfter(date);
        boolean toOk = m.getActiveTo() == null || !m.getActiveTo().isBefore(date);
        return fromOk && toOk;
    }

    private List<UUID> controlSeatIds(UUID projectId, LocalDate date) {
        List<UUID> ids = new ArrayList<>();
        for (ProjectTeamMember m : teamRepository.findByProjectIdAndRole(projectId, ProjectRole.PROJECT_CONTROL)) {
            if (m.getUserId() != null && activeOn(m, date)) ids.add(m.getUserId());
        }
        if (ids.isEmpty()) {
            for (ProjectTeamMember m : teamRepository.findByProjectIdAndRole(projectId, ProjectRole.PM)) {
                if (m.getUserId() != null && activeOn(m, date)) ids.add(m.getUserId());
            }
        }
        return ids;
    }

    // ---------------------------------------------------------------- delivery

    private void deliver(User to, UUID projectId, String windowLabel, String projectName,
                         List<SupBlock> supervisors, boolean rollUp, String cur, String link) {
        String names = supervisors.stream().map(SupBlock::name)
            .reduce((a, b) -> a + ", " + b).orElse("");
        String body = (rollUp
            ? "Capacity utilization summary (%s) for supervisors without a Team-tab reporting line: %s."
            : "Capacity utilization summary (%s) for your supervisors: %s.")
            .formatted(windowLabel, names);
        String subject = "Supervisor capacity utilization — %s · %s".formatted(projectName, windowLabel);
        try {
            notificationService.create(to.getId(), DprNotificationType.SUPERVISOR_CAPACITY_SUMMARY,
                "Supervisor capacity utilization", body, link, projectId, null);
            mailLogService.log(deliveryRow(projectId, AgentMailLog.CH_IN_APP, to,
                "Supervisor capacity utilization", null, "SENT", null));
        } catch (Exception ex) {
            log.warn("[SupervisorCapacityMail] in-app notification failed for {}: {}", to.getId(), ex.getMessage());
        }
        if (to.getEmail() == null || to.getEmail().isBlank()) {
            log.info("[SupervisorCapacityMail] email skipped — user {} has no email", to.getId());
            mailLogService.log(deliveryRow(projectId, AgentMailLog.CH_EMAIL, to, subject, null,
                AgentMailLog.STATUS_SKIPPED, "user has no email"));
            return;
        }
        StringBuilder blocks = new StringBuilder();
        for (SupBlock blk : supervisors) blocks.append(blockHtml(blk, cur, link));
        String intro = rollUp
            ? "Utilization summary for supervisors with <b>no reporting manager on the Team tab</b> — routed to project control:"
            : "Utilization summary for the supervisors reporting to you:";
        String html = """
            <div style="font-family:Arial,Helvetica,sans-serif;max-width:640px;margin:0 auto;border:1px solid #e5e0d8;border-radius:8px;overflow:hidden">
              <div style="background:#1F3A5F;color:#fff;padding:14px 20px;font-size:16px;font-weight:bold">Supervisor capacity utilization — %s</div>
              <div style="padding:20px;color:#2b2b2b;font-size:14px;line-height:1.6">
                <p>%s</p>
                %s
                <p style="color:#6b6b6b;font-size:12px">Efficiency = output vs the productivity norms per resource-day — the Capacity Util. tab's own engine. Figures cover the window %s.</p>
                <p><a href="%s" style="display:inline-block;background:#C9A227;color:#fff;text-decoration:none;padding:10px 18px;border-radius:6px;font-weight:bold">Open Capacity Utilization</a></p>
              </div>
            </div>
            """.formatted(
                HtmlUtils.htmlEscape(projectName != null ? projectName : "Project"),
                intro, blocks,
                HtmlUtils.htmlEscape(windowLabel != null ? windowLabel : ""),
                alertConfig.appBaseUrl() + link);
        EmailService.SendResult result =
            emailService.send(new EmailMessage(List.of(to.getEmail()), subject, html, null, null));
        mailLogService.log(deliveryRow(projectId, AgentMailLog.CH_EMAIL, to, subject, html,
            result.name(), null));
    }

    private static AgentMailLog deliveryRow(UUID projectId, String channel, User to, String subject,
                                            String bodyHtml, String status, String detail) {
        AgentMailLog row = new AgentMailLog();
        row.setProjectId(projectId);
        row.setCategory(AgentMailLog.CAT_SUPERVISOR_SUMMARY);
        row.setChannel(channel);
        row.setRecipientUserId(to.getId());
        row.setRecipientName(displayName(to));
        row.setRecipientEmail(to.getEmail());
        row.setSubject(subject);
        row.setBodyHtml(bodyHtml);
        row.setStatus(status);
        row.setDetail(detail);
        return row;
    }

    private static String displayName(User u) {
        String full = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
            + (u.getLastName() == null ? "" : u.getLastName())).trim();
        return full.isBlank() ? u.getUsername() : full;
    }

    private String blockHtml(SupBlock blk, String cur, String link) {
        StringBuilder b = new StringBuilder();
        b.append("<div style='border:1px solid #e5e0d8;border-radius:6px;padding:12px 16px;margin:0 0 12px'>")
         .append("<div style='font-weight:bold;font-size:14px;margin-bottom:6px'>");
        if (blk.userId() != null) {
            b.append("<a href='").append(alertConfig.appBaseUrl()).append(link)
             .append("?supervisorUserId=").append(blk.userId())
             .append("' style='color:#1F3A5F;text-decoration:none'>")
             .append(HtmlUtils.htmlEscape(blk.name())).append("</a>");
        } else {
            b.append(HtmlUtils.htmlEscape(blk.name()));
        }
        b.append("</div><ul style='margin:0;padding-left:18px;font-size:13px'>");
        if (blk.perf() != null) {
            b.append("<li>DPRs filed: <b>").append(blk.perf().filedWindow())
             .append("</b> &#183; workdone qty: <b>").append(QTY.format(blk.perf().qtyWindow())).append("</b></li>");
            // DBS line (AI Agent sheet DBS row: mail to the immediate boss, key points) —
            // the supervisor's income / expense / contribution from the DBS engine.
            if (blk.perf().income() != null || blk.perf().expense() != null) {
                BigDecimal income = blk.perf().income() == null ? BigDecimal.ZERO : blk.perf().income();
                BigDecimal expense = blk.perf().expense() == null ? BigDecimal.ZERO : blk.perf().expense();
                BigDecimal contribution = blk.perf().contribution() != null
                    ? blk.perf().contribution() : income.subtract(expense);
                b.append("<li>DBS: income <b>").append(MONEY.format(income))
                 .append("</b> &#183; expense <b>").append(MONEY.format(expense))
                 .append("</b> &#183; contribution <b style='color:")
                 .append(contribution.signum() >= 0 ? "#2E7D4F" : "#B3372E").append("'>")
                 .append(MONEY.format(contribution)).append(" ").append(HtmlUtils.htmlEscape(cur))
                 .append("</b></li>");
            }
        }
        if (blk.capacity() != null) {
            sideLine(b, "Manpower", blk.capacity().manpower(), cur);
            sideLine(b, "Equipment", blk.capacity().equipment(), cur);
            worstRoles(b, blk.capacity());
        } else {
            b.append("<li style='color:#6b6b6b'>Not linked to a user account &#8212; per-resource utilization can't be attributed; see the DPR figures above.</li>");
        }
        b.append("</ul></div>");
        return b.toString();
    }

    private static void sideLine(StringBuilder b, String label,
                                 CapacityUtilizationReport.Section section, String cur) {
        if (section == null || section.totalCumulative() == null) return;
        var t = section.totalCumulative();
        String eff = t.utilizationPct() == null ? "&#8212;" : PCT.format(t.utilizationPct()) + "%";
        b.append("<li>").append(label).append(": budget <b>").append(fmt(t.budgetDays()))
         .append("</b> &#247; counted <b>").append(fmt(t.actualDays()))
         .append("</b> days &#183; eff <b>").append(eff).append("</b>");
        if (t.costImplication() != null && t.costImplication().signum() != 0) {
            boolean over = t.costImplication().signum() > 0;
            b.append(" &#183; <span style='color:").append(over ? "#B3372E" : "#2E7D4F").append("'>")
             .append(over ? "overrun " : "saved ")
             .append(MONEY.format(t.costImplication().abs())).append(" ")
             .append(HtmlUtils.htmlEscape(cur)).append("</span>");
        }
        b.append("</li>");
    }

    /** Up to 3 roles below the 90 % band, worst cost impact first — the "key points". */
    private static void worstRoles(StringBuilder b, CapacityUtilizationReport report) {
        List<CapacityUtilizationReport.RoleRow> rows = new ArrayList<>();
        if (report.manpower() != null && report.manpower().rows() != null) rows.addAll(report.manpower().rows());
        if (report.equipment() != null && report.equipment().rows() != null) rows.addAll(report.equipment().rows());
        List<CapacityUtilizationReport.RoleRow> worst = rows.stream()
            .filter(r -> r.cumulative() != null && r.cumulative().utilizationPct() != null
                && r.cumulative().utilizationPct().doubleValue() < 90)
            .sorted(Comparator.comparing(
                (CapacityUtilizationReport.RoleRow r) ->
                    r.cumulative().costImplication() == null ? BigDecimal.ZERO : r.cumulative().costImplication())
                .reversed())
            .limit(3)
            .toList();
        if (worst.isEmpty()) return;
        b.append("<li>Below the 90&#160;% band: ");
        for (int i = 0; i < worst.size(); i++) {
            var r = worst.get(i);
            if (i > 0) b.append(", ");
            b.append(HtmlUtils.htmlEscape(r.roleName() == null ? "(role)" : r.roleName()))
             .append(" <b>").append(PCT.format(r.cumulative().utilizationPct())).append("%</b>");
        }
        b.append("</li>");
    }

    private static String fmt(BigDecimal v) {
        return v == null ? "&#8212;" : QTY.format(v);
    }
}
