package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.MaterialCategoryMasterRequest;
import com.bipros.resource.application.dto.MaterialCategoryMasterResponse;
import com.bipros.resource.domain.model.MaterialCategoryMaster;
import com.bipros.resource.domain.repository.MaterialCategoryMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MaterialCategoryMasterService")
class MaterialCategoryMasterServiceTest {

  @Mock private MaterialCategoryMasterRepository repository;
  @Mock private AuditService auditService;

  private MaterialCategoryMasterService service;

  @BeforeEach
  void setUp() {
    service = new MaterialCategoryMasterService(repository, auditService);
  }

  @Nested
  @DisplayName("create")
  class CreateTests {

    @Test
    @DisplayName("normalizes code to upper-case and persists")
    void normalizesCode() {
      when(repository.findByCode("CEMENT")).thenReturn(Optional.empty());
      when(repository.save(any())).thenAnswer(inv -> {
        MaterialCategoryMaster c = inv.getArgument(0);
        c.setId(UUID.randomUUID());
        return c;
      });

      MaterialCategoryMasterResponse r = service.create(
          new MaterialCategoryMasterRequest("cement", "Cement", null, 10, true));

      assertThat(r.code()).isEqualTo("CEMENT");
      assertThat(r.name()).isEqualTo("Cement");
    }

    @Test
    @DisplayName("rejects duplicate code")
    void rejectsDuplicate() {
      MaterialCategoryMaster existing = MaterialCategoryMaster.builder().code("STEEL").name("Steel").build();
      when(repository.findByCode("STEEL")).thenReturn(Optional.of(existing));

      assertThatThrownBy(() -> service.create(
          new MaterialCategoryMasterRequest("steel", "x", null, null, true)))
          .isInstanceOf(BusinessRuleException.class)
          .hasMessageContaining("already exists");
    }
  }

  @Nested
  @DisplayName("update")
  class UpdateTests {

    @Test
    @DisplayName("throws when category not found")
    void notFound() {
      UUID id = UUID.randomUUID();
      when(repository.findById(id)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.update(id,
          new MaterialCategoryMasterRequest("X", "X", null, null, null)))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }
}
