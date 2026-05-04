package com.bipros.api.config.seeder;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityCode;
import com.bipros.activity.domain.model.ActivityCodeAssignment;
import com.bipros.activity.domain.model.CodeScope;
import com.bipros.activity.domain.repository.ActivityCodeAssignmentRepository;
import com.bipros.activity.domain.repository.ActivityCodeRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.calendar.domain.model.Calendar;
import com.bipros.calendar.domain.model.CalendarException;
import com.bipros.calendar.domain.model.DayType;
import com.bipros.calendar.domain.repository.CalendarExceptionRepository;
import com.bipros.calendar.domain.repository.CalendarRepository;
import com.bipros.cost.domain.entity.CostAccount;
import com.bipros.cost.domain.repository.CostAccountRepository;
import com.bipros.evm.domain.entity.EvmCalculation;
import com.bipros.evm.domain.entity.EvmTechnique;
import com.bipros.evm.domain.entity.EtcMethod;
import com.bipros.evm.domain.repository.EvmCalculationRepository;
import com.bipros.document.domain.model.DrawingDiscipline;
import com.bipros.document.domain.model.DrawingRegister;
import com.bipros.document.domain.model.DrawingStatus;
import com.bipros.document.domain.model.RfiPriority;
import com.bipros.document.domain.model.RfiRegister;
import com.bipros.document.domain.model.RfiStatus;
import com.bipros.document.domain.model.Transmittal;
import com.bipros.document.domain.model.TransmittalItem;
import com.bipros.document.domain.model.TransmittalPurpose;
import com.bipros.document.domain.model.TransmittalStatus;
import com.bipros.document.domain.repository.DocumentRepository;
import com.bipros.document.domain.repository.DrawingRegisterRepository;
import com.bipros.document.domain.repository.RfiRegisterRepository;
import com.bipros.document.domain.repository.TransmittalItemRepository;
import com.bipros.document.domain.repository.TransmittalRepository;
import com.bipros.project.domain.model.DailyActivityResourceOutput;
import com.bipros.project.domain.model.DailyResourceDeployment;
import com.bipros.project.domain.model.DeploymentResourceType;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.DailyActivityResourceOutputRepository;
import com.bipros.project.domain.repository.DailyResourceDeploymentRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import com.bipros.resource.domain.model.ProductivityNorm;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.model.ResourceDailyLog;
import com.bipros.resource.domain.repository.ProductivityNormRepository;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceDailyLogRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.bipros.risk.domain.model.ActivityCorrelation;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.model.RiskActivityAssignment;
import com.bipros.risk.domain.model.RiskResponse;
import com.bipros.risk.domain.model.RiskResponseType;
import com.bipros.risk.domain.model.RiskStatus;
import com.bipros.risk.domain.model.RiskTrigger;
import com.bipros.risk.domain.repository.ActivityCorrelationRepository;
import com.bipros.risk.domain.repository.RiskActivityAssignmentRepository;
import com.bipros.risk.domain.repository.RiskRepository;
import com.bipros.risk.domain.repository.RiskResponseRepository;
import com.bipros.risk.domain.repository.RiskTriggerRepository;
import com.bipros.scheduling.domain.model.ScenarioStatus;
import com.bipros.scheduling.domain.model.ScenarioType;
import com.bipros.scheduling.domain.model.ScheduleScenario;
import com.bipros.scheduling.domain.repository.ScheduleScenarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Supplemental seeder for the Oman Barka–Nakhal Road project that fills modules not covered
 * by {@link OmanRoadProjectSeeder} (141), {@link OmanRoadDailyDataSeeder} (143),
 * {@link OmanRoadAttachmentsSeeder} (146) or the SQL report bundle:
 * <ul>
 *   <li>Risk — responses, triggers and activity assignments for the seeded risk register</li>
 *   <li>Document — drawing register, RFI register, transmittals + transmittal items</li>
 *   <li>Scheduling — schedule scenarios (BASELINE, CRASH, FAST_TRACK)</li>
 *   <li>Risk — duration correlations between same-WBS activity pairs</li>
 *   <li>Cost — 3-level GL hierarchy of cost accounts</li>
 * </ul>
 *
 * <p>Runs at {@code @Order(151)} so the project, WBS, activities, contracts and risks
 * (created by the SQL bundle inside the main seeder) already exist. Idempotent via
 * sentinel checks on each module.
 */
@Slf4j
@Component
@Profile("seed")
@Order(151)
@RequiredArgsConstructor
public class OmanRoadProjectSupplementalSeeder implements CommandLineRunner {

    private static final String PROJECT_CODE = "6155";
    private static final long DETERMINISTIC_SEED = 6155L;
    private static final LocalDate DEFAULT_DATA_DATE = LocalDate.of(2026, 4, 29);

    // ── Repositories ───────────────────────────────────────────────
    private final ProjectRepository projectRepository;
    private final ActivityRepository activityRepository;
    private final DrawingRegisterRepository drawingRegisterRepository;
    private final RfiRegisterRepository rfiRegisterRepository;
    private final TransmittalRepository transmittalRepository;
    private final TransmittalItemRepository transmittalItemRepository;
    private final DocumentRepository documentRepository;
    private final RiskRepository riskRepository;
    private final RiskResponseRepository riskResponseRepository;
    private final RiskTriggerRepository riskTriggerRepository;
    private final RiskActivityAssignmentRepository riskActivityAssignmentRepository;
    private final ActivityCorrelationRepository activityCorrelationRepository;
    private final ScheduleScenarioRepository scheduleScenarioRepository;
    private final CostAccountRepository costAccountRepository;
    private final EvmCalculationRepository evmCalculationRepository;
    private final ActivityCodeRepository activityCodeRepository;
    private final ActivityCodeAssignmentRepository activityCodeAssignmentRepository;
    private final CalendarExceptionRepository calendarExceptionRepository;
    private final CalendarRepository calendarRepository;
    private final WbsNodeRepository wbsNodeRepository;
    private final ResourceAssignmentRepository resourceAssignmentRepository;
    private final ResourceDailyLogRepository resourceDailyLogRepository;
    private final DailyActivityResourceOutputRepository dailyActivityResourceOutputRepository;
    private final DailyResourceDeploymentRepository dailyResourceDeploymentRepository;
    private final ProductivityNormRepository productivityNormRepository;
    private final ResourceRepository resourceRepository;

    @Override
    public void run(String... args) {
        Project project = projectRepository.findByCode(PROJECT_CODE).orElse(null);
        if (project == null) {
            log.info("[BNK-SUPP] project '{}' not found — skipping supplemental seeder", PROJECT_CODE);
            return;
        }
        UUID projectId = project.getId();

        log.info("[BNK-SUPP] starting supplemental seeding for '{}'", PROJECT_CODE);

        seedRiskResponsesTriggersAssignments(projectId);
        seedDrawingRegister(projectId);
        seedRfiRegister(projectId);
        seedTransmittals(projectId);
        seedScheduleScenarios(projectId);
        seedActivityCorrelations(projectId);
        seedCostAccounts();
        seedCalendarExceptions(projectId);
        seedActivityCodes(projectId);
        seedEvmCalculations(projectId);
        seedDailyActivityResourceOutput(projectId);
        seedDailyActivityResourceOutputs(projectId);
        seedManpowerOperationalWiring(projectId);

        log.info("[BNK-SUPP] supplemental seeding completed");
    }

