package com.bipros.ai.agent.domain;

import com.bipros.ai.agent.core.Severity;
import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Per-project override of the default severity → channels routing. A null {@code projectId} row is
 * a global default. {@code channelsCsv} is a comma-separated list of channel keys
 * (e.g. {@code "in_app,email,whatsapp"}); {@code immediate=false} defers to the daily digest.
 */
@Entity
@Table(schema = "ai", name = "agent_notification_rule")
@Getter
@Setter
public class AgentNotificationRule extends BaseEntity {

    @Column(name = "project_id")
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Column(name = "channels_csv", nullable = false, length = 120)
    private String channelsCsv;

    @Column(nullable = false)
    private boolean immediate = true;
}
