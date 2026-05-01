package com.bipros.ai.activity;

import com.bipros.ai.activity.dto.ActivityAiApplyRequest;
import com.bipros.ai.activity.dto.ActivityAiApplyResponse;
import com.bipros.ai.activity.dto.ActivityAiGenerateRequest;
import com.bipros.ai.activity.dto.ActivityAiGenerationResponse;
import com.bipros.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/projects/{projectId}/activities/ai")
@RequiredArgsConstructor
public class ActivityAiController {

    private final ActivityAiGenerationService activityAiGenerationService;

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER')")
    public ResponseEntity<ApiResponse<ActivityAiGenerationResponse>> generate(
            @PathVariable UUID projectId,
            @RequestBody ActivityAiGenerateRequest req) {
        ActivityAiGenerationResponse response = activityAiGenerationService.generate(projectId, req);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/apply")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER')")
    public ResponseEntity<ApiResponse<ActivityAiApplyResponse>> apply(
            @PathVariable UUID projectId,
            @RequestBody ActivityAiApplyRequest req) {
        ActivityAiApplyResponse response = activityAiGenerationService.applyGenerated(projectId, req);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
