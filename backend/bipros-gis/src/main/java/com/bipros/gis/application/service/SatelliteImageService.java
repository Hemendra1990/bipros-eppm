package com.bipros.gis.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.gis.application.dto.SatelliteImageRequest;
import com.bipros.gis.application.dto.SatelliteImageResponse;
import com.bipros.gis.domain.model.SatelliteImage;
import com.bipros.gis.domain.repository.SatelliteImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SatelliteImageService {

    private final SatelliteImageRepository imageRepository;

    public SatelliteImageResponse create(UUID projectId, SatelliteImageRequest request) {
        SatelliteImage image = new SatelliteImage();
        image.setProjectId(projectId);
        image.setLayerId(request.layerId());
        image.setImageName(request.imageName());
        image.setDescription(request.description());
        image.setCaptureDate(request.captureDate());
        image.setSource(request.source());
        image.setResolution(request.resolution());
        image.setBoundingBoxGeoJson(request.boundingBoxGeoJson());
        image.setFilePath(request.filePath());
        image.setFileSize(request.fileSize());
        image.setMimeType(request.mimeType());
        image.setNorthBound(request.northBound());
        image.setSouthBound(request.southBound());
        image.setEastBound(request.eastBound());
        image.setWestBound(request.westBound());

        SatelliteImage saved = imageRepository.save(image);
        return SatelliteImageResponse.from(saved);
    }

    public SatelliteImageResponse getById(UUID projectId, UUID imageId) {
        SatelliteImage image = imageRepository.findById(imageId)
            .filter(i -> i.getProjectId().equals(projectId))
            .orElseThrow(() -> new ResourceNotFoundException("SatelliteImage", imageId.toString()));
        return SatelliteImageResponse.from(image);
    }

    public List<SatelliteImageResponse> getByProject(UUID projectId) {
        return imageRepository.findByProjectIdOrderByCaptureDate(projectId)
            .stream()
            .map(SatelliteImageResponse::from)
            .toList();
    }

    public List<SatelliteImageResponse> getByProjectAndDateRange(UUID projectId, LocalDate fromDate, LocalDate toDate) {
        return imageRepository.findByProjectIdAndCaptureDateBetween(projectId, fromDate, toDate)
            .stream()
            .map(SatelliteImageResponse::from)
            .toList();
    }

    public SatelliteImageResponse update(UUID projectId, UUID imageId, SatelliteImageRequest request) {
        SatelliteImage image = imageRepository.findById(imageId)
            .filter(i -> i.getProjectId().equals(projectId))
            .orElseThrow(() -> new ResourceNotFoundException("SatelliteImage", imageId.toString()));

        image.setImageName(request.imageName());
        image.setDescription(request.description());
        image.setCaptureDate(request.captureDate());
        image.setSource(request.source());
        if (request.resolution() != null) image.setResolution(request.resolution());
        if (request.boundingBoxGeoJson() != null) image.setBoundingBoxGeoJson(request.boundingBoxGeoJson());
        if (request.northBound() != null) image.setNorthBound(request.northBound());
        if (request.southBound() != null) image.setSouthBound(request.southBound());
        if (request.eastBound() != null) image.setEastBound(request.eastBound());
        if (request.westBound() != null) image.setWestBound(request.westBound());

        SatelliteImage updated = imageRepository.save(image);
        return SatelliteImageResponse.from(updated);
    }

    public void delete(UUID projectId, UUID imageId) {
        SatelliteImage image = imageRepository.findById(imageId)
            .filter(i -> i.getProjectId().equals(projectId))
            .orElseThrow(() -> new ResourceNotFoundException("SatelliteImage", imageId.toString()));
        imageRepository.delete(image);
    }
}
