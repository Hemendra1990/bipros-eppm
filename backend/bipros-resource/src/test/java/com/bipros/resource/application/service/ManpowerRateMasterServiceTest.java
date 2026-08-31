package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.ManpowerRateMasterRequest;
import com.bipros.resource.application.dto.ManpowerRateMasterResponse;
import com.bipros.resource.domain.model.GradeMaster;
import com.bipros.resource.domain.model.ResourceRole;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.model.master.ManpowerCategoryMaster;
import com.bipros.resource.domain.model.rate.ManpowerRateMaster;
import com.bipros.resource.domain.repository.GradeMasterRepository;
import com.bipros.resource.domain.repository.ManpowerCategoryMasterRepository;
import com.bipros.resource.domain.repository.ManpowerRateMasterRepository;
import com.bipros.resource.domain.repository.ResourceRoleRepository;
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
@DisplayName("ManpowerRateMasterService")
class ManpowerRateMasterServiceTest {

  @Mock private ManpowerRateMasterRepository repository;
  @Mock private ResourceRoleRepository roleRepository;
  @Mock private ManpowerCategoryMasterRepository categoryRepository;
  @Mock private GradeMasterRepository gradeRepository;
  @Mock private RateMasterSyncService rateMasterSyncService;
  @Mock private AuditService auditService;

  private ManpowerRateMasterService service;

  private final UUID roleId = UUID.randomUUID();
  private final UUID categoryId = UUID.randomUUID();
  private final UUID gradeId = UUID.randomUUID();

  private ResourceRole role;
  private ManpowerCategoryMaster category;
  private GradeMaster grade;

  @BeforeEach
  void setUp() {
    service = new ManpowerRateMasterService(
        repository, roleRepository, categoryRepository, gradeRepository,
        rateMasterSyncService, auditService);

    ResourceType type = ResourceType.builder().code("LABOR").name("Labor").build();
    role = ResourceRole.builder().code("MASON").name("Mason").resourceType(type).build();
    role.setId(roleId);
    category = ManpowerCategoryMaster.builder().code("SKILLED").name("Skilled").build();
    category.setId(categoryId);
    grade = GradeMaster.builder().code("A").name("Grade A").build();
    grade.setId(gradeId);

    lenient().when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
    lenient().when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
    lenient().when(gradeRepository.findById(gradeId)).thenReturn(Optional.of(grade));
  }

  private ManpowerRateMasterRequest validRequest() {
    return new ManpowerRateMasterRequest(
        roleId, categoryId, gradeId, "Day", new BigDecimal("80.00"), true);
  }

  @Nested
  @DisplayName("create")
  class CreateTests {

    @Test
    @DisplayName("persists with role/category/grade FKs and unit + rate")
    void persistsValid() {
      when(repository.findByRoleIdAndCategoryIdAndGradeId(roleId, categoryId, gradeId))
          .thenReturn(Optional.empty());
      when(repository.save(any())).thenAnswer(inv -> {
        ManpowerRateMaster m = inv.getArgument(0);
        m.setId(UUID.randomUUID());
        return m;
      });

      ManpowerRateMasterResponse r = service.create(validRequest());

      assertThat(r.roleId()).isEqualTo(roleId);
      assertThat(r.unit()).isEqualTo("Day");
      assertThat(r.rate()).isEqualByComparingTo("80.00");
      assertThat(r.gradeCode()).isEqualTo("A");
    }

    @Test
    @DisplayName("rejects duplicate (role, category, grade) tuple")
    void duplicateKeyRejected() {
      ManpowerRateMaster existing = ManpowerRateMaster.builder()
          .roleId(roleId).categoryId(categoryId).gradeId(gradeId)
          .unit("Day").rate(BigDecimal.TEN).active(true).build();
      existing.setId(UUID.randomUUID());
      when(repository.findByRoleIdAndCategoryIdAndGradeId(roleId, categoryId, gradeId))
          .thenReturn(Optional.of(existing));

      assertThatThrownBy(() -> service.create(validRequest()))
          .isInstanceOf(BusinessRuleException.class)
          .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("rejects when category passed in is actually a sub-category")
    void categoryMustBeTopLevel() {
      ManpowerCategoryMaster wrong = ManpowerCategoryMaster.builder()
          .code("WRONG").name("Wrong").parentId(UUID.randomUUID()).build();
      wrong.setId(categoryId);
      when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(wrong));

      assertThatThrownBy(() -> service.create(validRequest()))
          .isInstanceOf(BusinessRuleException.class)
          .hasMessageContaining("sub-category");
    }

    @Test
    @DisplayName("rejects when role does not exist")
    void missingRole() {
      when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.create(validRequest()))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }
}
