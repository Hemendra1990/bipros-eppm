package com.bipros.api.dprreport;

import com.bipros.project.domain.model.ProjectRole;
import com.bipros.project.domain.model.ProjectTeamMember;
import com.bipros.project.domain.repository.ProjectTeamRepository;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Report recipients. Resolution chain (owner decision 2026-08-05, matches the approved preview):
 * explicit override ({@code dpr_report_recipients_override} or the request) → the project's
 * <b>PM + PROJECT_CONTROL seats</b> from {@code project_team} → enabled ADMIN users as the last
 * resort so a scheduled report never vanishes silently. (The branch resolved PM+CM; changed to
 * PM+Project Control per the client's "mail to Project control team" requirement.)
 */
@Service
@RequiredArgsConstructor
public class DprReportRecipientResolver {
    private static final List<ProjectRole> DEFAULT_SEATS = List.of(ProjectRole.PM, ProjectRole.PROJECT_CONTROL);

    private final ProjectTeamRepository teamRepository;
    private final UserRepository userRepository;

    private Set<UUID> defaultSeatIds(UUID projectId) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (ProjectRole role : DEFAULT_SEATS) {
            for (ProjectTeamMember m : teamRepository.findByProjectIdAndRole(projectId, role)) {
                if (m.getUserId() != null) {
                    ids.add(m.getUserId());
                }
            }
        }
        return ids;
    }

    public List<String> resolveEmails(ReportRequest r) {
        if (r.emailRecipients() != null && !r.emailRecipients().isEmpty()) return r.emailRecipients();
        if (!"SCHEDULED".equals(r.trigger())) return List.of();
        List<String> emails = userRepository.findAllById(defaultSeatIds(r.projectId())).stream()
            .filter(u -> u.isEnabled() && u.getEmail() != null && !u.getEmail().isBlank())
            .map(User::getEmail).distinct().toList();
        if (!emails.isEmpty()) return emails;
        return userRepository.findByRoleNamesAndEnabled(List.of("ADMIN")).stream()
            .map(User::getEmail).filter(Objects::nonNull).distinct().toList();
    }

    /** Same PM+Project-Control-else-admin resolution, returning internal user ids for in-app
     *  notifications (external override emails from {@link #resolveEmails} have no user id to
     *  notify). On-demand runs with no explicit recipients notify only the requester, which
     *  {@code DprReportService.notifyRecipients} already handles separately. */
    public List<UUID> resolveRecipientUserIds(ReportRequest r) {
        if (!"SCHEDULED".equals(r.trigger())) return List.of();
        List<UUID> resolved = userRepository.findAllById(defaultSeatIds(r.projectId())).stream()
            .filter(User::isEnabled)
            .map(User::getId).distinct().toList();
        if (!resolved.isEmpty()) return resolved;
        return userRepository.findByRoleNamesAndEnabled(List.of("ADMIN")).stream()
            .map(User::getId).distinct().toList();
    }
}
