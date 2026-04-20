package com.bipros.udf.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.udf.application.dto.CreateUserDefinedFieldRequest;
import com.bipros.udf.application.dto.SetUdfValueRequest;
import com.bipros.udf.application.dto.UdfValueResponse;
import com.bipros.udf.application.dto.UserDefinedFieldDto;
import com.bipros.udf.domain.model.*;
import com.bipros.udf.domain.repository.UdfValueRepository;
import com.bipros.udf.domain.repository.UserDefinedFieldRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UdfService Tests")
class UdfServiceTest {

    @Mock
    private UserDefinedFieldRepository userDefinedFieldRepository;

    @Mock
    private UdfValueRepository udfValueRepository;

    private UdfService udfService;

    @BeforeEach
    void setUp() {
        udfService = new UdfService(userDefinedFieldRepository, udfValueRepository);
    }

    private UserDefinedField buildField(UUID id, String name, UdfDataType dataType,
                                         UdfSubject subject, UdfScope scope) {
        UserDefinedField field = new UserDefinedField();
        field.setId(id);
        field.setName(name);
        field.setDataType(dataType);
        field.setSubject(subject);
        field.setScope(scope);
        field.setIsFormula(false);
        field.setSortOrder(0);
        return field;
    }

    private UdfValue buildUdfValue(UUID id, UUID fieldId, UUID entityId) {
        UdfValue value = new UdfValue();
        value.setId(id);
        value.setUserDefinedFieldId(fieldId);
        value.setEntityId(entityId);
        return value;
    }

    @Nested
    @DisplayName("createField")
    class CreateFieldTests {

        @Test
        @DisplayName("creates field successfully when under limit")
        void createField_underLimit_succeeds() {
            var request = CreateUserDefinedFieldRequest.builder()
                .name("Priority")
                .description("Task priority")
                .dataType(UdfDataType.TEXT)
                .subject(UdfSubject.ACTIVITY)
                .scope(UdfScope.GLOBAL)
                .isFormula(false)
                .sortOrder(1)
                .build();

            when(userDefinedFieldRepository.countByDataTypeAndSubject(UdfDataType.TEXT, UdfSubject.ACTIVITY))
                .thenReturn(5L);
            when(userDefinedFieldRepository.save(any(UserDefinedField.class)))
                .thenAnswer(inv -> {
                    UserDefinedField f = inv.getArgument(0);
                    f.setId(UUID.randomUUID());
                    return f;
                });

            UserDefinedFieldDto result = udfService.createField(request);

            assertThat(result.getName()).isEqualTo("Priority");
            assertThat(result.getDataType()).isEqualTo(UdfDataType.TEXT);
            assertThat(result.getSubject()).isEqualTo(UdfSubject.ACTIVITY);
            verify(userDefinedFieldRepository).save(any(UserDefinedField.class));
        }