    // ────────────────────────── Risk module ─────────────────────────
    /**
     * Seeds RiskResponse, RiskTrigger, and RiskActivityAssignment rows for the 12 risks
     * created by Agent 4's SQL bundle ({@code 09-risks.sql}). Strategy mix: 8 MITIGATE,
     * 2 TRANSFER, 2 ACCEPT. At least 4 responses left in DRAFT/PROPOSED so the user can
     * refine them.
     */
    private void seedRiskResponsesTriggersAssignments(UUID projectId) {
        if (riskResponseRepository.findAll().stream().findAny().isPresent()
                && riskActivityAssignmentRepository.findByProjectId(projectId).size() > 0) {
            log.info("[BNK-SUPP] risk responses + assignments already present — skipping");
            return;
        }

        List<Risk> risks = riskRepository.findByProjectId(projectId);
        if (risks.isEmpty()) {
            log.warn("[BNK-SUPP] no risks found for project — skipping responses/triggers/assignments");
            return;
        }
        risks.sort(Comparator.comparing(Risk::getCode, Comparator.nullsLast(Comparator.naturalOrder())));

        // Strategy mix per plan: 8 MITIGATE, 2 TRANSFER, 2 ACCEPT (12 total).
        RiskResponseType[] strategies = new RiskResponseType[]{
                RiskResponseType.MITIGATE, RiskResponseType.MITIGATE, RiskResponseType.MITIGATE,
                RiskResponseType.MITIGATE, RiskResponseType.MITIGATE, RiskResponseType.MITIGATE,
                RiskResponseType.MITIGATE, RiskResponseType.MITIGATE,
                RiskResponseType.TRANSFER, RiskResponseType.TRANSFER,
                RiskResponseType.ACCEPT, RiskResponseType.ACCEPT
        };
        // Operability: 4 PROPOSED so the user can refine; rest IN_PROGRESS or PLANNED.
        String[] statuses = new String[]{
                "IN_PROGRESS", "PLANNED", "IN_PROGRESS", "PROPOSED",
                "PLANNED", "IN_PROGRESS", "COMPLETED", "PROPOSED",
                "PLANNED", "PROPOSED", "PLANNED", "PROPOSED"
        };
        // Owner rotation: PM Mohsin or one of 4 supervisors by section.
        String[] ownerLabels = new String[]{
                "Mohsin Ahmad (PM)", "T. Swamy (Supervisor A)", "Nagarajan (Supervisor B)",
                "A.K. Singh (Supervisor C)", "Anbazhagan (Supervisor D)"
        };

        Random rng = new Random("6155-supp".hashCode());
        int respCount = 0, trigCount = 0, assignCount = 0;

        for (int i = 0; i < risks.size(); i++) {
            Risk r = risks.get(i);
            RiskResponseType strategy = strategies[i % strategies.length];
            String status = statuses[i % statuses.length];
            String owner = ownerLabels[i % ownerLabels.length];

            // ── RiskResponse ──
            RiskResponse rr = new RiskResponse();
            rr.setRiskId(r.getId());
            rr.setResponseType(strategy);
            rr.setDescription(buildResponseText(strategy, owner, r));
            rr.setResponsibleId(null);
            LocalDate planned = r.getIdentifiedDate() != null
                    ? r.getIdentifiedDate().plusDays(14L + (rng.nextInt(60)))
                    : LocalDate.of(2025, 3, 1).plusDays(rng.nextInt(120));
            rr.setPlannedDate(planned);
            if ("COMPLETED".equals(status)) {
                rr.setActualDate(planned.plusDays(rng.nextInt(20)));
                rr.setActualCost(new BigDecimal(2000 + rng.nextInt(8000)));
            }
            rr.setEstimatedCost(new BigDecimal(1500 + rng.nextInt(15000)));
            rr.setStatus(status);
            riskResponseRepository.save(rr);
            respCount++;

            // ── RiskTrigger (one per OPEN/MITIGATING risk) ──
            if (r.getStatus() != RiskStatus.CLOSED && r.getStatus() != RiskStatus.RESOLVED) {
                RiskTrigger rt = new RiskTrigger();
                rt.setRiskId(r.getId());
                rt.setProjectId(projectId);
                rt.setTriggerCondition(buildTriggerCondition(r));
                rt.setTriggerType(triggerTypeFor(i));
                rt.setThresholdValue(50.0 + rng.nextInt(40));
                rt.setCurrentValue(40.0 + rng.nextInt(50));
                boolean isTriggered = (i % 4 == 0);
                rt.setIsTriggered(isTriggered);
                if (isTriggered) rt.setTriggeredAt(Instant.now());
                rt.setEscalationLevel(escalationFor(i));
                rt.setNotifyRoles("PROJECT_MANAGER,RESOURCE_MANAGER");
                riskTriggerRepository.save(rt);
                trigCount++;
            }

            // ── RiskActivityAssignment — expand affected_activities CSV ──
            String csv = r.getAffectedActivities();
            if (csv != null && !csv.isBlank()) {
                Set<UUID> seen = new HashSet<>();
                for (String code : csv.split(",")) {
                    String trimmed = code.trim();
                    if (trimmed.isEmpty()) continue;
                    Activity a = findActivityByCodeOrPrefix(projectId, trimmed);
                    if (a == null) continue;
                    if (!seen.add(a.getId())) continue;
                    if (riskActivityAssignmentRepository
                            .existsByRiskIdAndActivityId(r.getId(), a.getId())) continue;
                    RiskActivityAssignment ra = new RiskActivityAssignment();
                    ra.setRiskId(r.getId());
                    ra.setActivityId(a.getId());
                    ra.setProjectId(projectId);
                    riskActivityAssignmentRepository.save(ra);
                    assignCount++;
                }
            }
        }
        log.info("[BNK-SUPP] seeded {} risk responses, {} triggers, {} activity assignments",
                respCount, trigCount, assignCount);
    }

    private static String buildResponseText(RiskResponseType strategy, String owner, Risk r) {
        String code = r.getCode() != null ? r.getCode() : "?";
        return switch (strategy) {
            case MITIGATE -> "Mitigation owned by " + owner + ". Daily site review, weekly steering escalation. "
                    + "Pre-emptive deployment of mitigations as per " + code + " response plan.";
            case TRANSFER -> "Transfer via insurance / sub-contract clauses. Owner: " + owner
                    + ". Bank guarantee + warranty backstop in place.";
            case ACCEPT -> "Accept low-impact risk. Monitoring only by " + owner
                    + ". Reviewed monthly — no active mitigation budget.";
            default -> owner + " responsible.";
        };
    }

    private static String buildTriggerCondition(Risk r) {
        return "Trigger: indicator for " + (r.getCode() != null ? r.getCode() : "risk")
                + " — " + (r.getTitle() != null ? r.getTitle() : "(no title)");
    }

    private static RiskTrigger.TriggerType triggerTypeFor(int i) {
        return switch (i % 4) {
            case 0 -> RiskTrigger.TriggerType.SCHEDULE_DELAY;
            case 1 -> RiskTrigger.TriggerType.COST_OVERRUN;
            case 2 -> RiskTrigger.TriggerType.MILESTONE_MISSED;
            default -> RiskTrigger.TriggerType.MANUAL;
        };
    }

    private static RiskTrigger.EscalationLevel escalationFor(int i) {
        return switch (i % 3) {
            case 0 -> RiskTrigger.EscalationLevel.AMBER;
            case 1 -> RiskTrigger.EscalationLevel.GREEN;
            default -> RiskTrigger.EscalationLevel.RED;
        };
    }

    /**
     * Resolves an activity for the given code (e.g. {@code "1.3.5(i)a"} or
     * {@code "ACT-1.3.5(i)a"}). Tries direct match, then falls back to wildcard prefix
     * matching since SQL-bundle risks reference BOQ-style codes while activities are
     * stored with the {@code ACT-} prefix.
     */
    private Activity findActivityByCodeOrPrefix(UUID projectId, String code) {
        List<Activity> all = activityRepository.findByProjectId(projectId);
        for (Activity a : all) {
            if (code.equals(a.getCode())) return a;
        }
        String withPrefix = "ACT-" + code;
        for (Activity a : all) {
            if (withPrefix.equals(a.getCode())) return a;
        }
        // Case-insensitive containment fallback
        String upper = code.toUpperCase();
        for (Activity a : all) {
            if (a.getCode() != null && a.getCode().toUpperCase().contains(upper)) return a;
        }
        return null;
    }

