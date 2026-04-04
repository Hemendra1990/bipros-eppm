package com.bipros.activity.application.dto;

import com.bipros.activity.domain.model.ActivityRelationship;
import com.bipros.activity.domain.model.RelationshipType;

import java.util.UUID;

public record RelationshipResponse(
    UUID id,
    UUID predecessorActivityId,
    UUID successorActivityId,
    RelationshipType relationshipType,
    Double lag,
    Boolean isExternal
) {
  public static RelationshipResponse from(ActivityRelationship relationship) {
    return new RelationshipResponse(
        relationship.getId(),
        relationship.getPredecessorActivityId(),
        relationship.getSuccessorActivityId(),
        relationship.getRelationshipType(),
        relationship.getLag(),
        relationship.getIsExternal()
    );
  }
}
