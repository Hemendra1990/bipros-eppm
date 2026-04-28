package com.bipros.permit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-year counter for permit codes (WP-YYYY-NNNN). Atomic allocator hits this row
 * via INSERT … ON CONFLICT DO UPDATE … RETURNING — see PermitCodeAllocator.
 */
@Entity
@Table(name = "permit_code_sequence", schema = "permit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PermitCodeSequence {

    @Id
    @Column(nullable = false)
    private Integer year;

    @Column(name = "last_seq", nullable = false)
    private Long lastSeq;
}
