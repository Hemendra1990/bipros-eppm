package com.bipros.api.config.seeder;

import com.bipros.permit.domain.model.ApprovalStatus;
import com.bipros.permit.domain.model.GasTestResult;
import com.bipros.permit.domain.model.Permit;
import com.bipros.permit.domain.model.PermitApproval;
import com.bipros.permit.domain.model.PermitGasTest;
import com.bipros.permit.domain.model.PermitStatus;
import com.bipros.permit.domain.model.PermitTypeTemplate;
import com.bipros.permit.domain.model.PermitWorker;
import com.bipros.permit.domain.model.RiskLevel;
import com.bipros.permit.domain.model.WorkShift;
import com.bipros.permit.domain.model.WorkerRole;
import com.bipros.permit.domain.repository.PermitApprovalRepository;
import com.bipros.permit.domain.repository.PermitGasTestRepository;
import com.bipros.permit.domain.repository.PermitRepository;
import com.bipros.permit.domain.repository.PermitTypeTemplateRepository;
import com.bipros.permit.domain.repository.PermitWorkerRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@Profile("seed")
@Order(152)
@RequiredArgsConstructor
public class OmanPermitSeeder implements CommandLineRunner {

    private static final String PROJECT_CODE = "6155";
    private static final long DETERMINISTIC_SEED = 6155L;
    private static final LocalDate DEFAULT_DATA_DATE = LocalDate.of(2026, 4, 29);

    private static final int PERMIT_COUNT = 30;
    private static final int LOOKBACK_DAYS = 60;

    private static final String[] TYPE_CODES = {"HOT_WORK", "EXCAVATION", "CONFINED_SPACE", "WORKING_AT_HEIGHTS"};

    private static final String[] LOCATIONS = {
            "Section A — Km 0+000 to 10+000",
            "Section B — Km 10+000 to 20+000",
            "Section C — Km 20+000 to 30+000",
            "Section D — Km 30+000 to 41+000",
            "Wadi Bridge — P1/P2 Substructure",
            "Barka Junction — Signalisation Area",
            "Nakhal Roundabout — Finishing Zone"
    };

    private static final String[] CHAINAGE_MARKERS = {
            "Ch 2+500", "Ch 8+300", "Ch 14+700", "Ch 18+200",
            "Ch 24+100", "Ch 31+500", "Ch 36+800"
    };

    private static final String[] SUPERVISORS = {"T. Swamy", "Nagarajan", "A.K. Singh", "Anbazhagan"};

    private static final String[] WORKER_NAMES = {
            "Ahmed Al-Balushi", "Rajesh Kumar", "Mohammed Al-Hinai", "Suresh Nair",
            "Khalid Al-Rashdi", "Prakash Sharma", "Ali Al-Habsi", "Vijay Singh",
            "Salim Al-Busaidi", "Ramesh Patel", "Hassan Al-Lawati", "Deepak Yadav"
    };

    private static final String[] TRADES = {
            "Welder", "Electrician", "Rigger", "Pipe Fitter",
            "Scaffolder", "Mason", "Steel Fixer", "Carpenter",
            "Equipment Operator", "Safety Watcher", "Helper", "Foreman"
    };

    private final ProjectRepository projectRepository;
    private final PermitRepository permitRepository;
    private final PermitTypeTemplateRepository permitTypeTemplateRepository;
    private final PermitWorkerRepository permitWorkerRepository;
    private final PermitGasTestRepository permitGasTestRepository;
    private final PermitApprovalRepository permitApprovalRepository;

