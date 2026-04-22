package com.bipros.gis.domain.model;

public enum SatelliteImageSource {
    SENTINEL_HUB("Sentinel-2 (Copernicus)"),
    ISRO_CARTOSAT("ISRO Cartosat-3"),
    PLANET_LABS("Planet Scope"),
    MAXAR("Maxar WorldView"),
    AIRBUS("Airbus Pléiades"),
    DRONE("Drone Survey"),
    MANUAL_UPLOAD("Manual Upload");

    private final String displayName;

    SatelliteImageSource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
