package com.bipros.resource.domain.service;

import com.bipros.resource.domain.model.ProductivityNorm;
import com.bipros.resource.domain.model.ProductivityNormType;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceTypeDef;
import com.bipros.resource.domain.model.WorkActivity;
import com.bipros.resource.domain.repository.ProductivityNormRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductivityNormLookupService — fallback order")
class ProductivityNormLookupServiceTest {

  @Mock private ProductivityNormRepository normRepository;
  @Mock private WorkActivityRepository workActivityRepository;
  @Mock private ResourceRepository resourceRepository;

  private ProductivityNormLookupService service;

  private UUID workActivityId;
  private UUID resourceId;
  private UUID typeDefId;
  private Resource resource;
  private ResourceTypeDef typeDef;

  @BeforeEach
  void setUp() {
    service = new ProductivityNormLookupService(normRepository, workActivityRepository, resourceRepository);
    workActivityId = UUID.randomUUID();
    resourceId = UUID.randomUUID();
    typeDefId = UUID.randomUUID();
    typeDef = ResourceTypeDef.builder().code("BULL_DOZER").name("Bull Dozer").build();
    typeDef.setId(typeDefId);
    resource = Resource.builder()
        .code("BD-001")
        .name("Bull Dozer 1")
        .resourceTypeDef(typeDef)
        .standardOutputPerDay(2500.0)
        .standardOutputUnit("Sqm")
        .build();
    resource.setId(resourceId);
  }

  @Test
  @DisplayName("specific-resource norm wins over type-level + legacy")
  void specificResourceWins() {
    when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));
    when(normRepository.findFirstByWorkActivityIdAndResourceId(workActivityId, resourceId))
        .thenReturn(Optional.of(norm(BigDecimal.valueOf(4500), "Sqm")));

    ResolvedNorm r = service.resolve(workActivityId, resourceId);

    assertThat(r.source()).isEqualTo(ResolvedNorm.Source.SPECIFIC_RESOURCE);
    assertThat(r.outputPerDay()).isEqualByComparingTo("4500");
    assertThat(r.unit()).isEqualTo("Sqm");
  }

  @Test
  @DisplayName("type-level norm used when no specific override")
  void typeLevelWhenNoOverride() {
    when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));
    when(normRepository.findFirstByWorkActivityIdAndResourceId(workActivityId, resourceId))
        .thenReturn(Optional.empty());
    when(normRepository.findFirstByWorkActivityIdAndResourceIsNullAndResourceTypeDefId(workActivityId, typeDefId))
        .thenReturn(Optional.of(norm(BigDecimal.valueOf(4000), "Sqm")));

    ResolvedNorm r = service.resolve(workActivityId, resourceId);

    assertThat(r.source()).isEqualTo(ResolvedNorm.Source.RESOURCE_TYPE);
    assertThat(r.outputPerDay()).isEqualByComparingTo("4000");
  }

  @Test
  @DisplayName("falls back to Resource.standardOutputPerDay when no norms exist")
  void legacyFallback() {
    when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));
    when(normRepository.findFirstByWorkActivityIdAndResourceId(any(), any())).thenReturn(Optional.empty());
    when(normRepository.findFirstByWorkActivityIdAndResourceIsNullAndResourceTypeDefId(any(), any()))
        .thenReturn(Optional.empty());

    ResolvedNorm r = service.resolve(workActivityId, resourceId);

    assertThat(r.source()).isEqualTo(ResolvedNorm.Source.RESOURCE_LEGACY);
    assertThat(r.outputPerDay()).isEqualByComparingTo("2500");
    assertThat(r.unit()).isEqualTo("Sqm");
  }

  @Test
  @DisplayName("returns NONE when nothing matches and no legacy default")
  void noneWhenAllEmpty() {
    resource.setStandardOutputPerDay(null);
    when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));
    when(normRepository.findFirstByWorkActivityIdAndResourceId(any(), any())).thenReturn(Optional.empty());
    when(normRepository.findFirstByWorkActivityIdAndResourceIsNullAndResourceTypeDefId(any(), any()))
        .thenReturn(Optional.empty());

    ResolvedNorm r = service.resolve(workActivityId, resourceId);

    assertThat(r.source()).isEqualTo(ResolvedNorm.Source.NONE);
    assertThat(r.outputPerDay()).isNull();
  }

  @Test
  @DisplayName("missing resource short-circuits to NONE")
  void noResourceMeansNone() {
    when(resourceRepository.findById(resourceId)).thenReturn(Optional.empty());

    ResolvedNorm r = service.resolve(workActivityId, resourceId);

    assertThat(r.source()).isEqualTo(ResolvedNorm.Source.NONE);
  }

  @Test
  @DisplayName("resolveByName looks up the activity and delegates to resolve()")
  void resolveByNameDelegates() {
    WorkActivity wa = WorkActivity.builder().code("CLEAR").name("Clearing & Grubbing").build();
    wa.setId(workActivityId);
    when(workActivityRepository.findByNameIgnoreCase("Clearing & Grubbing")).thenReturn(Optional.of(wa));
    when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));
    when(normRepository.findFirstByWorkActivityIdAndResourceId(workActivityId, resourceId))
        .thenReturn(Optional.of(norm(BigDecimal.valueOf(4500), "Sqm")));

    ResolvedNorm r = service.resolveByName("Clearing & Grubbing", resourceId);

    assertThat(r.source()).isEqualTo(ResolvedNorm.Source.SPECIFIC_RESOURCE);
    assertThat(r.workActivityId()).isEqualTo(workActivityId);
  }

  private ProductivityNorm norm(BigDecimal output, String unit) {
    WorkActivity wa = WorkActivity.builder().code("X").name("X").build();
    wa.setId(workActivityId);
    ProductivityNorm n = ProductivityNorm.builder()
        .normType(ProductivityNormType.EQUIPMENT)
        .workActivity(wa)
        .unit(unit)
        .outputPerDay(output)
        .build();
    n.setId(UUID.randomUUID());
    return n;
  }
}
