package com.bipros.api.config.seeder;

import com.bipros.api.config.seeder.OmanRoadProjectWorkbookReader.DirectLabourRow;
import com.bipros.api.config.seeder.OmanRoadProjectWorkbookReader.IndirectStaffRow;
import com.bipros.resource.domain.model.LabourCategory;
import com.bipros.resource.domain.model.LabourDesignation;
import com.bipros.resource.domain.model.LabourGrade;
import com.bipros.resource.domain.model.LabourStatus;
import com.bipros.resource.domain.model.NationalityType;
import com.bipros.resource.domain.repository.LabourDesignationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@Profile("seed")
@Order(140)
@RequiredArgsConstructor
public class OmanLabourMasterSeeder implements CommandLineRunner {

    private final LabourDesignationRepository designationRepo;
    private final ObjectMapper objectMapper;
    private final OmanRoadProjectWorkbookReader workbookReader;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (designationRepo.count() > 0) {
            log.info("Labour designations already present — skipping Oman seed");
            return;
        }
        // 1. Curated JSON master (44 designations with rich metadata).
        List<Map<String, Object>> rows = readDataset();
        List<LabourDesignation> jsonDesignations = rows.stream().map(this::toDesignation).toList();
        designationRepo.saveAll(jsonDesignations);

        // 2. Top up with Excel "Resource" sheet positions (DPR-internal File 1) so every
        //    role listed in the daily-site-resource report has a corresponding LabourDesignation.
        Set<String> seenKeys = new HashSet<>();
        for (LabourDesignation d : jsonDesignations) seenKeys.add(normalize(d.getDesignation()));
        Set<String> usedCodes = new HashSet<>();
        for (LabourDesignation d : jsonDesignations) usedCodes.add(d.getCode());

        List<LabourDesignation> excelDesignations = new ArrayList<>();
        if (workbookReader.exists()) {
            try {
                workbookReader.withWorkbook(OmanRoadProjectWorkbookReader.DPR_INTERNAL_PATH, wb -> {
                    List<IndirectStaffRow> indirect = workbookReader.readIndirectStaff(wb);
                    int n = 1;
                    for (IndirectStaffRow row : indirect) {
                        if (row.position() == null) continue;
                        String name = clean(row.position());
                        String key = normalize(name);
                        if (key.isEmpty() || !seenKeys.add(key)) continue;
                        excelDesignations.add(buildInferred(name, /*indirect=*/true, n++, usedCodes));
                    }
                    List<DirectLabourRow> direct = workbookReader.readDirectLabour(wb);
                    int m = 1;
                    for (DirectLabourRow row : direct) {
                        if (row.position() == null) continue;
                        String name = clean(row.position());
                        String key = normalize(name);
                        if (key.isEmpty() || !seenKeys.add(key)) continue;
                        excelDesignations.add(buildInferred(name, /*indirect=*/false, m++, usedCodes));
                    }
                    return null;
                });
            } catch (Exception e) {
                log.warn("Could not merge Excel labour positions ({}). JSON-only seed kept.", e.getMessage());
            }
        }
        if (!excelDesignations.isEmpty()) {
            designationRepo.saveAll(excelDesignations);
        }

