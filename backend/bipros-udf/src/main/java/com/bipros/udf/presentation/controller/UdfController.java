package com.bipros.udf.presentation.controller;

import com.bipros.common.dto.ApiResponse;
import com.bipros.udf.application.dto.CreateUserDefinedFieldRequest;
import com.bipros.udf.application.dto.SetUdfValueRequest;
import com.bipros.udf.application.dto.UdfValueResponse;
import com.bipros.udf.application.dto.UserDefinedFieldDto;
import com.bipros.udf.application.service.UdfService;
import com.bipros.udf.domain.model.UdfScope;
import com.bipros.udf.domain.model.UdfSubject;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/udf")
@RequiredArgsConstructor
public class UdfController {

    private final UdfService udfService;

    @PostMapping("/fields")
    public ResponseEntity<ApiResponse<UserDefinedFieldDto>> createField(
        @Valid @RequestBody CreateUserDefinedFieldRequest request) {
        UserDefinedFieldDto field = udfService.createField(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(field));
    }

    @GetMapping("/fields")
    public ResponseEntity<ApiResponse<List<UserDefinedFieldDto>>> listFields(
        @RequestParam UdfSubject subject,
        @RequestParam(required = false) UdfScope scope,
        @RequestParam(required = false) UUID projectId) {
        UdfScope resolvedScope = scope != null ? scope : UdfScope.GLOBAL;
        List<UserDefinedFieldDto> fields = udfService.getFieldsBySubject(subject, resolvedScope, projectId);
        return ResponseEntity.ok(ApiResponse.ok(fields));
    }

    @PutMapping("/fields/{fieldId}")
    public ResponseEntity<ApiResponse<UserDefinedFieldDto>> updateField(
        @PathVariable UUID fieldId,
        @Valid @RequestBody CreateUserDefinedFieldRequest request) {
        UserDefinedFieldDto field = udfService.updateField(fieldId, request);
        return ResponseEntity.ok(ApiResponse.ok(field));
    }

    @DeleteMapping("/fields/{fieldId}")
    public ResponseEntity<ApiResponse<Void>> deleteField(@PathVariable UUID fieldId) {
        udfService.deleteField(fieldId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PutMapping("/values/{fieldId}/{entityId}")
    public ResponseEntity<ApiResponse<UdfValueResponse>> setValue(
        @PathVariable UUID fieldId,
        @PathVariable UUID entityId,
        @Valid @RequestBody SetUdfValueRequest request) {
        UdfValueResponse value = udfService.setValue(fieldId, entityId, request);
        return ResponseEntity.ok(ApiResponse.ok(value));
    }

    @GetMapping("/values/{fieldId}/{entityId}")
    public ResponseEntity<ApiResponse<UdfValueResponse>> getValue(
        @PathVariable UUID fieldId,
        @PathVariable UUID entityId) {
        UdfValueResponse value = udfService.getValue(fieldId, entityId);
        return ResponseEntity.ok(ApiResponse.ok(value));
    }

    @GetMapping("/values/{entityId}")
    public ResponseEntity<ApiResponse<List<UdfValueResponse>>> getValuesForEntity(
        @PathVariable UUID entityId) {
        List<UdfValueResponse> values = udfService.getValuesForEntity(entityId);
        return ResponseEntity.ok(ApiResponse.ok(values));
    }

    @GetMapping("/fields/{fieldId}/evaluate/{entityId}")
    public ResponseEntity<ApiResponse<String>> evaluateFormula(
        @PathVariable UUID fieldId,
        @PathVariable UUID entityId) {
        String result = udfService.evaluateFormula(fieldId, entityId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
