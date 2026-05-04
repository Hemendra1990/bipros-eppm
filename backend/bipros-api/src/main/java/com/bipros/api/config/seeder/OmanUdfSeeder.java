package com.bipros.api.config.seeder;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.udf.domain.model.UdfDataType;
import com.bipros.udf.domain.model.UdfScope;
import com.bipros.udf.domain.model.UdfSubject;
import com.bipros.udf.domain.model.UdfValue;
import com.bipros.udf.domain.model.UserDefinedField;
import com.bipros.udf.domain.repository.UdfValueRepository;
import com.bipros.udf.domain.repository.UserDefinedFieldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Component
@Profile("seed")
@Order(156)
@RequiredArgsConstructor
public class OmanUdfSeeder implements CommandLineRunner {

    private static final String PROJECT_CODE = "6155";
    private static final long DETERMINISTIC_SEED = 6155L;
    private static final LocalDate DEFAULT_DATA_DATE = LocalDate.of(2026, 4, 29);

    private final ProjectRepository projectRepository;
    private final UserDefinedFieldRepository userDefinedFieldRepository;
    private final UdfValueRepository udfValueRepository;
    private final ActivityRepository activityRepository;

    private static final String[][] UDF_DEFS = {
        {"Snag Zone",          "TEXT", "ACTIVITY", "Zone reference for snag list tracking"},
        {"FAT Pass Number",    "TEXT", "ACTIVITY", "Factory Acceptance Test pass reference"},
        {"Lab Sample Number",  "TEXT", "PERMIT",   "Laboratory sample reference for material testing"},
        {"IFC Revision",       "TEXT", "PERMIT",   "Issued For Construction drawing revision number"},
    };

    private static final String[] SNAG_ZONES = {
        "Zone-A1", "Zone-A2", "Zone-B1", "Zone-B2", "Zone-C1", "Zone-C2", "Zone-D1", "Zone-D2"
    };

    private static final String[] FAT_PASS_NUMBERS = {
        "FAT-2026-001", "FAT-2026-002", "FAT-2026-003", "FAT-2026-004", "FAT-2026-005"
    };

    private static final String[] LAB_SAMPLE_NUMBERS = {
        "LAB-BNK-0401", "LAB-BNK-0402", "LAB-BNK-0403", "LAB-BNK-0404", "LAB-BNK-0405"
    };

    private static final String[] IFC_REVISIONS = {
        "IFC-RevA", "IFC-RevB", "IFC-RevC", "IFC-RevD", "IFC-RevE"
    };

    @Override
    public void run(String... args) {
        Optional<Project> projectOpt = projectRepository.findByCode(PROJECT_CODE);
        if (projectOpt.isEmpty()) {
            log.warn("[BNK-UDF] project '{}' not found — skipping", PROJECT_CODE);
            return;
        }
        Project project = projectOpt.get();
        UUID projectId = project.getId();

        if (userDefinedFieldRepository.countByDataTypeAndSubject(UdfDataType.TEXT, UdfSubject.ACTIVITY) > 0) {
            log.info("[BNK-UDF] UDF definitions already present — skipping");
            return;
        }

        Random rng = new Random(DETERMINISTIC_SEED);
        List<Activity> activities = activityRepository.findByProjectId(projectId);

        List<UserDefinedField> udfs = seedUdfDefinitions(projectId);
        int valueCount = seedUdfValues(udfs, activities, projectId, rng);

        log.info("[BNK-UDF] Seeded {} UDF definitions, {} UDF values", udfs.size(), valueCount);
    }

    private List<UserDefinedField> seedUdfDefinitions(UUID projectId) {
        List<UserDefinedField> udfs = new java.util.ArrayList<>();
        for (int i = 0; i < UDF_DEFS.length; i++) {
            String[] def = UDF_DEFS[i];
            UserDefinedField udf = new UserDefinedField();
            udf.setName(def[0]);
            udf.setDataType(UdfDataType.valueOf(def[1]));
            udf.setSubject(UdfSubject.valueOf(def[2]));
            udf.setScope(UdfScope.PROJECT);
            udf.setProjectId(projectId);
            udf.setDescription(def[3]);
            udf.setIsFormula(Boolean.FALSE);
            udf.setSortOrder(i);
            udfs.add(userDefinedFieldRepository.save(udf));
        }
        return udfs;
    }

    private int seedUdfValues(List<UserDefinedField> udfs, List<Activity> activities, UUID projectId, Random rng) {
        int created = 0;

        UserDefinedField snagZone = udfs.get(0);
        UserDefinedField fatPass = udfs.get(1);
        UserDefinedField labSample = udfs.get(2);
        UserDefinedField ifcRevision = udfs.get(3);

        int actLimit = Math.min(activities.size(), 10);
        for (int i = 0; i < actLimit; i++) {
            Activity act = activities.get(i);

            UdfValue snagVal = new UdfValue();
            snagVal.setUserDefinedFieldId(snagZone.getId());
            snagVal.setEntityId(act.getId());
            snagVal.setTextValue(SNAG_ZONES[i % SNAG_ZONES.length]);
            udfValueRepository.save(snagVal);
            created++;

            UdfValue fatVal = new UdfValue();
            fatVal.setUserDefinedFieldId(fatPass.getId());
            fatVal.setEntityId(act.getId());
            fatVal.setTextValue(FAT_PASS_NUMBERS[i % FAT_PASS_NUMBERS.length]);
            udfValueRepository.save(fatVal);
            created++;
        }

        // The unique constraint is (user_defined_field_id, entity_id). Binding 5 rows to
        // the same projectId for a single field collides — bind each to a distinct activity.
        int labIfcLimit = Math.min(activities.size() - actLimit, 5);
        for (int i = 0; i < labIfcLimit; i++) {
            Activity host = activities.get(actLimit + i);

            UdfValue labVal = new UdfValue();
            labVal.setUserDefinedFieldId(labSample.getId());
            labVal.setEntityId(host.getId());
            labVal.setTextValue(LAB_SAMPLE_NUMBERS[i % LAB_SAMPLE_NUMBERS.length]);
            udfValueRepository.save(labVal);
            created++;

            UdfValue ifcVal = new UdfValue();
            ifcVal.setUserDefinedFieldId(ifcRevision.getId());
            ifcVal.setEntityId(host.getId());
            ifcVal.setTextValue(IFC_REVISIONS[i % IFC_REVISIONS.length]);
            udfValueRepository.save(ifcVal);
            created++;
        }

        return created;
    }
}
