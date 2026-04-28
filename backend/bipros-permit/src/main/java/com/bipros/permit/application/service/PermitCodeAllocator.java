package com.bipros.permit.application.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;

/** Allocates permit codes in the format {@code WP-YYYY-NNNN}. */
@Service
public class PermitCodeAllocator {

    @PersistenceContext
    private EntityManager em;

    /**
     * Atomic increment via Postgres UPSERT + RETURNING. Runs in its own transaction so the
     * sequence advances even if the surrounding permit creation rolls back, preventing duplicate
     * permit codes. {@link org.springframework.data.jpa.repository.Modifying @Modifying} can't be
     * used here because it forces a void/int/Integer return type, but we need the new value.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String allocate() {
        int year = LocalDate.now(ZoneOffset.UTC).getYear();
        Number next = (Number) em.createNativeQuery("""
                INSERT INTO permit.permit_code_sequence (year, last_seq) VALUES (:year, 1)
                ON CONFLICT (year) DO UPDATE SET last_seq = permit_code_sequence.last_seq + 1
                RETURNING last_seq
                """)
                .setParameter("year", year)
                .getSingleResult();
        return "WP-%d-%04d".formatted(year, next.longValue());
    }
}
