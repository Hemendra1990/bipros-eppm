package com.bipros.document.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.document.application.dto.DrawingRegisterRequest;
import com.bipros.document.application.dto.DrawingRegisterResponse;
import com.bipros.document.domain.model.DrawingRegister;
import com.bipros.document.domain.repository.DrawingRegisterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DrawingRegisterService {

    private final DrawingRegisterRepository drawingRepository;

    public DrawingRegisterResponse createDrawing(UUID projectId, DrawingRegisterRequest request) {
        DrawingRegister drawing = new DrawingRegister();
        drawing.setProjectId(projectId);
        drawing.setDrawingNumber(request.drawingNumber());
        drawing.setTitle(request.title());
        drawing.setDiscipline(request.discipline());
        drawing.setRevision(request.revision());
        drawing.setRevisionDate(request.revisionDate());
        drawing.setStatus(request.status() != null ? request.status() : drawing.getStatus());
        drawing.setPackageCode(request.packageCode());
        drawing.setScale(request.scale());
        drawing.setDocumentId(request.documentId());

        DrawingRegister saved = drawingRepository.save(drawing);
        return DrawingRegisterResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public DrawingRegisterResponse getDrawing(UUID projectId, UUID drawingId) {
        DrawingRegister drawing = drawingRepository.findByProjectIdAndId(projectId, drawingId)
            .orElseThrow(() -> new ResourceNotFoundException("DrawingRegister", drawingId));
        return DrawingRegisterResponse.from(drawing);
    }

    @Transactional(readOnly = true)
    public List<DrawingRegisterResponse> listDrawings(UUID projectId) {
        return drawingRepository.findByProjectId(projectId)
            .stream()
            .map(DrawingRegisterResponse::from)
            .toList();
    }

    public DrawingRegisterResponse updateDrawing(UUID projectId, UUID drawingId, DrawingRegisterRequest request) {
        DrawingRegister drawing = drawingRepository.findByProjectIdAndId(projectId, drawingId)
            .orElseThrow(() -> new ResourceNotFoundException("DrawingRegister", drawingId));

        drawing.setTitle(request.title());
        drawing.setDiscipline(request.discipline());
        drawing.setRevision(request.revision());
        drawing.setRevisionDate(request.revisionDate());
        drawing.setStatus(request.status() != null ? request.status() : drawing.getStatus());
        drawing.setPackageCode(request.packageCode());
        drawing.setScale(request.scale());
        drawing.setDocumentId(request.documentId());

        DrawingRegister updated = drawingRepository.save(drawing);
        return DrawingRegisterResponse.from(updated);
    }

    public void deleteDrawing(UUID projectId, UUID drawingId) {
        DrawingRegister drawing = drawingRepository.findByProjectIdAndId(projectId, drawingId)
            .orElseThrow(() -> new ResourceNotFoundException("DrawingRegister", drawingId));
        drawingRepository.delete(drawing);
    }
}
