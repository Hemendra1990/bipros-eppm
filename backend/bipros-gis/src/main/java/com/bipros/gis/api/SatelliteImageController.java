package com.bipros.gis.api;

import com.bipros.common.dto.ApiResponse;
import com.bipros.gis.application.dto.SatelliteImageRequest;
import com.bipros.gis.application.dto.SatelliteImageResponse;
import com.bipros.gis.application.service.SatelliteImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects/{projectId}/gis/satellite-images")
@RequiredArgsConstructor
public class SatelliteImageController {

    private final SatelliteImageService imageService;

    @PostMapping
    public ResponseEntity<ApiResponse<SatelliteImageResponse>> create(
        @PathVariable UUID projectId,
        @Valid @RequestBody SatelliteImageRequest request
    ) {
        SatelliteImageResponse response = imageService.create(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SatelliteImageResponse>>> getAll(
        @PathVariable UUID projectId,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to
    ) {
        List<SatelliteImageResponse> response;
        if (from != null && to != null) {
            response = imageService.getByProjectAndDateRange(projectId, from, to);
        } else {
            response = imageService.getByProject(projectId);
        }
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{imageId}")
    public ResponseEntity<ApiResponse<SatelliteImageResponse>> getById(
        @PathVariable UUID projectId,
        @PathVariable UUID imageId
    ) {
        SatelliteImageResponse response = imageService.getById(projectId, imageId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{imageId}")
    public ResponseEntity<ApiResponse<SatelliteImageResponse>> update(
        @PathVariable UUID projectId,
        @PathVariable UUID imageId,
        @Valid @RequestBody SatelliteImageRequest request
    ) {
        SatelliteImageResponse response = imageService.update(projectId, imageId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<ApiResponse<Void>> delete(
        @PathVariable UUID projectId,
        @PathVariable UUID imageId
    ) {
        imageService.delete(projectId, imageId);
        return ResponseEntity.noContent().build();
    }
}
