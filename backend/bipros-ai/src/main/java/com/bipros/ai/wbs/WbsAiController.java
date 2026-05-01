package com.bipros.ai.wbs;

import com.bipros.ai.wbs.dto.WbsAiApplyRequest;
import com.bipros.ai.wbs.dto.WbsAiGenerateRequest;
import com.bipros.ai.wbs.dto.WbsAiGenerationResponse;
import com.bipros.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects/{projectId}/wbs/ai")
@RequiredArgsConstructor
public class WbsAiController {

    private final WbsAiGenerationService wbsAiGenerationService;

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER')")
    public ResponseEntity<ApiResponse<WbsAiGenerationResponse>> generate(
            @PathVariable UUID projectId,
            @RequestBody WbsAiGenerateRequest req) {
        WbsAiGenerationResponse response = wbsAiGenerationService.generate(projectId, req);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/apply")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER')")
    public ResponseEntity<ApiResponse<List<String>>> apply(
            @PathVariable UUID projectId,
            @RequestBody WbsAiApplyRequest req) {
        List<String> collisions = wbsAiGenerationService.applyGenerated(projectId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(collisions));
    }
}
