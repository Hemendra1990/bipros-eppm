package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.EquipmentRateMasterRequest;
import com.bipros.resource.application.dto.EquipmentRateMasterResponse;
import com.bipros.resource.domain.model.rate.EquipmentRateMaster;
import com.bipros.resource.domain.repository.EquipmentRateMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class EquipmentRateMasterService {

  private final EquipmentRateMasterRepository repository;
  private final RateMasterSyncService rateMasterSyncService;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public List<EquipmentRateMasterResponse> list() {
    return repository.findAll().stream()
        .sorted(Comparator
            .comparing(EquipmentRateMaster::getEquipmentName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(EquipmentRateMaster::getMake,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(EquipmentRateMaster::getModel,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
        .map(EquipmentRateMasterResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public EquipmentRateMasterResponse get(UUID id) {
    return EquipmentRateMasterResponse.from(require(id));
  }

  public EquipmentRateMasterResponse create(EquipmentRateMasterRequest req) {
    String name = req.equipmentName().trim();
    String make = req.make().trim();
    String model = req.model().trim();

    repository.findByEquipmentNameAndMakeAndModel(name, make, model)
        .ifPresent(existing -> {
          throw new BusinessRuleException("DUPLICATE_EQUIPMENT_RATE_KEY",
              "An equipment rate already exists for '" + name + " / " + make + " / " + model + "'");
        });

    EquipmentRateMaster entity = EquipmentRateMaster.builder()
        .equipmentName(name)
        .make(make)
        .model(model)
        .unit(req.unit().trim())
        .rate(req.rate())
        .active(req.active() == null ? Boolean.TRUE : req.active())
        .build();
    EquipmentRateMaster saved = repository.save(entity);
    auditService.logCreate("EquipmentRateMaster", saved.getId(),
        EquipmentRateMasterResponse.from(saved));
    return EquipmentRateMasterResponse.from(saved);
  }

  public EquipmentRateMasterResponse update(UUID id, EquipmentRateMasterRequest req) {
    EquipmentRateMaster e = require(id);

    String name = req.equipmentName().trim();
    String make = req.make().trim();
    String model = req.model().trim();

    boolean keyChanged = !e.getEquipmentName().equals(name)
        || !e.getMake().equals(make)
        || !e.getModel().equals(model);

    if (keyChanged) {
      repository.findByEquipmentNameAndMakeAndModel(name, make, model)
          .filter(other -> !other.getId().equals(id))
          .ifPresent(other -> {
            throw new BusinessRuleException("DUPLICATE_EQUIPMENT_RATE_KEY",
                "Another equipment rate already uses '" + name + " / " + make + " / " + model + "'");
          });
      e.setEquipmentName(name);
      e.setMake(make);
      e.setModel(model);
    }

    e.setUnit(req.unit().trim());
    e.setRate(req.rate());
    if (req.active() != null) e.setActive(req.active());

    EquipmentRateMaster saved = repository.save(e);
    rateMasterSyncService.syncResourcesForRateMaster(saved.getId(), saved.getUnit(), saved.getRate());
    auditService.logUpdate("EquipmentRateMaster", id, "equipmentRate", null,
        EquipmentRateMasterResponse.from(saved));
    return EquipmentRateMasterResponse.from(saved);
  }

  public void delete(UUID id) {
    EquipmentRateMaster e = require(id);
    repository.delete(e);
    auditService.logDelete("EquipmentRateMaster", id);
  }

  private EquipmentRateMaster require(UUID id) {
    return repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("EquipmentRateMaster", id));
  }
}