        @Test
        @DisplayName("throws when field limit exceeded for regular type")
        void createField_limitExceeded_throws() {
            var request = CreateUserDefinedFieldRequest.builder()
                .name("Extra Field")
                .dataType(UdfDataType.NUMBER)
                .subject(UdfSubject.PROJECT)
                .scope(UdfScope.GLOBAL)
                .isFormula(false)
                .build();

            when(userDefinedFieldRepository.countByDataTypeAndSubject(UdfDataType.NUMBER, UdfSubject.PROJECT))
                .thenReturn(100L);

            assertThatThrownBy(() -> udfService.createField(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Maximum 100 fields");

            verify(userDefinedFieldRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws when indicator field limit exceeded (max 20)")
        void createField_indicatorLimitExceeded_throws() {
            var request = CreateUserDefinedFieldRequest.builder()
                .name("Status Light")
                .dataType(UdfDataType.INDICATOR)
                .subject(UdfSubject.ACTIVITY)
                .scope(UdfScope.GLOBAL)
                .isFormula(false)
                .build();

            when(userDefinedFieldRepository.countByDataTypeAndSubject(UdfDataType.INDICATOR, UdfSubject.ACTIVITY))
                .thenReturn(20L);

            assertThatThrownBy(() -> udfService.createField(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Maximum 20 fields");
        }
    }

    @Nested
    @DisplayName("updateField")
    class UpdateFieldTests {

        @Test
        @DisplayName("updates existing field successfully")
        void updateField_existingField_succeeds() {
            UUID fieldId = UUID.randomUUID();
            var existingField = buildField(fieldId, "Old Name", UdfDataType.TEXT,
                UdfSubject.ACTIVITY, UdfScope.GLOBAL);

            var request = CreateUserDefinedFieldRequest.builder()
                .name("New Name")
                .description("Updated desc")
                .dataType(UdfDataType.TEXT)
                .subject(UdfSubject.ACTIVITY)
                .scope(UdfScope.GLOBAL)
                .isFormula(false)
                .sortOrder(2)
                .build();

            when(userDefinedFieldRepository.findById(fieldId)).thenReturn(Optional.of(existingField));
            when(userDefinedFieldRepository.save(any(UserDefinedField.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            UserDefinedFieldDto result = udfService.updateField(fieldId, request);

            assertThat(result.getName()).isEqualTo("New Name");
            assertThat(result.getDescription()).isEqualTo("Updated desc");
            assertThat(result.getSortOrder()).isEqualTo(2);
        }

        @Test
        @DisplayName("throws when field not found")
        void updateField_notFound_throws() {
            UUID fieldId = UUID.randomUUID();
            var request = CreateUserDefinedFieldRequest.builder()
                .name("Name")
                .dataType(UdfDataType.TEXT)
                .subject(UdfSubject.ACTIVITY)
                .build();

            when(userDefinedFieldRepository.findById(fieldId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> udfService.updateField(fieldId, request))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteField")
    class DeleteFieldTests {

        @Test
        @DisplayName("deletes field and cascades to values")
        void deleteField_existingField_deletesWithValues() {
            UUID fieldId = UUID.randomUUID();
            var field = buildField(fieldId, "ToDelete", UdfDataType.TEXT,
                UdfSubject.WBS, UdfScope.GLOBAL);

            var val1 = buildUdfValue(UUID.randomUUID(), fieldId, UUID.randomUUID());
            var val2 = buildUdfValue(UUID.randomUUID(), fieldId, UUID.randomUUID());

            when(userDefinedFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));
            when(udfValueRepository.findByUserDefinedFieldId(fieldId)).thenReturn(List.of(val1, val2));

            udfService.deleteField(fieldId);

            verify(udfValueRepository).deleteAll(List.of(val1, val2));
            verify(userDefinedFieldRepository).delete(field);
        }

        @Test
        @DisplayName("throws when field not found")
        void deleteField_notFound_throws() {
            UUID fieldId = UUID.randomUUID();
            when(userDefinedFieldRepository.findById(fieldId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> udfService.deleteField(fieldId))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getFieldsBySubject")
    class GetFieldsBySubjectTests {

        @Test
        @DisplayName("returns project-scoped fields when scope is PROJECT")
        void getFieldsBySubject_projectScope_returnsProjectFields() {
            UUID projectId = UUID.randomUUID();
            var field = buildField(UUID.randomUUID(), "ProjField", UdfDataType.TEXT,
                UdfSubject.ACTIVITY, UdfScope.PROJECT);

            when(userDefinedFieldRepository.findBySubjectAndProjectId(UdfSubject.ACTIVITY, projectId))
                .thenReturn(List.of(field));

            List<UserDefinedFieldDto> result = udfService.getFieldsBySubject(
                UdfSubject.ACTIVITY, UdfScope.PROJECT, projectId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("ProjField");
            verify(userDefinedFieldRepository).findBySubjectAndProjectId(UdfSubject.ACTIVITY, projectId);
        }

        @Test
        @DisplayName("returns global-scoped fields when scope is GLOBAL")
        void getFieldsBySubject_globalScope_returnsGlobalFields() {
            var field = buildField(UUID.randomUUID(), "GlobalField", UdfDataType.NUMBER,
                UdfSubject.PROJECT, UdfScope.GLOBAL);

            when(userDefinedFieldRepository.findBySubjectAndScope(UdfSubject.PROJECT, UdfScope.GLOBAL))
                .thenReturn(List.of(field));

            List<UserDefinedFieldDto> result = udfService.getFieldsBySubject(
                UdfSubject.PROJECT, UdfScope.GLOBAL, null);

            assertThat(result).hasSize(1);
            verify(userDefinedFieldRepository).findBySubjectAndScope(UdfSubject.PROJECT, UdfScope.GLOBAL);
        }

        @Test
        @DisplayName("returns all fields when no scope filter")
        void getFieldsBySubject_noScope_returnsAll() {
            var f1 = buildField(UUID.randomUUID(), "F1", UdfDataType.TEXT, UdfSubject.WBS, UdfScope.GLOBAL);
            var f2 = buildField(UUID.randomUUID(), "F2", UdfDataType.NUMBER, UdfSubject.WBS, UdfScope.PROJECT);

            when(userDefinedFieldRepository.findBySubject(UdfSubject.WBS)).thenReturn(List.of(f1, f2));

            List<UserDefinedFieldDto> result = udfService.getFieldsBySubject(UdfSubject.WBS, null, null);

            assertThat(result).hasSize(2);
            verify(userDefinedFieldRepository).findBySubject(UdfSubject.WBS);
        }
    }

    @Nested
    @DisplayName("setValue")
    class SetValueTests {

        @Test
        @DisplayName("sets text value for TEXT field")
        void setValue_textField_setsTextValue() {
            UUID fieldId = UUID.randomUUID();
            UUID entityId = UUID.randomUUID();
            var field = buildField(fieldId, "Note", UdfDataType.TEXT, UdfSubject.ACTIVITY, UdfScope.GLOBAL);
            var request = SetUdfValueRequest.builder().textValue("Hello").build();

            when(userDefinedFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));
            when(udfValueRepository.findByUserDefinedFieldIdAndEntityId(fieldId, entityId))
                .thenReturn(Optional.empty());
            when(udfValueRepository.save(any(UdfValue.class))).thenAnswer(inv -> {
                UdfValue v = inv.getArgument(0);
                v.setId(UUID.randomUUID());
                return v;
            });

            UdfValueResponse result = udfService.setValue(fieldId, entityId, request);

            assertThat(result.getTextValue()).isEqualTo("Hello");
            assertThat(result.getNumberValue()).isNull();
        }

        @Test
        @DisplayName("sets number value for NUMBER field")
        void setValue_numberField_setsNumberValue() {
            UUID fieldId = UUID.randomUUID();
            UUID entityId = UUID.randomUUID();
            var field = buildField(fieldId, "Score", UdfDataType.NUMBER, UdfSubject.ACTIVITY, UdfScope.GLOBAL);
            var request = SetUdfValueRequest.builder().numberValue(42.5).build();

            when(userDefinedFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));
            when(udfValueRepository.findByUserDefinedFieldIdAndEntityId(fieldId, entityId))
                .thenReturn(Optional.empty());
            when(udfValueRepository.save(any(UdfValue.class))).thenAnswer(inv -> {
                UdfValue v = inv.getArgument(0);
                v.setId(UUID.randomUUID());
                return v;
            });

            UdfValueResponse result = udfService.setValue(fieldId, entityId, request);

            assertThat(result.getNumberValue()).isEqualTo(42.5);
        }

        @Test
        @DisplayName("sets cost value for COST field")
        void setValue_costField_setsCostValue() {
            UUID fieldId = UUID.randomUUID();
            UUID entityId = UUID.randomUUID();
            var field = buildField(fieldId, "Budget", UdfDataType.COST, UdfSubject.PROJECT, UdfScope.GLOBAL);
            var request = SetUdfValueRequest.builder().costValue(new BigDecimal("1500.75")).build();

            when(userDefinedFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));
            when(udfValueRepository.findByUserDefinedFieldIdAndEntityId(fieldId, entityId))
                .thenReturn(Optional.empty());
            when(udfValueRepository.save(any(UdfValue.class))).thenAnswer(inv -> {
                UdfValue v = inv.getArgument(0);
                v.setId(UUID.randomUUID());
                return v;
            });

            UdfValueResponse result = udfService.setValue(fieldId, entityId, request);

            assertThat(result.getCostValue()).isEqualByComparingTo(new BigDecimal("1500.75"));
        }

        @Test
        @DisplayName("sets date value for DATE field")
        void setValue_dateField_setsDateValue() {
            UUID fieldId = UUID.randomUUID();
            UUID entityId = UUID.randomUUID();
            var field = buildField(fieldId, "Deadline", UdfDataType.DATE, UdfSubject.ACTIVITY, UdfScope.GLOBAL);
            LocalDate date = LocalDate.of(2026, 6, 15);
            var request = SetUdfValueRequest.builder().dateValue(date).build();

            when(userDefinedFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));
            when(udfValueRepository.findByUserDefinedFieldIdAndEntityId(fieldId, entityId))
                .thenReturn(Optional.empty());
            when(udfValueRepository.save(any(UdfValue.class))).thenAnswer(inv -> {
                UdfValue v = inv.getArgument(0);
                v.setId(UUID.randomUUID());
                return v;
            });

            UdfValueResponse result = udfService.setValue(fieldId, entityId, request);

            assertThat(result.getDateValue()).isEqualTo(date);
        }

        @Test
        @DisplayName("sets indicator value for INDICATOR field")
        void setValue_indicatorField_setsIndicatorValue() {
            UUID fieldId = UUID.randomUUID();
            UUID entityId = UUID.randomUUID();
            var field = buildField(fieldId, "Status", UdfDataType.INDICATOR, UdfSubject.ACTIVITY, UdfScope.GLOBAL);
            var request = SetUdfValueRequest.builder().indicatorValue(IndicatorColor.GREEN).build();

            when(userDefinedFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));
            when(udfValueRepository.findByUserDefinedFieldIdAndEntityId(fieldId, entityId))
                .thenReturn(Optional.empty());
            when(udfValueRepository.save(any(UdfValue.class))).thenAnswer(inv -> {
                UdfValue v = inv.getArgument(0);
                v.setId(UUID.randomUUID());
                return v;
            });

            UdfValueResponse result = udfService.setValue(fieldId, entityId, request);

            assertThat(result.getIndicatorValue()).isEqualTo(IndicatorColor.GREEN);
        }

        @Test
        @DisplayName("sets code value for CODE field")
        void setValue_codeField_setsCodeValue() {
            UUID fieldId = UUID.randomUUID();
            UUID entityId = UUID.randomUUID();
            var field = buildField(fieldId, "Category", UdfDataType.CODE, UdfSubject.WBS, UdfScope.GLOBAL);
            var request = SetUdfValueRequest.builder().codeValue("CAT-A").build();

            when(userDefinedFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));
            when(udfValueRepository.findByUserDefinedFieldIdAndEntityId(fieldId, entityId))
                .thenReturn(Optional.empty());
            when(udfValueRepository.save(any(UdfValue.class))).thenAnswer(inv -> {
                UdfValue v = inv.getArgument(0);
                v.setId(UUID.randomUUID());
                return v;
            });

            UdfValueResponse result = udfService.setValue(fieldId, entityId, request);

            assertThat(result.getCodeValue()).isEqualTo("CAT-A");
        }

        @Test
        @DisplayName("upserts when value already exists")
        void setValue_existingValue_updates() {
            UUID fieldId = UUID.randomUUID();
            UUID entityId = UUID.randomUUID();
            UUID valueId = UUID.randomUUID();
            var field = buildField(fieldId, "Note", UdfDataType.TEXT, UdfSubject.ACTIVITY, UdfScope.GLOBAL);
            var existing = buildUdfValue(valueId, fieldId, entityId);
            existing.setTextValue("Old text");

            var request = SetUdfValueRequest.builder().textValue("New text").build();

            when(userDefinedFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));
            when(udfValueRepository.findByUserDefinedFieldIdAndEntityId(fieldId, entityId))
                .thenReturn(Optional.of(existing));
            when(udfValueRepository.save(any(UdfValue.class))).thenAnswer(inv -> inv.getArgument(0));

            UdfValueResponse result = udfService.setValue(fieldId, entityId, request);

            assertThat(result.getTextValue()).isEqualTo("New text");
            assertThat(result.getId()).isEqualTo(valueId);
        }

        @Test
        @DisplayName("throws when text value missing for TEXT field")
        void setValue_textFieldMissingValue_throws() {
            UUID fieldId = UUID.randomUUID();
            UUID entityId = UUID.randomUUID();
            var field = buildField(fieldId, "Note", UdfDataType.TEXT, UdfSubject.ACTIVITY, UdfScope.GLOBAL);
            var request = SetUdfValueRequest.builder().build();

            when(userDefinedFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));

            assertThatThrownBy(() -> udfService.setValue(fieldId, entityId, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Text value is required");
        }

        @Test
        @DisplayName("throws when number value missing for NUMBER field")
        void setValue_numberFieldMissingValue_throws() {
            UUID fieldId = UUID.randomUUID();
            UUID entityId = UUID.randomUUID();
            var field = buildField(fieldId, "Score", UdfDataType.NUMBER, UdfSubject.ACTIVITY, UdfScope.GLOBAL);
            var request = SetUdfValueRequest.builder().build();

            when(userDefinedFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));

            assertThatThrownBy(() -> udfService.setValue(fieldId, entityId, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Number value is required");
        }

        @Test
        @DisplayName("throws when field not found")
        void setValue_fieldNotFound_throws() {
            UUID fieldId = UUID.randomUUID();
            UUID entityId = UUID.randomUUID();
            var request = SetUdfValueRequest.builder().textValue("test").build();

            when(userDefinedFieldRepository.findById(fieldId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> udfService.setValue(fieldId, entityId, request))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("clears other typed fields when setting value")
        void setValue_clearsOtherFields() {
            UUID fieldId = UUID.randomUUID();
            UUID entityId = UUID.randomUUID();
            var field = buildField(fieldId, "Note", UdfDataType.TEXT, UdfSubject.ACTIVITY, UdfScope.GLOBAL);

            var existing = buildUdfValue(UUID.randomUUID(), fieldId, entityId);
            existing.setNumberValue(99.0);
            existing.setCostValue(BigDecimal.TEN);

            var request = SetUdfValueRequest.builder().textValue("Hello").build();

            when(userDefinedFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));
            when(udfValueRepository.findByUserDefinedFieldIdAndEntityId(fieldId, entityId))
                .thenReturn(Optional.of(existing));
            when(udfValueRepository.save(any(UdfValue.class))).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<UdfValue> captor = ArgumentCaptor.forClass(UdfValue.class);
            udfService.setValue(fieldId, entityId, request);
            verify(udfValueRepository).save(captor.capture());

            UdfValue saved = captor.getValue();
            assertThat(saved.getTextValue()).isEqualTo("Hello");
            assertThat(saved.getNumberValue()).isNull();
            assertThat(saved.getCostValue()).isNull();
            assertThat(saved.getDateValue()).isNull();
            assertThat(saved.getIndicatorValue()).isNull();
            assertThat(saved.getCodeValue()).isNull();
        }
    }

    @Nested
    @DisplayName("getValue")
    class GetValueTests {

        @Test
        @DisplayName("returns value when exists")
        void getValue_exists_returnsResponse() {
            UUID fieldId = UUID.randomUUID();
            UUID entityId = UUID.randomUUID();
            var value = buildUdfValue(UUID.randomUUID(), fieldId, entityId);
            value.setTextValue("Hello");

            when(udfValueRepository.findByUserDefinedFieldIdAndEntityId(fieldId, entityId))
                .thenReturn(Optional.of(value));

            UdfValueResponse result = udfService.getValue(fieldId, entityId);

            assertThat(result.getTextValue()).isEqualTo("Hello");
            assertThat(result.getUserDefinedFieldId()).isEqualTo(fieldId);
        }

        @Test
        @DisplayName("throws when value not found")
        void getValue_notFound_throws() {
            UUID fieldId = UUID.randomUUID();
            UUID entityId = UUID.randomUUID();

            when(udfValueRepository.findByUserDefinedFieldIdAndEntityId(fieldId, entityId))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> udfService.getValue(fieldId, entityId))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getValuesForEntity")
    class GetValuesForEntityTests {

        @Test
        @DisplayName("returns all values for entity")
        void getValuesForEntity_returnsAll() {
            UUID entityId = UUID.randomUUID();
            var v1 = buildUdfValue(UUID.randomUUID(), UUID.randomUUID(), entityId);
            v1.setTextValue("A");
            var v2 = buildUdfValue(UUID.randomUUID(), UUID.randomUUID(), entityId);
            v2.setNumberValue(42.0);

            when(udfValueRepository.findByEntityId(entityId)).thenReturn(List.of(v1, v2));

            List<UdfValueResponse> result = udfService.getValuesForEntity(entityId);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("returns empty list when no values")
        void getValuesForEntity_empty_returnsEmptyList() {
            UUID entityId = UUID.randomUUID();
            when(udfValueRepository.findByEntityId(entityId)).thenReturn(List.of());

            List<UdfValueResponse> result = udfService.getValuesForEntity(entityId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("evaluateFormula")
    class EvaluateFormulaTests {

        @Test
        @DisplayName("evaluates arithmetic formula with field references")
        void evaluateFormula_arithmetic_returnsResult() {
            UUID fieldId = UUID.randomUUID();
            UUID entityId = UUID.randomUUID();
            UUID refFieldId = UUID.randomUUID();

            var formulaField = buildField(fieldId, "Total", UdfDataType.NUMBER,
                UdfSubject.ACTIVITY, UdfScope.GLOBAL);
            formulaField.setIsFormula(true);
            formulaField.setFormulaExpression("[Cost] * 1.1");

            var refField = buildField(refFieldId, "Cost", UdfDataType.NUMBER,
                UdfSubject.ACTIVITY, UdfScope.GLOBAL);

            var costValue = buildUdfValue(UUID.randomUUID(), refFieldId, entityId);
            costValue.setNumberValue(100.0);

            when(userDefinedFieldRepository.findById(fieldId)).thenReturn(Optional.of(formulaField));
            when(udfValueRepository.findByEntityId(entityId)).thenReturn(List.of(costValue));
            when(userDefinedFieldRepository.findById(refFieldId)).thenReturn(Optional.of(refField));

            String result = udfService.evaluateFormula(fieldId, entityId);

            assertThat(result).isEqualTo("110.00000000000001");
        }

        @Test
        @DisplayName("throws when field is not a formula")
        void evaluateFormula_notFormula_throws() {
            UUID fieldId = UUID.randomUUID();
            UUID entityId = UUID.randomUUID();
            var field = buildField(fieldId, "Plain", UdfDataType.TEXT,
                UdfSubject.ACTIVITY, UdfScope.GLOBAL);
            field.setIsFormula(false);

            when(userDefinedFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));

            assertThatThrownBy(() -> udfService.evaluateFormula(fieldId, entityId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not a formula");
        }

        @Test
        @DisplayName("returns null when formula expression is null")
        void evaluateFormula_nullExpression_returnsNull() {
            UUID fieldId = UUID.randomUUID();
            UUID entityId = UUID.randomUUID();
            var field = buildField(fieldId, "Empty", UdfDataType.NUMBER,
                UdfSubject.ACTIVITY, UdfScope.GLOBAL);
            field.setIsFormula(true);
            field.setFormulaExpression(null);

            when(userDefinedFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));

            String result = udfService.evaluateFormula(fieldId, entityId);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("throws when field not found")
        void evaluateFormula_fieldNotFound_throws() {
            UUID fieldId = UUID.randomUUID();
            UUID entityId = UUID.randomUUID();

            when(userDefinedFieldRepository.findById(fieldId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> udfService.evaluateFormula(fieldId, entityId))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
