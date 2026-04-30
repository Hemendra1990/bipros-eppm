package com.bipros.common.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Multi-instance lease for {@code @Scheduled} jobs. A node grabs the lease via
 * {@code INSERT … ON CONFLICT (name) DO UPDATE … WHERE until < now() RETURNING …}
 * so only one instance fires the job within the lease window.
 */
@Entity
@Table(name = "scheduled_job_lease", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledJobLease {

    @Id
    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "until", nullable = false)
    private Instant until;

    @Column(name = "owner", length = 80)
    private String owner;
}
