package com.bipros.gis.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.gis.application.dto.GisLayerRequest;
import com.bipros.gis.application.dto.GisLayerResponse;
import com.bipros.gis.domain.model.GisLayer;
import com.bipros.gis.domain.repository.GisLayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GisLayerService {

    private final GisLayerRepository layerRepository;

    public GisLayerResponse create(UUID projectId, GisLayerRequest request) {
        GisLayer layer = new GisLayer();
        layer.setProjectId(projectId);
        layer.setLayerName(request.layerName());
        layer.setLayerType(request.layerType());
        layer.setDescription(request.description());
        layer.setIsVisible(request.isVisible() != null ? request.isVisible() : true);
        layer.setOpacity(request.opacity() != null ? request.opacity() : 1.0);
        layer.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);

        GisLayer saved = layerRepository.save(layer);
        return GisLayerResponse.from(saved);
    }

    public GisLayerResponse getById(UUID projectId, UUID layerId) {
        GisLayer layer = layerRepository.findById(layerId)
            .filter(l -> l.getProjectId().equals(projectId))
            .orElseThrow(() -> new ResourceNotFoundException("GisLayer", layerId.toString()));
        return GisLayerResponse.from(layer);
    }

    public List<GisLayerResponse> getByProject(UUID projectId) {
        return layerRepository.findByProjectIdOrderBySortOrder(projectId)
            .stream()
            .map(GisLayerResponse::from)
            .toList();
    }

    public GisLayerResponse update(UUID projectId, UUID layerId, GisLayerRequest request) {
        GisLayer layer = layerRepository.findById(layerId)
            .filter(l -> l.getProjectId().equals(projectId))
            .orElseThrow(() -> new ResourceNotFoundException("GisLayer", layerId.toString()));

        layer.setLayerName(request.layerName());
        layer.setLayerType(request.layerType());
        layer.setDescription(request.description());
        if (request.isVisible() != null) layer.setIsVisible(request.isVisible());
        if (request.opacity() != null) layer.setOpacity(request.opacity());
        if (request.sortOrder() != null) layer.setSortOrder(request.sortOrder());

        GisLayer updated = layerRepository.save(layer);
        return GisLayerResponse.from(updated);
    }

    public void delete(UUID projectId, UUID layerId) {
        GisLayer layer = layerRepository.findById(layerId)
            .filter(l -> l.getProjectId().equals(projectId))
            .orElseThrow(() -> new ResourceNotFoundException("GisLayer", layerId.toString()));
        layerRepository.delete(layer);
    }
}
