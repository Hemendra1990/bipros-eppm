package com.bipros.ai.persona;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RolePersonaProviderTest {

    private final RolePersonaProvider provider = new RolePersonaProvider();

    @Test
    void siteManagerPersonaHasCrewIdleWastageKpis() {
        RolePersona p = provider.forProfile("SITE_MANAGER");
        assertNotNull(p);
        assertTrue(p.headline().contains("Site Manager"));
        assertTrue(p.primaryKpis().stream().anyMatch(k -> k.toLowerCase().contains("utiliz")));
        assertTrue(p.primaryKpis().stream().anyMatch(k -> k.toLowerCase().contains("idle")));
        assertTrue(p.primaryKpis().stream().anyMatch(k -> k.toLowerCase().contains("wastage")));
    }

    @Test
    void projectManagerPersonaHasCpiSpiCostKpis() {
        RolePersona p = provider.forProfile("PROJECT_MANAGER");
        assertNotNull(p);
        assertTrue(p.primaryKpis().stream().anyMatch(k -> k.toUpperCase().contains("CPI")));
        assertTrue(p.primaryKpis().stream().anyMatch(k -> k.toUpperCase().contains("SPI")));
    }

    @Test
    void qcManagerPersonaHasNcrTraceabilityKpis() {
        RolePersona p = provider.forProfile("QC_MANAGER");
        assertNotNull(p);
        assertTrue(p.primaryKpis().stream().anyMatch(k -> k.toUpperCase().contains("NCR")));
    }

    @Test
    void projectEngineerHasYieldProductivity() {
        RolePersona p = provider.forProfile("PROJECT_ENGINEER");
        assertNotNull(p);
        assertTrue(p.primaryKpis().stream().anyMatch(k -> k.toLowerCase().contains("yield")));
        assertTrue(p.primaryKpis().stream().anyMatch(k -> k.toLowerCase().contains("productivity")));
    }

    @Test
    void bimDataCoordinatorHasDataIntegrityKpis() {
        RolePersona p = provider.forProfile("BIM_DATA_COORDINATOR");
        assertNotNull(p);
        assertTrue(p.primaryKpis().stream().anyMatch(k -> k.toLowerCase().contains("data")));
    }

    @Test
    void unknownProfileReturnsNull() {
        assertNull(provider.forProfile("UNKNOWN"));
        assertNull(provider.forProfile(null));
    }

    @Test
    void renderProducesNonEmptyBlock() {
        RolePersona p = provider.forProfile("SITE_MANAGER");
        String block = p.render();
        assertTrue(block.contains("ROLE PERSONA"));
        assertTrue(block.contains("Site Manager"));
    }
}
