package com.bipros.analytics.eval;

import com.bipros.analytics.application.dto.AnalyticsAssistantRequest;
import com.bipros.analytics.application.dto.AnalyticsAssistantResponse;
import com.bipros.analytics.application.service.AnalyticsAssistantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * GenAI Analytics Assistant evaluation suite.
 *
 * <p><b>Manual run only.</b> The default {@code mvn test} excludes the
 * {@code "eval"} JUnit tag (configured in {@code pom.xml}). Run with:
 * <pre>
 *   EVAL_LLM_API_KEY=sk-... mvn test -pl bipros-analytics -Dgroups=eval
 * </pre>
 *
 * <p><b>Acceptance:</b> aggregate pass rate &ge; 90% asserted in {@link #assertPassRate()}.
 *
 * <p><b>Requirements at run time:</b>
 * <ul>
 *   <li>Postgres + ClickHouse + Redis up via {@code docker compose up -d}.</li>
 *   <li>Backend already-seeded (run a backfill first if needed).</li>
 *   <li>An LLM provider configured for the test user (the suite uses the default).</li>
 *   <li>Env var {@code EVAL_LLM_API_KEY} non-empty (used as a presence-check; the
 *       actual key consumed is whichever the orchestrator decrypts from the
 *       configured provider).</li>
 * </ul>
 */
@SpringBootTest
@Tag("eval")
@EnabledIfEnvironmentVariable(named = "EVAL_LLM_API_KEY", matches = ".+")
class EvaluationSuiteTest {

    private static final AtomicInteger PASS = new AtomicInteger();
    private static final AtomicInteger FAIL = new AtomicInteger();
    private static final double MIN_PASS_RATE = 0.90;

    @Autowired
    private AnalyticsAssistantService svc;

    @TestFactory
    Stream<DynamicTest> runAllFixtures() throws Exception {
        List<EvaluationFixture> fixtures = loadFixtures();
        return fixtures.stream()
                .map(fx -> dynamicTest(fx.category() + "/" + fx.id(), () -> runOne(fx)));
    }

    @AfterAll
    static void assertPassRate() {
        int total = PASS.get() + FAIL.get();
        if (total == 0) {
            // No fixtures matched — treat as a configuration error rather than success.
            throw new AssertionError("Eval suite ran zero fixtures; check classpath:eval-set");
        }
        double rate = (double) PASS.get() / total;
        // Stays as a JUnit failure rather than a print, so CI surfaces it.
        assertThat(rate)
                .as("eval pass rate (%d/%d passed)", PASS.get(), total)
                .isGreaterThanOrEqualTo(MIN_PASS_RATE);
    }

    private void runOne(EvaluationFixture fx) {
        AnalyticsAssistantResponse resp;
        try {
            resp = svc.handle(new AnalyticsAssistantRequest(fx.queryText(), fx.projectContextId()));
        } catch (Exception ex) {
            FAIL.incrementAndGet();
            throw new AssertionError("Fixture " + fx.id() + " threw " + ex.getClass().getSimpleName()
                    + ": " + ex.getMessage(), ex);
        }
        try {
            assertExpectations(fx, resp);
            PASS.incrementAndGet();
        } catch (AssertionError ae) {
            FAIL.incrementAndGet();
            throw ae;
        }
    }

    private static void assertExpectations(EvaluationFixture fx, AnalyticsAssistantResponse resp) {
        EvaluationFixture.Expect ex = fx.expect();
        String kind = ex.kind() == null ? "success" : ex.kind().toLowerCase(Locale.ROOT);
        if ("refusal".equals(kind)) {
            String wantStatus = ex.refusalKind() != null ? ex.refusalKind() : "REFUSED";
            assertThat(resp.status())
                    .as("fixture %s expected refusal status %s but got %s", fx.id(), wantStatus, resp.status())
                    .isEqualTo(wantStatus);
            return;
        }
        // success path
        assertThat(resp.status())
                .as("fixture %s expected SUCCESS but got %s", fx.id(), resp.status())
                .isEqualTo("SUCCESS");
        if (ex.toolCalled() != null && !"any".equalsIgnoreCase(ex.toolCalled())) {
            assertThat(resp.toolUsed())
                    .as("fixture %s expected tool %s but got %s", fx.id(), ex.toolCalled(), resp.toolUsed())
                    .isEqualTo(ex.toolCalled());
        }
        int minRows = ex.minRows() == null ? 0 : ex.minRows();
        if (minRows > 0) {
            assertThat(resp.rows())
                    .as("fixture %s expected ≥ %d rows", fx.id(), minRows)
                    .hasSizeGreaterThanOrEqualTo(minRows);
        }
        if (ex.narrativeContains() != null) {
            String narrative = resp.narrative() == null ? "" : resp.narrative().toLowerCase(Locale.ROOT);
            for (String needle : ex.narrativeContains()) {
                assertThat(narrative)
                        .as("fixture %s narrative should contain %s", fx.id(), needle)
                        .contains(needle.toLowerCase(Locale.ROOT));
            }
        }
    }

    private static List<EvaluationFixture> loadFixtures() throws Exception {
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath:eval-set/**/*.yaml");
        List<EvaluationFixture> out = new ArrayList<>(resources.length);
        for (Resource r : resources) {
            try (InputStream in = r.getInputStream()) {
                EvaluationFixture fx = yaml.readValue(in, EvaluationFixture.class);
                out.add(fx);
            }
        }
        return out;
    }
}
