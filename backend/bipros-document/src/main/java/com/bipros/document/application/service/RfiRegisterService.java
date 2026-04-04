package com.bipros.document.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.document.application.dto.RfiRegisterRequest;
import com.bipros.document.application.dto.RfiRegisterResponse;
import com.bipros.document.domain.model.RfiRegister;
import com.bipros.document.domain.repository.RfiRegisterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class RfiRegisterService {

    private final RfiRegisterRepository rfiRepository;

    public RfiRegisterResponse createRfi(UUID projectId, RfiRegisterRequest request) {
        RfiRegister rfi = new RfiRegister();
        rfi.setProjectId(projectId);
        rfi.setRfiNumber(request.rfiNumber());
        rfi.setSubject(request.subject());
        rfi.setDescription(request.description());
        rfi.setRaisedBy(request.raisedBy());
        rfi.setAssignedTo(request.assignedTo());
        rfi.setRaisedDate(request.raisedDate());
        rfi.setDueDate(request.dueDate());
        rfi.setClosedDate(request.closedDate());
        rfi.setStatus(request.status() != null ? request.status() : rfi.getStatus());
        rfi.setPriority(request.priority() != null ? request.priority() : rfi.getPriority());
        rfi.setResponse(request.response());

        RfiRegister saved = rfiRepository.save(rfi);
        return RfiRegisterResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public RfiRegisterResponse getRfi(UUID projectId, UUID rfiId) {
        RfiRegister rfi = rfiRepository.findByProjectIdAndId(projectId, rfiId)
            .orElseThrow(() -> new ResourceNotFoundException("RfiRegister", rfiId));
        return RfiRegisterResponse.from(rfi);
    }

    @Transactional(readOnly = true)
    public List<RfiRegisterResponse> listRfis(UUID projectId) {
        return rfiRepository.findByProjectId(projectId)
            .stream()
            .map(RfiRegisterResponse::from)
            .toList();
    }

    public RfiRegisterResponse updateRfi(UUID projectId, UUID rfiId, RfiRegisterRequest request) {
        RfiRegister rfi = rfiRepository.findByProjectIdAndId(projectId, rfiId)
            .orElseThrow(() -> new ResourceNotFoundException("RfiRegister", rfiId));

        rfi.setSubject(request.subject());
        rfi.setDescription(request.description());
        rfi.setAssignedTo(request.assignedTo());
        rfi.setDueDate(request.dueDate());
        rfi.setClosedDate(request.closedDate());
        rfi.setStatus(request.status() != null ? request.status() : rfi.getStatus());
        rfi.setPriority(request.priority() != null ? request.priority() : rfi.getPriority());
        rfi.setResponse(request.response());

        RfiRegister updated = rfiRepository.save(rfi);
        return RfiRegisterResponse.from(updated);
    }

    public void deleteRfi(UUID projectId, UUID rfiId) {
        RfiRegister rfi = rfiRepository.findByProjectIdAndId(projectId, rfiId)
            .orElseThrow(() -> new ResourceNotFoundException("RfiRegister", rfiId));
        rfiRepository.delete(rfi);
    }
}
