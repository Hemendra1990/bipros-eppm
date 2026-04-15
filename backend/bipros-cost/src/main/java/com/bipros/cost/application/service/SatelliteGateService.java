package com.bipros.cost.application.service;

import com.bipros.cost.domain.entity.RaBill;
import com.bipros.cost.domain.entity.SatelliteGate;
import com.bipros.gis.domain.model.ConstructionProgressSnapshot;
import com.bipros.gis.domain.repository.ConstructionProgressSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Evaluates the satellite payment gate on an {@link RaBill} before certification.
 *
 * <p>Algorithm (matches Excel M4 spec):
 * <ul>
 *   <li>Fetch latest {@link ConstructionProgressSnapshot} for the bill's
 *       {@code wbsPackageCode}.</li>
 *   <li>Compute {@code variance = |contractorClaimed - aiProgress|}.</li>
 *   <li>Band variance: ≤5% → PASS, 5-10 → HOLD_VARIANCE, &gt;10 → RED_VARIANCE.</li>
 *   <li>Stamp {@code aiSatellitePercent}, {@code satelliteGateVariance},
 *       {@code satelliteGate} on the bill.</li>
 * </ul>
 *
 * <p>Does not mutate {@code status} — that is the certifier's decision, informed
 * by the gate banding.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SatelliteGateService {

    private static final BigDecimal WARN_THRESHOLD = new BigDecimal("5.00");
    private static final BigDecimal RED_THRESHOLD = new BigDecimal("10.00");

    private final ConstructionProgressSnapshotRepository snapshotRepository;

    /**
     * Populate satellite-gate fields on the given bill. Returns the same bill for chaining.
     * Caller is responsible for saving the returned entity.
     */
    public RaBill evaluate(RaBill bill) {
        if (bill.getWbsPackageCode() == null || bill.getWbsPackageCode().isBlank()) {
            log.debug("[SatelliteGate] bill {} has no wbsPackageCode — gate skipped", bill.getBillNumber());
            return bill;
        }
        if (bill.getContractorClaimedPercent() == null) {
            log.debug("[SatelliteGate] bill {} has no contractor claim — gate skipped", bill.getBillNumber());
            return bill;
        }

        Optional<ConstructionProgressSnapshot> latest =
                snapshotRepository.findTopByWbsPackageCodeOrderByCaptureDateDesc(bill.getWbsPackageCode());
        if (latest.isEmpty() || latest.get().getAiProgressPercent() == null) {
            log.debug("[SatelliteGate] no AI snapshot for {} — gate skipped", bill.getWbsPackageCode());
            return bill;
        }

        BigDecimal ai = BigDecimal.valueOf(latest.get().getAiProgressPercent());
        BigDecimal claim = bill.getContractorClaimedPercent();
        BigDecimal variance = claim.subtract(ai).abs().setScale(2, RoundingMode.HALF_UP);

        SatelliteGate gate;
        if (variance.compareTo(WARN_THRESHOLD) <= 0) {
            gate = SatelliteGate.PASS;
        } else if (variance.compareTo(RED_THRESHOLD) <= 0) {
            gate = SatelliteGate.HOLD_VARIANCE;
        } else {
            gate = SatelliteGate.RED_VARIANCE;
        }

        bill.setAiSatellitePercent(ai.setScale(2, RoundingMode.HALF_UP));
        bill.setSatelliteGateVariance(variance);
        bill.setSatelliteGate(gate);

        log.info("[SatelliteGate] bill {} wbs={} ai={} claim={} variance={} → {}",
                bill.getBillNumber(), bill.getWbsPackageCode(), ai, claim, variance, gate);
        return bill;
    }
}
