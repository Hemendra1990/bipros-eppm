package com.bipros.gis.application.dto;

import com.bipros.gis.domain.model.SatelliteImage;
import com.bipros.gis.domain.model.SatelliteImageSource;
import com.bipros.gis.domain.model.SatelliteImageStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SatelliteImageResponse(
    UUID id,
    UUID projectId,
    UUID layerId,
    String imageName,
    String description,
    LocalDate captureDate,
    SatelliteImageSource source,
    String resolution,
    String boundingBoxGeoJson,
    String filePath,
    Long fileSize,
    String mimeType,
    Double northBound,
    Double southBound,
    Double eastBound,
    Double westBound,
    SatelliteImageStatus status,
    Instant createdAt,
    String createdBy
) {
    public static SatelliteImageResponse from(SatelliteImage entity) {
        return new SatelliteImageResponse(
            entity.getId(),
            entity.getProjectId(),
            entity.getLayerId(),
            entity.getImageName(),
            entity.getDescription(),
            entity.getCaptureDate(),
            entity.getSource(),
            entity.getResolution(),
            entity.getBoundingBoxGeoJson(),
            entity.getFilePath(),
            entity.getFileSize(),
            entity.getMimeType(),
            entity.getNorthBound(),
            entity.getSouthBound(),
            entity.getEastBound(),
            entity.getWestBound(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getCreatedBy()
        );
    }
}
