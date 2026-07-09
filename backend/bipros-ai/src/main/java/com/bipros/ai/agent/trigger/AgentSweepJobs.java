package com.bipros.ai.agent.trigger;

import com.bipros.ai.agent.core.AgentRegistry;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.memory.AgentMemoryService;
import com.bipros.ai.agent.pipeline.AgentPipelineRunner;
import com.bipros.ai.agent.pipeline.AgentPipelines;
import com.bipros.ai.agent.pipeline.AgentRunService;
import com.bipros.common.scheduling.ScheduledJobLeaseRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.ProjectStatus;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Scheduled sweeps for the agent framework, each lease-guarded so only one node fires (mirrors
 * {@code DprApprovalSlaEscalationJob}). Crons are configured under {@code bipros.agent.schedule}.
 *
 * <ul>
 *   <li><b>daily-sweep</b> — run {@code DAILY_PROJECT_SWEEP} for every ACTIVE project.</li>
 *   <li><b>ttl-expiry</b> — flip stale findings past their TTL to EXPIRED.</li>
 *   <li><b>issue-hourly</b> — run the {@code issue_intelligence} agent per ACTIVE project (best-effort).</li>
 *   <li><b>digest</b> / <b>portfolio-weekly</b> — TODO stubs (Track C owns the digest; portfolio
 *       agents are not yet registered).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentSweepJobs {

    private static final String ISSUE_AGENT_KEY = "issue_intelligence";

    private final ProjectRepository projectRepository;
    private final AgentPipelineRunner pipelineRunner;
    private final AgentMemoryService memoryService;
    private final AgentRunService agentRunService;
    private final AgentRegistry registry;
    private final ScheduledJobLeaseRepository leaseRepository;

    @Scheduled(cron = "${bipros.agent.schedule.daily-sweep-cron}")
    public void dailySweep() {
        if (!acquire("agent_daily_sweep")) {
            return;
        }
        List<Project> active = activeProjects();
        int fired = 0;
        for (Project p : active) {
            try {
                pipelineRunner.run(AgentPipelines.DAILY_PROJECT_SWEEP, p.getId(), "SWEEP", "daily-sweep");
                fired++;
            } catch (Exception ex) {
                log.warn("AgentSweepJobs.dailySweep failed for project {}: {}", p.getId(), ex.getMessage());
            }
        }
        log.info("AgentSweepJobs.dailySweep fired {} project sweeps", fired);
    }

    @Scheduled(cron = "${bipros.agent.schedule.ttl-expiry-cron}")
    public void ttlExpiry() {
        if (!acquire("agent_ttl_expiry")) {
            return;
        }
        int expired = memoryService.expireStale(Instant.now());
        log.info("AgentSweepJobs.ttlExpiry expired {} stale findings", expired);
    }

    @Scheduled(cron = "${bipros.agent.schedule.issue-hourly-cron}")
    public void issueHourly() {
        if (!acquire("agent_issue_hourly")) {
            return;
        }
        if (!registry.exists(ISSUE_AGENT_KEY)) {
            log.debug("AgentSweepJobs.issueHourly: agent '{}' not registered — skipping", ISSUE_AGENT_KEY);
            return;
        }
        List<Project> active = activeProjects();
        int fired = 0;
        for (Project p : active) {
            try {
                AgentRunContext ctx = AgentRunContext.forPipeline(
                        p.getId(), false, "SWEEP", "issue-hourly", null, Instant.now());
                agentRunService.runSingle(ISSUE_AGENT_KEY, ctx);
                fired++;
            } catch (Exception ex) {
                log.warn("AgentSweepJobs.issueHourly failed for project {}: {}", p.getId(), ex.getMessage());
            }
        }
        log.info("AgentSweepJobs.issueHourly ran {} on {} projects", ISSUE_AGENT_KEY, fired);
    }

    @Scheduled(cron = "${bipros.agent.schedule.digest-cron}")
    public void digest() {
        if (!acquire("agent_digest")) {
            return;
        }
        // TODO(Track C): build and deliver the daily findings digest.
        log.info("AgentSweepJobs.digest: stub — daily digest is owned by Track C");
    }

    @Scheduled(cron = "${bipros.agent.schedule.portfolio-weekly-cron}")
    public void portfolioWeekly() {
        if (!acquire("agent_portfolio_weekly")) {
            return;
        }
        // TODO: dispatch PORTFOLIO_WEEKLY (runner.run(PORTFOLIO_WEEKLY, null, ...)) once the
        // executive/portfolio agents are registered.
        log.info("AgentSweepJobs.portfolioWeekly: stub — awaiting portfolio agents");
    }

    private List<Project> activeProjects() {
        return projectRepository.findByStatus(ProjectStatus.ACTIVE, Pageable.unpaged()).getContent();
    }

    private boolean acquire(String jobName) {
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofMinutes(10));
        String owner = "node-" + UUID.randomUUID();
        return leaseRepository.tryAcquire(jobName, until, now, owner) != 0;
    }
}
