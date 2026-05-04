package com.bipros.api.config.seeder;

import com.bipros.portfolio.domain.ScoringModel;
import com.bipros.portfolio.infrastructure.repository.ScoringModelRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@Slf4j
@Component
@Profile("seed")
@Order(157)
@RequiredArgsConstructor
public class OmanPortfolioSeeder implements CommandLineRunner {

    private static final String PROJECT_CODE = "6155";
    private static final long DETERMINISTIC_SEED = 6155L;
    private static final LocalDate DEFAULT_DATA_DATE = LocalDate.of(2026, 4, 29);

    private final ProjectRepository projectRepository;
    private final ScoringModelRepository scoringModelRepository;

    @Override
    public void run(String... args) {
        Optional<Project> projectOpt = projectRepository.findByCode(PROJECT_CODE);
        if (projectOpt.isEmpty()) {
            log.warn("[BNK-PORTFOLIO] project '{}' not found — skipping", PROJECT_CODE);
            return;
        }

        if (scoringModelRepository.findByIsDefaultTrue().isPresent()) {
            log.info("[BNK-PORTFOLIO] default ScoringModel already present — skipping");
            return;
        }

        ScoringModel model = new ScoringModel();
        model.setName("Oman Portfolio Scoring");
        model.setDescription("Default scoring model for Oman Barka\u2013Nakhal road project portfolio "
            + "with criteria: schedule adherence (30%), cost performance (25%), quality compliance (20%), "
            + "safety record (15%), stakeholder satisfaction (10%)");
        model.setIsDefault(Boolean.TRUE);
        scoringModelRepository.save(model);

        log.info("[BNK-PORTFOLIO] Seeded 1 ScoringModel: 'Oman Portfolio Scoring' (default)");
    }
}
