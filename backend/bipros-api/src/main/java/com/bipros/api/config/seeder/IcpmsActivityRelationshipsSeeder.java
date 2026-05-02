package com.bipros.api.config.seeder;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityRelationship;
import com.bipros.activity.domain.model.RelationshipType;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IC-PMS M2 — Finish-to-Start dependencies between DMIC-N03 activities so the
 * CPM engine has a realistic network to traverse.
 *
 * <p>Seeds ~30 FS relationships chaining the 30 work activities and 4 milestones
 * published by {@link IcpmsActivitiesSeeder}. Lags are 0 by default except for a
 * few earthwork-to-granular curing gates where 5-10 days of compaction / settlement
 * is realistic.
 *
 * <p>Sentinel: {@code activityRelationshipRepository.count() > 0} → skip.
 */
@Slf4j
@Component
@Profile("legacy-demo")
@Order(107)
@RequiredArgsConstructor
public class IcpmsActivityRelationshipsSeeder implements CommandLineRunner {

    private final ActivityRepository activityRepository;
    private final ActivityRelationshipRepository relationshipRepository;
    private final ProjectRepository projectRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (relationshipRepository.count() > 0) {
            log.info("[IC-PMS M2] activity relationships already present, skipping");
            return;
        }

        Project project = projectRepository.findByCode("DMIC-PROG").orElse(null);
        if (project == null) {
            log.warn("[IC-PMS M2] DMIC-PROG project not found — run Phase A first");
            return;
        }
        UUID projectId = project.getId();

        Map<String, UUID> codeToId = new HashMap<>();
        for (Activity a : activityRepository.findByProjectId(projectId)) {
            codeToId.put(a.getCode(), a.getId());
        }
        if (codeToId.isEmpty()) {
            log.warn("[IC-PMS M2] no activities present for DMIC-PROG — run {} first",
                    IcpmsActivitiesSeeder.class.getSimpleName());
            return;
        }

        // ---------- Package 1: Trunk Road Zone A-1 & A-2 ----------
        // Linear chain A001 -> A002 -> A003 -> A004 with curing lag before embankment.
        link(codeToId, projectId, "N03-A001", "N03-A002", 0.0);
        link(codeToId, projectId, "N03-A001", "N03-A003", 0.0);
        link(codeToId, projectId, "N03-A002", "N03-A004", 0.0);
        link(codeToId, projectId, "N03-A003", "N03-A004", 0.0);
        link(codeToId, projectId, "N03-A004", "N03-A005", 0.0);
        link(codeToId, projectId, "N03-A005", "N03-A006", 5.0);   // 5d curing on excavated formation
        link(codeToId, projectId, "N03-A006", "N03-A007", 7.0);   // 7d embankment settlement
        link(codeToId, projectId, "N03-A007", "N03-A008", 0.0);
        link(codeToId, projectId, "N03-A008", "N03-A009", 0.0);
        link(codeToId, projectId, "N03-A009", "N03-A010", 0.0);
        link(codeToId, projectId, "N03-A010", "N03-A011", 0.0);
        link(codeToId, projectId, "N03-A011", "N03-A012", 0.0);
        link(codeToId, projectId, "N03-A012", "N03-A013", 0.0);

        // Zone A-2 starts parallel with Zone A-1 earthwork; sub-base follows earthwork.
        link(codeToId, projectId, "N03-A004", "N03-A014", 0.0);
        link(codeToId, projectId, "N03-A014", "N03-A015", 10.0);  // 10d curing

        // Zone A-1 earthwork completion milestone
        link(codeToId, projectId, "N03-A007", "N03-M001", 0.0);

        // Trunk Road Zone A-1 substantial completion milestone
        link(codeToId, projectId, "N03-A013", "N03-M004", 0.0);

        // ---------- Package 2: Water Supply & Treatment ----------
        link(codeToId, projectId, "N03-A016", "N03-A017", 0.0);
        link(codeToId, projectId, "N03-A017", "N03-A018", 0.0);
        link(codeToId, projectId, "N03-A016", "N03-A019", 0.0);
        link(codeToId, projectId, "N03-A019", "N03-A020", 0.0);
        link(codeToId, projectId, "N03-A018", "N03-M003", 0.0);   // WTP commissioning

        // ---------- Package 3: Power Distribution & Sub-station ----------
        link(codeToId, projectId, "N03-A021", "N03-A022", 0.0);
        link(codeToId, projectId, "N03-A022", "N03-A023", 0.0);
        link(codeToId, projectId, "N03-A023", "N03-A024", 0.0);
        link(codeToId, projectId, "N03-A024", "N03-A025", 0.0);
        link(codeToId, projectId, "N03-A025", "N03-M002", 0.0);   // 220kV energisation

        // ---------- Package 4: ICT / Smart City ----------
        link(codeToId, projectId, "N03-A026", "N03-A027", 0.0);
        link(codeToId, projectId, "N03-A027", "N03-A028", 0.0);
        link(codeToId, projectId, "N03-A028", "N03-A029", 0.0);

        // SCCC building civil works can start once fibre backbone segment 1 is in
        // so cable entry points and duct penetrations are pulled through early.
        link(codeToId, projectId, "N03-A026", "N03-A030", 0.0);

        long count = relationshipRepository.count();
        log.info("[IC-PMS M2] seeded {} activity relationships for DMIC-N03", count);
    }

    private void link(Map<String, UUID> codeToId, UUID projectId,
                      String predCode, String succCode, double lagDays) {
        UUID pred = codeToId.get(predCode);
        UUID succ = codeToId.get(succCode);
        if (pred == null || succ == null) {
            log.warn("[IC-PMS M2] relationship skipped — missing activity {} or {}",
                    predCode, succCode);
            return;
        }
        if (relationshipRepository.existsByPredecessorActivityIdAndSuccessorActivityId(pred, succ)) {
            return;
        }
        ActivityRelationship r = new ActivityRelationship();
        r.setProjectId(projectId);
        r.setPredecessorActivityId(pred);
        r.setSuccessorActivityId(succ);
        r.setRelationshipType(RelationshipType.FINISH_TO_START);
        r.setLag(lagDays);
        r.setIsExternal(false);
        relationshipRepository.save(r);
    }
}
