package com.bipros.api.config.seeder;

import com.bipros.risk.domain.model.Industry;
import com.bipros.risk.domain.model.RiskCategoryMaster;
import com.bipros.risk.domain.model.RiskCategoryType;
import com.bipros.risk.domain.repository.RiskCategoryMasterRepository;
import com.bipros.risk.domain.repository.RiskCategoryTypeRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds the system-default Risk Category Type / Master rows from the curated catalog at
 * {@code classpath:seed/risk-categories.json}. The catalog contains ~700 categories
 * across ~30 types and 22 industries, biased toward Indian highway/road infrastructure
 * with broad coverage for mass-event (Kumbh Mela), manufacturing, IT, healthcare,
 * pharma, mining, maritime, aerospace, agriculture, banking and telecom domains.
 *
 * <p>Idempotent — each row is upserted by {@code code}; existing rows are skipped so
 * boot is fast on warm databases. Seeded rows carry {@code systemDefault=true}: the
 * services block delete and code mutation on these rows.
 *
 * <p>Runs at {@code @Order(45)} — before {@link ProjectCategoryMasterSeeder} (55) and
 * {@link RiskTemplateSeeder} (60) so the latter can resolve categories by code.
 */
@Slf4j
@Component
@Order(45)
@RequiredArgsConstructor
public class RiskCategorySeeder implements CommandLineRunner {

    private static final String RESOURCE_PATH = "seed/risk-categories.json";

    private final RiskCategoryTypeRepository typeRepository;
    private final RiskCategoryMasterRepository categoryRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(String... args) {
        Catalog catalog;
        try (InputStream in = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            catalog = objectMapper.readValue(in, Catalog.class);
        } catch (IOException e) {
            log.error("[RiskCategorySeeder] failed to load {} — risk categories will not be seeded",
                RESOURCE_PATH, e);
            return;
        }

        Map<String, RiskCategoryType> typesByCode = new HashMap<>();
        int typesInserted = 0;
        int typesSkipped = 0;

        for (TypeRow row : catalog.types()) {
            var existing = typeRepository.findByCode(row.code());
            if (existing.isPresent()) {
                typesByCode.put(row.code(), existing.get());
                typesSkipped++;
                continue;
            }
            RiskCategoryType type = RiskCategoryType.builder()
                .code(row.code())
                .name(row.name())
                .description(row.description())
                .active(Boolean.TRUE)
                .sortOrder(row.sortOrder() == null ? 0 : row.sortOrder())
                .systemDefault(Boolean.TRUE)
                .build();
            RiskCategoryType saved = typeRepository.save(type);
            typesByCode.put(saved.getCode(), saved);
            typesInserted++;
        }

        int categoriesInserted = 0;
        int categoriesSkipped = 0;
        int categoriesOrphaned = 0;

        for (CategoryRow row : catalog.categories()) {
            if (categoryRepository.findByCode(row.code()).isPresent()) {
                categoriesSkipped++;
                continue;
            }
            RiskCategoryType type = typesByCode.get(row.typeCode());
            if (type == null) {
                log.warn("[RiskCategorySeeder] skipping category {} — typeCode {} not found",
                    row.code(), row.typeCode());
                categoriesOrphaned++;
                continue;
            }
            Industry industry = parseIndustry(row.industry(), row.code());
            if (industry == null) {
                categoriesOrphaned++;
                continue;
            }
            RiskCategoryMaster category = RiskCategoryMaster.builder()
                .code(row.code())
                .name(row.name())
                .description(row.description())
                .type(type)
                .industry(industry)
                .active(Boolean.TRUE)
                .sortOrder(row.sortOrder() == null ? 0 : row.sortOrder())
                .systemDefault(Boolean.TRUE)
                .build();
            categoryRepository.save(category);
            categoriesInserted++;
        }

        log.info("[RiskCategorySeeder] types: inserted={} skipped={}; categories: inserted={} skipped={} orphaned={}",
            typesInserted, typesSkipped, categoriesInserted, categoriesSkipped, categoriesOrphaned);
    }

    private static Industry parseIndustry(String raw, String forCategoryCode) {
        try {
            return Industry.fromString(raw);
        } catch (IllegalArgumentException ex) {
            log.warn("[RiskCategorySeeder] skipping category {} — invalid industry '{}'",
                forCategoryCode, raw);
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Catalog(List<TypeRow> types, List<CategoryRow> categories) {
        Catalog {
            if (types == null) types = List.of();
            if (categories == null) categories = List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TypeRow(String code, String name, String description, Integer sortOrder) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CategoryRow(
        String code, String name, String typeCode, String industry,
        String description, Integer sortOrder) {}
}
