package com.bipros.project.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.project.application.dto.CreateCorridorCodeRequest;
import com.bipros.project.application.dto.CorridorCodeResponse;
import com.bipros.project.domain.model.CorridorCode;
import com.bipros.project.domain.repository.CorridorCodeRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class CorridorCodeService {

    private final CorridorCodeRepository corridorCodeRepository;
    private final ProjectRepository projectRepository;

    public CorridorCodeResponse generateCode(CreateCorridorCodeRequest request) {
        log.info("Generating corridor code for project: {}", request.projectId());

        if (!projectRepository.existsById(request.projectId())) {
            throw new ResourceNotFoundException("Project", request.projectId());
        }

        // Check if corridor code already exists for this project
        if (corridorCodeRepository.findByProjectId(request.projectId()).isPresent()) {
            throw new BusinessRuleException("CORRIDOR_CODE_EXISTS", "Corridor code already exists for this project");
        }

        // Generate code: PREFIX-ZONE-NODE-TIMESTAMP
        String generatedCode = generateUniqueCode(
            request.corridorPrefix(),
            request.zoneCode(),
            request.nodeCode()
        );

        CorridorCode corridorCode = new CorridorCode();
        corridorCode.setProjectId(request.projectId());
        corridorCode.setCorridorPrefix(request.corridorPrefix());
        corridorCode.setZoneCode(request.zoneCode());
        corridorCode.setNodeCode(request.nodeCode());
        corridorCode.setGeneratedCode(generatedCode);

        CorridorCode saved = corridorCodeRepository.save(corridorCode);
        log.info("Corridor code generated: {} for project: {}", generatedCode, request.projectId());

        return CorridorCodeResponse.from(saved);
    }

    public CorridorCodeResponse getByProject(UUID projectId) {
        log.info("Fetching corridor code for project: {}", projectId);

        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project", projectId);
        }

        CorridorCode corridorCode = corridorCodeRepository.findByProjectId(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("CorridorCode", projectId));

        return CorridorCodeResponse.from(corridorCode);
    }

    private String generateUniqueCode(String prefix, String zone, String node) {
        // Normalize inputs
        String prefixPart = prefix.toUpperCase();
        String zonePart = zone.toUpperCase().replace(" ", "");
        String nodePart = node.toUpperCase().replace(" ", "");

        // Build base code: PREFIX-ZONE-NODE
        String baseCode = String.format("%s-%s-%s", prefixPart, zonePart, nodePart);

        // Add package sequence if needed (e.g., PKG001, PKG002...)
        int packageNumber = 1;
        String candidateCode = String.format("%s-PKG%03d", baseCode, packageNumber);

        while (corridorCodeRepository.findByGeneratedCode(candidateCode).isPresent()) {
            packageNumber++;
            candidateCode = String.format("%s-PKG%03d", baseCode, packageNumber);
        }

        return candidateCode;
    }
}
