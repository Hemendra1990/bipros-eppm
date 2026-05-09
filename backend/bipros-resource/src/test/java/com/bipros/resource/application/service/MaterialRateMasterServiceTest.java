package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.MaterialRateMasterRequest;
import com.bipros.resource.application.dto.MaterialRateMasterResponse;
import com.bipros.resource.domain.model.MaterialCategoryMaster;
import com.bipros.resource.domain.model.rate.MaterialRateMaster;
import com.bipros.resource.domain.repository.MaterialCategoryMasterRepository;
import com.bipros.resource.domain.repository.MaterialRateMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MaterialRateMasterService")
class MaterialRateMasterServiceTest {

  @Mock private MaterialRateMasterRepository repository;
  @Mock private MaterialCategoryMasterRepository categoryRepository;
  @Mock private RateMasterSyncService rateMasterSyncService;
  @Mock private AuditService auditService;

  private MaterialRateMasterService service;

  private final UUID categoryId = UUID.randomUUID();
  private MaterialCategoryMaster category;

  @BeforeEach
  void setUp() {
    service = new MaterialRateMasterService(repository, categoryRepository,
        rateMasterSyncService, auditService);
    category = MaterialCategoryMaster.builder().code("CEMENT").name("Cement").build();
    category.setId(categoryId);
    lenient().when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
  }

  @Nested
  @DisplayName("create")
  class CreateTests {

    @Test
    @DisplayName("persists with category FK and trimmed spec/grade")
    void persistsValid() {
      when(repository.findByCategoryIdAndSpecGrade(categoryId, "OPC 53"))
          .thenReturn(Optional.empty());
      when(repository.save(any())).thenAnswer(inv -> {
        MaterialRateMaster m = inv.getArgument(0);
        m.setId(UUID.randomUUID());
        return m;
      });

      MaterialRateMasterResponse r = service.create(new MaterialRateMasterRequest(
          categoryId, "  OPC 53  ", "Bag", new BigDecimal("350"), true));

      assertThat(r.categoryId()).isEqualTo(categoryId);
      assertThat(r.specGrade()).isEqualTo("OPC 53");
      assertThat(r.unit()).isEqualTo("Bag");
      assertThat(r.rate()).isEqualByComparingTo("350");
      assertThat(r.categoryCode()).isEqualTo("CEMENT");
    }

    @Test
    @DisplayName("rejects duplicate (category, spec/grade)")
    void duplicateKeyRejected() {
      MaterialRateMaster existing = MaterialRateMaster.builder()
          .categoryId(categoryId).specGrade("OPC 53")
          .unit("Bag").rate(BigDecimal.TEN).active(true).build();
      existing.setId(UUID.randomUUID());
      when(repository.findByCategoryIdAndSpecGrade(categoryId, "OPC 53"))
          .thenReturn(Optional.of(existing));

      assertThatThrownBy(() -> service.create(new MaterialRateMasterRequest(
          categoryId, "OPC 53", "Bag", new BigDecimal("999"), true)))
          .isInstanceOf(BusinessRuleException.class)
          .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("rejects when category does not exist")
    void missingCategory() {
      when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.create(new MaterialRateMasterRequest(
          categoryId, "OPC 53", "Bag", BigDecimal.TEN, true)))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }
}
