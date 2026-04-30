package com.bipros.common.scheduling;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Repository
public interface ScheduledJobLeaseRepository extends JpaRepository<ScheduledJobLease, String> {

    /** Returns 1 if this caller acquired the lease for the given job; 0 if another holder still has it. */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO public.scheduled_job_lease (name, until, owner) VALUES (:name, :until, :owner)
            ON CONFLICT (name) DO UPDATE SET until = EXCLUDED.until, owner = EXCLUDED.owner
                WHERE public.scheduled_job_lease.until < :now
            """, nativeQuery = true)
    int tryAcquire(@Param("name") String name,
                   @Param("until") Instant until,
                   @Param("now") Instant now,
                   @Param("owner") String owner);
}
