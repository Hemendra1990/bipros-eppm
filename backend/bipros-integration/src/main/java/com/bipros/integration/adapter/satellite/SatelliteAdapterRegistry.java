package com.bipros.integration.adapter.satellite;

import com.bipros.common.exception.BusinessRuleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves {@link SatelliteAdapter} beans by {@link SatelliteAdapter#vendorId()}.
 * Spring auto-discovers adapters via component scan; the registry just indexes
 * them by vendor id so callers pick without referencing concrete classes.
 */
@Component
@Slf4j
public class SatelliteAdapterRegistry {

    private final Map<String, SatelliteAdapter> byVendor;
    private final String defaultVendor;

    public SatelliteAdapterRegistry(List<SatelliteAdapter> adapters) {
        this.byVendor = new HashMap<>();
        for (SatelliteAdapter a : adapters) byVendor.put(a.vendorId(), a);
        // Prefer sentinel-hub as the default if present, otherwise any.
        this.defaultVendor = byVendor.containsKey("sentinel-hub")
            ? "sentinel-hub"
            : byVendor.keySet().stream().findFirst().orElse(null);
        log.info("[Satellite] registered adapters: {} (default: {})", byVendor.keySet(), defaultVendor);
    }

    public Optional<SatelliteAdapter> find(String vendorId) {
        return Optional.ofNullable(byVendor.get(vendorId));
    }

    public SatelliteAdapter defaultAdapter() {
        if (defaultVendor == null) {
            throw new BusinessRuleException("NO_SATELLITE_ADAPTER",
                "No satellite adapter is configured. Enable one of the bipros.satellite.* providers.");
        }
        return byVendor.get(defaultVendor);
    }
}
