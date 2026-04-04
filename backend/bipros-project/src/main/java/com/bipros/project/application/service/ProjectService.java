package com.bipros.project.application.service;

import com.bipros.common.dto.PagedResponse;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.project.application.dto.CreateProjectRequest;
import com.bipros.project.application.dto.ProjectResponse;
import com.bipros.project.application.dto.UpdateProjectRequest;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.EpsNodeRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final EpsNodeRepository epsNodeRepository;
    private final WbsNodeRepository wbsNodeRepository;

    public ProjectResponse createProject(CreateProjectRequest request) {
        log.info("Creating project with code: {}", request.code());

        if (projectRepository.existsByCode(request.code())) {
            throw new BusinessRuleException("PROJECT_CODE_DUPLICATE", "Project with code '" + request.code() + "' already exists");
        }

        if (!epsNodeRepository.existsById(request.epsNodeId())) {
            throw new ResourceNotFoundException("EpsNode", request.epsNodeId());
        }

        Project project = new Project();
        project.setCode(request.code());
        project.setName(request.name());
        project.setDescription(request.description());
        project.setEpsNodeId(request.epsNodeId());
        project.setObsNodeId(request.obsNodeId());
        project.setPlannedStartDate(request.plannedStartDate());
        project.setPlannedFinishDate(request.plannedFinishDate());
        if (request.priority() != null) {
            project.setPriority(request.priority());
        }

        Project saved = projectRepository.save(project);
        log.info("Project created with ID: {}", saved.getId());

        // Auto-create root WBS node
        createRootWbsNode(saved);

        return buildProjectResponse(saved);
    }

    public ProjectResponse updateProject(UUID id, UpdateProjectRequest request) {
        log.info("Updating project: {}", id);

        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Project", id));

        if (request.name() != null) {
            project.setName(request.name());
        }
        if (request.description() != null) {
            project.setDescription(request.description());
        }
        if (request.obsNodeId() != null) {
            project.setObsNodeId(request.obsNodeId());
        }
        if (request.plannedStartDate() != null) {
            project.setPlannedStartDate(request.plannedStartDate());
        }
        if (request.plannedFinishDate() != null) {
            project.setPlannedFinishDate(request.plannedFinishDate());
        }
        if (request.mustFinishByDate() != null) {
            project.setMustFinishByDate(request.mustFinishByDate());
        }
        if (request.status() != null) {
            project.setStatus(request.status());
        }
        if (request.priority() != null) {
            project.setPriority(request.priority());
        }
        if (request.dataDate() != null) {
            project.setDataDate(request.dataDate());
        }

        Project updated = projectRepository.save(project);
        log.info("Project updated: {}", id);

        return buildProjectResponse(updated);
    }

    public void deleteProject(UUID id) {
        log.info("Deleting project: {}", id);

        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Project", id));

        // Check for activities (placeholder)
        // TODO: Check activities when activities module is ready

        // Delete associated WBS nodes
        List<WbsNode> wbsNodes = wbsNodeRepository.findByProjectIdOrderBySortOrder(id);
        wbsNodeRepository.deleteAll(wbsNodes);

        projectRepository.delete(project);
        log.info("Project deleted: {}", id);
    }

    public ProjectResponse getProject(UUID id) {
        log.info("Fetching project: {}", id);

        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Project", id));

        return buildProjectResponse(project);
    }

    public PagedResponse<ProjectResponse> listProjects(Pageable pageable) {
        log.info("Fetching projects page: {}", pageable);

        Page<Project> page = projectRepository.findAll(pageable);

        List<ProjectResponse> content = page.getContent().stream()
            .map(this::buildProjectResponse)
            .collect(Collectors.toList());

        return PagedResponse.of(
            content,
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber(),
            page.getSize()
        );
    }

    public List<ProjectResponse> getProjectsByEps(UUID epsNodeId) {
        log.info("Fetching projects by EPS node: {}", epsNodeId);

        if (!epsNodeRepository.existsById(epsNodeId)) {
            throw new ResourceNotFoundException("EpsNode", epsNodeId);
        }

        return projectRepository.findByEpsNodeId(epsNodeId).stream()
            .map(this::buildProjectResponse)
            .collect(Collectors.toList());
    }

    private void createRootWbsNode(Project project) {
        WbsNode rootWbs = new WbsNode();
        rootWbs.setCode(project.getCode());
        rootWbs.setName(project.getName());
        rootWbs.setProjectId(project.getId());
        rootWbs.setParentId(null);
        rootWbs.setSortOrder(0);

        wbsNodeRepository.save(rootWbs);
        log.info("Root WBS node created for project: {}", project.getId());
    }

    private ProjectResponse buildProjectResponse(Project project) {
        return new ProjectResponse(
            project.getId(),
            project.getCode(),
            project.getName(),
            project.getDescription(),
            project.getEpsNodeId(),
            project.getObsNodeId(),
            project.getPlannedStartDate(),
            project.getPlannedFinishDate(),
            project.getDataDate(),
            project.getStatus(),
            project.getMustFinishByDate(),
            project.getPriority(),
            toLocalDateTime(project.getCreatedAt()),
            toLocalDateTime(project.getUpdatedAt())
        );
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneId.systemDefault()) : null;
    }
}
