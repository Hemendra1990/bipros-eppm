package com.bipros.ai.agent.api;

import com.bipros.ai.agent.api.dto.AgentDtos.AgentFindingDto;
import com.bipros.ai.agent.api.dto.AgentDtos.AgentRunDetailDto;
import com.bipros.ai.agent.api.dto.AgentDtos.AgentRunDto;
import com.bipros.ai.agent.api.dto.AgentDtos.AgentSummaryDto;
import com.bipros.ai.agent.api.dto.AgentDtos.PageDto;
import com.bipros.ai.agent.api.dto.AgentDtos.PipelineRunAcceptedResponse;
import com.bipros.ai.agent.api.dto.AgentDtos.RunAcceptedResponse;
import com.bipros.ai.agent.core.Agent;
import com.bipros.ai.agent.core.AgentRegistry;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.pipeline.AgentPipelineRunner;
import com.bipros.ai.agent.pipeline.AgentPipelines;
import com.bipros.ai.agent.domain.AgentFinding;
import com.bipros.ai.agent.domain.AgentFindingRepository;
import com.bipros.ai.agent.domain.AgentRun;
import com.bipros.ai.agent.domain.AgentRunRepository;
import com.bipros.ai.agent.domain.AgentRunStatus;
import com.bipros.ai.agent.domain.FindingStatus;
import com.bipros.ai.agent.memory.AgentMemoryService;
import com.bipros.ai.agent.pipeline.AgentRunService;
import com.bipros.ai.security.AiAccessGuard;
import com.bipros.common.dto.ApiResponse;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.security.ProjectAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Read + manual-run surface for the agent framework (§6). Reactive SSE streams, pipeline runs,
 * admin channel/budget and supervisor investigate live in their own controllers, added by later
 * tracks. RBAC: read via {@code @aiAccess.canRead}, run via {@code @aiAccess.canWrite}.
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AgentController {

    private final AgentRegistry registry;
    private final AgentRunService agentRunService;
    private final AgentPipelineRunner pipelineRunner;
    private final AgentRunRepository runRepository;
    private final AgentFindingRepository findingRepository;
    private final AgentMemoryService memoryService;
    private final AgentDtoMapper mapper;
    private final AiAccessGuard aiAccess;
    private final ProjectAccessGuard projectAccess;

    @GetMapping("/projects/{projectId}/agents")
    @PreAuthorize("@aiAccess.canRead(#projectId)")
    public ResponseEntity<ApiResponse<List<AgentSummaryDto>>> listAgents(@PathVariable UUID projectId) {
        List<AgentSummaryDto> out = registry.all().stream().map(a -> toSummary(a, projectId)).toList();
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @PostMapping("/projects/{projectId}/agents/{agentKey}/run")
    @PreAuthorize("@aiAccess.canWrite(#projectId)")
    public ResponseEntity<ApiResponse<RunAcceptedResponse>> runAgent(@PathVariable UUID projectId,
                                                                     @PathVariable String agentKey) {
        AgentRunContext ctx = AgentRunContext.manual(projectId, projectAccess.currentUserId());
        AgentRun run = agentRunService.runSingle(agentKey, ctx);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(new RunAcceptedResponse(run.getId())));
    }

    @PostMapping("/projects/{projectId}/agents/pipelines/{pipelineKey}/run")
    @PreAuthorize("@aiAccess.canWrite(#projectId)")
    public ResponseEntity<ApiResponse<PipelineRunAcceptedResponse>> runPipeline(@PathVariable UUID projectId,
                                                                                @PathVariable String pipelineKey) {
        if (AgentPipelines.byKey(pipelineKey) == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("UNKNOWN_PIPELINE", "No such pipeline: " + pipelineKey));
        }
        UUID pipelineRunId = pipelineRunner.run(pipelineKey, projectId, "MANUAL", "user:" + projectAccess.currentUserId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(new PipelineRunAcceptedResponse(pipelineRunId)));
    }

    /** Portfolio activity feed — recent agent runs across the projects the caller can see. */
    @GetMapping("/portfolio/agent-activity")
    public ResponseEntity<ApiResponse<List<AgentRunDto>>> portfolioActivity(
            @RequestParam(defaultValue = "30") int limit) {
        Pageable pageable = PageRequest.of(0, Math.min(limit, 100));
        Set<UUID> scoped = projectAccess.getAccessibleProjectIdsForCurrentUser();
        List<AgentRun> runs;
        if (scoped == null) {                       // ADMIN — no row filter
            runs = runRepository.findAllByOrderByStartedAtDesc(pageable);
        } else if (scoped.isEmpty()) {
            runs = List.of();
        } else {
            runs = runRepository.findByProjectIdInOrderByStartedAtDesc(scoped, pageable);
        }
        return ResponseEntity.ok(ApiResponse.ok(runs.stream().map(mapper::toRunDto).toList()));
    }

    @GetMapping("/projects/{projectId}/agent-runs")
    @PreAuthorize("@aiAccess.canRead(#projectId)")
    public ResponseEntity<ApiResponse<PageDto<AgentRunDto>>> listRuns(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String agentKey,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        AgentRunStatus st = status == null ? null : AgentRunStatus.valueOf(status.toUpperCase());
        Page<AgentRun> result;
        if (agentKey != null && st != null) {
            result = runRepository.findByProjectIdAndAgentKeyAndStatusOrderByStartedAtDesc(projectId, agentKey, st, pageable);
        } else if (agentKey != null) {
            result = runRepository.findByProjectIdAndAgentKeyOrderByStartedAtDesc(projectId, agentKey, pageable);
        } else if (st != null) {
            result = runRepository.findByProjectIdAndStatusOrderByStartedAtDesc(projectId, st, pageable);
        } else {
            result = runRepository.findByProjectIdOrderByStartedAtDesc(projectId, pageable);
        }
        return ResponseEntity.ok(ApiResponse.ok(toPage(result, mapper::toRunDto)));
    }

    @GetMapping("/agent-runs/{runId}")
    public ResponseEntity<ApiResponse<AgentRunDetailDto>> getRun(@PathVariable UUID runId) {
        AgentRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("AgentRun", runId));
        requireReadAccess(run.getProjectId());
        List<AgentFindingDto> findings = findingRepository.findByRunId(runId).stream()
                .map(mapper::toFindingDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(new AgentRunDetailDto(mapper.toRunDto(run), findings)));
    }

    @GetMapping("/projects/{projectId}/agent-findings")
    @PreAuthorize("@aiAccess.canRead(#projectId)")
    public ResponseEntity<ApiResponse<PageDto<AgentFindingDto>>> listFindings(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String agentKey,
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        FindingStatus st = FindingStatus.valueOf(status.toUpperCase());
        Page<AgentFinding> result;
        if (agentKey != null) {
            result = findingRepository.findByProjectIdAndAgentKeyAndStatusOrderByLastSeenAtDesc(projectId, agentKey, st, pageable);
        } else if (severity != null) {
            result = findingRepository.findByProjectIdAndStatusAndSeverityOrderByLastSeenAtDesc(
                    projectId, st, Severity.fromString(severity), pageable);
        } else {
            result = findingRepository.findByProjectIdAndStatusOrderBySeverityDescLastSeenAtDesc(projectId, st, pageable);
        }
        return ResponseEntity.ok(ApiResponse.ok(toPage(result, mapper::toFindingDto)));
    }

    @PostMapping("/agent-findings/{id}/acknowledge")
    public ResponseEntity<ApiResponse<AgentFindingDto>> acknowledge(@PathVariable UUID id) {
        AgentFinding f = loadFindingWithAccess(id);
        AgentFinding saved = memoryService.acknowledge(id, projectAccess.currentUserId(), Instant.now());
        return ResponseEntity.ok(ApiResponse.ok(mapper.toFindingDto(saved)));
    }

    @PostMapping("/agent-findings/{id}/resolve")
    public ResponseEntity<ApiResponse<AgentFindingDto>> resolve(@PathVariable UUID id) {
        AgentFinding f = loadFindingWithAccess(id);
        AgentFinding saved = memoryService.resolve(id, projectAccess.currentUserId(), Instant.now());
        return ResponseEntity.ok(ApiResponse.ok(mapper.toFindingDto(saved)));
    }

    // ---- helpers ----

    private AgentSummaryDto toSummary(Agent a, UUID projectId) {
        AgentRun last = runRepository.findFirstByAgentKeyAndProjectIdOrderByStartedAtDesc(a.key(), projectId).orElse(null);
        return new AgentSummaryDto(a.key(), a.displayName(), a.supportsPortfolio(), mapper.toRunDto(last));
    }

    private AgentFinding loadFindingWithAccess(UUID id) {
        AgentFinding f = findingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AgentFinding", id));
        requireReadAccess(f.getProjectId());
        return f;
    }

    private void requireReadAccess(UUID projectId) {
        if (!aiAccess.canRead(projectId)) {
            throw new AccessDeniedException("Not permitted to access this project's agent data");
        }
    }

    private static <E, D> PageDto<D> toPage(Page<E> page, Function<E, D> map) {
        return new PageDto<>(page.getContent().stream().map(map).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
