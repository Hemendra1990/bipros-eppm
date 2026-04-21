package com.bipros.security.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Corridor/WBS-scoped access for IC-PMS users.
 * A single row with {@code wbsNodeId = NULL} encodes the "All Corridors" sentinel (universal scope).
 * Other rows scope the user to specific WBS nodes (typically top-level corridor nodes like DMIC-N03).
 */
@Entity
@Table(
        name = "user_corridor_scope",
        schema = "public",
        indexes = {
                @Index(name = "idx_user_corridor_scope_user", columnList = "user_id"),
                @Index(name = "idx_user_corridor_scope_wbs", columnList = "wbs_node_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCorridorScope extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** NULL sentinel = all corridors / universal scope. */
    @Column(name = "wbs_node_id")
    private UUID wbsNodeId;
}
