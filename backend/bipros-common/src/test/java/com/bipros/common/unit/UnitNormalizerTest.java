package com.bipros.common.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnitNormalizerTest {

    // -----------------------------------------------------------------------
    // 1. Synonyms collapse to their canonical spelling
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("canonicalLabel — synonym collapsing")
    class SynonymCollapsing {

        @Test
        @DisplayName("cu.m. / CUM / Cum / CU_M all collapse to Cum")
        void cumSynonymsCollapse() {
            assertThat(UnitNormalizer.canonicalLabel("cu.m.")).isEqualTo("Cum");
            assertThat(UnitNormalizer.canonicalLabel("CUM")).isEqualTo("Cum");
            assertThat(UnitNormalizer.canonicalLabel("Cum")).isEqualTo("Cum");
            assertThat(UnitNormalizer.canonicalLabel("CU_M")).isEqualTo("Cum");
            assertThat(UnitNormalizer.canonicalLabel("m3")).isEqualTo("Cum");
            assertThat(UnitNormalizer.canonicalLabel("cubic metre")).isEqualTo("Cum");
            assertThat(UnitNormalizer.canonicalLabel("cubic meter")).isEqualTo("Cum");
        }

        @Test
        @DisplayName("sq.m. / Sqm collapse to Sqm")
        void sqmSynonymsCollapse() {
            assertThat(UnitNormalizer.canonicalLabel("sq.m.")).isEqualTo("Sqm");
            assertThat(UnitNormalizer.canonicalLabel("Sqm")).isEqualTo("Sqm");
            assertThat(UnitNormalizer.canonicalLabel("m2")).isEqualTo("Sqm");
            assertThat(UnitNormalizer.canonicalLabel("square metre")).isEqualTo("Sqm");
            assertThat(UnitNormalizer.canonicalLabel("square meter")).isEqualTo("Sqm");
        }

        @Test
        @DisplayName("lin.m. / Lm / Rm / R/mtr collapse to m")
        void runningMetreSynonymsCollapse() {
            assertThat(UnitNormalizer.canonicalLabel("lin.m.")).isEqualTo("m");
            assertThat(UnitNormalizer.canonicalLabel("linm")).isEqualTo("m");
            assertThat(UnitNormalizer.canonicalLabel("Lm")).isEqualTo("m");
            assertThat(UnitNormalizer.canonicalLabel("Rm")).isEqualTo("m");
            assertThat(UnitNormalizer.canonicalLabel("R/mtr")).isEqualTo("m");
            assertThat(UnitNormalizer.canonicalLabel("rm")).isEqualTo("m");
            assertThat(UnitNormalizer.canonicalLabel("running metre")).isEqualTo("m");
            assertThat(UnitNormalizer.canonicalLabel("running meter")).isEqualTo("m");
        }

        @Test
        @DisplayName("Manday collapses to Day")
        void mandaySynonymsCollapse() {
            assertThat(UnitNormalizer.canonicalLabel("Manday")).isEqualTo("Day");
            assertThat(UnitNormalizer.canonicalLabel("man-day")).isEqualTo("Day");
            assertThat(UnitNormalizer.canonicalLabel("mandays")).isEqualTo("Day");
        }

        @Test
        @DisplayName("Ltr / Ltr (legacy) collapse to L")
        void litreSynonymsCollapse() {
            assertThat(UnitNormalizer.canonicalLabel("Ltr")).isEqualTo("L");
            assertThat(UnitNormalizer.canonicalLabel("Ltr (legacy)")).isEqualTo("L");
            assertThat(UnitNormalizer.canonicalLabel("litre")).isEqualTo("L");
            assertThat(UnitNormalizer.canonicalLabel("liter")).isEqualTo("L");
        }

        @Test
        @DisplayName("Ls / lump sum collapse to LS")
        void lumpSumSynonymsCollapse() {
            assertThat(UnitNormalizer.canonicalLabel("Ls")).isEqualTo("LS");
            assertThat(UnitNormalizer.canonicalLabel("lump sum")).isEqualTo("LS");
            assertThat(UnitNormalizer.canonicalLabel("ls")).isEqualTo("LS");
        }

        @Test
        @DisplayName("Tonne collapses to MT")
        void tonneSynonymsCollapse() {
            assertThat(UnitNormalizer.canonicalLabel("Tonne")).isEqualTo("MT");
            assertThat(UnitNormalizer.canonicalLabel("tonne")).isEqualTo("MT");
            assertThat(UnitNormalizer.canonicalLabel("t")).isEqualTo("MT");
            assertThat(UnitNormalizer.canonicalLabel("metric tonne")).isEqualTo("MT");
        }

        @Test
        @DisplayName("Nr. / Nr / No. / number / nos collapse to Nos")
        void nosSynonymsCollapse() {
            assertThat(UnitNormalizer.canonicalLabel("Nr.")).isEqualTo("Nos");
            assertThat(UnitNormalizer.canonicalLabel("Nr")).isEqualTo("Nos");
            assertThat(UnitNormalizer.canonicalLabel("No.")).isEqualTo("Nos");
            assertThat(UnitNormalizer.canonicalLabel("number")).isEqualTo("Nos");
            assertThat(UnitNormalizer.canonicalLabel("nos")).isEqualTo("Nos");
        }

        @Test
        @DisplayName("kg. collapses to kg")
        void kgSynonymCollapses() {
            assertThat(UnitNormalizer.canonicalLabel("kg.")).isEqualTo("kg");
        }

        @Test
        @DisplayName("self-canonical units resolve to their declared canonical spelling")
        void selfCanonicalUnitsResolve() {
            assertThat(UnitNormalizer.canonicalLabel("Km")).isEqualTo("Km");
            assertThat(UnitNormalizer.canonicalLabel("trip")).isEqualTo("trip");
            assertThat(UnitNormalizer.canonicalLabel("layer")).isEqualTo("layer");
            assertThat(UnitNormalizer.canonicalLabel("Sqft")).isEqualTo("Sqft");
            assertThat(UnitNormalizer.canonicalLabel("ft")).isEqualTo("ft");
            assertThat(UnitNormalizer.canonicalLabel("in")).isEqualTo("in");
            assertThat(UnitNormalizer.canonicalLabel("Brass")).isEqualTo("Brass");
        }
    }

    // -----------------------------------------------------------------------
    // 2. mm is a distinct measure — never merged into m
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("canonicalLabel — mm is distinct from m")
    class MillimetreDistinct {

        @Test
        @DisplayName("mm resolves to mm, not m")
        void mmResolvesToItself() {
            assertThat(UnitNormalizer.canonicalLabel("mm")).isEqualTo("mm");
            assertThat(UnitNormalizer.canonicalLabel("mm")).isNotEqualTo("m");
        }

        @Test
        @DisplayName("sameUnit(mm, m) is false")
        void mmAndMAreNotSameUnit() {
            assertThat(UnitNormalizer.sameUnit("mm", "m")).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // 3. Case/trim insensitivity
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("canonicalLabel — case and whitespace insensitive matching")
    class CaseAndTrimInsensitivity {

        @Test
        @DisplayName("padded/mixed-case alias still resolves to canonical spelling")
        void paddedAliasResolves() {
            assertThat(UnitNormalizer.canonicalLabel("  cu.m. ")).isEqualTo("Cum");
        }

        @Test
        @DisplayName("sameUnit is case-insensitive across alias and canonical spelling")
        void sameUnitCaseInsensitive() {
            assertThat(UnitNormalizer.sameUnit("CUM", "cu.m.")).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // 4. Unknown units pass through unchanged (trimmed only)
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("canonicalLabel — unknown unit passthrough")
    class UnknownPassthrough {

        @Test
        @DisplayName("unrecognized unit is trimmed but not otherwise altered")
        void unknownUnitPassesThroughTrimmed() {
            assertThat(UnitNormalizer.canonicalLabel(" widget ")).isEqualTo("widget");
        }
    }

    // -----------------------------------------------------------------------
    // 5. null/blank handling
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("null/blank handling")
    class NullAndBlankHandling {

        @Test
        @DisplayName("canonicalLabel(null) and canonicalLabel(blank) return null")
        void canonicalLabelNullAndBlank() {
            assertThat(UnitNormalizer.canonicalLabel(null)).isNull();
            assertThat(UnitNormalizer.canonicalLabel("  ")).isNull();
        }

        @Test
        @DisplayName("sameUnit(null, ...) is false; sameUnit(x, x) is true")
        void sameUnitNullAndSelf() {
            assertThat(UnitNormalizer.sameUnit(null, "Cum")).isFalse();
            assertThat(UnitNormalizer.sameUnit("Cum", "Cum")).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // 6. sameUnit — cross-alias equivalence and non-equivalence
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("sameUnit — cross-alias comparison")
    class SameUnitComparison {

        @Test
        @DisplayName("Cum and cu.m. are the same unit")
        void cumAndAliasAreSame() {
            assertThat(UnitNormalizer.sameUnit("Cum", "cu.m.")).isTrue();
        }

        @Test
        @DisplayName("Day and Manday are the same unit")
        void dayAndMandayAreSame() {
            assertThat(UnitNormalizer.sameUnit("Day", "Manday")).isTrue();
        }

        @Test
        @DisplayName("Cum and Sqm are NOT the same unit")
        void cumAndSqmAreNotSame() {
            assertThat(UnitNormalizer.sameUnit("Cum", "Sqm")).isFalse();
        }
    }
}
