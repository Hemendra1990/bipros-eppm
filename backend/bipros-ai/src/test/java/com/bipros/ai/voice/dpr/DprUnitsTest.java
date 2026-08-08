package com.bipros.ai.voice.dpr;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DprUnitsTest {

    @Test
    void exactCaseInsensitiveMapsToCanonicalCasing() {
        assertThat(DprUnits.canonical("cum")).isEqualTo("Cum");
        assertThat(DprUnits.canonical("CUM")).isEqualTo("Cum");
        assertThat(DprUnits.canonical("Sqm")).isEqualTo("Sqm");
        assertThat(DprUnits.canonical("mt")).isEqualTo("MT");
        assertThat(DprUnits.canonical("kg")).isEqualTo("Kg");
        assertThat(DprUnits.canonical("hrs")).isEqualTo("Hrs");
    }

    @Test
    void synonymsMapToCanonicalCode() {
        assertThat(DprUnits.canonical("cubic meter")).isEqualTo("Cum");
        assertThat(DprUnits.canonical("cubic metre")).isEqualTo("Cum");
        assertThat(DprUnits.canonical("m3")).isEqualTo("Cum");
        assertThat(DprUnits.canonical("square meter")).isEqualTo("Sqm");
        assertThat(DprUnits.canonical("metric ton")).isEqualTo("MT");
        assertThat(DprUnits.canonical("tonne")).isEqualTo("MT");
        assertThat(DprUnits.canonical("number")).isEqualTo("Nr");
        assertThat(DprUnits.canonical("nos")).isEqualTo("Nr");
        assertThat(DprUnits.canonical("each")).isEqualTo("Nr");
        assertThat(DprUnits.canonical("bags")).isEqualTo("Bag");
        assertThat(DprUnits.canonical("hours")).isEqualTo("Hrs");
        assertThat(DprUnits.canonical("lump sum")).isEqualTo("LS");
        assertThat(DprUnits.canonical("weeks")).isEqualTo("Week");
    }

    @Test
    void retiredMagnitudeDifferentUnitsKeepCompactSpellingAsLegacy() {
        // Not on the 12-list and not dimension-identical to anything on it — must NOT be
        // converted; they pass through and the FE renders them "(legacy)".
        assertThat(DprUnits.canonical("square feet")).isEqualTo("Sqft");
        assertThat(DprUnits.canonical("millimetre")).isEqualTo("mm");
        assertThat(DprUnits.canonical("quintal")).isEqualTo("Quintal");
    }

    @Test
    void unknownUnitPassesThrough() {
        assertThat(DprUnits.canonical("furlong")).isEqualTo("furlong");
    }

    @Test
    void nullAndBlankHandled() {
        assertThat(DprUnits.canonical(null)).isNull();
        assertThat(DprUnits.canonical("   ")).isEmpty();
    }
}
