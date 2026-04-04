package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.resource.application.dto.CreateResourceCurveRequest;
import com.bipros.resource.application.dto.ResourceCurveResponse;
import com.bipros.resource.domain.model.ResourceCurve;
import com.bipros.resource.domain.repository.ResourceCurveRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ResourceCurveService {

  private final ResourceCurveRepository curveRepository;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public ResourceCurveResponse createCurve(CreateResourceCurveRequest request) {
    log.info("Creating resource curve: name={}", request.name());

    if (curveRepository.findByName(request.name()).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_CURVE_NAME", "Resource curve with name " + request.name() + " already exists");
    }

    if (request.curveData().size() != 21) {
      throw new BusinessRuleException("INVALID_CURVE_DATA", "Resource curve must have exactly 21 data points");
    }

    try {
      String curveDataJson = MAPPER.writeValueAsString(request.curveData());
      ResourceCurve curve = ResourceCurve.builder()
          .name(request.name())
          .description(request.description())
          .isDefault(false)
          .curveData(curveDataJson)
          .build();

      ResourceCurve saved = curveRepository.save(curve);
      log.info("Resource curve created: id={}", saved.getId());
      return ResourceCurveResponse.from(saved);
    } catch (JsonProcessingException e) {
      throw new BusinessRuleException("INVALID_CURVE_FORMAT", "Invalid curve data format: " + e.getMessage());
    }
  }

  public ResourceCurveResponse getCurve(UUID id) throws JsonProcessingException {
    log.info("Fetching resource curve: id={}", id);
    ResourceCurve curve = curveRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ResourceCurve", id));
    return ResourceCurveResponse.from(curve);
  }

  public List<ResourceCurveResponse> listCurves() throws JsonProcessingException {
    log.info("Listing all resource curves");
    return curveRepository.findAll().stream()
        .map(c -> {
          try {
            return ResourceCurveResponse.from(c);
          } catch (JsonProcessingException e) {
            log.error("Error parsing curve data for id: {}", c.getId(), e);
            return null;
          }
        })
        .toList();
  }

  public List<ResourceCurveResponse> listDefaultCurves() throws JsonProcessingException {
    log.info("Listing default resource curves");
    return curveRepository.findByIsDefaultTrue().stream()
        .map(c -> {
          try {
            return ResourceCurveResponse.from(c);
          } catch (JsonProcessingException e) {
            log.error("Error parsing curve data for id: {}", c.getId(), e);
            return null;
          }
        })
        .toList();
  }

  public ResourceCurveResponse updateCurve(UUID id, CreateResourceCurveRequest request) throws JsonProcessingException {
    log.info("Updating resource curve: id={}", id);
    ResourceCurve curve = curveRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ResourceCurve", id));

    if (!curve.getName().equals(request.name()) &&
        curveRepository.findByName(request.name()).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_CURVE_NAME", "Resource curve with name " + request.name() + " already exists");
    }

    if (request.curveData().size() != 21) {
      throw new BusinessRuleException("INVALID_CURVE_DATA", "Resource curve must have exactly 21 data points");
    }

    try {
      curve.setName(request.name());
      curve.setDescription(request.description());
      curve.setCurveData(MAPPER.writeValueAsString(request.curveData()));

      ResourceCurve updated = curveRepository.save(curve);
      log.info("Resource curve updated: id={}", id);
      return ResourceCurveResponse.from(updated);
    } catch (JsonProcessingException e) {
      throw new BusinessRuleException("INVALID_CURVE_FORMAT", "Invalid curve data format: " + e.getMessage());
    }
  }

  public void deleteCurve(UUID id) {
    log.info("Deleting resource curve: id={}", id);
    if (!curveRepository.existsById(id)) {
      throw new ResourceNotFoundException("ResourceCurve", id);
    }
    curveRepository.deleteById(id);
    log.info("Resource curve deleted: id={}", id);
  }

  public void seedDefaultCurves() {
    log.info("Seeding default resource curves");

    seedCurveIfNotExists("Bell", "Bell-shaped resource distribution", bellCurveData());
    seedCurveIfNotExists("Front Loaded", "Front-loaded resource distribution", frontLoadedCurveData());
    seedCurveIfNotExists("Back Loaded", "Back-loaded resource distribution", backLoadedCurveData());
    seedCurveIfNotExists("Triangular", "Triangular resource distribution", triangularCurveData());

    log.info("Default resource curves seeded");
  }

  private void seedCurveIfNotExists(String name, String description, List<Double> data) {
    if (curveRepository.findByName(name).isEmpty()) {
      try {
        ResourceCurve curve = ResourceCurve.builder()
            .name(name)
            .description(description)
            .isDefault(true)
            .curveData(MAPPER.writeValueAsString(data))
            .build();
        curveRepository.save(curve);
        log.info("Seeded curve: {}", name);
      } catch (JsonProcessingException e) {
        log.error("Error seeding curve: {}", name, e);
      }
    }
  }

  private List<Double> bellCurveData() {
    return Arrays.asList(0.5, 1.0, 2.0, 4.0, 6.5, 9.0, 11.5, 13.0, 14.0, 14.5, 15.0, 14.5, 14.0, 13.0, 11.5, 9.0, 6.5, 4.0, 2.0, 1.0, 0.5);
  }

  private List<Double> frontLoadedCurveData() {
    return Arrays.asList(20.0, 18.0, 16.0, 14.0, 12.0, 10.0, 8.0, 7.0, 6.0, 5.0, 4.5, 4.0, 3.5, 3.0, 2.5, 2.0, 1.5, 1.0, 0.5, 0.3, 0.2);
  }

  private List<Double> backLoadedCurveData() {
    return Arrays.asList(0.2, 0.3, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 6.0, 7.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0);
  }

  private List<Double> triangularCurveData() {
    return Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 15.0, 10.0, 9.0, 8.0, 7.0, 6.0, 5.0, 4.0, 3.0, 2.0, 1.0);
  }
}