    @Override
    public void run(String... args) {
        Project project = projectRepository.findByCode(PROJECT_CODE).orElse(null);
        if (project == null) {
            log.warn("[BNK-PERMIT] project '{}' not found — skipping", PROJECT_CODE);
            return;
        }
        UUID projectId = project.getId();

        if (permitRepository.findByProjectIdRecent(projectId,
                org.springframework.data.domain.PageRequest.of(0, 1)).hasContent()) {
            log.info("[BNK-PERMIT] permits already present for project '{}' — skipping", PROJECT_CODE);
            return;
        }

        Map<String, PermitTypeTemplate> templatesByCode = permitTypeTemplateRepository.findAll().stream()
                .collect(Collectors.toMap(PermitTypeTemplate::getCode, t -> t, (a, b) -> a));

        List<PermitTypeTemplate> availableTypes = java.util.Arrays.stream(TYPE_CODES)
                .map(templatesByCode::get)
                .filter(java.util.Objects::nonNull)
                .toList();

        if (availableTypes.isEmpty()) {
            log.warn("[BNK-PERMIT] no permit type templates found (run PermitPackSeeder first) — skipping");
            return;
        }

        log.info("[BNK-PERMIT] seeding {} permits for project '{}'", PERMIT_COUNT, PROJECT_CODE);
        Random rng = new Random(DETERMINISTIC_SEED);

        Instant now = DEFAULT_DATA_DATE.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant lookbackStart = now.minusSeconds((long) LOOKBACK_DAYS * 86400);

        PermitStatus[] statusCycle = {
                PermitStatus.CLOSED, PermitStatus.APPROVED, PermitStatus.IN_PROGRESS,
                PermitStatus.EXPIRED, PermitStatus.CLOSED, PermitStatus.APPROVED,
                PermitStatus.IN_PROGRESS, PermitStatus.CLOSED
        };

        int created = 0;
        for (int i = 0; i < PERMIT_COUNT; i++) {
            PermitTypeTemplate type = availableTypes.get(i % availableTypes.size());
            long dayOffset = (long) (rng.nextInt(LOOKBACK_DAYS));
            Instant startAt = lookbackStart.plusSeconds(dayOffset * 86400L + 6 * 3600L);
            int durationHours = 4 + rng.nextInt(20);
            Instant endAt = startAt.plusSeconds((long) durationHours * 3600L);

            PermitStatus status = statusCycle[i % statusCycle.length];
            String location = LOCATIONS[i % LOCATIONS.length];
            String chainage = CHAINAGE_MARKERS[i % CHAINAGE_MARKERS.length];
            String supervisor = SUPERVISORS[i % SUPERVISORS.length];

            String permitCode = String.format("BNK-PTW-%d-%03d",
                    DEFAULT_DATA_DATE.getYear(), i + 1);

            RiskLevel riskLevel = type.getDefaultRiskLevel() != null
                    ? type.getDefaultRiskLevel()
                    : RiskLevel.MEDIUM;

            Permit permit = new Permit();
            permit.setProjectId(projectId);
            permit.setPermitCode(permitCode);
            permit.setPermitTypeTemplateId(type.getId());
            permit.setStatus(status);
            permit.setRiskLevel(riskLevel);
            permit.setSupervisorName(supervisor);
            permit.setLocationZone(location);
            permit.setChainageMarker(chainage);
            permit.setStartAt(startAt);
            permit.setEndAt(endAt);
            permit.setValidFrom(startAt);
            permit.setValidTo(endAt);
            permit.setShift(rng.nextBoolean() ? WorkShift.DAY : WorkShift.NIGHT);
            permit.setTaskDescription(type.getName() + " — " + location + " — " + chainage);
            permit.setCurrentApprovalStep(3);
            permit.setApprovalsCompleted(status.ordinal() >= PermitStatus.APPROVED.ordinal() ? 3 : rng.nextInt(3));
            permit.setTotalApprovalsRequired(3);
            permit.setDeclarationAcceptedAt(startAt.minusSeconds(3600));
            permit.setQrToken(UUID.randomUUID().toString().replace("-", "").substring(0, 24));

            if (status == PermitStatus.CLOSED) {
                permit.setClosedAt(endAt.plusSeconds(1800));
                permit.setCloseRemarks("Work completed — site cleared, area inspected, permit closed out.");
            }
            if (status == PermitStatus.EXPIRED) {
                permit.setExpiredAt(endAt);
            }

            Permit savedPermit = permitRepository.save(permit);

            seedPermitWorkers(savedPermit.getId(), i, rng);
            if (type.isGasTestRequired()) {
                seedPermitGasTest(savedPermit.getId(), startAt, rng);
            }
            seedPermitApprovals(savedPermit.getId(), status, startAt, rng);
            created++;
        }

        log.info("[BNK-PERMIT] seeded {} permits with workers, gas tests, and approvals", created);
    }

