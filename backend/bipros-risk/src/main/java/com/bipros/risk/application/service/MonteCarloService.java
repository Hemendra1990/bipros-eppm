package com.bipros.risk.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.risk.application.dto.MonteCarloResultDto;
import com.bipros.risk.application.dto.MonteCarloSimulationDto;
import com.bipros.risk.domain.model.MonteCarloResult;
import com.bipros.risk.domain.model.MonteCarloSimulation;
import com.bipros.risk.domain.repository.MonteCarloResultRepository;
import com.bipros.risk.domain.repository.MonteCarloSimulationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MonteCarloService {

    private final MonteCarloSimulationRepository simulationRepository;
    private final MonteCarloResultRepository resultRepository;

    public MonteCarloSimulationDto runSimulation(UUID projectId, int iterations) {
        log.info("Starting Monte Carlo simulation for project: {} with {} iterations", projectId, iterations);

        // For this implementation, we use a simplified approach:
        // - Baseline duration and cost are provided externally (from project schedule)
        // - We simulate variations using ±20% for duration and ±15% for cost
        // - In production, integrate with bipros-scheduling module to get PERT estimates

        MonteCarloSimulation simulation = new MonteCarloSimulation();
        simulation.setProjectId(projectId);
        simulation.setSimulationName("Monte Carlo Run - " + Instant.now());
        simulation.setIterations(iterations);
        simulation.setStatus(MonteCarloSimulation.MonteCarloStatus.RUNNING);

        // Placeholder baseline values - in production, fetch from project schedule
        Double baselineDuration = 100.0; // days
        BigDecimal baselineCost = BigDecimal.valueOf(1000000); // amount

        simulation.setBaselineDuration(baselineDuration);
        simulation.setBaselineCost(baselineCost);

        MonteCarloSimulation savedSim = simulationRepository.save(simulation);

        // Run simulations
        List<MonteCarloResult> results = runSimulationIterations(savedSim.getId(), iterations, baselineDuration, baselineCost);
        resultRepository.saveAll(results);

        // Calculate percentiles (P50 and P80)
        List<Double> sortedDurations = results.stream()
            .map(MonteCarloResult::getProjectDuration)
            .sorted()
            .collect(Collectors.toList());

        List<BigDecimal> sortedCosts = results.stream()
            .map(MonteCarloResult::getProjectCost)
            .sorted()
            .collect(Collectors.toList());

        int p50Index = (int) (sortedDurations.size() * 0.5);
        int p80Index = (int) (sortedDurations.size() * 0.8);

        Double p50Duration = sortedDurations.get(p50Index);
        Double p80Duration = sortedDurations.get(p80Index);
        BigDecimal p50Cost = sortedCosts.get(p50Index);
        BigDecimal p80Cost = sortedCosts.get(p80Index);

        savedSim.setConfidenceP50Duration(p50Duration);
        savedSim.setConfidenceP80Duration(p80Duration);
        savedSim.setConfidenceP50Cost(p50Cost);
        savedSim.setConfidenceP80Cost(p80Cost);
        savedSim.setStatus(MonteCarloSimulation.MonteCarloStatus.COMPLETED);
        savedSim.setCompletedAt(Instant.now());

        MonteCarloSimulation completed = simulationRepository.save(savedSim);

        log.info("Monte Carlo simulation completed. P50 Duration: {}, P80 Duration: {}", p50Duration, p80Duration);

        return MonteCarloSimulationDto.from(completed);
    }

    private List<MonteCarloResult> runSimulationIterations(UUID simulationId, int iterations, Double baselineDuration, BigDecimal baselineCost) {
        List<MonteCarloResult> results = new ArrayList<>();
        Random random = new Random();

        // Duration variation: ±20%, Cost variation: ±15%
        Double durationVariation = baselineDuration * 0.2;
        Double costVariation = baselineCost.doubleValue() * 0.15;

        for (int i = 1; i <= iterations; i++) {
            // Triangular distribution sampling for duration
            Double min = baselineDuration - durationVariation;
            Double max = baselineDuration + durationVariation;
            Double mode = baselineDuration;

            Double sampledDuration = sampleTriangular(random, min, mode, max);

            // Cost variation (simplified linear relationship with duration)
            Double costFactor = sampledDuration / baselineDuration;
            BigDecimal sampledCost = baselineCost.multiply(BigDecimal.valueOf(costFactor));

            MonteCarloResult result = new MonteCarloResult();
            result.setSimulationId(simulationId);
            result.setIterationNumber(i);
            result.setProjectDuration(sampledDuration);
            result.setProjectCost(sampledCost);

            results.add(result);
        }

        return results;
    }

    private Double sampleTriangular(Random random, Double min, Double mode, Double max) {
        double u = random.nextDouble();
        double fc = (mode - min) / (max - min);

        if (u < fc) {
            return min + Math.sqrt(u * (max - min) * (mode - min));
        } else {
            return max - Math.sqrt((1 - u) * (max - min) * (max - mode));
        }
    }

    public MonteCarloSimulationDto getLatestSimulation(UUID projectId) {
        MonteCarloSimulation simulation = simulationRepository.findLatestByProjectId(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("MonteCarloSimulation", projectId));

        List<MonteCarloResult> results = resultRepository.findBySimulationIdOrderByIterationNumber(simulation.getId());
        MonteCarloSimulationDto dto = MonteCarloSimulationDto.from(simulation);
        dto.setResults(results.stream().map(MonteCarloResultDto::from).collect(Collectors.toList()));

        return dto;
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
        List<MonteCarloSimulation> simulations = simulationRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        return simulations.stream()
            .map(MonteCarloSimulationDto::from)
            .collect(Collectors.toList());
    }
}
