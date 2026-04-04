package com.bipros.activity.api;

import com.bipros.activity.application.dto.CreateRelationshipRequest;
import com.bipros.activity.application.dto.RelationshipResponse;
import com.bipros.activity.application.service.RelationshipService;
import com.bipros.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects/{projectId}/relationships")
@RequiredArgsConstructor
public class RelationshipController {

  private final RelationshipService relationshipService;

  @PostMapping
  public ResponseEntity<ApiResponse<RelationshipResponse>> createRelationship(
      @PathVariable UUID projectId,
      @Valid @RequestBody CreateRelationshipRequest request) {
    RelationshipResponse response = relationshipService.createRelationship(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<RelationshipResponse>>> getRelationships(
      @PathVariable UUID projectId) {
    List<RelationshipResponse> response = relationshipService.getRelationships(projectId);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @DeleteMapping("/{relationshipId}")
  public ResponseEntity<Void> deleteRelationship(
      @PathVariable UUID projectId,
      @PathVariable UUID relationshipId) {
    relationshipService.deleteRelationship(relationshipId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/predecessors/{activityId}")
  public ResponseEntity<ApiResponse<List<RelationshipResponse>>> getPredecessors(
      @PathVariable UUID projectId,
      @PathVariable UUID activityId) {
    List<RelationshipResponse> response = relationshipService.getPredecessors(activityId);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @GetMapping("/successors/{activityId}")
  public ResponseEntity<ApiResponse<List<RelationshipResponse>>> getSuccessors(
      @PathVariable UUID projectId,
      @PathVariable UUID activityId) {
    List<RelationshipResponse> response = relationshipService.getSuccessors(activityId);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }
}
