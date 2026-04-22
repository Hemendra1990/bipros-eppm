package com.bipros.contract.application.service;

import com.bipros.contract.domain.model.Contract;
import com.bipros.contract.domain.model.PerformanceBond;
import com.bipros.contract.domain.model.VariationOrder;
import com.bipros.contract.domain.repository.ContractRepository;
import com.bipros.contract.domain.repository.PerformanceBondRepository;
import com.bipros.contract.domain.repository.VariationOrderRepository;
import com.bipros.gis.domain.model.ConstructionProgressSnapshot;
import com.bipros.gis.domain.repository.ConstructionProgressSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Refreshes denormalised contract KPIs (VO count/value, BG expiry, kpiRefreshedAt).
 *
 * <p>SPI/CPI/physicalProgressAi/cumulativeRaBillsCrores/performanceScore are populated
 * by upstream services (EVM snapshots, RA bill submissions, contractor scorecards)
 * — this service only rolls up values that can be derived directly from contract sub-entities.
 *
 * <p>Runs nightly at 02:15 and on demand via {@link #refreshById(UUID)}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContractKpiService {

    private final ContractRepository contractRepository;
    private final PerformanceBondRepository bondRepository;
    private final VariationOrderRepository voRepository;
    private final ConstructionProgressSnapshotRepository progressSnapshotRepository;

    /** Nightly refresh — 02:15 local time, outside office hours. */
    @Scheduled(cron = "0 15 2 * * *")
    @Transactional
    public void refreshAll() {
        log.info("[ContractKpi] nightly refresh start");
        List<Contract> contracts = contractRepository.findAll();
        int refreshed = 0;
        for (Contract c : contracts) {
            refresh(c);
            refreshed++;
        }
        contractRepository.saveAll(contracts);
        log.info("[ContractKpi] nightly refresh done: {} contracts", refreshed);
    }

    @Transactional
    public Contract refreshById(UUID contractId) {
        Contract c = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found: " + contractId));
        refresh(c);
        return contractRepository.save(c);
    }

    private void refresh(Contract c) {
        // Variation orders rollup
        List<VariationOrder> vos = voRepository.findByContractId(c.getId());
        c.setVoNumbersIssued(vos.size());
        BigDecimal voTotal = vos.stream()
                .map(VariationOrder::getVoValue)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        c.setVoValueCrores(voTotal);

        // BG expiry — earliest active performance bond
        List<PerformanceBond> bonds = bondRepository.findByContractId(c.getId());
        LocalDate earliest = bonds.stream()
                .map(PerformanceBond::getExpiryDate)
                .filter(d -> d != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
        c.setBgExpiry(earliest);

        // AI-derived physical progress from the latest ConstructionProgressSnapshot
        // for this contract's WBS package. Stays null if no GIS data exists yet —
        // we don't want to accidentally overwrite a valid value with null.
        if (c.getWbsPackageCode() != null && !c.getWbsPackageCode().isBlank()) {
            Optional<ConstructionProgressSnapshot> snapshot = progressSnapshotRepository
                .findTopByWbsPackageCodeOrderByCaptureDateDesc(c.getWbsPackageCode());
            snapshot
                .map(ConstructionProgressSnapshot::getAiProgressPercent)
                .filter(p -> p != null)
                .ifPresent(pct -> c.setPhysicalProgressAi(
                    BigDecimal.valueOf(pct).setScale(2, RoundingMode.HALF_UP)));
        }

        c.setKpiRefreshedAt(OffsetDateTime.now());
    }
}
