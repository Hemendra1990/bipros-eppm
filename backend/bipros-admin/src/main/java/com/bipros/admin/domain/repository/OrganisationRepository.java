package com.bipros.admin.domain.repository;

import com.bipros.admin.domain.model.Organisation;
import com.bipros.admin.domain.model.OrganisationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganisationRepository extends JpaRepository<Organisation, UUID> {

    Optional<Organisation> findByCode(String code);

    List<Organisation> findByOrganisationType(OrganisationType organisationType);

    List<Organisation> findByActiveTrue();

    Optional<Organisation> findByPan(String pan);

    boolean existsByCode(String code);

    boolean existsByPan(String pan);

    /** Find the highest numeric suffix among CONT-NNN codes so the service can pick NNN+1. */
    @org.springframework.data.jpa.repository.Query(
        "select max(cast(substring(o.code, 6) as integer)) " +
        "from Organisation o where o.code like 'CONT-%' and length(o.code) <= 10")
    Integer findMaxContractorSuffix();
}
