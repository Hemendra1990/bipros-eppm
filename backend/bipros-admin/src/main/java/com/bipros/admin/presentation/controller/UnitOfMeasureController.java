package com.bipros.admin.presentation.controller;

import com.bipros.common.dto.ApiResponse;
import com.bipros.admin.application.dto.CreateUnitOfMeasureRequest;
import com.bipros.admin.application.dto.UnitOfMeasureDto;
import com.bipros.admin.application.service.UnitOfMeasureService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/v1/admin/units-of-measure")
@PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
@RequiredArgsConstructor
public class UnitOfMeasureController {

    private final UnitOfMeasureService unitOfMeasureService;

    @PostMapping
    public ResponseEntity<ApiResponse<UnitOfMeasureDto>> createUnitOfMeasure(
        @Valid @RequestBody CreateUnitOfMeasureRequest request) {
        UnitOfMeasureDto unit = unitOfMeasureService.createUnitOfMeasure(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(unit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UnitOfMeasureDto>> getUnitOfMeasure(@PathVariable UUID id) {
        UnitOfMeasureDto unit = unitOfMeasureService.getUnitOfMeasure(id);
        return ResponseEntity.ok(ApiResponse.ok(unit));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UnitOfMeasureDto>>> listUnitsOfMeasure() {
        List<UnitOfMeasureDto> units = unitOfMeasureService.listUnitsOfMeasure();
        return ResponseEntity.ok(ApiResponse.ok(units));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UnitOfMeasureDto>> updateUnitOfMeasure(
        @PathVariable UUID id,
        @Valid @RequestBody CreateUnitOfMeasureRequest request) {
        UnitOfMeasureDto unit = unitOfMeasureService.updateUnitOfMeasure(id, request);
        return ResponseEntity.ok(ApiResponse.ok(unit));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUnitOfMeasure(@PathVariable UUID id) {
        unitOfMeasureService.deleteUnitOfMeasure(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
