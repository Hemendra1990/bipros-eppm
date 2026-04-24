package com.bipros.admin.domain.repository;

import com.bipros.admin.domain.model.OrganisationProjectLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrganisationProjectLinkRepository
    extends JpaRepository<OrganisationProjectLink, UUID> {

    List<OrganisationProjectLink> findByOrganisationId(UUID organisationId);

    List<OrganisationProjectLink> findByProjectId(UUID projectId);

    void deleteByOrganisationId(UUID organisationId);
}
