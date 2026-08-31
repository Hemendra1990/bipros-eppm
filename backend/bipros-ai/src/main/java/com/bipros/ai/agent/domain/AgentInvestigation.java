package com.bipros.ai.agent.domain;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** A supervisor-mode investigation: a natural-language question answered by orchestrating agents-as-tools. */
@Entity
@Table(schema = "ai", name = "agent_investigation")
@Getter
@Setter
public class AgentInvestigation extends BaseEntity {

    @Column(name = "project_id")
    private UUID projectId;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(columnDefinition = "text")
    private String answer;

    /** JSON array of agent-run UUIDs spawned while answering. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "run_ids_json", columnDefinition = "jsonb")
    private String runIdsJson;

    @Column(name = "tokens_input")
    private Integer tokensInput;

    @Column(name = "tokens_output")
    private Integer tokensOutput;

    @Column(name = "asked_by")
    private UUID askedBy;
}
