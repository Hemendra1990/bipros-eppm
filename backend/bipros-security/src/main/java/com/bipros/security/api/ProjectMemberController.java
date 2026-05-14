package com.bipros.security.api;

import com.bipros.common.dto.ApiResponse;
import com.bipros.security.application.dto.UpdateProjectMemberRequest;
import com.bipros.security.application.service.CurrentUserService;
import com.bipros.security.application.service.ProjectMemberService;
import com.bipros.security.domain.model.ProjectMember;
import com.bipros.security.domain.model.ProjectMemberRole;
import com.bipros.security.domain.repository.ProjectMemberRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Manage per-project role assignments. Callers need {@code PROJECT_MEMBER.MANAGE} on the project
 * to assign / revoke members and {@code PROJECT_MEMBER.READ} to list them. ADMIN is short-circuited
 * by {@code CustomPermissionEvaluator}, so no explicit ADMIN escape hatch is required.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET    /v1/projects/{projectId}/members}        — list members</li>
 *   <li>{@code POST   /v1/projects/{projectId}/members}        — assign a role</li>
 *   <li>{@code PUT    /v1/projects/{projectId}/members/{id}}   — change an existing member's role</li>
 *   <li>{@code DELETE /v1/projects/{projectId}/members/{id}}   — revoke an assignment</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/v1/projects/{projectId}/members")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectMemberService projectMemberService;
    private final CurrentUserService currentUserService;

    public record AssignRequest(UUID userId, ProjectMemberRole role) {}

    public record MemberDto(UUID id, UUID userId, UUID projectId, ProjectMemberRole role, UUID grantedBy) {
        public static MemberDto from(ProjectMember m) {
            return new MemberDto(m.getId(), m.getUserId(), m.getProjectId(), m.getProjectRole(), m.getGrantedBy());
        }
    }

    @GetMapping
    @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'PROJECT_MEMBER.READ')")
    public ResponseEntity<ApiResponse<List<MemberDto>>> list(@PathVariable UUID projectId) {
        List<MemberDto> members = projectMemberRepository.findByProjectId(projectId).stream()
                .map(MemberDto::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(members));
    }

    @PostMapping
    @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'PROJECT_MEMBER.MANAGE')")
    @Transactional
    public ResponseEntity<ApiResponse<MemberDto>> assign(
            @PathVariable UUID projectId,
            @RequestBody AssignRequest request) {
        if (request.userId() == null || request.role() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("INVALID_REQUEST", "userId and role are required"));
        }
        if (projectMemberRepository.existsByUserIdAndProjectIdAndProjectRole(
                request.userId(), projectId, request.role())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("DUPLICATE", "User already has this role on the project"));
        }
        ProjectMember member = new ProjectMember(
                request.userId(), projectId, request.role(), currentUserService.getCurrentUserId());
        ProjectMember saved = projectMemberRepository.save(member);
        log.info("ProjectMember assigned: userId={} projectId={} role={} by={}",
                request.userId(), projectId, request.role(), currentUserService.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(MemberDto.from(saved)));
    }

    @PutMapping("/{memberId}")
    @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'PROJECT_MEMBER.MANAGE')")
    public ResponseEntity<ApiResponse<MemberDto>> update(
            @PathVariable UUID projectId,
            @PathVariable UUID memberId,
            @Valid @RequestBody UpdateProjectMemberRequest request) {
        MemberDto updated = projectMemberService.updateMemberRole(
                projectId, memberId, request.projectRole());
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    @DeleteMapping("/{memberId}")
    @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'PROJECT_MEMBER.MANAGE')")
    @Transactional
    public ResponseEntity<Void> revoke(@PathVariable UUID projectId, @PathVariable UUID memberId) {
        projectMemberRepository.findById(memberId).ifPresent(m -> {
            if (!projectId.equals(m.getProjectId())) {
                // path/projectId mismatch — treat as not-found rather than leak existence
                return;
            }
            projectMemberRepository.delete(m);
            log.info("ProjectMember revoked: id={} projectId={} by={}",
                    memberId, projectId, currentUserService.getCurrentUserId());
        });
        return ResponseEntity.noContent().build();
    }
}
