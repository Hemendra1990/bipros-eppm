package com.bipros.api.integration.security;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.project.domain.model.EpsNode;
import com.bipros.project.domain.model.ObsNode;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.ProjectStatus;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.EpsNodeRepository;
import com.bipros.project.domain.repository.ObsNodeRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import com.bipros.security.domain.model.AccessLevel;
import com.bipros.security.domain.model.ProjectMember;
import com.bipros.security.domain.model.ProjectMemberRole;
import com.bipros.security.domain.model.Role;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.model.UserObsAssignment;
import com.bipros.security.domain.model.UserRole;
import com.bipros.security.domain.repository.ProjectMemberRepository;
import com.bipros.security.domain.repository.RoleRepository;
import com.bipros.security.domain.repository.UserObsAssignmentRepository;
import com.bipros.security.domain.repository.UserRepository;
import com.bipros.security.domain.repository.UserRoleRepository;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Idempotently seeds the security-IT world. Call {@link #ensureSeeded()} from {@code @BeforeAll}
 * — re-running is a no-op so tests can co-exist with the {@code DataSeeder} that runs at startup.
 *
 * <p>Topology:
 * <pre>
 *   Roles      : ADMIN, FINANCE, PROJECT_MANAGER, TEAM_MEMBER, VIEWER (already seeded by DataSeeder)
 *
 *   OBS tree   :  obs.root
 *                 ├── obs.left   ← projectAlpha hangs here
 *                 └── obs.right  ← projectBeta hangs here
 *
 *   EPS tree   :  eps.root
 *                 └── eps.eppm
 *
 *   Projects   : projectAlpha (obs.left)   projectBeta (obs.right)
 *
 *   Activities : activityA1 (in projectAlpha), assignedTo = userTeam
 *                activityA2 (in projectAlpha), assignedTo = nobody
 *
 *   Users      :
 *      "it_admin"   → ROLE_ADMIN                         | OBS none      | ProjectMembers none
 *      "it_pm_a"    → ROLE_PROJECT_MANAGER               | OBS obs.left  | ProjectMember(alpha, PROJECT_MANAGER)
 *      "it_pm_b"    → ROLE_PROJECT_MANAGER               | OBS obs.right | ProjectMember(beta,  PROJECT_MANAGER)
 *      "it_team"    → ROLE_TEAM_MEMBER                   | OBS none      | ProjectMember(alpha, TEAM_MEMBER); assignedTo activityA1
 *      "it_finance" → ROLE_FINANCE + ROLE_VIEWER         | OBS obs.root  | (no project_members; sees via OBS subtree)
 *      "it_viewer"  → ROLE_VIEWER                        | OBS none      | none — should see nothing
 * </pre>
 */
@Component
public class SecurityTestFixture {

    public static final String PASSWORD = "Pa55word!";

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EpsNodeRepository epsNodeRepository;
    @Autowired private ObsNodeRepository obsNodeRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private WbsNodeRepository wbsNodeRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private UserObsAssignmentRepository userObsAssignmentRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;

    @Getter private UUID adminUserId;
    @Getter private UUID pmAlphaUserId;
    @Getter private UUID pmBetaUserId;
    @Getter private UUID teamUserId;
    @Getter private UUID financeUserId;
    @Getter private UUID viewerUserId;

    @Getter private UUID obsRootId;
    @Getter private UUID obsLeftId;
    @Getter private UUID obsRightId;
    @Getter private UUID epsRootId;
    @Getter private UUID epsEppmId;
    @Getter private UUID projectAlphaId;
    @Getter private UUID projectBetaId;
    @Getter private UUID activityA1Id;
    @Getter private UUID activityA2Id;

    /** Idempotent. Resolves to the seeded ids if a previous test run created them. */
    @Transactional
    public void ensureSeeded() {
        // ── OBS tree ──────────────────────────────────────────────────────────────
        ObsNode obsRoot = obsNodeRepository.findAll().stream()
                .filter(o -> "IT.ROOT".equals(o.getCode())).findFirst()
                .orElseGet(() -> obsNodeRepository.save(newObs("IT.ROOT", "IT Root", null)));
        obsRootId = obsRoot.getId();

        ObsNode obsLeft = obsNodeRepository.findAll().stream()
                .filter(o -> "IT.LEFT".equals(o.getCode())).findFirst()
                .orElseGet(() -> obsNodeRepository.save(newObs("IT.LEFT", "IT Left", obsRootId)));
        obsLeftId = obsLeft.getId();

        ObsNode obsRight = obsNodeRepository.findAll().stream()
                .filter(o -> "IT.RIGHT".equals(o.getCode())).findFirst()
                .orElseGet(() -> obsNodeRepository.save(newObs("IT.RIGHT", "IT Right", obsRootId)));
        obsRightId = obsRight.getId();

        // ── EPS tree ──────────────────────────────────────────────────────────────
        EpsNode epsRoot = epsNodeRepository.findAll().stream()
                .filter(e -> "IT.EPS.ROOT".equals(e.getCode())).findFirst()
                .orElseGet(() -> epsNodeRepository.save(newEps("IT.EPS.ROOT", "IT EPS Root", null)));
        epsRootId = epsRoot.getId();

        EpsNode epsEppm = epsNodeRepository.findAll().stream()
                .filter(e -> "IT.EPS.EPPM".equals(e.getCode())).findFirst()
                .orElseGet(() -> epsNodeRepository.save(newEps("IT.EPS.EPPM", "IT EPS EPPM", epsRootId)));
        epsEppmId = epsEppm.getId();

        // ── Projects ──────────────────────────────────────────────────────────────
        Project alpha = projectRepository.findByCode("IT.ALPHA")
                .orElseGet(() -> projectRepository.save(
                        newProject("IT.ALPHA", "IT Alpha Project", epsEppmId, obsLeftId)));
        projectAlphaId = alpha.getId();

        Project beta = projectRepository.findByCode("IT.BETA")
                .orElseGet(() -> projectRepository.save(
                        newProject("IT.BETA", "IT Beta Project", epsEppmId, obsRightId)));
        projectBetaId = beta.getId();

        // ── Root WBS for both projects (Activity.wbsNodeId is NOT NULL) ──────────
        WbsNode wbsAlpha = wbsNodeRepository.findByProjectIdOrderBySortOrder(projectAlphaId).stream()
                .findFirst().orElseGet(() -> wbsNodeRepository.save(newWbs("IT.ALPHA.WBS", projectAlphaId)));
        WbsNode wbsBeta = wbsNodeRepository.findByProjectIdOrderBySortOrder(projectBetaId).stream()
                .findFirst().orElseGet(() -> wbsNodeRepository.save(newWbs("IT.BETA.WBS", projectBetaId)));

        // ── Users ─────────────────────────────────────────────────────────────────
        adminUserId   = ensureUser("it_admin",   "it.admin@bipros.test",   "ADMIN");
        pmAlphaUserId = ensureUser("it_pm_a",    "it.pm.a@bipros.test",    "PROJECT_MANAGER");
        pmBetaUserId  = ensureUser("it_pm_b",    "it.pm.b@bipros.test",    "PROJECT_MANAGER");
        teamUserId    = ensureUser("it_team",    "it.team@bipros.test",    "TEAM_MEMBER");
        financeUserId = ensureUser("it_finance", "it.finance@bipros.test", "FINANCE", "VIEWER");
        viewerUserId  = ensureUser("it_viewer",  "it.viewer@bipros.test",  "VIEWER");

        // ── OBS assignments ───────────────────────────────────────────────────────
        ensureObsAssignment(pmAlphaUserId, obsLeftId,  AccessLevel.EDIT);
        ensureObsAssignment(pmBetaUserId,  obsRightId, AccessLevel.EDIT);
        ensureObsAssignment(financeUserId, obsRootId,  AccessLevel.VIEW);

        // ── ProjectMember rows ───────────────────────────────────────────────────
        ensureProjectMember(pmAlphaUserId, projectAlphaId, ProjectMemberRole.PROJECT_MANAGER);
        ensureProjectMember(pmBetaUserId,  projectBetaId,  ProjectMemberRole.PROJECT_MANAGER);
        ensureProjectMember(teamUserId,    projectAlphaId, ProjectMemberRole.TEAM_MEMBER);

        // ── Activities ────────────────────────────────────────────────────────────
        Activity a1 = activityRepository.findByProjectId(projectAlphaId).stream()
                .filter(a -> "IT.A1".equals(a.getCode())).findFirst()
                .orElseGet(() -> activityRepository.save(newActivity("IT.A1", "IT Activity 1",
                        projectAlphaId, wbsAlpha.getId(), teamUserId)));
        activityA1Id = a1.getId();
        if (a1.getAssignedTo() == null) {
            a1.setAssignedTo(teamUserId);
            activityRepository.save(a1);
        }

        Activity a2 = activityRepository.findByProjectId(projectAlphaId).stream()
                .filter(a -> "IT.A2".equals(a.getCode())).findFirst()
                .orElseGet(() -> activityRepository.save(newActivity("IT.A2", "IT Activity 2",
                        projectAlphaId, wbsAlpha.getId(), null)));
        activityA2Id = a2.getId();
    }

    // ─────────────────────────── helpers ────────────────────────────────────────

    private UUID ensureUser(String username, String email, String... roles) {
        User user = userRepository.findByUsername(username).orElseGet(() -> {
            User u = new User(username, email, passwordEncoder.encode(PASSWORD));
            u.setEnabled(true);
            u.setAccountLocked(false);
            return userRepository.save(u);
        });
        for (String roleName : roles) {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new IllegalStateException("Missing seeded role: " + roleName));
            if (!userRoleRepository.existsByUserIdAndRoleId(user.getId(), role.getId())) {
                userRoleRepository.save(new UserRole(user.getId(), role.getId()));
            }
        }
        return user.getId();
    }

    private void ensureObsAssignment(UUID userId, UUID obsNodeId, AccessLevel level) {
        userObsAssignmentRepository.findByUserIdAndObsNodeId(userId, obsNodeId)
                .orElseGet(() -> userObsAssignmentRepository.save(
                        new UserObsAssignment(userId, obsNodeId, level)));
    }

    private void ensureProjectMember(UUID userId, UUID projectId, ProjectMemberRole role) {
        if (!projectMemberRepository.existsByUserIdAndProjectIdAndProjectRole(userId, projectId, role)) {
            projectMemberRepository.save(new ProjectMember(userId, projectId, role, null));
        }
    }

    private static ObsNode newObs(String code, String name, UUID parentId) {
        ObsNode n = new ObsNode();
        n.setCode(code); n.setName(name); n.setParentId(parentId); n.setSortOrder(0);
        return n;
    }

    private static EpsNode newEps(String code, String name, UUID parentId) {
        EpsNode n = new EpsNode();
        n.setCode(code); n.setName(name); n.setParentId(parentId); n.setSortOrder(0);
        return n;
    }

    private static Project newProject(String code, String name, UUID epsNodeId, UUID obsNodeId) {
        Project p = new Project();
        p.setCode(code); p.setName(name);
        p.setEpsNodeId(epsNodeId); p.setObsNodeId(obsNodeId);
        p.setStatus(ProjectStatus.ACTIVE); p.setPriority(50);
        return p;
    }

    private static WbsNode newWbs(String code, UUID projectId) {
        WbsNode w = new WbsNode();
        w.setCode(code); w.setName(code); w.setProjectId(projectId); w.setSortOrder(0);
        return w;
    }

    private static Activity newActivity(String code, String name, UUID projectId, UUID wbsNodeId, UUID assignedTo) {
        Activity a = new Activity();
        a.setCode(code); a.setName(name);
        a.setProjectId(projectId); a.setWbsNodeId(wbsNodeId);
        a.setAssignedTo(assignedTo);
        return a;
    }
}
