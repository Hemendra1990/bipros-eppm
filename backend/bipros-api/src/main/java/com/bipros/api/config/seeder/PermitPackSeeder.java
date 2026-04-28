package com.bipros.api.config.seeder;

import com.bipros.permit.domain.model.ApprovalStepTemplate;
import com.bipros.permit.domain.model.NightWorkPolicy;
import com.bipros.permit.domain.model.PermitPack;
import com.bipros.permit.domain.model.PermitPackType;
import com.bipros.permit.domain.model.PermitTypePpe;
import com.bipros.permit.domain.model.PermitTypeTemplate;
import com.bipros.permit.domain.model.PpeItemTemplate;
import com.bipros.permit.domain.model.RiskLevel;
import com.bipros.permit.domain.repository.ApprovalStepTemplateRepository;
import com.bipros.permit.domain.repository.PermitPackRepository;
import com.bipros.permit.domain.repository.PermitPackTypeRepository;
import com.bipros.permit.domain.repository.PermitTypePpeRepository;
import com.bipros.permit.domain.repository.PermitTypeTemplateRepository;
import com.bipros.permit.domain.repository.PpeItemTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Seeds the universe of permit packs / types / PPE / approval flows the PTW module relies on.
 *
 * <p>Runs in every profile (HSE auditors require these to exist in production too).
 * Idempotent: each row is upserted by its natural key, so re-runs do not duplicate.
 */
@Component
@Order(50)
@Slf4j
@RequiredArgsConstructor
public class PermitPackSeeder implements CommandLineRunner {

