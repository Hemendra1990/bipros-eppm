package com.bipros.api.config.seeder;

import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.model.ResourceTypeDef;
import com.bipros.resource.domain.repository.ResourceTypeDefRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the three system-default Resource Type definitions — the 3M's: Manpower, Material,
 * Machine. These rows are protected against deletion and base-category mutation; admins can
 * rename them and tweak their auto-generated code prefix and sort order.
 *
 * <p>Idempotent: each row is upserted by {@code code}.
 *
 * <p>Runs at {@code @Order(50)} — well before {@link IcpmsPhaseDSeeder} (101+),
 * {@link ExcelMasterDataLoader} (109) and {@link NhaiRoadProjectSeeder} (140), all of which
 * create Resources that need the seeded defs to exist.
 */
@Slf4j
@Component
@Order(50)
@RequiredArgsConstructor
public class ResourceTypeDefSeeder implements CommandLineRunner {

  private final ResourceTypeDefRepository repository;

  @Override
  @Transactional
  public void run(String... args) {
    int inserted = 0;
    inserted += upsert("MANPOWER", "Manpower", ResourceType.LABOR, "LAB", 10);
    inserted += upsert("MATERIAL", "Material", ResourceType.MATERIAL, "MAT", 20);
    inserted += upsert("MACHINE", "Machine", ResourceType.NONLABOR, "EQ", 30);
    if (inserted > 0) {
      log.info("[ResourceTypeDefSeeder] inserted {} system-default resource type(s)", inserted);
    }
  }

  private int upsert(String code, String name, ResourceType baseCategory, String prefix, int sortOrder) {
    if (repository.findByCode(code).isPresent()) {
      return 0;
    }
    ResourceTypeDef def = ResourceTypeDef.builder()
        .code(code)
        .name(name)
        .baseCategory(baseCategory)
        .codePrefix(prefix)
        .sortOrder(sortOrder)
        .active(true)
        .systemDefault(true)
        .build();
    repository.save(def);
    return 1;
  }
}
