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
        assertThat(DprUnits.canonical("nos")).isEqualTo("Nos");
    }

    @Test
    void synonymsMapToCanonicalCode() {
        assertThat(DprUnits.canonical("cubic meter")).isEqualTo("Cum");
        assertThat(DprUnits.canonical("cubic metre")).isEqualTo("Cum");
        assertThat(DprUnits.canonical("m3")).isEqualTo("Cum");
        assertThat(DprUnits.canonical("square meter")).isEqualTo("Sqm");
        assertThat(DprUnits.canonical("metric ton")).isEqualTo("MT");
        assertThat(DprUnits.canonical("number")).isEqualTo("Nos");
        assertThat(DprUnits.canonical("bags")).isEqualTo("Bag");
        assertThat(DprUnits.canonical("hours")).isEqualTo("Hour");
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
