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
}
