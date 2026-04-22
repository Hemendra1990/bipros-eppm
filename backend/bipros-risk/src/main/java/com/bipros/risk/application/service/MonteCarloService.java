package com.bipros.risk.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.risk.application.dto.MonteCarloActivityStatDto;
import com.bipros.risk.application.dto.MonteCarloResultDto;
import com.bipros.risk.application.dto.MonteCarloRunRequest;
import com.bipros.risk.application.dto.MonteCarloSimulationDto;
import com.bipros.risk.application.simulation.MonteCarloEngine;
import com.bipros.risk.application.simulation.MonteCarloInput;
import com.bipros.risk.domain.model.DistributionType;
import com.bipros.risk.application.dto.MonteCarloCashflowBucketDto;
import com.bipros.risk.application.dto.MonteCarloMilestoneStatDto;
import com.bipros.risk.application.dto.MonteCarloRiskContributionDto;
import com.bipros.risk.domain.model.MonteCarloActivityStat;
import com.bipros.risk.domain.model.MonteCarloCashflowBucket;
import com.bipros.risk.domain.model.MonteCarloMilestoneStat;
import com.bipros.risk.domain.model.MonteCarloResult;
import com.bipros.risk.domain.model.MonteCarloRiskContribution;
import com.bipros.risk.domain.model.MonteCarloSimulation;
import com.bipros.risk.domain.repository.MonteCarloActivityStatRepository;
import com.bipros.risk.domain.repository.MonteCarloCashflowBucketRepository;
import com.bipros.risk.domain.repository.MonteCarloMilestoneStatRepository;
import com.bipros.risk.domain.repository.MonteCarloResultRepository;
import com.bipros.risk.domain.repository.MonteCarloRiskContributionRepository;
import com.bipros.risk.domain.repository.MonteCarloSimulationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MonteCarloService {

    private final MonteCarloSimulationRepository simulationRepository;
    private final MonteCarloResultRepository resultRepository;
    private final MonteCarloActivityStatRepository activityStatRepository;
    private final MonteCarloMilestoneStatRepository milestoneStatRepository;
    private final MonteCarloCashflowBucketRepository cashflowBucketRepository;
    private final MonteCarloRiskContributionRepository riskContributionRepository;
    private final MonteCarloEngine engine;

    public MonteCarloSimulationDto runSimulation(UUID projectId, MonteCarloRunRequest request) {
        int iterations = request != null && request.getIterations() != null ? request.getIterations() : 10_000;
        DistributionType dist = request != null && request.getDefaultDistribution() != null
            ? request.getDefaultDistribution() : DistributionType.TRIANGULAR;
        double variance = request != null && request.getFallbackVariancePct() != null
            ? request.getFallbackVariancePct() : 0.2;
        boolean enableRisks = request != null && Boolean.TRUE.equals(request.getEnableRisks());
        Long seed = request != null ? request.getRandomSeed() : null;

        log.info("Starting Monte Carlo: project={} iterations={} distribution={} variance={} seed={}",
            projectId, iterations, dist, variance, seed);

        MonteCarloSimulation sim = new MonteCarloSimulation();
        sim.setProjectId(projectId);
        sim.setSimulationName("Monte Carlo " + Instant.now());
        sim.setIterations(iterations);
        sim.setStatus(MonteCarloSimulation.MonteCarloStatus.RUNNING);
        // Required columns: populate before first save. Engine result overwrites these.
        sim.setBaselineDuration(0.0);
        sim.setBaselineCost(BigDecimal.ZERO);
        sim.setConfigJson(String.format(
            "{\"distribution\":\"%s\",\"variance\":%s,\"enableRisks\":%s,\"seed\":%s}",
            dist, variance, enableRisks, seed));
        sim = simulationRepository.save(sim);

        MonteCarloInput input = new MonteCarloInput(projectId, iterations, dist, variance, enableRisks, seed);
        try {
            MonteCarloEngine.EngineResult result = engine.run(input);

            sim.setBaselineId(result.baselineId());
            sim.setBaselineDuration(result.baselineDuration());
            sim.setBaselineCost(result.baselineCost());
            sim.setDataDate(result.dataDate());

            var d = result.durationStats();
            sim.setP10Duration(d.p10());
            sim.setP25Duration(d.p25());
            sim.setConfidenceP50Duration(d.p50());
            sim.setP75Duration(d.p75());
            sim.setConfidenceP80Duration(d.p80());
            sim.setP90Duration(d.p90());
            sim.setP95Duration(d.p95());
            sim.setP99Duration(d.p99());
            sim.setMeanDuration(d.mean());
            sim.setStddevDuration(d.stddev());

            var c = result.costStats();
            sim.setP10Cost(c.p10());
            sim.setP25Cost(c.p25());
            sim.setConfidenceP50Cost(c.p50());
            sim.setP75Cost(c.p75());
            sim.setConfidenceP80Cost(c.p80());
            sim.setP90Cost(c.p90());
            sim.setP95Cost(c.p95());
            sim.setP99Cost(c.p99());
            sim.setMeanCost(c.mean());
            sim.setStddevCost(c.stddev());

            sim.setIterationsCompleted(iterations);
            sim.setStatus(MonteCarloSimulation.MonteCarloStatus.COMPLETED);
            sim.setCompletedAt(Instant.now());
            MonteCarloSimulation savedSim = simulationRepository.save(sim);

            // Persist per-iteration project-level series.
            double[] durations = result.iterationDurations();
            BigDecimal[] costs = result.iterationCosts();
            List<MonteCarloResult> rows = new ArrayList<>(durations.length);
            for (int i = 0; i < durations.length; i++) {
                MonteCarloResult r = new MonteCarloResult();
                r.setSimulationId(savedSim.getId());
                r.setIterationNumber(i + 1);
                r.setProjectDuration(durations[i]);
                r.setProjectCost(costs[i]);
                rows.add(r);
            }
            resultRepository.saveAll(rows);

            // Persist per-activity stats.
            for (MonteCarloActivityStat stat : result.activityStats()) {
                stat.setSimulationId(savedSim.getId());
            }
            activityStatRepository.saveAll(result.activityStats());

            // Persist milestone stats.
            for (MonteCarloMilestoneStat m : result.milestoneStats()) {
                m.setSimulationId(savedSim.getId());
            }
            milestoneStatRepository.saveAll(result.milestoneStats());

            // Persist cashflow buckets.
            for (MonteCarloCashflowBucket b : result.cashflow()) {
                b.setSimulationId(savedSim.getId());
            }
            cashflowBucketRepository.saveAll(result.cashflow());

            // Persist risk contributions.
            for (MonteCarloRiskContribution rc : result.riskContributions()) {
                rc.setSimulationId(savedSim.getId());
            }
            riskContributionRepository.saveAll(result.riskContributions());

            return MonteCarloSimulationDto.from(savedSim);
        } catch (RuntimeException ex) {
            log.error("Monte Carlo failed for project {}: {}", projectId, ex.getMessage(), ex);
            sim.setStatus(MonteCarloSimulation.MonteCarloStatus.FAILED);
            sim.setErrorMessage(ex.getMessage());
            sim.setCompletedAt(Instant.now());
            simulationRepository.save(sim);
            throw ex;
        }
    }

    public MonteCarloSimulationDto getLatestSimulation(UUID projectId) {
        return simulationRepository.findLatestByProjectId(projectId)
            .map(simulation -> {
                List<MonteCarloResult> results = resultRepository.findBySimulationIdOrderByIterationNumber(simulation.getId());
                MonteCarloSimulationDto dto = MonteCarloSimulationDto.from(simulation);
                dto.setResults(results.stream().map(MonteCarloResultDto::from).collect(Collectors.toList()));
                return dto;
            })
            .orElse(null);
    }

    public MonteCarloSimulationDto getSimulation(UUID simulationId) {
        MonteCarloSimulation simulation = simulationRepository.findById(simulationId)
            .orElseThrow(() -> new ResourceNotFoundException("MonteCarloSimulation", simulationId));

        List<MonteCarloResult> results = resultRepository.findBySimulationIdOrderByIterationNumber(simulationId);
        MonteCarloSimulationDto dto = MonteCarloSimulationDto.from(simulation);
        dto.setResults(results.stream().map(MonteCarloResultDto::from).collect(Collectors.toList()));
        return dto;
    }

    public List<MonteCarloSimulationDto> listProjectSimulations(UUID projectId) {
        return simulationRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
            .map(MonteCarloSimulationDto::from)
            .collect(Collectors.toList());
    }

    public List<MonteCarloActivityStatDto> getActivityStats(UUID simulationId) {
        return activityStatRepository.findBySimulationIdOrderByCriticalityIndexDesc(simulationId).stream()
            .map(MonteCarloActivityStatDto::from)
            .collect(Collectors.toList());
    }

    public List<MonteCarloMilestoneStatDto> getMilestoneStats(UUID simulationId) {
        return milestoneStatRepository.findBySimulationId(simulationId).stream()
            .map(MonteCarloMilestoneStatDto::from)
            .collect(Collectors.toList());
    }

    public List<MonteCarloCashflowBucketDto> getCashflow(UUID simulationId) {
        return cashflowBucketRepository.findBySimulationIdOrderByPeriodEndDate(simulationId).stream()
            .map(MonteCarloCashflowBucketDto::from)
            .collect(Collectors.toList());
    }

    public List<MonteCarloRiskContributionDto> getRiskContributions(UUID simulationId) {
        return riskContributionRepository.findBySimulationIdOrderByOccurrenceRateDesc(simulationId).stream()
            .map(MonteCarloRiskContributionDto::from)
            .collect(Collectors.toList());
    }
}
