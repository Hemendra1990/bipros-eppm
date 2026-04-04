package com.bipros.activity.application.service;

import com.bipros.activity.application.dto.CreateRelationshipRequest;
import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityRelationship;
import com.bipros.activity.domain.model.RelationshipType;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.common.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RelationshipService Tests")
class RelationshipServiceTest {

  @Mock
  private ActivityRelationshipRepository relationshipRepository;

  @Mock
  private ActivityRepository activityRepository;

  private RelationshipService relationshipService;

  @BeforeEach
  void setUp() {
    relationshipService = new RelationshipService(relationshipRepository, activityRepository);
  }

  @Nested
  @DisplayName("Valid relationship chains")
  class ValidRelationshipTests {

    @Test
    @DisplayName("linear chain A->B->C succeeds")
    void linearChainSucceeds() {
      UUID projectId = UUID.randomUUID();
      UUID activityA = UUID.randomUUID();
      UUID activityB = UUID.randomUUID();
      UUID activityC = UUID.randomUUID();

      // Mock activities exist
      Activity mockActivityA = new Activity();
      mockActivityA.setId(activityA);
      mockActivityA.setProjectId(projectId);
      Activity mockActivityB = new Activity();
      mockActivityB.setId(activityB);
      mockActivityB.setProjectId(projectId);
      Activity mockActivityC = new Activity();
      mockActivityC.setId(activityC);
      mockActivityC.setProjectId(projectId);

      when(activityRepository.findById(activityA)).thenReturn(Optional.of(mockActivityA));
      when(activityRepository.findById(activityB)).thenReturn(Optional.of(mockActivityB));
      when(activityRepository.findById(activityC)).thenReturn(Optional.of(mockActivityC));

      // No existing relationships
      when(relationshipRepository.existsByPredecessorActivityIdAndSuccessorActivityId(any(), any()))
          .thenReturn(false);
      when(relationshipRepository.findByPredecessorActivityId(any())).thenReturn(new ArrayList<>());

      ActivityRelationship rel1 = createRelationship(projectId, activityA, activityB);
      ActivityRelationship rel2 = createRelationship(projectId, activityB, activityC);

      when(relationshipRepository.save(any())).thenReturn(rel1, rel2);

      // Should succeed without throwing
      var saved1 = relationshipService.createRelationship(
          new CreateRelationshipRequest(activityA, activityB, RelationshipType.FINISH_TO_START, 0.0)
      );
      var saved2 = relationshipService.createRelationship(
          new CreateRelationshipRequest(activityB, activityC, RelationshipType.FINISH_TO_START, 0.0)
      );

      assertNotNull(saved1);
      assertNotNull(saved2);
      verify(relationshipRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("diamond pattern A->B,C; B,C->D succeeds")
    void diamondPatternSucceeds() {
      UUID projectId = UUID.randomUUID();
      UUID activityA = UUID.randomUUID();
      UUID activityB = UUID.randomUUID();
      UUID activityC = UUID.randomUUID();
      UUID activityD = UUID.randomUUID();

      // Mock activities exist
      Activity mockActivityA = new Activity();
      mockActivityA.setId(activityA);
      mockActivityA.setProjectId(projectId);
      Activity mockActivityB = new Activity();
      mockActivityB.setId(activityB);
      mockActivityB.setProjectId(projectId);
      Activity mockActivityC = new Activity();
      mockActivityC.setId(activityC);
      mockActivityC.setProjectId(projectId);
      Activity mockActivityD = new Activity();
      mockActivityD.setId(activityD);
      mockActivityD.setProjectId(projectId);

      when(activityRepository.findById(activityA)).thenReturn(Optional.of(mockActivityA));
      when(activityRepository.findById(activityB)).thenReturn(Optional.of(mockActivityB));
      when(activityRepository.findById(activityC)).thenReturn(Optional.of(mockActivityC));
      when(activityRepository.findById(activityD)).thenReturn(Optional.of(mockActivityD));

      // No duplicate relationships
      when(relationshipRepository.existsByPredecessorActivityIdAndSuccessorActivityId(any(), any()))
          .thenReturn(false);
      // No circular dependencies
      when(relationshipRepository.findByPredecessorActivityId(any())).thenReturn(new ArrayList<>());
      when(relationshipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      // Create diamond relationships
      relationshipService.createRelationship(
          new CreateRelationshipRequest(activityA, activityB, RelationshipType.FINISH_TO_START, 0.0)
      );
      relationshipService.createRelationship(
          new CreateRelationshipRequest(activityA, activityC, RelationshipType.FINISH_TO_START, 0.0)
      );
      relationshipService.createRelationship(
          new CreateRelationshipRequest(activityB, activityD, RelationshipType.FINISH_TO_START, 0.0)
      );
      relationshipService.createRelationship(
          new CreateRelationshipRequest(activityC, activityD, RelationshipType.FINISH_TO_START, 0.0)
      );

      verify(relationshipRepository, times(4)).save(any());
    }
  }

  @Nested
  @DisplayName("Circular dependency detection")
  class CircularDependencyTests {

    @Test
    @DisplayName("self-reference A->A throws BusinessRuleException")
    void selfReferenceThrows() {
      UUID activityA = UUID.randomUUID();
      UUID projectId = UUID.randomUUID();

      // Mock activity exists
      Activity mockActivity = new Activity();
      mockActivity.setId(activityA);
      mockActivity.setProjectId(projectId);
      when(activityRepository.findById(activityA)).thenReturn(Optional.of(mockActivity));

      BusinessRuleException exception = assertThrows(
          BusinessRuleException.class,
          () -> relationshipService.createRelationship(
              new CreateRelationshipRequest(activityA, activityA, RelationshipType.FINISH_TO_START, 0.0)
          )
      );

      assertEquals("SELF_RELATIONSHIP", exception.getRuleCode());
      verify(relationshipRepository, never()).save(any());
    }

    @Test
    @DisplayName("two-activity cycle A->B->A throws BusinessRuleException")
    void twoActivityCycleThrows() {
      UUID projectId = UUID.randomUUID();
      UUID activityA = UUID.randomUUID();
      UUID activityB = UUID.randomUUID();

      // Mock activities exist
      Activity mockActivityA = new Activity();
      mockActivityA.setId(activityA);
      mockActivityA.setProjectId(projectId);
      Activity mockActivityB = new Activity();
      mockActivityB.setId(activityB);
      mockActivityB.setProjectId(projectId);

      when(activityRepository.findById(activityA)).thenReturn(Optional.of(mockActivityA));
      when(activityRepository.findById(activityB)).thenReturn(Optional.of(mockActivityB));

      // No duplicate relationship
      when(relationshipRepository.existsByPredecessorActivityIdAndSuccessorActivityId(any(), any()))
          .thenReturn(false);

      // Existing relationship B->A
      ActivityRelationship existingRel = createRelationship(projectId, activityB, activityA);
      when(relationshipRepository.findByPredecessorActivityId(activityB))
          .thenReturn(List.of(existingRel));

      // Trying to add A->B which would create cycle
      BusinessRuleException exception = assertThrows(
          BusinessRuleException.class,
          () -> relationshipService.createRelationship(
              new CreateRelationshipRequest(activityA, activityB, RelationshipType.FINISH_TO_START, 0.0)
          )
      );

      assertEquals("CIRCULAR_DEPENDENCY", exception.getRuleCode());
      verify(relationshipRepository, never()).save(any());
    }

    @Test
    @DisplayName("three-activity cycle A->B->C->A throws BusinessRuleException")
    void threeActivityCycleThrows() {
      UUID projectId = UUID.randomUUID();
      UUID activityA = UUID.randomUUID();
      UUID activityB = UUID.randomUUID();
      UUID activityC = UUID.randomUUID();

      // Mock activities exist
      Activity mockActivityA = new Activity();
      mockActivityA.setId(activityA);
      mockActivityA.setProjectId(projectId);
      Activity mockActivityB = new Activity();
      mockActivityB.setId(activityB);
      mockActivityB.setProjectId(projectId);
      Activity mockActivityC = new Activity();
      mockActivityC.setId(activityC);
      mockActivityC.setProjectId(projectId);

      when(activityRepository.findById(activityA)).thenReturn(Optional.of(mockActivityA));
      when(activityRepository.findById(activityC)).thenReturn(Optional.of(mockActivityC));

      // No duplicate relationships
      when(relationshipRepository.existsByPredecessorActivityIdAndSuccessorActivityId(any(), any()))
          .thenReturn(false);

      // Existing relationships: A->B, B->C
      ActivityRelationship relAB = createRelationship(projectId, activityA, activityB);
      ActivityRelationship relBC = createRelationship(projectId, activityB, activityC);

      when(relationshipRepository.findByPredecessorActivityId(activityA))
          .thenReturn(List.of(relAB));
      when(relationshipRepository.findByPredecessorActivityId(activityB))
          .thenReturn(List.of(relBC));

      // Trying to add C->A which would create cycle
      BusinessRuleException exception = assertThrows(
          BusinessRuleException.class,
          () -> relationshipService.createRelationship(
              new CreateRelationshipRequest(activityC, activityA, RelationshipType.FINISH_TO_START, 0.0)
          )
      );

      assertEquals("CIRCULAR_DEPENDENCY", exception.getRuleCode());
      verify(relationshipRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("Delete relationship")
  class DeleteRelationshipTests {

    @Test
    @DisplayName("delete existing relationship succeeds")
    void deleteRelationshipSucceeds() {
      UUID relationshipId = UUID.randomUUID();
      UUID projectId = UUID.randomUUID();
      UUID activityA = UUID.randomUUID();
      UUID activityB = UUID.randomUUID();

      ActivityRelationship relationship = createRelationship(projectId, activityA, activityB);
      relationship.setId(relationshipId);

      when(relationshipRepository.findById(relationshipId)).thenReturn(Optional.of(relationship));

      relationshipService.deleteRelationship(relationshipId);

      verify(relationshipRepository).deleteById(relationshipId);
    }
  }

  // Helper methods

  private ActivityRelationship createRelationship(
      UUID projectId,
      UUID predecessorId,
      UUID successorId) {
    ActivityRelationship rel = new ActivityRelationship();
    rel.setId(UUID.randomUUID());
    rel.setProjectId(projectId);
    rel.setPredecessorActivityId(predecessorId);
    rel.setSuccessorActivityId(successorId);
    rel.setRelationshipType(RelationshipType.FINISH_TO_START);
    rel.setLag(0.0);
    rel.setIsExternal(false);
    return rel;
  }
}
