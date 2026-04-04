package com.bipros.udf.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.udf.application.dto.CreateUserDefinedFieldRequest;
import com.bipros.udf.application.dto.SetUdfValueRequest;
import com.bipros.udf.application.dto.UdfValueResponse;
import com.bipros.udf.application.dto.UserDefinedFieldDto;
import com.bipros.udf.domain.engine.FormulaEvaluator;
import com.bipros.udf.domain.model.UdfDataType;
import com.bipros.udf.domain.model.UdfScope;
import com.bipros.udf.domain.model.UdfSubject;
import com.bipros.udf.domain.model.UserDefinedField;
import com.bipros.udf.domain.model.UdfValue;
import com.bipros.udf.domain.repository.UserDefinedFieldRepository;
import com.bipros.udf.domain.repository.UdfValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UdfService {

    private final UserDefinedFieldRepository userDefinedFieldRepository;
    private final UdfValueRepository udfValueRepository;

    private static final int MAX_FIELDS_PER_TYPE_SUBJECT = 100;
    private static final int MAX_INDICATOR_FIELDS = 20;

    public UserDefinedFieldDto createField(CreateUserDefinedFieldRequest request) {
        validateFieldLimit(request.getDataType(), request.getSubject());

        UserDefinedField field = new UserDefinedField();
        field.setName(request.getName());
        field.setDescription(request.getDescription());
        field.setDataType(request.getDataType());
        field.setSubject(request.getSubject());
        field.setScope(request.getScope());
        field.setProjectId(request.getProjectId());
        field.setIsFormula(request.getIsFormula());
        field.setFormulaExpression(request.getFormulaExpression());
        field.setDefaultValue(request.getDefaultValue());
        field.setSortOrder(request.getSortOrder());

        UserDefinedField saved = userDefinedFieldRepository.save(field);
        return mapToDto(saved);
    }

    public UserDefinedFieldDto updateField(UUID fieldId, CreateUserDefinedFieldRequest request) {
        UserDefinedField field = userDefinedFieldRepository.findById(fieldId)
            .orElseThrow(() -> new ResourceNotFoundException("UserDefinedField", fieldId));

        field.setName(request.getName());
        field.setDescription(request.getDescription());
        field.setDataType(request.getDataType());
        field.setSubject(request.getSubject());
        field.setScope(request.getScope());
        field.setProjectId(request.getProjectId());
        field.setIsFormula(request.getIsFormula());
        field.setFormulaExpression(request.getFormulaExpression());
        field.setDefaultValue(request.getDefaultValue());
        field.setSortOrder(request.getSortOrder());

        UserDefinedField updated = userDefinedFieldRepository.save(field);
        return mapToDto(updated);
    }

    public void deleteField(UUID fieldId) {
        UserDefinedField field = userDefinedFieldRepository.findById(fieldId)
            .orElseThrow(() -> new ResourceNotFoundException("UserDefinedField", fieldId));

        udfValueRepository.deleteAll(udfValueRepository.findByUserDefinedFieldId(fieldId));
        userDefinedFieldRepository.delete(field);
    }

    @Transactional(readOnly = true)
    public List<UserDefinedFieldDto> getFieldsBySubject(UdfSubject subject, UdfScope scope, UUID projectId) {
        List<UserDefinedField> fields;
        if (scope == UdfScope.PROJECT && projectId != null) {
            fields = userDefinedFieldRepository.findBySubjectAndProjectId(subject, projectId);
        } else if (scope == UdfScope.GLOBAL) {
            fields = userDefinedFieldRepository.findBySubjectAndScope(subject, UdfScope.GLOBAL);
        } else {
            fields = userDefinedFieldRepository.findBySubject(subject);
        }
        return fields.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public UdfValueResponse setValue(UUID fieldId, UUID entityId, SetUdfValueRequest request) {
        UserDefinedField field = userDefinedFieldRepository.findById(fieldId)
            .orElseThrow(() -> new ResourceNotFoundException("UserDefinedField", fieldId));

        validateValueType(field.getDataType(), request);

        UdfValue value = udfValueRepository.findByUserDefinedFieldIdAndEntityId(fieldId, entityId)
            .orElse(new UdfValue());

        value.setUserDefinedFieldId(fieldId);
        value.setEntityId(entityId);

        clearValueFields(value);

        switch (field.getDataType()) {
            case TEXT -> value.setTextValue(request.getTextValue());
            case NUMBER -> value.setNumberValue(request.getNumberValue());
            case COST -> value.setCostValue(request.getCostValue());
            case DATE -> value.setDateValue(request.getDateValue());
            case INDICATOR -> value.setIndicatorValue(request.getIndicatorValue());
            case CODE -> value.setCodeValue(request.getCodeValue());
        }

        UdfValue saved = udfValueRepository.save(value);
        return mapToValueResponse(saved);
    }

    @Transactional(readOnly = true)
    public UdfValueResponse getValue(UUID fieldId, UUID entityId) {
        UdfValue value = udfValueRepository.findByUserDefinedFieldIdAndEntityId(fieldId, entityId)
            .orElseThrow(() -> new ResourceNotFoundException("UdfValue", fieldId));
        return mapToValueResponse(value);
    }

    @Transactional(readOnly = true)
    public List<UdfValueResponse> getValuesForEntity(UUID entityId) {
        return udfValueRepository.findByEntityId(entityId).stream()
            .map(this::mapToValueResponse)
            .collect(Collectors.toList());
    }

    public String evaluateFormula(UUID fieldId, UUID entityId) {
        UserDefinedField field = userDefinedFieldRepository.findById(fieldId)
            .orElseThrow(() -> new ResourceNotFoundException("UserDefinedField", fieldId));

        if (!field.getIsFormula()) {
            throw new BusinessRuleException("FIELD_NOT_FORMULA", "Field is not a formula");
        }

        String expression = field.getFormulaExpression();
        if (expression == null) {
            return null;
        }

        Map<String, Object> context = buildContext(entityId);
        FormulaEvaluator evaluator = new FormulaEvaluator(expression, context);
        return evaluator.evaluate();
    }

    private Map<String, Object> buildContext(UUID entityId) {
        Map<String, Object> context = new HashMap<>();
        List<UdfValue> entityValues = udfValueRepository.findByEntityId(entityId);

        for (UdfValue val : entityValues) {
            UserDefinedField refField = userDefinedFieldRepository.findById(val.getUserDefinedFieldId())
                .orElse(null);
            if (refField != null) {
                Object value = extractValue(val);
                context.put(refField.getName(), value);
            }
        }

        return context;
    }

    private Object extractValue(UdfValue value) {
        if (value.getNumberValue() != null) return value.getNumberValue();
        if (value.getCostValue() != null) return value.getCostValue();
        if (value.getTextValue() != null) return value.getTextValue();
        if (value.getDateValue() != null) return value.getDateValue();
        if (value.getIndicatorValue() != null) return value.getIndicatorValue();
        if (value.getCodeValue() != null) return value.getCodeValue();
        return null;
    }

    private void validateFieldLimit(UdfDataType dataType, UdfSubject subject) {
        long count = userDefinedFieldRepository.countByDataTypeAndSubject(dataType, subject);
        int limit = UdfDataType.INDICATOR.equals(dataType) ? MAX_INDICATOR_FIELDS : MAX_FIELDS_PER_TYPE_SUBJECT;

        if (count >= limit) {
            throw new BusinessRuleException("FIELD_LIMIT_EXCEEDED",
                String.format("Maximum %d fields of type %s for subject %s already exists",
                    limit, dataType, subject));
        }
    }

    private void validateValueType(UdfDataType dataType, SetUdfValueRequest request) {
        switch (dataType) {
            case TEXT -> {
                if (request.getTextValue() == null) {
                    throw new BusinessRuleException("INVALID_VALUE", "Text value is required for TEXT type");
                }
            }
            case NUMBER -> {
                if (request.getNumberValue() == null) {
                    throw new BusinessRuleException("INVALID_VALUE", "Number value is required for NUMBER type");
                }
            }
            case COST -> {
                if (request.getCostValue() == null) {
                    throw new BusinessRuleException("INVALID_VALUE", "Cost value is required for COST type");
                }
            }
            case DATE -> {
                if (request.getDateValue() == null) {
                    throw new BusinessRuleException("INVALID_VALUE", "Date value is required for DATE type");
                }
            }
            case INDICATOR -> {
                if (request.getIndicatorValue() == null) {
                    throw new BusinessRuleException("INVALID_VALUE", "Indicator value is required for INDICATOR type");
                }
            }
            case CODE -> {
                if (request.getCodeValue() == null) {
                    throw new BusinessRuleException("INVALID_VALUE", "Code value is required for CODE type");
                }
            }
        }
    }

    private void clearValueFields(UdfValue value) {
        value.setTextValue(null);
        value.setNumberValue(null);
        value.setCostValue(null);
        value.setDateValue(null);
        value.setIndicatorValue(null);
        value.setCodeValue(null);
    }

    private String extractValueAsString(UdfValue value) {
        if (value.getTextValue() != null) return value.getTextValue();
        if (value.getNumberValue() != null) return String.valueOf(value.getNumberValue());
        if (value.getCostValue() != null) return value.getCostValue().toPlainString();
        if (value.getDateValue() != null) return value.getDateValue().toString();
        if (value.getIndicatorValue() != null) return value.getIndicatorValue().name();
        if (value.getCodeValue() != null) return value.getCodeValue();
        return "";
    }


    private UserDefinedFieldDto mapToDto(UserDefinedField field) {
        return UserDefinedFieldDto.builder()
            .id(field.getId())
            .name(field.getName())
            .description(field.getDescription())
            .dataType(field.getDataType())
            .subject(field.getSubject())
            .scope(field.getScope())
            .projectId(field.getProjectId())
            .isFormula(field.getIsFormula())
            .formulaExpression(field.getFormulaExpression())
            .defaultValue(field.getDefaultValue())
            .sortOrder(field.getSortOrder())
            .build();
    }

    private UdfValueResponse mapToValueResponse(UdfValue value) {
        return UdfValueResponse.builder()
            .id(value.getId())
            .userDefinedFieldId(value.getUserDefinedFieldId())
            .entityId(value.getEntityId())
            .textValue(value.getTextValue())
            .numberValue(value.getNumberValue())
            .costValue(value.getCostValue())
            .dateValue(value.getDateValue())
            .indicatorValue(value.getIndicatorValue())
            .codeValue(value.getCodeValue())
            .build();
    }
}
