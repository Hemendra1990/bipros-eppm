package com.bipros.admin.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.admin.application.dto.CreateUnitOfMeasureRequest;
import com.bipros.admin.application.dto.UnitOfMeasureDto;
import com.bipros.admin.domain.model.UnitOfMeasure;
import com.bipros.admin.domain.repository.UnitOfMeasureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UnitOfMeasureService {

    private final UnitOfMeasureRepository unitOfMeasureRepository;

    public UnitOfMeasureDto createUnitOfMeasure(CreateUnitOfMeasureRequest request) {
        if (unitOfMeasureRepository.findByCode(request.getCode()).isPresent()) {
            throw new BusinessRuleException("DUPLICATE_CODE", "Unit of measure with code " + request.getCode() + " already exists");
        }

        UnitOfMeasure unit = new UnitOfMeasure();
        unit.setCode(request.getCode());
        unit.setName(request.getName());
        unit.setAbbreviation(request.getAbbreviation());
        unit.setCategory(request.getCategory());

        UnitOfMeasure saved = unitOfMeasureRepository.save(unit);
        return mapToDto(saved);
    }

    public UnitOfMeasureDto updateUnitOfMeasure(UUID id, CreateUnitOfMeasureRequest request) {
        UnitOfMeasure unit = unitOfMeasureRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("UnitOfMeasure", id));

        if (!unit.getCode().equals(request.getCode()) &&
            unitOfMeasureRepository.findByCode(request.getCode()).isPresent()) {
            throw new BusinessRuleException("DUPLICATE_CODE", "Unit of measure with code " + request.getCode() + " already exists");
        }

        unit.setCode(request.getCode());
        unit.setName(request.getName());
        unit.setAbbreviation(request.getAbbreviation());
        unit.setCategory(request.getCategory());

        UnitOfMeasure updated = unitOfMeasureRepository.save(unit);
        return mapToDto(updated);
    }

    public void deleteUnitOfMeasure(UUID id) {
        UnitOfMeasure unit = unitOfMeasureRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("UnitOfMeasure", id));
        unitOfMeasureRepository.delete(unit);
    }

    @Transactional(readOnly = true)
    public UnitOfMeasureDto getUnitOfMeasure(UUID id) {
        UnitOfMeasure unit = unitOfMeasureRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("UnitOfMeasure", id));
        return mapToDto(unit);
    }

    @Transactional(readOnly = true)
    public List<UnitOfMeasureDto> listUnitsOfMeasure() {
        return unitOfMeasureRepository.findAll().stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    private UnitOfMeasureDto mapToDto(UnitOfMeasure unit) {
        return UnitOfMeasureDto.builder()
            .id(unit.getId())
            .code(unit.getCode())
            .name(unit.getName())
            .abbreviation(unit.getAbbreviation())
            .category(unit.getCategory())
            .build();
    }
}
