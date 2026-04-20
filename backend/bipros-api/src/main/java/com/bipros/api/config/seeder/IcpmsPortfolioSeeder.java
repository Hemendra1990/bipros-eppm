package com.bipros.api.config.seeder;

import com.bipros.portfolio.domain.Portfolio;
import com.bipros.portfolio.domain.PortfolioProject;
import com.bipros.portfolio.domain.ProjectScore;
import com.bipros.portfolio.domain.ScoringCriterion;
import com.bipros.portfolio.domain.ScoringModel;
import com.bipros.portfolio.infrastructure.repository.PortfolioProjectRepository;
import com.bipros.portfolio.infrastructure.repository.PortfolioRepository;
import com.bipros.portfolio.infrastructure.repository.ProjectScoreRepository;
import com.bipros.portfolio.infrastructure.repository.ScoringCriterionRepository;
import com.bipros.portfolio.infrastructure.repository.ScoringModelRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * IC-PMS — seeds a single DMIC Corridor portfolio, a default 4-criteria scoring
 * model, and per-criterion scores for the master DMIC programme project so the
 * M0 portfolio workbench has non-empty content.
 *
 * <p>Sentinel: any existing scoring model or portfolio rows are treated as
 * already seeded and the run is skipped.
 */
@Slf4j
@Component
@Profile("dev")
@Order(108)
@RequiredArgsConstructor
public class IcpmsPortfolioSeeder implements CommandLineRunner {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioProjectRepository portfolioProjectRepository;
    private final ScoringModelRepository scoringModelRepository;
    private final ScoringCriterionRepository scoringCriterionRepository;
    private final ProjectScoreRepository projectScoreRepository;
    private final ProjectRepository projectRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (portfolioRepository.count() > 0 || scoringModelRepository.count() > 0) {
            log.info("[IC-PMS Portfolio] portfolio/scoring model already present, skipping");
            return;
        }

        Project programme = projectRepository.findByCode("DMIC-PROG").orElse(null);
        if (programme == null) {
            log.warn("[IC-PMS Portfolio] DMIC-PROG project not found — run Phase A first");
            return;
        }

        // --- Portfolio ---
        Portfolio portfolio = new Portfolio();
        portfolio.setName("DMIC Corridor Portfolio (code: DMIC-CORRIDOR)");
        portfolio.setDescription("DMIC-CORRIDOR — Delhi Mumbai Industrial Corridor consolidated portfolio "
                + "spanning 5 geographic nodes (N03/N04/N05/N06/N08).");
        portfolio.setIsActive(true);
        portfolio = portfolioRepository.save(portfolio);

        // Attach master programme project
        PortfolioProject pp = new PortfolioProject();
        pp.setPortfolioId(portfolio.getId());
        pp.setProjectId(programme.getId());
        pp.setPriorityScore(8.5);
        portfolioProjectRepository.save(pp);

        // --- Scoring model ---
        ScoringModel model = new ScoringModel();
        model.setName("DMIC Default Scoring Model");
        model.setDescription("4-criterion weighted scoring — Strategic Fit, Budget, Schedule, Risk. "
                + "Weights sum to 1.0; each criterion scored 0-10.");
        model.setIsDefault(true);
        model = scoringModelRepository.save(model);

        UUID modelId = model.getId();
        UUID projectId = programme.getId();

        // --- Criteria + project scores ---
        ScoringCriterion strategic = criterion(modelId, "Strategic Fit", 0.35, 1);
        ScoringCriterion budget = criterion(modelId, "Budget Alignment", 0.20, 2);
        ScoringCriterion schedule = criterion(modelId, "Schedule Confidence", 0.25, 3);
        ScoringCriterion risk = criterion(modelId, "Risk Exposure (inverse)", 0.20, 4);

        score(projectId, modelId, strategic.getId(), 9.2);  // high strategic fit — flagship corridor
        score(projectId, modelId, budget.getId(), 7.5);     // CPI 0.96 (phase M2 EVM)
        score(projectId, modelId, schedule.getId(), 6.8);   // SPI 0.87 (phase M2 EVM)
        score(projectId, modelId, risk.getId(), 6.0);       // 3 RED + 1 CRIMSON risks (phase E)

        log.info("[IC-PMS Portfolio] seeded 1 portfolio, 1 scoring model with 4 criteria, 4 project scores");
    }

    private ScoringCriterion criterion(UUID modelId, String name, double weight, int sortOrder) {
        ScoringCriterion c = new ScoringCriterion();
        c.setScoringModelId(modelId);
        c.setName(name);
        c.setWeight(weight);
        c.setMinScore(0.0);
        c.setMaxScore(10.0);
        c.setSortOrder(sortOrder);
        return scoringCriterionRepository.save(c);
    }

    private void score(UUID projectId, UUID modelId, UUID criterionId, double score) {
        ProjectScore ps = new ProjectScore();
        ps.setProjectId(projectId);
        ps.setScoringModelId(modelId);
        ps.setScoringCriterionId(criterionId);
        ps.setScore(score);
        projectScoreRepository.save(ps);
    }
}
