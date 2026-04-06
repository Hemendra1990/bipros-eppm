package com.bipros.admin.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.admin.application.dto.JobServiceDto;
import com.bipros.admin.domain.model.JobService;
import com.bipros.admin.domain.repository.JobServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class JobServiceService {

    private final JobServiceRepository jobServiceRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public JobServiceDto getJobService(UUID id) {
        JobService job = jobServiceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("JobService", id));
        return mapToDto(job);
    }

    @Transactional(readOnly = true)
    public List<JobServiceDto> listJobServices() {
        return jobServiceRepository.findAll().stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    public JobServiceDto triggerJob(String name, UUID projectId) {
        JobService job = jobServiceRepository.findByName(name)
            .orElse(createNewJob(name, projectId));

        job.setStatus("RUNNING");
        job.setLastRunAt(Instant.now());
        job.setLastError(null);

        JobService saved = jobServiceRepository.save(job);
        auditService.logCreate("JobService", saved.getId(), mapToDto(saved));
        return mapToDto(saved);
    }

    public JobServiceDto completeJob(UUID id, long durationMs, String error) {
        JobService job = jobServiceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("JobService", id));

        job.setStatus(error == null ? "COMPLETED" : "FAILED");
        job.setLastDurationMs(durationMs);
        job.setLastError(error);

        JobService updated = jobServiceRepository.save(job);
        auditService.logUpdate("JobService", id, "job", null, mapToDto(updated));
        return mapToDto(updated);
    }

    public JobServiceDto updateJobStatus(UUID id, String status) {
        JobService job = jobServiceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("JobService", id));

        job.setStatus(status);
        JobService updated = jobServiceRepository.save(job);
        auditService.logUpdate("JobService", id, "status", null, mapToDto(updated));
        return mapToDto(updated);
    }

    @Transactional(readOnly = true)
    public JobServiceDto getJobStatus(UUID id) {
        JobService job = jobServiceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("JobService", id));
        return mapToDto(job);
    }

    private JobService createNewJob(String name, UUID projectId) {
        JobService job = new JobService();
        job.setName(name);
        job.setProjectId(projectId);
        job.setStatus("IDLE");
        JobService saved = jobServiceRepository.save(job);
        auditService.logCreate("JobService", saved.getId(), mapToDto(saved));
        return saved;
    }

    private JobServiceDto mapToDto(JobService job) {
        return JobServiceDto.builder()
            .id(job.getId())
            .name(job.getName())
            .projectId(job.getProjectId())
            .status(job.getStatus())
            .lastRunAt(job.getLastRunAt())
            .nextRunAt(job.getNextRunAt())
            .cronExpression(job.getCronExpression())
            .lastDurationMs(job.getLastDurationMs())
            .lastError(job.getLastError())
            .build();
    }
}
