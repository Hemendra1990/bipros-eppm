package com.bipros.contract.api;

import com.bipros.common.dto.ApiResponse;
import com.bipros.contract.application.dto.VariationOrderRequest;
import com.bipros.contract.application.dto.VariationOrderResponse;
import com.bipros.contract.application.service.VariationOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/contracts/{contractId}/variation-orders")
@RequiredArgsConstructor
public class VariationOrderController {

    private final VariationOrderService variationOrderService;

    @PostMapping
    public ResponseEntity<ApiResponse<VariationOrderResponse>> create(
        @PathVariable UUID contractId,
        @Valid @RequestBody VariationOrderRequest request) {
        VariationOrderResponse response = variationOrderService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<VariationOrderResponse>>> listByContract(
        @PathVariable UUID contractId) {
        List<VariationOrderResponse> response = variationOrderService.listByContract(contractId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VariationOrderResponse>> getById(
        @PathVariable UUID contractId,
        @PathVariable UUID id) {
        VariationOrderResponse response = variationOrderService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VariationOrderResponse>> update(
        @PathVariable UUID contractId,
        @PathVariable UUID id,
        @Valid @RequestBody VariationOrderRequest request) {
        VariationOrderResponse response = variationOrderService.update(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable UUID contractId,
        @PathVariable UUID id) {
        variationOrderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
