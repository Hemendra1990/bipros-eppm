package com.bipros.admin.application.service;

import com.bipros.admin.application.dto.OrganisationDto;
import com.bipros.admin.domain.model.Organisation;
import com.bipros.admin.domain.model.OrganisationType;
import com.bipros.admin.domain.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OrganisationService {

    private final OrganisationRepository organisationRepository;

    @Transactional(readOnly = true)
    public List<OrganisationDto> listAll() {
        return organisationRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<OrganisationDto> listByType(OrganisationType type) {
        return organisationRepository.findByOrganisationType(type).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public OrganisationDto get(UUID id) {
        return organisationRepository.findById(id).map(this::toDto)
                .orElseThrow(() -> new IllegalArgumentException("Organisation not found: " + id));
    }

    private OrganisationDto toDto(Organisation o) {
        return OrganisationDto.builder()
                .id(o.getId())
                .code(o.getCode())
                .name(o.getName())
                .shortName(o.getShortName())
                .organisationType(o.getOrganisationType())
                .parentOrganisationId(o.getParentOrganisationId())
                .active(o.isActive())
                .build();
    }
}