    // ────────────────────────── Drawing Register ─────────────────────────
    private void seedDrawingRegister(UUID projectId) {
        if (!drawingRegisterRepository.findByProjectId(projectId).isEmpty()) {
            log.info("[BNK-SUPP] drawing register already seeded — skipping");
            return;
        }
        record DwgSpec(String number, String title, DrawingDiscipline discipline,
                       String revision, LocalDate revDate, DrawingStatus status,
                       String pkg, String scale) {}
        List<DwgSpec> drawings = List.of(
                new DwgSpec("DWG-001", "Key Plan — Barka Junction to Nakhal Roundabout (Ch 0+000 to 41+000)",
                        DrawingDiscipline.CIVIL, "R2", LocalDate.of(2024, 9, 10), DrawingStatus.IFC, "BNK-MAIN", "1:5000"),
                new DwgSpec("DWG-002", "Typical Cross Section — Dual 2-Lane Carriageway with 5 m Median",
                        DrawingDiscipline.CIVIL, "R2", LocalDate.of(2024, 9, 12), DrawingStatus.IFC, "BNK-MAIN", "1:100"),
                new DwgSpec("DWG-003", "Junction Detail at Wadi Al Hattat",
                        DrawingDiscipline.CIVIL, "R1", LocalDate.of(2024, 10, 5), DrawingStatus.IFC, "BNK-JCT-01", "1:500"),
                new DwgSpec("DWG-004", "Bridge GA — Pier P2 Substructure (Wadi Crossing Ch 18+200)",
                        DrawingDiscipline.STRUCTURAL, "R1", LocalDate.of(2024, 11, 8), DrawingStatus.IFC, "BNK-BRIDGE-1", "1:50"),
                new DwgSpec("DWG-005", "Pavement Design — Flexible Pavement Layer Stack",
                        DrawingDiscipline.CIVIL, "R0", LocalDate.of(2024, 9, 20), DrawingStatus.IFC, "BNK-MAIN", "1:50"),
                new DwgSpec("DWG-006", "Drainage Layout — Cross & Side Drains Section A",
                        DrawingDiscipline.CIVIL, "R0", LocalDate.of(2024, 10, 12), DrawingStatus.IFC, "BNK-DRN", "1:1000"),
                new DwgSpec("DWG-007", "Bridge GA — Superstructure Beams & Deck Slab",
                        DrawingDiscipline.STRUCTURAL, "R0", LocalDate.of(2024, 11, 25), DrawingStatus.IFC, "BNK-BRIDGE-1", "1:50"),
                new DwgSpec("DWG-008", "Road Furniture — Signage & Marking Layout",
                        DrawingDiscipline.CIVIL, "R0", LocalDate.of(2025, 1, 15), DrawingStatus.IFC, "BNK-RF", "1:500"),
                new DwgSpec("DWG-009", "Street Lighting — High-Mast and Pole Layout",
                        DrawingDiscipline.ELECTRICAL, "R1", LocalDate.of(2025, 2, 10), DrawingStatus.IFA, "BNK-ELEC", "1:1000"),
                new DwgSpec("DWG-010", "Wadi Bridge Foundation — Pile Group P1",
                        DrawingDiscipline.STRUCTURAL, "R0", LocalDate.of(2025, 3, 1), DrawingStatus.IFA, "BNK-BRIDGE-1", "1:50"),
                new DwgSpec("DWG-011", "Pump Station — Mechanical Layout",
                        DrawingDiscipline.MECHANICAL, "R0", LocalDate.of(2025, 4, 5), DrawingStatus.IFA, "BNK-MEPF", "1:50"),
                new DwgSpec("DWG-012", "Pavement Design — DEPRECATED (Pre-CBR-recast)",
                        DrawingDiscipline.CIVIL, "R0", LocalDate.of(2024, 8, 1), DrawingStatus.SUPERSEDED, "BNK-MAIN", "1:50"));

        List<DrawingRegister> entities = new ArrayList<>(drawings.size());
        for (DwgSpec d : drawings) {
            DrawingRegister dr = new DrawingRegister();
            dr.setProjectId(projectId);
            dr.setDrawingNumber(d.number());
            dr.setTitle(d.title());
            dr.setDiscipline(d.discipline());
            dr.setRevision(d.revision());
            dr.setRevisionDate(d.revDate());
            dr.setStatus(d.status());
            dr.setPackageCode(d.pkg());
            dr.setScale(d.scale());
            entities.add(dr);
        }
        drawingRegisterRepository.saveAll(entities);
        log.info("[BNK-SUPP] seeded {} drawing register entries (mix: IFC/IFA/SUPERSEDED)", entities.size());
    }

    // ────────────────────────── RFI Register ─────────────────────────
    private void seedRfiRegister(UUID projectId) {
        if (!rfiRegisterRepository.findByProjectId(projectId).isEmpty()) {
            log.info("[BNK-SUPP] RFI register already seeded — skipping");
            return;
        }
        // Status mix mapped to enum (no UNDER_REVIEW; use OVERDUE / OPEN / RESPONDED / CLOSED).
        // Plan: 2 OPEN, 3 UNDER_REVIEW, 4 RESPONDED, 1 CLOSED.
        // Mapping: UNDER_REVIEW → OPEN (since enum lacks UNDER_REVIEW). Keeps 5 OPEN total,
        // 4 RESPONDED, 1 CLOSED — preserves the spirit (operability for OPEN ones).
        record RfiSpec(String number, String subject, String description,
                       String raisedBy, String assignedTo, LocalDate raised, LocalDate due,
                       LocalDate closed, RfiStatus status, RfiPriority priority, String response) {}
        List<RfiSpec> rfis = List.of(
                new RfiSpec("RFI-BNK-001", "Wadi crossing CBR retest at Ch 18+200",
                        "CBR field result 5.8% vs design 8% at Wadi crossing. Request retest protocol.",
                        "Site Engineer (T. Swamy)", "PMC — Materials Lead",
                        LocalDate.of(2024, 11, 5), LocalDate.of(2024, 11, 15), LocalDate.of(2024, 11, 12),
                        RfiStatus.RESPONDED, RfiPriority.CRITICAL,
                        "Retest 2024-11-10 — CBR 8.2% achieved after compaction recast. Proceed."),
                new RfiSpec("RFI-BNK-002", "Bitumen VG-30 alternate source — Sohar Refinery",
                        "OQ8 supply schedule slipped. Request approval of Sohar Refinery alternate source for BC layer.",
                        "Materials Manager", "PMC — DGM",
                        LocalDate.of(2024, 12, 8), LocalDate.of(2024, 12, 20), LocalDate.of(2024, 12, 18),
                        RfiStatus.RESPONDED, RfiPriority.HIGH,
                        "Sohar Refinery VG-30 batch approved subject to Marshall stability test."),
                new RfiSpec("RFI-BNK-003", "Bridge pier P2 soft strata confirmation",
                        "N-value < 6 at 7 m depth during P2 excavation. Confirm pile cap depth revision.",
                        "Bridge Engineer (Nagarajan)", "Design Consultant",
                        LocalDate.of(2025, 1, 12), LocalDate.of(2025, 2, 5), null,
                        RfiStatus.OPEN, RfiPriority.CRITICAL, null),
                new RfiSpec("RFI-BNK-004", "MECA EIA condition — dust suppression Section C",
                        "MECA condition Decision 2023-117 §4.3 requires water-bowser frequency clarification.",
                        "Environment Officer", "MECA Liaison",
                        LocalDate.of(2025, 1, 25), LocalDate.of(2025, 2, 20), null,
                        RfiStatus.OPEN, RfiPriority.HIGH, null),
                new RfiSpec("RFI-BNK-005", "Median width tolerance at urban segment Ch 32-37",
                        "Urban segment ROW < 30 m. Confirm 4 m median acceptance vs design 5 m.",
                        "Site Engineer (A.K. Singh)", "PMC",
                        LocalDate.of(2025, 2, 5), LocalDate.of(2025, 2, 25), LocalDate.of(2025, 2, 22),
                        RfiStatus.RESPONDED, RfiPriority.MEDIUM,
                        "4 m acceptable for Ch 33+200 to 36+800 with safety barrier upgrade."),
                new RfiSpec("RFI-BNK-006", "Wadi flood return-period assumption",
                        "Hydrology study used 50-yr return; latest rainfall data suggests 100-yr review.",
                        "Bridge Engineer", "Design Consultant",
                        LocalDate.of(2025, 2, 18), LocalDate.of(2025, 3, 18), null,
                        RfiStatus.OPEN, RfiPriority.HIGH, null),
                new RfiSpec("RFI-BNK-007", "Aggregate source qualification — Wadi Aday quarry",
                        "Confirm acceptance criteria for Wadi Aday quarry GSB material.",
                        "QC Engineer", "PMC",
                        LocalDate.of(2025, 3, 2), LocalDate.of(2025, 3, 15), LocalDate.of(2025, 3, 14),
                        RfiStatus.RESPONDED, RfiPriority.MEDIUM,
                        "Wadi Aday qualified — 3 sample sets passed LAA/IDV. Approved."),
                new RfiSpec("RFI-BNK-008", "Concrete C30 cement replacement — fly ash %",
                        "Fly-ash availability constrained; request increase from 25% to 30% by mass.",
                        "QC Engineer", "PMC",
                        LocalDate.of(2025, 3, 18), LocalDate.of(2025, 4, 1), null,
                        RfiStatus.OPEN, RfiPriority.LOW, null),
                new RfiSpec("RFI-BNK-009", "Lighting pole spacing on bridge approach",
                        "Existing approach lighting overlaps proposed bridge lighting at pier P3.",
                        "Electrical Engineer", "PMC",
                        LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 20), null,
                        RfiStatus.OPEN, RfiPriority.MEDIUM, null),
                new RfiSpec("RFI-BNK-010", "Site mobilisation closeout audit",
                        "Mobilisation phase complete. Confirm closeout audit format and date.",
                        "Project Manager (Mohsin)", "Client Project Director",
                        LocalDate.of(2024, 10, 1), LocalDate.of(2024, 10, 30), LocalDate.of(2024, 10, 28),
                        RfiStatus.CLOSED, RfiPriority.LOW,
                        "Closeout audit conducted 2024-10-25. No findings. Mobilisation accepted."));

