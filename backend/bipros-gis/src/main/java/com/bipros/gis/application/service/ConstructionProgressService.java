package com.bipros.gis.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.gis.application.dto.ConstructionProgressRequest;
import com.bipros.gis.application.dto.ConstructionProgressResponse;
import com.bipros.gis.application.dto.ProgressVarianceResponse;
import com.bipros.gis.domain.model.ConstructionProgressSnapshot;
import com.bipros.gis.domain.model.WbsPolygon;
import com.bipros.gis.domain.repository.ConstructionProgressSnapshotRepository;
import com.bipros.gis.domain.repository.WbsPolygonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConstructionProgressService {

    private final ConstructionProgressSnapshotRepository progressRepository;
    private final WbsPolygonRepository polygonRepository;

    public ConstructionProgressResponse create(UUID projectId, ConstructionProgressRequest request) {
        ConstructionProgressSnapshot snapshot = new ConstructionProgressSnapshot();
        snapshot.setProjectId(projectId);
        snapshot.setWbsPolygonId(request.wbsPolygonId());
        snapshot.setCaptureDate(request.captureDate());
        snapshot.setSatelliteImageId(request.satelliteImageId());
        snapshot.setDerivedProgressPercent(request.derivedProgressPercent());
        snapshot.setContractorClaimedPercent(request.contractorClaimedPercent());
        snapshot.setAnalysisMethod(request.analysisMethod());
        snapshot.setRemarks(request.remarks());

        // Calculate variance: derived - claimed
        if (request.derivedProgressPercent() != null && request.contractorClaimedPercent() != null) {
            double variance = request.derivedProgressPercent() - request.contractorClaimedPercent();
            snapshot.setVariancePercent(variance);
        }

        ConstructionProgressSnapshot saved = progressRepository.save(snapshot);
        return ConstructionProgressResponse.from(saved);
    }

    public ConstructionProgressResponse getById(UUID projectId, UUID snapshotId) {
        ConstructionProgressSnapshot snapshot = progressRepository.findById(snapshotId)
            .filter(s -> s.getProjectId().equals(projectId))
            .orElseThrow(() -> new ResourceNotFoundException("ConstructionProgressSnapshot", snapshotId.toString()));
        return ConstructionProgressResponse.from(snapshot);
    }

    public List<ConstructionProgressResponse> getByProject(UUID projectId) {
        return progressRepository.findByProjectId(projectId)
            .stream()
            .map(ConstructionProgressResponse::from)
            .toList();
    }

    public List<ConstructionProgressResponse> getByProjectAndDateRange(UUID projectId, LocalDate fromDate, LocalDate toDate) {
        return progressRepository.findByProjectIdAndCaptureDateBetween(projectId, fromDate, toDate)
            .stream()
            .map(ConstructionProgressResponse::from)
            .toList();
    }

    public List<ConstructionProgressResponse> getByWbsPolygon(UUID projectId, UUID polygonId) {
        // Verify polygon exists and belongs to project
        polygonRepository.findById(polygonId)
            .filter(p -> p.getProjectId().equals(projectId))
            .orElseThrow(() -> new ResourceNotFoundException("WbsPolygon", polygonId.toString()));

        return progressRepository.findByWbsPolygonIdOrderByCaptureDate(polygonId)
            .stream()
            .map(ConstructionProgressResponse::from)
            .toList();
    }

    public ConstructionProgressResponse update(UUID projectId, UUID snapshotId, ConstructionProgressRequest request) {
        ConstructionProgressSnapshot snapshot = progressRepository.findById(snapshotId)
            .filter(s -> s.getProjectId().equals(projectId))
            .orElseThrow(() -> new ResourceNotFoundException("ConstructionProgressSnapshot", snapshotId.toString()));

        snapshot.setCaptureDate(request.captureDate());
        if (request.satelliteImageId() != null) snapshot.setSatelliteImageId(request.satelliteImageId());
        if (request.derivedProgressPercent() != null) snapshot.setDerivedProgressPercent(request.derivedProgressPercent());
        if (request.contractorClaimedPercent() != null) snapshot.setContractorClaimedPercent(request.contractorClaimedPercent());
        snapshot.setAnalysisMethod(request.analysisMethod());
        if (request.remarks() != null) snapshot.setRemarks(request.remarks());

        // Recalculate variance
        if (snapshot.getDerivedProgressPercent() != null && snapshot.getContractorClaimedPercent() != null) {
            double variance = snapshot.getDerivedProgressPercent() - snapshot.getContractorClaimedPercent();
            snapshot.setVariancePercent(variance);
        }

        ConstructionProgressSnapshot updated = progressRepository.save(snapshot);
        return ConstructionProgressResponse.from(updated);
    }

    public void delete(UUID projectId, UUID snapshotId) {
        ConstructionProgressSnapshot snapshot = progressRepository.findById(snapshotId)
            .filter(s -> s.getProjectId().equals(projectId))
            .orElseThrow(() -> new ResourceNotFoundException("ConstructionProgressSnapshot", snapshotId.toString()));
        progressRepository.delete(snapshot);
    }

    public List<ProgressVarianceResponse> getProgressVariance(UUID projectId) {
        List<WbsPolygon> polygons = polygonRepository.findByProjectId(projectId);

        return polygons.stream()
            .map(polygon -> {
                List<ConstructionProgressSnapshot> snapshots = progressRepository.findByWbsPolygonIdOrderByCaptureDate(polygon.getId());

                // Get latest snapshot
                if (snapshots.isEmpty()) {
                    return new ProgressVarianceResponse(
                        polygon.getId(),
                        polygon.getWbsCode(),
                        polygon.getWbsName(),
                        null,
                        null,
                        null,
                        "NO_DATA"
                    );
                }

                ConstructionProgressSnapshot latest = snapshots.get(snapshots.size() - 1);
                String varianceStatus = "ON_TRACK";
                if (latest.getVariancePercent() != null && latest.getVariancePercent() > 10) {
                    varianceStatus = "BEHIND";
                } else if (latest.getVariancePercent() != null && latest.getVariancePercent() < -5) {
                    varianceStatus = "AHEAD";
                }

                return new ProgressVarianceResponse(
                    polygon.getId(),
                    polygon.getWbsCode(),
                    polygon.getWbsName(),
                    latest.getDerivedProgressPercent(),
                    latest.getContractorClaimedPercent(),
                    latest.getVariancePercent(),
                    varianceStatus
                );
            })
            .toList();
    }
}
