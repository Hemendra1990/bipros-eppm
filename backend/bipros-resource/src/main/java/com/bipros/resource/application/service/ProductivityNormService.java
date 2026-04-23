package com.bipros.resource.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.CreateProductivityNormRequest;
import com.bipros.resource.application.dto.ProductivityNormResponse;
import com.bipros.resource.domain.model.ProductivityNorm;
import com.bipros.resource.domain.model.ProductivityNormType;
import com.bipros.resource.domain.repository.ProductivityNormRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ProductivityNormService {

  private final ProductivityNormRepository repository;
  private final AuditService auditService;

  public ProductivityNormResponse create(CreateProductivityNormRequest request) {
    log.info("Creating productivity norm: type={}, activity={}", request.normType(), request.activityName());
    ProductivityNorm norm = toEntity(new ProductivityNorm(), request);
    ProductivityNorm saved = repository.save(norm);
    auditService.logCreate("ProductivityNorm", saved.getId(), ProductivityNormResponse.from(saved));
    return ProductivityNormResponse.from(saved);
  }

  public List<ProductivityNormResponse> createBulk(List<CreateProductivityNormRequest> requests) {
    log.info("Bulk-creating {} productivity norms", requests.size());
    List<ProductivityNorm> entities = requests.stream()
        .map(r -> toEntity(new ProductivityNorm(), r))
        .toList();
    return repository.saveAll(entities).stream()
        .map(ProductivityNormResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public ProductivityNormResponse get(UUID id) {
    return ProductivityNormResponse.from(find(id));
  }

  @Transactional(readOnly = true)
  public List<ProductivityNormResponse> list(ProductivityNormType normType) {
    List<ProductivityNorm> entities = normType == null
        ? repository.findAll()
        : repository.findByNormType(normType);
    return entities.stream().map(ProductivityNormResponse::from).toList();
  }

  public ProductivityNormResponse update(UUID id, CreateProductivityNormRequest request) {
    ProductivityNorm norm = find(id);
    toEntity(norm, request);
    ProductivityNorm updated = repository.save(norm);
    auditService.logUpdate("ProductivityNorm", id, "norm", norm, ProductivityNormResponse.from(updated));
    return ProductivityNormResponse.from(updated);
  }

  public void delete(UUID id) {
    if (!repository.existsById(id)) {
      throw new ResourceNotFoundException("ProductivityNorm", id);
    }
    repository.deleteById(id);
    auditService.logDelete("ProductivityNorm", id);
  }

  private ProductivityNorm find(UUID id) {
    return repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ProductivityNorm", id));
  }

  private ProductivityNorm toEntity(ProductivityNorm norm, CreateProductivityNormRequest r) {
    norm.setNormType(r.normType());
    norm.setActivityName(r.activityName());
    norm.setUnit(r.unit());
    norm.setOutputPerManPerDay(r.outputPerManPerDay());
    norm.setOutputPerHour(r.outputPerHour());
    norm.setCrewSize(r.crewSize());
    norm.setWorkingHoursPerDay(r.workingHoursPerDay());
    norm.setFuelLitresPerHour(r.fuelLitresPerHour());
    norm.setEquipmentSpec(r.equipmentSpec());
    norm.setRemarks(r.remarks());

    // Derive outputPerDay when omitted: manpower = perMan×crew; equipment = perHour×workingHrs.
    BigDecimal derived = r.outputPerDay();
    if (derived == null) {
      if (r.normType() == ProductivityNormType.MANPOWER
          && r.outputPerManPerDay() != null && r.crewSize() != null) {
        derived = r.outputPerManPerDay().multiply(BigDecimal.valueOf(r.crewSize()));
      } else if (r.normType() == ProductivityNormType.EQUIPMENT
          && r.outputPerHour() != null && r.workingHoursPerDay() != null) {
        derived = r.outputPerHour().multiply(BigDecimal.valueOf(r.workingHoursPerDay()));
      }
    }
    norm.setOutputPerDay(derived);
    return norm;
  }
}
