package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.CreateProductivityNormRequest;
import com.bipros.resource.domain.model.ProductivityNormType;
import com.bipros.resource.domain.model.WorkActivity;
import com.bipros.resource.domain.repository.ProductivityNormRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.bipros.resource.domain.repository.ResourceTypeDefRepository;
import com.bipros.resource.domain.repository.WorkActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductivityNormService — validation rules")
class ProductivityNormServiceValidationTest {

  @Mock private ProductivityNormRepository repository;
  @Mock private WorkActivityRepository workActivityRepository;
  @Mock private ResourceTypeDefRepository resourceTypeDefRepository;
  @Mock private ResourceRepository resourceRepository;
  @Mock private AuditService auditService;

  private ProductivityNormService service;

  @BeforeEach
  void setUp() {
    service = new ProductivityNormService(
        repository, workActivityRepository, resourceTypeDefRepository, resourceRepository, auditService);
  }

  @Test
  @DisplayName("rejects when both resourceTypeDefId and resourceId are present")
  void scopeMutuallyExclusive() {
    UUID waId = UUID.randomUUID();
    CreateProductivityNormRequest req = new CreateProductivityNormRequest(
        ProductivityNormType.EQUIPMENT, waId, UUID.randomUUID(), UUID.randomUUID(),
        null, "Sqm", null, null, null, BigDecimal.valueOf(1000), null, null, null, null);

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("not both");
  }

  @Test
  @DisplayName("rejects when neither workActivityId nor activityName is provided")
  void requiresWorkActivity() {
    CreateProductivityNormRequest req = new CreateProductivityNormRequest(
        ProductivityNormType.EQUIPMENT, null, null, null,
        null, "Sqm", null, null, null, BigDecimal.valueOf(1000), null, null, null, null);

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("workActivityId");
  }

  @Test
  @DisplayName("rejects when activityName provided but does not resolve to an existing WorkActivity")
  void unknownActivityName() {
    when(workActivityRepository.findByNameIgnoreCase("Mystery")).thenReturn(Optional.empty());

    CreateProductivityNormRequest req = new CreateProductivityNormRequest(
        ProductivityNormType.EQUIPMENT, null, null, null,
        "Mystery", "Sqm", null, null, null, BigDecimal.valueOf(1000), null, null, null, null);

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("No WorkActivity matches");
  }

  @Test
  @DisplayName("derives outputPerDay for MANPOWER (perMan × crew) when omitted")
  void derivesOutputPerDayManpower() {
    UUID waId = UUID.randomUUID();
    WorkActivity wa = WorkActivity.builder().code("X").name("X").build();
    wa.setId(waId);
    when(workActivityRepository.findById(waId)).thenReturn(Optional.of(wa));
    when(repository.save(any(com.bipros.resource.domain.model.ProductivityNorm.class)))
        .thenAnswer(inv -> {
          com.bipros.resource.domain.model.ProductivityNorm n = inv.getArgument(0);
          n.setId(UUID.randomUUID());
          return n;
        });

    CreateProductivityNormRequest req = new CreateProductivityNormRequest(
        ProductivityNormType.MANPOWER, waId, null, null,
        null, "Cum", BigDecimal.valueOf(8), null, 5, null, null, null, null, null);

    var resp = service.create(req);

    org.assertj.core.api.Assertions.assertThat(resp.outputPerDay()).isEqualByComparingTo("40");
  }

  private static <T> T any(@SuppressWarnings("unused") Class<T> ignore) {
    return org.mockito.ArgumentMatchers.any();
  }
}
