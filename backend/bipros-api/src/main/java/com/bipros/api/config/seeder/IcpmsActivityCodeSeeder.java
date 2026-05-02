package com.bipros.api.config.seeder;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityCode;
import com.bipros.activity.domain.model.ActivityCodeAssignment;
import com.bipros.activity.domain.model.CodeScope;
import com.bipros.activity.domain.repository.ActivityCodeAssignmentRepository;
import com.bipros.activity.domain.repository.ActivityCodeRepository;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * IC-PMS M2 GAP — P6-style activity codes + code-value assignments.
 *
 * <p>Seeds three project-scoped code categories (DISC, PHASE, ZONE) on DMIC-PROG, then
 * assigns code values to each of the 34 DMIC-N03 activities based on keyword heuristics
 * on activity name. Assignments are pragmatic — each activity gets 1–3 codes — so the
 * code-filter UI has realistic cardinality.
 *
 * <p>Field-name reality check:
 * <ul>
 *   <li>{@code ActivityCode} has {@code name}, {@code description}, {@code scope}
 *       ({@link CodeScope} with values GLOBAL/EPS/PROJECT), {@code projectId}. It does
 *       NOT have a {@code code} column — the spec asked for a "code" identifier but the
 *       entity only exposes {@code name}; we embed the short tag (DISC/PHASE/ZONE) as a
 *       prefix on {@code name} so the UI can display both pieces.</li>
 *   <li>{@code ActivityCodeAssignment} has {@code activityId}, {@code activityCodeId}
 *       and {@code codeValue} (the string value like "CIVIL"). There is no separate
 *       {@code ActivityCodeValue} catalog entity — the value is stored inline.</li>
 * </ul>
 *
 * <p>Sentinel: {@code activityCodeRepository.count() > 0} — any pre-existing code skips.
 */
@Slf4j
@Component
@Profile("legacy-demo")
@Order(121)
@RequiredArgsConstructor
public class IcpmsActivityCodeSeeder implements CommandLineRunner {

    private final ActivityCodeRepository activityCodeRepository;
    private final ActivityCodeAssignmentRepository activityCodeAssignmentRepository;
    private final ActivityRepository activityRepository;
    private final ProjectRepository projectRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (activityCodeRepository.count() > 0) {
            log.info("[IC-PMS M2 GAP] activity codes already present, skipping");
            return;
        }

        Project project = projectRepository.findByCode("DMIC-PROG").orElse(null);
        if (project == null) {
            log.warn("[IC-PMS M2 GAP] DMIC-PROG project not found — run Phase A first");
            return;
        }
        UUID projectId = project.getId();

        // ---- categories ----
        ActivityCode discCode = saveCode("DISC — Discipline", projectId,
                "Engineering discipline responsible for the activity (civil / structural / electrical / mechanical).", 1);
        ActivityCode phaseCode = saveCode("PHASE — Phase", projectId,
                "Execution phase classification — mobilisation, earthworks, structures, finishes, handover.", 2);
        ActivityCode zoneCode = saveCode("ZONE — Zone", projectId,
                "Geographic zone in the Dholera SIR corridor (Zone A / B / C).", 3);

        Map<String, UUID> codeIds = new HashMap<>();
        codeIds.put("DISC", discCode.getId());
        codeIds.put("PHASE", phaseCode.getId());
        codeIds.put("ZONE", zoneCode.getId());

        // ---- assignments ----
        List<Activity> activities = activityRepository.findByProjectId(projectId);
        int assignments = 0;
        for (Activity a : activities) {
            String name = a.getName() == null ? "" : a.getName().toLowerCase(Locale.ROOT);
            String code = a.getCode() == null ? "" : a.getCode().toUpperCase(Locale.ROOT);

            // --- Discipline assignment --- (keyword-driven, always assign one)
            String disc = "CIVIL";
            if (name.contains("transformer") || name.contains("switchgear") || name.contains("gis ")
                    || name.contains("power") || name.contains("sub-station") || name.contains("220kv")
                    || name.contains("33kv") || name.contains("fibre") || name.contains("ofc")
                    || name.contains("scc") || name.contains("hv testing") || name.contains("energis")) {
                disc = "ELECTRICAL";
            } else if (name.contains("m&e") || name.contains("mechanical") || name.contains("pump")
                    || name.contains("intake") || name.contains("clarifier")) {
                disc = "MECHANICAL";
            } else if (name.contains("civil") || name.contains("building") || name.contains("wtp")
                    || name.contains("structure") || name.contains("culvert")) {
                disc = "STRUCTURAL";
            }
            saveAssignment(a.getId(), codeIds.get("DISC"), disc);
            assignments++;

            // --- Phase assignment --- (keyword-driven, skip when no match — keeps assignment
            // cardinality in the 50-70 target range instead of forcing every activity into a phase)
            String phase = null;
            if (name.contains("mobilis") || name.contains("survey") || name.contains("soil test")
                    || name.contains("clearing")) {
                phase = "MOBILISATION";
            } else if (name.contains("earthwork") || name.contains("excavation") || name.contains("embankment")
                    || name.contains("compaction") || name.contains("formation")) {
                phase = "EARTHWORKS";
            } else if (name.contains("handover") || name.contains("commissioning")
                    || name.contains("milestone") || name.contains("energis")) {
                phase = "HANDOVER";
            } else if (name.contains("marking") || name.contains("kerb") || name.contains("furniture")
                    || name.contains("signage") || name.contains("restoration") || name.contains("surface course")) {
                phase = "FINISHES";
            } else if (name.contains("pavement") || name.contains("civil") || name.contains("switchgear")
                    || name.contains("transformer") || name.contains("sub-base") || name.contains("wmm")
                    || name.contains("dbm") || name.contains("gsb") || name.contains("bituminous")) {
                phase = "STRUCTURES";
            }
            if (phase != null) {
                saveAssignment(a.getId(), codeIds.get("PHASE"), phase);
                assignments++;
            }

            // --- Zone assignment --- (only where the activity name/code makes it obvious — not
            // every activity belongs to a spatial zone; this keeps cardinality ~50-70 rows.)
            String zone = null;
            if (name.contains("zone a-1") || code.matches(".*(A00[1-9]|A01[0-3]).*")) {
                zone = "ZONE_A";
            } else if (name.contains("zone a-2") || name.contains("zone a")) {
                zone = "ZONE_A";
            } else if (name.contains("zone b")) {
                zone = "ZONE_B";
            } else if (name.contains("zone c")) {
                zone = "ZONE_C";
            }
            if (zone != null) {
                saveAssignment(a.getId(), codeIds.get("ZONE"), zone);
                assignments++;
            }
        }

        log.info("[IC-PMS M2 GAP] seeded 3 activity codes (DISC/PHASE/ZONE) with {} assignments across {} activities",
                assignments, activities.size());
    }

    // ------------------------------------------------------------------ helpers

    private ActivityCode saveCode(String name, UUID projectId, String description, int sortOrder) {
        ActivityCode c = new ActivityCode();
        c.setName(name);
        c.setDescription(description);
        c.setScope(CodeScope.PROJECT);
        c.setProjectId(projectId);
        c.setSortOrder(sortOrder);
        return activityCodeRepository.save(c);
    }

    private void saveAssignment(UUID activityId, UUID activityCodeId, String value) {
        ActivityCodeAssignment a = new ActivityCodeAssignment();
        a.setActivityId(activityId);
        a.setActivityCodeId(activityCodeId);
        a.setCodeValue(value);
        activityCodeAssignmentRepository.save(a);
    }
}
