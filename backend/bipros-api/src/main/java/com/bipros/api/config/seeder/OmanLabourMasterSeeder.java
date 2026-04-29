package com.bipros.api.config.seeder;

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
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Profile("seed")
@Order(140)
@RequiredArgsConstructor
public class OmanLabourMasterSeeder implements CommandLineRunner {

    private final LabourDesignationRepository designationRepo;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (designationRepo.count() > 0) {
            log.info("Labour designations already present — skipping Oman seed");
            return;
        }
        List<Map<String, Object>> rows = readDataset();
        List<LabourDesignation> designations = rows.stream().map(this::toDesignation).toList();
        designationRepo.saveAll(designations);
        log.info("Seeded {} Oman labour designations", designations.size());
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
}
