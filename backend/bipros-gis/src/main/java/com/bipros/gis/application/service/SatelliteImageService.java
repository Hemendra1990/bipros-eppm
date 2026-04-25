package com.bipros.gis.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.gis.application.dto.SatelliteImageRequest;
import com.bipros.gis.application.dto.SatelliteImageResponse;
import com.bipros.gis.application.dto.UploadSatelliteImageRequest;
import com.bipros.gis.domain.model.SatelliteImage;
import com.bipros.gis.domain.model.SatelliteImageSource;
import com.bipros.gis.domain.model.SatelliteImageStatus;
import com.bipros.gis.domain.model.WbsPolygon;
import com.bipros.gis.domain.repository.SatelliteImageRepository;
import com.bipros.gis.domain.repository.WbsPolygonRepository;
import com.bipros.integration.storage.RasterStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SatelliteImageService {

    private final SatelliteImageRepository imageRepository;
    private final WbsPolygonRepository polygonRepository;
    private final RasterStorage rasterStorage;
    private final GeoTiffProcessor geoTiffProcessor;
    private final ProgressAnalyzerService analyzerService;

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

    @Transactional
    public SatelliteImageResponse uploadManualImage(UUID projectId, UploadSatelliteImageRequest request,
                                                     MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessRuleException("FILE_EMPTY", "Uploaded file is empty");
        }

        String originalFilename = file.getOriginalFilename();
        boolean isGeoTiff = originalFilename != null &&
            (originalFilename.toLowerCase().endsWith(".tif") || originalFilename.toLowerCase().endsWith(".tiff"));

        byte[] imageBytes;
        Double northBound = null;
        Double southBound = null;
        Double eastBound = null;
        Double westBound = null;
        String mimeType;
        String boundingBoxGeoJson = request.boundingBoxGeoJson();

        try {
            if (isGeoTiff) {
                GeoTiffProcessor.ProcessedImage processed = geoTiffProcessor.process(file.getBytes());
                imageBytes = processed.pngBytes();
                southBound = processed.southBound();
                northBound = processed.northBound();
                westBound = processed.westBound();
                eastBound = processed.eastBound();
                mimeType = "image/png";

                boundingBoxGeoJson = String.format(
                    "{\"type\":\"Polygon\",\"coordinates\":[[[%.8f,%.8f],[%.8f,%.8f],[%.8f,%.8f],[%.8f,%.8f],[%.8f,%.8f]]]}",
                    westBound, southBound, eastBound, southBound, eastBound, northBound,
                    westBound, northBound, westBound, southBound
                );
            } else {
                imageBytes = file.getBytes();
                String lowerName = originalFilename != null ? originalFilename.toLowerCase() : "";
                if (lowerName.endsWith(".png")) {
                    mimeType = "image/png";
                } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
                    mimeType = "image/jpeg";
                } else {
                    mimeType = file.getContentType();
                    if (mimeType == null || mimeType.isBlank()) {
                        mimeType = "application/octet-stream";
                    }
                }

                if (boundingBoxGeoJson == null || boundingBoxGeoJson.isBlank()) {
                    throw new BusinessRuleException("BOUNDS_REQUIRED",
                        "Bounding box is required for non-GeoTIFF images");
                }

                GeoJsonReader reader = new GeoJsonReader();
                org.locationtech.jts.geom.Geometry geom = reader.read(boundingBoxGeoJson);
                Envelope env = geom.getEnvelopeInternal();
                westBound = env.getMinX();
                eastBound = env.getMaxX();
                southBound = env.getMinY();
                northBound = env.getMaxY();
            }
        } catch (IOException e) {
            throw new BusinessRuleException("FILE_READ_ERROR",
                "Failed to read uploaded file: " + e.getMessage());
        } catch (org.locationtech.jts.io.ParseException e) {
            throw new BusinessRuleException("INVALID_GEOJSON",
                "Invalid bounding box GeoJSON: " + e.getMessage());
        }

        String sceneId = "MANUAL-" + UUID.randomUUID().toString().substring(0, 8);
        String key = projectId + "/manual/" + sceneId + ".png";
        if (!isGeoTiff) {
            String ext = mimeType.equals("image/jpeg") ? ".jpg" : ".png";
            key = projectId + "/manual/" + sceneId + ext;
        }

        URI storedAt = rasterStorage.put(key, imageBytes, mimeType);

        SatelliteImage image = new SatelliteImage();
        image.setProjectId(projectId);
        image.setSceneId(sceneId);
        image.setImageName(request.imageName());
        image.setDescription(request.description());
        image.setCaptureDate(request.captureDate());
        image.setSource(SatelliteImageSource.MANUAL_UPLOAD);
        image.setResolution(request.resolution());
        image.setBoundingBoxGeoJson(boundingBoxGeoJson);
        image.setFilePath(storedAt.toString());
        image.setFileSize((long) imageBytes.length);
        image.setMimeType(mimeType);
        image.setNorthBound(northBound);
        image.setSouthBound(southBound);
        image.setEastBound(eastBound);
        image.setWestBound(westBound);
        image.setStatus(SatelliteImageStatus.READY);

        SatelliteImage saved = imageRepository.save(image);

        List<WbsPolygon> polygons = polygonRepository.findByProjectId(projectId);
        for (WbsPolygon polygon : polygons) {
            try {
                analyzerService.analyzeAsync(saved, polygon, null);
            } catch (Exception e) {
                log.warn("Failed to dispatch analyzer for polygon {}: {}", polygon.getWbsCode(), e.getMessage());
            }
        }

        return SatelliteImageResponse.from(saved);
    }
}
