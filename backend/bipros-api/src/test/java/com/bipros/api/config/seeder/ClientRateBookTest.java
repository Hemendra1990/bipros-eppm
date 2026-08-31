package com.bipros.api.config.seeder;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integrity of the generated client rate book (seed/client-rate-book.json). This is production
 * seed data — a malformed entry would fail at boot on a client deployment, so the build checks
 * it instead: counts against the source sheet, code uniqueness (role codes are unique in the
 * DB), every rate parses as a non-negative 4-dp decimal, and the owner-approved monthly/26
 * conversion arithmetic.
 */
class ClientRateBookTest {

    private static ClientRateBook book;

    @BeforeAll
    static void load() throws Exception {
        book = ClientRateBook.load(new ObjectMapper());
    }

    @Test
    void counts_match_the_source_sheet_after_agreed_cleanups() {
        // 174 sheet materials − 3 duplicate codes (6060/6068/6069 second occurrences)
        assertThat(book.materials()).hasSize(171);
        assertThat(book.equipment()).hasSize(60);
        // 25 titled rows − overheads/dup-Plumber, grouped by title → 19 roles / 24 N-E variants
        assertThat(book.manpower()).hasSize(19);
        assertThat(book.manpower().stream().mapToInt(m -> m.variants().size()).sum()).isEqualTo(24);
        assertThat(book.version()).isEqualTo(1);
    }

    @Test
    void codes_are_unique_across_all_three_sections() {
        Set<String> seen = new HashSet<>();
        List<String> all = new java.util.ArrayList<>();
        book.materials().forEach(m -> all.add(m.code()));
        book.equipment().forEach(e -> all.add(e.code()));
        book.manpower().forEach(m -> all.add(m.code()));
        for (String code : all) {
            assertThat(code).isNotBlank();
            assertThat(seen.add(code)).as("duplicate role code: %s", code).isTrue();
        }
    }

    @Test
    void every_rate_parses_as_a_non_negative_decimal() {
        book.materials().forEach(m -> {
            if (m.rate() != null) {
                assertThat(new BigDecimal(m.rate())).isGreaterThanOrEqualTo(BigDecimal.ZERO);
                assertThat(m.unit()).as("material %s has a rate but no unit", m.code()).isNotNull();
            }
        });
        book.equipment().forEach(e -> {
            if (e.ratePerDay() != null) {
                assertThat(new BigDecimal(e.ratePerDay())).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            }
        });
        book.manpower().forEach(m -> m.variants().forEach(v -> {
            assertThat(new BigDecimal(v.salaryPerMonth())).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(v.category()).isIn("National", "Expat");
        }));
    }

    @Test
    void monthly_to_daily_conversion_matches_the_client_arithmetic() {
        // Mason 163/month ÷ 26 = 6.2692/day; Watchman 450 ÷ 26 = 17.3077 (HALF_UP, 4 dp).
        assertThat(new BigDecimal("163").divide(ClientRateBookSeeder.DAYS_PER_MONTH, 4, RoundingMode.HALF_UP))
            .isEqualByComparingTo("6.2692");
        assertThat(new BigDecimal("450").divide(ClientRateBookSeeder.DAYS_PER_MONTH, 4, RoundingMode.HALF_UP))
            .isEqualByComparingTo("17.3077");

        ClientRateBook.ManpowerRole mason = book.manpower().stream()
            .filter(m -> m.title().equals("Mason")).findFirst().orElseThrow();
        assertThat(mason.variants()).singleElement()
            .satisfies(v -> assertThat(new BigDecimal(v.salaryPerMonth())).isEqualByComparingTo("163"));
    }

    @Test
    void known_spot_checks_from_the_sheet() {
        assertThat(book.materials().stream().filter(m -> m.code().equals("CEMENT")).findFirst().orElseThrow())
            .satisfies(m -> {
                assertThat(m.unit()).isEqualTo("Bag");
                assertThat(new BigDecimal(m.rate())).isEqualByComparingTo("1.45");
            });
        assertThat(book.equipment().stream().filter(e -> e.code().equals("JCB")).findFirst().orElseThrow())
            .satisfies(e -> {
                assertThat(new BigDecimal(e.ratePerDay())).isEqualByComparingTo("30");
                assertThat(e.operatorCode()).isEqualTo("OPE");
            });
        // Typo fixes applied at generation: no 'Tecnician'/'Electrican' titles survive.
        assertThat(book.manpower()).noneMatch(m -> m.title().contains("Tecnician"));
        assertThat(book.manpower()).noneMatch(m -> m.title().equals("Electrican"));
        // Overheads are excluded.
        assertThat(book.manpower()).noneMatch(m -> m.title().contains("Charges"));
    }
}