        log.info("Seeded {} Oman labour designations ({} from JSON + {} from Excel Resource sheet)",
                jsonDesignations.size() + excelDesignations.size(),
                jsonDesignations.size(),
                excelDesignations.size());
        log.info("Note: per-project deployments are NOT seeded by this seeder — "
            + "use scripts/seed-oman-labour.sh --with-deployments <projectId> to bind designations to a project.");
    }

    private List<Map<String, Object>> readDataset() throws Exception {
        try (InputStream in = new ClassPathResource("oman-labour-master.json").getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<>() {});
        }
    }

    @SuppressWarnings("unchecked")
    private LabourDesignation toDesignation(Map<String, Object> r) {
        return LabourDesignation.builder()
            .code((String) r.get("code"))
            .designation((String) r.get("designation"))
            .category(LabourCategory.valueOf((String) r.get("category")))
            .trade((String) r.get("trade"))
            .grade(LabourGrade.valueOf((String) r.get("grade")))
            .nationality(NationalityType.valueOf((String) r.get("nationality")))
            .experienceYearsMin(((Number) r.get("experienceYearsMin")).intValue())
            .defaultDailyRate(new BigDecimal(r.get("defaultDailyRate").toString()))
            .currency("OMR")
            .skills((List<String>) r.getOrDefault("skills", List.of()))
            .certifications((List<String>) r.getOrDefault("certifications", List.of()))
            .status(LabourStatus.ACTIVE)
            .sortOrder(((Number) r.getOrDefault("sortOrder", 0)).intValue())
            .build();
    }

    /** Normalised match key — lowercase, single-spaced, punctuation-stripped. */
    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Trim + collapse internal whitespace. Preserves the Excel's casing for display. */
    private static String clean(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }

    /**
     * Build a LabourDesignation for an Excel position not present in the JSON master.
     * Category, grade, rate, and nationality are inferred from the position name.
     * Code follows the {@code <prefix>-X<NNN>} pattern so the JSON-curated codes
     * (SM-001…GL-005) are never colliding.
     */
    private LabourDesignation buildInferred(String position, boolean indirectColumn, int seq, Set<String> usedCodes) {
        LabourCategory category = inferCategory(position, indirectColumn);
        LabourGrade grade = inferGrade(category, position);
        BigDecimal rate = inferDailyRate(grade);
        int yearsMin = inferYearsMin(grade);
        String code;
        do {
            code = String.format("%s-X%03d", category.getCodePrefix(), seq++);
        } while (!usedCodes.add(code));
        return LabourDesignation.builder()
                .code(code)
                .designation(position)
                .category(category)
                .trade(inferTrade(category, position))
                .grade(grade)
                .nationality(NationalityType.OMANI_OR_EXPAT)
                .experienceYearsMin(yearsMin)
                .defaultDailyRate(rate)
                .currency("OMR")
                .skills(List.of())
                .certifications(List.of())
                .status(LabourStatus.ACTIVE)
                .sortOrder(1000 + seq) // stable but after JSON master
                .build();
    }

    private LabourCategory inferCategory(String pos, boolean indirectColumn) {
        String p = pos.toLowerCase(Locale.ROOT);
        // Plant operators / drivers regardless of column
        if (p.equals("operator") || p.contains(" operator") || p.endsWith("operator")
                || p.contains("driver") || p.contains("hdd") || p.contains("ldd")) {
            return LabourCategory.PLANT_EQUIPMENT;
        }
        // Indirect column → management / engineering / supervision
        if (indirectColumn) {
            if (p.contains("manager") || p.contains("engineer") || p.contains("lead")
                    || p.contains("supervisor") || p.contains("controller") || p.contains("officer")
                    || p.contains("incharge") || p.contains("superintendent") || p.contains("medic")
                    || p.contains("surveyor") || p.contains("draughtsman") || p.contains("draftsman")
                    || p.contains("quantity") || p.contains("planning") || p.contains("interface")
                    || p.contains("project") || p.contains("design") || p.contains("technician")
                    || p.contains("inspector") || p.contains("foreman") || p.contains("pro")
                    || p.contains("representative") || p.contains("coordinator")) {
                return LabourCategory.SITE_MANAGEMENT;
            }
            if (p.contains("clerk") || p.contains("admin") || p.contains("watchman")
                    || p.contains("store") || p.contains("camp boss") || p.contains("purchaser")) {
                return LabourCategory.GENERAL_UNSKILLED;
            }
            return LabourCategory.SITE_MANAGEMENT;
        }
        // Direct column → skilled / semi-skilled / unskilled by trade keyword
        if (p.contains("electrician") || p.contains("welder") || p.contains("mason")
                || p.contains("carpenter") || p.contains("plumber") || p.contains("rigger")
                || p.contains("mechanic") && !p.contains("helper")
                || p.contains("painter") || p.contains("steel fixer") || p.contains("technician")
                || p.contains("chargehand") || p.contains("scaffold")) {
            return LabourCategory.SKILLED_LABOUR;
        }
        if (p.contains("helper") || p.contains("rakman") || p.contains("rake")
                || p.contains("screwman") || p.contains("tyreman") || p.contains("pest")
                || p.contains("chainman")) {
            return LabourCategory.SEMI_SKILLED_LABOUR;
        }
        // Catering / housekeeping / general
        if (p.contains("cook") || p.contains("waiter") || p.contains("kitchen") || p.contains("chapati")
                || p.contains("butcher") || p.contains("laundry") || p.contains("room")
                || p.contains("office boy") || p.contains("officeboy") || p.contains("cleaner")
                || p.contains("care taker") || p.contains("camp")) {
            return LabourCategory.GENERAL_UNSKILLED;
        }
        return LabourCategory.GENERAL_UNSKILLED;
    }

    private LabourGrade inferGrade(LabourCategory cat, String pos) {
        String p = pos.toLowerCase(Locale.ROOT);
        return switch (cat) {
            case SITE_MANAGEMENT -> {
                if (p.contains("manager") || p.contains("director") || p.contains("lead")
                        || p.contains("superintendent") || p.contains("representative")
                        || p.equals("project manager")) yield LabourGrade.A;
                yield LabourGrade.B;
            }
            case PLANT_EQUIPMENT -> LabourGrade.C;
            case SKILLED_LABOUR -> LabourGrade.C;
            case SEMI_SKILLED_LABOUR -> LabourGrade.D;
            case GENERAL_UNSKILLED -> LabourGrade.E;
        };
    }

    private BigDecimal inferDailyRate(LabourGrade grade) {
        // Mid-band of the LabourGrade docstring ranges (OMR/day).
        return switch (grade) {
            case A -> new BigDecimal("110.00");
            case B -> new BigDecimal("60.00");
            case C -> new BigDecimal("36.00");
            case D -> new BigDecimal("22.00");
            case E -> new BigDecimal("12.00");
        };
    }

    private int inferYearsMin(LabourGrade grade) {
        return switch (grade) {
            case A -> 12;
            case B -> 6;
            case C -> 3;
            case D -> 1;
            case E -> 0;
        };
    }

    private String inferTrade(LabourCategory cat, String pos) {
        return switch (cat) {
            case SITE_MANAGEMENT     -> "Engineering / Management";
            case PLANT_EQUIPMENT     -> "Plant & Equipment";
            case SKILLED_LABOUR      -> "Skilled Trade";
            case SEMI_SKILLED_LABOUR -> "Semi-Skilled Trade";
            case GENERAL_UNSKILLED   -> "General Support";
        };
    }
}
