package com.bipros.api.notification;

import com.bipros.api.email.EmailMessage;
import com.bipros.api.email.EmailService;
import com.bipros.common.notification.NotificationService;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.ProjectRole;
import com.bipros.project.domain.model.ProjectTeamMember;
import com.bipros.project.domain.repository.ProjectTeamRepository;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Daily missing-DPR alert (AI Agent sheet, DPR row: "Email trigger … to the his immediate boss
 * &amp; project control team for those who did not submit the DPR").
 *
 * <p>Expected set (owner decision 2026-08-10): the DISTINCT supervisors of IN_PROGRESS
 * activities — if you supervise live work, you are expected to file daily. Submitted set: the
 * checked day's DPRs with approval_status SUBMITTED/APPROVED/REJECTED (a rejected DPR WAS
 * submitted; a draft was not). Matching is user-id first, else case-insensitive trimmed name —
 * the Khasab free-text-supervisor reality.
 *
 * <p>Routing: each missing supervisor's boss = their Team-tab reports-to (seat active on the
 * checked date). One email per boss listing their missing supervisors, plus one summary to the
 * PROJECT_CONTROL seats (PM seats as fallback) listing everyone — including supervisors that
 * could not be routed to a boss, so the gap is visible rather than silent. In-app notifications
 * mirror every email. Delivery is EMAIL; a WHATSAPP channel value falls back to email until a
 * provider is configured.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DprMissingAlertService {

    record MissingSupervisor(UUID userId, String name) {}

    @PersistenceContext private EntityManager em;

    private final ProjectTeamRepository teamRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final DprAlertConfig alertConfig;
    private final DprMissingAlertRunRepository runRepository;
    private final AgentMailLogService mailLogService;

    @Transactional
    public void recordSkippedNonWorkingDay(UUID projectId, LocalDate target) {
        saveRun(projectId, target, 0, 0, true);
    }

    @Transactional
    public void runForProject(Project project, LocalDate target) {
        UUID projectId = project.getId();
        List<MissingSupervisor> missing = findMissing(projectId, target);
        if (missing.isEmpty()) {
            saveRun(projectId, target, 0, 0, false);
            return;
        }
        if ("WHATSAPP".equals(alertConfig.channel())) {
            log.info("[DprMissingAlert] dpr_alert_channel=WHATSAPP but no provider is configured — falling back to email");
        }

        // Boss per missing supervisor from the Team tab (seat active on the checked date).
        Map<UUID, ProjectTeamMember> seatByUser = new LinkedHashMap<>();
        for (ProjectTeamMember m : teamRepository.findByProjectId(projectId)) {
            if (m.getUserId() != null && activeOn(m, target)) {
                seatByUser.putIfAbsent(m.getUserId(), m);
            }
        }
        Map<UUID, List<MissingSupervisor>> byBoss = new LinkedHashMap<>();
        List<MissingSupervisor> unroutable = new ArrayList<>();
        for (MissingSupervisor ms : missing) {
            ProjectTeamMember seat = ms.userId() != null ? seatByUser.get(ms.userId()) : null;
            UUID boss = seat != null ? seat.getReportsToUserId() : null;
            if (boss != null) {
                byBoss.computeIfAbsent(boss, k -> new ArrayList<>()).add(ms);
            } else {
                unroutable.add(ms);
            }
        }

        String link = "/projects/" + projectId + "/dpr";
        String href = alertConfig.appBaseUrl() + link;
        int emailsSent = 0;

        for (Map.Entry<UUID, List<MissingSupervisor>> e : byBoss.entrySet()) {
            User boss = userRepository.findById(e.getKey()).orElse(null);
            if (boss == null) continue;
            String names = joinNames(e.getValue());
            String body = "No DPR was submitted for %s by: %s. Please follow up so the missing report(s) are filed.".formatted(target, names);
            notify(boss.getId(), projectId, body, link);
            if (sendEmail(boss, project, target, e.getValue(), List.of(), href)) emailsSent++;
        }

        // Project-control summary — everyone missing, with the unroutable supervisors called out.
        List<UUID> controlIds = controlSeatIds(projectId, target);
        for (UUID controlId : controlIds) {
            User user = userRepository.findById(controlId).orElse(null);
            if (user == null) continue;
            String body = "No DPR was submitted for %s by: %s.".formatted(target, joinNames(missing));
            notify(user.getId(), projectId, body, link);
            if (sendEmail(user, project, target, missing, unroutable, href)) emailsSent++;
        }

        saveRun(projectId, target, missing.size(), emailsSent, false);
        log.info("[DprMissingAlert] project={} date={} missing={} emails={}", projectId, target, missing.size(), emailsSent);
    }

    // ---------------------------------------------------------------- detection

    @SuppressWarnings("unchecked")
    List<MissingSupervisor> findMissing(UUID projectId, LocalDate target) {
        List<Object[]> expected = em.createNativeQuery(
                "SELECT DISTINCT a.supervisor_user_id, a.supervisor_user_name "
                    + "FROM activity.activities a "
                    + "WHERE a.project_id = :pid AND a.status = 'IN_PROGRESS' "
                    + "  AND (a.supervisor_user_id IS NOT NULL "
                    + "       OR (a.supervisor_user_name IS NOT NULL AND btrim(a.supervisor_user_name) <> ''))")
                .setParameter("pid", projectId)
                .getResultList();
        if (expected.isEmpty()) return List.of();

        List<Object[]> submitted = em.createNativeQuery(
                "SELECT DISTINCT d.supervisor_user_id, d.supervisor_name "
                    + "FROM project.daily_progress_reports d "
                    + "WHERE d.project_id = :pid AND d.report_date = :target "
                    + "  AND d.approval_status IN ('SUBMITTED', 'APPROVED', 'REJECTED')")
                .setParameter("pid", projectId)
                .setParameter("target", target)
                .getResultList();

        Set<String> submittedKeys = new HashSet<>();
        for (Object[] r : submitted) {
            if (r[0] != null) submittedKeys.add("id:" + r[0]);
            if (r[1] != null && !((String) r[1]).isBlank()) submittedKeys.add(nameKey((String) r[1]));
        }

        Set<String> seen = new LinkedHashSet<>();
        List<MissingSupervisor> missing = new ArrayList<>();
        for (Object[] r : expected) {
            UUID userId = (UUID) r[0];
            String name = (String) r[1];
            boolean filed = (userId != null && submittedKeys.contains("id:" + userId))
                    || (name != null && !name.isBlank() && submittedKeys.contains(nameKey(name)));
            if (filed) continue;
            String dedupKey = userId != null ? "id:" + userId : nameKey(name);
            if (seen.add(dedupKey)) {
                missing.add(new MissingSupervisor(userId, name != null && !name.isBlank() ? name : String.valueOf(userId)));
            }
        }
        return missing;
    }

    private static String nameKey(String name) {
        return "nm:" + name.trim().toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- delivery helpers

    private static boolean activeOn(ProjectTeamMember m, LocalDate date) {
        boolean fromOk = m.getActiveFrom() == null || !m.getActiveFrom().isAfter(date);
        boolean toOk = m.getActiveTo() == null || !m.getActiveTo().isBefore(date);
        return fromOk && toOk;
    }

    private List<UUID> controlSeatIds(UUID projectId, LocalDate target) {
        List<UUID> ids = new ArrayList<>();
        for (ProjectTeamMember m : teamRepository.findByProjectIdAndRole(projectId, ProjectRole.PROJECT_CONTROL)) {
            if (m.getUserId() != null && activeOn(m, target)) ids.add(m.getUserId());
        }
        if (ids.isEmpty()) {
            for (ProjectTeamMember m : teamRepository.findByProjectIdAndRole(projectId, ProjectRole.PM)) {
                if (m.getUserId() != null && activeOn(m, target)) ids.add(m.getUserId());
            }
        }
        return ids;
    }

    private void notify(UUID userId, UUID projectId, String body, String link) {
        try {
            notificationService.create(userId, DprNotificationType.DPR_MISSING_ALERT,
                    "Missing DPR submissions", body, link, projectId, null);
            mailLogService.log(deliveryRow(projectId, AgentMailLog.CH_IN_APP, userId, null, null,
                    "Missing DPR submissions", null, "SENT", null));
        } catch (Exception ex) {
            log.warn("[DprMissingAlert] in-app notification failed for {}: {}", userId, ex.getMessage());
        }
    }

    private static AgentMailLog deliveryRow(UUID projectId, String channel, UUID userId, String name,
                                            String email, String subject, String bodyHtml,
                                            String status, String detail) {
        AgentMailLog row = new AgentMailLog();
        row.setProjectId(projectId);
        row.setCategory(AgentMailLog.CAT_MISSING_DPR);
        row.setChannel(channel);
        row.setRecipientUserId(userId);
        row.setRecipientName(name);
        row.setRecipientEmail(email);
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

    private boolean sendEmail(User to, Project project, LocalDate target,
                              List<MissingSupervisor> missing, List<MissingSupervisor> unroutable, String href) {
        if (to.getEmail() == null || to.getEmail().isBlank()) {
            log.info("[DprMissingAlert] email skipped — user {} has no email", to.getId());
            mailLogService.log(deliveryRow(project.getId(), AgentMailLog.CH_EMAIL, to.getId(),
                    displayName(to), null, "Missing DPRs — " + target, null,
                    AgentMailLog.STATUS_SKIPPED, "user has no email"));
            return false;
        }
        StringBuilder rows = new StringBuilder();
        for (MissingSupervisor ms : missing) {
            rows.append("<li>").append(HtmlUtils.htmlEscape(ms.name())).append("</li>");
        }
        String unroutableBlock = "";
        if (!unroutable.isEmpty()) {
            StringBuilder u = new StringBuilder();
            for (MissingSupervisor ms : unroutable) {
                u.append("<li>").append(HtmlUtils.htmlEscape(ms.name())).append("</li>");
            }
            unroutableBlock = """
                <p style="margin-top:14px"><b>No reporting manager could be resolved for:</b></p>
                <ul>%s</ul>
                <p style="color:#6b6b6b;font-size:12px">These supervisors are not linked to a user with a Team-tab reporting line — only this summary covers them.</p>
                """.formatted(u);
        }
        String html = """
            <div style="font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;border:1px solid #e5e0d8;border-radius:8px;overflow:hidden">
              <div style="background:#1F3A5F;color:#fff;padding:14px 20px;font-size:16px;font-weight:bold">Missing DPR submissions — %s</div>
              <div style="padding:20px;color:#2b2b2b;font-size:14px;line-height:1.6">
                <p>The following supervisor(s) did not submit a Daily Progress Report for <b>%s</b>:</p>
                <ul>%s</ul>
                %s
                <p><a href="%s" style="display:inline-block;background:#C9A227;color:#fff;text-decoration:none;padding:10px 18px;border-radius:6px;font-weight:bold">Open the DPR log</a></p>
              </div>
            </div>
            """.formatted(
                HtmlUtils.htmlEscape(project.getName() != null ? project.getName() : "Project"),
                target, rows, unroutableBlock, href);
        String subject = "Missing DPRs — %s · %s".formatted(project.getName(), target);
        EmailService.SendResult result = emailService.send(new EmailMessage(List.of(to.getEmail()), subject, html, null, null));
        mailLogService.log(deliveryRow(project.getId(), AgentMailLog.CH_EMAIL, to.getId(),
                displayName(to), to.getEmail(), subject, html, result.name(), null));
        return result == EmailService.SendResult.SENT;
    }

    private static String joinNames(List<MissingSupervisor> list) {
        StringBuilder sb = new StringBuilder();
        for (MissingSupervisor ms : list) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(ms.name());
        }
        return sb.toString();
    }

    private void saveRun(UUID projectId, LocalDate target, int missingCount, int emailsSent, boolean skipped) {
        DprMissingAlertRun run = new DprMissingAlertRun();
        run.setProjectId(projectId);
        run.setAlertDate(target);
        run.setMissingCount(missingCount);
        run.setEmailsSent(emailsSent);
        run.setSkippedNonWorking(skipped);
        run.setGeneratedAt(Instant.now());
        runRepository.save(run);
    }
}