    private final PermitPackRepository packRepository;
    private final PermitTypeTemplateRepository typeRepository;
    private final PermitPackTypeRepository packTypeRepository;
    private final PpeItemTemplateRepository ppeRepository;
    private final PermitTypePpeRepository typePpeRepository;
    private final ApprovalStepTemplateRepository approvalStepRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Map<String, UUID> ppe = seedPpe();
        Map<String, UUID> types = seedTypes(ppe);
        seedApprovalFlows(types);
        seedPacks(types);
        log.info("[PermitPackSeeder] {} packs, {} types, {} PPE items present",
                packRepository.count(), typeRepository.count(), ppeRepository.count());
    }

    // ── PPE catalogue ─────────────────────────────────────────────────────

    private Map<String, UUID> seedPpe() {
        Object[][] items = {
                {"HARD_HAT", "Hard Hat / Helmet", "helmet", true, 10},
                {"HI_VIS_VEST", "Hi-Vis Safety Vest", "vest", true, 20},
                {"SAFETY_BOOTS", "Safety Boots (Steel Toe)", "boot", true, 30},
                {"GLOVES", "Safety Gloves", "glove", true, 40},
                {"GOGGLES", "Safety Goggles / Face Shield", "goggles", false, 50},
                {"FULL_BODY_HARNESS", "Full Body Harness", "harness", false, 60},
                {"RESPIRATOR", "Respirator", "respirator", false, 70},
                {"HEARING_PROTECTION", "Hearing Protection", "ear", false, 80},
                {"FACE_SHIELD", "Welding Face Shield", "shield", false, 90},
                {"FR_COVERALLS", "Flame-Resistant Coveralls", "coverall", false, 100},
                {"RUBBER_INSULATING_GLOVES", "Rubber Insulating Gloves", "glove", false, 110},
                {"SCBA", "Self-Contained Breathing Apparatus", "scba", false, 120},
                {"LIFEJACKET", "Lifejacket", "lifejacket", false, 130},
                {"FALL_ARRESTOR", "Fall Arrestor", "arrestor", false, 140}
        };
        Map<String, UUID> result = new java.util.HashMap<>();
        for (Object[] r : items) {
            String code = (String) r[0];
            UUID id = ppeRepository.findByCode(code).map(PpeItemTemplate::getId).orElseGet(() -> {
                PpeItemTemplate p = new PpeItemTemplate();
                p.setCode(code);
                p.setName((String) r[1]);
                p.setIconKey((String) r[2]);
                p.setMandatory((Boolean) r[3]);
                p.setSortOrder((Integer) r[4]);
                return ppeRepository.save(p).getId();
            });
            result.put(code, id);
        }
        return result;
    }

    // ── Permit type catalogue ─────────────────────────────────────────────

    private Map<String, UUID> seedTypes(Map<String, UUID> ppe) {
        // code, name, defaultRisk, jsa, gas, isolation, blast, dive, nightPolicy, maxHrs, minRole, color, icon, sort, ppeCodes(csv)
        Object[][] rows = {
                row("HOT_WORK", "Hot Work / Welding", RiskLevel.HIGH, true, true, false, false, false, NightWorkPolicy.RESTRICTED, 24, "ROLE_PROJECT_MANAGER", "#E07A1F", "flame", 10,
                        "HARD_HAT,HI_VIS_VEST,SAFETY_BOOTS,GLOVES,FACE_SHIELD,FR_COVERALLS"),
                row("CONFINED_SPACE", "Confined Space Entry", RiskLevel.HIGH, true, true, false, false, false, NightWorkPolicy.LIMITED, 12, "ROLE_PROJECT_MANAGER", "#7C3AED", "warning", 20,
                        "HARD_HAT,HI_VIS_VEST,SAFETY_BOOTS,GLOVES,RESPIRATOR,SCBA"),
                row("LIFTING", "Lifting Operations", RiskLevel.HIGH, true, false, false, false, false, NightWorkPolicy.RESTRICTED, 8, "ROLE_PROJECT_MANAGER", "#0EA5A4", "crane", 30,
                        "HARD_HAT,HI_VIS_VEST,SAFETY_BOOTS,GLOVES"),
                row("ELECTRICAL", "Electrical Works", RiskLevel.MEDIUM, true, false, false, false, false, NightWorkPolicy.ALLOWED, 168, "ROLE_HSE_OFFICER", "#D4AF37", "bolt", 40,
                        "HARD_HAT,HI_VIS_VEST,SAFETY_BOOTS,RUBBER_INSULATING_GLOVES,GOGGLES"),
                row("EXCAVATION", "Excavation & Trenching", RiskLevel.MEDIUM, true, false, false, false, false, NightWorkPolicy.ALLOWED, 168, "ROLE_SITE_ENGINEER", "#3B82F6", "shovel", 50,
                        "HARD_HAT,HI_VIS_VEST,SAFETY_BOOTS,GLOVES,GOGGLES"),
                row("NIGHT_SHIFT", "Night Shift Work", RiskLevel.MEDIUM, true, false, false, false, false, NightWorkPolicy.ALLOWED, 12, "ROLE_PROJECT_MANAGER", "#1E293B", "moon", 60,
                        "HARD_HAT,HI_VIS_VEST,SAFETY_BOOTS,GLOVES"),
                row("TRAFFIC_MGMT", "Traffic Management", RiskLevel.LOW, false, false, false, false, false, NightWorkPolicy.ALLOWED, 168, "ROLE_SITE_ENGINEER", "#FB923C", "traffic", 70,
                        "HARD_HAT,HI_VIS_VEST,SAFETY_BOOTS"),
                row("CIVIL_WORKS", "Civil Works", RiskLevel.LOW, false, false, false, false, false, NightWorkPolicy.ALLOWED, 168, "ROLE_SITE_ENGINEER", "#B8962E", "tower", 80,
                        "HARD_HAT,HI_VIS_VEST,SAFETY_BOOTS,GLOVES"),
                row("WORKING_AT_HEIGHTS", "Working at Heights", RiskLevel.HIGH, true, false, false, false, false, NightWorkPolicy.LIMITED, 12, "ROLE_PROJECT_MANAGER", "#9B2C2C", "ladder", 90,
                        "HARD_HAT,HI_VIS_VEST,SAFETY_BOOTS,GLOVES,FULL_BODY_HARNESS,FALL_ARRESTOR"),
                row("SCAFFOLD_ERECTION", "Scaffold Erection", RiskLevel.MEDIUM, true, false, false, false, false, NightWorkPolicy.LIMITED, 24, "ROLE_HSE_OFFICER", "#6B7280", "scaffold", 100,
                        "HARD_HAT,HI_VIS_VEST,SAFETY_BOOTS,GLOVES,FULL_BODY_HARNESS"),
                row("DEMOLITION", "Demolition", RiskLevel.HIGH, true, false, false, false, false, NightWorkPolicy.RESTRICTED, 48, "ROLE_PROJECT_MANAGER", "#475569", "demo", 110,
                        "HARD_HAT,HI_VIS_VEST,SAFETY_BOOTS,GLOVES,GOGGLES,RESPIRATOR,HEARING_PROTECTION"),
                row("ROOF_WORK", "Roof Work", RiskLevel.HIGH, true, false, false, false, false, NightWorkPolicy.LIMITED, 12, "ROLE_HSE_OFFICER", "#EF4444", "roof", 120,
                        "HARD_HAT,HI_VIS_VEST,SAFETY_BOOTS,GLOVES,FULL_BODY_HARNESS,FALL_ARRESTOR"),
                row("COLD_WORK", "Cold Work / General Maintenance", RiskLevel.LOW, false, false, false, false, false, NightWorkPolicy.ALLOWED, 168, "ROLE_SITE_ENGINEER", "#94A3B8", "wrench", 130,
                        "HARD_HAT,HI_VIS_VEST,SAFETY_BOOTS,GLOVES"),
                row("HOT_TAPPING", "Hot Tapping", RiskLevel.HIGH, true, true, true, false, false, NightWorkPolicy.RESTRICTED, 8, "ROLE_PROJECT_MANAGER", "#F97316", "tap", 140,
                        "HARD_HAT,HI_VIS_VEST,SAFETY_BOOTS,GLOVES,FACE_SHIELD,FR_COVERALLS,SCBA"),
                row("PRESSURE_TEST", "Pressure / Hydro Testing", RiskLevel.HIGH, true, false, false, false, false, NightWorkPolicy.LIMITED, 12, "ROLE_PROJECT_MANAGER", "#0E7490", "gauge", 150,
                        "HARD_HAT,HI_VIS_VEST,SAFETY_BOOTS,GLOVES,GOGGLES,FACE_SHIELD"),
                row("RADIOGRAPHY", "Radiography (NDT)", RiskLevel.HIGH, true, false, false, false, false, NightWorkPolicy.LIMITED, 12, "ROLE_HSE_OFFICER", "#A21CAF", "radiation", 160,
                        "HARD_HAT,HI_VIS_VEST,SAFETY_BOOTS,GLOVES"),
                row("DIVING", "Diving Operations", RiskLevel.HIGH, true, false, false, false, true, NightWorkPolicy.RESTRICTED, 8, "ROLE_PROJECT_MANAGER", "#0369A1", "diver", 170,
                        "LIFEJACKET,SCBA"),
                row("LOTO", "Lock-Out Tag-Out", RiskLevel.HIGH, true, false, true, false, false, NightWorkPolicy.LIMITED, 24, "ROLE_HSE_OFFICER", "#1F2937", "lock", 180,
                        "HARD_HAT,HI_VIS_VEST,SAFETY_BOOTS,GLOVES,RUBBER_INSULATING_GLOVES,FACE_SHIELD"),
                row("BLASTING", "Blasting / Explosives", RiskLevel.HIGH, true, false, false, true, false, NightWorkPolicy.RESTRICTED, 6, "ROLE_PROJECT_MANAGER", "#B45309", "explosion", 190,
                        "HARD_HAT,HI_VIS_VEST,SAFETY_BOOTS,GLOVES,GOGGLES,HEARING_PROTECTION"),
                row("MACHINE_GUARDING", "Machine Guarding Override", RiskLevel.MEDIUM, true, false, false, false, false, NightWorkPolicy.ALLOWED, 24, "ROLE_HSE_OFFICER", "#64748B", "gear", 200,
                        "HARD_HAT,HI_VIS_VEST,SAFETY_BOOTS,GLOVES,GOGGLES,HEARING_PROTECTION"),
                row("CHEMICAL_HANDLING", "Chemical Handling", RiskLevel.MEDIUM, true, false, false, false, false, NightWorkPolicy.ALLOWED, 24, "ROLE_HSE_OFFICER", "#16A34A", "flask", 210,
                        "HARD_HAT,HI_VIS_VEST,SAFETY_BOOTS,GLOVES,GOGGLES,RESPIRATOR,FR_COVERALLS"),
                row("TRACK_POSSESSION", "Track Possession", RiskLevel.HIGH, true, false, false, false, false, NightWorkPolicy.LIMITED, 8, "ROLE_PROJECT_MANAGER", "#0F766E", "rail", 220,
                        "HARD_HAT,HI_VIS_VEST,SAFETY_BOOTS,GLOVES"),
                row("OHE_WORK", "Overhead Equipment (OHE) Work", RiskLevel.HIGH, true, false, true, false, false, NightWorkPolicy.LIMITED, 8, "ROLE_HSE_OFFICER", "#FBBF24", "spark", 230,
                        "HARD_HAT,HI_VIS_VEST,SAFETY_BOOTS,RUBBER_INSULATING_GLOVES,FULL_BODY_HARNESS"),
                row("AIRSIDE", "Airside Work", RiskLevel.HIGH, true, false, false, false, false, NightWorkPolicy.LIMITED, 24, "ROLE_PROJECT_MANAGER", "#0EA5E9", "plane", 240,
                        "HARD_HAT,HI_VIS_VEST,SAFETY_BOOTS,GLOVES,HEARING_PROTECTION")
        };

        Map<String, UUID> result = new java.util.HashMap<>();
        for (Object[] r : rows) {
            String code = (String) r[0];
            UUID id = typeRepository.findByCode(code).map(PermitTypeTemplate::getId).orElseGet(() -> {
                PermitTypeTemplate t = new PermitTypeTemplate();
                t.setCode(code);
                t.setName((String) r[1]);
                t.setDefaultRiskLevel((RiskLevel) r[2]);
                t.setJsaRequired((Boolean) r[3]);
                t.setGasTestRequired((Boolean) r[4]);
                t.setIsolationRequired((Boolean) r[5]);
                t.setBlastingRequired((Boolean) r[6]);
                t.setDivingRequired((Boolean) r[7]);
                t.setNightWorkPolicy((NightWorkPolicy) r[8]);
                t.setMaxDurationHours((Integer) r[9]);
                t.setMinApprovalRole((String) r[10]);
                t.setColorHex((String) r[11]);
                t.setIconKey((String) r[12]);
                t.setSortOrder((Integer) r[13]);
                return typeRepository.save(t).getId();
            });
            result.put(code, id);

            // PPE links
            String ppeCsv = (String) r[14];
            for (String ppeCode : ppeCsv.split(",")) {
                UUID ppeId = ppe.get(ppeCode.trim());
                if (ppeId == null) continue;
                if (typePpeRepository.findByPermitTypeTemplateIdAndPpeItemTemplateId(id, ppeId).isEmpty()) {
                    PermitTypePpe link = new PermitTypePpe();
                    link.setPermitTypeTemplateId(id);
                    link.setPpeItemTemplateId(ppeId);
                    link.setRequired(true);
                    typePpeRepository.save(link);
                }
            }
        }
        return result;
    }

    private static Object[] row(Object... values) {
        return values;
    }

    // ── Approval flow templates ───────────────────────────────────────────

    private void seedApprovalFlows(Map<String, UUID> types) {
        // Default 5-step flow applied to all types unless overridden below.
        for (Map.Entry<String, UUID> e : types.entrySet()) {
            seedDefaultFlow(e.getValue(), needsPmStep(e.getKey()), gasTestRoleHook(e.getKey()));
        }
    }

    private boolean needsPmStep(String code) {
        return List.of("HOT_WORK", "CONFINED_SPACE", "LIFTING", "WORKING_AT_HEIGHTS", "DEMOLITION",
                "ROOF_WORK", "HOT_TAPPING", "PRESSURE_TEST", "RADIOGRAPHY", "DIVING", "BLASTING",
                "TRACK_POSSESSION", "AIRSIDE", "NIGHT_SHIFT").contains(code);
    }

    private boolean gasTestRoleHook(String code) {
        return List.of("CONFINED_SPACE", "HOT_WORK", "HOT_TAPPING").contains(code);
    }

    private void seedDefaultFlow(UUID typeId, boolean pmRequired, boolean gasGate) {
        // Idempotency: if there's already at least one step for this type, skip.
        if (!approvalStepRepository.findByPermitTypeTemplateIdOrderByStepNoAsc(typeId).isEmpty()) return;

        addStep(typeId, 1, "Application Submitted", "ROLE_FOREMAN", null, false);
        addStep(typeId, 2, "Site Engineer Review", "ROLE_SITE_ENGINEER", null, false);
        addStep(typeId, 3, "HSE Officer Clearance", "ROLE_HSE_OFFICER", "MEDIUM,HIGH", false);
        if (pmRequired) {
            addStep(typeId, 4, "Project Manager Final Approval", "ROLE_PROJECT_MANAGER", "HIGH", false);
        }
    }

    private void addStep(UUID typeId, int stepNo, String label, String role,
                         String requiredForRiskLevels, boolean optional) {
        ApprovalStepTemplate s = new ApprovalStepTemplate();
        s.setPermitTypeTemplateId(typeId);
        s.setStepNo(stepNo);
        s.setLabel(label);
        s.setRole(role);
        s.setRequiredForRiskLevels(requiredForRiskLevels);
        s.setOptional(optional);
        approvalStepRepository.save(s);
    }

    // ── Industry packs ────────────────────────────────────────────────────

    private void seedPacks(Map<String, UUID> types) {
        seedPack("ROAD", "Road / Highway Construction", 10,
                List.of("TRAFFIC_MGMT", "EXCAVATION", "HOT_WORK", "CONFINED_SPACE", "LIFTING",
                        "CIVIL_WORKS", "ELECTRICAL", "NIGHT_SHIFT"), types);
        seedPack("BUILDING", "Building / Vertical Construction", 20,
                List.of("CIVIL_WORKS", "WORKING_AT_HEIGHTS", "SCAFFOLD_ERECTION", "DEMOLITION", "ROOF_WORK",
                        "HOT_WORK", "ELECTRICAL", "LIFTING", "EXCAVATION", "NIGHT_SHIFT"), types);
        seedPack("OIL_GAS", "Oil & Gas (Upstream / Downstream)", 30,
                List.of("HOT_WORK", "COLD_WORK", "CONFINED_SPACE", "HOT_TAPPING", "PRESSURE_TEST",
                        "RADIOGRAPHY", "DIVING", "LOTO", "EXCAVATION", "LIFTING"), types);
        seedPack("POWER_PLANT", "Power Plant (Thermal / Nuclear / Hydro)", 40,
                List.of("LOTO", "HOT_WORK", "CONFINED_SPACE", "RADIOGRAPHY", "WORKING_AT_HEIGHTS",
                        "ELECTRICAL", "EXCAVATION"), types);
        seedPack("TUNNEL_METRO", "Tunnel / Metro / Underground", 50,
                List.of("EXCAVATION", "BLASTING", "CONFINED_SPACE", "ELECTRICAL", "LIFTING", "HOT_WORK",
                        "NIGHT_SHIFT"), types);
        seedPack("RAILWAY", "Railway / Track Works", 60,
                List.of("TRACK_POSSESSION", "HOT_WORK", "OHE_WORK", "NIGHT_SHIFT", "EXCAVATION", "LIFTING"), types);
        seedPack("MARINE", "Marine / Offshore / Port", 70,
                List.of("DIVING", "HOT_WORK", "CONFINED_SPACE", "LIFTING", "COLD_WORK"), types);
        seedPack("AIRPORT", "Airport / Aviation Construction", 80,
                List.of("AIRSIDE", "HOT_WORK", "EXCAVATION", "NIGHT_SHIFT", "LIFTING"), types);
        seedPack("MANUFACTURING", "Manufacturing / Industrial Plant", 90,
                List.of("LOTO", "HOT_WORK", "CONFINED_SPACE", "MACHINE_GUARDING", "ELECTRICAL",
                        "CHEMICAL_HANDLING"), types);
        seedPack("RENEWABLES", "Renewables (Solar / Wind)", 100,
                List.of("WORKING_AT_HEIGHTS", "ELECTRICAL", "LOTO", "LIFTING", "HOT_WORK"), types);
        seedPack("MINING", "Mining / Quarrying", 110,
                List.of("BLASTING", "EXCAVATION", "CONFINED_SPACE", "LOTO", "ELECTRICAL", "LIFTING"), types);
        seedPack("GENERAL", "General (catch-all)", 120,
                List.of("HOT_WORK", "COLD_WORK", "EXCAVATION", "LIFTING", "ELECTRICAL", "WORKING_AT_HEIGHTS"), types);
    }

    private void seedPack(String code, String name, int sortOrder, List<String> typeCodes,
                          Map<String, UUID> types) {
        UUID packId = packRepository.findByCode(code).map(PermitPack::getId).orElseGet(() -> {
            PermitPack p = new PermitPack();
            p.setCode(code);
            p.setName(name);
            p.setActive(true);
            p.setSortOrder(sortOrder);
            return packRepository.save(p).getId();
        });
        int order = 10;
        for (String tCode : typeCodes) {
            UUID typeId = types.get(tCode);
            if (typeId == null) {
                log.warn("[PermitPackSeeder] pack {} references missing type {}", code, tCode);
                continue;
            }
            if (packTypeRepository.findByPackIdAndPermitTypeTemplateId(packId, typeId).isEmpty()) {
                PermitPackType l = new PermitPackType();
                l.setPackId(packId);
                l.setPermitTypeTemplateId(typeId);
                l.setSortOrder(order);
                packTypeRepository.save(l);
            }
            order += 10;
        }
    }
}
