package com.bipros.ai.resolver;

import com.bipros.resource.domain.model.ProjectResource;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.repository.ProjectResourceRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class EffectiveRateResolverTest {

  private ProjectResourceRepository projectResourceRepo;
  private ResourceRepository resourceRepo;
  private EffectiveRateResolver resolver;

  private UUID projectId;
  private UUID resourceId;

  @BeforeEach
  void setUp() {
    projectResourceRepo = Mockito.mock(ProjectResourceRepository.class);
    resourceRepo = Mockito.mock(ResourceRepository.class);
    resolver = new EffectiveRateResolver(projectResourceRepo, resourceRepo);
    projectId = UUID.randomUUID();
    resourceId = UUID.randomUUID();
  }

  @Test
  void usesProjectResourceOverrideWhenSet() {
    UUID rateMasterId = UUID.randomUUID();
    Resource r = new Resource();
    r.setUnit("Day");
    r.setCostPerUnit(new BigDecimal("80"));
    r.setRateMasterId(rateMasterId);
    when(resourceRepo.findById(resourceId)).thenReturn(Optional.of(r));

    ProjectResource pr = ProjectResource.builder()
        .projectId(projectId)
        .resourceId(resourceId)
        .rateOverride(new BigDecimal("95"))
        .build();
    pr.setId(UUID.randomUUID());
    when(projectResourceRepo.findByProjectIdAndResourceId(projectId, resourceId))
        .thenReturn(Optional.of(pr));

    EffectiveRate er = resolver.resolve(projectId, resourceId);

    assertThat(er.rate()).isEqualByComparingTo("95");
    assertThat(er.source()).isEqualTo(EffectiveRate.Source.OVERRIDE);
    assertThat(er.overrideApplied()).isTrue();
    assertThat(er.unit()).isEqualTo("Day");
    assertThat(er.basis()).isEqualTo("DAY");
    assertThat(er.rateMasterId()).isEqualTo(rateMasterId);
    assertThat(er.projectResourceId()).isEqualTo(pr.getId());
  }

  @Test
  void fallsBackToResourceCostPerUnitWhenNoOverride() {
    Resource r = new Resource();
    r.setUnit("Hour");
    r.setCostPerUnit(new BigDecimal("80"));
    when(resourceRepo.findById(resourceId)).thenReturn(Optional.of(r));
    when(projectResourceRepo.findByProjectIdAndResourceId(projectId, resourceId))
        .thenReturn(Optional.empty());

    EffectiveRate er = resolver.resolve(projectId, resourceId);

    assertThat(er.rate()).isEqualByComparingTo("80");
    assertThat(er.source()).isEqualTo(EffectiveRate.Source.RESOURCE);
    assertThat(er.overrideApplied()).isFalse();
    assertThat(er.unit()).isEqualTo("Hour");
    assertThat(er.basis()).isEqualTo("HOUR");
  }

  @Test
  void poolPresentButRateOverrideNullFallsBackToBase() {
    Resource r = new Resource();
    r.setUnit("Bag");
    r.setCostPerUnit(new BigDecimal("350"));
    when(resourceRepo.findById(resourceId)).thenReturn(Optional.of(r));

    ProjectResource pr = ProjectResource.builder()
        .projectId(projectId)
        .resourceId(resourceId)
        .rateOverride(null)
        .availabilityOverride(0.8)
        .build();
    pr.setId(UUID.randomUUID());
    when(projectResourceRepo.findByProjectIdAndResourceId(projectId, resourceId))
        .thenReturn(Optional.of(pr));

    EffectiveRate er = resolver.resolve(projectId, resourceId);

    assertThat(er.rate()).isEqualByComparingTo("350");
    assertThat(er.source()).isEqualTo(EffectiveRate.Source.RESOURCE);
    assertThat(er.overrideApplied()).isFalse();
    assertThat(er.basis()).isEqualTo("EACH");
    assertThat(er.projectResourceId()).isEqualTo(pr.getId());
  }

  @Test
  void customUnitOverridesResourceUnitWhenOverrideApplied() {
    Resource r = new Resource();
    r.setUnit("Day");
    r.setCostPerUnit(new BigDecimal("80"));
    when(resourceRepo.findById(resourceId)).thenReturn(Optional.of(r));

    ProjectResource pr = ProjectResource.builder()
        .projectId(projectId)
        .resourceId(resourceId)
        .rateOverride(new BigDecimal("12"))
        .customUnit("Hour")
        .build();
    pr.setId(UUID.randomUUID());
    when(projectResourceRepo.findByProjectIdAndResourceId(projectId, resourceId))
        .thenReturn(Optional.of(pr));

    EffectiveRate er = resolver.resolve(projectId, resourceId);

    assertThat(er.unit()).isEqualTo("Hour");
    assertThat(er.basis()).isEqualTo("HOUR");
    assertThat(er.rate()).isEqualByComparingTo("12");
  }

  @Test
  void nullResourceIdReturnsNone() {
    EffectiveRate er = resolver.resolve(projectId, null);
    assertThat(er.source()).isEqualTo(EffectiveRate.Source.NONE);
    assertThat(er.rate()).isNull();
    assertThat(er.overrideApplied()).isFalse();
  }

  @Test
  void missingResourceReturnsNone() {
    when(resourceRepo.findById(resourceId)).thenReturn(Optional.empty());
    when(projectResourceRepo.findByProjectIdAndResourceId(projectId, resourceId))
        .thenReturn(Optional.empty());

    EffectiveRate er = resolver.resolve(projectId, resourceId);
    assertThat(er.source()).isEqualTo(EffectiveRate.Source.NONE);
    assertThat(er.rate()).isNull();
    assertThat(er.unit()).isNull();
    assertThat(er.basis()).isNull();
  }

  @Test
  void nullProjectIdUsesBaseRate() {
    Resource r = new Resource();
    r.setUnit("Each");
    r.setCostPerUnit(new BigDecimal("500"));
    when(resourceRepo.findById(resourceId)).thenReturn(Optional.of(r));

    EffectiveRate er = resolver.resolve(null, resourceId);

    assertThat(er.rate()).isEqualByComparingTo("500");
    assertThat(er.source()).isEqualTo(EffectiveRate.Source.RESOURCE);
    assertThat(er.basis()).isEqualTo("EACH");
    Mockito.verify(projectResourceRepo, Mockito.never())
        .findByProjectIdAndResourceId(Mockito.any(), Mockito.any());
  }
}
