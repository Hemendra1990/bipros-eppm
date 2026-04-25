package com.bipros.risk.application.service;

import com.bipros.risk.application.dto.RiskAnalysisQuality;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.model.RiskProbability;
import com.bipros.risk.domain.model.RiskResponse;
import com.bipros.risk.domain.model.RiskResponseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RiskQualityService — analysis quality scoring")
class RiskQualityServiceTest {

    private final RiskQualityService service = new RiskQualityService();

    @Nested
    @DisplayName("scoring")
    class Scoring {

        @Test
        @DisplayName("freshly copied risk (rating only, no owner/desc/response) → NOT_ANALYSED, score=1")
        void freshCopyIsNotAnalysed() {
            Risk r = baseRisk();
            r.setProbability(RiskProbability.MEDIUM);
            r.setImpactCost(3);

            RiskAnalysisQuality q = service.assess(r, List.of());

            assertThat(q.score()).isEqualTo(1);
            assertThat(q.level()).isEqualTo(RiskAnalysisQuality.QualityLevel.NOT_ANALYSED);
            assertThat(q.criteria()).containsEntry("hasRating", true)
                .containsEntry("hasOwner", false)
                .containsEntry("hasDescription", false)
                .containsEntry("hasResponse", false);
        }

        @Test
        @DisplayName("with owner + rating + long description → PARTIALLY_ANALYSED, score=3")
        void threeOfFourIsPartial() {
            Risk r = baseRisk();
            r.setProbability(RiskProbability.HIGH);
            r.setImpactCost(4);
            r.setOwnerId(UUID.randomUUID());
            r.setDescription("a".repeat(60));

            RiskAnalysisQuality q = service.assess(r, List.of());

            assertThat(q.score()).isEqualTo(3);
            assertThat(q.level()).isEqualTo(RiskAnalysisQuality.QualityLevel.PARTIALLY_ANALYSED);
        }

        @Test
        @DisplayName("all four criteria met → WELL_ANALYSED, score=4")
        void allFourIsWellAnalysed() {
            Risk r = baseRisk();
            r.setProbability(RiskProbability.HIGH);
            r.setImpactSchedule(4);
            r.setOwnerId(UUID.randomUUID());
            r.setDescription("a".repeat(60));

            RiskResponse resp = new RiskResponse();
            resp.setResponseType(RiskResponseType.MITIGATE);
            resp.setResponsibleId(UUID.randomUUID());

            RiskAnalysisQuality q = service.assess(r, List.of(resp));

            assertThat(q.score()).isEqualTo(4);
            assertThat(q.level()).isEqualTo(RiskAnalysisQuality.QualityLevel.WELL_ANALYSED);
            assertThat(q.criteria().values()).containsOnly(true);
        }

        @Test
        @DisplayName("empty risk → NOT_ANALYSED, score=0")
        void emptyRiskIsNotAnalysed() {
            RiskAnalysisQuality q = service.assess(baseRisk(), List.of());

            assertThat(q.score()).isZero();
            assertThat(q.level()).isEqualTo(RiskAnalysisQuality.QualityLevel.NOT_ANALYSED);
        }

        @Test
        @DisplayName("response without responsibleId does NOT count")
        void incompleteResponseDoesNotCount() {
            Risk r = baseRisk();
            RiskResponse resp = new RiskResponse();
            resp.setResponseType(RiskResponseType.AVOID);
            // responsibleId left null

            RiskAnalysisQuality q = service.assess(r, List.of(resp));

            assertThat(q.criteria()).containsEntry("hasResponse", false);
        }

        @Test
        @DisplayName("description shorter than threshold does NOT count")
        void shortDescriptionDoesNotCount() {
            Risk r = baseRisk();
            r.setDescription("too short");

            RiskAnalysisQuality q = service.assess(r, List.of());

            assertThat(q.criteria()).containsEntry("hasDescription", false);
        }

        @Test
        @DisplayName("rating requires probability AND at least one impact field")
        void ratingNeedsProbabilityAndImpact() {
            Risk r = baseRisk();
            r.setProbability(RiskProbability.MEDIUM);
            // both impactCost and impactSchedule null

            RiskAnalysisQuality q = service.assess(r, List.of());

            assertThat(q.criteria()).containsEntry("hasRating", false);
        }
    }

    @Nested
    @DisplayName("level banding")
    class Banding {

        @Test
        @DisplayName("score 0–1 → NOT_ANALYSED")
        void zeroOrOneNotAnalysed() {
            assertThat(RiskAnalysisQuality.levelFor(0))
                .isEqualTo(RiskAnalysisQuality.QualityLevel.NOT_ANALYSED);
            assertThat(RiskAnalysisQuality.levelFor(1))
                .isEqualTo(RiskAnalysisQuality.QualityLevel.NOT_ANALYSED);
        }

        @Test
        @DisplayName("score 2–3 → PARTIALLY_ANALYSED")
        void twoOrThreePartial() {
            assertThat(RiskAnalysisQuality.levelFor(2))
                .isEqualTo(RiskAnalysisQuality.QualityLevel.PARTIALLY_ANALYSED);
            assertThat(RiskAnalysisQuality.levelFor(3))
                .isEqualTo(RiskAnalysisQuality.QualityLevel.PARTIALLY_ANALYSED);
        }

        @Test
        @DisplayName("score 4 → WELL_ANALYSED")
        void fourWellAnalysed() {
            assertThat(RiskAnalysisQuality.levelFor(4))
                .isEqualTo(RiskAnalysisQuality.QualityLevel.WELL_ANALYSED);
        }
    }

    private static Risk baseRisk() {
        Risk r = new Risk();
        r.setProjectId(UUID.randomUUID());
        r.setCode("RISK-TEST");
        r.setTitle("Test risk");
        return r;
    }
}
