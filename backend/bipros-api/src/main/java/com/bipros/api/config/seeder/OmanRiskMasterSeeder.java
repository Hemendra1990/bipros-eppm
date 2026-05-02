package com.bipros.api.config.seeder;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.model.RiskActivityAssignment;
import com.bipros.risk.domain.model.RiskScoringConfig;
import com.bipros.risk.domain.repository.RiskActivityAssignmentRepository;
import com.bipros.risk.domain.repository.RiskRepository;
import com.bipros.risk.domain.repository.RiskScoringConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Component
@Profile("seed")
@Order(153)
@RequiredArgsConstructor
public class OmanRiskMasterSeeder implements CommandLineRunner {

    private static final String PROJECT_CODE = "6155";
    private static final long DETERMINISTIC_SEED = 6155L;
    private static final LocalDate DEFAULT_DATA_DATE = LocalDate.of(2026, 4, 29);

    private final ProjectRepository projectRepository;
    private final RiskScoringConfigRepository riskScoringConfigRepository;
    private final RiskRepository riskRepository;
    private final RiskActivityAssignmentRepository riskActivityAssignmentRepository;
    private final ActivityRepository activityRepository;

    @Override
    public void run(String... args) {
        Optional<Project> projectOpt = projectRepository.findByCode(PROJECT_CODE);
        if (projectOpt.isEmpty()) {
            log.warn("[BNK-RISK-MASTER] project '{}' not found — skipping", PROJECT_CODE);
            return;
        }
        Project project = projectOpt.get();
        UUID projectId = project.getId();

        if (riskScoringConfigRepository.findByProjectId(projectId).isPresent()) {
            log.info("[BNK-RISK-MASTER] RiskScoringConfig already present for project '{}' — skipping", PROJECT_CODE);
            return;
        }

        Random rng = new Random(DETERMINISTIC_SEED);

        seedScoringConfig(projectId);
        int assignmentCount = seedRiskActivityAssignments(projectId, rng);

        log.info("[BNK-RISK-MASTER] Seeded 1 RiskScoringConfig, {} risk-activity assignments", assignmentCount);
    }

    private void seedScoringConfig(UUID projectId) {
        RiskScoringConfig config = new RiskScoringConfig();
        config.setProjectId(projectId);
        config.setScoringMethod(RiskScoringConfig.ScoringMethod.HIGHEST_IMPACT);
        config.setActive(Boolean.TRUE);
        riskScoringConfigRepository.save(config);
    }

    private int seedRiskActivityAssignments(UUID projectId, Random rng) {
        List<Risk> risks = riskRepository.findByProjectId(projectId);
        if (risks.isEmpty()) {
            log.warn("[BNK-RISK-MASTER] no risks found for project — skipping assignments");
            return 0;
        }
        List<Activity> activities = activityRepository.findByProjectId(projectId);
        if (activities.isEmpty()) {
            log.warn("[BNK-RISK-MASTER] no activities found for project — skipping assignments");
            return 0;
        }

        List<UUID> activityIds = activities.stream().map(Activity::getId).toList();
        int created = 0;

        for (Risk risk : risks) {
            int assignCount = 2 + rng.nextInt(4);
            List<UUID> picked = pickUnique(activityIds, assignCount, rng);

            for (UUID activityId : picked) {
                if (riskActivityAssignmentRepository.existsByRiskIdAndActivityId(risk.getId(), activityId)) {
                    continue;
                }
                RiskActivityAssignment assignment = new RiskActivityAssignment();
                assignment.setRiskId(risk.getId());
                assignment.setActivityId(activityId);
                assignment.setProjectId(projectId);
                riskActivityAssignmentRepository.save(assignment);
                created++;
            }
        }
        return created;
    }

    private List<UUID> pickUnique(List<UUID> source, int count, Random rng) {
        List<UUID> pool = new ArrayList<>(source);
        int n = Math.min(count, pool.size());
        List<UUID> picked = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int idx = rng.nextInt(pool.size());
            picked.add(pool.remove(idx));
        }
        return picked;
    }
}