        List<RfiRegister> entities = new ArrayList<>(rfis.size());
        for (RfiSpec r : rfis) {
            RfiRegister rr = new RfiRegister();
            rr.setProjectId(projectId);
            rr.setRfiNumber(r.number());
            rr.setSubject(r.subject());
            rr.setDescription(r.description());
            rr.setRaisedBy(r.raisedBy());
            rr.setAssignedTo(r.assignedTo());
            rr.setRaisedDate(r.raised());
            rr.setDueDate(r.due());
            rr.setClosedDate(r.closed());
            rr.setStatus(r.status());
            rr.setPriority(r.priority());
            rr.setResponse(r.response());
            entities.add(rr);
        }
        rfiRegisterRepository.saveAll(entities);
        log.info("[BNK-SUPP] seeded {} RFI register entries (5 OPEN, 4 RESPONDED, 1 CLOSED)",
                entities.size());
    }

    // ────────────────────────── Transmittals ─────────────────────────
    private void seedTransmittals(UUID projectId) {
        if (!transmittalRepository.findByProjectId(projectId).isEmpty()) {
            log.info("[BNK-SUPP] transmittals already seeded — skipping");
            return;
        }
        record TrmSpec(String number, String subject, String fromParty, String toParty,
                       LocalDate sent, LocalDate due, TransmittalStatus status, String remarks) {}
        List<TrmSpec> trms = List.of(
                new TrmSpec("TRM-BNK-001", "Submission of Method Statement — Earthworks",
                        "BNK Contractor JV", "PMC — DGM",
                        LocalDate.of(2024, 9, 16), LocalDate.of(2024, 9, 30),
                        TransmittalStatus.ACKNOWLEDGED,
                        "Earthworks MS approved with minor markups. Re-submit redlined version."),
                new TrmSpec("TRM-BNK-002", "Submission of ITP — Bituminous Concrete",
                        "BNK Contractor JV", "PMC — Materials",
                        LocalDate.of(2024, 11, 5), LocalDate.of(2024, 11, 20),
                        TransmittalStatus.ACKNOWLEDGED,
                        "BC ITP approved unconditionally. Issued for construction."),
                new TrmSpec("TRM-BNK-003", "Monthly Progress Report — March 2025",
                        "BNK Contractor JV", "MoTC Region Office",
                        LocalDate.of(2025, 4, 5), LocalDate.of(2025, 4, 15),
                        TransmittalStatus.RECEIVED,
                        "MPR submitted with physical 32% / financial 28%. Awaiting acknowledgment."),
                new TrmSpec("TRM-BNK-004", "Drawing Issue Pack — Bridge GA Revision R1",
                        "Design Consultant", "BNK Contractor JV",
                        LocalDate.of(2024, 11, 10), LocalDate.of(2024, 11, 18),
                        TransmittalStatus.ACKNOWLEDGED,
                        "Bridge GA R1 (DWG-004 + DWG-007) issued for construction."),
                new TrmSpec("TRM-BNK-005", "Variation Order Proposal VO-002 — Lighting Scope Change",
                        "BNK Contractor JV", "PMC — DGM",
                        LocalDate.of(2025, 4, 15), LocalDate.of(2025, 5, 5),
                        TransmittalStatus.SENT,
                        "VO-002 proposal — high-mast lighting at urban segment per RFI-BNK-009."));

        List<UUID> drawingDocIds = documentRepository.findByProjectId(projectId).stream()
                .filter(d -> d.getDocumentNumber() != null
                        && (d.getDocumentNumber().startsWith("BNK-MS")
                            || d.getDocumentNumber().startsWith("BNK-ITP")
                            || d.getDocumentNumber().startsWith("BNK-PEP")
                            || d.getDocumentNumber().startsWith("BNK-DOC")))
                .map(com.bipros.document.domain.model.Document::getId)
                .toList();

        int trmCount = 0, itemCount = 0;
        for (int i = 0; i < trms.size(); i++) {
            TrmSpec t = trms.get(i);
            Transmittal tr = new Transmittal();
            tr.setProjectId(projectId);
            tr.setTransmittalNumber(t.number());
            tr.setSubject(t.subject());
            tr.setFromParty(t.fromParty());
            tr.setToParty(t.toParty());
            tr.setSentDate(t.sent());
            tr.setDueDate(t.due());
            tr.setStatus(t.status());
            tr.setRemarks(t.remarks());
            Transmittal saved = transmittalRepository.save(tr);
            trmCount++;

            // Link 1-2 documents per transmittal (round-robin from available docs)
            if (!drawingDocIds.isEmpty()) {
                int linkCount = 1 + (i % 2);
                for (int k = 0; k < linkCount; k++) {
                    UUID docId = drawingDocIds.get((i + k) % drawingDocIds.size());
                    TransmittalItem ti = new TransmittalItem();
                    ti.setTransmittalId(saved.getId());
                    ti.setDocumentId(docId);
                    ti.setPurpose(switch (t.status()) {
                        case ACKNOWLEDGED -> TransmittalPurpose.FOR_CONSTRUCTION;
                        case RECEIVED -> TransmittalPurpose.FOR_INFORMATION;
                        case SENT -> TransmittalPurpose.FOR_APPROVAL;
                        default -> TransmittalPurpose.FOR_REVIEW;
                    });
                    ti.setRemarks("Linked from transmittal " + t.number());
                    transmittalItemRepository.save(ti);
                    itemCount++;
                }
            }
        }
        log.info("[BNK-SUPP] seeded {} transmittals + {} transmittal items", trmCount, itemCount);
    }

    // ────────────────────────── Schedule Scenarios ─────────────────────────
    private void seedScheduleScenarios(UUID projectId) {
        if (!scheduleScenarioRepository.findByProjectId(projectId).isEmpty()) {
            log.info("[BNK-SUPP] schedule scenarios already seeded — skipping");
            return;
        }

        ScheduleScenario baseline = ScheduleScenario.builder()
                .projectId(projectId)
                .scenarioName("BNK Baseline — Sep 2024")
                .description("Original baseline schedule with 5-day work-week (Sun–Thu) and Gulf-market productivity norms.")
                .scenarioType(ScenarioType.BASELINE)
                .baseScheduleResultId(null)
                .projectDuration(720.0)
                .criticalPathLength(720.0)
                .totalCost(new BigDecimal("75000000.000"))
                .modifiedActivities("None")
                .status(ScenarioStatus.CALCULATED)
                .createdAt(Instant.parse("2024-09-01T00:00:00Z"))
                .build();
        scheduleScenarioRepository.save(baseline);

        ScheduleScenario crash = ScheduleScenario.builder()
                .projectId(projectId)
                .scenarioName("CRASH_BITUMINOUS — Recover Wadi Crossing Slip")
                .description("Crash scenario with double-shift bituminous paving (Section A + B) and additional crew "
                        + "to recover ~18-day slip from Wadi crossing soft-strata RFI-BNK-003.")
                .scenarioType(ScenarioType.CRASH)
                .baseScheduleResultId(baseline.getId())
                .projectDuration(702.0)
                .criticalPathLength(702.0)
                .totalCost(new BigDecimal("76250000.000"))
                .modifiedActivities("ACT-3.1,ACT-3.2,ACT-3.3,ACT-5.1")
                .status(ScenarioStatus.CALCULATED)
                .createdAt(Instant.parse("2025-01-15T00:00:00Z"))
                .build();
        scheduleScenarioRepository.save(crash);

        ScheduleScenario fastTrack = ScheduleScenario.builder()
                .projectId(projectId)
                .scenarioName("FAST_TRACK_BRIDGES — Parallel Bridge Construction")
                .description("Fast-track scenario running both wadi bridge structures (P1 + P2) in parallel "
                        + "instead of sequential. Saves 35 days but increases peak resource demand.")
                .scenarioType(ScenarioType.FAST_TRACK)
                .baseScheduleResultId(baseline.getId())
                .projectDuration(685.0)
                .criticalPathLength(685.0)
                .totalCost(new BigDecimal("77500000.000"))
                .modifiedActivities("ACT-7.1,ACT-7.2,ACT-7.3,ACT-7.4")
                .status(ScenarioStatus.DRAFT)
                .createdAt(Instant.parse("2025-02-20T00:00:00Z"))
                .build();
        scheduleScenarioRepository.save(fastTrack);

        log.info("[BNK-SUPP] seeded 3 schedule scenarios (BASELINE, CRASH_BITUMINOUS, FAST_TRACK_BRIDGES)");
    }

    // ────────────────────────── Activity Correlations ─────────────────────────
    /**
     * Same-WBS pair correlation rows (Pertmaster duration correlation, 0.6–0.8 range).
     * Caps at 30 rows; same-WBS pairs only — guarantees the correlation is meaningful
     * (similar work scope = duration drift moves in tandem).
     */
    private void seedActivityCorrelations(UUID projectId) {
        if (!activityCorrelationRepository.findByProjectId(projectId).isEmpty()) {
            log.info("[BNK-SUPP] activity correlations already seeded — skipping");
            return;
        }
        List<Activity> activities = activityRepository.findByProjectId(projectId);
        if (activities.size() < 2) {
            log.warn("[BNK-SUPP] fewer than 2 activities — skipping correlations");
            return;
        }

        // Group by WBS node
        Map<UUID, List<Activity>> byWbs = activities.stream()
                .filter(a -> a.getWbsNodeId() != null)
                .collect(Collectors.groupingBy(Activity::getWbsNodeId));

        Random rng = new Random("6155-supp".hashCode());
        int created = 0;
        int target = 30;
        for (List<Activity> group : byWbs.values()) {
            if (created >= target) break;
            if (group.size() < 2) continue;
            // Sort by id for deterministic pair selection
            group.sort(Comparator.comparing(Activity::getId));
            // Take all unique unordered pairs (a<b by id) within the group, up to remaining budget.
            for (int i = 0; i < group.size() - 1 && created < target; i++) {
                for (int j = i + 1; j < group.size() && created < target; j++) {
                    Activity a = group.get(i);
                    Activity b = group.get(j);
                    UUID aId = a.getId().compareTo(b.getId()) <= 0 ? a.getId() : b.getId();
                    UUID bId = a.getId().compareTo(b.getId()) <= 0 ? b.getId() : a.getId();
                    if (activityCorrelationRepository
                            .findByProjectIdAndActivityAIdAndActivityBId(projectId, aId, bId)
                            .isPresent()) continue;
                    double coef = 0.60 + rng.nextDouble() * 0.20; // 0.60 .. 0.80
                    ActivityCorrelation ac = ActivityCorrelation.builder()
                            .projectId(projectId)
                            .activityAId(aId)
                            .activityBId(bId)
                            .coefficient(Math.round(coef * 1000.0) / 1000.0)
                            .build();
                    activityCorrelationRepository.save(ac);
                    created++;
                }
            }
        }
        log.info("[BNK-SUPP] seeded {} activity correlations (same-WBS pairs, coef 0.60-0.80)", created);
    }

    // ────────────────────────── Cost Accounts ─────────────────────────
    /**
     * Standard road-project GL hierarchy — 3 levels per plan §4.25.
     * Cost accounts are global (no projectId), so use {@code findByCode} for idempotency.
     */
    private void seedCostAccounts() {
        if (costAccountRepository.findByCode("1000").isPresent()) {
            log.info("[BNK-SUPP] cost accounts already seeded — skipping");
            return;
        }

        record CASpec(String code, String name, String description, String parentCode, int sortOrder) {}
        List<CASpec> specs = List.of(
                // L1 (3 root accounts)
                new CASpec("1000", "Direct Costs", "Direct works on the project — earthworks, pavement, structures", null, 10),
                new CASpec("2000", "Indirect Costs", "Site overheads, supervision, equipment hire", null, 20),
                new CASpec("3000", "Other / Reserves", "Contingency, escalation, financing", null, 30),
                // L2 under 1000 — Direct Costs
                new CASpec("1100", "Earthworks Materials", "Borrow material, cut/fill aggregates", "1000", 110),
                new CASpec("1200", "Pavement Materials", "GSB, WMM, DBM, BC, asphalt", "1000", 120),
                new CASpec("1300", "Structures Materials", "Concrete, rebar, prestressing strand", "1000", 130),
                new CASpec("1400", "Direct Labour", "Carriageway, paving, structures crews", "1000", 140),
                new CASpec("1500", "Direct Plant & Equipment", "Pavers, rollers, excavators (productive hours)", "1000", 150),
                // L2 under 2000 — Indirect Costs
                new CASpec("2100", "Site Establishment", "Camp, site office, utilities, fencing", "2000", 210),
                new CASpec("2200", "Supervision & Management", "PM, supervisors, planners, QS staff", "2000", 220),
                new CASpec("2300", "HSE & Quality", "PPE, signage, environmental compliance, lab", "2000", 230),
                // L2 under 3000 — Reserves
                new CASpec("3100", "Contingency", "Project contingency reserve", "3000", 310),
                new CASpec("3200", "Escalation", "Material/labour price escalation", "3000", 320),
                // L3 under 1100 (Earthworks Materials)
                new CASpec("1110", "Borrow Material", "Imported borrow material", "1100", 1110),
                new CASpec("1120", "Aggregates", "Crushed aggregate sub-base", "1100", 1120),
                // L3 under 1200 (Pavement Materials)
                new CASpec("1210", "Bitumen VG-30", "VG-30 binder for BC layer", "1200", 1210),
                new CASpec("1220", "Asphalt Mix", "Plant-mixed BC and DBM", "1200", 1220));

        // Save in topological order (parents first). Since input is already L1→L2→L3, two-pass
        // resolution by code is sufficient.
        Map<String, UUID> idByCode = new java.util.HashMap<>();
        int saved = 0;
        for (CASpec s : specs) {
            CostAccount ca = new CostAccount();
            ca.setCode(s.code());
            ca.setName(s.name());
            ca.setDescription(s.description());
            ca.setSortOrder(s.sortOrder());
            if (s.parentCode() != null) {
                UUID pid = idByCode.get(s.parentCode());
                if (pid == null) {
                    // Fallback: lookup in DB (covers re-runs / partial seeding)
                    pid = costAccountRepository.findByCode(s.parentCode())
                            .map(CostAccount::getId).orElse(null);
                }
                ca.setParentId(pid);
            }
            CostAccount persisted = costAccountRepository.save(ca);
            idByCode.put(s.code(), persisted.getId());
            saved++;
        }
        log.info("[BNK-SUPP] seeded {} cost accounts (3-level GL hierarchy)", saved);
    }

    // ────────────────────────── Calendar Exceptions ─────────────────────────
    private void seedCalendarExceptions(UUID projectId) {
        if (!calendarExceptionRepository.findByCalendarId(
                calendarRepository.findAll().stream()
                        .filter(c -> "OMAN-5day".equals(c.getCode()))
                        .findFirst().map(Calendar::getId).orElse(UUID.randomUUID()))
                .isEmpty()) {
            log.info("[BNK-SUPP] calendar exceptions already seeded — skipping");
            return;
        }
        Calendar cal = calendarRepository.findAll().stream()
                .filter(c -> "OMAN-5day".equals(c.getCode()))
                .findFirst().orElse(null);
        if (cal == null) {
            log.warn("[BNK-SUPP] OMAN-5day calendar not found — skipping exceptions");
            return;
        }
        record HolidaySpec(LocalDate date, String name) {}
        List<HolidaySpec> holidays = List.of(
                new HolidaySpec(LocalDate.of(2025, 7, 23), "Renaissance Day"),
                new HolidaySpec(LocalDate.of(2025, 11, 18), "National Day"),
                new HolidaySpec(LocalDate.of(2025, 3, 30), "Eid Al Fitr (placeholder)"),
                new HolidaySpec(LocalDate.of(2025, 6, 6), "Eid Al Adha (placeholder)"),
                new HolidaySpec(LocalDate.of(2025, 9, 4), "Prophet's Birthday"),
                new HolidaySpec(LocalDate.of(2026, 1, 1), "New Year's Day"));
        int count = 0;
        for (HolidaySpec h : holidays) {
            CalendarException ex = new CalendarException();
            ex.setCalendarId(cal.getId());
            ex.setExceptionDate(h.date());
            ex.setDayType(DayType.NON_WORKING);
            ex.setName(h.name());
            calendarExceptionRepository.save(ex);
            count++;
        }
        log.info("[BNK-SUPP] seeded {} calendar exceptions (Oman public holidays)", count);
    }

    // ────────────────────────── Activity Codes ─────────────────────────
    private void seedActivityCodes(UUID projectId) {
        if (activityCodeRepository.findByProjectId(projectId).size() > 0) {
            log.info("[BNK-SUPP] activity codes already seeded — skipping");
            return;
        }
        List<Activity> activities = activityRepository.findByProjectId(projectId);
        if (activities.isEmpty()) return;

        String[][] codeDefs = {
                {"Phase", "WBS Phase classification"},
                {"Stretch", "Corridor stretch assignment"},
                {"Trade", "Construction trade classification"},
                {"Supervisor", "Responsible supervisor"},
                {"Cost-Center", "Cost center allocation"}};
        String[] phaseValues = {"MOBILISATION", "EARTHWORKS", "DRAINAGE", "PAVEMENT", "STRUCTURES"};
        String[] stretchValues = {"BNK-S1", "BNK-S2", "BNK-S3", "BNK-S4"};
        String[] tradeValues = {"EARTHWORK", "PAVEMENT", "DRAINAGE", "BRIDGE", "UTILITIES"};
        String[] supervisorValues = {"T-SWAMY", "NAGARAJAN", "AKSINGH", "ANBAZHAGAN"};
        String[] costCenterValues = {"CC-1000", "CC-2000", "CC-3000"};
        String[][] allValues = {phaseValues, stretchValues, tradeValues, supervisorValues, costCenterValues};

        Random rng = new Random("6155-supp".hashCode());
        int codeCount = 0, assignCount = 0;
        for (int i = 0; i < codeDefs.length; i++) {
            ActivityCode ac = new ActivityCode();
            ac.setName(codeDefs[i][0]);
            ac.setDescription(codeDefs[i][1]);
            ac.setScope(CodeScope.PROJECT);
            ac.setProjectId(projectId);
            ac.setSortOrder(i);
            ActivityCode saved = activityCodeRepository.save(ac);
            codeCount++;

            String[] values = allValues[i];
            for (Activity a : activities) {
                if (rng.nextInt(5) > 1) continue; // ~40% assignment rate
                String value = values[rng.nextInt(values.length)];
                ActivityCodeAssignment assignment = new ActivityCodeAssignment();
                assignment.setActivityId(a.getId());
                assignment.setActivityCodeId(saved.getId());
                assignment.setCodeValue(value);
                activityCodeAssignmentRepository.save(assignment);
                assignCount++;
            }
        }
        log.info("[BNK-SUPP] seeded {} activity codes + {} assignments", codeCount, assignCount);
    }

    // ────────────────────────── EVM Calculations ─────────────────────────
    private void seedEvmCalculations(UUID projectId) {
        if (evmCalculationRepository.findByProjectIdOrderByDataDateDesc(projectId).size() > 0) {
            log.info("[BNK-SUPP] EVM calculations already seeded — skipping");
            return;
        }
        List<WbsNode> wbsNodes = wbsNodeRepository.findByProjectIdOrderBySortOrder(projectId);
        List<WbsNode> leaves = wbsNodes.stream()
                .filter(n -> n.getWbsLevel() != null && n.getWbsLevel() >= 3)
                .toList();
        if (leaves.isEmpty()) {
            leaves = wbsNodes.stream()
                    .filter(n -> n.getWbsLevel() != null && n.getWbsLevel() >= 2)
                    .toList();
        }
        if (leaves.isEmpty()) return;

        LocalDate[] periodDates = {
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 29)};
        Random rng = new Random(DETERMINISTIC_SEED);
        int count = 0;
        for (WbsNode node : leaves) {
            BigDecimal bac = node.getBudgetCrores() != null
                    ? node.getBudgetCrores().multiply(new BigDecimal("1000000"))
                    : new BigDecimal("5000000");
            for (int p = 0; p < periodDates.length; p++) {
                double progressPct = Math.min(1.0, 0.15 + p * 0.20 + rng.nextDouble() * 0.1);
                BigDecimal pv = bac.multiply(BigDecimal.valueOf(Math.min(1.0, 0.20 + p * 0.22)));
                BigDecimal ev = bac.multiply(BigDecimal.valueOf(progressPct));
                BigDecimal ac = ev.multiply(BigDecimal.valueOf(1.03 + rng.nextDouble() * 0.05));
                BigDecimal sv = ev.subtract(pv);
                BigDecimal cv = ev.subtract(ac);
                double spi = pv.signum() > 0 ? ev.doubleValue() / pv.doubleValue() : 1.0;
                double cpi = ac.signum() > 0 ? ev.doubleValue() / ac.doubleValue() : 1.0;
                BigDecimal eac = cpi > 0 ? bac.divide(BigDecimal.valueOf(cpi), 2, java.math.RoundingMode.HALF_UP) : bac;
                BigDecimal etc = eac.subtract(ac);

                EvmCalculation evm = new EvmCalculation();
                evm.setProjectId(projectId);
                evm.setWbsNodeId(node.getId());
                evm.setDataDate(periodDates[p]);
                evm.setBudgetAtCompletion(bac.setScale(2, java.math.RoundingMode.HALF_UP));
                evm.setPlannedValue(pv.setScale(2, java.math.RoundingMode.HALF_UP));
                evm.setEarnedValue(ev.setScale(2, java.math.RoundingMode.HALF_UP));
                evm.setActualCost(ac.setScale(2, java.math.RoundingMode.HALF_UP));
                evm.setScheduleVariance(sv.setScale(2, java.math.RoundingMode.HALF_UP));
                evm.setCostVariance(cv.setScale(2, java.math.RoundingMode.HALF_UP));
                evm.setSchedulePerformanceIndex(Math.round(spi * 1000.0) / 1000.0);
                evm.setCostPerformanceIndex(Math.round(cpi * 1000.0) / 1000.0);
                evm.setEstimateAtCompletion(eac.setScale(2, java.math.RoundingMode.HALF_UP));
                evm.setEstimateToComplete(etc.setScale(2, java.math.RoundingMode.HALF_UP));
                evm.setVarianceAtCompletion(bac.subtract(eac).setScale(2, java.math.RoundingMode.HALF_UP));
                evm.setEvmTechnique(EvmTechnique.ACTIVITY_PERCENT_COMPLETE);
                evm.setEtcMethod(EtcMethod.CPI_BASED);
                evm.setPerformancePercentComplete(Math.round(progressPct * 10000.0) / 100.0);
                evmCalculationRepository.save(evm);
                count++;
            }
        }
        log.info("[BNK-SUPP] seeded {} EVM calculations ({} leaves × 4 periods)", count, leaves.size());
    }

    // ────────────────────────── Daily Activity Resource Output ─────────────────────────
    private void seedDailyActivityResourceOutput(UUID projectId) {
        if (resourceDailyLogRepository.count() > 0) {
            log.info("[BNK-SUPP] resource daily logs already present — skipping");
            return;
        }
        List<Activity> activities = activityRepository.findByProjectId(projectId).stream()
                .filter(a -> a.getStatus() == com.bipros.activity.domain.model.ActivityStatus.IN_PROGRESS)
                .limit(20)
                .toList();
        if (activities.isEmpty()) return;

        List<ResourceAssignment> assignments = resourceAssignmentRepository.findByProjectId(projectId);
        Map<UUID, List<ResourceAssignment>> byActivity = assignments.stream()
                .collect(Collectors.groupingBy(ResourceAssignment::getActivityId));

        LocalDate from = DEFAULT_DATA_DATE.minusDays(30);
        Random rng = new Random(DETERMINISTIC_SEED);
        // Dedupe globally: uk_resource_daily_log forbids duplicate (resource_id, log_date)
        // — without this, two activities sharing a resource collide.
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (Activity a : activities) {
            List<ResourceAssignment> actAssignments = byActivity.getOrDefault(a.getId(), List.of());
            List<UUID> resourceIds = actAssignments.stream()
                    .map(ResourceAssignment::getResourceId)
                    .distinct()
                    .limit(3)
                    .toList();
            if (resourceIds.isEmpty()) continue;

            LocalDate d = from;
            while (!d.isAfter(DEFAULT_DATA_DATE.minusDays(1))) {
                if (d.getDayOfWeek() == java.time.DayOfWeek.FRIDAY
                        || d.getDayOfWeek() == java.time.DayOfWeek.SATURDAY) {
                    d = d.plusDays(1);
                    continue;
                }
                for (UUID resId : resourceIds) {
                    if (!seen.add(resId + "|" + d)) continue;
                    ResourceDailyLog log = new ResourceDailyLog();
                    log.setResourceId(resId);
                    log.setLogDate(d);
                    log.setPlannedUnits(8.0);
                    log.setActualUnits(6.0 + rng.nextDouble() * 2.5);
                    log.setUtilisationPercent(75.0 + rng.nextDouble() * 20.0);
                    log.setWbsPackageCode(a.getCode());
                    log.setRemarks("Auto-seeded by BNK supplemental seeder");
                    resourceDailyLogRepository.save(log);
                    count++;
                }
                d = d.plusDays(1);
            }
        }
        log.info("[BNK-SUPP] seeded {} resource daily log entries", count);
    }

    // ──────────────── Daily Activity-Resource Outputs (UI-facing) ────────────────
    /**
     * Populates {@code project.daily_activity_resource_outputs} — the table behind the
     * "Daily Outputs" tab and the source of the "Capacity Utilization" report. One row
     * per (date × activity × resource) for the last 60 working days, drawn from
     * {@link ResourceAssignment} and the activity's productivity norm so the
     * Capacity-vs-Norm view actually has something to compute.
     *
     * <p>Quantity executed = norm × deterministic variance factor (0.65–1.05) so the
     * UI's traffic-light bands (≥100 % green, 80–99 % yellow, &lt;80 % red) all show
     * up. When no norm is found for the activity, falls back to 1.0 unit/day with
     * unit "LS" (lump-sum) — keeps the row valid without hiding the gap.
     */
    private void seedDailyActivityResourceOutputs(UUID projectId) {
        if (dailyActivityResourceOutputRepository
                .findByProjectIdOrderByOutputDateDescIdAsc(projectId).size() > 0) {
            log.info("[BNK-SUPP] daily activity resource outputs already present — skipping");
            return;
        }

        // Only IN_PROGRESS / COMPLETED activities — NOT_STARTED has no real outputs to show.
        List<Activity> activities = activityRepository.findByProjectId(projectId).stream()
                .filter(a -> a.getStatus() == com.bipros.activity.domain.model.ActivityStatus.IN_PROGRESS
                          || a.getStatus() == com.bipros.activity.domain.model.ActivityStatus.COMPLETED)
                .toList();
        if (activities.isEmpty()) {
            log.warn("[BNK-SUPP] no in-progress/completed activities — skipping daily outputs");
            return;
        }

        List<ResourceAssignment> assignments = resourceAssignmentRepository.findByProjectId(projectId);
        if (assignments.isEmpty()) {
            log.warn("[BNK-SUPP] no resource assignments — skipping daily outputs");
            return;
        }
        Map<UUID, List<ResourceAssignment>> byActivity = assignments.stream()
                .collect(Collectors.groupingBy(ResourceAssignment::getActivityId));

        // Resource lookup once (used to map a resource to its type for type-scoped norms).
        Map<UUID, Resource> resourcesById = resourceRepository.findAll().stream()
                .collect(Collectors.toMap(Resource::getId, r -> r, (a, b) -> a));

        Random rng = new Random(DETERMINISTIC_SEED);
        LocalDate dataDate = DEFAULT_DATA_DATE;
        LocalDate from = dataDate.minusDays(60);
        int count = 0;
        int skippedNoNorm = 0;
        int skippedNoResources = 0;

        for (Activity a : activities) {
            List<ResourceAssignment> actAssignments = byActivity.getOrDefault(a.getId(), List.of());
            // Cap at 3 distinct resources per activity to keep volume sane (~60 days × 3 ≈ 180 rows/activity).
            List<UUID> resourceIds = actAssignments.stream()
                    .map(ResourceAssignment::getResourceId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .limit(3)
                    .toList();
            if (resourceIds.isEmpty()) {
                skippedNoResources++;
                continue;
            }

            // Look up productivity norms for the activity's WorkActivity once (multiple norms ⇒
            // pick one that matches the resource's type, else fall back to the first).
            List<ProductivityNorm> norms = a.getWorkActivityId() != null
                    ? productivityNormRepository.findByWorkActivityId(a.getWorkActivityId())
                    : List.of();

            // Pick a per-resource norm (resource-scoped > type-scoped > first available > null).
            Map<UUID, ProductivityNorm> normByResource = new java.util.HashMap<>();
            for (UUID resId : resourceIds) {
                Resource res = resourcesById.get(resId);
                ProductivityNorm picked = null;
                if (res != null) {
                    picked = norms.stream()
                            .filter(n -> n.getResource() != null && resId.equals(n.getResource().getId()))
                            .findFirst().orElse(null);
                    if (picked == null && res.getResourceType() != null) {
                        UUID typeId = res.getResourceType().getId();
                        picked = norms.stream()
                                .filter(n -> n.getResourceType() != null
                                        && typeId.equals(n.getResourceType().getId()))
                                .findFirst().orElse(null);
                    }
                }
                if (picked == null && !norms.isEmpty()) picked = norms.get(0);
                normByResource.put(resId, picked);
            }
            if (normByResource.values().stream().allMatch(java.util.Objects::isNull)) {
                skippedNoNorm++;
            }

            LocalDate d = from;
            // Activity window: don't generate output rows after actual finish (for COMPLETED) or
            // after data date (for IN_PROGRESS). Skip days before activity actually started.
            LocalDate actStart = a.getActualStartDate() != null ? a.getActualStartDate()
                    : a.getPlannedStartDate() != null ? a.getPlannedStartDate() : from;
            LocalDate actEnd = a.getActualFinishDate() != null ? a.getActualFinishDate()
                    : dataDate.minusDays(1);

            while (!d.isAfter(dataDate.minusDays(1))) {
                // Skip weekends (Friday/Saturday in Oman).
                if (d.getDayOfWeek() == java.time.DayOfWeek.FRIDAY
                        || d.getDayOfWeek() == java.time.DayOfWeek.SATURDAY) {
                    d = d.plusDays(1);
                    continue;
                }
                if (d.isBefore(actStart) || d.isAfter(actEnd)) {
                    d = d.plusDays(1);
                    continue;
                }
                for (UUID resId : resourceIds) {
                    ProductivityNorm norm = normByResource.get(resId);
                    BigDecimal normPerDay = norm != null ? norm.getOutputPerDay() : null;
                    String normUnit = norm != null ? norm.getUnit() : null;
                    if (normPerDay == null || normPerDay.signum() <= 0) {
                        normPerDay = BigDecimal.ONE;
                        normUnit = (normUnit == null || normUnit.isBlank()) ? "LS" : normUnit;
                    }
                    if (normUnit == null || normUnit.isBlank()) normUnit = "Cum";

                    // Variance band tuned so ~25 % red, ~35 % yellow, ~40 % green.
                    double pick = rng.nextDouble();
                    double factor = pick < 0.25 ? 0.55 + rng.nextDouble() * 0.20    // red 0.55–0.75
                                  : pick < 0.60 ? 0.80 + rng.nextDouble() * 0.18    // yellow 0.80–0.98
                                                : 1.00 + rng.nextDouble() * 0.10;   // green 1.00–1.10
                    BigDecimal qty = normPerDay
                            .multiply(BigDecimal.valueOf(factor))
                            .setScale(3, java.math.RoundingMode.HALF_UP);
                    double hours = 7.5 + rng.nextDouble() * 1.5;   // 7.5–9.0 h
                    double days = Math.round((hours / 8.0) * 100.0) / 100.0;

                    DailyActivityResourceOutput out = DailyActivityResourceOutput.builder()
                            .projectId(projectId)
                            .outputDate(d)
                            .activityId(a.getId())
                            .resourceId(resId)
                            .qtyExecuted(qty)
                            .unit(normUnit)
                            .hoursWorked(Math.round(hours * 100.0) / 100.0)
                            .daysWorked(days)
                            .remarks(factor < 0.80 ? "Below norm — site condition variance"
                                  : factor > 1.05 ? "Above norm — favourable conditions"
                                                  : null)
                            .build();
                    dailyActivityResourceOutputRepository.save(out);
                    count++;
                }
                d = d.plusDays(1);
            }
        }
        log.info("[BNK-SUPP] seeded {} daily activity-resource outputs across {} activities "
                + "(skipped: {} no-resources, {} no-norm)",
                count, activities.size(), skippedNoResources, skippedNoNorm);
    }

    // ──────────────── Manpower Operational Wiring ────────────────
    /**
     * Wires the manpower {@code Resource} rows (codes starting {@code BNK-MP-}) into the
     * three operational tables that drive the DPR / Daily Outputs / Capacity Util / Resource
     * Deployment tabs:
     * <ol>
     *   <li>{@code resource.resource_assignments} — every IN_PROGRESS / COMPLETED activity
     *       gets two manpower trades (round-robin) alongside its existing equipment
     *       assignments. Without this, manpower never appears in the activity-detail
     *       Resources tab and never feeds Daily Outputs.</li>
     *   <li>{@code project.daily_activity_resource_outputs} — for each new manpower
     *       assignment × working day in the activity window, write a productivity row.
     *       Quantity uses the trade's productivity norm (from the Capacity_Utilization
     *       workbook's Manpower utilization sheet) where available, else falls back to
     *       a tier-based default. Capacity Util now picks up manpower lines too.</li>
     *   <li>{@code project.daily_resource_deployments} — for each manpower trade × last
     *       30 working days, write a deployment row of type MANPOWER. The DPR-deployment
     *       grid then shows manpower head-counts next to the equipment hours.</li>
     * </ol>
     *
     * <p>Idempotent — sentinel-checks each table for existing manpower rows before
     * writing.
     */
    private void seedManpowerOperationalWiring(UUID projectId) {
        // Find manpower resources by code prefix (set by OmanRoadProjectSeeder).
        List<com.bipros.resource.domain.model.Resource> manpower = resourceRepository.findAll().stream()
                .filter(r -> r.getCode() != null && r.getCode().startsWith("BNK-MP-"))
                .toList();
        if (manpower.isEmpty()) {
            log.info("[BNK-SUPP] no manpower resources (code prefix BNK-MP-) — skipping manpower wiring");
            return;
        }

        // Sentinel: are manpower assignments already present?
        long existingManpowerAssignments = resourceAssignmentRepository.findByProjectId(projectId).stream()
                .filter(ra -> manpower.stream().anyMatch(mp -> mp.getId().equals(ra.getResourceId())))
                .count();

        Random rng = new Random(DETERMINISTIC_SEED + 1L);
        LocalDate dataDate = DEFAULT_DATA_DATE;

        // ─── Step 1: ResourceAssignments (manpower → activities) ───
        List<Activity> targetActivities = activityRepository.findByProjectId(projectId).stream()
                .filter(a -> a.getStatus() == com.bipros.activity.domain.model.ActivityStatus.IN_PROGRESS
                          || a.getStatus() == com.bipros.activity.domain.model.ActivityStatus.COMPLETED)
                .toList();
        int newAssignments = 0;
        if (existingManpowerAssignments == 0L) {
            int idx = 0;
            for (Activity a : targetActivities) {
                // Two manpower trades per activity, round-robin, deterministic.
                for (int k = 0; k < 2; k++) {
                    com.bipros.resource.domain.model.Resource mp = manpower.get((idx + k * 7) % manpower.size());
                    double durationDays = a.getOriginalDuration() != null ? a.getOriginalDuration() : 5.0;
                    double plannedUnits = durationDays * 8.0;
                    BigDecimal plannedCost = mp.getCostPerUnit() == null
                            ? BigDecimal.ZERO
                            : mp.getCostPerUnit()
                                .multiply(BigDecimal.valueOf(plannedUnits / 8.0))
                                .setScale(2, java.math.RoundingMode.HALF_UP);
                    ResourceAssignment ra = ResourceAssignment.builder()
                            .projectId(projectId)
                            .activityId(a.getId())
                            .resourceId(mp.getId())
                            .plannedUnits(plannedUnits)
                            .remainingUnits(plannedUnits)
                            .rateType("STANDARD")
                            .plannedStartDate(a.getPlannedStartDate())
                            .plannedFinishDate(a.getPlannedFinishDate())
                            .plannedCost(plannedCost)
                            .build();
                    resourceAssignmentRepository.save(ra);
                    newAssignments++;
                }
                idx++;
            }
        }

        // ─── Step 2: Daily Activity Resource Outputs for manpower assignments ───
        // Generate only for manpower assignments that don't yet have outputs.
        Set<UUID> manpowerResourceIds = manpower.stream()
                .map(com.bipros.resource.domain.model.Resource::getId)
                .collect(Collectors.toSet());
        // Map productivity norm by resourceId where the resource is bound directly to a norm.
        Map<UUID, com.bipros.resource.domain.model.ProductivityNorm> normByResource = new java.util.HashMap<>();
        for (com.bipros.resource.domain.model.ProductivityNorm pn : productivityNormRepository.findAll()) {
            if (pn.getResource() != null && pn.getOutputPerDay() != null
                    && pn.getOutputPerDay().signum() > 0
                    && manpowerResourceIds.contains(pn.getResource().getId())) {
                normByResource.putIfAbsent(pn.getResource().getId(), pn);
            }
        }

        int newOutputs = 0;
        Set<String> outputSeen = new HashSet<>();
        // Pre-load existing manpower outputs so we don't recreate.
        dailyActivityResourceOutputRepository.findByProjectIdOrderByOutputDateDescIdAsc(projectId).stream()
                .filter(o -> manpowerResourceIds.contains(o.getResourceId()))
                .forEach(o -> outputSeen.add(o.getActivityId() + "|" + o.getResourceId() + "|" + o.getOutputDate()));

        List<ResourceAssignment> manpowerAssignments = resourceAssignmentRepository.findByProjectId(projectId).stream()
                .filter(ra -> manpowerResourceIds.contains(ra.getResourceId()))
                .toList();

        LocalDate from = dataDate.minusDays(60);
        for (ResourceAssignment ra : manpowerAssignments) {
            Activity act = activityRepository.findById(ra.getActivityId()).orElse(null);
            if (act == null) continue;
            if (act.getStatus() == com.bipros.activity.domain.model.ActivityStatus.NOT_STARTED) continue;
            LocalDate actStart = act.getActualStartDate() != null ? act.getActualStartDate()
                    : act.getPlannedStartDate() != null ? act.getPlannedStartDate() : from;
            LocalDate actEnd = act.getActualFinishDate() != null ? act.getActualFinishDate()
                    : dataDate.minusDays(1);
            com.bipros.resource.domain.model.Resource mp = manpower.stream()
                    .filter(m -> m.getId().equals(ra.getResourceId()))
                    .findFirst().orElse(null);
            if (mp == null) continue;
            com.bipros.resource.domain.model.ProductivityNorm norm = normByResource.get(ra.getResourceId());
            BigDecimal normPerDay = norm != null ? norm.getOutputPerDay() : null;
            String unit = norm != null ? norm.getUnit() : null;
            if (normPerDay == null || normPerDay.signum() <= 0) {
                normPerDay = BigDecimal.valueOf(8); // 8 hours / man-day fallback
                unit = "Day";
            }
            if (unit == null || unit.isBlank()) unit = "Day";

            LocalDate d = from;
            while (!d.isAfter(dataDate.minusDays(1))) {
                if (d.getDayOfWeek() == java.time.DayOfWeek.FRIDAY
                        || d.getDayOfWeek() == java.time.DayOfWeek.SATURDAY) {
                    d = d.plusDays(1);
                    continue;
                }
                if (d.isBefore(actStart) || d.isAfter(actEnd)) {
                    d = d.plusDays(1);
                    continue;
                }
                String key = act.getId() + "|" + ra.getResourceId() + "|" + d;
                if (outputSeen.add(key)) {
                    double pick = rng.nextDouble();
                    double factor = pick < 0.25 ? 0.55 + rng.nextDouble() * 0.20
                                  : pick < 0.60 ? 0.80 + rng.nextDouble() * 0.18
                                                : 1.00 + rng.nextDouble() * 0.10;
                    BigDecimal qty = normPerDay.multiply(BigDecimal.valueOf(factor))
                            .setScale(3, java.math.RoundingMode.HALF_UP);
                    double hours = 7.5 + rng.nextDouble() * 1.5;
                    double days = Math.round((hours / 8.0) * 100.0) / 100.0;
                    DailyActivityResourceOutput out = DailyActivityResourceOutput.builder()
                            .projectId(projectId)
                            .outputDate(d)
                            .activityId(act.getId())
                            .resourceId(ra.getResourceId())
                            .qtyExecuted(qty)
                            .unit(unit)
                            .hoursWorked(Math.round(hours * 100.0) / 100.0)
                            .daysWorked(days)
                            .remarks("Manpower — auto-seeded by BNK supplemental seeder")
                            .build();
                    dailyActivityResourceOutputRepository.save(out);
                    newOutputs++;
                }
                d = d.plusDays(1);
            }
        }

        // ─── Step 3: Daily Resource Deployments — manpower head-count rows ───
        // Skip if any MANPOWER deployments already exist.
        long existingMpDeployments = dailyResourceDeploymentRepository.findAll().stream()
                .filter(drd -> projectId.equals(drd.getProjectId())
                        && drd.getResourceType() == DeploymentResourceType.MANPOWER)
                .count();
        int newDeployments = 0;
        if (existingMpDeployments == 0L) {
            LocalDate dpFrom = dataDate.minusDays(30);
            LocalDate d = dpFrom;
            while (!d.isAfter(dataDate.minusDays(1))) {
                if (d.getDayOfWeek() == java.time.DayOfWeek.FRIDAY
                        || d.getDayOfWeek() == java.time.DayOfWeek.SATURDAY) {
                    d = d.plusDays(1);
                    continue;
                }
                // Each working day, deploy 8 manpower trades (rotating subset).
                int dayOffset = (int) java.time.temporal.ChronoUnit.DAYS.between(dpFrom, d);
                for (int k = 0; k < Math.min(8, manpower.size()); k++) {
                    com.bipros.resource.domain.model.Resource mp = manpower.get(
                            (dayOffset * 3 + k * 5) % manpower.size());
                    int planned = 4 + rng.nextInt(8);              // 4–11 men planned
                    int deployed = Math.max(1, planned - rng.nextInt(3)); // 1–planned deployed
                    double hours = deployed * (7.5 + rng.nextDouble() * 1.5);
                    double idle = deployed * (rng.nextDouble() * 0.5);
                    DailyResourceDeployment drd = DailyResourceDeployment.builder()
                            .projectId(projectId)
                            .logDate(d)
                            .resourceType(DeploymentResourceType.MANPOWER)
                            .resourceDescription(mp.getName())
                            .resourceId(mp.getId())
                            .resourceRoleId(mp.getRole() != null ? mp.getRole().getId() : null)
                            .nosPlanned(planned)
                            .nosDeployed(deployed)
                            .hoursWorked(Math.round(hours * 10.0) / 10.0)
                            .idleHours(Math.round(idle * 10.0) / 10.0)
                            .remarks(deployed < planned ? "Short by " + (planned - deployed) : null)
                            .build();
                    dailyResourceDeploymentRepository.save(drd);
                    newDeployments++;
                }
                d = d.plusDays(1);
            }
        }

        log.info("[BNK-SUPP] manpower wiring — assignments: {} new ({} pre-existing), "
                + "daily outputs: {} new, deployments: {} new",
                newAssignments, existingManpowerAssignments, newOutputs, newDeployments);
    }
}
