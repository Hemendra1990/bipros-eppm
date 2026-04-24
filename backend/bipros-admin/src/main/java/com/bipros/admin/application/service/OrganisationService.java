package com.bipros.admin.application.service;

import com.bipros.admin.application.dto.CreateOrganisationRequest;
import com.bipros.admin.application.dto.OrganisationDto;
import com.bipros.admin.domain.model.Organisation;
import com.bipros.admin.domain.model.OrganisationProjectLink;
import com.bipros.admin.domain.model.OrganisationRegistrationStatus;
import com.bipros.admin.domain.model.OrganisationType;
import com.bipros.admin.domain.repository.OrganisationProjectLinkRepository;
import com.bipros.admin.domain.repository.OrganisationRepository;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrganisationService {

    private final OrganisationRepository organisationRepository;
    private final OrganisationProjectLinkRepository linkRepository;
    private final AuditService auditService;

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
                .orElseThrow(() -> new ResourceNotFoundException("Organisation", id));
    }

    public OrganisationDto create(CreateOrganisationRequest request) {
        String code = request.code() != null && !request.code().isBlank()
            ? request.code() : generateContractorCode();
        if (organisationRepository.existsByCode(code)) {
            throw new BusinessRuleException("DUPLICATE_CODE",
                "Organisation with code '" + code + "' already exists");
        }
        if (request.pan() != null && !request.pan().isBlank()
            && organisationRepository.existsByPan(request.pan())) {
            throw new BusinessRuleException("DUPLICATE_PAN",
                "An organisation with PAN '" + request.pan() + "' is already registered");
        }

        Organisation org = Organisation.builder()
            .code(code)
            .name(request.name())
            .shortName(request.shortName())
            .organisationType(request.organisationType())
            .parentOrganisationId(request.parentOrganisationId())
            .active(request.active() != null ? request.active() : true)
            .contactPersonName(request.contactPersonName())
            .contactMobile(request.contactMobile())
            .contactEmail(request.contactEmail())
            .pan(request.pan())
            .gstin(request.gstin())
            .registrationNumber(request.registrationNumber())
            .addressLine(request.addressLine())
            .city(request.city())
            .state(request.state())
            .pincode(request.pincode())
            .registrationStatus(resolveStatus(request.registrationStatus(), true))
            .build();
        syncActiveFromStatus(org);

        Organisation saved = organisationRepository.save(org);
        replaceProjectLinks(saved.getId(), request.associatedProjectIds());
        auditService.logCreate("Organisation", saved.getId(), toDto(saved));
        log.info("Organisation created: id={}, code={}", saved.getId(), saved.getCode());
        return toDto(saved);
    }

    public OrganisationDto update(UUID id, CreateOrganisationRequest request) {
        Organisation org = organisationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Organisation", id));

        if (request.name() != null) org.setName(request.name());
        if (request.shortName() != null) org.setShortName(request.shortName());
        if (request.organisationType() != null) org.setOrganisationType(request.organisationType());
        if (request.parentOrganisationId() != null) org.setParentOrganisationId(request.parentOrganisationId());
        if (request.contactPersonName() != null) org.setContactPersonName(request.contactPersonName());
        if (request.contactMobile() != null) org.setContactMobile(request.contactMobile());
        if (request.contactEmail() != null) org.setContactEmail(request.contactEmail());
        if (request.pan() != null) {
            if (!request.pan().equals(org.getPan())
                && organisationRepository.existsByPan(request.pan())) {
                throw new BusinessRuleException("DUPLICATE_PAN",
                    "An organisation with PAN '" + request.pan() + "' is already registered");
            }
            org.setPan(request.pan());
        }
        if (request.gstin() != null) org.setGstin(request.gstin());
        if (request.registrationNumber() != null) org.setRegistrationNumber(request.registrationNumber());
        if (request.addressLine() != null) org.setAddressLine(request.addressLine());
        if (request.city() != null) org.setCity(request.city());
        if (request.state() != null) org.setState(request.state());
        if (request.pincode() != null) org.setPincode(request.pincode());
        if (request.registrationStatus() != null) org.setRegistrationStatus(request.registrationStatus());
        if (request.active() != null) org.setActive(request.active());
        syncActiveFromStatus(org);

        Organisation updated = organisationRepository.save(org);
        if (request.associatedProjectIds() != null) {
            replaceProjectLinks(updated.getId(), request.associatedProjectIds());
        }
        auditService.logUpdate("Organisation", id, "organisation", null, toDto(updated));
        return toDto(updated);
    }

    public void delete(UUID id) {
        Organisation org = organisationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Organisation", id));
        linkRepository.deleteByOrganisationId(id);
        organisationRepository.delete(org);
        auditService.logDelete("Organisation", id);
    }

    public OrganisationDto assignToProjects(UUID organisationId, List<UUID> projectIds) {
        Organisation org = organisationRepository.findById(organisationId)
            .orElseThrow(() -> new ResourceNotFoundException("Organisation", organisationId));
        replaceProjectLinks(organisationId, projectIds);
        return toDto(org);
    }

    private void replaceProjectLinks(UUID organisationId, List<UUID> projectIds) {
        if (projectIds == null) return;
        linkRepository.deleteByOrganisationId(organisationId);
        linkRepository.flush();
        for (UUID projectId : projectIds) {
            OrganisationProjectLink link = new OrganisationProjectLink();
            link.setOrganisationId(organisationId);
            link.setProjectId(projectId);
            linkRepository.save(link);
        }
    }

    private OrganisationRegistrationStatus resolveStatus(
            OrganisationRegistrationStatus requested, boolean isCreate) {
        if (requested != null) return requested;
        return isCreate ? OrganisationRegistrationStatus.ACTIVE : null;
    }

    private void syncActiveFromStatus(Organisation org) {
        if (org.getRegistrationStatus() == null) return;
        switch (org.getRegistrationStatus()) {
            case ACTIVE, PENDING_KYC -> org.setActive(true);
            case SUSPENDED, CLOSED -> org.setActive(false);
        }
    }

    private String generateContractorCode() {
        Integer max = organisationRepository.findMaxContractorSuffix();
        int next = max == null ? 1 : max + 1;
        return String.format("CONT-%03d", next);
    }

    private OrganisationDto toDto(Organisation o) {
        List<UUID> projectIds = new ArrayList<>();
        if (o.getId() != null) {
            linkRepository.findByOrganisationId(o.getId())
                .forEach(l -> projectIds.add(l.getProjectId()));
        }
        return OrganisationDto.builder()
                .id(o.getId())
                .code(o.getCode())
                .name(o.getName())
                .shortName(o.getShortName())
                .organisationType(o.getOrganisationType())
                .parentOrganisationId(o.getParentOrganisationId())
                .active(o.isActive())
                .contactPersonName(o.getContactPersonName())
                .contactMobile(o.getContactMobile())
                .contactEmail(o.getContactEmail())
                .pan(o.getPan())
                .gstin(o.getGstin())
                .registrationNumber(o.getRegistrationNumber())
                .addressLine(o.getAddressLine())
                .city(o.getCity())
                .state(o.getState())
                .pincode(o.getPincode())
                .registrationStatus(o.getRegistrationStatus())
                .associatedProjectIds(projectIds)
                .build();
    }
}
