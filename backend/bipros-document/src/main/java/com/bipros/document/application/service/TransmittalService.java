package com.bipros.document.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.document.application.dto.TransmittalItemRequest;
import com.bipros.document.application.dto.TransmittalItemResponse;
import com.bipros.document.application.dto.TransmittalRequest;
import com.bipros.document.application.dto.TransmittalResponse;
import com.bipros.document.domain.model.Transmittal;
import com.bipros.document.domain.model.TransmittalItem;
import com.bipros.document.domain.repository.TransmittalItemRepository;
import com.bipros.document.domain.repository.TransmittalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TransmittalService {

    private final TransmittalRepository transmittalRepository;
    private final TransmittalItemRepository itemRepository;
    private final AuditService auditService;

    public TransmittalResponse createTransmittal(UUID projectId, TransmittalRequest request) {
        Transmittal transmittal = new Transmittal();
        transmittal.setProjectId(projectId);
        transmittal.setTransmittalNumber(request.transmittalNumber());
        transmittal.setSubject(request.subject());
        transmittal.setFromParty(request.fromParty());
        transmittal.setToParty(request.toParty());
        transmittal.setSentDate(request.sentDate());
        transmittal.setDueDate(request.dueDate());
        transmittal.setStatus(request.status() != null ? request.status() : transmittal.getStatus());
        transmittal.setRemarks(request.remarks());

        Transmittal saved = transmittalRepository.save(transmittal);
        auditService.logCreate("Transmittal", saved.getId(), saved);
        return TransmittalResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public TransmittalResponse getTransmittal(UUID projectId, UUID transmittalId) {
        Transmittal transmittal = transmittalRepository.findByProjectIdAndId(projectId, transmittalId)
            .orElseThrow(() -> new ResourceNotFoundException("Transmittal", transmittalId));
        return TransmittalResponse.from(transmittal);
    }

    @Transactional(readOnly = true)
    public List<TransmittalResponse> listTransmittals(UUID projectId) {
        return transmittalRepository.findByProjectId(projectId)
            .stream()
            .map(TransmittalResponse::from)
            .toList();
    }

    public TransmittalResponse updateTransmittal(UUID projectId, UUID transmittalId, TransmittalRequest request) {
        Transmittal transmittal = transmittalRepository.findByProjectIdAndId(projectId, transmittalId)
            .orElseThrow(() -> new ResourceNotFoundException("Transmittal", transmittalId));

        auditService.logUpdate("Transmittal", transmittalId, "subject", transmittal.getSubject(), request.subject());
        auditService.logUpdate("Transmittal", transmittalId, "status", transmittal.getStatus(), request.status());

        transmittal.setSubject(request.subject());
        transmittal.setFromParty(request.fromParty());
        transmittal.setToParty(request.toParty());
        transmittal.setSentDate(request.sentDate());
        transmittal.setDueDate(request.dueDate());
        transmittal.setStatus(request.status() != null ? request.status() : transmittal.getStatus());
        transmittal.setRemarks(request.remarks());

        Transmittal updated = transmittalRepository.save(transmittal);
        return TransmittalResponse.from(updated);
    }

    public void deleteTransmittal(UUID projectId, UUID transmittalId) {
        Transmittal transmittal = transmittalRepository.findByProjectIdAndId(projectId, transmittalId)
            .orElseThrow(() -> new ResourceNotFoundException("Transmittal", transmittalId));

        // Delete items
        List<TransmittalItem> items = itemRepository.findByTransmittalId(transmittalId);
        itemRepository.deleteAll(items);

        transmittalRepository.delete(transmittal);
        auditService.logDelete("Transmittal", transmittalId);
    }

    @Transactional(readOnly = true)
    public List<TransmittalItemResponse> getTransmittalItems(UUID projectId, UUID transmittalId) {
        Transmittal transmittal = transmittalRepository.findByProjectIdAndId(projectId, transmittalId)
            .orElseThrow(() -> new ResourceNotFoundException("Transmittal", transmittalId));

        return itemRepository.findByTransmittalId(transmittalId)
            .stream()
            .map(TransmittalItemResponse::from)
            .toList();
    }

    public TransmittalItemResponse addItem(UUID projectId, UUID transmittalId, TransmittalItemRequest request) {
        Transmittal transmittal = transmittalRepository.findByProjectIdAndId(projectId, transmittalId)
            .orElseThrow(() -> new ResourceNotFoundException("Transmittal", transmittalId));

        TransmittalItem item = new TransmittalItem();
        item.setTransmittalId(transmittalId);
        item.setDocumentId(request.documentId());
        item.setPurpose(request.purpose());
        item.setRemarks(request.remarks());

        TransmittalItem saved = itemRepository.save(item);
        auditService.logCreate("TransmittalItem", saved.getId(), saved);
        return TransmittalItemResponse.from(saved);
    }

    public void removeItem(UUID projectId, UUID transmittalId, UUID itemId) {
        Transmittal transmittal = transmittalRepository.findByProjectIdAndId(projectId, transmittalId)
            .orElseThrow(() -> new ResourceNotFoundException("Transmittal", transmittalId));

        TransmittalItem item = itemRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("TransmittalItem", itemId));

        itemRepository.delete(item);
        auditService.logDelete("TransmittalItem", itemId);
    }
}