    private void seedPermitWorkers(UUID permitId, int index, Random rng) {
        int workerCount = 2 + (index % 2);
        WorkerRole[] roles = {WorkerRole.PRINCIPAL, WorkerRole.HELPER, WorkerRole.FIRE_WATCH};

        for (int w = 0; w < workerCount; w++) {
            String name = WORKER_NAMES[(index * 3 + w) % WORKER_NAMES.length];
            String trade = TRADES[(index * 2 + w) % TRADES.length];
            WorkerRole role = roles[w % roles.length];
            String civilId = String.format("OM-%d%04d", 80 + rng.nextInt(20), rng.nextInt(9999));

            PermitWorker worker = new PermitWorker();
            worker.setPermitId(permitId);
            worker.setFullName(name);
            worker.setCivilId(civilId);
            worker.setNationality(w == 0 ? "Omani" : "Indian");
            worker.setTrade(trade);
            worker.setRoleOnPermit(role);
            worker.setTrainingCertsJson("[\"HSE-Induction\", \"Toolbox-Talk-" + (index + 1) + "\"]");
            permitWorkerRepository.save(worker);
        }
    }

    private void seedPermitGasTest(UUID permitId, Instant testedAt, Random rng) {
        PermitGasTest gt = new PermitGasTest();
        gt.setPermitId(permitId);
        gt.setLelPct(BigDecimal.valueOf(0.1 + rng.nextDouble() * 1.5).setScale(2, RoundingMode.HALF_UP));
        gt.setO2Pct(BigDecimal.valueOf(20.5 + rng.nextDouble() * 0.4).setScale(2, RoundingMode.HALF_UP));
        gt.setH2sPpm(BigDecimal.valueOf(0.1 + rng.nextDouble() * 2.0).setScale(2, RoundingMode.HALF_UP));
        gt.setCoPpm(BigDecimal.valueOf(1.0 + rng.nextDouble() * 5.0).setScale(2, RoundingMode.HALF_UP));
        gt.setResult(GasTestResult.PASS);
        gt.setTestedAt(testedAt.minusSeconds(1800));
        gt.setInstrumentSerial("GD-7000-" + String.format("%04d", rng.nextInt(9999)));
        permitGasTestRepository.save(gt);
    }

    private void seedPermitApprovals(UUID permitId, PermitStatus status, Instant startAt, Random rng) {
        record ApprovalStep(int stepNo, String label, String role) {}
        List<ApprovalStep> steps = List.of(
                new ApprovalStep(1, "Application Submitted", "ROLE_FOREMAN"),
                new ApprovalStep(2, "Site Engineer Review", "ROLE_SITE_ENGINEER"),
                new ApprovalStep(3, "HSE Officer Clearance", "ROLE_HSE_OFFICER")
        );

        boolean isApprovedOrBeyond = status.ordinal() >= PermitStatus.APPROVED.ordinal();

        for (ApprovalStep step : steps) {
            PermitApproval pa = new PermitApproval();
            pa.setPermitId(permitId);
            pa.setStepNo(step.stepNo());
            pa.setLabel(step.label());
            pa.setRole(step.role());

            if (isApprovedOrBeyond) {
                pa.setStatus(ApprovalStatus.APPROVED);
                pa.setReviewedAt(startAt.minusSeconds((long) (4 - step.stepNo()) * 3600));
                pa.setRemarks("Approved — conditions verified.");
            } else if (step.stepNo() <= 1) {
                pa.setStatus(ApprovalStatus.APPROVED);
                pa.setReviewedAt(startAt.minusSeconds(7200));
                pa.setRemarks("Submitted and acknowledged.");
            } else {
                pa.setStatus(ApprovalStatus.PENDING);
            }
            permitApprovalRepository.save(pa);
        }
    }


}
